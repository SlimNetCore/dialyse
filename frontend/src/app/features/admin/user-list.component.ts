import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  OnInit,
  signal,
  TemplateRef,
  viewChild,
} from '@angular/core';
import {CommonModule} from '@angular/common';
import {RouterLink} from '@angular/router';
import {MatTableModule} from '@angular/material/table';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatChipsModule} from '@angular/material/chips';
import {MatCardModule} from '@angular/material/card';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatSnackBar, MatSnackBarModule} from '@angular/material/snack-bar';
import {MatDialog} from '@angular/material/dialog';
import {MatMenuModule} from '@angular/material/menu';
import {MatCheckboxModule} from '@angular/material/checkbox';
import {MatPaginatorModule, PageEvent} from '@angular/material/paginator';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {compatForm} from '@angular/forms/signals/compat';
import {FormField} from '@angular/forms/signals';
import {AppUser} from '../../core/api/admin-api.service';
import {ConfirmDialogComponent} from '../../shared/confirm-dialog.component';
import {ColumnFilterRendererComponent} from '../../shared/column-filter-renderer.component';
import {
  ConfigurableListComponent,
  SharedListColumn,
} from '../../shared/configurable-list.component';
import {UserListStore} from './state/user-list.store';

type FilterType = 'text' | 'boolean';

@Component({
  selector: 'app-user-list',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSnackBarModule,
    MatMenuModule,
    MatCheckboxModule,
    MatPaginatorModule,
    TranslateModule,
    ConfigurableListComponent,
    FormField,
  ],
  templateUrl: './user-list.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './user-list.component.css',
})
export class UserListComponent implements OnInit {
  private readonly userListStore = inject(UserListStore);
  private readonly snackbar = inject(MatSnackBar);
  private readonly dialog = inject(MatDialog);
  readonly allColumnsConfig = [
    {key: 'username', label: 'ADMIN.USERS.COL_USERNAME', type: 'text' as FilterType},
    {key: 'fullName', label: 'ADMIN.USERS.COL_FULLNAME', type: 'text' as FilterType},
    {key: 'email', label: 'PATIENT_FORM.EMAIL', type: 'text' as FilterType},
    {key: 'roles', label: 'ADMIN.USERS.COL_ROLES', type: 'text' as FilterType},
    {key: 'centers', label: 'ADMIN.USERS.COL_CENTERS', type: 'text' as FilterType},
    {key: 'active', label: 'ADMIN.USERS.COL_ACTIVE', type: 'boolean' as FilterType},
    {key: 'actions', label: 'COMMON.COL_ACTIONS', type: 'text' as FilterType},
  ] as const;
  readonly hasActiveFilters = this.userListStore.hasActiveFilters;
  private readonly translate = inject(TranslateService);
  readonly visibleColumns = this.userListStore.visibleColumns;
  readonly displayedColumns = computed(() =>
    this.allColumnsConfig.filter((c) => this.visibleColumns()[c.key]).map((c) => c.key),
  );
  readonly roleOptions = computed(() => {
    const map = new Map<string, { value: string; label: string }>();
    for (const user of this.rows()) {
      for (const role of user.roles ?? []) {
        if (!map.has(role.name)) {
          map.set(role.name, {value: role.name, label: role.name});
        }
      }
    }
    return Array.from(map.values()).sort((a, b) => a.label.localeCompare(b.label, 'fr'));
  });

