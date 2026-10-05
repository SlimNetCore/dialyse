import {TestBed} from '@angular/core/testing';
import {provideZonelessChangeDetection, signal} from '@angular/core';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {beforeEach, describe, expect, it} from 'vitest';
import {NotificationBellComponent} from './notification-bell.component';
import {NotificationBellStore} from '../state/notification-bell.store';
import {WebSocketService, WsEvent} from '../ws/websocket.service';

const event = (type: string, payload: Record<string, string>): WsEvent =>
  ({type, centerId: 'c', payload, timestamp: '2026-10-05T07:00:00Z'}) as WsEvent;

describe('NotificationBellComponent — textes des évènements', () => {
  let cmp: NotificationBellComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [NotificationBellComponent, TranslateModule.forRoot()],
      providers: [
        provideZonelessChangeDetection(),
        {provide: WebSocketService, useValue: {connectionStatus: signal('stable'), lastEvent: signal(null)}},
        {
          provide: NotificationBellStore,
          useValue: {
            open: signal(false), activeTab: signal('unread'), selectedEventId: signal(null),
            unreadEvents: signal([]), readEvents: signal([]), visibleEvents: signal([]), selectedEvent: signal(null),
            unreadCount: signal(0),
          },
        },
      ],
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('fr', {
      NOTIFICATION: {
        SEANCES_A_REGULARISER: '{{count}} séance(s) à régulariser depuis le {{date}}',
        SAISIE: {PARAMEDICAL: '{{auteur}} a saisi le volet de {{nom}}'},
      },
    });
    translate.use('fr');
    cmp = TestBed.createComponent(NotificationBellComponent).componentInstance;
  });

  it('rappelle à l\'administrateur le nombre de séances à régulariser et la plus ancienne', () => {
    const evt = event('SEANCES_A_REGULARISER', {nbSeances: '3', plusAncienne: '2026-09-28', targetRoles: 'ADMIN'});

    expect(cmp.textFor(evt)).toBe('3 séance(s) à régulariser depuis le 2026-09-28');
    expect(cmp.iconFor(evt)).toBe('pending_actions');
    expect(cmp.iconClass(evt)).toBe('warning');
  });

  it('nomme l\'auteur et le patient d\'une saisie d\'infirmier', () => {
    const evt = event('SAISIE_INFIRMIER', {
      saisie: 'PARAMEDICAL',
      auteur: 'inf-01',
      patientNom: 'Dupont',
      patientPrenom: 'Jean'
    });

    expect(cmp.textFor(evt)).toBe('inf-01 a saisi le volet de Dupont Jean');
    expect(cmp.iconFor(evt)).toBe('medical_services');
  });

  it('affiche le type brut d\'un évènement inconnu', () => {
    const evt = event('AUTRE_CHOSE', {});
    expect(cmp.textFor(evt)).toBe('AUTRE_CHOSE');
    expect(cmp.iconFor(evt)).toBe('info');
  });
});
