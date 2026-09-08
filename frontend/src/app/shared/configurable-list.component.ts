import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  HostListener,
  OnDestroy,
  TemplateRef,
  TrackByFunction,
  Type,
  ViewEncapsulation,
  computed,
  effect,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {MatCheckboxChange, MatCheckboxModule} from '@angular/material/checkbox';
import {MatIconModule} from '@angular/material/icon';
import {MatMenuModule, MatMenuTrigger} from '@angular/material/menu';
import {MatTableModule} from '@angular/material/table';
import {MatTooltipModule} from '@angular/material/tooltip';
import {TranslateModule} from '@ngx-translate/core';
import {firstValueFrom, Observable, Subject, Subscription} from 'rxjs';
import {debounceTime, groupBy, mergeMap} from 'rxjs/operators';
import {ColumnFilterRendererComponent, ColumnFilterType} from './column-filter-renderer.component';
import {DynamicFilterHostComponent} from './dynamic-filter-host.component';
import {HemodialysisLoaderComponent} from './hemodialysis-loader.component';

export type SortDirection = 'asc' | 'desc' | '';

/**
 * `local`: tri/filtrage appliqués côté client sur `rows()` (défaut, adapté aux
 * petites listes qui ne changent pas souvent).
 * `remote`: `rows()` est considéré déjà trié/filtré/paginé par le serveur — le
 * composant se contente d'afficher tel quel et notifie chaque changement de tri/filtre
 * via `(remoteQueryChange)` pour que le parent puisse relancer la requête.
 */
export type SharedListDataMode = 'local' | 'remote';

/** Etat complet à envoyer au serveur en mode `remote` (tri courant, tous les filtres, et la page). */
export interface SharedListRemoteQuery {
  sort: SharedListSortChange;
  filters: Record<string, string>;
  page: { index: number; size: number };
}

export type SharedListFilterOption = { value: string; label: string };

/**
 * Configuration de filtrage d'une colonne.
 *
 * - `type`, `options` et `placeholder` alimentent le rendu standard via
 *   `ColumnFilterRendererComponent`.
 * - `optionsLoader` permet de charger des options a la demande (menu) ou en eager
 *   quand `inlineFilters=true`.
 * - `component`/`componentInputs` permettent de brancher un renderer de filtre custom
 *   via `DynamicFilterHostComponent`.
 */
export interface SharedListFilterConfig {
  type?: ColumnFilterType;
  options?: SharedListFilterOption[];
  optionsLoader?: () => Observable<SharedListFilterOption[]> | Promise<SharedListFilterOption[]>;
  placeholder?: string;
  labelKey?: string;
  component?: Type<unknown>;
  componentInputs?: Record<string, unknown>;
}

export interface SharedListColumn<T> {
  id: string;
  headerKey: string;
  valueAccessor: (row: T) => unknown;
  visible?: boolean;
  sortable?: boolean;
  resizable?: boolean;
  widthPx?: number;
  minWidthPx?: number;
  maxWidthPx?: number;
  cellTemplate?: TemplateRef<{ $implicit: T; row: T; value: unknown; column: SharedListColumn<T> }>;
  sortValueAccessor?: (row: T) => string | number | boolean | Date | null | undefined;
  filter?: SharedListFilterConfig;
  filterPredicate?: (row: T, filterValue: string) => boolean;
  mobileRowActions?: boolean;
  copy?:
    | boolean
    | {
    valueAccessor?: (row: T) => string;
    tooltipKey?: string;
  };
}

export interface SharedListSortChange {
  columnId: string;
  direction: SortDirection;
}

export interface SharedListCopyEvent<T = any> {
  columnId: string;
  value: string;
  row: T;
}

export interface SharedListDetailToggleEvent<T = any> {
  row: T;
  expanded: boolean;
  expandedKeys: unknown[];
}

export interface SharedListSelectionChangeEvent<T = any> {
  row: T | null;
  selected: boolean;
  selectedKeys: unknown[];
  selectedRows: T[];
}

export interface SharedListContextMenuEvent<T = any> {
  row: T;
  position: { x: number; y: number };
}

/** Everything a saved "view" captures about the list's presentation. */
export interface SharedListViewState {
  columnVisibility: Record<string, boolean>;
  columnOrder: string[];
  sort: SharedListSortChange;
  filters: Record<string, string>;
  /** Only populated when `paginationEnabled=true` (reuses `[pageIndex]`/`[pageSize]`). */
  pageIndex?: number;
  pageSize?: number;
}

export interface SharedListView {
  id: string;
  name: string;
  createdAt: string;
  updatedAt: string;
  state: SharedListViewState;
}

/**
 * The whole "views" store for a list: every saved view plus which one is active.
 * By default the component persists this itself (localStorage, keyed by
 * `viewsStorageKey`). A parent that wants to persist it elsewhere (backend, file...)
 * can instead pass `[viewsStore]` (controlled mode) and listen to `(viewsStoreChange)`.
 */
export interface SharedListViewsStore {
  views: SharedListView[];
  activeViewId: string | null;
}

@Component({
  selector: 'app-configurable-list',
  standalone: true,
  imports: [
    CommonModule,
    MatTableModule,
    MatMenuModule,
    MatButtonModule,
    MatCheckboxModule,
    MatIconModule,
    MatTooltipModule,
    TranslateModule,
    ColumnFilterRendererComponent,
    DynamicFilterHostComponent,
    HemodialysisLoaderComponent,
  ],
  templateUrl: './configurable-list.component.html',
  styleUrl: './configurable-list.component.css',
  changeDetection: ChangeDetectionStrategy.Eager,
  encapsulation: ViewEncapsulation.None,
})
export class ConfigurableListComponent implements OnDestroy {
  /**
   * Composant de liste partagee, basee sur Angular Material Table.
   *
   * Fonctionnalites majeures:
   * - colonnes configurables (ordre defini par le parent, visibilite, resize)
   * - tri local
   * - filtrage local par colonne (renderer standard ou custom)
   * - copie rapide d'une cellule
   * - ligne detail expandable (mode interne ou controle)
   * - selection de lignes (checkbox, interne ou controle)
   * - support responsive (ligne d'actions mobile)
   *
   * Lien avec `ColumnFilterRendererComponent`:
   * - `configurable-list` decide QUAND/OÙ afficher le filtre (menu vs inline)
   * - `ColumnFilterRendererComponent` decide COMMENT capturer la valeur de filtre
   * - la valeur remonte via `(valueChange)` puis est stockee ici dans `columnFilters`
   * - le filtrage final s'applique dans `displayedRows()` via `matchesAllFilters()`
   */
  private static readonly FILTER_OPTIONS_EMPTY_ERROR_KEY = 'COMMON.REF_OPTIONS_EMPTY';
  private static readonly FILTER_OPTIONS_LOAD_ERROR_KEY = 'COMMON.REF_OPTIONS_LOAD_ERROR';

