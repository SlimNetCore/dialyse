# Configurable List (shared)

Composant réutilisable de tableau Angular Material avec:

- colonnes paramétrables
- tri local (`asc`/`desc`)
- filtres par colonne
- indicateur filtre actif (rouge)
- redimensionnement des colonnes
- support de composant de filtre custom
- responsive mobile/tablette
- **ligne de détail dépliable (expansion gérée en interne ou pilotée de l'extérieur)**

Le composant est autonome : aucune dépendance à un module métier. Il peut être copié tel quel dans un autre projet Angular (avec `column-filter-renderer`,
`dynamic-filter-host` et `hemodialysis-loader`), plus `@ngx-translate` et Angular Material.

## Fichiers

- `configurable-list.component.ts`
- `configurable-list.component.html`
- `configurable-list.component.css`
- `dynamic-filter-host.component.ts`

## API principale

Entrées:

- `rows`: données à afficher
- `columns`: configuration des colonnes
- `emptyLabelKey`: clé i18n affichée si vide
- `minTableWidthPx`: largeur min de la table (scroll horizontal mobile)
- `rowKeyAccessor`: clé métier stable d'une ligne (trackBy + état d'expansion)
- `showResetFilters` / `resetFiltersLabelKey`: bouton "réinitialiser les filtres"

Sorties:

- `filtersChange`: map `{columnId: value}`
- `sortChange`: `{columnId, direction}`
- `rowClick`: ligne cliquée
- `detailToggle`: `{row, expanded, expandedKeys}` à chaque (dé)pliage

## Ligne de détail (expandable rows)

Trois modes, du plus simple au plus contrôlé :

### 1. Mode intégré (recommandé)

Le composant gère lui-même l'état d'expansion. Fournir uniquement le template :

```html

<app-configurable-list
  #list
  [rows]="rows()"
  [columns]="columns()"
  [detailRowTemplate]="detailTpl"
  [detailRowCanExpand]="canExpandRow"
  [rowKeyAccessor]="rowKey"
/>

<ng-template #detailTpl let-row>
  ... contenu du détail ...
</ng-template>
```

- clic sur la ligne = toggle (désactivable via `[detailRowToggleOnRowClick]="false"`)
- `[detailRowAccordion]="true"` : un seul détail ouvert à la fois
- `detailRowCanExpand` : garde optionnelle (ex. `(row) => row.lignes?.length > 0`)
- API publique via référence template : `list.toggleDetail(row)`,
  `list.collapseAllDetails()`, `list.expandedDetailCount()`, `list.isRowExpanded(row)`
- la ligne dépliée reçoit la classe CSS `row-expanded` et `aria-expanded="true"`

### 2. Mode contrôlé par clés

L'état vit chez le parent (store, URL…) :

```html
<app-configurable-list
  [detailRowTemplate]="detailTpl"
  [expandedRowKeys]="expandedIds()"
  [rowKeyAccessor]="rowKey"
  (detailToggle)="..."
/>
```

### 3. Mode contrôlé par prédicat (legacy)

```html
<app-configurable-list [detailRowTemplate]="detailTpl" [detailRowWhen]="isExpanded"/>
```

`detailRowWhen` court-circuite entièrement l'état interne.

## Exemple rapide

```html

<app-configurable-list
  [rows]="rows()"
  [columns]="displayedColumnDefs()"
  [emptyLabelKey]="'ADMIN.USERS.EMPTY'"
  (filtersChange)="onListFiltersChange($event)"
/>
```

```ts
readonly
displayedColumnDefs = computed(() => [
  {
    id: 'username',
    headerKey: 'ADMIN.USERS.COL_USERNAME',
    valueAccessor: (row: AppUser) => row.USERNAME,
    sortable: true,
    resizable: true,
    filter: {type: 'text'},
  },
]);
```

## Filtre custom avec source de données propre

Définir `filter.component` et `filter.componentInputs` sur la colonne. Le composant custom doit exposer:

- input `value`
- output `valueChange`
- output `clear` (optionnel)

Le host dynamique (`dynamic-filter-host`) fait le binding automatiquement.

