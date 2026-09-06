import {ChangeDetectionStrategy, Component, computed, effect, inject, TemplateRef, viewChild} from '@angular/core';
import {DecimalPipe} from '@angular/common';
import {RouterLink} from '@angular/router';
import {MatCardModule} from '@angular/material/card';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatSelectModule} from '@angular/material/select';
import {MatTooltipModule} from '@angular/material/tooltip';
import {MatSnackBar} from '@angular/material/snack-bar';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {BaseChartDirective} from 'ng2-charts';
import {Chart, ChartData, ChartOptions, registerables} from 'chart.js';
import {FacturationStore} from './state/facturation.store';
import {AppShellStore} from '../../core/state/app-shell.store';
import {AuthStore} from '../../core/state/auth.store';
import {BackendApiService, FacturationPreviewInvoice} from '../../core/api/backend-api.service';
import {ConfigurableListComponent, SharedListColumn} from '../../shared/configurable-list.component';

Chart.register(...registerables);

@Component({
  selector: 'app-facturation-workspace',
  standalone: true,
  imports: [
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatTooltipModule,
    BaseChartDirective,
    TranslateModule,
    DecimalPipe,
    ConfigurableListComponent,
  ],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './facturation-workspace.component.html',
  styleUrl: './facturation-workspace.component.css',
})
export class FacturationWorkspaceComponent {
  protected readonly store = inject(FacturationStore);
  private readonly appShell = inject(AppShellStore);
  private readonly auth = inject(AuthStore);
  private readonly translate = inject(TranslateService);
  private readonly api = inject(BackendApiService);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly currentCenterId = computed(() => this.appShell.currentCenterId());
  protected readonly currentUsername = computed(() => this.auth.username() ?? 'system');

  protected readonly invoiceExpandCellTemplate = viewChild<TemplateRef<any>>('invoiceExpandCell');
  protected readonly invoiceNumeroCellTemplate = viewChild<TemplateRef<any>>('invoiceNumeroCell');
  protected readonly invoicePatientCellTemplate = viewChild<TemplateRef<any>>('invoicePatientCell');
  protected readonly invoiceStatusCellTemplate = viewChild<TemplateRef<any>>('invoiceStatusCell');
  protected readonly invoiceSeancesCellTemplate = viewChild<TemplateRef<any>>('invoiceSeancesCell');
  protected readonly invoiceAmountCellTemplate = viewChild<TemplateRef<any>>('invoiceAmountCell');
  protected readonly invoiceDetailTemplateRef = viewChild<TemplateRef<any>>('invoiceDetailRow');

  protected readonly invoiceColumns = computed<SharedListColumn<FacturationPreviewInvoice>[]>(() => [
    {
      id: 'expand',
      headerKey: '',
      valueAccessor: () => '',
      sortable: false,
      resizable: false,
      widthPx: 64,
      minWidthPx: 56,
      maxWidthPx: 76,
      cellTemplate: this.invoiceExpandCellTemplate() ?? undefined,
    },
    {
      id: 'numeroFacture',
      headerKey: 'FACTURATION.PREVIEW.TABLE.NUMERO',
      valueAccessor: (row) => row.numeroFacture ?? '',
      sortable: true,
      resizable: true,
      minWidthPx: 170,
      filter: {type: 'text'},
      cellTemplate: this.invoiceNumeroCellTemplate() ?? undefined,
      copy: true,
    },
    {
      id: 'patient',
      headerKey: 'FACTURATION.PREVIEW.TABLE.PATIENT',
      valueAccessor: (row) => `${row.patientCode ?? ''} ${row.patientFullName ?? ''}`.trim(),
      sortValueAccessor: (row) => row.patientFullName ?? '',
      sortable: true,
      resizable: true,
      minWidthPx: 220,
      filter: {type: 'text'},
      cellTemplate: this.invoicePatientCellTemplate() ?? undefined,
    },
    {
      id: 'patientStatusSnapshot',
      headerKey: 'FACTURATION.PREVIEW.TABLE.PATIENT_STATUS',
      valueAccessor: (row) => row.patientStatusSnapshot ?? '',
      sortable: true,
      resizable: true,
      minWidthPx: 140,
      filter: {type: 'text'},
      cellTemplate: this.invoiceStatusCellTemplate() ?? undefined,
    },
    {
      id: 'seancesCount',
      headerKey: 'FACTURATION.PREVIEW.TABLE.SESSIONS',
      valueAccessor: (row) => row.seances?.length ?? 0,
      sortValueAccessor: (row) => row.seances?.length ?? 0,
      sortable: true,
      resizable: true,
      minWidthPx: 110,
      cellTemplate: this.invoiceSeancesCellTemplate() ?? undefined,
    },
    {
      id: 'totalHt',
      headerKey: 'FACTURATION.PREVIEW.INVOICE_HT',
      valueAccessor: (row) => Number(row.totalHt ?? 0),
      sortValueAccessor: (row) => Number(row.totalHt ?? 0),
      sortable: true,
      resizable: true,
      minWidthPx: 120,
      cellTemplate: this.invoiceAmountCellTemplate() ?? undefined,
    },
    {
      id: 'totalTva',
      headerKey: 'FACTURATION.PREVIEW.INVOICE_TVA',
      valueAccessor: (row) => Number(row.totalTva ?? 0),
      sortValueAccessor: (row) => Number(row.totalTva ?? 0),
      sortable: true,
      resizable: true,
      minWidthPx: 120,
      cellTemplate: this.invoiceAmountCellTemplate() ?? undefined,
    },
    {
      id: 'totalTtc',
      headerKey: 'FACTURATION.PREVIEW.INVOICE_TTC',
      valueAccessor: (row) => Number(row.totalTtc ?? 0),
      sortValueAccessor: (row) => Number(row.totalTtc ?? 0),
      sortable: true,
      resizable: true,
      minWidthPx: 130,
      cellTemplate: this.invoiceAmountCellTemplate() ?? undefined,
    },
  ]);

