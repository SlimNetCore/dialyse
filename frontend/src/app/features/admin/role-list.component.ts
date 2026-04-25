import {Component, computed, inject, OnInit, signal} from '@angular/core';
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

@Component({
  selector: 'app-role-list',
  standalone: true,
  imports: [
    CommonModule, RouterLink, FormsModule, MatTableModule, MatButtonModule, MatIconModule,
    MatCardModule, MatFormFieldModule, MatInputModule, MatMenuModule, MatCheckboxModule,
    MatPaginatorModule, MatSnackBarModule
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
            <div class="th-wrap">
              <div class="th-top"><span>Code</span>
                <mat-icon class="filter-ind"
                          [class.active]="isColumnFiltered('code')">{{ isColumnFiltered('code') ? 'filter_alt' : 'filter_alt_off' }}
                </mat-icon>
              </div>
              <div class="th-filter"><input class="col-filter" matInput [value]="columnFilterValue('code')"
                                            (input)="onColumnFilter('code', $event)"/>@if (isColumnFiltered('code')) {
                <button mat-icon-button class="clear-filter" (click)="clearColumnFilter('code')">
                  <mat-icon>close</mat-icon>
                </button>
              }</div>
            </div>
          </th>
          <td mat-cell *matCellDef="let r">{{ r.CODE }}</td>
        </ng-container>

        <ng-container matColumnDef="name">
          <th mat-header-cell *matHeaderCellDef>
            <div class="th-wrap">
              <div class="th-top"><span>Nom</span>
                <mat-icon class="filter-ind"
                          [class.active]="isColumnFiltered('name')">{{ isColumnFiltered('name') ? 'filter_alt' : 'filter_alt_off' }}
                </mat-icon>
              </div>
              <div class="th-filter"><input class="col-filter" matInput [value]="columnFilterValue('name')"
                                            (input)="onColumnFilter('name', $event)"/>@if (isColumnFiltered('name')) {
                <button mat-icon-button class="clear-filter" (click)="clearColumnFilter('name')">
                  <mat-icon>close</mat-icon>
                </button>
              }</div>
            </div>
          </th>
          <td mat-cell *matCellDef="let r">{{ r.NAME }}</td>
        </ng-container>

        <ng-container matColumnDef="description">
          <th mat-header-cell *matHeaderCellDef>
            <div class="th-wrap">
              <div class="th-top"><span>Description</span>
                <mat-icon class="filter-ind"
                          [class.active]="isColumnFiltered('description')">{{ isColumnFiltered('description') ? 'filter_alt' : 'filter_alt_off' }}
                </mat-icon>
              </div>
              <div class="th-filter"><input class="col-filter" matInput [value]="columnFilterValue('description')"
                                            (input)="onColumnFilter('description', $event)"/>@if (isColumnFiltered('description')) {
                <button mat-icon-button class="clear-filter" (click)="clearColumnFilter('description')">
                  <mat-icon>close</mat-icon>
                </button>
              }</div>
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
        <tr mat-row *matRowDef="let row; columns: displayedColumns();"></tr>
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

    .th-wrap {
      display: grid;
      gap: 6px;
    }

    .th-top {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 6px;
    }

    .th-filter {
      display: flex;
      align-items: center;
      gap: 6px;
      padding: 3px;
      border-radius: 10px;
      background: color-mix(in srgb, var(--app-primary-soft) 60%, white);
      border: 1px solid var(--app-border);
    }

    .filter-ind {
      font-size: 17px;
      width: 17px;
      height: 17px;
      color: #94a3b8;
    }

    .filter-ind.active {
      color: #0ea5e9;
    }

    .col-filter {
      height: 30px;
      width: 100%;
      min-width: 96px;
      font-size: 12px;
      border: 1px solid transparent;
      border-radius: 8px;
      padding: 4px 8px;
      background: #fff;
      outline: none;
    }

    .col-filter:focus {
      border-color: var(--app-primary-outline);
      box-shadow: 0 0 0 3px color-mix(in srgb, var(--app-primary) 14%, white);
    }

    .clear-filter {
      width: 26px;
      height: 26px;
    }

    .clear-filter mat-icon {
      font-size: 16px;
      width: 16px;
      height: 16px;
    }
  `]
})
export class RoleListComponent implements OnInit {
  private readonly api = inject(AdminApiService);
  private readonly snackbar = inject(MatSnackBar);
  private readonly dialog = inject(MatDialog);

  readonly allColumnsConfig = [
    {key: 'code', label: 'Code'},
    {key: 'name', label: 'Nom'},
    {key: 'description', label: 'Description'},
    {key: 'actions', label: 'Actions'}
  ] as const;
  readonly visibleColumns = signal<Record<string, boolean>>({code: true, name: true, description: true, actions: true});
  readonly displayedColumns = computed(() => this.allColumnsConfig.filter(c => this.visibleColumns()[c.key]).map(c => c.key));

  readonly rows = signal<AppRole[]>([]);
  readonly total = signal(0);
  readonly pageIndex = signal(0);
  readonly pageSize = signal(10);
  readonly columnFilters = signal<Record<string, string>>({});
  readonly searchTerm = signal('');

  ngOnInit(): void {
    this.fetchPage(0, this.pageSize());
  }

  onSearch(value: string): void {
    this.searchTerm.set(value);
    this.fetchPage(0, this.pageSize());
  }

  toggleColumn(column: string, checked: boolean): void {
    this.visibleColumns.update(prev => ({...prev, [column]: checked}));
  }

  isColumnVisible(column: string): boolean {
    return !!this.visibleColumns()[column];
  }

  onColumnFilter(column: string, event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.columnFilters.update(prev => ({...prev, [column]: value}));
    this.fetchPage(0, this.pageSize());
  }

  columnFilterValue(column: string): string {
    return this.columnFilters()[column] ?? '';
  }

  isColumnFiltered(column: string): boolean {
    return !!(this.columnFilters()[column] ?? '').trim();
  }

  clearColumnFilter(column: string): void {
    this.columnFilters.update(prev => ({...prev, [column]: ''}));
    this.fetchPage(0, this.pageSize());
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
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
    this.api.listRolesPaged({
      page,
      size,
      search: this.searchTerm(),
      filters: this.columnFilters()
    }).subscribe({
      next: (res) => {
        this.rows.set(res.items ?? []);
        this.total.set(res.total ?? 0);
        this.pageIndex.set(res.page ?? page);
      },
      error: () => {
        this.rows.set([]);
        this.total.set(0);
      }
    });
  }
}
