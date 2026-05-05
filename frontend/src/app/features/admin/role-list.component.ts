import {Component, computed, HostListener, inject, OnInit, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {RouterLink} from '@angular/router';
import {FormsModule} from '@angular/forms';
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
import {AdminApiService, AppRole} from '../../core/api/admin-api.service';
import {ConfirmDialogComponent} from '../../shared/confirm-dialog.component';
import {ColumnFilterRendererComponent} from '../../shared/column-filter-renderer.component';
import {RoleListStore} from './state/role-list.store';

@Component({
  selector: 'app-role-list',
  standalone: true,
  imports: [
    CommonModule, RouterLink, FormsModule, MatTableModule, MatButtonModule, MatIconModule,
    MatCardModule, MatFormFieldModule, MatInputModule, MatMenuModule, MatCheckboxModule,
    MatPaginatorModule, MatSnackBarModule, ColumnFilterRendererComponent
  ],
  template: `
    <mat-card>
      <div class="header">
        <h2><mat-icon>admin_panel_settings</mat-icon> Gestion des rôles</h2>
        <button mat-flat-button color="primary" routerLink="/admin/roles/new"><mat-icon>add</mat-icon> Nouveau rôle</button>
      </div>

      <div class="toolbar">
        <mat-form-field appearance="outline" class="search">
          <mat-label>Rechercher</mat-label>
          <input matInput [ngModel]="searchTerm()" (ngModelChange)="onSearch($event)"/>
          <mat-icon matPrefix>search</mat-icon>
        </mat-form-field>

        <button mat-stroked-button color="primary" [matMenuTriggerFor]="colsMenu">
          <mat-icon>view_column</mat-icon>
          Colonnes
        </button>
        <button mat-stroked-button color="warn" (click)="clearAllColumnFilters()" [disabled]="!hasActiveFilters()">
          <mat-icon>filter_alt_off</mat-icon>
          Réinitialiser filtres
        </button>
        <mat-menu #colsMenu="matMenu">
          @for (c of allColumnsConfig; track c.key) {
            @if (c.key !== 'actions') {
              <button mat-menu-item (click)="$event.stopPropagation()">
                <mat-checkbox [checked]="isColumnVisible(c.key)"
                              (change)="toggleColumn(c.key, $event.checked)">{{ c.label }}
                </mat-checkbox>
              </button>
            }
          }
        </mat-menu>
      </div>

      <table mat-table [dataSource]="rows()" class="full-width">
        <ng-container matColumnDef="code">
          <th mat-header-cell *matHeaderCellDef>
            <div class="th-wrap" [class.open]="isFilterOpen('code')">
              <div class="th-top"><span>Code</span>
                <mat-icon class="filter-ind"
                          (click)="toggleFilterPanel('code', $event)"
                          [class.active]="isColumnFiltered('code')">{{ isColumnFiltered('code') ? 'filter_alt' : 'filter_alt_off' }}
                </mat-icon>
              </div>
              <div class="th-filter">
                <app-column-filter-renderer type="text" [value]="columnFilterValue('code')"
                                            (valueChange)="onColumnFilterValue('code', $event)"
                                            (clear)="clearColumnFilter('code')"/>
              </div>
            </div>
          </th>
          <td mat-cell *matCellDef="let r">{{ r.CODE }}</td>
        </ng-container>

        <ng-container matColumnDef="name">
          <th mat-header-cell *matHeaderCellDef>
            <div class="th-wrap" [class.open]="isFilterOpen('name')">
              <div class="th-top"><span>Nom</span>
                <mat-icon class="filter-ind"
                          (click)="toggleFilterPanel('name', $event)"
                          [class.active]="isColumnFiltered('name')">{{ isColumnFiltered('name') ? 'filter_alt' : 'filter_alt_off' }}
                </mat-icon>
              </div>
              <div class="th-filter">
                <app-column-filter-renderer type="text" [value]="columnFilterValue('name')"
                                            (valueChange)="onColumnFilterValue('name', $event)"
                                            (clear)="clearColumnFilter('name')"/>
              </div>
            </div>
          </th>
          <td mat-cell *matCellDef="let r">{{ r.NAME }}</td>
        </ng-container>

        <ng-container matColumnDef="description">
          <th mat-header-cell *matHeaderCellDef>
            <div class="th-wrap" [class.open]="isFilterOpen('description')">
              <div class="th-top"><span>Description</span>
                <mat-icon class="filter-ind"
                          (click)="toggleFilterPanel('description', $event)"
                          [class.active]="isColumnFiltered('description')">{{ isColumnFiltered('description') ? 'filter_alt' : 'filter_alt_off' }}
                </mat-icon>
              </div>
              <div class="th-filter">
                <app-column-filter-renderer type="text" [value]="columnFilterValue('description')"
                                            (valueChange)="onColumnFilterValue('description', $event)"
                                            (clear)="clearColumnFilter('description')"/>
              </div>
            </div>
          </th>
          <td mat-cell *matCellDef="let r">{{ r.DESCRIPTION }}</td>
        </ng-container>

        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef>Actions</th>
          <td mat-cell *matCellDef="let r">
            <button mat-icon-button color="primary" [routerLink]="['/admin/roles', r.ID, 'edit']"><mat-icon>edit</mat-icon></button>
            <button mat-icon-button color="warn" (click)="deleteRole(r)"><mat-icon>delete</mat-icon></button>
          </td>
        </ng-container>

        <tr mat-header-row *matHeaderRowDef="displayedColumns()"></tr>
        <tr mat-row *matRowDef="let row; columns: displayedColumns();" [attr.data-row-id]="row.ID || ''"></tr>
        <tr class="mat-mdc-row" *matNoDataRow>
          <td class="mat-mdc-cell no-data-cell" [attr.colspan]="displayedColumns().length">
            Aucun role trouve
          </td>
        </tr>
      </table>

      <mat-paginator [length]="total()" [pageIndex]="pageIndex()" [pageSize]="pageSize()"
                     [pageSizeOptions]="[5,10,20,50]" (page)="onPageChange($event)"></mat-paginator>
    </mat-card>
  `,
  styles: [`
    .header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
    .header h2 { display: flex; align-items: center; gap: 8px; color: #1b5e20; margin: 0; }

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
    .full-width { width: 100%; }

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

    .th-filter :where(app-column-filter-renderer) { width: 100%; }

    .no-data-cell {
      text-align: center;
      padding: 14px;
      color: var(--app-muted);
      font-weight: 600;
    }
  `]
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
    {key: 'actions', label: 'Actions'}
  ] as const;
  readonly visibleColumns = signal<Record<string, boolean>>({code: true, name: true, description: true, actions: true});
  readonly displayedColumns = computed(() => this.allColumnsConfig.filter(c => this.visibleColumns()[c.key]).map(c => c.key));

  readonly rows = this.roleListStore.rows;
  readonly total = this.roleListStore.total;
  readonly pageIndex = this.roleListStore.pageIndex;
  readonly pageSize = this.roleListStore.pageSize;
  readonly columnFilters = this.roleListStore.columnFilters;
  readonly searchTerm = this.roleListStore.searchTerm;

  ngOnInit(): void {
    this.fetchPage(0, this.pageSize());
  }

  onSearch(value: string): void {
    this.roleListStore.setSearchTerm(value);
    this.fetchPage(0, this.pageSize());
  }

  toggleColumn(column: string, checked: boolean): void {
    this.visibleColumns.update(prev => ({...prev, [column]: checked}));
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
  private readonly openFilterColumn = signal<string | null>(null);

  toggleFilterPanel(column: string, event: MouseEvent): void {
    event.stopPropagation();
    this.openFilterColumn.update((current) => (current === column ? null : column));
  }

  isFilterOpen(column: string): boolean {
    return this.openFilterColumn() === column;
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    const target = event.target as HTMLElement | null;
    if (!target) {
      this.openFilterColumn.set(null);
      return;
    }
    if (target.closest('.th-wrap')) {
      return;
    }
    this.openFilterColumn.set(null);
  }

  clearAllColumnFilters(): void {
    this.openFilterColumn.set(null);
    this.roleListStore.clearAllFilters();
    this.fetchPage(0, this.pageSize());
  }

  onPageChange(event: PageEvent): void {
    this.roleListStore.setPagination(event.pageIndex, event.pageSize);
    this.fetchPage(event.pageIndex, event.pageSize);
  }

  deleteRole(r: AppRole): void {
    const ref = this.dialog.open(ConfirmDialogComponent, {
      width: '440px',
      data: {
        title: 'Supprimer le rôle',
        message: `Êtes-vous sûr de vouloir supprimer le rôle ${r.CODE} ? Cette action est irréversible.`,
        confirmLabel: 'Supprimer',
        cancelLabel: 'Annuler',
        color: 'warn',
        icon: 'delete'
      }
    });
    ref.afterClosed().subscribe(confirmed => {
      if (!confirmed) return;
      this.api.deleteRole(r.ID).subscribe(() => {
        this.snackbar.open('Rôle supprimé', 'OK', {duration: 2000});
        this.fetchPage(this.pageIndex(), this.pageSize());
      });
    });
  }

  private fetchPage(page: number, size: number): void {
    this.roleListStore.setLoading(true);
    this.api.listRolesPaged({
      page,
      size,
      search: this.searchTerm(),
      filters: this.columnFilters()
    }).subscribe({
      next: (res) => {
        this.roleListStore.setPageData(res.items ?? [], res.total ?? 0, res.page ?? page);
        this.roleListStore.setLoading(false);
      },
      error: () => {
        this.roleListStore.setPageData([], 0, page);
        this.roleListStore.setLoading(false);
      }
    });
  }
}