  /** Source des donnees (non filtrees/non triees), fournie par le parent. */
  readonly rows = input<any[]>([]);
  /** Definition des colonnes (valeur, tri, filtre, templates, largeur...). */
  readonly columns = input<SharedListColumn<any>[]>([]);
  /** Mode controle: visibilite des colonnes pilotee par le parent. */
  readonly columnVisibility = input<Record<string, boolean> | null>(null);
  /** Mode controle: ordre des colonnes (ids) pilote par le parent. */
  readonly columnOrder = input<ReadonlyArray<string> | null>(null);
  /** Mode controle: filtres pilotes par le parent. */
  readonly filters = input<Record<string, string> | null>(null);
  readonly emptyLabelKey = input('COMMON.NO_DATA');
  readonly minTableWidthPx = input(760);
  readonly rowClassFn = input<((row: any) => string | string[] | Record<string, boolean> | null) | null>(null);
  readonly rowTrackBy = input<TrackByFunction<any> | null>(null);
  /** Template de detail (master/detail). Quand null, pas de detail row. */
  readonly detailRowTemplate = input<TemplateRef<{ $implicit: any; row: any }> | null>(null);
  /**
   * Controlled mode: external predicate deciding whether the detail is expanded.
   * When provided, internal expansion state is bypassed entirely.
   */
  readonly detailRowWhen = input<((index: number, row: any) => boolean) | null>(null);
  /**
   * Controlled mode (key based): externally managed list of expanded row keys.
   * Keys are resolved with `rowKeyAccessor` / `rowTrackBy` / `row.id`.
   */
  readonly expandedRowKeys = input<ReadonlyArray<unknown> | null>(null);
  /** Uncontrolled mode: toggle the detail row when the data row is clicked. */
  readonly detailRowToggleOnRowClick = input(true);
  /** Uncontrolled mode: only one detail row expanded at a time. */
  readonly detailRowAccordion = input(false);
  /** Optional guard: rows for which a detail can be expanded (e.g. has children). */
  readonly detailRowCanExpand = input<((row: any) => boolean) | null>(null);
  /** Stable business key for a row (expansion state, copy feedback, trackBy fallback). */
  readonly rowKeyAccessor = input<((row: any) => unknown) | null>(null);
  /** Show/hide the built-in "reset all filters" button. */
  readonly showResetFilters = input(true);
  /** i18n key of the reset filters button (overridable per project). */
  readonly resetFiltersLabelKey = input('COMMON.RESET_FILTERS_BUTTON');
  /** Show a leading checkbox column to select one or many rows. */
  readonly rowSelectionEnabled = input(false);
  /** Controlled mode (key based): externally managed selected row keys. */
  readonly selectedRowKeys = input<ReadonlyArray<unknown> | null>(null);
  /** Render column filters inline inside the header cell (no filter icon/menu). */
  readonly inlineFilters = input(false);
  /** Optional list of column ids allowed to render inline filters (when inlineFilters=true). */
  readonly inlineFilterColumnIds = input<ReadonlyArray<string> | null>(null);
  /** Show a summary bar of the active filters below the list. */
  readonly showActiveFiltersBar = input(false);
  /** Enable right-click contextual menu on data rows. */
  readonly rowContextMenuEnabled = input(false);
  /** Context menu content provided by parent component. */
  readonly rowContextMenuTemplate = input<TemplateRef<{ $implicit: any; row: any }> | null>(null);

  /**
   * `local` (défaut): tri/filtres appliqués sur `rows()` côté client.
   * `remote`: `rows()` est affiché tel quel (déjà trié/filtré/paginé côté serveur) ;
   * tout changement de tri ou de filtre émet `(remoteQueryChange)` au lieu d'être
   * appliqué localement.
   */
  readonly dataMode = input<SharedListDataMode>('local');

  /**
   * Enables page-aware behavior. Off by default (back-compat: `displayedRows()` is
   * never sliced, exactly like before this feature existed).
   * - `local` mode: `displayedRows()` is sliced to `[pageIndex*pageSize, +pageSize)`
   *   after filter/sort, and `(filteredCountChange)` reports the post-filter total so
   *   your own paginator's `[length]` stays correct.
   * - `remote` mode: page is embedded (reset to `0`) in every `(remoteQueryChange)`
   *   triggered by a filter/sort change. Direct page navigation (your own paginator's
   *   `(page)` event) is NOT routed through this component — call your fetch method
   *   directly with the new page, same as before.
   */
  readonly paginationEnabled = input(false);
  /** Current page index (0-based). Only used when `paginationEnabled=true`. */
  readonly pageIndex = input(0);
  /** Current page size. Only used when `paginationEnabled=true`. */
  readonly pageSize = input(10);

  /** Show/hide the whole "views" toolbar (save/switch/delete). Off by default. */
  readonly viewsEnabled = input(false);
  /**
   * Uncontrolled mode: storage key used to persist the views store in `localStorage`
   * (namespaced automatically). Required for built-in persistence to do anything —
   * without it, views still work but only for the current page session.
   */
  readonly viewsStorageKey = input<string | null>(null);
  /** Controlled mode: parent owns the views store entirely (backend, file, etc.). */
  readonly viewsStore = input<SharedListViewsStore | null>(null);

  readonly rowClick = output<any>();
  /** Emitted whenever any filter value changes. */
  readonly filtersChange = output<Record<string, string>>();
  readonly sortChange = output<SharedListSortChange>();
  readonly cellCopied = output<SharedListCopyEvent>();
  readonly detailToggle = output<SharedListDetailToggleEvent>();
  /** Emitted when the internal column picker toggles a column visibility. */
  readonly columnVisibilityChange = output<Record<string, boolean>>();
  /** Emitted whenever the user drags a column header to a new position. */
  readonly columnOrderChange = output<string[]>();
  /** Emitted on row selection/unselection and select-all operations. */
  readonly selectionChange = output<SharedListSelectionChangeEvent>();
  /** Emitted when the contextual menu is requested on a row. */
  readonly rowContextMenu = output<SharedListContextMenuEvent>();
  /**
   * Emitted whenever the views store changes (saved, activated, deleted) — in
   * uncontrolled mode this mirrors what was just written to `localStorage`; in
   * controlled mode this is the ONLY place the change is reported, since the
   * component does not persist anything itself. Wire this to save wherever you want.
   */
  readonly viewsStoreChange = output<SharedListViewsStore>();
  /** Emitted whenever a view becomes active (user switch, or auto-activation on load). */
  readonly viewActivated = output<SharedListView | null>();
  /** Emitted when an activated view carries pagination — apply it to your own paginator. */
  readonly viewPaginationRestore = output<{ pageIndex: number; pageSize: number }>();
  /**
   * `dataMode='remote'` only: emitted with the full current sort + filters whenever
   * either changes (sort toggle, filter value, reset, or a saved view activating with
   * new sort/filters). Build your server request from this payload.
   */
  readonly remoteQueryChange = output<SharedListRemoteQuery>();
  /** `dataMode='local'` + `paginationEnabled=true` only: post-filter row count — bind to your paginator's `[length]`. */
  readonly filteredCountChange = output<number>();
  /** `dataMode='local'` + `paginationEnabled=true` only: emitted with `0` when a filter/sort change should reset the current page. */
  readonly pageIndexChange = output<number>();

  readonly columnsMenuItems = computed(() =>
    this.columns().filter((column) => column.id !== '__detail_row__' && column.id !== '__mobile_actions__'),
  );

  readonly viewsList = computed(() => this.effectiveViewsStore().views);
  readonly activeViewId = computed(() => this.effectiveViewsStore().activeViewId);
  readonly activeView = computed(() => this.viewsList().find((v) => v.id === this.activeViewId()) ?? null);

