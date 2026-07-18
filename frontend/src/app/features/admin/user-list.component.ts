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
import {MatChipsModule} from '@angular/material/chips';
import {MatCardModule} from '@angular/material/card';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatSnackBar, MatSnackBarModule} from '@angular/material/snack-bar';
import {MatDialog} from '@angular/material/dialog';
import {MatMenuModule} from '@angular/material/menu';
import {MatCheckboxModule} from '@angular/material/checkbox';
import {MatPaginatorModule, PageEvent} from '@angular/material/paginator';
import {TranslateModule} from '@ngx-translate/core';
import {compatForm} from '@angular/forms/signals/compat';
import {FormField} from '@angular/forms/signals';
import {AppUser} from '../../core/api/admin-api.service';
import {ConfirmDialogComponent} from '../../shared/confirm-dialog.component';
import {ColumnFilterRendererComponent} from '../../shared/column-filter-renderer.component';
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
    ColumnFilterRendererComponent,
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
  readonly hasActiveFilters = this.userListStore.hasActiveFilters;

  readonly allColumnsConfig = [
    {key: 'username', label: "Nom d'utilisateur", type: 'text' as FilterType},
    {key: 'fullName', label: 'Nom complet', type: 'text' as FilterType},
    {key: 'email', label: 'Email', type: 'text' as FilterType},
    {key: 'roles', label: 'Rôles', type: 'text' as FilterType},
    {key: 'centers', label: 'Centres', type: 'text' as FilterType},
    {key: 'active', label: 'Actif', type: 'boolean' as FilterType},
    {key: 'actions', label: 'Actions', type: 'text' as FilterType},
  ] as const;
  readonly visibleColumns = this.userListStore.visibleColumns;
  readonly displayedColumns = computed(() =>
    this.allColumnsConfig.filter((c) => this.visibleColumns()[c.key]).map((c) => c.key),
  );

  readonly rows = this.userListStore.rows;
  readonly total = this.userListStore.total;
  readonly pageIndex = this.userListStore.pageIndex;
  readonly pageSize = this.userListStore.pageSize;
  readonly columnFilters = this.userListStore.columnFilters;
  readonly searchTerm = this.userListStore.searchTerm;
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

  onColumnFilterValue(column: string, value: string): void {
    this.userListStore.setFilter(column, value);
    this.userListStore.loadPage({page: 0, size: this.pageSize()});
  }

  columnFilterValue(column: string): string {
    return this.columnFilters()[column] ?? '';
  }

  isColumnFiltered(column: string): boolean {
    return !!(this.columnFilters()[column] ?? '').trim();
  }

  clearColumnFilter(column: string): void {
    this.userListStore.clearFilter(column);
    this.userListStore.loadPage({page: 0, size: this.pageSize()});
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
    this.userListStore.toggleFilterPanel(column);
  }

  isFilterOpen(column: string): boolean {
    return this.userListStore.openFilterColumn() === column;
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    const target = event.target as HTMLElement | null;
    if (!target) {
      this.userListStore.closeFilterPanel();
      return;
    }
    if (target.closest('.th-wrap')) {
      return;
    }
    this.userListStore.closeFilterPanel();
  }

  clearAllColumnFilters(): void {
    this.userListStore.closeFilterPanel();
    this.userListStore.clearAllFilters();
    this.userListStore.loadPage({page: 0, size: this.pageSize()});
  }

  onPageChange(event: PageEvent): void {
    this.userListStore.setPagination(event.pageIndex, event.pageSize);
    this.userListStore.loadPage({page: event.pageIndex, size: event.pageSize});
  }

  deleteUser(u: AppUser): void {
    const ref = this.dialog.open(ConfirmDialogComponent, {
      width: 'min(96vw, 440px)',
      data: {
        title: "Supprimer l'utilisateur",
        message: `Êtes-vous sûr de vouloir supprimer l'utilisateur ${u.USERNAME} ? Cette action est irréversible.`,
        confirmLabel: 'Supprimer',
        cancelLabel: 'Annuler',
        color: 'warn',
        icon: 'delete',
      },
    });
    ref.afterClosed().subscribe((confirmed) => {
      if (!confirmed) return;
      // ✅ Appel API via le store maintenant
      this.userListStore.deleteUser(u.ID);
      this.snackbar.open('Utilisateur supprimé', 'OK', {duration: 2000});
    });
  }
}
