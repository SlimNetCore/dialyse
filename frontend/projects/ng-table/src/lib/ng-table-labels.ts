/**
 * Tous les textes affichés par `ng-table` (aucune dépendance i18n externe).
 * Fournissez `[labels]` avec seulement les clés à surcharger — le reste garde
 * `NG_TABLE_DEFAULT_LABELS`.
 */
export interface NgTableLabels {
  columnsButton: string;
  viewsButton: string;
  resetFiltersButton: string;
  clearFilter: string;
  activeFilters: string;
  /** Interpolé : `{field}` est remplacé par le libellé de la colonne. */
  filterBy: string;
  refOptionsLoading: string;
  refOptionsEmpty: string;
  refOptionsLoadError: string;
  dateStart: string;
  dateEnd: string;
  ok: string;
  cancel: string;
  all: string;
  search: string;
  noData: string;
  sort: string;
  sortAsc: string;
  sortDesc: string;
  copy: string;
  yes: string;
  no: string;
}

export const NG_TABLE_DEFAULT_LABELS: NgTableLabels = {
  columnsButton: 'Colonnes',
  viewsButton: 'Vues',
  resetFiltersButton: 'Réinitialiser les filtres',
  clearFilter: 'Effacer le filtre',
  activeFilters: 'Filtres actifs',
  filterBy: 'Filtrer par {field}',
  refOptionsLoading: 'Chargement des options...',
  refOptionsEmpty: 'Aucune option chargée.',
  refOptionsLoadError: 'Erreur de chargement des options.',
  dateStart: 'Date début',
  dateEnd: 'Date fin',
  ok: 'OK',
  cancel: 'Annuler',
  all: 'Tous',
  search: 'Rechercher',
  noData: 'Aucune donnée',
  sort: 'Trier',
  sortAsc: 'Trié croissant',
  sortDesc: 'Trié décroissant',
  copy: 'Copier',
  yes: 'Oui',
  no: 'Non',
};
