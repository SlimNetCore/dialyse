import {describe, expect, it} from 'vitest';
import {WsEvent} from '../ws/websocket.service';
import {ContexteNotification, dateLisible, presenterNotification} from './notification-presentation.util';

const event = (type: string, payload: Record<string, string> = {}): WsEvent =>
  ({type, centerId: 'c', payload, timestamp: '2026-10-05T07:00:00Z'}) as WsEvent;

/** Traduction factice : la clé, suivie de ses paramètres — ce que l'écran recevrait à traduire. */
const traduire = (cle: string, parametres?: Record<string, unknown>): string =>
  parametres ? `${cle} ${JSON.stringify(parametres)}` : cle;

const contexte = (...roles: string[]): ContexteNotification =>
  ({hasRole: (role) => roles.includes(role), traduire, aujourdhui: '2026-09-29'});

const vue = (evt: WsEvent, ...roles: string[]) => presenterNotification(evt, contexte(...roles));

/** Tous les évènements que le serveur publie vers la cloche. */
const TYPES = [
  'PATIENT_CREATED', 'PATIENT_UPDATED', 'PEC_VALIDATED', 'PEC_CLOSED', 'PEC_DELETED', 'ATTESTATION_CREATED',
  'ATTESTATION_DELETED', 'SEANCE_CREATED', 'SEANCE_VALIDATED', 'SEANCE_MEDICAL_SAVED', 'SEANCE_PARAMEDICAL_SAVED',
  'SEANCE_CONSOMMABLE_CHANGED', 'SEANCE_DEVERROUILLEE', 'SEANCE_SUPPRIMEE', 'SAISIE_INFIRMIER',
  'OBSERVANCE_NON_RESPECTEE', 'INFIRMIER_ABSENCE_DECLAREE', 'INFIRMIER_ABSENCE_ENREGISTREE',
  'INFIRMIER_SOUS_EFFECTIF', 'INFIRMIER_SUREFFECTIF', 'OPTIMISATION_PROPOSITION', 'GENERATEUR_INDISPONIBLE',
  'SEANCES_DEPLACEES', 'PATIENT_REPLACE_ISOLEMENT', 'ISOLEMENT_IMPOSSIBLE', 'ABSENCES_A_QUALIFIER',
  'SEANCES_A_REGULARISER', 'STOCK_MOVEMENT_CHANGED', 'STOCK_INVENTORY_CHANGED',
];

/** Écrans ouverts à l'infirmier seul et au médecin seul (mêmes règles que `role-scope.guard`). */
const ouvertAInfirmier = (chemin: string) =>
  ['/seances', '/seances/historique', '/seances/absences-patients', '/infirmiers/moi'].includes(chemin);
const ouvertAuMedecin = (chemin: string) =>
  ['/medecin', '/patients', '/seances/absences-patients'].includes(chemin)
  || /^\/patients\/[^/]+(\/(cahier|stats|dossier-medical)(\/.*)?)?$/.test(chemin);

