import {ChangeDetectionStrategy, Component, computed, inject, TemplateRef, viewChild} from '@angular/core';
import {DecimalPipe} from '@angular/common';
import {MatCardModule} from '@angular/material/card';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatSelectModule} from '@angular/material/select';
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
import {ConfigurableListComponent, SharedListColumn} from '../../shared/configurable-list.component';

@Component({
  selector: 'app-comptabilite-dashboard',
  standalone: true,
  imports: [
    DecimalPipe,
    MatCardModule, MatButtonModule, MatIconModule,
    MatSelectModule, MatPaginatorModule,
    MatProgressBarModule, MatTooltipModule, MatBadgeModule,
    MatChipsModule, TranslateModule, ConfigurableListComponent,
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
  protected readonly expandCellTemplate = viewChild<TemplateRef<any>>('expandCell');
  protected readonly numeroPieceCellTemplate = viewChild<TemplateRef<any>>('numeroPieceCell');
  protected readonly journalCodeCellTemplate = viewChild<TemplateRef<any>>('journalCodeCell');
  protected readonly libelleCellTemplate = viewChild<TemplateRef<any>>('libelleCell');
  protected readonly totalDebitCellTemplate = viewChild<TemplateRef<any>>('totalDebitCell');
  protected readonly statutCellTemplate = viewChild<TemplateRef<any>>('statutCell');
  protected readonly detailRowTemplateRef = viewChild<TemplateRef<any>>('detailRowTemplate');
  protected readonly ecritureColumns = computed<SharedListColumn<EcritureComptableItem>[]>(() => [
    {
      id: 'expand',
      headerKey: '',
      valueAccessor: () => '',
      sortable: false,
      resizable: false,
      widthPx: 72,
      minWidthPx: 64,
      maxWidthPx: 84,
      cellTemplate: this.expandCellTemplate() ?? undefined,
    },
    {
      id: 'numeroPiece',
      headerKey: 'COMPTABILITE.TABLE.NUMERO_PIECE',
      valueAccessor: (row) => row.numeroPiece ?? '',
      sortable: true,
      resizable: true,
      minWidthPx: 170,
      filter: {type: 'text', labelKey: 'COMPTABILITE.TABLE.NUMERO_PIECE'},
      cellTemplate: this.numeroPieceCellTemplate() ?? undefined,
    },
    {
      id: 'journalCode',
      headerKey: 'COMPTABILITE.TABLE.JOURNAL',
      valueAccessor: (row) => row.journalCode ?? '',
      sortable: true,
      resizable: true,
      minWidthPx: 130,
      filter: {type: 'text', labelKey: 'COMPTABILITE.TABLE.JOURNAL'},
      cellTemplate: this.journalCodeCellTemplate() ?? undefined,
    },
    {
      id: 'dateEcriture',
      headerKey: 'COMPTABILITE.TABLE.DATE',
      valueAccessor: (row) => row.dateEcriture ?? '',
      sortable: true,
      resizable: true,
      minWidthPx: 140,
      filter: {type: 'date', labelKey: 'COMPTABILITE.TABLE.DATE'},
    },
    {
      id: 'libelle',
      headerKey: 'COMPTABILITE.TABLE.LIBELLE',
      valueAccessor: (row) => row.libelle ?? '',
      sortable: true,
      resizable: true,
      minWidthPx: 260,
      filter: {type: 'text', labelKey: 'COMPTABILITE.TABLE.LIBELLE'},
      cellTemplate: this.libelleCellTemplate() ?? undefined,
    },
    {
      id: 'totalDebit',
      headerKey: 'COMPTABILITE.TABLE.TOTAL',
      valueAccessor: (row) => Number(row.totalDebit ?? 0),
      sortValueAccessor: (row) => Number(row.totalDebit ?? 0),
      sortable: true,
      resizable: true,
      minWidthPx: 140,
      filter: {type: 'text', labelKey: 'COMPTABILITE.TABLE.TOTAL'},
      cellTemplate: this.totalDebitCellTemplate() ?? undefined,
    },
    {
      id: 'statut',
      headerKey: 'COMPTABILITE.TABLE.STATUT',
      valueAccessor: (row) => row.statut ?? '',
      sortable: true,
      resizable: true,
      minWidthPx: 130,
      filter: {type: 'text', labelKey: 'COMPTABILITE.TABLE.STATUT'},
      cellTemplate: this.statutCellTemplate() ?? undefined,
    },
  ]);
  protected readonly rowClassFn = () => ({
    'ecriture-row': true,
  });

  protected readonly canExpandRow = (row: EcritureComptableItem): boolean =>
    (row.lignes?.length ?? 0) > 0;

  protected readonly rowKey = (row: EcritureComptableItem): string => row.id;

  protected onPageChange(event: PageEvent): void {
    this.store.setPagination(event.pageIndex, event.pageSize);
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
}
