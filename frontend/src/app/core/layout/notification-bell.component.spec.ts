import {TestBed} from '@angular/core/testing';
import {Component, provideZonelessChangeDetection, signal} from '@angular/core';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {NotificationBellComponent} from './notification-bell.component';
import {provideRouter} from '@angular/router';
import {AuthStore} from '../state/auth.store';
import {NotificationBellStore} from '../state/notification-bell.store';
import {WebSocketService, WsEvent} from '../ws/websocket.service';

const event = (type: string, payload: Record<string, string>): WsEvent =>
  ({type, centerId: 'c', payload, timestamp: '2026-10-05T07:00:00Z'}) as WsEvent;

const absence = event('INFIRMIER_ABSENCE_ENREGISTREE',
  {infirmier: 'Amrani Sara', debut: '2026-10-19', fin: '2026-11-02', targetRoles: 'ADMIN,MEDECIN'});
const patient = event('PATIENT_CREATED', {patientId: 'p1', nom: 'Kaci', prenom: 'Lila'});

/** Écran d'arrivée factice : les actions naviguent réellement. */
@Component({template: ''})
class EcranCible {
}

describe('NotificationBellComponent', () => {
  let roles: string[];
  let store: {
    open: ReturnType<typeof signal<boolean>>;
    selectedEvent: ReturnType<typeof signal<WsEvent | null>>;
    selectMessage: ReturnType<typeof vi.fn>;
    closePanel: ReturnType<typeof vi.fn>;
    [cle: string]: unknown;
  };

  beforeEach(() => {
    roles = ['ADMIN'];
    store = {
      open: signal(true), activeTab: signal('unread'), selectedEventId: signal(null),
      unreadEvents: signal([absence, patient]), readEvents: signal([]), visibleEvents: signal([absence, patient]),
      selectedEvent: signal<WsEvent | null>(null), unreadCount: signal(2),
      eventId: (evt: WsEvent) => `${evt.type}-${evt.timestamp}`, isRead: () => false,
      selectMessage: vi.fn(), closePanel: vi.fn(), togglePanel: vi.fn(), setActiveTab: vi.fn(), markAllRead: vi.fn(),
    };
    TestBed.configureTestingModule({
      imports: [NotificationBellComponent, TranslateModule.forRoot()],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([{path: '**', component: EcranCible}]),
        {provide: AuthStore, useValue: {hasRole: (r: string) => roles.includes(r)}},
        {
          provide: WebSocketService,
          useValue: {connectionStatus: signal('stable'), lastEvent: signal(null), statusColor: () => 'green'},
        },
        {provide: NotificationBellStore, useValue: store},
      ],
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('fr', {
      NOTIFICATION: {
        TITRE: {
          INFIRMIER_ABSENCE_ENREGISTREE: 'Absence d\'un infirmier à remplacer',
          PATIENT_CREATED: 'Nouveau patient'
        },
        INFIRMIER_ABSENCE_ENREGISTREE: 'Absence de {{infirmier}} du {{debut}} au {{fin}}',
        PATIENT_CREATED: 'Nouveau patient : {{nom}}',
        ACTION: {
          COUVERTURE: 'Chercher des remplaçants',
          FICHE_PATIENT: 'Ouvrir la fiche du patient',
          PLANNING: 'Voir le planning'
        },
        ACTION_REQUISE: 'Action requise',
      },
    });
    translate.use('fr');
  });

  function render() {
    const fixture = TestBed.createComponent(NotificationBellComponent);
    fixture.detectChanges();
    return {fixture, root: fixture.nativeElement as HTMLElement};
  }

  const item = (root: HTMLElement, type: string) =>
    root.querySelector<HTMLElement>(`[data-testid="notif-item"][data-type="${type}"]`)!;

  it('affiche pour chaque notification un libellé, un message lisible et son action', () => {
    const {root} = render();

    const ligne = item(root, 'INFIRMIER_ABSENCE_ENREGISTREE');
    expect(ligne.querySelector('.message-title')!.textContent).toContain('Absence d\'un infirmier à remplacer');
    expect(ligne.querySelector('.message-title')!.textContent).toContain('Action requise');
    expect(ligne.querySelector('.message-text')!.textContent).toContain('Absence de Amrani Sara du 19/10/2026 au 02/11/2026');
    const action = ligne.querySelector<HTMLAnchorElement>('[data-testid="notif-item-action"]')!;
    expect(action.textContent).toContain('Chercher des remplaçants');
    expect(action.getAttribute('href')).toContain('/seances/optimisation?perimetre=COUVERTURE&debut=2026-10-19');

    const autre = item(root, 'PATIENT_CREATED');
    expect(autre.querySelector('.message-title')!.textContent).not.toContain('Action requise');
    expect(autre.querySelector<HTMLAnchorElement>('[data-testid="notif-item-action"]')!.getAttribute('href'))
      .toBe('/patients/p1');
    expect(root.querySelectorAll('[data-testid="notif-item-action"]')).toHaveLength(2);
  });

  it('adapte l\'action au profil : le médecin va au planning de la semaine de l\'absence', () => {
    roles = ['MEDECIN'];
    const {root} = render();

    const action = item(root, 'INFIRMIER_ABSENCE_ENREGISTREE')
      .querySelector<HTMLAnchorElement>('[data-testid="notif-item-action"]')!;
    expect(action.textContent).toContain('Voir le planning');
    expect(action.getAttribute('href')).toBe('/medecin?date=2026-10-19');
  });

  it('marque la notification comme lue et ferme le panneau quand l\'utilisateur ouvre son action', () => {
    const {root} = render();

    item(root, 'INFIRMIER_ABSENCE_ENREGISTREE')
      .querySelector<HTMLAnchorElement>('[data-testid="notif-item-action"]')!
      .dispatchEvent(new MouseEvent('click', {bubbles: true, cancelable: true}));

    expect(store.selectMessage).toHaveBeenCalledWith(absence);
    expect(store.closePanel).toHaveBeenCalled();
  });

  it('détaille la notification sélectionnée sans code technique ni contenu brut, avec le bouton d\'action', () => {
    store.selectedEvent.set(absence);
    const {root} = render();

    expect(root.querySelector('[data-testid="notif-titre"]')!.textContent).toContain('Absence d\'un infirmier à remplacer');
    expect(root.querySelector('[data-testid="notif-message"]')!.textContent).toContain('Amrani Sara');
    expect(root.querySelector('[data-testid="notif-action"]')!.textContent).toContain('Chercher des remplaçants');
    expect(root.querySelector('.message-detail')!.textContent).not.toContain('INFIRMIER_ABSENCE_ENREGISTREE');
    expect(root.querySelector('pre')).toBeNull();
  });

  it('sélectionne une notification au clic sur son texte', () => {
    const {root} = render();

    item(root, 'PATIENT_CREATED').querySelector<HTMLButtonElement>('.message-open')!.click();

    expect(store.selectMessage).toHaveBeenCalledWith(patient);
    expect(store.closePanel).not.toHaveBeenCalled();
  });
});