describe('présentation des notifications', () => {
  it('donne à chaque évènement un libellé, un message, une icône et une action, quel que soit le profil', () => {
    for (const type of TYPES) {
      for (const roles of [['ADMIN'], ['SECRETAIRE'], ['MEDECIN'], ['INFIRMIER']]) {
        const v = vue(event(type, {patientId: 'p1', runId: 'r1', typeAlerte: 'RETARD_CONSTATE'}), ...roles);

        expect(v.titre, type).toMatch(/^NOTIFICATION\.TITRE\.[A-Z_]+$/);
        expect(v.titre, type).not.toBe('NOTIFICATION.TITRE.INCONNUE');
        expect(v.message.length, type).toBeGreaterThan(0);
        expect(v.message, type).not.toBe(type);
        expect(v.icone, type).not.toBe('info');
        expect(v.action.label, `${type} ${roles}`).toMatch(/^NOTIFICATION\.ACTION\.[A-Z_]+$/);
        expect(v.action.commands[0], `${type} ${roles}`).toMatch(/^\//);
      }
    }
  });

  it('ne mène jamais l\'infirmier seul ni le médecin seul vers un écran qui leur est fermé', () => {
    for (const type of TYPES) {
      const payload = {patientId: 'p1', runId: 'r1', typeAlerte: 'RETARD_CONSTATE', debut: '2026-10-19'};
      const infirmier = vue(event(type, payload), 'INFIRMIER').action.commands.join('/').replace('//', '/');
      const medecin = vue(event(type, payload), 'MEDECIN').action.commands.join('/').replace('//', '/');

      expect(ouvertAInfirmier(infirmier), `${type} → ${infirmier}`).toBe(true);
      expect(ouvertAuMedecin(medecin), `${type} → ${medecin}`).toBe(true);
    }
  });

  it('propose de chercher un remplaçant sur toute la période de l\'absence d\'un infirmier', () => {
    const absence = event('INFIRMIER_ABSENCE_ENREGISTREE', {
      infirmier: 'Amrani Sara',
      debut: '2026-10-19',
      fin: '2026-11-02'
    });
    const v = vue(absence, 'ADMIN');

    expect(v.titre).toBe('NOTIFICATION.TITRE.INFIRMIER_ABSENCE_ENREGISTREE');
    expect(v.message).toContain('"infirmier":"Amrani Sara","debut":"19/10/2026","fin":"02/11/2026"');
    expect(v.actionRequise).toBe(true);
    expect(v.ton).toBe('attention');
    expect(v.action).toEqual({
      label: 'NOTIFICATION.ACTION.COUVERTURE', commands: ['/seances/optimisation'],
      queryParams: {perimetre: 'COUVERTURE', debut: '2026-10-19', semaines: '3'},
    });

    // absence déjà commencée : la recherche part d'aujourd'hui
    const enCours = event('INFIRMIER_ABSENCE_DECLAREE', {infirmier: 'X', debut: '2026-09-21', fin: '2026-10-10'});
    expect(vue(enCours, 'SECRETAIRE').action.queryParams)
      .toEqual({perimetre: 'COUVERTURE', debut: '2026-09-29', semaines: '2'});
    expect(vue(event('INFIRMIER_SOUS_EFFECTIF', {nbCreneaux: '2', premiereDate: '2026-10-05'}), 'ADMIN').action)
      .toMatchObject({label: 'NOTIFICATION.ACTION.COUVERTURE', queryParams: {perimetre: 'COUVERTURE'}});
  });

  it('mène le médecin au planning de la semaine de l\'absence, faute de pouvoir lancer l\'optimisation', () => {
    const absence = event('INFIRMIER_ABSENCE_ENREGISTREE', {
      infirmier: 'Amrani Sara',
      debut: '2026-10-19',
      fin: '2026-11-02'
    });

    expect(vue(absence, 'MEDECIN').action).toEqual(
      {label: 'NOTIFICATION.ACTION.PLANNING', commands: ['/medecin'], queryParams: {date: '2026-10-19'}});
    expect(vue(event('GENERATEUR_INDISPONIBLE'), 'MEDECIN').action.commands).toEqual(['/medecin']);
    expect(vue(event('OPTIMISATION_PROPOSITION', {runId: 'r1'}), 'MEDECIN').action.commands).toEqual(['/medecin']);
  });

  it('ouvre l\'optimisation du bon périmètre, ou la proposition elle-même', () => {
    expect(vue(event('OPTIMISATION_PROPOSITION', {runId: 'r1', motif: 'MAINTENANCE', valeur: '2'}), 'ADMIN'))
      .toMatchObject({
        message: 'NOTIFICATION.OPTIMISATION_PROPOSITION.MAINTENANCE {"n":"2"}', ton: 'attention', actionRequise: true,
        action: {
          label: 'NOTIFICATION.ACTION.PROPOSITION',
          commands: ['/seances/optimisation'],
          queryParams: {run: 'r1'}
        },
      });
    expect(vue(event('OPTIMISATION_PROPOSITION', {motif: 'GAIN', valeur: '3'}), 'ADMIN')).toMatchObject({
      ton: 'succes', actionRequise: false,
      action: {label: 'NOTIFICATION.ACTION.OPTIMISATION', commands: ['/seances/optimisation'], queryParams: {}},
    });
    expect(vue(event('GENERATEUR_INDISPONIBLE', {
      generateur: 'A-G1', statut: 'HORS_SERVICE', nbPatients: '2',
      patients: 'BENALI Karim, KACI Lila'
    }), 'ADMIN')).toMatchObject({
      icone: 'build_circle', actionRequise: true,
      action: {label: 'NOTIFICATION.ACTION.MAINTENANCE', queryParams: {perimetre: 'MAINTENANCE'}},
    });
    expect(vue(event('INFIRMIER_SUREFFECTIF', {
      nbCreneaux: '2', nbVacations: '3', heures: '15',
      premiereDate: '2026-10-08'
    }), 'ADMIN')).toMatchObject({
      message: 'NOTIFICATION.INFIRMIER_SUREFFECTIF {"count":"2","date":"08/10/2026","vacations":"3","heures":"15"}',
      action: {label: 'NOTIFICATION.ACTION.ROULEMENT', queryParams: {perimetre: 'ROULEMENT'}},
    });
    expect(vue(event('ISOLEMENT_IMPOSSIBLE', {patientId: 'p1', patientNom: 'KACI Lila'}), 'ADMIN').action)
      .toEqual({
        label: 'NOTIFICATION.ACTION.REPLANIFIER', commands: ['/seances/optimisation'],
        queryParams: {perimetre: 'PATIENTS'}
      });
    expect(vue(event('ISOLEMENT_IMPOSSIBLE', {patientId: 'p1'}), 'MEDECIN').action.commands)
      .toEqual(['/patients', 'p1', 'dossier-medical', 'serologies']);
  });

  it('explique le retard d\'observance en quantités et mène à l\'onglet Anémie du patient', () => {
    const retard = event('OBSERVANCE_NON_RESPECTEE', {
      patientId: 'p1', typeAlerte: 'RETARD_CONSTATE', typeTraitement: 'EPO', debut: '2026-09-28', fin: '2026-10-04',
      attendu: '8000', administre: '4000', unite: 'UI',
    });
    const rappel = event('OBSERVANCE_NON_RESPECTEE',
      {
        patientId: 'p1',
        typeAlerte: 'RAPPEL_ECHEANCE',
        typeTraitement: 'EPO',
        fin: '2026-10-11',
        attendu: '3',
        administre: '2'
      });

    const v = vue(retard, 'MEDECIN');
    expect(v.titre).toBe('NOTIFICATION.TITRE.OBSERVANCE_RETARD');
    expect(v.message).toContain('NOTIFICATION.OBSERVANCE_NON_RESPECTEE.DOSE_RETARD_CONSTATE');
    expect(v.message).toContain('"attendu":8000,"administre":4000,"manque":4000,"unite":"UI"');
    expect(v.message).toContain('"debut":"28/09/2026","fin":"04/10/2026"');
    expect(v.action).toEqual({
      label: 'NOTIFICATION.ACTION.ANEMIE', commands: ['/patients', 'p1', 'dossier-medical', 'anemie'], queryParams: {},
    });
    expect(vue(rappel, 'MEDECIN')).toMatchObject({titre: 'NOTIFICATION.TITRE.OBSERVANCE_RAPPEL', icone: 'vaccines'});
    expect(vue(rappel, 'MEDECIN').message).toContain('COMPTE_RAPPEL_ECHEANCE');

    // alerte d'avant le détail : texte d'origine du serveur
    const ancienne = event('OBSERVANCE_NON_RESPECTEE', {patientId: 'p1', message: 'Retard constaté (ancien texte)'});
    expect(vue(ancienne, 'MEDECIN').message).toBe('Retard constaté (ancien texte)');
  });

  it('mène le médecin au bon écran pour chaque saisie d\'un infirmier', () => {
    const saisie = (type: string) => vue(event('SAISIE_INFIRMIER', {
      saisie: type, auteur: 'inf-01', patientId: 'p1', patientNom: 'Dupont', patientPrenom: 'Jean', date: '2026-10-03',
    }), 'MEDECIN');

    expect(saisie('PARAMEDICAL')).toMatchObject({
      titre: 'NOTIFICATION.TITRE.SAISIE_INFIRMIER',
      message: 'NOTIFICATION.SAISIE.PARAMEDICAL {"nom":"Dupont Jean","auteur":"inf-01","date":"03/10/2026"}',
      action: {label: 'NOTIFICATION.ACTION.CAHIER', commands: ['/patients', 'p1', 'cahier']},
    });
    expect(saisie('ANEMIE').action.commands).toEqual(['/patients', 'p1', 'dossier-medical', 'anemie']);
    expect(saisie('ABSENCE').action).toMatchObject(
      {label: 'NOTIFICATION.ACTION.ABSENCES_PATIENTS', commands: ['/seances/absences-patients']});
  });

  it('mène aux écrans de traitement : absences à qualifier, séances à régulariser, poste infirmier', () => {
    expect(vue(event('ABSENCES_A_QUALIFIER', {nbAQualifier: '4', nbEnRetard: '1'}), 'INFIRMIER')).toMatchObject({
      message: 'NOTIFICATION.ABSENCES_A_QUALIFIER {"count":"4","late":"1"}', actionRequise: true,
      action: {label: 'NOTIFICATION.ACTION.ABSENCES_PATIENTS', commands: ['/seances/absences-patients']},
    });
    expect(vue(event('SEANCES_A_REGULARISER', {nbSeances: '3', plusAncienne: '2026-09-28'}), 'ADMIN')).toMatchObject({
      message: 'NOTIFICATION.SEANCES_A_REGULARISER {"count":"3","date":"28/09/2026"}', icone: 'pending_actions',
      action: {label: 'NOTIFICATION.ACTION.REGULARISER', commands: ['/seances']},
    });
    expect(vue(event('SEANCE_DEVERROUILLEE', {patientNom: 'Dupont', patientPrenom: 'Jean', dateSeance: '2026-10-03'}),
      'INFIRMIER')).toMatchObject({
      message: 'NOTIFICATION.SEANCE_DEVERROUILLEE {"nom":"Dupont Jean","date":"03/10/2026"}', actionRequise: true,
      action: {label: 'NOTIFICATION.ACTION.POSTE_INFIRMIER', commands: ['/seances']},
    });
    expect(vue(event('SEANCE_SUPPRIMEE', {dateSeance: '2026-10-05'}), 'SECRETAIRE').action)
      .toMatchObject({label: 'NOTIFICATION.ACTION.HISTORIQUE', commands: ['/seances/historique']});
  });

  it('mène à la fiche du patient, aux prises en charge et aux attestations', () => {
    expect(vue(event('PATIENT_CREATED', {patientId: 'p1', nom: 'Kaci', prenom: 'Lila'}), 'SECRETAIRE')).toMatchObject({
      message: 'NOTIFICATION.PATIENT_CREATED {"nom":"Kaci Lila"}',
      action: {label: 'NOTIFICATION.ACTION.FICHE_PATIENT', commands: ['/patients', 'p1']},
    });
    expect(vue(event('PATIENT_UPDATED', {}), 'ADMIN').action.commands).toEqual(['/patients']);
    expect(vue(event('PEC_VALIDATED', {patientNom: 'Kaci'}), 'ADMIN')).toMatchObject({
      ton: 'succes', action: {label: 'NOTIFICATION.ACTION.PEC', commands: ['/patients/pec-list']},
    });
    expect(vue(event('PEC_CLOSED', {patientId: 'p1'}), 'MEDECIN').action.commands).toEqual(['/patients', 'p1']);
    expect(vue(event('ATTESTATION_CREATED', {}), 'SECRETAIRE').action.commands).toEqual(['/patients/attestations-list']);
    expect(vue(event('PATIENT_REPLACE_ISOLEMENT', {patientNom: 'Kaci', salle: 'Isolement 1'}), 'ADMIN').action)
      .toMatchObject({label: 'NOTIFICATION.ACTION.PLACEMENT', commands: ['/seances/planning']});
  });

  it('annonce l\'ouverture d\'un inventaire et mène la gestion aux inventaires', () => {
    const ouvert = event('STOCK_INVENTORY_CHANGED',
      {statut: 'EN_COURS', reference: 'INV-2026-0001', dateInventaire: '2026-10-05', par: 'pharmacien'});

    expect(vue(ouvert, 'ADMIN')).toMatchObject({
      titre: 'NOTIFICATION.TITRE.STOCK_INVENTORY_CHANGED',
      message: 'NOTIFICATION.STOCK_INVENTORY_CHANGED.EN_COURS {"reference":"INV-2026-0001","date":"05/10/2026","par":"pharmacien"}',
      action: {label: 'NOTIFICATION.ACTION.INVENTAIRES', commands: ['/stock/inventaires']},
    });
    expect(vue(event('STOCK_INVENTORY_CHANGED', {statut: 'AUTRE'}), 'ADMIN').message)
      .toContain('NOTIFICATION.STOCK_INVENTORY_CHANGED.DEFAULT');
    expect(vue(event('STOCK_MOVEMENT_CHANGED', {reference: 'BR-1', articles: '3'}), 'SECRETAIRE')).toMatchObject({
      message: 'NOTIFICATION.STOCK_MOVEMENT_CHANGED {"reference":"BR-1","count":"3"}',
      action: {label: 'NOTIFICATION.ACTION.STOCK', commands: ['/stock']},
    });
  });

  it('présente un évènement inconnu sans code technique en titre, avec l\'accueil pour action', () => {
    expect(vue(event('AUTRE_CHOSE'), 'ADMIN')).toEqual({
      titre: 'NOTIFICATION.TITRE.INCONNUE', message: 'AUTRE_CHOSE', icone: 'info', ton: 'info', actionRequise: false,
      action: {label: 'NOTIFICATION.ACTION.ACCUEIL', commands: ['/dashboard'], queryParams: {}},
    });
  });

  it('affiche les dates comme on les lit', () => {
    expect(dateLisible('2026-10-19')).toBe('19/10/2026');
    expect(dateLisible('2026-10-19T08:00:00Z')).toBe('19/10/2026');
    expect(dateLisible('')).toBe('');
    expect(dateLisible(undefined)).toBe('');
    expect(dateLisible('demain')).toBe('demain');
  });
});