  readonly visibleColumns = computed(() => {
    const columns = this.columns();
    const visibility = this.effectiveColumnVisibility();

    const filtered = !visibility
      ? columns.filter((column) => column.visible !== false)
      : columns.filter((column) => visibility[column.id] ?? true);

    const order = this.effectiveColumnOrder();
    if (order.length === 0) {
      return filtered;
    }

    const orderIndex = new Map(order.map((id, index) => [id, index]));
    return [...filtered].sort((a, b) => {
      const indexA = orderIndex.has(a.id) ? orderIndex.get(a.id)! : Number.MAX_SAFE_INTEGER;
      const indexB = orderIndex.has(b.id) ? orderIndex.get(b.id)! : Number.MAX_SAFE_INTEGER;
      return indexA - indexB;
    });
  });
  readonly actionColumn = computed(() =>
    this.visibleColumns().find((column) => column.mobileRowActions) ?? null,
  );
  /** Local mode pipeline: rows source -> filtres -> tri (sans pagination). */
  private readonly filteredSortedRows = computed(() => {
    const sourceRows = this.rows();
    const activeColumns = this.visibleColumns();
    const filters = this.columnFilters();
    const sort = this.sortState();

    let nextRows = sourceRows.filter((row) => this.matchesAllFilters(row, activeColumns, filters));
    if (!sort.columnId || !sort.direction) {
      return nextRows;
    }

    const sortColumn = activeColumns.find((column) => column.id === sort.columnId);
    if (!sortColumn) {
      return nextRows;
    }

    nextRows = [...nextRows].sort((left, right) => {
      const leftValue = this.getSortValue(left, sortColumn);
      const rightValue = this.getSortValue(right, sortColumn);
      const compareResult = this.compareSortValues(leftValue, rightValue);
      return sort.direction === 'asc' ? compareResult : -compareResult;
    });

    return nextRows;
  });

  readonly displayedRows = computed(() => {
    if (this.dataMode() === 'remote') {
      // Le serveur a déjà filtré/trié/paginé — on affiche tel quel.
      return this.rows();
    }

    const filteredSorted = this.filteredSortedRows();
    if (!this.paginationEnabled()) {
      return filteredSorted;
    }

    const size = this.pageSize();
    if (!size || size <= 0) {
      return filteredSorted;
    }

    const start = this.pageIndex() * size;
    return filteredSorted.slice(start, start + size);
  });
  protected readonly isMobileView = signal(
    typeof window !== 'undefined' ? window.innerWidth <= 760 : false,
  );
  /** Etat interne de selection quand `selectedRowKeys` n'est pas fourni. */
  protected readonly internalSelectedKeys = signal<ReadonlySet<unknown>>(new Set());
  private readonly mobileActionsColumnId = '__mobile_actions__';
  protected readonly contextMenuRow = signal<any | null>(null);

  readonly hasColumns = computed(() => this.displayedColumnIds().length > 0);
  protected readonly columnFilters = signal<Record<string, string>>({});
  /** Summary of currently active filters (for the bottom bar). */
  readonly activeFilterSummaries = computed<Array<{ columnId: string; labelKey: string; value: string }>>(() => {
    const filters = this.columnFilters();
    const summaries: Array<{ columnId: string; labelKey: string; value: string }> = [];
    for (const column of this.columns()) {
      const rawValue = (filters[column.id] ?? '').trim();
      if (!rawValue) {
        continue;
      }
      summaries.push({
        columnId: column.id,
        labelKey: column.filter?.labelKey ?? column.headerKey,
        value: this.formatFilterValueForDisplay(column, rawValue),
      });
    }
    return summaries;
  });
  protected readonly sortState = signal<SharedListSortChange>({columnId: '', direction: ''});
  /** Native HTML5 drag-and-drop state for column reordering (id of the column being dragged / hovered). */
  protected readonly draggingColumnId = signal<string | null>(null);
  protected readonly dragOverColumnId = signal<string | null>(null);
  protected readonly copiedCellKey = signal<string | null>(null);
  protected readonly lazyFilterOptions = signal<Record<string, SharedListFilterOption[]>>({});
  protected readonly lazyFilterLoading = signal<Record<string, boolean>>({});
  protected readonly lazyFilterErrors = signal<Record<string, string>>({});
  readonly mobileActionRowColumns = computed(() => {
    if (!this.isMobileView()) {
      return [] as string[];
    }
    return this.actionColumn() ? [this.mobileActionsColumnId] : [];
  });
  readonly detailRowColumns = ['__detail_row__'];
  /** Uncontrolled expansion state (row keys currently expanded). */
  protected readonly internalExpandedKeys = signal<ReadonlySet<unknown>>(new Set());
  protected readonly columnWidths = signal<Record<string, number>>({});
  protected readonly contextMenuPosition = signal<{ x: number; y: number }>({x: 0, y: 0});
  protected readonly contextMenuTriggerRef = viewChild<MatMenuTrigger>('rowContextMenuTrigger');
  private readonly selectionColumnId = '__row_selection__';
  readonly displayedColumnIds = computed(() => {
    // Colonne technique de selection injectee en tete quand activee.
    const ids = this.visibleColumns().map((column) => column.id);
    if (!this.isMobileView()) {
      return this.rowSelectionEnabled() ? [this.selectionColumnId, ...ids] : ids;
    }

    const actionColumn = this.actionColumn();
    if (!actionColumn) {
      return ids;
    }

    const withoutActions = ids.filter((id) => id !== actionColumn.id);
    const mobileIds = withoutActions.length > 0 ? withoutActions : ids;
    return this.rowSelectionEnabled() ? [this.selectionColumnId, ...mobileIds] : mobileIds;
  });
  /**
   * Free-typed filter keystrokes flow through here instead of committing straight to
   * `columnFilters`. Grouped by column so typing in one field never resets another
   * field's debounce timer (`groupBy` + `mergeMap` keeps each column's debounce
   * independent), then `debounceTime` collapses rapid keystrokes into one commit —
   * which in `remote` mode means one request instead of one per character.
   */
  private readonly filterInputSubject = new Subject<{ columnId: string; value: string; epoch: number }>();
  private filterInputSubscription: Subscription | null = null;
  private readonly filterEpochByColumn = new Map<string, number>();
  private readonly internalColumnVisibility = signal<Record<string, boolean>>({});
  private readonly internalColumnOrder = signal<string[]>([]);
  protected readonly newViewName = signal('');
  private readonly internalViewsStore = signal<SharedListViewsStore>({views: [], activeViewId: null});
  private hasLoadedInitialViewsStore = false;
  private readonly activeFilterColumnId = signal<string | null>(null);
  private readonly activeFilterTrigger = signal<MatMenuTrigger | null>(null);

  private readonly collator = new Intl.Collator('fr', {numeric: true, sensitivity: 'base'});
  private resizingState: { columnId: string; startX: number; startWidth: number } | null = null;

