import {ChangeDetectionStrategy, Component, computed, effect, inject} from '@angular/core';
import {DecimalPipe, PercentPipe} from '@angular/common';
import {MatCardModule} from '@angular/material/card';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatSelectModule} from '@angular/material/select';
import {MatTableModule} from '@angular/material/table';
import {MatPaginatorModule, PageEvent} from '@angular/material/paginator';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatTooltipModule} from '@angular/material/tooltip';
import {MatBadgeModule} from '@angular/material/badge';
import {MatDialog} from '@angular/material/dialog';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {BaseChartDirective} from 'ng2-charts';
import {Chart, ChartData, ChartOptions, registerables} from 'chart.js';
import {ReglementPreviewRow, ReglementStore} from './state/reglement.store';
import {AppShellStore} from '../../core/state/app-shell.store';
import {AuthStore} from '../../core/state/auth.store';
import {ReglementInvoiceRow} from '../../core/api/backend-api.service';
import {
  PaymentHistoryDialogComponent,
  PaymentHistoryDialogData
} from './payment-history-dialog/payment-history-dialog.component';

Chart.register(...registerables);

@Component({
  selector: 'app-reglement-workspace',
  standalone: true,
  imports: [
    DecimalPipe,
    PercentPipe,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatTableModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatTooltipModule,
    MatBadgeModule,
    TranslateModule,
    BaseChartDirective,
  ],
  templateUrl: './reglement-workspace.component.html',
  styleUrl: './reglement-workspace.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReglementWorkspaceComponent {
  protected readonly store = inject(ReglementStore);
  protected readonly displayedColumns = [
    'numeroFacture',
    'numeroAssurance',
    'patientNom',
    'patientPrenom',
    'caisse',
    'agence',
    'centrePayeur',
    'montantFacture',
    'montantRegle',
    'solde',
    'etat',
    'codeReglement',
    'actions',
  ];
  protected readonly years = Array.from({length: 6}, (_, index) => new Date().getFullYear() - index);
  protected readonly months = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12];
  private readonly appShell = inject(AppShellStore);
  protected readonly centerId = computed(() => this.appShell.currentCenterId());
  private readonly auth = inject(AuthStore);
  protected readonly userId = computed(() => this.auth.username() ?? 'system');
  private readonly translate = inject(TranslateService);
  protected readonly statusChartData = computed<ChartData<'doughnut'>>(() => {
    const dashboard = this.store.dashboard();
    const buckets = dashboard?.statusBreakdown ?? [];
    return {
      labels: buckets.map((bucket) => this.translate.instant(`REGLEMENT_MODULE.DASHBOARD.BUCKETS.${bucket.code}`)),
      datasets: [
        {
          data: buckets.map((bucket) => Number(bucket.count ?? 0)),
          backgroundColor: [
            '#c62828',
            '#ef6c00',
            '#1b5e20',
            '#ff9800',
          ],
        },
      ],
    };
  });
  protected readonly amountChartData = computed<ChartData<'bar'>>(() => {
    const cards = this.store.dashboardCards();
    return {
      labels: [
        this.translate.instant('REGLEMENT_MODULE.DASHBOARD.AMOUNTS.TOTAL_FACTURE'),
        this.translate.instant('REGLEMENT_MODULE.DASHBOARD.AMOUNTS.TOTAL_REGLE'),
        this.translate.instant('REGLEMENT_MODULE.DASHBOARD.AMOUNTS.TOTAL_RESTE'),
        this.translate.instant('REGLEMENT_MODULE.DASHBOARD.AMOUNTS.TOTAL_TROP_PERCU'),
      ],
      datasets: [
        {
          label: this.translate.instant('REGLEMENT_MODULE.DASHBOARD.AMOUNTS.TITLE'),
          data: [cards.totalFacture, cards.totalRegle, cards.totalReste, cards.totalTropPercu],
          backgroundColor: ['#006a6a', '#1b5e20', '#c62828', '#ef6c00'],
          borderRadius: 12,
        },
      ],
    };
  });
  private readonly dialog = inject(MatDialog);

  constructor() {
    // Vider les champs de saisie après chaque sauvegarde réussie
    effect(() => {
      const key = this.store.saveCycleKey();
      if (key > 0) {
        this.clearPaymentInputs();
      }
    });
  }

  protected onYearChange(value: string): void {
    this.store.setYear(Number(value));
  }

  protected onMonthChange(value: string): void {
    this.store.setMonth(value ? Number(value) : null);
  }

  protected onCaisseChange(value: string): void {
    this.store.setCaisseId(value || null);
  }

  protected onAgenceChange(value: string): void {
    this.store.setAgenceId(value || null);
  }

  protected onCentrePayeurChange(value: string): void {
    this.store.setCentrePayeurId(value || null);
  }

  protected onPageChange(event: PageEvent): void {
    this.store.setPagination(event.pageIndex, event.pageSize);
  }

  protected onResetFilters(): void {
    this.store.clearFilters();
  }

  protected onRefresh(): void {
    const centerId = this.centerId();
    const userId = this.userId();
    if (!centerId) {
      return;
    }
    this.store.loadPage({centerId, userId, page: this.store.pageIndex(), size: this.store.pageSize()});
    this.store.loadDashboard({centerId});
  }

  protected onPaymentInput(factureId: string, value: string): void {
    this.store.setPaymentDraft(factureId, value);
  }

  protected onPaymentEnter(event: Event): void {
    event.preventDefault();
    const currentInput = event.target as HTMLInputElement;
    const allInputs = Array.from(
      document.querySelectorAll<HTMLInputElement>('.data-table .payment-field input[type="number"]')
    );
    const currentIndex = allInputs.indexOf(currentInput);
    if (currentIndex >= 0 && currentIndex < allInputs.length - 1) {
      const next = allInputs[currentIndex + 1];
      next.focus();
      next.select();
    }
  }

  protected onBatchSave(): void {
    const centerId = this.centerId();
    if (!centerId) return;
    this.store.batchSavePayments({centerId, userId: this.userId()});
  }

  protected trackByFactureId(_index: number, row: ReglementPreviewRow): string {
    return row.factureId;
  }

  protected hasMultiplePayments(row: ReglementPreviewRow): boolean {
    return (row.paymentCount ?? 0) > 1;
  }

  protected isPaymentLocked(row: ReglementPreviewRow): boolean {
    return row.soldeType === 'REGLE' && !row.hasDraft;
  }

  protected openHistory(row: ReglementInvoiceRow): void {
    const centerId = this.centerId();
    if (!centerId) return;
    this.dialog.open(PaymentHistoryDialogComponent, {
      data: {row, centerId} as PaymentHistoryDialogData,
      width: '700px',
      maxWidth: '98vw',
      panelClass: 'app-dialog',
    });
  }

  protected onExport(format: 'excel' | 'csv'): void {
    const centerId = this.centerId();
    if (!centerId) return;
    this.store.exportReglements({centerId, format});
  }

  protected isSavingRow(factureId: string): boolean {
    return this.store.savingFactureIds().includes(factureId);
  }

  protected statusLabel(row: ReglementPreviewRow): string {
    return this.translate.instant(`REGLEMENT_MODULE.STATUS.${row.etat}`);
  }

  protected soldeLabel(row: ReglementPreviewRow): string {
    if (row.soldeType === 'TROP_PERCU') {
      return this.translate.instant('REGLEMENT_MODULE.TABLE.SOLDE_OVERPAID', {amount: row.tropPercu});
    }
    if (row.soldeType === 'REGLE') {
      return this.translate.instant('REGLEMENT_MODULE.TABLE.SOLDE_SETTLED');
    }
    return this.translate.instant('REGLEMENT_MODULE.TABLE.SOLDE_REMAINING', {amount: row.reste});
  }

  protected soldeClass(row: ReglementPreviewRow): string {
    if (row.soldeType === 'TROP_PERCU') {
      return 'solde-overpaid';
    }
    if (row.soldeType === 'REGLE') {
      return 'solde-settled';
    }
    return 'solde-pending';
  }

  protected multiplePaymentsTooltip(row: ReglementPreviewRow): string {
    return this.translate.instant('REGLEMENT_MODULE.TABLE.MULTIPLE_BADGE_TOOLTIP', {
      count: row.paymentCount ?? 0,
    });
  }

  private clearPaymentInputs(): void {
    document.querySelectorAll<HTMLInputElement>(
      '.data-table .payment-field input[type="number"]'
    ).forEach((input) => {
      input.value = '';
    });
  }

  protected monthLabel(month: number): string {
    return this.translate.instant(`REGLEMENT_MODULE.MONTHS.${month}`);
  }

  protected chartOptions(): ChartOptions<'bar' | 'doughnut'> {
    return {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: {display: true, position: 'bottom'},
      },
    };
  }
}










