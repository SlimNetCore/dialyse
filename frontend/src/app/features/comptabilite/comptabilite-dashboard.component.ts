import {ChangeDetectionStrategy, Component, computed, effect, inject} from '@angular/core';
import {DecimalPipe} from '@angular/common';
import {MatCardModule} from '@angular/material/card';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatSelectModule} from '@angular/material/select';
import {MatTableModule, MatTableDataSource} from '@angular/material/table';
import {MatPaginatorModule, PageEvent} from '@angular/material/paginator';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatTooltipModule} from '@angular/material/tooltip';
import {MatBadgeModule} from '@angular/material/badge';
import {MatChipsModule} from '@angular/material/chips';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {ComptabiliteStore} from './state/comptabilite.store';
import {AppShellStore} from '../../core/state/app-shell.store';
import {AuthStore} from '../../core/state/auth.store';
import {JournalCode} from '../../core/api/comptabilite-api.service';
import {EcritureComptableItem} from '../../core/api/comptabilite-api.service';

@Component({
  selector: 'app-comptabilite-dashboard',
  standalone: true,
  imports: [
    DecimalPipe,
    MatCardModule, MatButtonModule, MatIconModule,
    MatSelectModule, MatTableModule, MatPaginatorModule,
    MatProgressBarModule, MatTooltipModule, MatBadgeModule,
    MatChipsModule, TranslateModule,
  ],
  templateUrl: './comptabilite-dashboard.component.html',
  styleUrl: './comptabilite-dashboard.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ComptabiliteDashboardComponent {
  protected readonly store = inject(ComptabiliteStore);
  private readonly appShell = inject(AppShellStore);
  private readonly auth = inject(AuthStore);
  private readonly translate = inject(TranslateService);

  protected readonly centerId = computed(() => this.appShell.currentCenterId());
  protected readonly userId = computed(() => this.auth.username() ?? 'system');

  protected readonly years = Array.from({length: 6}, (_, i) => new Date().getFullYear() - i);
  protected readonly months = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12];
  protected readonly journaux: Array<{ code: JournalCode | null; label: string }> = [
    {code: null, label: 'COMPTABILITE.FILTERS.ALL_JOURNALS'},
    {code: 'VE', label: 'COMPTABILITE.JOURNAL.VE'},
    {code: 'BQ', label: 'COMPTABILITE.JOURNAL.BQ'},
    {code: 'CA', label: 'COMPTABILITE.JOURNAL.CA'},
  ];

  protected readonly displayedColumns = [
    'expand', 'numeroPiece', 'journalCode', 'dateEcriture', 'libelle', 'totalDebit', 'statut'
  ];
  protected readonly detailRowColumns = ['detailRow'];

  protected readonly dataSource = new MatTableDataSource<EcritureComptableItem>([]);

  constructor() {
    effect(() => {
      const rows = this.store.rows();
      this.dataSource.data = rows;
    });
  }

  protected onPageChange(event: PageEvent): void {
    this.store.setPagination(event.pageIndex, event.pageSize);
  }

  protected toggleRowExpansion(row: EcritureComptableItem, event?: Event): void {
    event?.preventDefault();
    event?.stopPropagation();
    this.store.toggleExpandedRow(row.id);
  }

  protected isRowExpanded(ecritureId: string): boolean {
    return this.store.expandedEcritureIds().includes(ecritureId);
  }

  protected readonly isDetailRowExpanded = (_index: number, row: EcritureComptableItem): boolean =>
    this.isRowExpanded(row.id) && (row.lignes?.length ?? 0) > 0;


  protected expandedCount(): number {
    return this.store.expandedEcritureIds().length;
  }

  protected closeAllExpandedRows(): void {
    this.store.collapseAllExpandedRows();
  }

  protected totalCredit(row: EcritureComptableItem): number {
    return (row.lignes ?? []).reduce((sum, ligne) => sum + Number(ligne.montantCredit ?? 0), 0);
  }

  protected detailBalanceClass(row: EcritureComptableItem): string {
    const debit = Number(row.totalDebit ?? 0);
    const credit = this.totalCredit(row);
    return Math.abs(debit - credit) < 0.0001 ? 'detail-balance-ok' : 'detail-balance-warning';
  }

  protected detailBalanceLabel(row: EcritureComptableItem): string {
    return this.detailBalanceClass(row) === 'detail-balance-ok'
      ? 'COMPTABILITE.DETAIL.BALANCE_OK'
      : 'COMPTABILITE.DETAIL.BALANCE_WARNING';
  }

  protected onExport(journalCode: JournalCode): void {
    const centerId = this.centerId();
    if (!centerId) return;
    const year = this.store.year();
    const month = this.store.month();
    const from = month ? `${year}-${String(month).padStart(2, '0')}-01` : `${year}-01-01`;
    const lastDay = month ? new Date(year, month, 0).getDate() : 31;
    const to = month ? `${year}-${String(month).padStart(2, '0')}-${String(lastDay).padStart(2, '0')}` : `${year}-12-31`;
    this.store.exporterJournal({centerId, from, to, journalCode});
  }

  protected statutClass(statut: string): string {
    switch (statut) {
      case 'VALIDEE':
        return 'statut-validee';
      case 'EXPORTEE':
        return 'statut-exportee';
      default:
        return 'statut-brouillon';
    }
  }

  protected monthLabel(month: number): string {
    return this.translate.instant(`REGLEMENT_MODULE.MONTHS.${month}`);
  }

  protected trackByEcritureId(_index: number, row: { id: string }): string {
    return row.id;
  }
}