  constructor() {
    this.filterInputSubscription = this.filterInputSubject
      .pipe(
        groupBy((entry) => entry.columnId),
        mergeMap((group) => group.pipe(debounceTime(350))),
      )
      .subscribe((entry) => {
        if (entry.epoch !== this.filterEpoch(entry.columnId)) {
          return; // superseded by a clear/reset that happened while this keystroke was debouncing
        }
        this.commitFilterValue(entry.columnId, entry.value);
      });

    effect(() => {
      const externalFilters = this.filters();
      if (!externalFilters) {
        return;
      }
      this.columnFilters.set(this.withDefaultFilterKeys(externalFilters));
      this.requestFilterPositionUpdate();
    });

    effect(() => {
      const externalVisibility = this.columnVisibility();
      if (!externalVisibility) {
        return;
      }
      this.internalColumnVisibility.set({...externalVisibility});
    });

    effect(() => {
      const externalOrder = this.columnOrder();
      if (!externalOrder) {
        return;
      }
      this.internalColumnOrder.set([...externalOrder]);
    });

    effect(() => {
      // Defensive sync: suit la liste des colonnes courantes.
      const columns = this.columns();
      this.internalColumnVisibility.update((current) => {
        const next = {...current};
        const ids = new Set(columns.map((column) => column.id));
        for (const key of Object.keys(next)) {
          if (!ids.has(key)) {
            delete next[key];
          }
        }
        for (const column of columns) {
          if (next[column.id] === undefined) {
            next[column.id] = column.visible !== false;
          }
        }
        return next;
      });

      // Drop stale ids from the drag order (removed/renamed columns) without
      // resetting the whole order — newly seen columns simply fall back to
      // their natural position via visibleColumns()'s MAX_SAFE_INTEGER fallback.
      this.internalColumnOrder.update((current) => {
        if (current.length === 0) {
          return current;
        }
        const ids = new Set(columns.map((column) => column.id));
        const pruned = current.filter((id) => ids.has(id));
        return pruned.length === current.length ? current : pruned;
      });

      // Le composant construit lui-même le jeu de filtres à partir de `columns()` —
      // indépendamment de ce que le parent branche (ou non) sur [filters]. Chaque
      // colonne filtrable obtient une entrée (défaut '') dans `columnFilters`, et les
      // colonnes retirées/renommées voient la leur nettoyée. Ainsi `filtersChange` /
      // `remoteQueryChange` exposent toujours un jeu complet, prévisible, et
      // directement exploitable par n'importe quel consommateur sans qu'il ait à
      // connaître/répéter la liste des colonnes filtrables lui-même.
      this.columnFilters.update((current) => this.withDefaultFilterKeys(current));
    });

    effect(() => {
      const nextColumns = this.visibleColumns();
      this.columnWidths.update((current) => {
        const existing = new Set(nextColumns.map((column) => column.id));
        const next: Record<string, number> = {};

        for (const [columnId, width] of Object.entries(current)) {
          if (existing.has(columnId)) {
            next[columnId] = width;
          }
        }

        for (const column of nextColumns) {
          if (next[column.id] !== undefined) {
            continue;
          }
          if (column.widthPx && column.widthPx > 0) {
            next[column.id] = column.widthPx;
          }
        }

        return next;
      });

      this.requestFilterPositionUpdate();
    });

    effect(() => {
      this.displayedRows().length;
      this.requestFilterPositionUpdate();
    });

    effect(() => {
      if (this.dataMode() !== 'local' || !this.paginationEnabled()) {
        return;
      }
      this.filteredCountChange.emit(this.filteredSortedRows().length);
    });

    // Inline mode: lazy filter options must be loaded eagerly since there is no menu-open event.
    effect(() => {
      if (!this.inlineFilters()) {
        return;
      }
      for (const column of this.visibleColumns()) {
        if (column.filter?.optionsLoader) {
          void this.ensureLazyFilterOptions(column.id, column.filter);
        }
      }
    });

    // Views: the active view is auto-applied exactly once, on the first time a store
    // becomes available (controlled: parent-supplied input; uncontrolled: localStorage
    // read) — never again afterwards, so the user's later edits are never silently
    // reverted by a stale re-read or an echoed store update from the parent.
    effect(() => {
      if (!this.viewsEnabled()) {
        return;
      }
      const external = this.viewsStore();
      if (external) {
        this.internalViewsStore.set(external);
        if (!this.hasLoadedInitialViewsStore) {
          this.hasLoadedInitialViewsStore = true;
          this.applyActiveView(external);
        }
        return;
      }
      if (this.hasLoadedInitialViewsStore) {
        return;
      }
      const key = this.viewsStorageKey();
      if (!key) {
        return;
      }
      this.hasLoadedInitialViewsStore = true;
      const loaded = this.loadViewsStoreFromLocalStorage(key);
      this.internalViewsStore.set(loaded);
      this.applyActiveView(loaded);
    });
  }

  ngOnDestroy(): void {
    this.stopResize();
    this.filterInputSubscription?.unsubscribe();
  }

  onHeaderSort(column: SharedListColumn<any>): void {
    if (!column.sortable) {
      return;
    }

    const current = this.sortState();
    const isSameColumn = current.columnId === column.id;

    let nextDirection: SortDirection = 'asc';
    if (isSameColumn && current.direction === 'asc') {
      nextDirection = 'desc';
    } else if (isSameColumn && current.direction === 'desc') {
      nextDirection = '';
    }

    const nextState: SharedListSortChange = {
      columnId: nextDirection ? column.id : '',
      direction: nextDirection,
    };
    this.sortState.set(nextState);
    this.sortChange.emit(nextState);
    this.onQueryStateChanged();
  }

  /**
   * `text` filters are free-typed — every keystroke would otherwise re-run local
   * filtering and, in `remote` mode, fire a request per character. Those are
   * debounced (see `filterInputSubject`); discrete selections (select/enum/boolean/
   * date pickers) commit immediately since they're single deliberate actions.
   */
  onFilterValue(columnId: string, value: string): void {
    if (!this.isFreeTypedFilter(columnId)) {
      this.commitFilterValue(columnId, value);
      return;
    }
    this.filterInputSubject.next({columnId, value, epoch: this.filterEpoch(columnId)});
  }

  clearFilter(columnId: string): void {
    this.bumpFilterEpoch(columnId); // supersede any debounced keystroke still in flight for this column
    this.commitFilterValue(columnId, '');
  }

  clearAllFilters(): void {
    for (const column of this.columns()) {
      this.bumpFilterEpoch(column.id);
    }
    const next: Record<string, string> = {};
    this.columnFilters.set(next);
    this.filtersChange.emit(next);
    this.onQueryStateChanged();
  }

  /**
   * Saves the list's current presentation (columns, order, sort, filters, and
   * pagination when `paginationEnabled=true`) as a view. If a view with the same name
   * already exists, it is overwritten in place; otherwise a new view is created.
   * Either way the saved view becomes the active one.
   */
  saveCurrentAsView(name: string): void {
    const trimmed = name.trim();
    if (!trimmed) {
      return;
    }

    const state: SharedListViewState = {
      columnVisibility: {...this.effectiveColumnVisibility()},
      columnOrder: [...this.effectiveColumnOrder()],
      sort: {...this.sortState()},
      filters: {...this.columnFilters()},
      ...(this.paginationEnabled() ? {pageIndex: this.pageIndex(), pageSize: this.pageSize()} : {}),
    };

    const store = this.effectiveViewsStore();
    const now = new Date().toISOString();
    const existing = store.views.find((v) => v.name === trimmed);

    let nextViews: SharedListView[];
    let activeViewId: string;
    if (existing) {
      activeViewId = existing.id;
      nextViews = store.views.map((v) => (v.id === existing.id ? {...v, state, updatedAt: now} : v));
    } else {
      const created: SharedListView = {id: this.generateViewId(), name: trimmed, createdAt: now, updatedAt: now, state};
      activeViewId = created.id;
      nextViews = [...store.views, created];
    }

    this.commitViewsStore({views: nextViews, activeViewId});
    this.newViewName.set('');
  }

  private isFreeTypedFilter(columnId: string): boolean {
    const column = this.columns().find((c) => c.id === columnId);
    return (column?.filter?.type ?? 'text') === 'text';
  }

  private filterEpoch(columnId: string): number {
    return this.filterEpochByColumn.get(columnId) ?? 0;
  }

  private bumpFilterEpoch(columnId: string): void {
    this.filterEpochByColumn.set(columnId, this.filterEpoch(columnId) + 1);
  }

  private commitFilterValue(columnId: string, value: string): void {
    this.columnFilters.update((current) => {
      const next = {...current, [columnId]: value ?? ''};
      this.filtersChange.emit(next);
      return next;
    });
    this.onQueryStateChanged();
  }