  readonly rows = this.userListStore.rows;
  readonly total = this.userListStore.total;
  readonly pageIndex = this.userListStore.pageIndex;
  readonly pageSize = this.userListStore.pageSize;
  readonly columnFilters = this.userListStore.columnFilters;
  readonly searchTerm = this.userListStore.searchTerm;
  readonly searchModel = signal({term: this.searchTerm()});
  readonly searchForm = compatForm(this.searchModel);
  protected readonly rolesCellTemplate = viewChild<TemplateRef<any>>('rolesCell');
  protected readonly centersCellTemplate = viewChild<TemplateRef<any>>('centersCell');
  protected readonly activeCellTemplate = viewChild<TemplateRef<any>>('activeCell');
  protected readonly actionsCellTemplate = viewChild<TemplateRef<any>>('actionsCell');
  readonly displayedColumnDefs = computed<SharedListColumn<AppUser>[]>(() => {
    const columns = this.allColumnsConfig.filter((c) => this.visibleColumns()[c.key]);
    const roleOptions = this.roleOptions();

    return columns.map((column) => {
      if (column.key === 'actions') {
        return {
          id: column.key,
          headerKey: column.label,
          valueAccessor: () => '',
          sortable: false,
          resizable: false,
          widthPx: 112,
          minWidthPx: 96,
          maxWidthPx: 160,
          cellTemplate: this.actionsCellTemplate() ?? undefined,
        } satisfies SharedListColumn<AppUser>;
      }

      if (column.key === 'roles') {
        return {
          id: column.key,
          headerKey: column.label,
          valueAccessor: (row) => (row.roles ?? []).map((r) => r.name).join(', '),
          sortable: true,
          resizable: true,
          minWidthPx: 160,
          filter: {
            component: ColumnFilterRendererComponent,
            componentInputs: {
              type: 'enum',
              options: roleOptions,
              labelKey: column.label,
            },
          },
          cellTemplate: this.rolesCellTemplate() ?? undefined,
        } satisfies SharedListColumn<AppUser>;
      }

      if (column.key === 'centers') {
        return {
          id: column.key,
          headerKey: column.label,
          valueAccessor: (row) => (row.centers ?? []).map((c) => c.name).join(', '),
          sortable: true,
          resizable: true,
          minWidthPx: 180,
          filter: {type: 'text', labelKey: column.label},
          cellTemplate: this.centersCellTemplate() ?? undefined,
        } satisfies SharedListColumn<AppUser>;
      }

      if (column.key === 'active') {
        return {
          id: column.key,
          headerKey: column.label,
          valueAccessor: (row) => !!row.active,
          sortable: true,
          resizable: true,
          minWidthPx: 120,
          maxWidthPx: 150,
          filter: {type: 'boolean', labelKey: column.label},
          cellTemplate: this.activeCellTemplate() ?? undefined,
        } satisfies SharedListColumn<AppUser>;
      }

      return {
        id: column.key,
        headerKey: column.label,
        valueAccessor: (row) => this.defaultColumnValue(row, column.key),
        sortable: true,
        resizable: true,
        minWidthPx: 160,
        filter: {type: 'text', labelKey: column.label},
      } satisfies SharedListColumn<AppUser>;
    });
  });

  constructor() {
    effect(() => {
      const term = this.searchModel().term;
      if (term !== this.searchTerm()) {
        this.onSearch(term);
      }
    });
  }

  ngOnInit(): void {
    this.userListStore.loadPage({page: 0, size: this.pageSize()});
  }

  onSearch(value: string): void {
    this.userListStore.setSearchTerm(value);
    this.userListStore.loadPage({page: 0, size: this.pageSize()});
  }

  toggleColumn(column: string, checked: boolean): void {
    this.userListStore.setVisibleColumn(column, checked);
  }

  isColumnVisible(column: string): boolean {
    return this.visibleColumns()[column] ?? false;
  }

  onListFiltersChange(filters: Record<string, string>): void {
    for (const column of this.allColumnsConfig) {
      if (column.key === 'actions') {
        continue;
      }
      const nextValue = filters[column.key] ?? '';
      this.userListStore.setFilter(column.key, nextValue);
    }
    this.userListStore.loadPage({page: 0, size: this.pageSize()});
  }

  clearAllColumnFilters(): void {
    this.userListStore.clearAllFilters();
    this.userListStore.loadPage({page: 0, size: this.pageSize()});
  }

  deleteUser(u: AppUser): void {
    const ref = this.dialog.open(ConfirmDialogComponent, {
      width: 'min(96vw, 440px)',
      data: {
        title: this.translate.instant('ADMIN.USERS.DELETE_TITLE'),
        message: this.translate.instant('ADMIN.USERS.DELETE_CONFIRM', {username: u.username}),
        confirmLabel: this.translate.instant('COMMON.DELETE'),
        cancelLabel: this.translate.instant('PATIENT_FORM.BTN_CANCEL'),
        color: 'warn',
        icon: 'delete',
      },
    });
    ref.afterClosed().subscribe((confirmed) => {
      if (!confirmed) return;
      // ✅ Appel API via le store maintenant
      this.userListStore.deleteUser(u.id);
      this.snackbar.open(this.translate.instant('ADMIN.USERS.DELETED_OK'), this.translate.instant('COMMON.OK'), {duration: 2000});
    });
  }

  onPageChange(event: PageEvent): void {
    this.userListStore.setPagination(event.pageIndex, event.pageSize);
    this.userListStore.loadPage({page: event.pageIndex, size: event.pageSize});
  }

  private defaultColumnValue(row: AppUser, key: string): string {
    const fieldKey = (key === 'fullName' ? 'full_name' : key) as keyof AppUser;
    const value = row[fieldKey];
    if (value === null || value === undefined) return '';
    return `${value}`;
  }
}
