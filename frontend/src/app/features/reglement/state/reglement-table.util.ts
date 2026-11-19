import type {NgTableColumn, NgTableSortChange, NgTableTone} from '@sbourahla/ng-table';
import {ReglementEtat, ReglementSoldeType} from '../../../core/api/backend-api.service';

/** Tailles de page proposées par la liste des factures à régler (20 par défaut, comme le store). */
export const TAILLES_DE_PAGE_REGLEMENT: readonly number[] = [10, 20, 50, 100];

/** Teinte de l'état de règlement d'une facture. */
export const TONS_ETAT_REGLEMENT: Record<ReglementEtat, NgTableTone> = {
  NON_REGLEE: 'danger',
  PARTIELLEMENT_REGLEE: 'warning',
  REGLEE: 'success',
};

/** Teinte du solde : soldée en succès, trop-perçu en alerte, reste dû en danger. */
export const TONS_SOLDE_REGLEMENT: Record<ReglementSoldeType, NgTableTone> = {
  REGLE: 'success',
  TROP_PERCU: 'warning',
  RESTE: 'danger',
};

/** Tri et filtres de colonne demandés par la table (ils s'appliquent à la page chargée). */
export type CriteresDeLaPage = { sort: NgTableSortChange | null; filters: Record<string, string> };

const texte = (valeur: unknown): string => (valeur == null ? '' : String(valeur)).toLocaleLowerCase();

/**
 * L'API des règlements ne pagine que par année / mois / caisse / agence / centre payeur : comme l'ancienne liste,
 * le tri et les filtres texte des colonnes portent sur la page chargée (filtre « contient », insensible à la casse).
 */
export function filtrerEtTrierLaPage<T>(rows: readonly T[], columns: readonly NgTableColumn<T>[],
                                        criteres: CriteresDeLaPage): T[] {
  const parId = new Map(columns.map((c) => [c.id, c]));
  const filtres = Object.entries(criteres.filters ?? {})
    .map(([id, brut]) => ({colonne: parId.get(id), brut: brut ?? '', valeur: texte(brut).trim()}))
    .filter((f) => !!f.colonne && f.valeur !== '');
  const garde = (row: T, f: (typeof filtres)[number]): boolean => f.colonne!.filterPredicate
    ? f.colonne!.filterPredicate(row, f.brut)
    : texte(f.colonne!.valueAccessor(row)).includes(f.valeur);
  let resultat = filtres.length ? rows.filter((row) => filtres.every((f) => garde(row, f))) : [...rows];
  const sort = criteres.sort;
  const colonne = sort?.direction ? parId.get(sort.columnId) : undefined;
  if (sort && colonne) {
    const cle = (row: T): unknown => (colonne.sortValueAccessor ?? colonne.valueAccessor)(row);
    const sens = sort.direction === 'desc' ? -1 : 1;
    resultat = resultat.sort((a, b) => {
      const va = cle(a);
      const vb = cle(b);
      if (typeof va === 'number' && typeof vb === 'number') return (va - vb) * sens;
      return texte(va).localeCompare(texte(vb)) * sens;
    });
  }
  return resultat;
}
