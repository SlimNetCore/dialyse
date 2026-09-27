import {AgeRow, CaisseRow, CaisseTotal, SexeRow} from '../../core/api/direction-api.service';

export type CaisseMetric = 'patients' | 'seances' | 'caHt';

export type PivotCentre = { id: string; nom: string };

export type PivotLine = {
  code: string;
  nom: string;
  /** Valeur par centre (null : effectif masqué par l'anonymat). */
  cells: Record<string, number | null>;
  /** Total de la société pour cette caisse (null : effectif masqué). */
  total: number | null;
};

/**
 * Tableau croisé caisse × centre pour une mesure (patients, séances ou CA HT). Les caisses sont classées par CA HT
 * décroissant (ordre des totaux serveur) ; une cellule absente vaut 0 sauf pour les patients (masquée = null).
 */
export function pivotCaisses(
  centres: readonly PivotCentre[],
  rows: readonly CaisseRow[],
  totals: readonly CaisseTotal[],
  metric: CaisseMetric,
): PivotLine[] {
  return totals.map((t) => {
    const cells: Record<string, number | null> = {};
    for (const c of centres) {
      const row = rows.find((r) => r.centerId === c.id && r.caisseCode === t.caisseCode);
      cells[c.id] = row ? row[metric] : 0;
    }
    return {code: t.caisseCode, nom: t.caisse ?? t.caisseCode, cells, total: t[metric]};
  });
}

/** Sexe par centre en séries de graphique : une série par sexe, les effectifs masqués comptent 0. */
export function sexeSeries(rows: readonly SexeRow[]): {
  labels: string[];
  masculin: number[];
  feminin: number[];
  autre: number[]
} {
  return {
    labels: rows.map((r) => r.nom),
    masculin: rows.map((r) => r.masculin ?? 0),
    feminin: rows.map((r) => r.feminin ?? 0),
    autre: rows.map((r) => r.autre ?? 0),
  };
}

/** Tranches d'âge par centre en séries de graphique (une série par tranche). */
export function ageSeries(rows: readonly AgeRow[], codes: readonly string[]): {
  labels: string[];
  series: Record<string, number[]>
} {
  const series: Record<string, number[]> = {};
  for (const code of codes) {
    series[code] = rows.map((r) => r.tranches.find((t) => t.code === code)?.count ?? 0);
  }
  return {labels: rows.map((r) => r.nom), series};
}

export const AGE_CODES = ['0_17', '18_29', '30_44', '45_59', '60_PLUS', 'INCONNU'] as const;
