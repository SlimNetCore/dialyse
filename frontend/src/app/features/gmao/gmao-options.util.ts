import {
  StatutEquipement,
  StatutIntervention,
  TypeEquipement,
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

export function statutEquipementTone(statut: StatutEquipement): GmaoBadgeTone {
  return STATUT_EQUIPEMENT_TONE[statut] ?? 'neutral';
}

export function statutInterventionTone(statut: StatutIntervention): GmaoBadgeTone {
  return STATUT_INTERVENTION_TONE[statut] ?? 'neutral';
}

/** Droit particulier : décider de la réforme d'un équipement (distinct de l'administration GMAO). */
export const ROLE_GMAO_REFORME = 'GMAO_REFORME';
