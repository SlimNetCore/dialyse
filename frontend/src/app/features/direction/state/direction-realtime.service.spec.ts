import {TestBed} from '@angular/core/testing';
import {MatSnackBar} from '@angular/material/snack-bar';
import {TranslateService} from '@ngx-translate/core';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {DashboardChange, DashboardChangedEvent} from '../../../core/api/direction-api.service';
import {DirectionRealtimeService} from './direction-realtime.service';
import {DirectionStore} from './direction.store';

const change = (centre: string, family: string, name: string, before: number, after: number): DashboardChange =>
  ({centerId: centre, centre, family, name, before, after});

const event = (changes: DashboardChange[]): DashboardChangedEvent =>
  ({type: 'DASHBOARD_CHANGED', societeId: 's', at: '2026-09-27T10:00:00Z', changes});

describe('DirectionRealtimeService', () => {
  let refresh: ReturnType<typeof vi.fn>;
  let snack: { open: ReturnType<typeof vi.fn> };
  let service: DirectionRealtimeService;

  beforeEach(() => {
    vi.useFakeTimers();
    refresh = vi.fn().mockResolvedValue(undefined);
    snack = {open: vi.fn()};
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        {provide: DirectionStore, useValue: {refresh}},
        {provide: MatSnackBar, useValue: snack},
        {
          provide: TranslateService,
          // renvoie la clé et ses paramètres : suffisant pour vérifier ce qui est demandé à l'interface
          useValue: {instant: (key: string, params?: Record<string, unknown>) => `${key}${params ? JSON.stringify(params) : ''}`},
        },
      ],
    });
    service = TestBed.inject(DirectionRealtimeService);
  });

  afterEach(() => vi.useRealTimers());

  it('crée une notification par centre et par famille et prévient l’utilisateur', () => {
    service.handle(event([
      change('Alpha', 'SEANCES', 'seances', 1, 2),
      change('Alpha', 'FINANCE', 'caTtc', 100, 250),
      change('Beta', 'SEANCES', 'seances', 3, 4),
    ]));
    const list = service.notifications();
    expect(list).toHaveLength(3);
    expect(list.map((n) => `${n.centre}|${n.family}`)).toEqual(['Alpha|SEANCES', 'Alpha|FINANCE', 'Beta|SEANCES']);
    expect(list.every((n) => !n.read)).toBe(true);
    expect(service.unreadCount()).toBe(3);
    expect(snack.open).toHaveBeenCalledTimes(1);
    expect(snack.open.mock.calls[0][0]).toContain('(+2)');
  });

  it('regroupe une rafale d’événements en une seule relecture des données', () => {
    service.handle(event([change('Alpha', 'SEANCES', 'seances', 1, 2)]));
    service.handle(event([change('Alpha', 'FINANCE', 'caTtc', 1, 2)]));
    service.handle(event([change('Beta', 'PATIENTS', 'patients', 5, 6)]));
    expect(refresh).not.toHaveBeenCalled();
    vi.advanceTimersByTime(500);
    expect(refresh).toHaveBeenCalledTimes(1);
  });

  it('distingue une alerte levée d’une alerte résolue', () => {
    service.handle(event([change('Alpha', 'ALERTES', 'LOTS_PERIMES', 0, 2), change('Alpha', 'ALERTES', 'STOCK_SOUS_SEUIL', 3, 0)]));
    const n = service.notifications()[0];
    expect(n.text).toContain('DIRECTION.RT.ALERT_RAISED');
    expect(n.text).toContain('DIRECTION.RT.ALERT_CLEARED');
    expect(n.severity).toBe('critical'); // au moins une alerte levée
    expect(n.title).toContain('ALERT_RAISED_TITLE');
  });

  it('qualifie une variation d’indicateur en information, avec sa famille en titre', () => {
    service.handle(event([change('Alpha', 'FINANCE', 'caTtc', 100, 250)]));
    const n = service.notifications()[0];
    expect(n.severity).toBe('info');
    expect(n.title).toContain('FAMILY_LABEL.FINANCE');
    expect(n.detail).toContain('+150');
  });

  it('ignore un événement sans changement', () => {
    service.handle(event([]));
    expect(service.notifications()).toHaveLength(0);
    vi.advanceTimersByTime(1000);
    expect(refresh).not.toHaveBeenCalled();
  });

  it('marque lu, efface et limite la liste à 50 notifications', () => {
    for (let i = 0; i < 60; i++) {
      service.handle(event([change(`C${i}`, 'SEANCES', 'seances', i, i + 1)]));
    }
    expect(service.notifications()).toHaveLength(50);
    const first = service.notifications()[0];
    service.markRead(first.id);
    expect(service.unreadCount()).toBe(49);
    service.markAllRead();
    expect(service.unreadCount()).toBe(0);
    service.clear();
    expect(service.notifications()).toEqual([]);
  });

  it('ne se connecte pas sans société', () => {
    service.start(null);
    expect(service.status()).toBe('off');
  });
});
