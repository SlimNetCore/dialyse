import {WsEvent} from '../ws/websocket.service';
import {horizonPourAbsence} from '../../features/planning/optimisation/horizon-absence.util';

/**
 * Présentation d'une notification : pour chaque évènement, un libellé court, un message complet, une icône, un
 * niveau d'attention et **une action** — l'écran où traiter ou consulter ce dont elle parle, selon le profil de
 * l'utilisateur. Fonctions pures : la traduction et les rôles sont fournis par l'appelant.
 */

export type Traduire = (cle: string, parametres?: Record<string, unknown>) => string;

export type NotificationAction = {
  /** Clé de traduction du bouton. */
  label: string;
  commands: string[];
  queryParams: Record<string, string>;
};

export type TonNotification = 'info' | 'succes' | 'attention';

export type NotificationVue = {
  titre: string;
  message: string;
  icone: string;
  ton: TonNotification;
  /** L'utilisateur a quelque chose à faire (remplacement, régularisation, replanification…). */
  actionRequise: boolean;
  action: NotificationAction;
};

export type ContexteNotification = {
  hasRole: (role: string) => boolean;
  traduire: Traduire;
  /** Date du jour (ISO), pour borner la période d'une absence déjà commencée. */
  aujourdhui: string;
};

/** Zone de l'application ouverte à l'utilisateur : gestion du centre, médecin seul, infirmier seul. */
type Profil = 'GESTION' | 'MEDECIN' | 'INFIRMIER';

const ISO = /^(\d{4})-(\d{2})-(\d{2})/;
/** États d'un inventaire qui ont leur propre message. */
const STATUTS_INVENTAIRE = ['EN_COURS', 'CLOTURE', 'ANNULE'];

/** Date ISO (`2026-10-19`) affichée comme on la lit (`19/10/2026`) ; toute autre valeur est rendue telle quelle. */
export function dateLisible(valeur: string | undefined | null): string {
  const m = ISO.exec(valeur ?? '');
  return m ? `${m[3]}/${m[2]}/${m[1]}` : (valeur ?? '');
}

function profilDe(hasRole: (role: string) => boolean): Profil {
  if (hasRole('ADMIN') || hasRole('SECRETAIRE')) return 'GESTION';
  if (hasRole('MEDECIN')) return 'MEDECIN';
  if (hasRole('INFIRMIER')) return 'INFIRMIER';
  return 'GESTION';
}

const action = (label: string, commands: string[], queryParams: Record<string, string> = {}): NotificationAction =>
  ({label: `NOTIFICATION.ACTION.${label}`, commands, queryParams});

/** Écran d'accueil du profil : l'action de repli quand l'écran idéal ne lui est pas ouvert. */
function accueil(profil: Profil): NotificationAction {
  switch (profil) {
    case 'MEDECIN':
      return action('PLANNING', ['/medecin']);
    case 'INFIRMIER':
      return action('POSTE_INFIRMIER', ['/seances']);
    default:
      return action('ACCUEIL', ['/dashboard']);
  }
}

/** Fiche du patient (lecture seule pour le médecin) ; l'infirmier seul n'y a pas accès. */
function fichePatient(profil: Profil, patientId: string | undefined): NotificationAction {
  if (profil === 'INFIRMIER') return accueil(profil);
  return patientId ? action('FICHE_PATIENT', ['/patients', patientId]) : action('LISTE_PATIENTS', ['/patients']);
}

/** Le poste infirmier, où la séance se saisit ; le médecin seul consulte le cahier de dialyse du patient. */
function seance(profil: Profil, patientId: string | undefined): NotificationAction {
  if (profil !== 'MEDECIN') return action('POSTE_INFIRMIER', ['/seances']);
  return patientId ? action('CAHIER', ['/patients', patientId, 'cahier']) : accueil(profil);
}

/** Le planning : celui des séances pour la gestion, celui du jour pour le médecin. */
function planning(profil: Profil, date?: string | null): NotificationAction {
  if (profil === 'GESTION') return action('PLANNING', ['/seances/planning'], date ? {date} : {});
  if (profil === 'MEDECIN') return action('PLANNING', ['/medecin'], date ? {date} : {});
  return accueil(profil);
}

