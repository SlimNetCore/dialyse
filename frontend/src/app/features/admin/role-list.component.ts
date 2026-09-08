import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  HostListener,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import {CommonModule} from '@angular/common';
import {RouterLink} from '@angular/router';
import {MatTableModule} from '@angular/material/table';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatCardModule} from '@angular/material/card';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatMenuModule} from '@angular/material/menu';
import {MatCheckboxModule} from '@angular/material/checkbox';
import {MatPaginatorModule, PageEvent} from '@angular/material/paginator';
import {MatSnackBar, MatSnackBarModule} from '@angular/material/snack-bar';
import {MatDialog} from '@angular/material/dialog';
import {compatForm} from '@angular/forms/signals/compat';
import {FormField} from '@angular/forms/signals';
import {AdminApiService, AppRole} from '../../core/api/admin-api.service';
import {ConfirmDialogComponent} from '../../shared/confirm-dialog.component';
import {ColumnFilterRendererComponent} from '../../shared/column-filter-renderer.component';
import {RoleListStore} from './state/role-list.store';

@Component({
  selector: 'app-role-list',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatMenuModule,
    MatCheckboxModule,
    MatPaginatorModule,
    MatSnackBarModule,
    ColumnFilterRendererComponent,
    FormField,
  ],
  templateUrl: './role-list.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './role-list.component.css',
})
export class RoleListComponent implements OnInit {
  private readonly api = inject(AdminApiService);
  private readonly roleListStore = inject(RoleListStore);
  private readonly snackbar = inject(MatSnackBar);
  private readonly dialog = inject(MatDialog);
  readonly hasActiveFilters = this.roleListStore.hasActiveFilters;

  readonly allColumnsConfig = [
    {key: 'code', label: 'Code'},
    {key: 'name', label: 'Nom'},
    {key: 'description', label: 'Description'},
    {key: 'actions', label: 'Actions'},
  ] as const;
  readonly visibleColumns = this.roleListStore.visibleColumns;
  readonly displayedColumns = computed(() =>
    this.allColumnsConfig.filter((c) => this.visibleColumns()[c.key]).map((c) => c.key),
  );

  readonly rows = this.roleListStore.rows;
  readonly total = this.roleListStore.total;
  readonly pageIndex = this.roleListStore.pageIndex;
  readonly pageSize = this.roleListStore.pageSize;
  readonly columnFilters = this.roleListStore.columnFilters;
  readonly searchTerm = this.roleListStore.searchTerm;
  readonly searchModel = signal({term: this.searchTerm()});
  readonly searchForm = compatForm(this.searchModel);

  constructor() {
    effect(() => {
      const term = this.searchModel().term;
      if (term !== this.searchTerm()) {
        this.onSearch(term);
      }
    });
  }

  ngOnInit(): void {
    this.fetchPage(0, this.pageSize());
  }

  onSearch(value: string): void {
    this.roleListStore.setSearchTerm(value);
    this.fetchPage(0, this.pageSize());
  }

  toggleColumn(column: string, checked: boolean): void {
    this.roleListStore.setVisibleColumn(column, checked);
  }

  isColumnVisible(column: string): boolean {
    return this.visibleColumns()[column] ?? false;
  }

  onColumnFilterValue(column: string, value: string): void {
    this.roleListStore.setFilter(column, value);
    this.fetchPage(0, this.pageSize());
  }

  columnFilterValue(column: string): string {
    return this.columnFilters()[column] ?? '';
  }

  isColumnFiltered(column: string): boolean {
    return !!(this.columnFilters()[column] ?? '').trim();
  }

  clearColumnFilter(column: string): void {
    this.roleListStore.clearFilter(column);
    this.fetchPage(0, this.pageSize());
  }

  toggleFilterPanel(column: string, event: MouseEvent): void {
    event.stopPropagation();
    if (typeof window !== 'undefined' && window.innerWidth <= 760) {
      const wrap = (event.target as HTMLElement).closest('.th-wrap');
      if (wrap) {
        const rect = wrap.getBoundingClientRect();
        document.documentElement.style.setProperty(
          '--filter-row-bottom',
          `${Math.round(rect.bottom + 6)}px`,
        );
      }
    }
    this.roleListStore.toggleFilterPanel(column);
  }

  isFilterOpen(column: string): boolean {
    return this.roleListStore.openFilterColumn() === column;
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    const target = event.target as HTMLElement | null;
    if (!target) {
      this.roleListStore.closeFilterPanel();
      return;
    }
    if (target.closest('.th-wrap')) {
      return;
    }
    this.roleListStore.closeFilterPanel();
  }

  clearAllColumnFilters(): void {
    this.roleListStore.closeFilterPanel();
    this.roleListStore.clearAllFilters();
    this.fetchPage(0, this.pageSize());
  }

  onPageChange(event: PageEvent): void {
    this.roleListStore.setPagination(event.pageIndex, event.pageSize);
    this.fetchPage(event.pageIndex, event.pageSize);
  }

  deleteRole(r: AppRole): void {
    const ref = this.dialog.open(ConfirmDialogComponent, {
      width: 'min(96vw, 440px)',
      data: {
        title: 'Supprimer le rôle',
        message: `Êtes-vous sûr de vouloir supprimer le rôle ${r.code} ? Cette action est irréversible.`,
        confirmLabel: 'Supprimer',
        cancelLabel: 'Annuler',
        color: 'warn',
        icon: 'delete',
      },
    });
    ref.afterClosed().subscribe((confirmed) => {
      if (!confirmed) return;
      this.api.deleteRole(r.id).subscribe(() => {
        this.snackbar.open('Rôle supprimé', 'OK', {duration: 2000});
        this.fetchPage(this.pageIndex(), this.pageSize());
      });
    });
  }

  private fetchPage(page: number, size: number): void {
    this.roleListStore.setLoading(true);
    this.api
      .listRolesPaged({
        page,
        size,
        search: this.searchTerm(),
        filters: this.columnFilters(),
      })
      .subscribe({
        next: (res) => {
          this.roleListStore.setPageData(res.items ?? [], res.total ?? 0, res.page ?? page);
          this.roleListStore.setLoading(false);
        },
        error: () => {
          this.roleListStore.setPageData([], 0, page);
          this.roleListStore.setLoading(false);
        },
      });
  }
}