  isFilterActive(columnId: string): boolean {
    return !!(this.columnFilters()[columnId] ?? '').trim();
  }

  currentFilterValue(columnId: string): string {
    return this.columnFilters()[columnId] ?? '';
  }

  /** i18n key displayed as title of the filter menu (column label). */
  filterMenuTitle(column: SharedListColumn<any>): string {
    return column.filter?.labelKey ?? column.headerKey ?? '';
  }

  onResizeStart(event: MouseEvent, column: SharedListColumn<any>): void {
    if (!column.resizable) {
      return;
    }

    // Let dblclick trigger auto-fit without initiating a drag cycle.
    if (event.detail > 1) {
      return;
    }

    event.preventDefault();
    event.stopPropagation();

    const widthMap = this.columnWidths();
    const startWidth = widthMap[column.id] ?? column.widthPx ?? 180;
    this.resizingState = {
      columnId: column.id,
      startX: event.clientX,
      startWidth,
    };

    document.addEventListener('mousemove', this.onMouseMoveBound);
    document.addEventListener('mouseup', this.onMouseUpBound);
  }

  onResizeAutoFit(event: MouseEvent, column: SharedListColumn<any>): void {
    if (!column.resizable) {
      return;
    }

    event.preventDefault();
    event.stopPropagation();

    const handle = event.target as HTMLElement | null;
    const table = handle?.closest('table.shared-table') as HTMLElement | null;
    if (!table) {
      return;
    }

    const selector = `.mat-column-${this.escapeCssToken(column.id)}`;
    const cells = Array.from(table.querySelectorAll<HTMLElement>(selector));
    if (cells.length === 0) {
      return;
    }

    let measured = 0;
    for (const cell of cells) {
      const preferredNode =
        (cell.querySelector('.header-button') as HTMLElement | null)
        ?? (cell.querySelector('.th-wrap') as HTMLElement | null)
        ?? (cell.querySelector('.cell-content') as HTMLElement | null)
        ?? cell;

      const computed = window.getComputedStyle(cell);
      const padding = (parseFloat(computed.paddingLeft) || 0) + (parseFloat(computed.paddingRight) || 0);
      measured = Math.max(measured, Math.ceil(preferredNode.scrollWidth + padding + 14));
    }

    const minWidth = column.minWidthPx ?? 120;
    const maxWidth = column.maxWidthPx ?? 620;
    const nextWidth = Math.max(minWidth, Math.min(maxWidth, measured));

    this.columnWidths.update((current) => ({
      ...current,
      [column.id]: nextWidth,
    }));

    this.requestFilterPositionUpdate();
  }

  columnWidthPx(column: SharedListColumn<any>): number | null {
    const width = this.columnWidths()[column.id] ?? column.widthPx;
    return width && width > 0 ? width : null;
  }

  currentSortIcon(column: SharedListColumn<any>): string {
    const sort = this.sortState();
    if (sort.columnId !== column.id || !sort.direction) {
      return 'swap_vert';
    }
    return sort.direction === 'asc' ? 'north' : 'south';
  }

  currentSortAriaLabel(column: SharedListColumn<any>): string {
    const sort = this.sortState();
    if (sort.columnId !== column.id || !sort.direction) {
      return 'COMMON.SORT';
    }
    return sort.direction === 'asc' ? 'COMMON.SORT_ASC' : 'COMMON.SORT_DESC';
  }

  cellValue(row: any, column: SharedListColumn<any>): unknown {
    return column.valueAccessor(row);
  }

  onRowClick(row: any): void {
    this.rowClick.emit(row);
    if (this.isUncontrolledDetailMode() && this.detailRowToggleOnRowClick()) {
      this.toggleDetail(row);
    }
  }

  onRowContextMenu(event: MouseEvent, row: any): void {
    if (!this.rowContextMenuEnabled() || !this.rowContextMenuTemplate()) {
      return;
    }

    event.preventDefault();
    event.stopPropagation();
    this.contextMenuRow.set(row);
    // Small offset keeps the pointer visible and makes the menu feel anchored to the click.
    this.contextMenuPosition.set({x: event.clientX + 2, y: event.clientY + 2});
    this.rowContextMenu.emit({
      row,
      position: {x: event.clientX, y: event.clientY},
    });

    const trigger = this.contextMenuTriggerRef();
    if (!trigger) {
      return;
    }

    if (trigger.menuOpen) {
      trigger.closeMenu();
    }
    // Wait one frame so overlay origin position is fully updated before opening.
    requestAnimationFrame(() => trigger.openMenu());
  }

  onRowContextMenuClosed(): void {
    this.contextMenuRow.set(null);
  }

  /** Programmatic toggle of a row detail (uncontrolled mode only). */
  toggleDetail(row: any): void {
    if (!this.detailRowTemplate() || !this.rowCanExpand(row)) {
      return;
    }

    const key = this.rowKey(row);
    const current = new Set(this.internalExpandedKeys());
    const expanded = !current.has(key);

    if (expanded) {
      if (this.detailRowAccordion()) {
        current.clear();
      }
      current.add(key);
    } else {
      current.delete(key);
    }

    this.internalExpandedKeys.set(current);
    this.detailToggle.emit({row, expanded, expandedKeys: [...current]});
  }

  /** Collapse every expanded detail row (uncontrolled mode). */
  collapseAllDetails(): void {
    if (this.internalExpandedKeys().size === 0) {
      return;
    }
    this.internalExpandedKeys.set(new Set());
    this.detailToggle.emit({row: null, expanded: false, expandedKeys: []});
  }

  /** Number of currently expanded detail rows (uncontrolled mode). */
  expandedDetailCount(): number {
    return this.internalExpandedKeys().size;
  }

  rowCanExpand(row: any): boolean {
    const guard = this.detailRowCanExpand();
    return guard ? guard(row) : true;
  }

  isRowExpanded(row: any): boolean {
    return this.isDetailExpanded(0, row);
  }

  isRowSelected(row: any): boolean {
    if (!this.rowSelectionEnabled()) {
      return false;
    }
    const key = this.rowKey(row);
    const external = this.selectedRowKeys();
    if (external) {
      return external.includes(key);
    }
    return this.internalSelectedKeys().has(key);
  }

  selectedRowsCount(): number {
    return this.resolveSelectedKeysSet().size;
  }

  areAllDisplayedRowsSelected(): boolean {
    if (!this.rowSelectionEnabled()) {
      return false;
    }
    const rows = this.displayedRows();
    if (rows.length === 0) {
      return false;
    }
    const selected = this.resolveSelectedKeysSet();
    return rows.every((row) => selected.has(this.rowKey(row)));
  }

  hasPartiallySelectedDisplayedRows(): boolean {
    if (!this.rowSelectionEnabled()) {
      return false;
    }
    const rows = this.displayedRows();
    if (rows.length === 0) {
      return false;
    }
    const selected = this.resolveSelectedKeysSet();
    const selectedCount = rows.reduce((count, row) => count + (selected.has(this.rowKey(row)) ? 1 : 0), 0);
    return selectedCount > 0 && selectedCount < rows.length;
  }

  onToggleRowSelection(event: MatCheckboxChange, row: any): void {
    const checked = !!event.checked;
    const key = this.rowKey(row);
    const selected = new Set(this.resolveSelectedKeysSet());
    if (checked) {
      selected.add(key);
    } else {
      selected.delete(key);
    }
    this.commitSelection(selected, row, checked);
  }

