import {CentreStats, MonthlyPoint} from '../../core/api/direction-api.service';

/** Effectif masqué par le seuil d'anonymat : le serveur renvoie `null`, l'écran affiche « < seuil ». */
export function formatHeadcount(value: number | null, threshold: number): string {
  return value === null ? `< ${threshold}` : String(value);
}

/** Centres classés par chiffre d'affaires TTC décroissant (à égalité : ordre alphabétique). */
export function rankByRevenue(centres: readonly CentreStats[]): CentreStats[] {
  return [...centres].sort((a, b) => b.caTtc - a.caTtc || a.nom.localeCompare(b.nom));
}

export type MonthTotal = { mois: string; caTtc: number; seances: number };

/** Cumule les points mensuels de tous les centres : une valeur par mois, dans l'ordre chronologique. */
export function monthlyTotals(points: readonly MonthlyPoint[]): MonthTotal[] {
  const byMonth = new Map<string, MonthTotal>();
  for (const p of points) {
    const entry = byMonth.get(p.mois) ?? {mois: p.mois, caTtc: 0, seances: 0};
    entry.caTtc += p.caTtc;
    entry.seances += p.seances;
    byMonth.set(p.mois, entry);
  }
  return [...byMonth.values()].sort((a, b) => a.mois.localeCompare(b.mois));
}

/** Niveau du taux d'encaissement (couleur de la cellule). */
export function collectionLevel(rate: number | null): 'good' | 'warn' | 'bad' | 'none' {
  if (rate === null) return 'none';
  if (rate >= 80) return 'good';
  if (rate >= 50) return 'warn';
  return 'bad';
}
