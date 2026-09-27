import {CentreStats, DirectionAlert, MonthlyPoint} from '../../core/api/direction-api.service';

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

/** Ne garde que les lignes du centre isolé par le filtre ; toutes les lignes si `centerId` est `null`. */
export function filterByCentre<T extends {
  centerId: string | null
}>(rows: readonly T[], centerId: string | null): T[] {
  return centerId ? rows.filter((r) => r.centerId === centerId) : [...rows];
}

/** Rang (1 = premier) de chaque centre dans une liste déjà classée, par `centerId`. */
export function rankIndex(ranked: readonly { centerId: string | null }[]): Record<string, number> {
  return Object.fromEntries(ranked.map((c, i) => [c.centerId ?? '', i + 1]));
}

/** Niveau du taux d'encaissement (couleur de la cellule). */
export function collectionLevel(rate: number | null): 'good' | 'warn' | 'bad' | 'none' {
  if (rate === null) return 'none';
  if (rate >= 80) return 'good';
  if (rate >= 50) return 'warn';
  return 'bad';
}

/**
 * Niveau d'un marqueur KDIGO (part des patients évalués dans la cible) : vert ≥ 66 %, orange 45-65 %, rouge < 45 %,
 * neutre si non publiable (masqué par l'anonymat ou moins de patients évalués que le seuil).
 */
export function kdigoLevel(pctDansCible: number | null): 'good' | 'warn' | 'bad' | 'none' {
  if (pctDansCible === null) return 'none';
  if (pctDansCible >= 66) return 'good';
  if (pctDansCible >= 45) return 'warn';
  return 'bad';
}

/** Affichage d'un taux : « — » lorsqu'il est masqué par le seuil d'anonymat. */
export function formatPct(value: number | null): string {
  return value === null ? '—' : `${value} %`;
}

export type CsvSection = { title: string; headers: string[]; rows: (string | number | null)[][] };

/** Échappe un champ pour du CSV (séparateur `;`, guillemets doublés si la valeur en contient ou contient `;`/`\n`). */
function csvField(value: string | number | null): string {
  if (value === null) return '';
  const s = String(value);
  return /[";\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
}

/**
 * Assemble plusieurs tableaux en un seul fichier CSV (séparateur `;`, pour ouverture directe dans Excel) : un titre
 * de section, sa ligne d'en-têtes, ses lignes de données, puis une ligne vide avant la section suivante.
 */
export function buildDashboardCsv(sections: readonly CsvSection[]): string {
  const lines: string[] = [];
  for (const section of sections) {
    if (section.rows.length === 0) continue;
    lines.push(csvField(section.title));
    lines.push(section.headers.map(csvField).join(';'));
    for (const row of section.rows) lines.push(row.map(csvField).join(';'));
    lines.push('');
  }
  return lines.join('\r\n');
}

/** Alertes critiques d'abord, puis par centre et par code (ordre stable et lisible). */
export function sortAlerts(alerts: readonly DirectionAlert[]): DirectionAlert[] {
  const rank = (a: DirectionAlert): number => (a.severity === 'CRITICAL' ? 0 : 1);
  return [...alerts].sort((a, b) => rank(a) - rank(b) || a.centre.localeCompare(b.centre) || a.code.localeCompare(b.code));
}

export type Delta = { pct: number | null; direction: 'up' | 'down' | 'flat' };

/**
 * Variation en % d'un indicateur entre la période affichée et celle, de même durée, qui la précède immédiatement.
 * `pct` vaut `null` quand la comparaison n'a pas de sens (période précédente à zéro, sauf si les deux sont à zéro).
 */
export function delta(current: number, previous: number): Delta {
  if (previous === 0) {
    if (current === 0) return {pct: 0, direction: 'flat'};
    return {pct: null, direction: 'up'};
  }
  const pct = ((current - previous) / previous) * 100;
  const direction = pct > 0.05 ? 'up' : pct < -0.05 ? 'down' : 'flat';
  return {pct, direction};
}

export type PeriodPreset = 'MONTH' | 'QUARTER' | 'YEAR' | 'LAST_12_MONTHS';

/** Bornes {from, to} (ISO AAAA-MM-JJ) d'un raccourci de période, calculées par rapport à `now`. */
export function periodForPreset(preset: PeriodPreset, now: Date): { from: string; to: string } {
  const iso = (d: Date): string => {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  };
  const to = iso(now);
  switch (preset) {
    case 'MONTH':
      return {from: iso(new Date(now.getFullYear(), now.getMonth(), 1)), to};
    case 'QUARTER':
      return {from: iso(new Date(now.getFullYear(), Math.floor(now.getMonth() / 3) * 3, 1)), to};
    case 'YEAR':
      return {from: iso(new Date(now.getFullYear(), 0, 1)), to};
    case 'LAST_12_MONTHS':
      return {from: iso(new Date(now.getFullYear(), now.getMonth() - 11, 1)), to};
  }
}

/** Les `count` derniers mois écoulés (le mois courant est exclu), du plus récent au plus ancien, au format AAAA-MM. */
export function lastCompleteMonths(now: Date, count: number): string[] {
  const months: string[] = [];
  for (let i = 1; i <= count; i++) {
    const d = new Date(now.getFullYear(), now.getMonth() - i, 1);
    months.push(`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`);
  }
  return months;
}
