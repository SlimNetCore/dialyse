import {Component, computed, HostListener, inject, OnInit, signal} from '@angular/core';
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
import {ColumnFilterRendererComponent} from '../../../shared/column-filter-renderer.component';
import {AttestationListStore} from './state/attestation-list.store';

@Component({
  selector: 'app-attestation-list',
  standalone: true,
  imports: [
    CommonModule, MatTableModule, MatButtonModule, MatIconModule, MatCardModule,
    MatTooltipModule, MatSnackBarModule, MatInputModule, MatMenuModule, MatCheckboxModule,
    MatPaginatorModule, TranslateModule, ColumnFilterRendererComponent
  ],
  template: `
    <mat-card class="list-card">
      <div class="header">
        <h2>{{ 'ATTEST_LIST.TITLE' | translate }}</h2>
        <span class="spacer"></span>
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
                              (change)="toggleColumn(c.key, $event.checked)">{{ c.labelKey | translate }}
                </mat-checkbox>
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
            <div class="th-wrap" [class.open]="isFilterOpen('code')">
              <div class="th-top"><span>{{ 'ATTEST_LIST.COL_CODE' | translate }}</span>
                <mat-icon class="filter-ind"
                          (click)="toggleFilterPanel('code', $event)"
                          [class.active]="isColumnFiltered('code')">{{ isColumnFiltered('code') ? 'filter_alt' : 'filter_alt_off' }}
                </mat-icon>
              </div>
              <div class="th-filter">
                <app-column-filter-renderer type="text" labelKey="ATTEST_LIST.COL_CODE" [value]="columnFilterValue('code')"
                                            (valueChange)="onColumnFilterValue('code', $event)"
                                            (clear)="clearColumnFilter('code')"/>
              </div>
            </div>
          </th>
          <td mat-cell *matCellDef="let r">{{ r.CODE_PATIENT || r.code_patient }}</td>
        </ng-container>
        <ng-container matColumnDef="nom">
          <th mat-header-cell *matHeaderCellDef>
            <div class="th-wrap" [class.open]="isFilterOpen('nom')">
              <div class="th-top"><span>{{ 'ATTEST_LIST.COL_PATIENT' | translate }}</span>
                <mat-icon class="filter-ind"
                          (click)="toggleFilterPanel('nom', $event)"
                          [class.active]="isColumnFiltered('nom')">{{ isColumnFiltered('nom') ? 'filter_alt' : 'filter_alt_off' }}
                </mat-icon>
              </div>
              <div class="th-filter">
                <app-column-filter-renderer type="text" labelKey="ATTEST_LIST.COL_PATIENT" [value]="columnFilterValue('nom')"
                                            (valueChange)="onColumnFilterValue('nom', $event)"
                                            (clear)="clearColumnFilter('nom')"/>
              </div>
            </div>
          </th>
          <td mat-cell *matCellDef="let r">{{ r.NOM || r.nom }} {{ r.PRENOM || r.prenom }}</td>
        </ng-container>
        <ng-container matColumnDef="assurance">
          <th mat-header-cell *matHeaderCellDef>
            <div class="th-wrap" [class.open]="isFilterOpen('assurance')">
              <div class="th-top"><span>{{ 'ATTEST_LIST.COL_ASSURANCE' | translate }}</span>
                <mat-icon class="filter-ind"
                          (click)="toggleFilterPanel('assurance', $event)"
                          [class.active]="isColumnFiltered('assurance')">{{ isColumnFiltered('assurance') ? 'filter_alt' : 'filter_alt_off' }}
                </mat-icon>
              </div>
              <div class="th-filter">
                <app-column-filter-renderer type="text" labelKey="ATTEST_LIST.COL_ASSURANCE" [value]="columnFilterValue('assurance')"
                                            (valueChange)="onColumnFilterValue('assurance', $event)"
                                            (clear)="clearColumnFilter('assurance')"/>
              </div>
            </div>
          </th>
          <td mat-cell *matCellDef="let r">{{ r.NUMERO_ASSURANCE || r.numero_assurance }}</td>
        </ng-container>
        <ng-container matColumnDef="debut">
          <th mat-header-cell *matHeaderCellDef>
            <div class="th-wrap" [class.open]="isFilterOpen('debut')">
              <div class="th-top"><span>{{ 'ATTEST_LIST.COL_DEBUT' | translate }}</span>
                <mat-icon class="filter-ind"
                          (click)="toggleFilterPanel('debut', $event)"
                          [class.active]="isColumnFiltered('debut')">{{ isColumnFiltered('debut') ? 'filter_alt' : 'filter_alt_off' }}
                </mat-icon>
              </div>
              <div class="th-filter">
                <app-column-filter-renderer type="date" labelKey="ATTEST_LIST.COL_DEBUT" [value]="columnFilterValue('debut')"
                                            (valueChange)="onColumnFilterValue('debut', $event)"
                                            (clear)="clearColumnFilter('debut')"/>
              </div>
            </div>
          </th>
          <td mat-cell *matCellDef="let r">{{ r.DATE_DEBUT || r.date_debut }}</td>
        </ng-container>
        <ng-container matColumnDef="fin">
          <th mat-header-cell *matHeaderCellDef>
            <div class="th-wrap" [class.open]="isFilterOpen('fin')">
              <div class="th-top"><span>{{ 'ATTEST_LIST.COL_FIN' | translate }}</span>
                <mat-icon class="filter-ind"
                          (click)="toggleFilterPanel('fin', $event)"
                          [class.active]="isColumnFiltered('fin')">{{ isColumnFiltered('fin') ? 'filter_alt' : 'filter_alt_off' }}
                </mat-icon>
              </div>
              <div class="th-filter">
                <app-column-filter-renderer type="date" labelKey="ATTEST_LIST.COL_FIN" [value]="columnFilterValue('fin')"
                                            (valueChange)="onColumnFilterValue('fin', $event)"
                                            (clear)="clearColumnFilter('fin')"/>
              </div>
            </div>
          </th>
          <td mat-cell *matCellDef="let r">{{ r.DATE_FIN || r.date_fin }}</td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef>{{ 'ATTEST_LIST.COL_ACTIONS' | translate }}</th>
          <td mat-cell *matCellDef="let r">
            <button mat-icon-button color="primary" (click)="printRow(r)"
                    [matTooltip]="'PATIENT_LIST.BTN_PRINT' | translate">
              <mat-icon>print</mat-icon>
            </button>
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="displayedColumns()"></tr>
        <tr mat-row *matRowDef="let row; columns: displayedColumns()"
            [attr.data-row-key]="row.PATIENT_ID || row.patient_id || row.CODE_PATIENT || row.code_patient"></tr>
        <tr class="mat-mdc-row" *matNoDataRow>
          <td class="mat-mdc-cell no-data-cell" [attr.colspan]="displayedColumns().length">
            Aucune attestation trouvee
          </td>
        </tr>
      </table>
      </div>

      <mat-paginator [length]="total()" [pageIndex]="pageIndex()" [pageSize]="pageSize()"
                     [pageSizeOptions]="[5,10,20,50]" (page)="onPageChange($event)"></mat-paginator>
    </mat-card>
  `,
  styles: [`
    .list-card {
      padding: 10px;
      border: 1px solid var(--app-border);
      background: var(--app-surface);
      box-shadow: var(--app-shadow);
    }

    .header {
      display: flex;
      align-items: center;
      margin-bottom: 10px;
      gap: 8px;
      flex-wrap: wrap;
    }

    .header h2 {
      margin: 0;
      color: var(--app-text);
      font-size: 1.2rem;
    }

    .spacer {
      flex: 1;
    }

    .table-wrap {
      overflow: auto;
      border: 1px solid var(--app-border);
      border-radius: 12px;
      background: var(--app-surface-solid);
    }

    .w100 {
      width: 100%;
    }

    .w100 .mat-mdc-header-cell {
      color: var(--app-primary);
      font-weight: 700;
      overflow: visible !important;
      position: relative;
      z-index: 5;
    }

    .w100 .mat-mdc-header-cell:has(.filter-ind:hover),
    .w100 .mat-mdc-header-cell:has(.th-filter:hover),
    .w100 .mat-mdc-header-cell:has(.filter-ind.active),
    .w100 .mat-mdc-header-cell:focus-within {
      z-index: 2000;
    }

    .w100,
    .w100 .mat-mdc-header-row,
    .w100 .mat-mdc-row,
    .w100 .mat-mdc-cell,
    .w100 .mat-mdc-header-cell {
      overflow: visible;
    }

    .w100 .mat-mdc-row:hover {
      background: var(--app-row-hover);
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
      padding: 8px;
      position: absolute;
      top: calc(100% + 4px);
      left: 0;
      min-width: 240px;
      width: max-content;
      max-width: 360px;
      z-index: 2100;
      border-radius: 16px;
      box-shadow: var(--app-shadow-soft);
      background: var(--app-filter-panel-bg);
      border: 1px solid var(--app-border-strong);
      backdrop-filter: blur(18px);
    }

    .th-wrap.open .th-filter {
      display: flex;
    }

    @media (max-width: 760px) {
      .th-filter {
        position: fixed;
        top: var(--filter-row-bottom, 200px);
        bottom: auto;
        left: 50%;
        right: auto;
        transform: translateX(-50%);
        width: calc(100vw - 32px);
        max-width: 420px;
        min-width: 260px;
        border-radius: 18px;
        z-index: 2300;
        margin: 0;
        padding: 14px 16px;
        box-sizing: border-box;
        box-shadow: 0 8px 32px rgba(0, 0, 0, 0.18),
        0 2px 8px rgba(0, 0, 0, 0.10);
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

    .th-filter :where(app-column-filter-renderer) { width: 100%; }

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

      .header > button {
        flex: 1 1 100%;
      }

      .w100 .mat-mdc-cell button.mat-mdc-icon-button {
        width: 40px;
        height: 40px;
        padding: 8px;
      }
    }
  `]
})
export class AttestationListComponent implements OnInit {
  private readonly api = inject(BackendApiService);
  private readonly store = inject(AppShellStore);
  private readonly attestationListStore = inject(AttestationListStore);
  private readonly snack = inject(MatSnackBar);
  readonly hasActiveFilters = this.attestationListStore.hasActiveFilters;

