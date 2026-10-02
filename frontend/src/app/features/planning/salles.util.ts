import {SalleVue} from '../../core/api/planning-api.service';

export type EtatCapacite = 'ILLIMITEE' | 'LIBRE' | 'PLEINE' | 'DEPASSEE';

/** Remplissage d'une salle : illimitée, avec des places, pleine ou au-delà de sa capacité. */
export function etatCapacite(s: Pick<SalleVue, 'capacite' | 'nbGenerateurs'>): EtatCapacite {
  if (s.capacite === null) return 'ILLIMITEE';
  if (s.nbGenerateurs > s.capacite) return 'DEPASSEE';
  return s.nbGenerateurs === s.capacite ? 'PLEINE' : 'LIBRE';
}

/** Taux de remplissage (0 à 100) pour la jauge ; nul pour une salle sans capacité. */
export function tauxRemplissage(s: Pick<SalleVue, 'capacite' | 'nbGenerateurs'>): number {
  if (s.capacite === null || s.capacite <= 0) return 0;
  return Math.min(100, Math.round((s.nbGenerateurs / s.capacite) * 100));
}

/** Générateurs de la salle en état de dialyser (en service). */
export function nbEnService(s: Pick<SalleVue, 'generateurs'>): number {
  return s.generateurs.filter((g) => g.statut === 'EN_SERVICE').length;
}
