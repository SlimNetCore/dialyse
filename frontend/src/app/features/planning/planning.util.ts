import {
  CellulePlanning,
  JourPlanning,
  JourSemaine,
  SemainePlanning,
} from '../../core/api/planning-api.service';

/** Décale une date `yyyy-MM-dd` d'un nombre de jours (calcul en UTC : pas de dérive liée à l'heure d'été). */
export function decalerJours(date: string, jours: number): string {
  const [y, m, d] = date.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d + jours)).toISOString().slice(0, 10);
}

/** Un jour est fermé s'il n'est pas ouvert chaque semaine ou s'il tombe sur une fermeture datée. */
export function jourFerme(jour: JourPlanning): boolean {
  return !jour.ouvertHebdomadaire || jour.fermetureMotif !== null;
}

export function cellule(
  semaine: SemainePlanning, salleId: string, creneauId: string, jour: JourSemaine,
): CellulePlanning | undefined {
  return semaine.cellules.find((c) => c.salleId === salleId && c.creneauId === creneauId && c.jour === jour);
}

/** Cellule pleine (au moins autant d'occupants que de générateurs), vide, ou partiellement occupée. */
export function etatCellule(c: CellulePlanning | undefined): 'closed' | 'empty' | 'partial' | 'full' | 'over' {
  if (!c || c.capacite === 0) return c && c.occupants.length > 0 ? 'over' : 'closed';
  if (c.occupants.length === 0) return 'empty';
  if (c.occupants.length > c.capacite) return 'over';
  return c.occupants.length === c.capacite ? 'full' : 'partial';
}