  protected readonly invoiceRowKey = (row: FacturationPreviewInvoice): string => row.previewKey;

  protected readonly canExpandInvoice = (row: FacturationPreviewInvoice): boolean =>
    (row.seances?.length ?? 0) > 0;

  protected readonly invoiceRowClassFn = () => ({'invoice-row': true});

  protected readonly revenueChartData = computed<ChartData<'line'>>(() => {
    const points = this.store.revenueTrend();
    return {
      labels: points.map((point) => this.monthLabel(point.month)),
      datasets: [
        {
          label: this.translate.instant('FACTURATION.DASHBOARD.CHART.REVENUE'),
          data: points.map((point) => Number(point.revenueHt ?? 0)),
          borderColor: this.themeColor('--app-primary', '#26a69a'),
          backgroundColor: this.themeColor('--app-primary-soft', 'rgba(38, 166, 154, 0.20)'),
          tension: 0.3,
          fill: true,
        },
      ],
    };
  });
  protected readonly byCaisseRows = computed(() => {
    const rows = this.store.dashboard()?.byInsurance ?? [];
    const buckets = [
      {code: 'CNAS', label: 'CNAS', seancesCount: 0, patientsCount: 0},
      {code: 'CASNOS', label: 'CASNOS', seancesCount: 0, patientsCount: 0},
      {code: 'CAMSSP', label: 'CAMSSP', seancesCount: 0, patientsCount: 0},
    ];
    for (const row of rows) {
      const key = `${row.code ?? ''} ${row.label ?? ''}`.toUpperCase();
      const target = buckets.find((bucket) => key.includes(bucket.code));
      if (!target) {
        continue;
      }
      target.seancesCount += Number(row.seancesCount ?? 0);
      target.patientsCount += Number(row.patientsCount ?? 0);
    }
    return buckets;
  });

  constructor() {
    effect(() => {
      const centerId = this.currentCenterId();
      if (!centerId) {
        return;
      }
      this.store.setActiveCenterId(centerId);
      this.store.loadSettings({centerId});
      this.store.loadForfaits({centerId});
      this.store.loadDashboard({centerId, month: this.store.month()});
      this.store.loadRevenueTrend({centerId, endingMonth: this.store.month(), months: 6});
    });

    effect(() => {
      const centerId = this.currentCenterId();
      const success = this.store.successMessage();
      if (!centerId || success !== 'FACTURATION.SUCCESS.VALIDATED') {
        return;
      }
      this.store.loadDashboard({centerId, month: this.store.month()});
      this.store.loadRevenueTrend({centerId, endingMonth: this.store.month(), months: 6});
    });
  }

  protected onPeriodMode(mode: 'month' | 'custom'): void {
    this.store.setPeriodMode(mode);
  }

  protected onMonthChange(value: string): void {
    this.store.setMonth(value);
    const centerId = this.currentCenterId();
    if (centerId && /^\d{4}-\d{2}$/.test(value)) {
      this.store.loadDashboard({centerId, month: value});
      this.store.loadRevenueTrend({centerId, endingMonth: value, months: 6});
    }
  }

  protected onStartDateChange(value: string): void {
    this.store.setStartDate(value);
  }

  protected onEndDateChange(value: string): void {
    this.store.setEndDate(value);
  }

  protected onCalculatePreview(): void {
    const centerId = this.currentCenterId();
    if (!centerId) {
      return;
    }
    this.store.calculatePreview({centerId});
  }

  protected onValidateFacturation(): void {
    const centerId = this.currentCenterId();
    if (!centerId) {
      return;
    }
    this.store.validateFacturation({
      centerId,
      userId: this.currentUsername(),
    });
  }

  protected onRemoveSeanceFromPreview(seanceId: string): void {
    const centerId = this.currentCenterId();
    if (!centerId) {
      return;
    }
    this.store.removeSeanceFromPreview({
      centerId,
      userId: this.currentUsername(),
      seanceId,
    });
  }

