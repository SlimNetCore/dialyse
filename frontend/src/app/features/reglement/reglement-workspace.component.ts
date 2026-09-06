import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
  TemplateRef,
  viewChild
} from '@angular/core';
import {DecimalPipe, PercentPipe} from '@angular/common';
import {MatCardModule} from '@angular/material/card';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatSelectModule} from '@angular/material/select';
import {MatPaginatorModule, PageEvent} from '@angular/material/paginator';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatTooltipModule} from '@angular/material/tooltip';
import {MatBadgeModule} from '@angular/material/badge';
import {MatMenuModule} from '@angular/material/menu';
import {MatCheckboxModule} from '@angular/material/checkbox';
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
import {ConfigurableListComponent, SharedListColumn} from '../../shared/configurable-list.component';

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
    MatPaginatorModule,
    MatProgressBarModule,
    MatTooltipModule,
    MatBadgeModule,
    MatMenuModule,
    MatCheckboxModule,
    TranslateModule,
    BaseChartDirective,
    ConfigurableListComponent,
  ],
  templateUrl: './reglement-workspace.component.html',
  styleUrl: './reglement-workspace.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReglementWorkspaceComponent {
  protected readonly store = inject(ReglementStore);
  protected readonly montantRegleCellTemplate = viewChild<TemplateRef<any>>('montantRegleCell');
  protected readonly montantFactureCellTemplate = viewChild<TemplateRef<any>>('montantFactureCell');
  protected readonly soldeCellTemplate = viewChild<TemplateRef<any>>('soldeCell');
  protected readonly etatCellTemplate = viewChild<TemplateRef<any>>('etatCell');
  protected readonly codeReglementCellTemplate = viewChild<TemplateRef<any>>('codeReglementCell');
  protected readonly actionsCellTemplate = viewChild<TemplateRef<any>>('actionsCell');
  protected readonly reglementColumns = computed<SharedListColumn<ReglementPreviewRow>[]>(() => [
    {
      id: 'numeroFacture',
      headerKey: 'REGLEMENT_MODULE.TABLE.NUMERO_FACTURE',
      valueAccessor: (row) => row.numeroFacture ?? '',
      sortable: true,
      resizable: true,
      minWidthPx: 150,
      filter: {type: 'text', labelKey: 'REGLEMENT_MODULE.TABLE.NUMERO_FACTURE'},
    },
    {
      id: 'numeroAssurance',
      headerKey: 'REGLEMENT_MODULE.TABLE.NUMERO_ASSURANCE',
      valueAccessor: (row) => row.numeroAssurance ?? '',
      sortable: true,
      resizable: true,
      minWidthPx: 150,
      filter: {type: 'text', labelKey: 'REGLEMENT_MODULE.TABLE.NUMERO_ASSURANCE'},
    },
    {
      id: 'patientNom',
      headerKey: 'REGLEMENT_MODULE.TABLE.PATIENT_NOM',
      valueAccessor: (row) => row.patientNom ?? '',
      sortable: true,
      resizable: true,
      minWidthPx: 150,
      filter: {type: 'text', labelKey: 'REGLEMENT_MODULE.TABLE.PATIENT_NOM'},
    },
    {
      id: 'patientPrenom',
      headerKey: 'REGLEMENT_MODULE.TABLE.PATIENT_PRENOM',
      valueAccessor: (row) => row.patientPrenom ?? '',
      sortable: true,
      resizable: true,
      minWidthPx: 150,
      filter: {type: 'text', labelKey: 'REGLEMENT_MODULE.TABLE.PATIENT_PRENOM'},
    },
    {
      id: 'caisse',
      headerKey: 'REGLEMENT_MODULE.TABLE.CAISSE',
      valueAccessor: (row) => row.caisse ?? '',
      sortable: true,
      resizable: true,
      minWidthPx: 140,
      filter: {type: 'text', labelKey: 'REGLEMENT_MODULE.TABLE.CAISSE'},
    },
    {
      id: 'agence',
      headerKey: 'REGLEMENT_MODULE.TABLE.AGENCE',
      valueAccessor: (row) => row.agence ?? '',
      sortable: true,
      resizable: true,
      minWidthPx: 140,
      filter: {type: 'text', labelKey: 'REGLEMENT_MODULE.TABLE.AGENCE'},
    },
    {
      id: 'centrePayeur',
      headerKey: 'REGLEMENT_MODULE.TABLE.CENTRE_PAYEUR',
      valueAccessor: (row) => row.centrePayeur ?? '',
      sortable: true,
      resizable: true,
      minWidthPx: 160,
      filter: {type: 'text', labelKey: 'REGLEMENT_MODULE.TABLE.CENTRE_PAYEUR'},
    },
    {
      id: 'montantFacture',
      headerKey: 'REGLEMENT_MODULE.TABLE.MONTANT_FACTURE',
      valueAccessor: (row) => Number(row.montantFacture ?? 0),
      sortValueAccessor: (row) => Number(row.montantFacture ?? 0),
      sortable: true,
      resizable: true,
      minWidthPx: 150,
      filter: {type: 'text', labelKey: 'REGLEMENT_MODULE.TABLE.MONTANT_FACTURE'},
      cellTemplate: this.montantFactureCellTemplate() ?? undefined,
    },
    {
      id: 'montantRegle',
      headerKey: 'REGLEMENT_MODULE.TABLE.MONTANT_REGLE',
      valueAccessor: (row) => Number(row.montantRegle ?? 0),
      sortValueAccessor: (row) => Number(row.montantRegle ?? 0),
      sortable: true,
      resizable: true,
      minWidthPx: 180,
      filter: {type: 'text', labelKey: 'REGLEMENT_MODULE.TABLE.MONTANT_REGLE'},
      cellTemplate: this.montantRegleCellTemplate() ?? undefined,
    },
    {
      id: 'solde',
      headerKey: 'REGLEMENT_MODULE.TABLE.SOLDE',
      valueAccessor: (row) => this.soldeLabel(row),
      sortable: false,
      resizable: true,
      minWidthPx: 190,
      filter: {type: 'text', labelKey: 'REGLEMENT_MODULE.TABLE.SOLDE'},
      cellTemplate: this.soldeCellTemplate() ?? undefined,
    },
    {
      id: 'etat',
      headerKey: 'REGLEMENT_MODULE.TABLE.ETAT',
      valueAccessor: (row) => this.statusLabel(row),
      sortable: true,
      resizable: true,
      minWidthPx: 140,
      filter: {type: 'text', labelKey: 'REGLEMENT_MODULE.TABLE.ETAT'},
      cellTemplate: this.etatCellTemplate() ?? undefined,
    },
    {
      id: 'codeReglement',
      headerKey: 'REGLEMENT_MODULE.TABLE.CODE_REGLEMENT',
      valueAccessor: (row) => row.latestCodeReglement ?? '',
      sortable: true,
      resizable: true,
      minWidthPx: 170,
      filter: {type: 'text', labelKey: 'REGLEMENT_MODULE.TABLE.CODE_REGLEMENT'},
      cellTemplate: this.codeReglementCellTemplate() ?? undefined,
    },
    {
      id: 'actions',
      headerKey: 'REGLEMENT_MODULE.TABLE.ACTIONS',
      valueAccessor: () => '',
      sortable: false,
      resizable: false,
      mobileRowActions: true,
      minWidthPx: 240,
      widthPx: 280,
      maxWidthPx: 360,
      cellTemplate: this.actionsCellTemplate() ?? undefined,
    },
  ]);
  protected readonly visibleColumns = signal<Record<string, boolean>>({
    numeroFacture: true,
    numeroAssurance: true,
    patientNom: true,
    patientPrenom: true,
    caisse: true,
    agence: true,
    centrePayeur: true,
    montantFacture: true,
    montantRegle: true,
    solde: true,
    etat: true,
    codeReglement: true,
    actions: true,
  });
  protected readonly columnMenuItems = computed(() =>
    this.reglementColumns()
      .filter((column) => column.id !== 'actions')
      .map((column) => ({id: column.id, headerKey: column.headerKey})),
  );

  protected readonly rowClassFn = (row: ReglementPreviewRow) => ({
    'row-has-draft': !!row.hasDraft,
  });
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
      document.querySelectorAll<HTMLInputElement>('.reglement-data-list .payment-field input[type="number"]')
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

  protected isColumnVisible(columnId: string): boolean {
    return this.visibleColumns()[columnId] ?? false;
  }

  protected toggleColumn(columnId: string, checked: boolean): void {
    this.visibleColumns.update((current) => ({
      ...current,
      [columnId]: checked,
    }));
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
      '.reglement-data-list .payment-field input[type="number"]'
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