/** L'optimisation d'un périmètre, pour qui peut la lancer ; sinon le planning. */
function optimisation(profil: Profil, label: string, queryParams: Record<string, string>): NotificationAction {
  return profil === 'GESTION' ? action(label, ['/seances/optimisation'], queryParams) : planning(profil);
}

/** Recherche de remplaçants sur toute la période de l'absence, pas seulement la semaine en cours. */
function couverture(profil: Profil, evt: WsEvent, aujourdhui: string): NotificationAction {
  const debut = evt.payload['debut'] ?? evt.payload['premiereDate'] ?? null;
  if (profil !== 'GESTION') return planning(profil, debut);
  const horizon = horizonPourAbsence(debut, evt.payload['fin'] ?? null, aujourdhui);
  return action('COUVERTURE', ['/seances/optimisation'], horizon
    ? {perimetre: 'COUVERTURE', debut: horizon.debut, semaines: String(horizon.semaines)}
    : debut ? {perimetre: 'COUVERTURE', debut} : {perimetre: 'COUVERTURE'});
}

const nomComplet = (nom: string | undefined, prenom: string | undefined): string =>
  `${nom ?? ''} ${prenom ?? ''}`.trim();

type Fiche = Omit<NotificationVue, 'titre' | 'message'> & {
  /** Suffixe de `NOTIFICATION.TITRE.` ; par défaut le type de l'évènement. */
  titre?: string;
  /** Clé complète du message et ses paramètres ; `texte` court-circuite la traduction. */
  message?: string;
  parametres?: Record<string, unknown>;
  texte?: string;
};

const info = (icone: string, act: NotificationAction, reste: Partial<Fiche> = {}): Fiche =>
  ({icone, ton: 'info', actionRequise: false, action: act, ...reste});
const succes = (icone: string, act: NotificationAction, reste: Partial<Fiche> = {}): Fiche =>
  ({icone, ton: 'succes', actionRequise: false, action: act, ...reste});
/** Alerte : quelque chose est à faire. */
const alerte = (icone: string, act: NotificationAction, reste: Partial<Fiche> = {}): Fiche =>
  ({icone, ton: 'attention', actionRequise: true, action: act, ...reste});

