import {Component, computed, inject, OnInit, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {RouterLink} from '@angular/router';
import {FormsModule} from '@angular/forms';
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
import {AdminApiService, AppUser} from '../../core/api/admin-api.service';
import {ConfirmDialogComponent} from '../../shared/confirm-dialog.component';

type FilterType = 'text' | 'boolean';

@Component({
  selector: 'app-user-list',
  standalone: true,
  imports: [
    CommonModule, RouterLink, FormsModule, MatTableModule, MatButtonModule, MatIconModule,
    MatChipsModule, MatCardModule, MatFormFieldModule, MatInputModule, MatSnackBarModule,
    MatMenuModule, MatCheckboxModule, MatPaginatorModule, TranslateModule
  ],
  template: `
    <mat-card>
      <div class="header">
        <h2><mat-icon>people</mat-icon> Gestion des utilisateurs</h2>
        <button mat-flat-button color="primary" routerLink="/admin/users/new"><mat-icon>person_add</mat-icon> Nouvel utilisateur</button>
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
        <ng-container matColumnDef="username">
          <th mat-header-cell *matHeaderCellDef>
            <div class="th-wrap">
              <div class="th-top"><span>Nom d'utilisateur</span>
                <mat-icon class="filter-ind"
                          [class.active]="isColumnFiltered('username')">{{ isColumnFiltered('username') ? 'filter_alt' : 'filter_alt_off' }}
                </mat-icon>
              </div>
              <div class="th-filter"><input class="col-filter" matInput [value]="columnFilterValue('username')"
                                            (input)="onColumnFilter('username', $event)"/>@if (isColumnFiltered('username')) {
                <button mat-icon-button class="clear-filter" (click)="clearColumnFilter('username')">
                  <mat-icon>close</mat-icon>
                </button>
              }</div>
            </div>
          </th>
          <td mat-cell *matCellDef="let u">{{ u.USERNAME }}</td>
        </ng-container>

        <ng-container matColumnDef="fullName">
          <th mat-header-cell *matHeaderCellDef>
            <div class="th-wrap">
              <div class="th-top"><span>Nom complet</span>
                <mat-icon class="filter-ind"
                          [class.active]="isColumnFiltered('fullName')">{{ isColumnFiltered('fullName') ? 'filter_alt' : 'filter_alt_off' }}
                </mat-icon>
              </div>
              <div class="th-filter"><input class="col-filter" matInput [value]="columnFilterValue('fullName')"
                                            (input)="onColumnFilter('fullName', $event)"/>@if (isColumnFiltered('fullName')) {
                <button mat-icon-button class="clear-filter" (click)="clearColumnFilter('fullName')">
                  <mat-icon>close</mat-icon>
                </button>
              }</div>
            </div>
          </th>
          <td mat-cell *matCellDef="let u">{{ u.FULL_NAME }}</td>
        </ng-container>

        <ng-container matColumnDef="email">
          <th mat-header-cell *matHeaderCellDef>
            <div class="th-wrap">
              <div class="th-top"><span>Email</span>
                <mat-icon class="filter-ind"
                          [class.active]="isColumnFiltered('email')">{{ isColumnFiltered('email') ? 'filter_alt' : 'filter_alt_off' }}
                </mat-icon>
              </div>
              <div class="th-filter"><input class="col-filter" matInput [value]="columnFilterValue('email')"
                                            (input)="onColumnFilter('email', $event)"/>@if (isColumnFiltered('email')) {
                <button mat-icon-button class="clear-filter" (click)="clearColumnFilter('email')">
                  <mat-icon>close</mat-icon>
                </button>
              }</div>
            </div>
          </th>
          <td mat-cell *matCellDef="let u">{{ u.EMAIL }}</td>
        </ng-container>

        <ng-container matColumnDef="roles">
          <th mat-header-cell *matHeaderCellDef>
            <div class="th-wrap">
              <div class="th-top"><span>Rôles</span>
                <mat-icon class="filter-ind"
                          [class.active]="isColumnFiltered('roles')">{{ isColumnFiltered('roles') ? 'filter_alt' : 'filter_alt_off' }}
                </mat-icon>
              </div>
              <div class="th-filter"><input class="col-filter" matInput [value]="columnFilterValue('roles')"
                                            (input)="onColumnFilter('roles', $event)"/>@if (isColumnFiltered('roles')) {
                <button mat-icon-button class="clear-filter" (click)="clearColumnFilter('roles')">
                  <mat-icon>close</mat-icon>
                </button>
              }</div>
            </div>
          </th>
          <td mat-cell *matCellDef="let u">
            <mat-chip-set>@for (r of u.roles; track r.ID) {
              <mat-chip>{{ r.NAME }}</mat-chip>
            }</mat-chip-set>
          </td>
        </ng-container>

        <ng-container matColumnDef="centers">
          <th mat-header-cell *matHeaderCellDef>
            <div class="th-wrap">
              <div class="th-top"><span>Centres</span>
                <mat-icon class="filter-ind"
                          [class.active]="isColumnFiltered('centers')">{{ isColumnFiltered('centers') ? 'filter_alt' : 'filter_alt_off' }}
                </mat-icon>
              </div>
              <div class="th-filter"><input class="col-filter" matInput [value]="columnFilterValue('centers')"
                                            (input)="onColumnFilter('centers', $event)"/>@if (isColumnFiltered('centers')) {
                <button mat-icon-button class="clear-filter" (click)="clearColumnFilter('centers')">
                  <mat-icon>close</mat-icon>
                </button>
              }</div>
            </div>
          </th>
          <td mat-cell *matCellDef="let u">
            <mat-chip-set>@for (c of u.centers; track c.ID) {
              <mat-chip>{{ c.NAME }}</mat-chip>
            }</mat-chip-set>
          </td>
        </ng-container>

        <ng-container matColumnDef="active">
          <th mat-header-cell *matHeaderCellDef>
            <div class="th-wrap">
              <div class="th-top"><span>Actif</span>
                <mat-icon class="filter-ind"
                          [class.active]="isColumnFiltered('active')">{{ isColumnFiltered('active') ? 'filter_alt' : 'filter_alt_off' }}
                </mat-icon>
              </div>
              <div class="th-filter">
                <select class="col-filter" [value]="columnFilterValue('active')"
                        (change)="onBooleanFilter('active', $event)">
                  <option value="">Tous</option>
                  <option value="true">Actif</option>
                  <option value="false">Inactif</option>
                </select>
                @if (isColumnFiltered('active')) {
                  <button mat-icon-button class="clear-filter" (click)="clearColumnFilter('active')">
                    <mat-icon>close</mat-icon>
                  </button>
                }
              </div>
            </div>
          </th>
          <td mat-cell *matCellDef="let u">
            <mat-icon [style.color]="u.ACTIVE ? '#1b5e20' : '#c62828'">{{ u.ACTIVE ? 'check_circle' : 'cancel' }}
            </mat-icon>
          </td>
        </ng-container>

        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef>Actions</th>
          <td mat-cell *matCellDef="let u">
            <button mat-icon-button color="primary" [routerLink]="['/admin/users', u.ID, 'edit']"><mat-icon>edit</mat-icon></button>
            <button mat-icon-button color="warn" (click)="deleteUser(u)"><mat-icon>delete</mat-icon></button>
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
    .toolbar { display: flex; align-items: center; gap: 12px; margin-bottom: 8px; flex-wrap: wrap; }
    .search { width: min(440px, 100%); }
    .full-width { width: 100%; }
    .th-wrap { display: grid; gap: 6px; }
    .th-top { display: flex; align-items: center; justify-content: space-between; gap: 6px; }
    .th-filter { display: flex; align-items: center; gap: 6px; padding: 3px; border-radius: 10px; background: color-mix(in srgb, var(--app-primary-soft) 60%, white); border: 1px solid var(--app-border); }
    .filter-ind { font-size: 17px; width: 17px; height: 17px; color: #94a3b8; }
    .filter-ind.active { color: #0ea5e9; }
    .col-filter { height: 30px; width: 100%; min-width: 96px; font-size: 12px; border: 1px solid transparent; border-radius: 8px; padding: 4px 8px; background: #fff; outline: none; }
    .col-filter:focus { border-color: var(--app-primary-outline); box-shadow: 0 0 0 3px color-mix(in srgb, var(--app-primary) 14%, white); }
    .clear-filter { width: 26px; height: 26px; }
    .clear-filter mat-icon { font-size: 16px; width: 16px; height: 16px; }
  `]
})
export class UserListComponent implements OnInit {
  private readonly api = inject(AdminApiService);
  private readonly snackbar = inject(MatSnackBar);
  private readonly dialog = inject(MatDialog);

