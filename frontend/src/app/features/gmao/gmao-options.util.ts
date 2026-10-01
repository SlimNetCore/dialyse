import {
  PrioriteIntervention,
  StatutEquipement,
  StatutIntervention,
  TypeEquipement,
  TypeDocumentIntervention,
  TypeIntervention,
} from '../../core/api/gmao-api.service';

/**
 * Valeurs d'énumération GMAO et aides d'affichage.
 * Les libellés ne sont jamais codés en dur ici : chaque valeur se résout via la clé i18n
 * `GMAO.EQUIPEMENT_TYPE.<valeur>` / `GMAO.STATUT_EQUIPEMENT.<valeur>` / etc. (AGENTS.md §19).
 */
export const TYPES_EQUIPEMENT: TypeEquipement[] = [
  'GENERATEUR_DIALYSE', 'STATION_TRAITEMENT_EAU', 'RO_REVERSE_OSMOSIS',
  'ULTRAFILTRE', 'CHARBON_ACTIF', 'ADOUCISSEUR', 'DESINFECTANT_CHIMIQUE',
  'FILTRE_PARTICULES', 'POMPE_EAU', 'COMPRESSEUR_AIR', 'ALARME_SURVEILLANCE', 'AUTRE',
];

export const STATUTS_EQUIPEMENT: StatutEquipement[] = [
  'EN_SERVICE', 'EN_MAINTENANCE', 'EN_ATTENTE_PIECE', 'HORS_SERVICE', 'DESACTIF', 'A_REFORMER', 'REFORME',
];

/** États saisissables à la création d'une intervention (état de l'équipement au moment de l'intervention). */
export const ETATS_AVANT_INTERVENTION: StatutEquipement[] = [
  'EN_SERVICE', 'EN_MAINTENANCE', 'EN_ATTENTE_PIECE', 'HORS_SERVICE',
];

/** États saisissables à la clôture : « À réformer » est une proposition, la décision revient à la personne habilitée. */
export const ETATS_APRES_INTERVENTION: StatutEquipement[] = [
  'EN_SERVICE', 'EN_ATTENTE_PIECE', 'HORS_SERVICE', 'A_REFORMER',
];

/** Statuts rendant un équipement indisponible pour l'affectation d'un patient (sécurité patient). */
export const STATUTS_INDISPONIBLES_PATIENT: StatutEquipement[] = [
  'EN_MAINTENANCE', 'EN_ATTENTE_PIECE', 'HORS_SERVICE', 'A_REFORMER', 'REFORME',
];

export const TYPES_INTERVENTION: TypeIntervention[] = [
  'PREVENTIVE', 'CURATIVE', 'URGENTE', 'CONTROLE', 'INSTALLATION',
  'DEINSTALLATION', 'REMPLACEMENT_PIECE', 'REVISION_COMPLETE',
];

export const PRIORITES_INTERVENTION: PrioriteIntervention[] = ['NORMALE', 'HAUTE', 'URGENTE'];

export const TYPES_DOCUMENT_INTERVENTION: TypeDocumentIntervention[] = ['BON_INTERVENTION', 'FACTURE', 'PHOTO', 'AUTRE'];

export const STATUTS_INTERVENTION: StatutIntervention[] = [
  'PLANIFIEE', 'EN_COURS', 'TERMINEE', 'ANNULEE', 'EN_ATTENTE_VALIDATION',
];

/** Variante de couleur de pastille (classes définies dans gmao-shared.css). */
export type GmaoBadgeTone = 'success' | 'warning' | 'info' | 'danger' | 'neutral';

const STATUT_EQUIPEMENT_TONE: Record<StatutEquipement, GmaoBadgeTone> = {
  EN_SERVICE: 'success',
  EN_MAINTENANCE: 'warning',
  EN_ATTENTE_PIECE: 'info',
  HORS_SERVICE: 'danger',
  DESACTIF: 'neutral',
  A_REFORMER: 'danger',
  REFORME: 'neutral',
};

const STATUT_INTERVENTION_TONE: Record<StatutIntervention, GmaoBadgeTone> = {
  PLANIFIEE: 'info',
  EN_COURS: 'warning',
  TERMINEE: 'success',
  ANNULEE: 'danger',
  EN_ATTENTE_VALIDATION: 'warning',
};

const PRIORITE_TONE: Record<PrioriteIntervention, GmaoBadgeTone> = {
  NORMALE: 'neutral',
  HAUTE: 'warning',
  URGENTE: 'danger',
};

export function prioriteTone(priorite: PrioriteIntervention): GmaoBadgeTone {
  return PRIORITE_TONE[priorite] ?? 'neutral';
}

export function statutEquipementTone(statut: StatutEquipement): GmaoBadgeTone {
  return STATUT_EQUIPEMENT_TONE[statut] ?? 'neutral';
}

export function statutInterventionTone(statut: StatutIntervention): GmaoBadgeTone {
  return STATUT_INTERVENTION_TONE[statut] ?? 'neutral';
}

/** Droit particulier : décider de la réforme d'un équipement (distinct de l'administration GMAO). */
export const ROLE_GMAO_REFORME = 'GMAO_REFORME';

/** Durée entre deux dates ISO en minutes (0 si l'une manque ou si la fin précède le début). */
export function dureeMinutes(debut: string | null | undefined, fin: string | null | undefined): number {
  if (!debut || !fin) return 0;
  const ms = new Date(fin).getTime() - new Date(debut).getTime();
  return Number.isFinite(ms) && ms > 0 ? Math.round(ms / 60000) : 0;
}

/** Durée lisible « 2 h 05 » / « 45 min » (chaîne vide si inconnue). */
export function dureeLabel(minutes: number): string {
  if (minutes <= 0) return '';
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  if (h === 0) return `${m} min`;
  return `${h} h ${String(m).padStart(2, '0')}`;
}

/** Valeur initiale d'un champ datetime-local : maintenant, à la minute, en heure locale. */
export function nowDatetimeLocal(): string {
  const d = new Date();
  d.setSeconds(0, 0);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/**
 * Convention : le serveur stocke et renvoie des instants UTC (« …Z »), le navigateur affiche en heure locale.
 * Convertit la saisie d'un `datetime-local` (heure locale du navigateur) en instant ISO UTC pour l'API.
 */
export function localInputToUtcIso(value: string): string {
  return new Date(value).toISOString();
}

/** Convertit une date saisie (`yyyy-MM-dd`, jour local) en instant ISO UTC (minuit local). */
export function localDateToUtcIso(value: string): string {
  return new Date(`${value}T00:00`).toISOString();
}

/** Instant ISO UTC → valeur `yyyy-MM-dd` du jour local (préremplissage d'un champ date). */
export function utcIsoToLocalDate(iso: string): string {
  const d = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/** Fuseau IANA du navigateur (ex. « Africa/Algiers »), UTC si indisponible. */
export function browserTimeZone(): string {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
  } catch {
    return 'UTC';
  }
}
