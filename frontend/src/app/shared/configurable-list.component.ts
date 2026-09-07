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
import {firstValueFrom, Observable} from 'rxjs';
import {ColumnFilterRendererComponent, ColumnFilterType} from './column-filter-renderer.component';
import {DynamicFilterHostComponent} from './dynamic-filter-host.component';
import {HemodialysisLoaderComponent} from './hemodialysis-loader.component';

export type SortDirection = 'asc' | 'desc' | '';

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
  readonly resetFiltersLabelKey = input('PATIENT_LIST.RESET_FILTERS_BUTTON');
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

  readonly rowClick = output<any>();
  /** Emitted whenever any filter value changes. */
  readonly filtersChange = output<Record<string, string>>();
  readonly sortChange = output<SharedListSortChange>();
  readonly cellCopied = output<SharedListCopyEvent>();
  readonly detailToggle = output<SharedListDetailToggleEvent>();
  /** Emitted when the internal column picker toggles a column visibility. */
  readonly columnVisibilityChange = output<Record<string, boolean>>();
  /** Emitted on row selection/unselection and select-all operations. */
  readonly selectionChange = output<SharedListSelectionChangeEvent>();
  /** Emitted when the contextual menu is requested on a row. */
  readonly rowContextMenu = output<SharedListContextMenuEvent>();

  readonly columnsMenuItems = computed(() =>
    this.columns().filter((column) => column.id !== '__detail_row__' && column.id !== '__mobile_actions__'),
  );

  readonly visibleColumns = computed(() => {
    const columns = this.columns();
    const visibility = this.effectiveColumnVisibility();

    if (!visibility) {
      return columns.filter((column) => column.visible !== false);
    }

    return columns.filter((column) => visibility[column.id] ?? true);
  });
  readonly actionColumn = computed(() =>
    this.visibleColumns().find((column) => column.mobileRowActions) ?? null,
  );
  readonly displayedRows = computed(() => {
    // Pipeline local: rows source -> filtres -> tri.
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
  private readonly internalColumnVisibility = signal<Record<string, boolean>>({});
  private readonly activeFilterColumnId = signal<string | null>(null);
  private readonly activeFilterTrigger = signal<MatMenuTrigger | null>(null);

  private readonly collator = new Intl.Collator('fr', {numeric: true, sensitivity: 'base'});
  private resizingState: { columnId: string; startX: number; startWidth: number } | null = null;

  constructor() {
    effect(() => {
      const externalFilters = this.filters();
      if (!externalFilters) {
        return;
      }
      this.columnFilters.set({...externalFilters});
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
  }

  ngOnDestroy(): void {
    this.stopResize();
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
  }

  onFilterValue(columnId: string, value: string): void {
    this.columnFilters.update((current) => {
      const next = {...current, [columnId]: value ?? ''};
      this.filtersChange.emit(next);
      return next;
    });
  }

  clearFilter(columnId: string): void {
    this.onFilterValue(columnId, '');
  }

  clearAllFilters(): void {
    const next: Record<string, string> = {};
    this.columnFilters.set(next);
    this.filtersChange.emit(next);
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

  private effectiveColumnVisibility(): Record<string, boolean> {
    // Priorite au mode controle, fallback sur l'etat interne.
    return this.columnVisibility() ?? this.internalColumnVisibility();
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










