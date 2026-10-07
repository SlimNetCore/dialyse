import {MotifAbsence, StatutAbsence} from '../../core/api/absence-patient-api.service';

export type ActionAbsence = 'qualifier' | 'rattrapage' | 'annuler';

/** Date du jour au format ISO (calendrier local). */
export function todayIso(now: Date = new Date()): string {
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
}

/** Une absence rattrapée ou annulée n'est plus modifiable. */
/**
 * Rôles qui peuvent déclarer l'absence d'un patient : administrateur, secrétariat et infirmier. Le médecin consulte
 * (planning en lecture seule) mais ne déclare pas ; le serveur applique la même règle.
 */
export const ROLES_DECLARANT_ABSENCE: readonly string[] = ['ADMIN', 'SECRETAIRE', 'INFIRMIER'];

export const peutDeclarerParRole = (hasRole: (role: string) => boolean): boolean =>
  ROLES_DECLARANT_ABSENCE.some((role) => hasRole(role));

export const estModifiable = (statut: StatutAbsence): boolean =>
  statut === 'A_QUALIFIER' || statut === 'JUSTIFIEE' || statut === 'NON_JUSTIFIEE';

/**
 * Action proposée sur une absence : toute absence modifiable peut être rattrapée ; qualifier ou annuler une absence
 * déjà qualifiée (correction) est réservé à l'administrateur et au médecin.
 */
export function actionPossible(mode: ActionAbsence, statut: StatutAbsence, peutCorriger: boolean): boolean {
  if (!estModifiable(statut)) return false;
  if (mode === 'rattrapage') return true;
  return statut === 'A_QUALIFIER' || peutCorriger;
}

/** Les champs requis par l'action sont-ils renseignés (même règle que le serveur) ? */
export function actionValide(
  mode: ActionAbsence,
  statut: StatutAbsence,
  m: { motif: MotifAbsence | ''; commentaire: string; dateRattrapage: string },
): boolean {
  const commentaire = m.commentaire.trim().length > 0;
  switch (mode) {
    case 'qualifier':
      return !!m.motif && (commentaire || (m.motif !== 'AUTRE' && statut === 'A_QUALIFIER'));
    case 'rattrapage':
      return !!m.dateRattrapage;
    case 'annuler':
      return commentaire;
  }
}
