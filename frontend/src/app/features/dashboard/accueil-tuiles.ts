import {isSecretaryOnly, MODULES_FERMES_A_LA_SECRETAIRE} from '../../core/auth/role-scope.guard';

/** Modules présentés sur l'Accueil : ceux qui ont un tableau de bord (ni Comptabilité, ni Administration, ni Planning). */
export type AccueilModule = 'patients' | 'seances' | 'infirmiers' | 'stock' | 'gmao' | 'facturation' | 'reglement';

/** Libellé (clé i18n) et format d'un chiffre clé d'une tuile. */
export type ChiffreCleDefinition = {
  label: string;
  params?: Record<string, unknown>;
  format: 'nombre' | 'montant';
};

/** Tuile de l'Accueil : un module, son tableau de bord et ses 1 ou 2 chiffres clés. */
export type AccueilTuile = {
  module: AccueilModule;
  /** Tableau de bord du module (même route que la première entrée de sa barre de module). */
  route: string;
  label: string;
  icon: string;
  /** Rôle exigé, comme l'entrée « Tableau de bord » de la barre de module (même règle que le menu). */
  role?: string;
  chiffres: ChiffreCleDefinition[];
};

/** Horizon (jours) des PEC expirantes affichées sur la tuile Patients, défaut du tableau de bord Patients. */
export const ECHEANCE_PEC_JOURS = 30;
/** Horizon (jours) de la couverture infirmière, celui du tableau de bord Infirmiers. */
export const HORIZON_COUVERTURE_JOURS = 7;

/**
 * Tuiles dans l'ordre du menu des modules. L'Accueil n'est ouvert qu'aux profils de centre non restreints (ADMIN,
 * SECRETAIRE, cumuls de rôles) : les règles du menu qui s'y appliquent sont le rôle exigé ({@link AccueilTuile.role})
 * et le retrait du stock et de la GMAO pour la secrétaire seule. Si le serveur refuse malgré tout une lecture (403),
 * la tuile est masquée par le magasin de l'Accueil.
 */
export const ACCUEIL_TUILES: ReadonlyArray<AccueilTuile> = [
  {
    module: 'patients', route: '/patients/tableau-de-bord', label: 'NAV.PATIENTS', icon: 'people',
    chiffres: [
      {label: 'DASHBOARD.PATIENTS', format: 'nombre'},
      {label: 'DASHBOARD.PEC_EXPIRING', params: {days: ECHEANCE_PEC_JOURS}, format: 'nombre'},
    ],
  },
  {
    module: 'seances', route: '/seances/tableau-de-bord', label: 'NAV.SEANCES', icon: 'event_note',
    chiffres: [
      {label: 'ACCUEIL.SEANCES_MOIS', format: 'nombre'},
      {label: 'ACCUEIL.ABSENCES_MOIS', format: 'nombre'},
    ],
  },
  {
    module: 'infirmiers', route: '/infirmiers/tableau-de-bord', label: 'NAV.INFIRMIERS', icon: 'medical_services',
    chiffres: [{label: 'ACCUEIL.CRENEAUX_SOUS_EFFECTIF', params: {days: HORIZON_COUVERTURE_JOURS}, format: 'nombre'}],
  },
  {
    module: 'facturation', route: '/facturation/tableau-de-bord', label: 'NAV.FACTURATION', icon: 'receipt',
    role: 'ADMIN',
    chiffres: [
      {label: 'ACCUEIL.CA_TTC_MOIS', format: 'montant'},
      {label: 'ACCUEIL.FACTURES_MOIS', format: 'nombre'},
    ],
  },
  {
    module: 'reglement', route: '/reglement/tableau-de-bord', label: 'NAV.REGLEMENT', icon: 'payments',
    chiffres: [
      {label: 'ACCUEIL.FACTURES_NON_REGLEES', format: 'nombre'},
      {label: 'ACCUEIL.RESTE_A_ENCAISSER', format: 'montant'},
    ],
  },
  {
    module: 'stock', route: '/stock/tableau-de-bord', label: 'NAV.STOCK', icon: 'inventory_2',
    chiffres: [
      {label: 'ACCUEIL.ALERTES_STOCK', format: 'nombre'},
      {label: 'ACCUEIL.LOTS_PEREMPTION', format: 'nombre'},
    ],
  },
  {
    module: 'gmao', route: '/gmao/tableau-de-bord', label: 'NAV.GMAO', icon: 'build_circle',
    chiffres: [
      {label: 'ACCUEIL.INTERVENTIONS_EN_COURS', format: 'nombre'},
      {label: 'ACCUEIL.INTERVENTIONS_A_RELANCER', format: 'nombre'},
    ],
  },
];

/** Délai (ms) de regroupement des évènements temps réel avant de recharger les tuiles concernées. */
export const DELAI_RAFRAICHISSEMENT_MS = 300;

/**
 * Tuile dont les chiffres changent avec un évènement temps réel du centre, ou null si aucune n'est concernée. Le scan
 * d'un patient valide une séance ; un replacement en isolement ne change aucun chiffre de l'Accueil.
 */
export function moduleRafraichiPar(type: string): AccueilModule | null {
  if (type.startsWith('SEANCE_') || type === 'PATIENT_SCANNED') return 'seances';
  if (type === 'PATIENT_REPLACE_ISOLEMENT') return null;
  if (type.startsWith('PATIENT_') || type.startsWith('PEC_')) return 'patients';
  if (type.startsWith('STOCK_') || type === 'LOTS_PROCHES_PEREMPTION') return 'stock';
  if (type.startsWith('INFIRMIER_')) return 'infirmiers';
  if (type === 'GENERATEUR_INDISPONIBLE' || type === 'REFORME_PROPOSEE') return 'gmao';
  return null;
}

/**
 * Tuiles visibles pour un profil : même règle que le menu (rôle exigé par l'entrée « Tableau de bord », modules
 * retirés du menu de la secrétaire seule).
 */
export function tuilesVisibles(hasRole: (role: string) => boolean, roles: readonly string[] = []): AccueilTuile[] {
  const secretaireSeule = isSecretaryOnly(roles);
  return ACCUEIL_TUILES
    .filter((tuile) => !tuile.role || hasRole(tuile.role))
    .filter((tuile) => !secretaireSeule || !MODULES_FERMES_A_LA_SECRETAIRE.includes(tuile.module));
}
