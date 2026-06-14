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
  template: `
    <mat-card>
      <div class="header">
        <h2><mat-icon>people</mat-icon> Gestion des utilisateurs</h2>
        <button mat-flat-button color="primary" routerLink="/admin/users/new">
          <mat-icon>person_add</mat-icon>
          Nouvel utilisateur
        </button>
      </div>

      <div class="toolbar">
        <mat-form-field appearance="outline" class="search">
          <mat-label>Rechercher</mat-label>
          <input matInput [formField]="searchForm.term"/>
          <mat-icon matPrefix>search</mat-icon>
        </mat-form-field>

        <button mat-stroked-button color="primary" [matMenuTriggerFor]="colsMenu">
          <mat-icon>view_column</mat-icon>
          Colonnes
        </button>
        <button
          mat-stroked-button
          color="warn"
          (click)="clearAllColumnFilters()"
          [disabled]="!hasActiveFilters()"
        >
          <mat-icon>filter_alt_off</mat-icon>
          Réinitialiser filtres
        </button>
        <mat-menu #colsMenu="matMenu">
          @for (c of allColumnsConfig; track c.key) {
            @if (c.key !== 'actions') {
              <button mat-menu-item (click)="$event.stopPropagation()">
                <mat-checkbox
                  [checked]="isColumnVisible(c.key)"
                  (change)="toggleColumn(c.key, $event.checked)"
                >{{ c.label }}
                </mat-checkbox>
              </button>
            }
          }
        </mat-menu>
      </div>

      <div class="table-wrap">
        <table mat-table [dataSource]="rows()" class="full-width">
          <ng-container matColumnDef="username">
            <th mat-header-cell *matHeaderCellDef>
              <div class="th-wrap" [class.open]="isFilterOpen('username')">
                <div class="th-top">
                  <span>Nom d'utilisateur</span>
                  <mat-icon
                    class="filter-ind"
                    (click)="toggleFilterPanel('username', $event)"
                    [class.active]="isColumnFiltered('username')"
                  >{{ isColumnFiltered('username') ? 'filter_alt' : 'filter_alt_off' }}
                  </mat-icon>
                </div>
                <div class="th-filter">
                  <app-column-filter-renderer
                    type="text"
                    [value]="columnFilterValue('username')"
                    (valueChange)="onColumnFilterValue('username', $event)"
                    (clear)="clearColumnFilter('username')"
                  />
                </div>
              </div>
            </th>
            <td mat-cell *matCellDef="let u">{{ u.USERNAME }}</td>
          </ng-container>

          <ng-container matColumnDef="fullName">
            <th mat-header-cell *matHeaderCellDef>
              <div class="th-wrap" [class.open]="isFilterOpen('fullName')">
                <div class="th-top">
                  <span>Nom complet</span>
                  <mat-icon
                    class="filter-ind"
                    (click)="toggleFilterPanel('fullName', $event)"
                    [class.active]="isColumnFiltered('fullName')"
                  >{{ isColumnFiltered('fullName') ? 'filter_alt' : 'filter_alt_off' }}
                  </mat-icon>
                </div>
                <div class="th-filter">
                  <app-column-filter-renderer
                    type="text"
                    [value]="columnFilterValue('fullName')"
                    (valueChange)="onColumnFilterValue('fullName', $event)"
                    (clear)="clearColumnFilter('fullName')"
                  />
                </div>
              </div>
            </th>
            <td mat-cell *matCellDef="let u">{{ u.FULL_NAME }}</td>
          </ng-container>

          <ng-container matColumnDef="email">
            <th mat-header-cell *matHeaderCellDef>
              <div class="th-wrap" [class.open]="isFilterOpen('email')">
                <div class="th-top">
                  <span>Email</span>
                  <mat-icon
                    class="filter-ind"
                    (click)="toggleFilterPanel('email', $event)"
                    [class.active]="isColumnFiltered('email')"
                  >{{ isColumnFiltered('email') ? 'filter_alt' : 'filter_alt_off' }}
                  </mat-icon>
                </div>
                <div class="th-filter">
                  <app-column-filter-renderer
                    type="text"
                    [value]="columnFilterValue('email')"
                    (valueChange)="onColumnFilterValue('email', $event)"
                    (clear)="clearColumnFilter('email')"
                  />
                </div>
              </div>
            </th>
            <td mat-cell *matCellDef="let u">{{ u.EMAIL }}</td>
          </ng-container>

          <ng-container matColumnDef="roles">
            <th mat-header-cell *matHeaderCellDef>
              <div class="th-wrap" [class.open]="isFilterOpen('roles')">
                <div class="th-top">
                  <span>Rôles</span>
                  <mat-icon
                    class="filter-ind"
                    (click)="toggleFilterPanel('roles', $event)"
                    [class.active]="isColumnFiltered('roles')"
                  >{{ isColumnFiltered('roles') ? 'filter_alt' : 'filter_alt_off' }}
                  </mat-icon>
                </div>
                <div class="th-filter">
                  <app-column-filter-renderer
                    type="text"
                    [value]="columnFilterValue('roles')"
                    (valueChange)="onColumnFilterValue('roles', $event)"
                    (clear)="clearColumnFilter('roles')"
                  />
                </div>
              </div>
            </th>
            <td mat-cell *matCellDef="let u">
              <mat-chip-set>
                @for (r of u.roles; track r.ID) {
                  <mat-chip>{{ r.NAME }}</mat-chip>
                }
              </mat-chip-set>
            </td>
          </ng-container>

          <ng-container matColumnDef="centers">
            <th mat-header-cell *matHeaderCellDef>
              <div class="th-wrap" [class.open]="isFilterOpen('centers')">
                <div class="th-top">
                  <span>Centres</span>
                  <mat-icon
                    class="filter-ind"
                    (click)="toggleFilterPanel('centers', $event)"
                    [class.active]="isColumnFiltered('centers')"
                  >{{ isColumnFiltered('centers') ? 'filter_alt' : 'filter_alt_off' }}
                  </mat-icon>
                </div>
                <div class="th-filter">
                  <app-column-filter-renderer
                    type="text"
                    [value]="columnFilterValue('centers')"
                    (valueChange)="onColumnFilterValue('centers', $event)"
                    (clear)="clearColumnFilter('centers')"
                  />
                </div>
              </div>
            </th>
            <td mat-cell *matCellDef="let u">
              <mat-chip-set>
                @for (c of u.centers; track c.ID) {
                  <mat-chip>{{ c.NAME }}</mat-chip>
                }
              </mat-chip-set>
            </td>
          </ng-container>

          <ng-container matColumnDef="active">
            <th mat-header-cell *matHeaderCellDef>
              <div class="th-wrap" [class.open]="isFilterOpen('active')">
                <div class="th-top">
                  <span>Actif</span>
                  <mat-icon
                    class="filter-ind"
                    (click)="toggleFilterPanel('active', $event)"
                    [class.active]="isColumnFiltered('active')"
                  >{{ isColumnFiltered('active') ? 'filter_alt' : 'filter_alt_off' }}
                  </mat-icon>
                </div>
                <div class="th-filter">
                  <app-column-filter-renderer
                    type="boolean"
                    [value]="columnFilterValue('active')"
                    (valueChange)="onColumnFilterValue('active', $event)"
                    (clear)="clearColumnFilter('active')"
                  />
                </div>
              </div>
            </th>
            <td mat-cell *matCellDef="let u">
              <mat-icon [style.color]="u.ACTIVE ? '#1b5e20' : '#c62828'"
              >{{ u.ACTIVE ? 'check_circle' : 'cancel' }}
              </mat-icon>
            </td>
          </ng-container>

          <ng-container matColumnDef="actions">
            <th mat-header-cell *matHeaderCellDef>Actions</th>
            <td mat-cell *matCellDef="let u">
              <button mat-icon-button color="primary" [routerLink]="['/admin/users', u.ID, 'edit']">
                <mat-icon>edit</mat-icon>
              </button>
              <button mat-icon-button color="warn" (click)="deleteUser(u)">
                <mat-icon>delete</mat-icon>
              </button>
            </td>
          </ng-container>

          <tr mat-header-row *matHeaderRowDef="displayedColumns()"></tr>
          <tr
            mat-row
            *matRowDef="let row; columns: displayedColumns()"
            [attr.data-row-id]="row?.ID"
          ></tr>
          <tr class="mat-mdc-row" *matNoDataRow>
            <td class="mat-mdc-cell no-data-cell" [attr.colspan]="displayedColumns().length">
              Aucun utilisateur trouve
            </td>
          </tr>
        </table>
      </div>

      <mat-paginator
        [length]="total()"
        [pageIndex]="pageIndex()"
        [pageSize]="pageSize()"
        [pageSizeOptions]="[5, 10, 20, 50]"
        (page)="onPageChange($event)"
      ></mat-paginator>
    </mat-card>
  `,
  changeDetection: ChangeDetectionStrategy.Eager,
  styles: [
    `
      .header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 16px;
      }

      .header h2 {
        display: flex;
        align-items: center;
        gap: 8px;
        color: #1b5e20;
        margin: 0;
      }

      .toolbar {
        display: flex;
        align-items: center;
        gap: 12px;
        margin-bottom: 8px;
        flex-wrap: wrap;
      }

      .search {
        width: min(440px, 100%);
      }

      .table-wrap {
        overflow: auto;
        border-radius: 16px;
        border: 1px solid var(--app-border);
        background: var(--app-surface-solid);
        margin-bottom: 8px;
      }

      .full-width {
        width: 100%;
      }

      .full-width .mat-mdc-header-cell {
        overflow: visible !important;
        position: relative;
        z-index: 5;
      }

      .full-width .mat-mdc-header-cell:has(.filter-ind:hover),
      .full-width .mat-mdc-header-cell:has(.th-filter:hover),
      .full-width .mat-mdc-header-cell:has(.filter-ind.active),
      .full-width .mat-mdc-header-cell:focus-within {
        z-index: 2000;
      }

      .full-width,
      .full-width .mat-mdc-header-row,
      .full-width .mat-mdc-row,
      .full-width .mat-mdc-cell,
      .full-width .mat-mdc-header-cell {
        overflow: visible;
      }

      .th-wrap {
        display: grid;
        gap: 6px;
        position: relative;
        overflow: visible;
        z-index: 6;
      }

      .th-wrap.open {
        z-index: 2101;
      }

      .th-top {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 6px;
      }

      .th-filter {
        display: none;
        align-items: center;
        gap: 6px;
        padding: 3px;
        position: absolute;
        top: calc(100% + 4px);
        left: 0;
        min-width: 240px;
        width: max-content;
        max-width: 360px;
        z-index: 2100;
        border-radius: 10px;
        box-shadow: 0 10px 25px rgba(2, 6, 23, 0.12);
        background: color-mix(in srgb, var(--app-primary-soft) 60%, white);
        border: 1px solid var(--app-border);
      }

      .th-wrap.open .th-filter {
        display: flex;
      }

      @media (max-width: 760px) {
        .th-filter {
          position: fixed;
          top: var(--filter-row-bottom, 200px);
          bottom: auto;
          left: 0;
          right: 0;
          transform: none;
          width: 100vw;
          max-width: 100vw;
          min-width: 100vw;
          border-radius: 0;
          z-index: 2300;
          margin: 0;
          padding: 14px 16px;
          box-sizing: border-box;
          box-shadow: 0 8px 32px rgba(0, 0, 0, 0.18),
          0 2px 8px rgba(0, 0, 0, 0.1);
        }
      }

      .filter-ind {
        font-size: 17px;
        width: 17px;
        height: 17px;
        color: #94a3b8;
        cursor: pointer;
      }

      .filter-ind.active {
        color: #dc2626;
      }

      .th-filter :where(app-column-filter-renderer) {
        width: 100%;
      }

      .no-data-cell {
        text-align: center;
        padding: 14px;
        color: var(--app-muted);
        font-weight: 600;
      }

      @media (max-width: 900px) {
        .header {
          align-items: flex-start;
        }

        .header h2 {
          font-size: 1.05rem;
        }

        .full-width {
          min-width: 760px;
        }
      }
    `,
  ],
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
