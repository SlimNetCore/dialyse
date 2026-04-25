import {Component, computed, inject, OnInit, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {MatTableModule} from '@angular/material/table';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatCardModule} from '@angular/material/card';
import {MatTooltipModule} from '@angular/material/tooltip';
import {MatSnackBar, MatSnackBarModule} from '@angular/material/snack-bar';
import {MatInputModule} from '@angular/material/input';
import {MatMenuModule} from '@angular/material/menu';
import {MatCheckboxModule} from '@angular/material/checkbox';
import {MatPaginatorModule, PageEvent} from '@angular/material/paginator';
import {BackendApiService} from '../../../core/api/backend-api.service';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {TranslateModule} from '@ngx-translate/core';

@Component({
  selector: 'app-pec-list',
  standalone: true,
  imports: [
    CommonModule, MatTableModule, MatButtonModule, MatIconModule, MatCardModule,
    MatTooltipModule, MatSnackBarModule, MatInputModule, MatMenuModule, MatCheckboxModule,
    MatPaginatorModule, TranslateModule
  ],
  template: `
    <mat-card class="list-card">
      <div class="header">
        <h2>{{ 'PEC_LIST.TITLE' | translate }}</h2>
        <span class="spacer"></span>
        <button mat-stroked-button color="primary" [matMenuTriggerFor]="colsMenu"><mat-icon>view_column</mat-icon> Colonnes</button>
        <mat-menu #colsMenu="matMenu">
          @for (c of allColumnsConfig; track c.key) {
            @if (c.key !== 'actions') {
              <button mat-menu-item (click)="$event.stopPropagation()">
                <mat-checkbox [checked]="isColumnVisible(c.key)" (change)="toggleColumn(c.key, $event.checked)">{{ c.labelKey | translate }}</mat-checkbox>
              </button>
            }
          }
        </mat-menu>
        <button mat-stroked-button color="primary" (click)="printList()"><mat-icon>print</mat-icon> {{ 'PATIENT_LIST.BTN_PRINT' | translate }}</button>
        <button mat-stroked-button color="primary" (click)="exportExcel()"><mat-icon>table_view</mat-icon> {{ 'PATIENT_LIST.BTN_EXPORT_EXCEL' | translate }}</button>
      </div>

      <div class="table-wrap">
      <table mat-table [dataSource]="rows()" class="w100">
        <ng-container matColumnDef="code">
          <th mat-header-cell *matHeaderCellDef>
            <div class="th-wrap">
              <div class="th-top"><span>{{ 'PEC_LIST.COL_CODE' | translate }}</span><mat-icon class="filter-ind" [class.active]="isColumnFiltered('code')">{{ isColumnFiltered('code') ? 'filter_alt' : 'filter_alt_off' }}</mat-icon></div>
              <div class="th-filter"><input class="col-filter" matInput [value]="columnFilterValue('code')" (input)="onColumnFilter('code', $event)"/>@if (isColumnFiltered('code')) {<button mat-icon-button class="clear-filter" (click)="clearColumnFilter('code')"><mat-icon>close</mat-icon></button>}</div>
            </div>
          </th>
          <td mat-cell *matCellDef="let r">{{r.CODE_PATIENT || r.code_patient}}</td>
        </ng-container>

        <ng-container matColumnDef="nom">
          <th mat-header-cell *matHeaderCellDef>
            <div class="th-wrap">
              <div class="th-top"><span>{{ 'PEC_LIST.COL_PATIENT' | translate }}</span><mat-icon class="filter-ind" [class.active]="isColumnFiltered('nom')">{{ isColumnFiltered('nom') ? 'filter_alt' : 'filter_alt_off' }}</mat-icon></div>
              <div class="th-filter"><input class="col-filter" matInput [value]="columnFilterValue('nom')" (input)="onColumnFilter('nom', $event)"/>@if (isColumnFiltered('nom')) {<button mat-icon-button class="clear-filter" (click)="clearColumnFilter('nom')"><mat-icon>close</mat-icon></button>}</div>
            </div>
          </th>
          <td mat-cell *matCellDef="let r">{{r.NOM || r.nom}} {{r.PRENOM || r.prenom}}</td>
        </ng-container>

        <ng-container matColumnDef="assurance">
          <th mat-header-cell *matHeaderCellDef>
            <div class="th-wrap">
              <div class="th-top"><span>{{ 'PEC_LIST.COL_ASSURANCE' | translate }}</span><mat-icon class="filter-ind" [class.active]="isColumnFiltered('assurance')">{{ isColumnFiltered('assurance') ? 'filter_alt' : 'filter_alt_off' }}</mat-icon></div>
              <div class="th-filter"><input class="col-filter" matInput [value]="columnFilterValue('assurance')" (input)="onColumnFilter('assurance', $event)"/>@if (isColumnFiltered('assurance')) {<button mat-icon-button class="clear-filter" (click)="clearColumnFilter('assurance')"><mat-icon>close</mat-icon></button>}</div>
            </div>
          </th>
          <td mat-cell *matCellDef="let r">{{r.NUMERO_ASSURANCE || r.numero_assurance}}</td>
        </ng-container>

        <ng-container matColumnDef="debut">
          <th mat-header-cell *matHeaderCellDef>
            <div class="th-wrap">
              <div class="th-top"><span>{{ 'PEC_LIST.COL_DEBUT' | translate }}</span><mat-icon class="filter-ind" [class.active]="isColumnFiltered('debut')">{{ isColumnFiltered('debut') ? 'filter_alt' : 'filter_alt_off' }}</mat-icon></div>
              <div class="th-filter"><input class="col-filter" type="date" matInput [value]="columnFilterValue('debut')" (input)="onColumnFilter('debut', $event)"/>@if (isColumnFiltered('debut')) {<button mat-icon-button class="clear-filter" (click)="clearColumnFilter('debut')"><mat-icon>close</mat-icon></button>}</div>
            </div>
          </th>
          <td mat-cell *matCellDef="let r">{{r.DATE_DEBUT_DEMANDE || r.date_debut_demande}}</td>
        </ng-container>

        <ng-container matColumnDef="fin">
          <th mat-header-cell *matHeaderCellDef>
            <div class="th-wrap">
              <div class="th-top"><span>{{ 'PEC_LIST.COL_FIN' | translate }}</span><mat-icon class="filter-ind" [class.active]="isColumnFiltered('fin')">{{ isColumnFiltered('fin') ? 'filter_alt' : 'filter_alt_off' }}</mat-icon></div>
              <div class="th-filter"><input class="col-filter" type="date" matInput [value]="columnFilterValue('fin')" (input)="onColumnFilter('fin', $event)"/>@if (isColumnFiltered('fin')) {<button mat-icon-button class="clear-filter" (click)="clearColumnFilter('fin')"><mat-icon>close</mat-icon></button>}</div>
            </div>
          </th>
          <td mat-cell *matCellDef="let r">{{r.DATE_FIN_DEMANDE || r.date_fin_demande}}</td>
        </ng-container>

        <ng-container matColumnDef="statut">
          <th mat-header-cell *matHeaderCellDef>
            <div class="th-wrap">
              <div class="th-top"><span>{{ 'PEC_LIST.COL_STATUS' | translate }}</span><mat-icon class="filter-ind" [class.active]="isColumnFiltered('statut')">{{ isColumnFiltered('statut') ? 'filter_alt' : 'filter_alt_off' }}</mat-icon></div>
              <div class="th-filter"><input class="col-filter" matInput [value]="columnFilterValue('statut')" (input)="onColumnFilter('statut', $event)"/>@if (isColumnFiltered('statut')) {<button mat-icon-button class="clear-filter" (click)="clearColumnFilter('statut')"><mat-icon>close</mat-icon></button>}</div>
            </div>
          </th>
          <td mat-cell *matCellDef="let r"><span class="status-badge">{{r.STATUT || r.statut}}</span></td>
        </ng-container>

        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef>{{ 'PEC_LIST.COL_ACTIONS' | translate }}</th>
          <td mat-cell *matCellDef="let r"><button mat-icon-button color="primary" (click)="printRow(r)" [matTooltip]="'PATIENT_LIST.BTN_PRINT' | translate"><mat-icon>print</mat-icon></button></td>
        </ng-container>

        <tr mat-header-row *matHeaderRowDef="displayedColumns()"></tr>
        <tr mat-row *matRowDef="let row; columns: displayedColumns()"></tr>
      </table>
      </div>

      <mat-paginator [length]="total()" [pageIndex]="pageIndex()" [pageSize]="pageSize()" [pageSizeOptions]="[5,10,20,50]" (page)="onPageChange($event)"></mat-paginator>
    </mat-card>
  `,
  styles: [`
    .list-card { padding: 10px; border: 1px solid var(--app-border); background: var(--app-surface); box-shadow: var(--app-shadow); }
    .header { display: flex; align-items: center; margin-bottom: 10px; gap: 8px; flex-wrap: wrap; }
    .header h2 { margin: 0; color: var(--app-text); font-size: 1.2rem; }
    .spacer { flex: 1; }
    .table-wrap { overflow: auto; border: 1px solid var(--app-border); border-radius: 12px; background: var(--app-surface); }
    .w100 { width: 100%; }
    .w100 .mat-mdc-header-cell { color: var(--app-primary); font-weight: 700; }
    .w100 .mat-mdc-row:hover { background: color-mix(in srgb, var(--app-primary-soft) 70%, white); }
    .status-badge { padding: 2px 10px; border-radius: 999px; font-size: 11px; font-weight: 700; color: var(--app-primary); background: var(--app-primary-soft); border: 1px solid var(--app-primary-outline); }
    .th-wrap { display: grid; gap: 6px; }
    .th-top { display: flex; align-items: center; justify-content: space-between; gap: 6px; }
    .th-filter { display: flex; align-items: center; gap: 6px; padding: 3px; border-radius: 10px; background: color-mix(in srgb, var(--app-primary-soft) 60%, white); border: 1px solid var(--app-border); }
    .filter-ind { font-size: 17px; width: 17px; height: 17px; color: #94a3b8; }
    .filter-ind.active { color: var(--app-primary); }
    .col-filter { height: 30px; width: 100%; min-width: 96px; font-size: 12px; border: 1px solid transparent; border-radius: 8px; padding: 4px 8px; background: #fff; outline: none; }
    .col-filter:focus { border-color: var(--app-primary-outline); box-shadow: 0 0 0 3px color-mix(in srgb, var(--app-primary) 14%, white); }
    .clear-filter { width: 26px; height: 26px; }
    .clear-filter mat-icon { font-size: 16px; width: 16px; height: 16px; }
  `]
})
export class PecListComponent implements OnInit {
  private readonly api = inject(BackendApiService);
  private readonly store = inject(AppShellStore);
  private readonly snack = inject(MatSnackBar);

