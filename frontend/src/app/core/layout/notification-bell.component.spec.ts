import {TestBed} from '@angular/core/testing';
import {provideZonelessChangeDetection, signal} from '@angular/core';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {NotificationBellComponent} from './notification-bell.component';
import {provideRouter} from '@angular/router';
import {AuthStore} from '../state/auth.store';
import {NotificationBellStore} from '../state/notification-bell.store';
import {WebSocketService, WsEvent} from '../ws/websocket.service';

const event = (type: string, payload: Record<string, string>): WsEvent =>
  ({type, centerId: 'c', payload, timestamp: '2026-10-05T07:00:00Z'}) as WsEvent;

describe('NotificationBellComponent — textes des évènements', () => {
  let cmp: NotificationBellComponent;
  let roles: string[];

  beforeEach(() => {
    roles = ['ADMIN'];
    TestBed.configureTestingModule({
      imports: [NotificationBellComponent, TranslateModule.forRoot()],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        {provide: AuthStore, useValue: {hasRole: (r: string) => roles.includes(r)}},
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

  it('prévient l\'infirmier qu\'une séance oubliée est déverrouillée', () => {
    TestBed.inject(TranslateService).setTranslation('fr', {
      NOTIFICATION: {SEANCE_DEVERROUILLEE: 'Séance de {{nom}} du {{date}} déverrouillée'},
    }, true);
    const evt = event('SEANCE_DEVERROUILLEE', {
      patientNom: 'Dupont',
      patientPrenom: 'Jean',
      dateSeance: '2026-10-03',
      targetRoles: 'INFIRMIER'
    });

    expect(cmp.textFor(evt)).toBe('Séance de Dupont Jean du 2026-10-03 déverrouillée');
    expect(cmp.iconFor(evt)).toBe('lock_open');
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

  it('annonce la proposition de la replanification nocturne selon son motif', () => {
    TestBed.inject(TranslateService).setTranslation('fr', {
      NOTIFICATION: {
        OPTIMISATION_PROPOSITION: {
          MAINTENANCE: '{{n}} séance(s) à déplacer', GAIN: 'Économie de {{n}} ressource(s)',
        },
      },
    }, true);
    const maintenance = event('OPTIMISATION_PROPOSITION', {runId: 'r1', perimetre: 'MAINTENANCE', motif: 'MAINTENANCE',
      valeur: '2', targetRoles: 'ADMIN'});
    const gain = event('OPTIMISATION_PROPOSITION', {runId: 'r2', perimetre: 'PATIENTS', motif: 'GAIN', valeur: '3'});

    expect(cmp.textFor(maintenance)).toBe('2 séance(s) à déplacer');
    expect(cmp.iconFor(maintenance)).toBe('auto_fix_high');
    expect(cmp.iconClass(maintenance)).toBe('warning');
    expect(cmp.textFor(gain)).toBe('Économie de 3 ressource(s)');
    expect(cmp.iconClass(gain)).toBe('pec');
  });

  it('annonce la suppression d\'une séance par l\'administration', () => {
    TestBed.inject(TranslateService).setTranslation('fr', {
      NOTIFICATION: {SEANCE_SUPPRIMEE: 'Séance du {{date}} supprimée'},
    }, true);
    const evt = event('SEANCE_SUPPRIMEE', {seanceId: 's1', patientId: 'p1', dateSeance: '2026-10-05'});

    expect(cmp.textFor(evt)).toBe('Séance du 2026-10-05 supprimée');
    expect(cmp.iconFor(evt)).toBe('delete');
  });

  it('alerte l\'administrateur d\'un générateur indisponible avec ses patients', () => {
    TestBed.inject(TranslateService).setTranslation('fr', {
      NOTIFICATION: {GENERATEUR_INDISPONIBLE: '{{generateur}} {{statut}} : {{count}} patient(s) ({{patients}})'},
      GMAO: {STATUT_EQUIPEMENT: {HORS_SERVICE: 'hors service'}},
    }, true);
    const evt = event('GENERATEUR_INDISPONIBLE', {
      generateur: 'A-G1', statut: 'HORS_SERVICE', nbPatients: '2',
      patients: 'BENALI Karim, KACI Lila', targetRoles: 'ADMIN,SECRETAIRE'
    });

    expect(cmp.textFor(evt)).toBe('A-G1 hors service : 2 patient(s) (BENALI Karim, KACI Lila)');
    expect(cmp.iconFor(evt)).toBe('build_circle');
    expect(cmp.iconClass(evt)).toBe('warning');
  });

  it('prévient le médecin que des séances ont été déplacées et que l\'administrateur a une absence à couvrir', () => {
    TestBed.inject(TranslateService).setTranslation('fr', {
      NOTIFICATION: {
        SEANCES_DEPLACEES: '{{patients}} patient(s), {{temporaires}} séance(s) temporaire(s)',
        INFIRMIER_ABSENCE_ENREGISTREE: '{{infirmier}} absent du {{debut}} au {{fin}}',
      },
    }, true);

    const deplacees = event('SEANCES_DEPLACEES', {perimetre: 'PATIENTS', nbPatients: '3', nbSeancesTemporaires: '0'});
    const absence = event('INFIRMIER_ABSENCE_ENREGISTREE', {
      infirmier: 'Amrani Sara', debut: '2026-10-08',
      fin: '2026-10-09', targetRoles: 'ADMIN'
    });

    expect(cmp.textFor(deplacees)).toBe('3 patient(s), 0 séance(s) temporaire(s)');
    expect(cmp.iconFor(deplacees)).toBe('swap_horiz');
    expect(cmp.textFor(absence)).toBe('Amrani Sara absent du 2026-10-08 au 2026-10-09');
    expect(cmp.iconFor(absence)).toBe('event_busy');
    expect(cmp.iconClass(absence)).toBe('warning');
  });

  it('chiffre pour l\'administrateur le sur-effectif d\'infirmiers payés sans activité utile', () => {
    TestBed.inject(TranslateService).setTranslation('fr', {
      NOTIFICATION: {INFIRMIER_SUREFFECTIF: '{{count}} créneau(x) dès le {{date}} : {{vacations}} vacation(s), {{heures}} h'},
    }, true);
    const evt = event('INFIRMIER_SUREFFECTIF', {
      nbCreneaux: '2', nbVacations: '3', heures: '15',
      premiereDate: '2026-10-08', targetRoles: 'ADMIN'
    });

    expect(cmp.textFor(evt)).toBe('2 créneau(x) dès le 2026-10-08 : 3 vacation(s), 15 h');
    expect(cmp.iconFor(evt)).toBe('person_off');
    expect(cmp.iconClass(evt)).toBe('warning');
    expect(cmp.lienFor(evt)).toMatchObject({
      commands: ['/seances/optimisation'],
      queryParams: {perimetre: 'ROULEMENT'}
    });
  });

  it('mène chaque alerte à l\'optimisation du bon périmètre ou à la proposition elle-même', () => {
    const lien = (type: string, payload: Record<string, string> = {}) => cmp.lienFor(event(type, payload));

    expect(lien('OPTIMISATION_PROPOSITION', {runId: 'r1'})).toMatchObject({
      commands: ['/seances/optimisation'], queryParams: {run: 'r1'},
    });
    expect(lien('GENERATEUR_INDISPONIBLE')).toMatchObject({queryParams: {perimetre: 'MAINTENANCE'}});
    for (const type of ['INFIRMIER_SOUS_EFFECTIF', 'INFIRMIER_ABSENCE_DECLAREE', 'INFIRMIER_ABSENCE_ENREGISTREE']) {
      expect(lien(type), type).toMatchObject({queryParams: {perimetre: 'COUVERTURE'}});
    }
    expect(lien('SEANCES_DEPLACEES')).toMatchObject({commands: ['/seances/planning']});
    expect(lien('SAISIE_INFIRMIER')).toBeNull();
    expect(lien('OPTIMISATION_PROPOSITION')).toBeNull();
  });

  it('ouvre l\'optimisation sur la période de l\'absence, pas seulement la semaine en cours', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-09-29T08:00:00Z'));
    try {
      const absence = event('INFIRMIER_ABSENCE_ENREGISTREE',
        {infirmier: 'Amrani Sara', debut: '2026-10-19', fin: '2026-11-02', targetRoles: 'ADMIN,MEDECIN'});

      expect(cmp.lienFor(absence)).toMatchObject({
        commands: ['/seances/optimisation'],
        queryParams: {perimetre: 'COUVERTURE', debut: '2026-10-19', semaines: '3'},
      });

      const enCours = event('INFIRMIER_ABSENCE_DECLAREE', {infirmier: 'X', debut: '2026-09-21', fin: '2026-10-10'});
      expect(cmp.lienFor(enCours)).toMatchObject({
        queryParams: {perimetre: 'COUVERTURE', debut: '2026-09-29', semaines: '2'},
      });
    } finally {
      vi.useRealTimers();
    }
  });

  it('mène le médecin au planning de la semaine de l\'absence d\'un infirmier', () => {
    roles = ['MEDECIN'];
    const absence = event('INFIRMIER_ABSENCE_ENREGISTREE', {
      infirmier: 'Amrani Sara',
      debut: '2026-10-19',
      fin: '2026-11-02'
    });

    expect(cmp.lienFor(absence)).toMatchObject({
      label: 'NOTIFICATION.ACTION.PLANNING', commands: ['/medecin'], queryParams: {date: '2026-10-19'},
    });
    expect(cmp.lienFor(event('INFIRMIER_SOUS_EFFECTIF', {premiereDate: '2026-10-05', nbCreneaux: '2'})))
      .toMatchObject({commands: ['/medecin'], queryParams: {date: '2026-10-05'}});
  });

  it('ne propose pas l\'optimisation à un profil qui ne peut pas la lancer', () => {
    roles = ['MEDECIN'];

    expect(cmp.lienFor(event('GENERATEUR_INDISPONIBLE', {}))).toBeNull();
    expect(cmp.lienFor(event('OPTIMISATION_PROPOSITION', {runId: 'r1'}))).toBeNull();
    expect(cmp.lienFor(event('SEANCES_DEPLACEES', {}))).toMatchObject({commands: ['/medecin']});
  });

  it('explique au médecin le retard d\'observance en quantités et mène à l\'onglet Anémie', () => {
    TestBed.inject(TranslateService).setTranslation('fr', {
      NOTIFICATION: {
        OBSERVANCE_NON_RESPECTEE: {
          DOSE_RETARD_CONSTATE: '{{traitement}} {{debut}}→{{fin}} : {{administre}}/{{attendu}} {{unite}}, manque {{manque}}',
          COMPTE_RAPPEL_ECHEANCE: '{{traitement}} : {{manque}} administration(s) avant {{fin}}',
        },
      },
      DOSSIER_MEDICAL: {ALERTE_OBSERVANCE_EXPLICATION: {TRAITEMENT: {EPO: 'EPO'}}},
    }, true);
    roles = ['MEDECIN'];
    const retard = event('OBSERVANCE_NON_RESPECTEE', {
      patientId: 'p1', typeAlerte: 'RETARD_CONSTATE', typeTraitement: 'EPO', debut: '2026-09-28', fin: '2026-10-04',
      attendu: '8000', administre: '4000', unite: 'UI',
    });
    const rappel = event('OBSERVANCE_NON_RESPECTEE', {
      patientId: 'p1',
      typeAlerte: 'RAPPEL_ECHEANCE',
      typeTraitement: 'EPO',
      fin: '2026-10-11',
      attendu: '3',
      administre: '2',
    });

    expect(cmp.textFor(retard)).toBe('EPO 2026-09-28→2026-10-04 : 4000/8000 UI, manque 4000');
    expect(cmp.textFor(rappel)).toBe('EPO : 1 administration(s) avant 2026-10-11');
    expect(cmp.iconFor(retard)).toBe('vaccines');
    expect(cmp.iconClass(retard)).toBe('warning');
    expect(cmp.lienFor(retard)).toMatchObject({commands: ['/patients', 'p1', 'dossier-medical', 'anemie']});
  });

  it('garde le texte du serveur pour une alerte d\'observance antérieure au détail, sans lien pour un infirmier', () => {
    roles = ['INFIRMIER'];
    const ancienne = event('OBSERVANCE_NON_RESPECTEE', {patientId: 'p1', message: 'Retard constaté (ancien texte)'});

    expect(cmp.textFor(ancienne)).toBe('Retard constaté (ancien texte)');
    expect(cmp.lienFor(ancienne)).toBeNull();
  });

  it('affiche le type brut d\'un évènement inconnu', () => {
    const evt = event('AUTRE_CHOSE', {});
    expect(cmp.textFor(evt)).toBe('AUTRE_CHOSE');
    expect(cmp.iconFor(evt)).toBe('info');
  });
});