  onToggleAllDisplayedRows(event: MatCheckboxChange): void {
    const checked = !!event.checked;
    const selected = new Set(this.resolveSelectedKeysSet());
    const rows = this.displayedRows();
    for (const row of rows) {
      const key = this.rowKey(row);
      if (checked) {
        selected.add(key);
      } else {
        selected.delete(key);
      }
    }
    this.commitSelection(selected, null, checked);
  }

  isColumnVisible(columnId: string): boolean {
    return this.effectiveColumnVisibility()[columnId] ?? true;
  }

  onToggleColumnVisibility(columnId: string, checked: boolean): void {
    const next = {
      ...this.effectiveColumnVisibility(),
      [columnId]: checked,
    };
    this.internalColumnVisibility.set(next);
    this.columnVisibilityChange.emit(next);
  }

  /**
   * Reports a sort/filter change: emits the full combined query in `remote` mode
   * (page reset to `0`), or requests a page reset in paginated `local` mode.
   */
  private onQueryStateChanged(): void {
    if (this.dataMode() === 'remote') {
      this.remoteQueryChange.emit({
        sort: this.sortState(),
        filters: this.columnFilters(),
        page: {index: 0, size: this.paginationEnabled() ? this.pageSize() : 0},
      });
      return;
    }
    if (this.paginationEnabled() && this.pageIndex() !== 0) {
      this.pageIndexChange.emit(0);
    }
  }

  /** Switches to a saved view, applying its presentation immediately. */
  activateView(view: SharedListView): void {
    const store = this.effectiveViewsStore();
    this.commitViewsStore({...store, activeViewId: view.id});
    this.applyViewState(view);
  }

  /** Deletes a saved view. If it was the active one, the first remaining view (if any) becomes active. */
  deleteView(view: SharedListView): void {
    const store = this.effectiveViewsStore();
    const nextViews = store.views.filter((v) => v.id !== view.id);
    const nextActiveId = store.activeViewId === view.id ? (nextViews[0]?.id ?? null) : store.activeViewId;
    this.commitViewsStore({views: nextViews, activeViewId: nextActiveId});
  }

  /** Reorders columns after a header drag-and-drop. Disabled on mobile (columns are already collapsed there). */
  onColumnDragStart(event: DragEvent, column: SharedListColumn<any>): void {
    if (this.isMobileView()) {
      return;
    }
    this.draggingColumnId.set(column.id);
    event.dataTransfer?.setData('text/plain', column.id);
    if (event.dataTransfer) {
      event.dataTransfer.effectAllowed = 'move';
    }
  }

  onColumnDragOver(event: DragEvent, column: SharedListColumn<any>): void {
    const draggingId = this.draggingColumnId();
    if (!draggingId || draggingId === column.id) {
      return;
    }
    // Must call preventDefault() for the browser to allow a drop on this element.
    event.preventDefault();
    if (event.dataTransfer) {
      event.dataTransfer.dropEffect = 'move';
    }
    this.dragOverColumnId.set(column.id);
  }

  onColumnDragLeave(column: SharedListColumn<any>): void {
    if (this.dragOverColumnId() === column.id) {
      this.dragOverColumnId.set(null);
    }
  }

  onColumnDrop(event: DragEvent, column: SharedListColumn<any>): void {
    event.preventDefault();
    const sourceId = this.draggingColumnId();
    this.draggingColumnId.set(null);
    this.dragOverColumnId.set(null);
    if (!sourceId || sourceId === column.id) {
      return;
    }

    const reorderable = this.visibleColumns().map((c) => c.id);
    const fromIndex = reorderable.indexOf(sourceId);
    const toIndex = reorderable.indexOf(column.id);
    if (fromIndex === -1 || toIndex === -1) {
      return;
    }
    reorderable.splice(fromIndex, 1);
    reorderable.splice(toIndex, 0, sourceId);

    // Columns currently hidden by the picker keep their place at the end so
    // toggling them visible later doesn't jump them to an unexpected spot.
    const hiddenIds = this.columns()
      .map((c) => c.id)
      .filter((id) => !reorderable.includes(id));

    const next = [...reorderable, ...hiddenIds];
    this.internalColumnOrder.set(next);
    this.columnOrderChange.emit(next);
  }

  onColumnDragEnd(): void {
    this.draggingColumnId.set(null);
    this.dragOverColumnId.set(null);
  }

  shouldRenderInlineFilter(column: SharedListColumn<any>): boolean {
    if (!this.inlineFilters() || !column.filter) {
      return false;
    }
    const allowedColumns = this.inlineFilterColumnIds();
    if (!allowedColumns || allowedColumns.length === 0) {
      return true;
    }
    return allowedColumns.includes(column.id);
  }

  rowClasses(row: any): string | string[] | Record<string, boolean> {
    return this.rowClassFn()?.(row) ?? '';
  }

  hasCopyAction(column: SharedListColumn<any>, row: any): boolean {
    return !!this.resolveCopyValue(column, row);
  }

  copyTooltipKey(column: SharedListColumn<any>): string {
    if (typeof column.copy === 'object' && column.copy.tooltipKey) {
      return column.copy.tooltipKey;
    }
    return 'COMMON.COPY';
  }

  copyIconName(column: SharedListColumn<any>, row: any, rowIndex: number): string {
    const key = this.copyCellKey(column, row, rowIndex);
    return this.copiedCellKey() === key ? 'check' : 'content_copy';
  }

  onCopyCellValue(event: MouseEvent, column: SharedListColumn<any>, row: any, rowIndex: number): void {
    event.stopPropagation();
    const value = this.resolveCopyValue(column, row);
    if (!value) {
      return;
    }

    const key = this.copyCellKey(column, row, rowIndex);
    navigator.clipboard.writeText(value)
      .catch(() => undefined)
      .finally(() => {
        this.copiedCellKey.set(key);
        setTimeout(() => {
          if (this.copiedCellKey() === key) {
            this.copiedCellKey.set(null);
          }
        }, 1400);
      });

    this.cellCopied.emit({columnId: column.id, value, row});
  }

  closeFilterMenuOnEnter(event: Event, trigger: MatMenuTrigger): void {
    event.stopPropagation();
    queueMicrotask(() => trigger.closeMenu());
  }

  onFilterMenuOpened(columnId: string, trigger: MatMenuTrigger, filter: SharedListFilterConfig): void {
    this.activeFilterColumnId.set(columnId);
    this.activeFilterTrigger.set(trigger);
    void this.ensureLazyFilterOptions(columnId, filter);
    this.requestFilterPositionUpdate();
  }

  onFilterMenuClosed(columnId: string): void {
    if (this.activeFilterColumnId() === columnId) {
      this.activeFilterColumnId.set(null);
      this.activeFilterTrigger.set(null);
    }
  }

  onTableWrapScroll(): void {
    this.requestFilterPositionUpdate();
  }

  isFilterOptionsLoading(columnId: string): boolean {
    return this.lazyFilterLoading()[columnId];
  }

  filterOptionsError(columnId: string): string {
    return this.lazyFilterErrors()[columnId] ?? '';
  }

  resolvedFilterOptions(columnId: string, filter: SharedListFilterConfig): SharedListFilterOption[] {
    const lazyOptions = this.lazyFilterOptions()[columnId];
    if (lazyOptions) {
      return lazyOptions;
    }
    return filter.options ?? [];
  }

  @HostListener('window:resize')
  onWindowResize(): void {
    this.isMobileView.set(window.innerWidth <= 760);
    this.requestFilterPositionUpdate();
  }

