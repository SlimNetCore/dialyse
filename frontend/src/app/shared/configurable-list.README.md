# Configurable List (shared)

Composant réutilisable de tableau Angular Material avec:

- colonnes paramétrables
- tri local (`asc`/`desc`)
- filtres par colonne
- indicateur filtre actif (rouge)
- redimensionnement des colonnes
- support de composant de filtre custom
- responsive mobile/tablette

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

Sorties:

- `filtersChange`: map `{columnId: value}`
- `sortChange`: `{columnId, direction}`
- `rowClick`: ligne cliquée

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