  readonly allColumnsConfig = [
    {key: 'username', label: "Nom d'utilisateur", type: 'text' as FilterType},
    {key: 'fullName', label: 'Nom complet', type: 'text' as FilterType},
    {key: 'email', label: 'Email', type: 'text' as FilterType},
    {key: 'roles', label: 'Rôles', type: 'text' as FilterType},
    {key: 'centers', label: 'Centres', type: 'text' as FilterType},
    {key: 'active', label: 'Actif', type: 'boolean' as FilterType},
    {key: 'actions', label: 'Actions', type: 'text' as FilterType}
  ] as const;
  readonly visibleColumns = signal<Record<string, boolean>>({
    username: true,
    fullName: true,
    email: true,
    roles: true,
    centers: true,
    active: true,
    actions: true
  });
  readonly displayedColumns = computed(() => this.allColumnsConfig.filter(c => this.visibleColumns()[c.key]).map(c => c.key));

  readonly rows = signal<AppUser[]>([]);
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

  onBooleanFilter(column: string, event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
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

  deleteUser(u: AppUser): void {
    const ref = this.dialog.open(ConfirmDialogComponent, {
      width: '440px',
      data: {
        title: 'Supprimer l\'utilisateur',
        message: `Êtes-vous sûr de vouloir supprimer l'utilisateur ${u.USERNAME} ? Cette action est irréversible.`,
        confirmLabel: 'Supprimer',
        cancelLabel: 'Annuler',
        color: 'warn',
        icon: 'delete'
      }
    });
    ref.afterClosed().subscribe(confirmed => {
      if (!confirmed) return;
      this.api.deleteUser(u.ID).subscribe(() => {
        this.snackbar.open('Utilisateur supprimé', 'OK', {duration: 2000});
        this.fetchPage(this.pageIndex(), this.pageSize());
      });
    });
  }

  private fetchPage(page: number, size: number): void {
    this.api.listUsersPaged({
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

