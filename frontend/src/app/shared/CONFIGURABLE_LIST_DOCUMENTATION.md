# `app-configurable-list` — documentation détaillée

Composant de liste partagé, basé sur Angular Material Table, standalone, signal-based.
Fichiers : `configurable-list.component.ts` / `.html` / `.css`.

## Rôle

`app-configurable-list` centralise tout ce qu'une liste "métier" du projet a besoin de faire, pour éviter de réécrire dans chaque écran (patients, factures, séances, stock, règlements, comptabilité, utilisateurs...) :

- colonnes configurables : visibilité, **ordre (drag-and-drop)**, largeur (resize)
- tri local
- filtrage local par colonne (menu ou inline), barre de filtres actifs
- copie rapide d'une cellule
- ligne détail expandable (master/detail)
- sélection de lignes (checkbox), interne ou contrôlée
- menu contextuel (clic droit) sur une ligne
- **vues sauvegardées** (colonnes, ordre, tri, filtres, pagination) persistées en `localStorage` ou déléguées au parent
- support responsive (colonne d'actions mobile, largeur mini avec scroll horizontal)

Le composant ne fait aucun appel réseau : les `rows` sont fournies par le parent, déjà chargées (ou paginées côté serveur).

## Pattern contrôlé / non-contrôlé

Convention utilisée pour plusieurs features (visibilité colonnes, ordre colonnes, filtres, sélection, expansion détail, vues) :

- l'`input()` correspondant accepte `null` comme sentinelle **"non contrôlé"** → le composant gère alors un signal interne.
- si le parent fournit une valeur non-`null`, le composant devient **contrôlé** : il n'écrit plus dans son état interne, il se contente d'émettre l'`output()` correspondant et attend que le parent renvoie la nouvelle valeur via binding.
- en interne, une méthode privée `effectiveXxx()` résout la priorité (valeur contrôlée si non-null, sinon signal interne).

Exemple minimal (visibilité colonnes) :

```html

<app-configurable-list
  [columns]="allColumnDefs()"
  [columnVisibility]="visibleColumns()"
  (columnVisibilityChange)="visibleColumns.set($event)"
  [rows]="rows()"
/>
```

Si `[columnVisibility]` n'est pas bindé du tout (ou reçoit `null`), le composant gère lui-même l'état (via son menu "Colonnes") sans rien demander au parent.

## Exemple rapide

```ts
readonly
columns: SharedListColumn < Patient > [] = [
  {
    id: 'code',
    headerKey: 'PATIENT_LIST.COL_CODE',
    valueAccessor: (row) => row.code,
    sortable: true,
    copy: true,
  },
  {
    id: 'nom',
    headerKey: 'PATIENT_LIST.COL_NOM',
    valueAccessor: (row) => row.nom,
    sortable: true,
    filter: {type: 'text'},
  },
  {
    id: 'etatPatient',
    headerKey: 'PATIENT_LIST.COL_ETAT',
    valueAccessor: (row) => row.etatPatient,
    filter: {
      type: 'select',
      options: [
        {value: 'ACTIF', label: 'Actif'},
        {value: 'DECEDE', label: 'Décédé'},
      ],
    },
    cellTemplate: etatCell, // TemplateRef
  },
];
```

```html

<app-configurable-list
  [columns]="columns"
  [rows]="rows()"
  [rowSelectionEnabled]="true"
  [showActiveFiltersBar]="true"
/>
```

## Référence API

### `SharedListColumn<T>`

| Champ                                      | Type                                                                   | Description                                                                                                                                               |
|--------------------------------------------|------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------|
| `id`                                       | `string`                                                               | Identifiant unique, utilisé partout (visibilité, ordre, tri, filtres, vues).                                                                              |
| `headerKey`                                | `string`                                                               | Clé i18n de l'en-tête.                                                                                                                                    |
| `valueAccessor`                            | `(row: T) => unknown`                                                  | Valeur brute de la cellule (affichée si pas de `cellTemplate`).                                                                                           |
| `visible?`                                 | `boolean`                                                              | Visibilité par défaut (mode non contrôlé), `true` si omis.                                                                                                |
| `sortable?`                                | `boolean`                                                              | Active le tri sur cette colonne.                                                                                                                          |
| `resizable?`                               | `boolean`                                                              | Active le redimensionnement (drag sur la bordure du `<th>`).                                                                                              |
| `widthPx?` / `minWidthPx?` / `maxWidthPx?` | `number`                                                               | Contraintes de largeur.                                                                                                                                   |
| `cellTemplate?`                            | `TemplateRef<{$implicit: T; row: T; value: unknown; column}>`          | Template custom de cellule.                                                                                                                               |
| `sortValueAccessor?`                       | `(row: T) => string \| number \| boolean \| Date \| null \| undefined` | Valeur utilisée pour le tri si différente de `valueAccessor` (ex. trier par date brute alors que la cellule affiche un format localisé).                  |
| `filter?`                                  | `SharedListFilterConfig`                                               | Configuration du filtre (voir plus bas).                                                                                                                  |
| `filterPredicate?`                         | `(row: T, filterValue: string) => boolean`                             | Logique de filtrage custom, remplace le filtrage par défaut (égalité/`includes` sur `valueAccessor`).                                                     |
| `mobileRowActions?`                        | `boolean`                                                              | Marque cette colonne comme la colonne d'actions à afficher en vue mobile (les autres colonnes sont masquées, cf. section responsive).                     |
| `copy?`                                    | `boolean \| {valueAccessor?, tooltipKey?}`                             | Ajoute un bouton "copier" sur la cellule. `true` copie le résultat de `valueAccessor` (converti en `string`) ; l'objet permet un accessor/tooltip dédiés. |

### `SharedListFilterConfig`

```ts
interface SharedListFilterConfig {
  type?: ColumnFilterType;              // 'text' | 'select' | 'date' | 'date-range' | ... (cf. ColumnFilterRendererComponent)
  options?: SharedListFilterOption[];    // options statiques pour un select
  optionsLoader?: () => Observable<SharedListFilterOption[]> | Promise<
  ...>; // options chargées à la demande
  placeholder?: string;
  labelKey?: string;                     // libellé affiché dans la barre de filtres actifs
  component?: Type<unknown>;             // renderer de filtre 100% custom
  componentInputs?: Record<string, unknown>;
}
```

- `type`/`options`/`placeholder` alimentent le rendu standard via `ColumnFilterRendererComponent`.
- `optionsLoader` charge les options à la demande (à l'ouverture du menu filtre, ou en eager si `inlineFilters=true`).
- `component`/`componentInputs` permettent de brancher un composant de filtre entièrement custom via `DynamicFilterHostComponent` — il doit exposer un `@Input() value` et un `@Output() valueChange`.

La valeur de filtre est toujours une **`string`** (y compris pour les select/date — c'est au `filterPredicate` ou au filtrage par défaut de l'interpréter).

### Inputs principaux

| Input                       | Type                                                             | Défaut                                | Description                                                                                                                                                                                                                                                           |
|-----------------------------|------------------------------------------------------------------|---------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `rows`                      | `any[]`                                                          | `[]`                                  | Données source (non filtrées/triées).                                                                                                                                                                                                                                 |
| `columns`                   | `SharedListColumn<any>[]`                                        | `[]`                                  | Définition des colonnes.                                                                                                                                                                                                                                              |
| `columnVisibility`          | `Record<string, boolean> \| null`                                | `null`                                | Mode contrôlé de la visibilité.                                                                                                                                                                                                                                       |
| `columnOrder`               | `ReadonlyArray<string> \| null`                                  | `null`                                | Mode contrôlé de l'ordre des colonnes (ids, dans l'ordre d'affichage voulu).                                                                                                                                                                                          |
| `filters`                   | `Record<string, string> \| null`                                 | `null`                                | Mode contrôlé des filtres.                                                                                                                                                                                                                                            |
| `emptyLabelKey`             | `string`                                                         | `'COMMON.NO_DATA'`                    | Clé i18n affichée quand la liste est vide.                                                                                                                                                                                                                            |
| `minTableWidthPx`           | `number`                                                         | `760`                                 | Largeur mini avant scroll horizontal (desktop).                                                                                                                                                                                                                       |
| `rowClassFn`                | `(row) => string \| string[] \| Record<string, boolean> \| null` | `null`                                | Classes CSS dynamiques par ligne.                                                                                                                                                                                                                                     |
| `rowTrackBy`                | `TrackByFunction<any> \| null`                                   | `null`                                | `trackBy` custom pour `*matRowDef`.                                                                                                                                                                                                                                   |
| `rowKeyAccessor`            | `(row) => unknown`                                               | `null`                                | Clé métier stable d'une ligne (sélection, expansion, feedback copie, fallback trackBy). Recommandé dès que `row.id` n'est pas fiable/présent.                                                                                                                         |
| `detailRowTemplate`         | `TemplateRef<{$implicit, row}>`                                  | `null`                                | Template de la ligne détail. `null` = pas de ligne détail.                                                                                                                                                                                                            |
| `detailRowWhen`             | `(index, row) => boolean`                                        | `null`                                | Mode contrôlé (par index+row) de l'expansion.                                                                                                                                                                                                                         |
| `expandedRowKeys`           | `ReadonlyArray<unknown> \| null`                                 | `null`                                | Mode contrôlé (par clé) de l'expansion.                                                                                                                                                                                                                               |
| `detailRowToggleOnRowClick` | `boolean`                                                        | `true`                                | Mode non contrôlé : clic sur la ligne = toggle détail.                                                                                                                                                                                                                |
| `detailRowAccordion`        | `boolean`                                                        | `false`                               | Mode non contrôlé : une seule ligne dépliée à la fois.                                                                                                                                                                                                                |
| `detailRowCanExpand`        | `(row) => boolean`                                               | `null`                                | Garde optionnelle (ex. n'autoriser le détail que si la ligne a des enfants).                                                                                                                                                                                          |
| `showResetFilters`          | `boolean`                                                        | `true`                                | Affiche le bouton "réinitialiser les filtres".                                                                                                                                                                                                                        |
| `resetFiltersLabelKey`      | `string`                                                         | `'PATIENT_LIST.RESET_FILTERS_BUTTON'` | Clé i18n du bouton reset (à surcharger par écran).                                                                                                                                                                                                                    |
| `rowSelectionEnabled`       | `boolean`                                                        | `false`                               | Ajoute une colonne checkbox de sélection.                                                                                                                                                                                                                             |
| `selectedRowKeys`           | `ReadonlyArray<unknown> \| null`                                 | `null`                                | Mode contrôlé de la sélection (par clé, cf. `rowKeyAccessor`).                                                                                                                                                                                                        |
| `inlineFilters`             | `boolean`                                                        | `false`                               | Filtres affichés directement dans l'en-tête (pas de menu/icône).                                                                                                                                                                                                      |
| `inlineFilterColumnIds`     | `ReadonlyArray<string> \| null`                                  | `null`                                | Restreint les filtres inline à certaines colonnes.                                                                                                                                                                                                                    |
| `showActiveFiltersBar`      | `boolean`                                                        | `false`                               | Affiche une barre récapitulative des filtres actifs (avec suppression individuelle).                                                                                                                                                                                  |
| `rowContextMenuEnabled`     | `boolean`                                                        | `false`                               | Active le clic droit sur les lignes.                                                                                                                                                                                                                                  |
| `rowContextMenuTemplate`    | `TemplateRef<{$implicit: row, row}>`                             | `null`                                | Contenu du menu contextuel (des `<button mat-menu-item>` typiquement).                                                                                                                                                                                                |
| `viewsEnabled`              | `boolean`                                                        | `false`                               | Affiche/masque tout le bloc "Vues" (bouton + menu).                                                                                                                                                                                                                   |
| `viewsStorageKey`           | `string \| null`                                                 | `null`                                | Mode non contrôlé : clé de persistance `localStorage` (namespacée automatiquement).                                                                                                                                                                                   |
| `viewsStore`                | `SharedListViewsStore \| null`                                   | `null`                                | Mode contrôlé : le parent possède entièrement le store des vues.                                                                                                                                                                                                      |
| `dataMode`                  | `'local' \| 'remote'`                                            | `'local'`                             | Voir section "Mode local / distant" ci-dessous.                                                                                                                                                                                                                       |
| `paginationEnabled`         | `boolean`                                                        | `false`                               | Active la pagination "prise en compte" par le composant (slicing local, ou embarquée dans `remoteQueryChange`) **et** la capture de `pageIndex`/`pageSize` dans les vues sauvegardées. `false` = comportement historique, aucune régression sur les écrans existants. |
| `pageIndex`                 | `number`                                                         | `0`                                   | Page courante (0-based). Utilisé seulement si `paginationEnabled=true` — réutilisé aussi bien pour la pagination que pour les vues (il n'y a pas d'input séparé pour les vues).                                                                                       |
| `pageSize`                  | `number`                                                         | `10`                                  | Taille de page. Même chose que `pageIndex` — un seul couple d'inputs pour les deux usages.                                                                                                                                                                            |

### Outputs

| Output                   | Payload                                                                          | Description                                                                                                                                                                                                                                                                                        |
|--------------------------|----------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `rowClick`               | `any` (la ligne)                                                                 | Clic sur une ligne de données.                                                                                                                                                                                                                                                                     |
| `filtersChange`          | `Record<string, string>`                                                         | Tout changement de filtre.                                                                                                                                                                                                                                                                         |
| `sortChange`             | `SharedListSortChange` (`{columnId, direction}`)                                 | Changement de tri.                                                                                                                                                                                                                                                                                 |
| `cellCopied`             | `SharedListCopyEvent` (`{columnId, value, row}`)                                 | Après un clic sur le bouton copier d'une cellule.                                                                                                                                                                                                                                                  |
| `detailToggle`           | `SharedListDetailToggleEvent` (`{row, expanded, expandedKeys}`)                  | Ouverture/fermeture d'une ligne détail.                                                                                                                                                                                                                                                            |
| `columnVisibilityChange` | `Record<string, boolean>`                                                        | Changement via le menu "Colonnes".                                                                                                                                                                                                                                                                 |
| `columnOrderChange`      | `string[]`                                                                       | Nouvel ordre des ids de colonnes après un drag-and-drop d'en-tête.                                                                                                                                                                                                                                 |
| `selectionChange`        | `SharedListSelectionChangeEvent` (`{row, selected, selectedKeys, selectedRows}`) | Sélection/désélection d'une ligne ou tout-sélectionner.                                                                                                                                                                                                                                            |
| `rowContextMenu`         | `SharedListContextMenuEvent` (`{row, position}`)                                 | Ouverture du menu contextuel.                                                                                                                                                                                                                                                                      |
| `viewsStoreChange`       | `SharedListViewsStore`                                                           | Le store des vues a changé (créée, activée, supprimée). En mode non contrôlé c'est un simple miroir de ce qui vient d'être écrit en `localStorage` ; en mode contrôlé, **c'est le seul endroit où le changement est notifié**, puisque le composant ne persiste plus rien lui-même.                |
| `viewActivated`          | `SharedListView \| null`                                                         | Une vue devient active (changement manuel ou activation automatique au chargement). `null` si aucune vue active.                                                                                                                                                                                   |
| `viewPaginationRestore`  | `{pageIndex: number; pageSize: number}`                                          | Émis quand la vue activée contient une pagination — à appliquer à votre propre paginator.                                                                                                                                                                                                          |
| `remoteQueryChange`      | `SharedListRemoteQuery` (`{sort, filters, page}`)                                | **Mode `remote` uniquement.** Émis à chaque changement de tri ou de filtre (y compris reset et activation de vue), avec l'état **complet** — tri courant, tous les filtres, et la page (toujours remise à `0`, cf. ci-dessous) — à utiliser pour reconstruire la requête serveur en un seul appel. |
| `filteredCountChange`    | `number`                                                                         | **Mode `local` + `paginationEnabled=true` uniquement.** Nombre de lignes après filtrage (avant pagination) — à brancher sur le `[length]` de votre propre `<mat-paginator>`, puisque `rows()` côté parent contient l'intégralité du jeu de données non filtré.                                     |
| `pageIndexChange`        | `number`                                                                         | **Mode `local` + `paginationEnabled=true` uniquement.** Émis avec `0` quand un changement de tri/filtre doit ramener l'utilisateur à la première page.                                                                                                                                             |

## Mode local / distant (`dataMode`)

Le composant ne fait jamais lui-même d'appel réseau — `dataMode` détermine seulement s'il traite `rows()` comme le jeu de données complet (à filtrer/trier/paginer lui-même) ou comme une page déjà préparée par le serveur.

### Mode local complet (`dataMode='local'`, défaut)

Tri, filtrage **et pagination** sont appliqués **côté client**, sans aucun appel réseau au-delà du chargement initial de `rows()`. Adapté aux listes de taille raisonnable qui ne changent pas souvent (référentiels, petites listes admin...).

- Sans `[paginationEnabled]` (ou `false`, défaut) : comportement historique inchangé — `displayedRows()` = toutes les lignes filtrées/triées, aucune pagination interne (celle éventuellement affichée par le parent porte alors sur les lignes déjà slicées par lui-même, ou n'existe pas).
- Avec `[paginationEnabled]="true"` : `displayedRows()` est en plus tronqué à la page courante (`pageIndex`/`pageSize`). Le composant expose alors :
  - `(filteredCountChange)` — le total **après filtrage** (le composant est seul à le connaître, puisque le parent ne lui a passé que le jeu de données brut) — à binder sur `[length]` de votre paginator.
  - `(pageIndexChange)` — remise à `0` automatique dès qu'un tri/filtre change, à renvoyer vers votre `pageIndex` pour que le paginator ne reste pas bloqué sur une page devenue invalide.

```html

<app-configurable-list
  [dataMode]="'local'"
  [paginationEnabled]="true"
  [pageIndex]="pageIndex()"
  [pageSize]="pageSize()"
  [columns]="columns"
  [rows]="allRowsLoadedOnce()"
  (filteredCountChange)="filteredTotal.set($event)"
  (pageIndexChange)="pageIndex.set($event)"
/>
<mat-paginator [length]="filteredTotal()" [pageIndex]="pageIndex()" [pageSize]="pageSize()"
               (page)="pageIndex.set($event.pageIndex); pageSize.set($event.pageSize)"/>
```

Aucune requête n'est déclenchée par un changement de filtre, de tri ou de page — tout se passe en mémoire sur `allRowsLoadedOnce()`.

**Exemple réel — `patient-list`, les deux modes fonctionnels en même temps** (voir [`patient-list.component.ts`](../features/patient/patient-list.component.ts) / [`.html`](../features/patient/patient-list.component.html) / [`patient-list.store.ts`](../features/patient/state/patient-list.store.ts)). Plutôt que de figer un seul mode, cet écran garde les deux chemins pleinement fonctionnels et bascule via **un seul flag** :

```ts
// patient-list.component.ts
protected readonly
dataMode: 'local' | 'remote' = 'local'; // changez cette ligne pour basculer
```

Ce flag est synchronisé une fois vers le store dans le constructeur (`this.patientListStore.setDataMode(this.dataMode)`), et le template bind `[dataMode]="dataMode"` sur `app-configurable-list` ainsi que **tous** les handlers des deux modes à la fois (`remoteQueryChange`, `filteredCountChange`, `pageIndexChange`) — sans risque, puisque le composant n'émet que ceux du mode réellement actif.

Côté store, l'effet `onInit` lit `store.dataMode()` et bifurque :

- `local` → ne dépend que du centre/utilisateur, appelle `loadAllPatients(centerId, userId)` qui charge **tous** les patients du centre en paginant en interne côté backend (200/requête max, boucle via `expand`), une seule fois par centre.
- `remote` → dépend en plus de `pageIndex`/`pageSize`/`columnFilters`/`sortColumnId`/`sortDirection`, appelle `loadPage(...)` (filtrage/tri/pagination serveur) à chaque changement.

Côté composant, `onPageChange`/`onViewPaginationRestore` branchent eux aussi sur `this.dataMode` : `applyRemoteQuery(...)` en remote, simple `setPagination(...)` (aucune requête) en local. Le `[length]` du paginator utilise un `displayedTotal()` qui choisit `filteredTotal()` (post-filtre, alimenté par `filteredCountChange`) en local ou `total()` (total serveur) en remote.

### Mode remote complet (`dataMode='remote'`)

Tri, filtrage et pagination sont délégués **entièrement au serveur** :

- le composant **n'applique plus aucun tri/filtrage/pagination local** : `rows()` est affiché tel quel, en supposant qu'il s'agit déjà de la page exacte renvoyée par le backend pour la requête courante.
- chaque changement de tri (clic sur un en-tête triable) ou de filtre (saisie, reset individuel, "réinitialiser les filtres", activation d'une vue) émet **un seul événement combiné** `(remoteQueryChange)` avec l'état complet — `{sort: {columnId, direction}, filters: {...}, page: {index, size}}` — prêt à être transformé en une requête HTTP unique. `page.index` est toujours remis à `0` (changer un tri/filtre repart de la première page), `page.size` reprend `pageSize()` si `paginationEnabled=true`, sinon `0`.
- la **navigation de page elle-même** (clic sur une page du paginator) n'est **pas** captée par ce composant — il ne rend aucun paginator lui-même. Faites appeler votre méthode de fetch directement depuis le `(page)` de votre propre `<mat-paginator>`, avec le tri/filtres courants + la nouvelle page, idéalement via la **même** méthode combinée que celle utilisée pour `(remoteQueryChange)` afin de garder un point d'entrée unique vers le serveur.
- `(sortChange)`/`(filtersChange)` continuent d'être émis normalement en plus (rien ne change de ce côté) — `(remoteQueryChange)` est la commodité qui bundle tout pour construire la requête en un seul endroit.

```html

<app-configurable-list
  [dataMode]="'remote'"
  [paginationEnabled]="true"
  [pageIndex]="pageIndex()"
  [pageSize]="pageSize()"
  [columns]="columns"
  [rows]="serverRows()"
  [filters]="columnFilters()"
  (remoteQueryChange)="onRemoteQueryChange($event)"
/>
<mat-paginator (page)="onPageChange($event)" [length]="total()" [pageIndex]="pageIndex()" [pageSize]="pageSize()"/>
```

```ts
onRemoteQueryChange(query
:
SharedListRemoteQuery
):
void {
  this.fetchRows(query); // { sort, filters, page } — un seul appel réseau
}

onPageChange(event
:
PageEvent
):
void {
  this.fetchRows({sort: this.currentSort(), filters: this.currentFilters(), page: {index: event.pageIndex, size: event.pageSize}});
}
```

Quand `dataMode='remote'` est actif, `PatientListStore.applyRemoteQuery(query)` patche `columnFilters`/`sortColumnId`/`sortDirection`/`pageIndex`/`pageSize` en **un seul** `patchState` (→ un seul appel `loadPage`/réseau, que le déclencheur soit un filtre, un tri ou une page). Le backend (`PatientListQueryService.search`) construit un `Sort` JPA à partir d'une **liste blanche** de colonnes réellement triables côté base (`code`, `nom`, `prenom`, `sexe`, `dateAdmission`, `numeroAssurance`, `etatPatient`, `medecinTraitantId`, `positionId`, `transporteurAllerId`, `transporteurRetourId`) ; les colonnes dérivées après coup (`nonFacturable`, `pecStatus`, `pecForfaitId`, `joursDialyse`) ne peuvent pas être poussées en SQL et retombent silencieusement sur le tri par défaut (nom, prénom).

⚠️ Basculer `[dataMode]` **à la volée sur une instance déjà affichée** (au lieu de choisir un mode fixe au démarrage, comme le fait le flag `patient-list` ci-dessus) change radicalement la sémantique de `rows()` — ça ne recharge rien tout seul. Le pattern `patient-list` fonctionne parce que le choix est fixé une fois au démarrage du composant (et propagé au store via `setDataMode`), pas changé dynamiquement pendant que l'utilisateur interagit avec la liste.

## Ordre des colonnes (drag-and-drop)

Chaque en-tête de colonne a une poignée (`drag_indicator`) qui déclenche un **drag-and-drop HTML5 natif** (pas Angular CDK — un premier essai avec `@angular/cdk/drag-drop` a été abandonné : son repositionnement live par `transform` est incompatible avec la mise en page `<table>`/`display:table-cell`, ce qui provoquait des colonnes qui se chevauchent). L'implémentation actuelle utilise directement les événements `dragstart`/`dragover`/`dragleave`/`drop`/`dragend` et l'API `DataTransfer`.

- Désactivé en vue mobile (`isMobileView()` → `draggable` est retiré de la poignée).
- En mode non contrôlé, l'ordre résultant est stocké dans un signal interne (`internalColumnOrder`) et directement reflété par `visibleColumns()`.
- En mode contrôlé, bindez `[columnOrder]` et `(columnOrderChange)` :

```html

<app-configurable-list
  [columns]="columns"
  [columnOrder]="columnOrder()"
  (columnOrderChange)="columnOrder.set($event)"
  [rows]="rows()"
/>
```

- `columnOrder` ne doit contenir que des `id` de colonnes ; les colonnes absentes de la liste sont poussées à la fin dans leur ordre de définition (`columns` array).

## Ligne détail (master/detail)

Trois modes, du plus simple au plus contrôlé :

**1. Non contrôlé (le plus courant)** — le composant gère lui-même l'expansion au clic sur la ligne :

```html

<app-configurable-list
  [columns]="columns"
  [rows]="rows()"
  [detailRowTemplate]="detailTpl"
  [detailRowAccordion]="true"
/>
<ng-template #detailTpl let-row>
  <div class="detail-panel">{{ row.notes }}</div>
</ng-template>
```

**2. Contrôlé par index+row** (`detailRowWhen`) — utile si l'expansion dépend d'un état externe complexe :

```html
[detailRowWhen]="(index, row) => expandedIndexes().has(index)"
```

**3. Contrôlé par clé** (`expandedRowKeys`, combiné à `rowKeyAccessor`) — le mode recommandé si vous devez piloter/persister l'expansion depuis le parent (ex. lié à une navigation ou une vue sauvegardée) :

```html
[rowKeyAccessor]="(row) => row.id"
[expandedRowKeys]="expandedIds()"
(detailToggle)="onDetailToggle($event)"
```

## Sélection de lignes

```html

<app-configurable-list
  [rowSelectionEnabled]="true"
  (selectionChange)="onSelectionChange($event)"
/>
```

- Non contrôlé par défaut (checkbox interne + case "tout sélectionner" dans l'en-tête).
- Contrôlé via `[selectedRowKeys]` (nécessite `rowKeyAccessor` si `row.id` n'est pas la clé naturelle) + écoute de `(selectionChange)` pour renvoyer le nouvel état au parent.

## Menu contextuel (clic droit)

```html

<app-configurable-list
  [rowContextMenuEnabled]="true"
  [rowContextMenuTemplate]="rowMenu"
  (rowContextMenu)="onRowContextMenu($event)"
/>
<ng-template #rowMenu let-row>
  <button mat-menu-item (click)="openDetails(row)">
    <mat-icon>visibility</mat-icon>
    <span>Voir</span>
  </button>
  <button mat-menu-item (click)="printFiche(row)">
    <mat-icon>print</mat-icon>
    <span>Imprimer</span>
  </button>
</ng-template>
```

Le template reçoit la ligne cliquée via `let-row` (context `$implicit`/`row`). Le menu Material (`mat-menu`) est positionné au point de clic exact — l'ancre technique (`#rowContextMenuAnchor`) est reparentée dynamiquement dans `document.body` lors de l'ouverture, pour éviter le bug où un ancêtre avec `backdrop-filter` crée un nouveau *containing block* pour `position: fixed` et déplace le menu. Elle est retirée au `ngOnDestroy`.

## Vues sauvegardées ("Views")

Système permettant à l'utilisateur de sauvegarder l'état complet d'affichage de la liste (colonnes visibles, ordre des colonnes, tri, filtres, et éventuellement pagination) sous un nom, d'y revenir plus tard, d'en créer plusieurs, et de les supprimer.

### Activation

```html

<app-configurable-list
  [viewsEnabled]="true"
  [viewsStorageKey]="'patient-list'"
  [paginationEnabled]="true"
  [pageIndex]="pageIndex()"
  [pageSize]="pageSize()"
  (viewActivated)="onViewActivated($event)"
  (viewPaginationRestore)="onViewPaginationRestore($event)"
/>
```

La capture de pagination dans les vues **réutilise `[pageIndex]`/`[pageSize]`** — il n'y a pas d'input dédié aux vues. Si `paginationEnabled=false`, les vues sauvegardées n'embarquent tout simplement pas de pagination (`state.pageIndex`/`state.pageSize` restent `undefined`, `(viewPaginationRestore)` n'est jamais émis).

- `[viewsEnabled]="false"` (défaut) masque entièrement le bouton "Vues" et son menu — aucune UI, aucun coût.
- Un bouton "Vues" ouvre un menu : champ nom + bouton "Enregistrer la vue actuelle", puis la liste des vues existantes (activer / supprimer).
- À l'activation d'une vue, le composant réapplique en interne : visibilité des colonnes, ordre des colonnes, tri, filtres — et émet `filtersChange` pour rester cohérent si les filtres sont par ailleurs contrôlés par le parent.
- Au chargement du composant, **la dernière vue active est automatiquement réappliquée** (si le store en contient une).

### Persistance : non contrôlée (localStorage) vs contrôlée

**Mode non contrôlé (par défaut dès que `viewsStorageKey` est fourni)** — le composant lit/écrit lui-même dans `localStorage`, sous la clé namespacée `` `configurable-list.views.${viewsStorageKey}` `` (ex. `configurable-list.views.patient-list`). Rien à faire côté parent au-delà du binding ci-dessus : créer, activer, supprimer une vue persiste immédiatement et survit à un rechargement de page / une fermeture de navigateur (stockage propre au navigateur/poste de l'utilisateur — pas synchronisé entre appareils, pas partagé entre utilisateurs, pas de sauvegarde côté serveur).

**Mode contrôlé** — si vous fournissez `[viewsStore]` (non-`null`), le composant cesse totalement d'écrire dans `localStorage` : il se contente d'émettre `(viewsStoreChange)` à chaque modification, et c'est au parent de décider où stocker ça (appel API, fichier, etc.) puis de renvoyer la valeur mise à jour via le binding :

```html

<app-configurable-list
  [viewsEnabled]="true"
  [viewsStore]="viewsStore()"
  (viewsStoreChange)="onViewsStoreChange($event)"
/>
```

```ts
onViewsStoreChange(store
:
SharedListViewsStore
)
{
  this.viewsStore.set(store);
  this.viewsApi.save(store).subscribe(); // ex. persistance backend
}
```

### Point d'attention : colonnes contrôlées + vues

Si `columnVisibility` (et/ou `columnOrder`) est **contrôlé** par le parent (cas de `patient-list`), activer une vue ne suffit pas à faire réapparaître visuellement les bonnes colonnes : le composant a bien mis à jour son état interne au moment de l'activation, mais comme la visibilité est contrôlée, c'est la valeur du parent (`[columnVisibility]`) qui prévaut à chaque rendu. Il faut donc explicitement resynchroniser le signal du parent en écoutant `(viewActivated)` :

```ts
// patient-list.component.ts
onViewActivated(view
:
SharedListView | null
):
void {
  if(!
view
)
{
  return;
}
this.visibleColumns.set({...view.state.columnVisibility});
}
```

De la même façon, la pagination vit **en dehors** du composant (paginator séparé) : si une vue a été sauvegardée avec une page/pageSize (capturés depuis `[pageIndex]`/`[pageSize]` quand `paginationEnabled=true`), il faut écouter `(viewPaginationRestore)` pour la réappliquer à votre propre state de pagination :

```ts
onViewPaginationRestore(event
:
{
  pageIndex: number;
  pageSize: number
}
):
void {
  this.patientListStore.setPagination(event.pageIndex, event.pageSize);
}
```

### Types

```ts
interface SharedListViewState {
  columnVisibility: Record<string, boolean>;
  columnOrder: string[];
  sort: SharedListSortChange;
  filters: Record<string, string>;
  pageIndex?: number; // seulement si paginationEnabled=true
  pageSize?: number;
}

interface SharedListView {
  id: string;
  name: string;
  createdAt: string;
  updatedAt: string;
  state: SharedListViewState;
}

interface SharedListViewsStore {
  views: SharedListView[];
  activeViewId: string | null;
}
```

### Exemple complet (`patient-list`)

```html

<app-configurable-list
  (cellCopied)="onListCellCopied($event)"
  (columnVisibilityChange)="onColumnVisibilityChange($event)"
  (filteredCountChange)="onFilteredCountChange($event)"
  (pageIndexChange)="onPageIndexReset($event)"
  (remoteQueryChange)="onRemoteQueryChange($event)"
  (rowClick)="onListRowClick($event)"
  (viewActivated)="onViewActivated($event)"
  (viewPaginationRestore)="onViewPaginationRestore($event)"
  [columns]="allColumnDefs()"
  [columnVisibility]="visibleColumns()"
  [dataMode]="dataMode"
  [filters]="columnFilters()"
  [inlineFilterColumnIds]="patientInlineFilterColumnIds"
  [inlineFilters]="true"
  [pageIndex]="pageIndex()"
  [pageSize]="pageSize()"
  [paginationEnabled]="true"
  [rowSelectionEnabled]="true"
  [rowContextMenuEnabled]="true"
  [rowContextMenuTemplate]="patientRowContextMenu"
  [rows]="rows()"
  [showActiveFiltersBar]="true"
  [viewsEnabled]="true"
  [viewsStorageKey]="'patient-list'"
/>
```

Les handlers des **deux** modes sont bindés simultanément (`remoteQueryChange` pour remote ; `filteredCountChange`/`pageIndexChange` pour local) — sans risque, le composant n'émettant que ceux du mode réellement actif (`[dataMode]="dataMode"`, le flag du composant). `[pageIndex]`/`[pageSize]` servent à la fois pour la pagination (les deux modes) et pour les vues (capturés dans `SharedListViewState`) — un seul couple d'inputs pour tous ces usages.

## Copie de cellule

```ts
{
  id: 'code',
...,
  copy: true
}
// ou
{
  id: 'code',
...,
  copy: {
    valueAccessor: (row) => row.code.toUpperCase(), tooltipKey
  :
    'PATIENT_LIST.COPY_CODE'
  }
}
```

Affiche un petit bouton "copier" au survol de la cellule ; un feedback visuel bref confirme la copie. L'événement `(cellCopied)` est émis avec `{columnId, value, row}`.

## Responsive

- En dessous de `760px` de large (`isMobileView()`), la table bascule en vue mobile : seule la colonne marquée `mobileRowActions: true` reste visible en tant que colonne d'actions condensée, la poignée de drag-and-drop des colonnes est désactivée.
- Au-dessus du seuil, si la somme des largeurs de colonnes dépasse `minTableWidthPx`, un scroll horizontal apparaît plutôt que de compresser les colonnes.

## Filtres : menu vs inline

- Par défaut (`inlineFilters=false`), chaque colonne filtrable a une icône dans l'en-tête ouvrant un mini-menu de filtre.
- `inlineFilters=true` : le filtre est rendu directement dans la cellule d'en-tête (pas de menu). `inlineFilterColumnIds` permet de restreindre ce mode à certaines colonnes seulement (les autres restent en mode menu).
- `showActiveFiltersBar=true` ajoute sous la table une barre récapitulative des filtres actifs avec suppression individuelle en un clic.
- `showResetFilters`/`resetFiltersLabelKey` contrôlent le bouton global de réinitialisation.

### Debounce des filtres texte

Les filtres de type `text` (saisie libre) sont **debouncés à 350 ms** avant de committer dans `columnFilters` et de déclencher `filtersChange`/`onQueryStateChanged` (donc `remoteQueryChange` en mode `remote`) — sans ça, chaque caractère tapé recalculerait le filtrage local et, en mode distant, partirait en requête serveur. Implémenté avec `groupBy` (un flux indépendant par colonne, pour que taper dans "Nom" ne réinitialise pas le timer de "Prénom") + `mergeMap(group => group.pipe(debounceTime(350)))`, avec un système d'epoch par colonne pour qu'un clic sur "effacer" (immédiat) ne soit jamais écrasé par une frappe encore en attente de debounce.

Les filtres à choix fixe (`select`/`enum`/`boolean`/`date`) commitent **immédiatement**, sans debounce — ce sont des actions ponctuelles (clic), pas une saisie continue.

### Le jeu de filtres est construit par le composant, pas par le consommateur

`columnFilters` (l'état interne qui alimente `filtersChange`/`remoteQueryChange`/`displayedRows()`) est **toujours dérivé de `columns()`** : à chaque changement de la liste de colonnes, le composant s'assure lui-même qu'il existe une entrée (`''` par défaut) pour **chaque colonne ayant un `filter` ou un `filterPredicate`**, et supprime celles des colonnes retirées/renommées. Cela s'applique aussi bien en mode non contrôlé qu'en mode contrôlé (`[filters]` fourni — la valeur du parent est complétée avec les clés manquantes, jamais tronquée).

Conséquence pratique : **le consommateur n'a jamais besoin d'énumérer lui-même les colonnes filtrables**. `filtersChange`/`remoteQueryChange` exposent systématiquement un objet complet et prévisible, clé par `id` de colonne — un backend générique peut le transmettre tel quel (voir l'exemple `listPatients()` dans `backend-api.service.ts`, qui fait un passthrough générique `Object.entries(filters)` plutôt que d'énumérer chaque champ à la main : ajouter une nouvelle colonne filtrable au tableau suffit, tant que son `id` correspond au nom du champ attendu côté backend — aucune plomberie supplémentaire à écrire pour chaque nouvelle colonne).

## Points d'attention

- **Performance** : `displayedRows()` (filtrage + tri) est un `computed()` recalculé à chaque changement de `rows`/`columns`/filtres/tri — pour de très gros volumes, préférez la pagination/filtrage côté serveur et ne passez à la table que la page courante.
- **`rowKeyAccessor`** : fortement recommandé dès que `rowSelectionEnabled`, `expandedRowKeys`, ou les vues sont utilisés sans un `row.id` fiable et stable.
- **i18n** : toutes les clés (`headerKey`, `resetFiltersLabelKey`, `emptyLabelKey`, boutons "Colonnes"/"Vues"...) doivent exister dans les 4 fichiers de locale (`fr.json`, `en.json`, `ar.json`, `kab.json`), dans le bloc `"COMMON"` de premier niveau pour les clés partagées par le composant lui-même.
- **CDK Drag & Drop non utilisé** : volontairement, pour la raison expliquée dans la section "Ordre des colonnes" — ne pas réintroduire `@angular/cdk/drag-drop` sur ce composant sans revalider que la mise en page `<table>` n'en souffre pas.

## Fichiers liés

- `configurable-list.component.ts` / `.html` / `.css` — le composant.
- `column-filter-renderer.component.ts` — rendu standard des filtres (`ColumnFilterType`, options, etc.).
- `dynamic-filter-host.component.ts` — hébergement d'un composant de filtre custom.
- `select-filter.component.ts` — dépendance interne de `column-filter-renderer.component.ts` (rendu des filtres `enum`/`boolean`).
- Exemple d'intégration le plus complet à date : `frontend/src/app/features/patient/patient-list.component.ts` / `.html`.

## Portabilité vers un autre projet

Le composant n'a **aucune dépendance métier** (aucune référence à "patient", "centre", "dialyse"...) — il ne connaît que des concepts de liste génériques. Pour le réutiliser tel quel dans un autre projet Angular, il faut copier ces **4 fichiers `.ts`** (+ leurs `.html`/`.css`) : `configurable-list.component.*`, `column-filter-renderer.component.ts`, `dynamic-filter-host.component.ts`, `select-filter.component.ts`. Aucun autre fichier du projet n'est requis.

**Dépendances npm** (peer, à avoir dans le projet cible) : `@angular/core`, `@angular/common`, `@angular/material` (`button`, `checkbox`, `icon`, `menu`, `table`, `tooltip`, `form-field`, `input` — pour les filtres), `@ngx-translate/core`, `rxjs`. Rien d'autre.

**Deux points de configuration à prévoir dans le projet cible** (pas des bugs, des prérequis d'intégration) :

- **i18n** : le composant utilise en dur un petit jeu de clés `COMMON.*` (`COLUMNS_BUTTON`, `VIEWS_BUTTON`, `RESET_FILTERS_BUTTON`, `CLEAR_FILTER`, `ACTIVE_FILTERS`, `FILTER_BY`, `REF_OPTIONS_EMPTY`, `REF_OPTIONS_LOAD_ERROR`, `REF_OPTIONS_LOADING`) plus `NO_DATA` (configurable via `emptyLabelKey`) et `SEARCH` (dans `select-filter.component.ts`) — à définir dans les fichiers de traduction du nouveau projet (`@ngx-translate` doit être configuré).
- **CSS** : `configurable-list.component.css` référence des variables CSS custom (`--app-*`) définies par le thème de ce projet, sans fallback — voir la section détaillée ci-dessous pour la liste complète et des exemples prêts à copier.

Corrigé au passage pour cette portabilité : un import mort de `HemodialysisLoaderComponent` (jamais utilisé dans le template) a été retiré, et la valeur par défaut de `resetFiltersLabelKey` (qui pointait vers `PATIENT_LIST.RESET_FILTERS_BUTTON`, une clé propre à cette app) a été remplacée par une clé générique `COMMON.RESET_FILTERS_BUTTON`, ajoutée aux 4 fichiers de langue (`fr`/`en`/`ar`/`kab`) avec le même texte qu'avant — aucun changement visible sur les écrans existants.

## Guide d'intégration — variables CSS à définir dans le projet cible

`configurable-list.component.css` ne définit **aucune** de ces variables lui-même — il les consomme via `var(--app-xxx)` **sans valeur de repli**. Si elles n'existent pas dans le projet cible, le navigateur traite la déclaration CSS concernée comme invalide (bordure/couleur/fond non appliqués), le composant reste utilisable mais visuellement "nu". Il suffit de les définir une fois, globalement (`:root` ou équivalent), avec vos propres couleurs.

### Liste complète (14 variables `--app-*`)

| Variable                | Rôle dans le composant                                              | Exemple clair (light)               | Exemple sombre (dark)             |
|-------------------------|---------------------------------------------------------------------|-------------------------------------|-----------------------------------|
| `--app-border`          | Bordures fines (cellules, séparateurs, contour des puces de filtre) | `rgba(15, 23, 42, 0.13)`            | `rgba(151, 196, 206, 0.14)`       |
| `--app-border-strong`   | Bordures plus marquées (panneau de filtre, menu déroulant)          | `rgba(15, 23, 42, 0.24)`            | `rgba(151, 196, 206, 0.28)`       |
| `--app-surface`         | Fond des panneaux/menus (colonnes, vues)                            | `#ffffff`                           | `rgba(10, 28, 36, 0.78)`          |
| `--app-surface-soft`    | Fond légèrement teinté (lignes alternées, zones secondaires)        | `#f7fbfe`                           | `rgba(15, 40, 49, 0.72)`          |
| `--app-filter-panel-bg` | Fond du panneau de filtre par colonne (menu popup)                  | `#ffffff`                           | `rgba(9, 25, 33, 0.98)`           |
| `--app-field-bg`        | Fond des champs de saisie (filtres inline, recherche)               | `#ffffff`                           | `rgba(255, 255, 255, 0.06)`       |
| `--app-hover-surface`   | Fond au survol d'une ligne/option                                   | `#f6fbff`                           | `rgba(255, 255, 255, 0.08)`       |
| `--app-text`            | Texte principal                                                     | `#0d1d26`                           | `#eff8fb`                         |
| `--app-muted`           | Texte secondaire (labels, icônes inactives, placeholders)           | `#4f6573`                           | `#8aa8b3`                         |
| `--app-primary`         | Couleur d'accent (icônes actives, tri actif, bouton principal)      | `#3a7ca5`                           | `#61d8df`                         |
| `--app-primary-soft`    | Fond teinté à l'accent (puce de filtre actif, ligne sélectionnée)   | `rgba(58, 124, 165, 0.16)`          | `rgba(97, 216, 223, 0.14)`        |
| `--app-primary-outline` | Contour teinté à l'accent (focus, drag-over)                        | `rgba(58, 124, 165, 0.34)`          | `rgba(97, 216, 223, 0.38)`        |
| `--app-shadow-soft`     | Ombre portée (panneau de filtre, menus)                             | `0 8px 20px rgba(15, 23, 42, 0.09)` | `0 16px 48px rgba(0, 0, 0, 0.24)` |
| `--app-blur`            | Flou du fond derrière un panneau flottant (`backdrop-filter`)       | `0px`                               | `18px`                            |

Le composant utilise aussi `var(--mat-sys-error)` (badges/icônes d'erreur) — c'est un token **standard** généré automatiquement par le système de thématisation Angular Material (`mat.all-component-themes(...)`), pas une variable à définir vous-même : tant qu'un thème Material est appliqué globalement, il existe déjà.

### Exemple minimal — thème clair uniquement (à copier dans vos styles globaux)

```css
:root {
  --app-border: rgba(15, 23, 42, 0.13);
  --app-border-strong: rgba(15, 23, 42, 0.24);
  --app-surface: #ffffff;
  --app-surface-soft: #f7fbfe;
  --app-filter-panel-bg: #ffffff;
  --app-field-bg: #ffffff;
  --app-hover-surface: #f6fbff;
  --app-text: #0d1d26;
  --app-muted: #4f6573;
  --app-primary: #3a7ca5;
  --app-primary-soft: rgba(58, 124, 165, 0.16);
  --app-primary-outline: rgba(58, 124, 165, 0.34);
  --app-shadow-soft: 0 8px 20px rgba(15, 23, 42, 0.09);
  --app-blur: 0px;
}
```

Remplacez simplement `#3a7ca5` par la couleur d'accent de votre marque : c'est la seule teinte qui pilote `--app-primary`/`--app-primary-soft`/`--app-primary-outline` (gardez le même ratio d'opacité pour les deux variantes `-soft`/`-outline`, ~16 % et ~34 %, ça fonctionne avec n'importe quelle couleur de base).

### Exemple avec support du mode sombre

Le pattern utilisé dans ce projet ([`styles.scss`](../../../styles.scss)) bascule sur un attribut `data-mode` posé sur `<html>` — adaptable à n'importe quel mécanisme de dark mode (classe, `prefers-color-scheme`, etc.) :

```css
:root,
html[data-mode='light'] {
  --app-border: rgba(15, 23, 42, 0.13);
  --app-border-strong: rgba(15, 23, 42, 0.24);
  --app-surface: #ffffff;
  --app-surface-soft: #f7fbfe;
  --app-filter-panel-bg: #ffffff;
  --app-field-bg: #ffffff;
  --app-hover-surface: #f6fbff;
  --app-text: #0d1d26;
  --app-muted: #4f6573;
  --app-primary: #3a7ca5;
  --app-primary-soft: rgba(58, 124, 165, 0.16);
  --app-primary-outline: rgba(58, 124, 165, 0.34);
  --app-shadow-soft: 0 8px 20px rgba(15, 23, 42, 0.09);
  --app-blur: 0px;
}

html[data-mode='dark'] {
  --app-border: rgba(151, 196, 206, 0.14);
  --app-border-strong: rgba(151, 196, 206, 0.28);
  --app-surface: rgba(10, 28, 36, 0.78);
  --app-surface-soft: rgba(15, 40, 49, 0.72);
  --app-filter-panel-bg: rgba(9, 25, 33, 0.98);
  --app-field-bg: rgba(255, 255, 255, 0.06);
  --app-hover-surface: rgba(255, 255, 255, 0.08);
  --app-text: #eff8fb;
  --app-muted: #8aa8b3;
  --app-primary: #61d8df;
  --app-primary-soft: rgba(97, 216, 223, 0.14);
  --app-primary-outline: rgba(97, 216, 223, 0.38);
  --app-shadow-soft: 0 16px 48px rgba(0, 0, 0, 0.24);
  --app-blur: 18px;
}
```

### Vérifier que tout est bien branché

Après avoir collé l'un des deux blocs ci-dessus, ouvrez la liste et vérifiez à l'œil : bordures de cellules visibles, en-tête de colonne triée en couleur d'accent, panneau de filtre avec un fond et une ombre (pas transparent/collé au reste de la page), puce "filtre actif" teintée. Si une de ces zones reste sans couleur, il manque très probablement la variable correspondante dans le tableau ci-dessus — inspectez l'élément dans les DevTools : une valeur `var(--app-xxx)` qui ne se résout pas apparaît comme propriété invalide (barrée) dans l'onglet Styles.