  @HostListener('window:scroll')
  onWindowScroll(): void {
    this.requestFilterPositionUpdate();
  }

  trackByColumn = (_: number, column: SharedListColumn<any>): string => column.id;

  trackByRow = (index: number, row: any): any => {
    const keyAccessor = this.rowKeyAccessor();
    if (keyAccessor) {
      return keyAccessor(row);
    }
    const externalTrackBy = this.rowTrackBy();
    if (externalTrackBy) {
      return externalTrackBy(index, row);
    }
    return row?.id ?? row?.ID ?? index;
  };

  resolvedTrackBy: TrackByFunction<any> = (index: number, row: any): any =>
    this.trackByRow(index, row);

  mobileActionsRowWhen = (_: number, _row: any): boolean => this.mobileActionRowColumns().length > 0;

  dataRowWhen = (_: number, _row: any): boolean => true;

  /**
   * The detail row is ALWAYS rendered when a template is provided.
   * Material only re-evaluates `when:` predicates on data re-render, so
   * expansion state must NOT be part of the predicate. Visibility is
   * driven by `isDetailExpanded()` bindings instead, which are re-evaluated
   * on every change detection cycle.
   */
  detailRowRenderWhen = (_index: number, _row: any): boolean => !!this.detailRowTemplate();

  isDetailExpanded(index: number, row: any): boolean {
    if (!this.detailRowTemplate()) {
      return false;
    }

    const when = this.detailRowWhen();
    if (when) {
      return !!when(index, row);
    }

    if (!this.rowCanExpand(row)) {
      return false;
    }

    const key = this.rowKey(row);
    const externalKeys = this.expandedRowKeys();
    if (externalKeys) {
      return externalKeys.includes(key);
    }

    return this.internalExpandedKeys().has(key);
  }

  /** Uncontrolled mode = no external predicate nor external keys provided. */
  private isUncontrolledDetailMode(): boolean {
    return !!this.detailRowTemplate() && !this.detailRowWhen() && !this.expandedRowKeys();
  }

  /**
   * Completes a filters map with a `''` entry for every column of `columns()` that
   * has a `filter`/`filterPredicate`, and drops entries for columns no longer present.
   * Used both when mirroring the controlled `[filters]` input and when `columns()`
   * itself changes, so the component's filters set is always self-built from its own
   * column definitions — no consumer needs to know/repeat the filterable column list.
   */
  private withDefaultFilterKeys(source: Record<string, string>): Record<string, string> {
    const filterableIds = new Set(
      this.columns().filter((column) => !!column.filter || !!column.filterPredicate).map((column) => column.id),
    );
    let changed = false;
    const next: Record<string, string> = {};
    for (const id of filterableIds) {
      next[id] = source[id] ?? '';
      if (source[id] === undefined) {
        changed = true;
      }
    }
    for (const key of Object.keys(source)) {
      if (!filterableIds.has(key)) {
        changed = true;
      }
    }
    return changed ? next : source;
  }

  private effectiveColumnVisibility(): Record<string, boolean> {
    // Priorite au mode controle, fallback sur l'etat interne.
    return this.columnVisibility() ?? this.internalColumnVisibility();
  }

  private effectiveColumnOrder(): string[] {
    const external = this.columnOrder();
    return external ? [...external] : this.internalColumnOrder();
  }

  private effectiveViewsStore(): SharedListViewsStore {
    return this.viewsStore() ?? this.internalViewsStore();
  }

  private applyActiveView(store: SharedListViewsStore): void {
    const active = store.views.find((v) => v.id === store.activeViewId);
    if (active) {
      this.applyViewState(active);
    }
  }

  private applyViewState(view: SharedListView): void {
    const state = view.state;
    this.internalColumnVisibility.set({...state.columnVisibility});
    this.internalColumnOrder.set([...state.columnOrder]);
    this.sortState.set({...state.sort});
    this.columnFilters.set({...state.filters});
    this.filtersChange.emit(this.columnFilters());
    this.onQueryStateChanged();
    if (state.pageIndex !== undefined && state.pageSize !== undefined) {
      this.viewPaginationRestore.emit({pageIndex: state.pageIndex, pageSize: state.pageSize});
    }
    this.viewActivated.emit(view);
  }

  /** Central write path: updates in-memory state, persists to localStorage in uncontrolled mode, and always reports out. */
  private commitViewsStore(next: SharedListViewsStore): void {
    this.internalViewsStore.set(next);
    const key = this.viewsStorageKey();
    if (key && !this.viewsStore()) {
      this.saveViewsStoreToLocalStorage(key, next);
    }
    this.viewsStoreChange.emit(next);
  }

  private loadViewsStoreFromLocalStorage(key: string): SharedListViewsStore {
    try {
      const raw = localStorage.getItem(this.viewsStorageNamespacedKey(key));
      if (!raw) {
        return {views: [], activeViewId: null};
      }
      const parsed = JSON.parse(raw);
      if (parsed && Array.isArray(parsed.views)) {
        return {views: parsed.views, activeViewId: parsed.activeViewId ?? null};
      }
    } catch {
      // Corrupt/unavailable storage — fall through to an empty store.
    }
    return {views: [], activeViewId: null};
  }

  private saveViewsStoreToLocalStorage(key: string, store: SharedListViewsStore): void {
    try {
      localStorage.setItem(this.viewsStorageNamespacedKey(key), JSON.stringify(store));
    } catch {
      // Storage full/unavailable (e.g. private browsing) — the view still works for this session.
    }
  }

  private viewsStorageNamespacedKey(key: string): string {
    return `configurable-list.views.${key}`;
  }

