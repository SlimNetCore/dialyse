import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  OnDestroy,
  TemplateRef,
  Type,
  computed,
  effect,
  input,
  output,
  signal,
} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatMenuModule} from '@angular/material/menu';
import {MatTableModule} from '@angular/material/table';
import {TranslateModule} from '@ngx-translate/core';
import {ColumnFilterRendererComponent, ColumnFilterType} from './column-filter-renderer.component';
import {DynamicFilterHostComponent} from './dynamic-filter-host.component';

export type SortDirection = 'asc' | 'desc' | '';

export type SharedListFilterOption = { value: string; label: string };

export interface SharedListFilterConfig {
  type?: ColumnFilterType;
  options?: SharedListFilterOption[];
  placeholder?: string;
  labelKey?: string;
  component?: Type<unknown>;
  componentInputs?: Record<string, unknown>;
}

export interface SharedListColumn<T> {
  id: string;
  headerKey: string;
  valueAccessor: (row: T) => unknown;
  sortable?: boolean;
  resizable?: boolean;
  widthPx?: number;
  minWidthPx?: number;
  maxWidthPx?: number;
  cellTemplate?: TemplateRef<{ $implicit: T; row: T; value: unknown; column: SharedListColumn<T> }>;
  sortValueAccessor?: (row: T) => string | number | boolean | Date | null | undefined;
  filter?: SharedListFilterConfig;
  filterPredicate?: (row: T, filterValue: string) => boolean;
}

export interface SharedListSortChange {
  columnId: string;
  direction: SortDirection;
}

@Component({
  selector: 'app-configurable-list',
  standalone: true,
  imports: [
    CommonModule,
    MatTableModule,
    MatMenuModule,
    MatButtonModule,
    MatIconModule,
    TranslateModule,
    ColumnFilterRendererComponent,
    DynamicFilterHostComponent,
  ],
  templateUrl: './configurable-list.component.html',
  styleUrl: './configurable-list.component.css',
  changeDetection: ChangeDetectionStrategy.Eager,
})
export class ConfigurableListComponent<T extends Record<string, unknown>> implements OnDestroy {
  readonly rows = input<T[]>([]);
  readonly columns = input<SharedListColumn<T>[]>([]);
  readonly emptyLabelKey = input('COMMON.NO_DATA');
  readonly minTableWidthPx = input(760);

  readonly rowClick = output<T>();
  readonly filtersChange = output<Record<string, string>>();
  readonly sortChange = output<SharedListSortChange>();

  readonly displayedColumnIds = computed(() => this.columns().map((column) => column.id));
  readonly hasColumns = computed(() => this.displayedColumnIds().length > 0);
  protected readonly columnFilters = signal<Record<string, string>>({});
  protected readonly sortState = signal<SharedListSortChange>({columnId: '', direction: ''});
  readonly displayedRows = computed(() => {
    const sourceRows = this.rows();
    const activeColumns = this.columns();
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
  protected readonly columnWidths = signal<Record<string, number>>({});

  private readonly collator = new Intl.Collator('fr', {numeric: true, sensitivity: 'base'});
  private resizingState: { columnId: string; startX: number; startWidth: number } | null = null;

  constructor() {
    effect(() => {
      const nextColumns = this.columns();
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
    });
  }

  ngOnDestroy(): void {
    this.stopResize();
  }

  onHeaderSort(column: SharedListColumn<T>): void {
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

  onResizeStart(event: MouseEvent, column: SharedListColumn<T>): void {
    if (!column.resizable) {
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

  columnWidthPx(column: SharedListColumn<T>): number | null {
    const width = this.columnWidths()[column.id] ?? column.widthPx;
    return width && width > 0 ? width : null;
  }

  currentSortIcon(column: SharedListColumn<T>): string {
    const sort = this.sortState();
    if (sort.columnId !== column.id || !sort.direction) {
      return 'swap_vert';
    }
    return sort.direction === 'asc' ? 'north' : 'south';
  }

  currentSortAriaLabel(column: SharedListColumn<T>): string {
    const sort = this.sortState();
    if (sort.columnId !== column.id || !sort.direction) {
      return 'COMMON.SORT';
    }
    return sort.direction === 'asc' ? 'COMMON.SORT_ASC' : 'COMMON.SORT_DESC';
  }

  cellValue(row: T, column: SharedListColumn<T>): unknown {
    return column.valueAccessor(row);
  }

  onRowClick(row: T): void {
    this.rowClick.emit(row);
  }

  trackByColumn = (_: number, column: SharedListColumn<T>): string => column.id;

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

  private matchesAllFilters(
    row: T,
    columns: SharedListColumn<T>[],
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

  private matchesColumnFilter(row: T, column: SharedListColumn<T>, filterValue: string): boolean {
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

  private getSortValue(row: T, column: SharedListColumn<T>): string | number | Date | boolean | null {
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