  protected onPreviewSeanceForfaitChange(seanceId: string, forfaitId: string | null): void {
    const centerId = this.currentCenterId();
    if (!centerId || !forfaitId) {
      return;
    }
    this.store.updateSeanceForfaitInPreview({
      centerId,
      userId: this.currentUsername(),
      seanceId,
      forfaitId,
    });
  }

  protected seanceStatusClass(status: string | null | undefined): string {
    const normalized = (status ?? '').toUpperCase();
    if (normalized.includes('TERMINE') || normalized.includes('FACTUREE')) {
      return 'status-done';
    }
    if (normalized.includes('EN_COURS')) {
      return 'status-in-progress';
    }
    return 'status-pending';
  }

  protected patientStatusClass(status: string | null | undefined): string {
    const s = (status ?? '').toUpperCase();
    if (s === 'PERMANENT') return 'pat-status-permanent';
    if (s.includes('TRANSFER')) return 'pat-status-transferred';
    if (s === 'VACANCIER') return 'pat-status-vacancier';
    if (s === 'DÉCÉDÉ' || s === 'DECEDE') return 'pat-status-deceased';
    if (s === 'GREFFÉ' || s === 'GREFFE') return 'pat-status-greffe';
    return 'pat-status-default';
  }

  protected forfaitOptions(): Array<{ id: string; label: string; price: number | null }> {
    return this.store.forfaits().map((item) => ({
      id: item.id,
      label: this.resolveForfaitLabel(item),
      price: this.resolveForfaitPrice(item),
    }));
  }

  protected refreshDashboard(): void {
    const centerId = this.currentCenterId();
    if (!centerId) {
      return;
    }
    this.store.loadDashboard({centerId, month: this.store.month()});
    this.store.loadRevenueTrend({centerId, endingMonth: this.store.month(), months: 6});
  }

  protected onPrintMonthlySummary(): void {
    const centerId = this.currentCenterId();
    if (!centerId) {
      return;
    }
    const period = this.resolveSummaryPeriod();
    this.api.printFacturationSynthese({
      centerId,
      periodStart: period.start,
      periodEnd: period.end,
      format: 'PDF',
    }).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        window.open(url, '_blank', 'noopener');
        setTimeout(() => URL.revokeObjectURL(url), 10_000);
      },
      error: () => {
        this.snackBar.open(
          this.translate.instant('COMMON.PRINT_ERROR'),
          this.translate.instant('COMMON.OK'),
          {duration: 4000}
        );
      },
    });
  }

  protected revenueChartOptions(): ChartOptions<'line'> {
    return {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: {display: true, position: 'bottom'},
      },
      scales: {
        y: {
          title: {
            display: true,
            text: this.translate.instant('FACTURATION.DASHBOARD.CHART.REVENUE'),
          },
          ticks: {
            callback: (value) => `${value}`,
          },
        },
      },
    };
  }

  private monthLabel(value: string): string {
    if (!/^\d{4}-\d{2}$/.test(value)) {
      return value;
    }
    const [year, month] = value.split('-').map((item) => Number(item));
    const d = new Date(Date.UTC(year, month - 1, 1));
    return d.toLocaleDateString('fr-FR', {month: 'short', year: '2-digit'});
  }

  private themeColor(variable: string, fallback: string): string {
    if (typeof window === 'undefined') {
      return fallback;
    }
    const raw = getComputedStyle(document.documentElement).getPropertyValue(variable).trim();
    return raw || fallback;
  }

  private resolveForfaitLabel(item: {
    code?: string | null;
    nom?: string | null;
    label?: string | null;
    libelle?: string | null
  }): string {
    const code = item.code ?? '';
    const label = item.label ?? item.nom ?? '';
    if (code && label) {
      return `${code} - ${label}`;
    }
    return label || code || 'Forfait';
  }

  private resolveForfaitPrice(item: { libelle?: string | null; prix?: number | null }): number | null {
    if (typeof item.prix === 'number') {
      return item.prix;
    }
    if (item.libelle == null) {
      return null;
    }
    const parsed = Number(item.libelle);
    return Number.isFinite(parsed) ? parsed : null;
  }

  private resolveSummaryPeriod(): { start: string; end: string } {
    if (this.store.periodMode() === 'custom') {
      return {
        start: this.store.startDate(),
        end: this.store.endDate(),
      };
    }
    const month = this.store.month();
    const [yearRaw, monthRaw] = month.split('-');
    const year = Number(yearRaw);
    const monthValue = Number(monthRaw);
    if (!year || !monthValue) {
      const now = new Date();
      const fallbackMonth = `${now.getUTCMonth() + 1}`.padStart(2, '0');
      const start = `${now.getUTCFullYear()}-${fallbackMonth}-01`;
      const end = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() + 1, 0)).toISOString().slice(0, 10);
      return {start, end};
    }
    const start = `${year}-${`${monthValue}`.padStart(2, '0')}-01`;
    const end = new Date(Date.UTC(year, monthValue, 0)).toISOString().slice(0, 10);
    return {start, end};
  }
}