function ficheDe(evt: WsEvent, profil: Profil, ctx: ContexteNotification): Fiche {
  const p = evt.payload;
  const patientId = p['patientId'] || undefined;
  const patientSeance = {nom: nomComplet(p['patientNom'], p['patientPrenom']), date: dateLisible(p['dateSeance'])};

  switch (evt.type) {
    case 'PATIENT_CREATED':
    case 'PATIENT_UPDATED':
      return info(evt.type === 'PATIENT_CREATED' ? 'person_add' : 'manage_accounts', fichePatient(profil, patientId),
        {parametres: {nom: nomComplet(p['nom'], p['prenom'])}});

    case 'PEC_VALIDATED':
    case 'PEC_CLOSED':
    case 'PEC_DELETED': {
      const pec = profil === 'GESTION' ? action('PEC', ['/patients/pec-list']) : fichePatient(profil, patientId);
      const fiche = {parametres: {nom: p['patientNom'] ?? ''}};
      return evt.type === 'PEC_VALIDATED' ? succes('verified', pec, fiche)
        : info(evt.type === 'PEC_CLOSED' ? 'event_busy' : 'delete', pec, fiche);
    }

    case 'ATTESTATION_CREATED':
    case 'ATTESTATION_DELETED':
      return info(evt.type === 'ATTESTATION_CREATED' ? 'badge' : 'delete', profil === 'GESTION'
        ? action('ATTESTATIONS', ['/patients/attestations-list']) : fichePatient(profil, patientId));

    case 'SEANCE_CREATED':
      return info('event_available', seance(profil, patientId), {parametres: patientSeance});
    case 'SEANCE_VALIDATED':
      return succes('task_alt', seance(profil, patientId), {parametres: patientSeance});
    case 'SEANCE_MEDICAL_SAVED':
      return info('stethoscope', seance(profil, patientId), {parametres: patientSeance});
    case 'SEANCE_PARAMEDICAL_SAVED':
      return info('monitor_heart', seance(profil, patientId), {parametres: patientSeance});
    case 'SEANCE_CONSOMMABLE_CHANGED':
      return info('inventory_2', seance(profil, patientId));
    case 'SEANCE_DEVERROUILLEE':
      return alerte('lock_open', seance(profil, patientId), {parametres: patientSeance});
    case 'SEANCE_SUPPRIMEE':
      return info('delete', profil === 'MEDECIN' ? accueil(profil) : action('HISTORIQUE', ['/seances/historique']),
        {parametres: {date: dateLisible(p['dateSeance'])}});

    case 'SAISIE_INFIRMIER': {
      const saisie = p['saisie'] ?? 'DEFAULT';
      const cible = saisie === 'ABSENCE' ? action('ABSENCES_PATIENTS', ['/seances/absences-patients'])
        : saisie === 'ANEMIE' && patientId && profil !== 'INFIRMIER'
          ? action('ANEMIE', ['/patients', patientId, 'dossier-medical', 'anemie'])
          : patientId && profil !== 'INFIRMIER' ? action('CAHIER', ['/patients', patientId, 'cahier'])
            : accueil(profil);
      return info('medical_services', cible, {
        message: `NOTIFICATION.SAISIE.${saisie}`,
        parametres: {nom: patientSeance.nom, auteur: p['auteur'] ?? '', date: dateLisible(p['date'])},
      });
    }

    case 'OBSERVANCE_NON_RESPECTEE': {
      const cible = patientId && profil !== 'INFIRMIER'
        ? action('ANEMIE', ['/patients', patientId, 'dossier-medical', 'anemie']) : accueil(profil);
      const nature = p['typeAlerte'];
      // alertes d'avant le détail : texte d'origine du serveur
      if (!nature) return alerte('vaccines', cible, {titre: 'OBSERVANCE_RETARD', texte: p['message'] || evt.type});
      const unite = p['unite'] ?? '';
      const attendu = Number(p['attendu'] ?? 0);
      const administre = Number(p['administre'] ?? 0);
      return alerte('vaccines', cible, {
        titre: nature === 'RAPPEL_ECHEANCE' ? 'OBSERVANCE_RAPPEL' : 'OBSERVANCE_RETARD',
        message: `NOTIFICATION.OBSERVANCE_NON_RESPECTEE.${unite ? 'DOSE' : 'COMPTE'}_${nature}`,
        parametres: {
          traitement: ctx.traduire(
            `DOSSIER_MEDICAL.ALERTE_OBSERVANCE_EXPLICATION.TRAITEMENT.${p['typeTraitement'] ?? 'EPO'}`),
          attendu, administre, manque: Math.max(0, attendu - administre), unite,
          debut: dateLisible(p['debut']), fin: dateLisible(p['fin']),
        },
      });
    }

    case 'INFIRMIER_ABSENCE_DECLAREE':
    case 'INFIRMIER_ABSENCE_ENREGISTREE':
      return alerte('event_busy', couverture(profil, evt, ctx.aujourdhui), {
        parametres: {infirmier: p['infirmier'] ?? '', debut: dateLisible(p['debut']), fin: dateLisible(p['fin'])},
      });
    case 'INFIRMIER_SOUS_EFFECTIF':
      return alerte('groups', couverture(profil, evt, ctx.aujourdhui),
        {parametres: {count: p['nbCreneaux'] ?? '', date: dateLisible(p['premiereDate'])}});
    case 'INFIRMIER_SUREFFECTIF':
      return alerte('person_off', optimisation(profil, 'ROULEMENT', {perimetre: 'ROULEMENT'}), {
        parametres: {
          count: p['nbCreneaux'] ?? '0', date: dateLisible(p['premiereDate']), vacations: p['nbVacations'] ?? '0',
          heures: p['heures'] ?? '0',
        },
      });

    case 'OPTIMISATION_PROPOSITION': {
      const motif = p['motif'] ?? 'GAIN';
      const cible = optimisation(profil, p['runId'] ? 'PROPOSITION' : 'OPTIMISATION', p['runId'] ? {run: p['runId']} : {});
      const fiche = {message: `NOTIFICATION.OPTIMISATION_PROPOSITION.${motif}`, parametres: {n: p['valeur'] ?? ''}};
      return motif === 'GAIN' ? succes('auto_fix_high', cible, fiche) : alerte('auto_fix_high', cible, fiche);
    }

    case 'GENERATEUR_INDISPONIBLE':
      return alerte('build_circle', optimisation(profil, 'MAINTENANCE', {perimetre: 'MAINTENANCE'}), {
        parametres: {
          generateur: p['generateur'] ?? '', statut: ctx.traduire(`GMAO.STATUT_EQUIPEMENT.${p['statut'] ?? ''}`),
          count: p['nbPatients'] ?? '0', patients: p['patients'] ?? '',
        },
      });
    case 'SEANCES_DEPLACEES':
      return info('swap_horiz', planning(profil),
        {parametres: {patients: p['nbPatients'] ?? '0', temporaires: p['nbSeancesTemporaires'] ?? '0'}});

    case 'PATIENT_REPLACE_ISOLEMENT':
      return info('masks', profil === 'GESTION' ? action('PLACEMENT', ['/seances/planning']) : planning(profil),
        {parametres: {nom: p['patientNom'] ?? '', salle: p['salle'] ?? ''}});
    case 'ISOLEMENT_IMPOSSIBLE':
      return alerte('masks', profil === 'GESTION'
          ? action('REPLANIFIER', ['/seances/optimisation'], {perimetre: 'PATIENTS'})
          : profil === 'MEDECIN' && patientId
            ? action('SEROLOGIES', ['/patients', patientId, 'dossier-medical', 'serologies']) : accueil(profil),
        {parametres: {nom: p['patientNom'] ?? ''}});

    case 'ABSENCES_A_QUALIFIER':
      return alerte('event_busy', action('ABSENCES_PATIENTS', ['/seances/absences-patients']),
        {parametres: {count: p['nbAQualifier'] ?? '', late: p['nbEnRetard'] ?? ''}});
    case 'SEANCES_A_REGULARISER':
      return alerte('pending_actions', profil === 'MEDECIN' ? accueil(profil) : action('REGULARISER', ['/seances']),
        {parametres: {count: p['nbSeances'] ?? '', date: dateLisible(p['plusAncienne'])}});

    case 'STOCK_MOVEMENT_CHANGED':
      return info('inventory', profil === 'GESTION' ? action('STOCK', ['/stock']) : accueil(profil),
        {parametres: {reference: p['reference'] ?? '', count: p['articles'] ?? '0'}});
    case 'STOCK_INVENTORY_CHANGED': {
      const statut = STATUTS_INVENTAIRE.includes(p['statut'] ?? '') ? p['statut'] : 'DEFAULT';
      return info('fact_check', profil === 'GESTION' ? action('INVENTAIRES', ['/stock/inventaires']) : accueil(profil), {
        message: `NOTIFICATION.STOCK_INVENTORY_CHANGED.${statut}`,
        parametres: {reference: p['reference'] ?? '', date: dateLisible(p['dateInventaire']), par: p['par'] ?? ''},
      });
    }

    default:
      return info('info', accueil(profil), {titre: 'INCONNUE', texte: evt.type});
  }
}

/** Libellé, message, icône, niveau d'attention et action d'un évènement, pour le profil de l'utilisateur. */
export function presenterNotification(evt: WsEvent, ctx: ContexteNotification): NotificationVue {
  const {titre, message, parametres, texte, ...vue} = ficheDe(evt, profilDe(ctx.hasRole), ctx);
  return {
    ...vue,
    titre: ctx.traduire(`NOTIFICATION.TITRE.${titre ?? evt.type}`),
    message: texte ?? ctx.traduire(message ?? `NOTIFICATION.${evt.type}`, parametres),
  };
}
