import {CaseGrille, JourSemaine} from '../../../../core/api/planning-api.service';

/** Jours dans l'ordre de la semaine de dialyse (dimanche → samedi), comme sur la fiche patient. */
export const JOURS: readonly JourSemaine[] =
  ['DIMANCHE', 'LUNDI', 'MARDI', 'MERCREDI', 'JEUDI', 'VENDREDI', 'SAMEDI'];

/** Champ de la fiche patient correspondant à chaque jour. */
export type JourFlagKey =
  'jourDimanche' | 'jourLundi' | 'jourMardi' | 'jourMercredi' | 'jourJeudi' | 'jourVendredi' | 'jourSamedi';

const FLAG_KEYS: Record<JourSemaine, JourFlagKey> = {
  DIMANCHE: 'jourDimanche', LUNDI: 'jourLundi', MARDI: 'jourMardi', MERCREDI: 'jourMercredi',
  JEUDI: 'jourJeudi', VENDREDI: 'jourVendredi', SAMEDI: 'jourSamedi',
};

/** Jours cochés sur la fiche → liste de jours (imposés à l'aide au placement). */
export function joursCoches(flags: Partial<Record<JourFlagKey, boolean>>): JourSemaine[] {
  return JOURS.filter((j) => !!flags[FLAG_KEYS[j]]);
}

/** Jours d'une proposition → les sept cases de la fiche patient. */
export function joursVersFlags(jours: readonly JourSemaine[]): Record<JourFlagKey, boolean> {
  const set = new Set(jours);
  return {
    jourDimanche: set.has('DIMANCHE'), jourLundi: set.has('LUNDI'), jourMardi: set.has('MARDI'),
    jourMercredi: set.has('MERCREDI'), jourJeudi: set.has('JEUDI'), jourVendredi: set.has('VENDREDI'),
    jourSamedi: set.has('SAMEDI'),
  };
}

export type EtatCase = 'full' | 'partial' | 'free' | 'none';

/** Couleur d'une case de la grille : complet, partiellement libre, libre, ou sans générateur en service. */
export function etatCase(c: CaseGrille): EtatCase {
  if (c.capacite === 0) return 'none';
  const libres = Math.max(0, c.capacite - c.occupes);
  if (libres === 0) return 'full';
  return libres < c.capacite ? 'partial' : 'free';
}

/** Case de la grille pour une salle, un créneau et un jour (absente : aucune capacité). */
export function trouverCase(grille: readonly CaseGrille[], salleId: string, creneauId: string, jour: JourSemaine): CaseGrille | undefined {
  return grille.find((c) => c.salleId === salleId && c.creneauId === creneauId && c.jour === jour);
}