  readonly rows = this.attestationListStore.rows;
  readonly total = this.attestationListStore.total;
  readonly pageIndex = this.attestationListStore.pageIndex;
  readonly pageSize = this.attestationListStore.pageSize;

  readonly allColumnsConfig = [
    {key: 'code', labelKey: 'ATTEST_LIST.COL_CODE'},
    {key: 'nom', labelKey: 'ATTEST_LIST.COL_PATIENT'},
    {key: 'assurance', labelKey: 'ATTEST_LIST.COL_ASSURANCE'},
    {key: 'debut', labelKey: 'ATTEST_LIST.COL_DEBUT'},
    {key: 'fin', labelKey: 'ATTEST_LIST.COL_FIN'},
    {key: 'actions', labelKey: 'ATTEST_LIST.COL_ACTIONS'}
  ] as const;
  readonly visibleColumns = this.attestationListStore.visibleColumns;
  private readonly isCompactViewport = signal(typeof window !== 'undefined' ? window.innerWidth <= 900 : false);
  private readonly mobilePriorityColumns = new Set<string>(['code', 'nom', 'debut', 'fin', 'actions']);
  readonly displayedColumns = computed(() => {
    const visible = this.allColumnsConfig.filter(c => this.visibleColumns()[c.key]).map(c => c.key);
    if (!this.isCompactViewport()) return visible;

    const prioritized = visible.filter((key) => this.mobilePriorityColumns.has(key));
    if (visible.includes('actions') && !prioritized.includes('actions')) prioritized.push('actions');
    return prioritized.length > 0 ? prioritized : visible.slice(0, 4);
  });
  readonly columnFilters = this.attestationListStore.columnFilters;

