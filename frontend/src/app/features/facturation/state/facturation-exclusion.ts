import {MotifAbsence} from '../../../core/api/absence-patient-api.service';

/** Séance retirée de la simulation en cours, réinclusible tant qu'elle n'est pas facturée. */
export type SeanceExclue = {
  seanceId: string;
  seanceDate: string;
  patientLabel: string;
  motif: MotifAbsence;
};

/** Saisie d'une exclusion en cours : le motif est obligatoire, le commentaire l'est avec AUTRE. */
export function exclusionEstValide(motif: MotifAbsence | null, commentaire: string): boolean {
  if (!motif) {
    return false;
  }
  return motif !== 'AUTRE' || commentaire.trim().length > 0;
}
