import {provideZonelessChangeDetection, signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {of, throwError} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {AlerteServeur, NotificationApiService} from '../api/notification-api.service';
import {WebSocketService, WsEvent} from '../ws/websocket.service';
import {AuthStore} from './auth.store';
import {NotificationBellStore} from './notification-bell.store';

const CENTRE = '11111111-1111-1111-1111-111111111111';

const alerte = (id: string, type: string, timestamp: string, payload: Record<string, string> = {},
                lue = false): AlerteServeur => ({id, type, centerId: CENTRE, payload, timestamp, lue});

describe('NotificationBellStore', () => {
  let api: Record<'liste' | 'marquerLues' | 'toutMarquerLu', ReturnType<typeof vi.fn>>;
  let wsEvents: ReturnType<typeof signal<WsEvent[]>>;
  let roles: string[];
  let centre: ReturnType<typeof signal<string | null>>;

  beforeEach(() => {
    roles = ['ADMIN'];
    centre = signal<string | null>(CENTRE);
    wsEvents = signal<WsEvent[]>([]);
    api = {
      liste: vi.fn().mockReturnValue(of({
        items: [
          alerte('a1', 'OPTIMISATION_PROPOSITION', '2026-10-07T02:31:00Z', {runId: 'r1', targetRoles: 'ADMIN'}),
          alerte('a2', 'GENERATEUR_INDISPONIBLE', '2026-10-06T10:00:00Z', {targetRoles: 'ADMIN'}, true),
          alerte('a3', 'INFIRMIER_SOUS_EFFECTIF', '2026-10-05T07:15:00Z', {targetRoles: 'MEDECIN'}),
        ],
        total: 3, page: 0, size: 50,
      })),
      marquerLues: vi.fn().mockReturnValue(of(undefined)),
      toutMarquerLu: vi.fn().mockReturnValue(of(undefined)),
    };
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        {provide: NotificationApiService, useValue: api},
        {provide: WebSocketService, useValue: {events: wsEvents}},
        {provide: AuthStore, useValue: {centerId: centre, hasRole: (r: string) => roles.includes(r)}},
      ],
    });
  });

  async function store() {
    const s = TestBed.inject(NotificationBellStore);
    await s.charger();
    return s;
  }

  it('relit à la connexion les alertes durables du centre, que l\'utilisateur ait été connecté ou non', async () => {
    const s = await store();

    expect(api.liste).toHaveBeenCalledWith(CENTRE, 0, 50);
    expect(s.unreadEvents().map((e) => e.type)).toEqual(['OPTIMISATION_PROPOSITION']);
    expect(s.readEvents().map((e) => e.type)).toEqual(['GENERATEUR_INDISPONIBLE']);
    expect(s.unreadCount()).toBe(1);
  });

  it('ne montre que les alertes ciblant l\'un des rôles de l\'utilisateur', async () => {
    const admin = await store();

    expect(admin.tous().map((e) => e.id)).toEqual(['a1', 'a2']);
  });

  it('n\'affiche jamais un évènement technique de rafraîchissement comme une notification', async () => {
    wsEvents.set([{
      type: 'PRESCRIPTION_CHANGED', centerId: CENTRE, payload: {patientId: 'p1', targetRoles: 'ADMIN'},
      timestamp: '2026-10-07T10:00:00Z',
    } as WsEvent]);
    const s = await store();

    expect(s.tous().map((e) => e.type)).not.toContain('PRESCRIPTION_CHANGED');
    expect(s.unreadCount()).toBe(1);
  });

  it('montre au médecin celles qui le ciblent', async () => {
    roles = ['MEDECIN'];

    const medecin = await store();

    expect(medecin.tous().map((e) => e.id)).toEqual(['a3']);
  });

  it('réunit temps réel et journal sans doublon et du plus récent au plus ancien', async () => {
    const s = await store();
    wsEvents.set([
      {
        id: 'a1', type: 'OPTIMISATION_PROPOSITION', centerId: CENTRE, payload: {targetRoles: 'ADMIN'},
        timestamp: '2026-10-07T02:31:00Z'
      },
      {type: 'PATIENT_CREATED', centerId: CENTRE, payload: {patientCode: 'P1'}, timestamp: '2026-10-07T08:00:00Z'},
    ]);

    expect(s.tous().map((e) => e.type)).toEqual(['PATIENT_CREATED', 'OPTIMISATION_PROPOSITION', 'GENERATEUR_INDISPONIBLE']);
  });

  it('enregistre la lecture côté serveur pour une alerte durable, une seule fois', async () => {
    const s = await store();
    const alerteNuit = s.unreadEvents()[0];

    s.selectMessage(alerteNuit);
    s.selectMessage(alerteNuit);

    expect(api.marquerLues).toHaveBeenCalledTimes(1);
    expect(api.marquerLues).toHaveBeenCalledWith(CENTRE, ['a1']);
    expect(s.isRead(alerteNuit)).toBe(true);
    expect(s.unreadCount()).toBe(0);
  });

  it('ne sollicite pas le serveur pour un évènement qui ne vaut que sur le moment', async () => {
    const s = await store();
    wsEvents.set([{
      type: 'PATIENT_CREATED', centerId: CENTRE, payload: {patientCode: 'P1'},
      timestamp: '2026-10-07T08:00:00Z'
    }]);

    s.selectMessage(wsEvents()[0]);

    expect(api.marquerLues).not.toHaveBeenCalled();
    expect(s.isRead(wsEvents()[0])).toBe(true);
  });

  it('marque tout comme lu localement et sur le serveur', async () => {
    const s = await store();

    s.markAllRead();

    expect(s.unreadCount()).toBe(0);
    expect(api.toutMarquerLu).toHaveBeenCalledWith(CENTRE);
  });

  it('garde le temps réel quand le journal est indisponible', async () => {
    api.liste.mockReturnValue(throwError(() => new Error('boom')));
    const s = await store();
    wsEvents.set([{
      type: 'PATIENT_CREATED', centerId: CENTRE, payload: {patientCode: 'P1'},
      timestamp: '2026-10-07T08:00:00Z'
    }]);

    expect(s.unreadEvents()).toHaveLength(1);
  });

  it('ne lit pas le journal d\'une session sans centre', async () => {
    centre.set(null);
    api.liste.mockClear();

    await TestBed.inject(NotificationBellStore).charger();

    expect(api.liste).not.toHaveBeenCalled();
  });
});