  readonly rows = signal<any[]>([]);
  readonly total = signal(0);
  readonly pageIndex = signal(0);
  readonly pageSize = signal(10);

  readonly allColumnsConfig = [
    {key: 'code', labelKey: 'PEC_LIST.COL_CODE'},
    {key: 'nom', labelKey: 'PEC_LIST.COL_PATIENT'},
    {key: 'assurance', labelKey: 'PEC_LIST.COL_ASSURANCE'},
    {key: 'debut', labelKey: 'PEC_LIST.COL_DEBUT'},
    {key: 'fin', labelKey: 'PEC_LIST.COL_FIN'},
    {key: 'statut', labelKey: 'PEC_LIST.COL_STATUS'},
    {key: 'actions', labelKey: 'PEC_LIST.COL_ACTIONS'}
  ] as const;
  readonly visibleColumns = signal<Record<string, boolean>>({
    code: true,
    nom: true,
    assurance: true,
    debut: true,
    fin: true,
    statut: true,
    actions: true
  });
  readonly displayedColumns = computed(() => this.allColumnsConfig.filter(c => this.visibleColumns()[c.key]).map(c => c.key));
  readonly columnFilters = signal<Record<string, string>>({});

  ngOnInit(): void {
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

  private fetchPage(page: number, size: number): void {
    const cid = this.store.currentCenterId();
    if (!cid) {
      this.rows.set([]);
      this.total.set(0);
      return;
    }

    this.api.listPecsDetailed(cid, {page, size, filters: this.columnFilters()}).subscribe({
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

  printList(): void {
    const cid = this.store.currentCenterId();
    if (!cid) return;
    this.api.printDocument(cid, 'LISTE_PEC', {}).subscribe({
      next: (blob) => window.open(URL.createObjectURL(blob), '_blank'),
      error: (e) => this.snack.open('Erreur impression: ' + (e?.error?.text || e.message), 'OK', { duration: 5000 })
    });
  }

  exportExcel(): void {
    const cid = this.store.currentCenterId();
    if (!cid) return;
    this.api.printDocument(cid, 'LISTE_PEC', {}, 'EXCEL').subscribe({
      next: (blob) => {
        const a = document.createElement('a');
        a.href = URL.createObjectURL(blob);
        a.download = 'liste-pec.xls';
        a.click();
      },
      error: (e) => this.snack.open('Erreur export: ' + (e?.error?.text || e.message), 'OK', { duration: 5000 })
    });
  }

  printRow(r: any): void {
    const cid = this.store.currentCenterId();
    const pid = (r.PATIENT_ID || r.patient_id || '').toString();
    if (!cid || !pid) return;
    this.api.printDocument(cid, 'PEC', { patientId: pid }).subscribe({
      next: (blob) => window.open(URL.createObjectURL(blob), '_blank'),
      error: (e) => this.snack.open('Erreur impression: ' + (e?.error?.text || e.message), 'OK', { duration: 5000 })
    });
  }
}