  private generateViewId(): string {
    if (typeof crypto !== 'undefined' && crypto.randomUUID) {
      return crypto.randomUUID();
    }
    return `view-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
  }

  private resolveSelectedKeysSet(): Set<unknown> {
    // Priorite au mode controle, fallback sur l'etat interne.
    const external = this.selectedRowKeys();
    if (external) {
      return new Set(external);
    }
    return new Set(this.internalSelectedKeys());
  }

  private commitSelection(next: Set<unknown>, row: any | null, selected: boolean): void {
    const external = this.selectedRowKeys();
    if (!external) {
      this.internalSelectedKeys.set(next);
    }

    const selectedRows = this.rows().filter((item) => next.has(this.rowKey(item)));
    this.selectionChange.emit({
      row,
      selected,
      selectedKeys: [...next],
      selectedRows,
    });
  }

  private rowKey(row: any): unknown {
    // Cle metier stable: rowKeyAccessor > rowTrackBy > heuristique id.
    const accessor = this.rowKeyAccessor();
    if (accessor) {
      return accessor(row);
    }
    const externalTrackBy = this.rowTrackBy();
    if (externalTrackBy) {
      return externalTrackBy(0, row);
    }
    return row?.id ?? row?.ID ?? row;
  }

  mobileActionsCellContext(row: any): {
    $implicit: any;
    row: any;
    value: unknown;
    column: SharedListColumn<any>
  } | null {
    const actionColumn = this.actionColumn();
    if (!actionColumn) {
      return null;
    }

    return {
      $implicit: row,
      row,
      value: this.cellValue(row, actionColumn),
      column: actionColumn,
    };
  }

  mobileActionsColspan(): number {
    return Math.max(1, this.displayedColumnIds().length || 1);
  }

  detailRowColspan(): number {
    return Math.max(1, this.displayedColumnIds().length || 1);
  }

  private readonly onMouseMoveBound = (event: MouseEvent) => this.onResizeMove(event);

  private readonly onMouseUpBound = () => this.stopResize();

  private onResizeMove(event: MouseEvent): void {
    if (!this.resizingState) {
      return;
    }

    const column = this.columns().find((item) => item.id === this.resizingState?.columnId);
    if (!column) {
      this.stopResize();
      return;
    }

    const delta = event.clientX - this.resizingState.startX;
    const minWidth = column.minWidthPx ?? 120;
    const maxWidth = column.maxWidthPx ?? 620;
    const nextWidth = Math.max(minWidth, Math.min(maxWidth, this.resizingState.startWidth + delta));

    this.columnWidths.update((current) => ({
      ...current,
      [column.id]: nextWidth,
    }));
  }

  private stopResize(): void {
    if (!this.resizingState) {
      return;
    }
    this.resizingState = null;
    document.removeEventListener('mousemove', this.onMouseMoveBound);
    document.removeEventListener('mouseup', this.onMouseUpBound);
  }

  private requestFilterPositionUpdate(): void {
    const trigger = this.activeFilterTrigger();
    if (!trigger?.menuOpen) {
      return;
    }
    requestAnimationFrame(() => {
      if (trigger.menuOpen) {
        trigger.updatePosition();
      }
    });
  }

  /** Human readable value for the active filters bar (resolves enum option labels). */
  private formatFilterValueForDisplay(column: SharedListColumn<any>, rawValue: string): string {
    const filter = column.filter;
    if (!filter) {
      return rawValue;
    }

    if (filter.type === 'date' && rawValue.includes('..')) {
      const [from = '', to = ''] = rawValue.split('..', 2);
      if (from && to) {
        return `${from} → ${to}`;
      }
      return from || to;
    }

    const options = this.resolvedFilterOptions(column.id, filter);
    if (options.length === 0) {
      return rawValue;
    }

    return rawValue
      .split(',')
      .map((part) => part.trim())
      .filter((part) => !!part)
      .map((part) => options.find((option) => option.value === part)?.label ?? part)
      .join(', ');
  }

  private resolveCopyValue(column: SharedListColumn<any>, row: any): string {
    if (!column.copy) {
      return '';
    }

    if (typeof column.copy === 'object' && column.copy.valueAccessor) {
      return `${column.copy.valueAccessor(row) ?? ''}`.trim();
    }

    return `${column.valueAccessor(row) ?? ''}`.trim();
  }

  private copyCellKey(column: SharedListColumn<any>, row: any, rowIndex: number): string {
    const rowId = row?.id ?? row?.ID ?? rowIndex;
    return `${column.id}:${rowId}`;
  }

  private async ensureLazyFilterOptions(columnId: string, filter: SharedListFilterConfig): Promise<void> {
    // Evite les doubles chargements; memoization par colonne.
    if (!filter.optionsLoader) {
      return;
    }

    const loaded = this.lazyFilterOptions()[columnId];
    const loading = this.lazyFilterLoading()[columnId];
    if (loaded || loading) {
      return;
    }

    this.lazyFilterLoading.update((current) => ({
      ...current,
      [columnId]: true,
    }));
    this.lazyFilterErrors.update((current) => ({
      ...current,
      [columnId]: '',
    }));

    try {
      const source = filter.optionsLoader();
      const options = this.isObservableSource(source)
        ? await firstValueFrom(source)
        : await source;

      if (!options || options.length === 0) {
        this.lazyFilterOptions.update((current) => ({
          ...current,
          [columnId]: [],
        }));
        this.lazyFilterErrors.update((current) => ({
          ...current,
          [columnId]: ConfigurableListComponent.FILTER_OPTIONS_EMPTY_ERROR_KEY,
        }));
        return;
      }

      this.lazyFilterOptions.update((current) => ({
        ...current,
        [columnId]: options,
      }));
    } catch {
      this.lazyFilterOptions.update((current) => ({
        ...current,
        [columnId]: [],
      }));
      this.lazyFilterErrors.update((current) => ({
        ...current,
        [columnId]: ConfigurableListComponent.FILTER_OPTIONS_LOAD_ERROR_KEY,
      }));
    } finally {
      this.lazyFilterLoading.update((current) => ({
        ...current,
        [columnId]: false,
      }));
      this.requestFilterPositionUpdate();
    }
  }

  private isObservableSource(
    value: Observable<SharedListFilterOption[]> | Promise<SharedListFilterOption[]>,
  ): value is Observable<SharedListFilterOption[]> {
    return typeof (value as Observable<SharedListFilterOption[]>)?.subscribe === 'function';
  }

  private escapeCssToken(value: string): string {
    return (value ?? '').replace(/[^a-zA-Z0-9_-]/g, (match) => `\\${match}`);
  }

  private matchesAllFilters(
    row: any,
    columns: SharedListColumn<any>[],
    activeFilters: Record<string, string>,
  ): boolean {
    for (const column of columns) {
      const filterValue = (activeFilters[column.id] ?? '').trim();
      if (!filterValue) {
        continue;
      }

      if (!this.matchesColumnFilter(row, column, filterValue)) {
        return false;
      }
    }
    return true;
  }

  private matchesColumnFilter(row: any, column: SharedListColumn<any>, filterValue: string): boolean {
    if (column.filterPredicate) {
      return column.filterPredicate(row, filterValue);
    }

    const lowerFilter = filterValue.toLowerCase();
    const raw = column.valueAccessor(row);

    if (raw === null || raw === undefined) {
      return false;
    }

    if (typeof raw === 'boolean') {
      const expected = lowerFilter === 'true' || lowerFilter === '1';
      return raw === expected;
    }

    if (typeof raw === 'number') {
      const parsed = Number(lowerFilter);
      if (!Number.isNaN(parsed)) {
        return raw === parsed;
      }
      return `${raw}`.toLowerCase().includes(lowerFilter);
    }

    return `${raw}`.toLowerCase().includes(lowerFilter);
  }

  private getSortValue(row: any, column: SharedListColumn<any>): string | number | Date | boolean | null {
    if (column.sortValueAccessor) {
      return column.sortValueAccessor(row) ?? null;
    }

    const raw = column.valueAccessor(row);
    if (raw instanceof Date) {
      return raw;
    }
    if (typeof raw === 'boolean' || typeof raw === 'number' || typeof raw === 'string') {
      return raw;
    }
    return raw === null || raw === undefined ? null : `${raw}`;
  }

  private compareSortValues(
    left: string | number | Date | boolean | null,
    right: string | number | Date | boolean | null,
  ): number {
    if (left === right) {
      return 0;
    }

    if (left === null || left === undefined) {
      return 1;
    }

    if (right === null || right === undefined) {
      return -1;
    }

    if (left instanceof Date || right instanceof Date) {
      const leftTime = this.toComparableDateValue(left);
      const rightTime = this.toComparableDateValue(right);

      if (Number.isNaN(leftTime) && Number.isNaN(rightTime)) {
        return 0;
      }
      if (Number.isNaN(leftTime)) {
        return 1;
      }
      if (Number.isNaN(rightTime)) {
        return -1;
      }

      return leftTime - rightTime;
    }

    if (typeof left === 'boolean' || typeof right === 'boolean') {
      return Number(left) - Number(right);
    }

    if (typeof left === 'number' && typeof right === 'number') {
      return left - right;
    }

    return this.collator.compare(`${left}`, `${right}`);
  }

  private toComparableDateValue(value: string | number | Date | boolean): number {
    if (value instanceof Date) {
      return value.getTime();
    }
    if (typeof value === 'string' || typeof value === 'number') {
      return new Date(value).getTime();
    }
    return Number.NaN;
  }
}