  ngOnInit(): void {
    this.fetchPage(0, this.pageSize());
  }

  toggleColumn(column: string, checked: boolean): void {
    this.attestationListStore.setVisibleColumn(column, checked);
  }

  isColumnVisible(column: string): boolean {
    return this.visibleColumns()[column] ?? false;
  }

  onColumnFilterValue(column: string, value: string): void {
    this.attestationListStore.setFilter(column, value);
    this.fetchPage(0, this.pageSize());
  }

  columnFilterValue(column: string): string {
    return this.columnFilters()[column] ?? '';
  }

  isColumnFiltered(column: string): boolean {
    return !!(this.columnFilters()[column] ?? '').trim();
  }

  clearColumnFilter(column: string): void {
    this.attestationListStore.clearFilter(column);
    this.fetchPage(0, this.pageSize());
  }

  toggleFilterPanel(column: string, event: MouseEvent): void {
    event.stopPropagation();
    if (typeof window !== 'undefined' && window.innerWidth <= 760) {
      const wrap = (event.target as HTMLElement).closest('.th-wrap');
      if (wrap) {
        const rect = wrap.getBoundingClientRect();
        document.documentElement.style.setProperty('--filter-row-bottom', `${Math.round(rect.bottom + 6)}px`);
      }
    }
    this.attestationListStore.toggleFilterPanel(column);
  }

