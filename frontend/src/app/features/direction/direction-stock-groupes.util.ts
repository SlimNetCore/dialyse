import {StockGroupesOverview, ValeursStockGroupe} from '../../core/api/direction-api.service';

/** Ligne du tableau de valorisation : total consolidé d'un groupe, ou ligne d'un centre pour ce groupe. */
export type StockGroupeRow = {
  key: string;
  nom: string;
  /** null pour la ligne de total consolidé. */
  centre: string | null;
  nbArticles: number;
  valeurs: ValeursStockGroupe;
  total: boolean;
};

/**
 * Lignes à afficher : pour chaque groupe, son total consolidé puis le détail par centre (le total n'est pas
 * affiché quand un centre est isolé par le filtre : seules les lignes de ce centre sont conservées).
 */
export function stockGroupeRows(overview: StockGroupesOverview | null, selectedCentre: string | null): StockGroupeRow[] {
  if (!overview) return [];
  const rows: StockGroupeRow[] = [];
  for (const g of overview.groupes) {
    const centres = selectedCentre ? g.centres.filter((c) => c.centerId === selectedCentre) : g.centres;
    if (centres.length === 0) continue;
    if (!selectedCentre) {
      rows.push({
        key: `${g.nom}|total`,
        nom: g.nom,
        centre: null,
        nbArticles: g.nbArticles,
        valeurs: g.total,
        total: true
      });
    }
    for (const c of centres) {
      rows.push({
        key: `${g.nom}|${c.centerId}`, nom: g.nom, centre: c.centreNom, nbArticles: c.nbArticles,
        valeurs: c.valeurs, total: false
      });
    }
  }
  return rows;
}