  isFilterOpen(column: string): boolean {
    return this.attestationListStore.openFilterColumn() === column;
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    const target = event.target as HTMLElement | null;
    if (!target) {
      this.attestationListStore.closeFilterPanel();
      return;
    }
    if (target.closest('.th-wrap')) {
      return;
    }
    this.attestationListStore.closeFilterPanel();
  }

  @HostListener('window:resize')
  onWindowResize(): void {
    this.isCompactViewport.set(window.innerWidth <= 900);
  }

  clearAllColumnFilters(): void {
    this.attestationListStore.closeFilterPanel();
    this.attestationListStore.clearAllFilters();
    this.fetchPage(0, this.pageSize());
  }

  onPageChange(event: PageEvent): void {
    this.attestationListStore.setPagination(event.pageIndex, event.pageSize);
    this.fetchPage(event.pageIndex, event.pageSize);
  }

  private fetchPage(page: number, size: number): void {
    const cid = this.store.currentCenterId();
    if (!cid) {
      this.attestationListStore.setPageData([], 0, 0);
      return;
    }

    this.attestationListStore.setLoading(true);
    this.api.listAttestationsByCenter(cid, {page, size, filters: this.columnFilters()}).subscribe({
      next: (res) => {
        this.attestationListStore.setPageData(res.items ?? [], res.total ?? 0, res.page ?? page);
        this.attestationListStore.setLoading(false);
      },
      error: () => {
        this.attestationListStore.setPageData([], 0, page);
        this.attestationListStore.setLoading(false);
      }
    });
  }

  printList(): void {
    const cid = this.store.currentCenterId();
    if (!cid) return;
    this.api.printDocument(cid, 'LISTE_ATTESTATIONS', {}).subscribe({
      next: (blob) => window.open(URL.createObjectURL(blob), '_blank'),
      error: (e) => this.snack.open('Erreur impression: ' + (e?.error?.text || e.message), 'OK', { duration: 5000 })
    });
  }

  exportExcel(): void {
    const cid = this.store.currentCenterId();
    if (!cid) return;
    this.api.printDocument(cid, 'LISTE_ATTESTATIONS', {}, 'EXCEL').subscribe({
      next: (blob) => {
        const a = document.createElement('a');
        a.href = URL.createObjectURL(blob);
        a.download = 'liste-attestations.xls';
        a.click();
      },
      error: (e) => this.snack.open('Erreur export: ' + (e?.error?.text || e.message), 'OK', { duration: 5000 })
    });
  }

  printRow(r: any): void {
    const cid = this.store.currentCenterId();
    const pid = (r.PATIENT_ID || r.patient_id || '').toString();
    if (!cid || !pid) return;
    this.api.printDocument(cid, 'ATTESTATION', { patientId: pid }).subscribe({
      next: (blob) => window.open(URL.createObjectURL(blob), '_blank'),
      error: (e) => this.snack.open('Erreur impression: ' + (e?.error?.text || e.message), 'OK', { duration: 5000 })
    });
  }
}
