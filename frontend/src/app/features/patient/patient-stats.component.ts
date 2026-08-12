import {ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked} from '@angular/core';
import {CommonModule} from '@angular/common';
import {ActivatedRoute, Router} from '@angular/router';
import {MatCardModule} from '@angular/material/card';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatSelectModule} from '@angular/material/select';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {MatSnackBar, MatSnackBarModule} from '@angular/material/snack-bar';
import {forkJoin} from 'rxjs';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {BaseChartDirective} from 'ng2-charts';
import {Chart, ChartData, ChartOptions, registerables} from 'chart.js';
import {BackendApiService, PatientMedicalStats, PatientParamedicalStats,} from '../../core/api/backend-api.service';
import {AppShellStore} from '../../core/state/app-shell.store';
import {AuthStore} from '../../core/state/auth.store';

Chart.register(...registerables);

type BarPoint = {
  label: string;
  value: number;
};

@Component({
  selector: 'app-patient-stats',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    TranslateModule,
    BaseChartDirective,
  ],
  templateUrl: './patient-stats.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './patient-stats.component.css',
})
export class PatientStatsComponent {
  readonly period = signal<'1m' | '3m' | '6m' | '1y' | 'custom'>('3m');
  readonly fromDate = signal<string>('');
  readonly toDate = signal<string>('');
  readonly loading = signal<boolean>(false);
  readonly exporting = signal<boolean>(false);
  readonly paramedical = signal<PatientParamedicalStats>({
    seanceCount: 0,
    avgPoidsAvantKg: 0,
    avgPoidsApresKg: 0,
    avgUfReelleMl: 0,
    poidsEvolution: [],
    taEvolution: [],
  });
  readonly medical = signal<PatientMedicalStats>({
    avgHbGDl: 0,
    avgKtV: 0,
    avgFerritineNgMl: 0,
    hbTrend: [],
    epoTrend: [],
  });

  readonly lineChartOptions: ChartOptions<'line'> = {
    responsive: true,
    maintainAspectRatio: false,
    interaction: {mode: 'nearest', intersect: false},
    plugins: {
      legend: {
        display: true,
        position: 'bottom',
        labels: {
          usePointStyle: true,
          pointStyle: 'circle',
          boxWidth: 8,
          color: '#64748b',
          font: {size: 12, weight: 600},
        },
      },
      tooltip: {
        enabled: true,
        backgroundColor: '#0f172a',
        titleColor: '#f8fafc',
        bodyColor: '#e2e8f0',
        displayColors: true,
        padding: 10,
      },
    },
    scales: {
      x: {
        ticks: {color: '#64748b', maxRotation: 0, autoSkip: true},
        grid: {color: 'rgba(100,116,139,0.15)'},
      },
      y: {
        ticks: {color: '#64748b'},
        grid: {color: 'rgba(100,116,139,0.15)'},
      },
    },
  };

  readonly poidsChart = computed<ChartData<'line'>>(() => {
    const rows = this.paramedical().poidsEvolution;
    const labels = rows.map((row, i) => this.formatDisplayDate(this.readLabel(row, ['DATE_SEANCE', 'date_seance'])) || `S${i + 1}`);
    return {
      labels,
      datasets: [
        {
          label: this.translate.instant('PATIENT_STATS.CHART_POIDS_BEFORE'),
          data: this.seriesValues(rows, ['poids_avant_kg', 'POIDS_AVANT_KG']),
          borderColor: '#2563eb',
          backgroundColor: 'rgba(37, 99, 235, 0.15)',
          borderWidth: 2,
          tension: 0.35,
          pointRadius: 3,
          pointHoverRadius: 6,
          pointHitRadius: 14,
          fill: false,
        },
        {
          label: this.translate.instant('PATIENT_STATS.CHART_POIDS_AFTER'),
          data: this.seriesValues(rows, ['poids_apres_kg', 'POIDS_APRES_KG']),
          borderColor: '#0f766e',
          backgroundColor: 'rgba(15, 118, 110, 0.15)',
          borderWidth: 2,
          tension: 0.35,
          pointRadius: 3,
          pointHoverRadius: 6,
          pointHitRadius: 14,
          fill: false,
        },
      ],
    };
  });

  readonly taChart = computed<ChartData<'line'>>(() => {
    const rows = this.paramedical().taEvolution;
    const labels = rows.map((row, i) => this.formatDisplayDate(this.readLabel(row, ['DATE_SEANCE', 'date_seance'])) || `S${i + 1}`);
    return {
      labels,
      datasets: [
        {
          label: this.translate.instant('PATIENT_STATS.CHART_TA_SYS_BEFORE'),
          data: this.seriesValues(rows, ['ta_systolique_avant', 'TA_SYSTOLIQUE_AVANT']),
          borderColor: '#1d4ed8',
          backgroundColor: 'rgba(29, 78, 216, 0.15)',
          borderWidth: 2,
          tension: 0.28,
          pointRadius: 2.8,
          pointHoverRadius: 5.8,
          pointHitRadius: 14,
          fill: false,
        },
        {
          label: this.translate.instant('PATIENT_STATS.CHART_TA_DIA_BEFORE'),
          data: this.seriesValues(rows, ['ta_diastolique_avant', 'TA_DIASTOLIQUE_AVANT']),
          borderColor: '#0f766e',
          backgroundColor: 'rgba(15, 118, 110, 0.15)',
          borderWidth: 2,
          tension: 0.28,
          pointRadius: 2.8,
          pointHoverRadius: 5.8,
          pointHitRadius: 14,
          fill: false,
        },
        {
          label: this.translate.instant('PATIENT_STATS.CHART_TA_SYS_AFTER'),
          data: this.seriesValues(rows, ['ta_systolique_apres', 'TA_SYSTOLIQUE_APRES']),
          borderColor: '#7c3aed',
          backgroundColor: 'rgba(124, 58, 237, 0.15)',
          borderWidth: 2,
          tension: 0.28,
          pointRadius: 2.8,
          pointHoverRadius: 5.8,
          pointHitRadius: 14,
          fill: false,
        },
        {
          label: this.translate.instant('PATIENT_STATS.CHART_TA_DIA_AFTER'),
          data: this.seriesValues(rows, ['ta_diastolique_apres', 'TA_DIASTOLIQUE_APRES']),
          borderColor: '#ea580c',
          backgroundColor: 'rgba(234, 88, 12, 0.15)',
          borderWidth: 2,
          tension: 0.28,
          pointRadius: 2.8,
          pointHoverRadius: 5.8,
          pointHitRadius: 14,
          fill: false,
        },
      ],
    };
  });

  readonly hbChart = computed<ChartData<'line'>>(() => {
    const rows = this.medical().hbTrend;
    const labels = rows.map((row, i) => this.formatDisplayDate(this.readLabel(row, ['DATE_PRELEVEMENT', 'date_prelevement'])) || `M${i + 1}`);
    return {
      labels,
      datasets: [
        {
          label: this.translate.instant('PATIENT_STATS.CHART_HB'),
          data: this.seriesValues(rows, ['hb_g_dl', 'HB_G_DL']),
          borderColor: '#7c3aed',
          backgroundColor: 'rgba(124, 58, 237, 0.15)',
          borderWidth: 2,
          tension: 0.35,
          pointRadius: 3,
          pointHoverRadius: 6,
          pointHitRadius: 14,
          fill: true,
        },
      ],
    };
  });

  readonly hasPoidsData = computed(() => this.hasChartValues(this.poidsChart()));
  readonly hasTaData = computed(() => this.hasChartValues(this.taChart()));
  readonly hasHbData = computed(() => this.hasChartValues(this.hbChart()));

  readonly ufBars = computed<BarPoint[]>(() =>
    this.paramedical()
      .poidsEvolution.map((row, idx) => {
        const value = this.readNumber(row, ['uf_reelle_ml', 'UF_REELLE_ML']);
        return {
          label: this.formatDisplayDate(this.readLabel(row, ['date_seance', 'DATE_SEANCE'])) || `S${idx + 1}`,
          value,
        };
      })
      .filter((r) => r.value > 0),
  );

  private readonly api = inject(BackendApiService);
  private readonly appShell = inject(AppShellStore);
  private readonly auth = inject(AuthStore);
  readonly canExport = computed(() => this.auth.hasRole('ADMIN'));
  private readonly route = inject(ActivatedRoute);
  readonly patientIdInput = input<string | null>(null, {alias: 'patientId'});
  readonly patientId = computed<string>(() => this.patientIdInput() || this.route.snapshot.paramMap.get('id') || '');
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);
  private readonly translate = inject(TranslateService);

  constructor() {
    this.setPeriod('3m');
    effect(() => {
      const centerId = this.appShell.currentCenterId();
      const patientId = this.patientId();
      if (!centerId || !patientId) return;
      untracked(() => this.reload());
    });
  }

  setPeriod(value: '1m' | '3m' | '6m' | '1y' | 'custom'): void {
    this.period.set(value);
    if (value === 'custom') return;

    const now = new Date();
    const to = now.toISOString().slice(0, 10);
    const fromDate = new Date(now);
    if (value === '1m') fromDate.setMonth(fromDate.getMonth() - 1);
    if (value === '3m') fromDate.setMonth(fromDate.getMonth() - 3);
    if (value === '6m') fromDate.setMonth(fromDate.getMonth() - 6);
    if (value === '1y') fromDate.setFullYear(fromDate.getFullYear() - 1);
    this.fromDate.set(fromDate.toISOString().slice(0, 10));
    this.toDate.set(to);
  }

  reload(): void {
    const centerId = this.appShell.currentCenterId();
    const patientId = this.patientId();
    if (!centerId || !patientId) return;

    this.loading.set(true);
    forkJoin({
      paramedical: this.api.getPatientParamedicalStats(
        centerId,
        patientId,
        this.fromDate(),
        this.toDate(),
      ),
      medical: this.api.getPatientMedicalStats(centerId, patientId, this.fromDate(), this.toDate()),
    }).subscribe({
      next: ({paramedical, medical}) => {
        this.paramedical.set(paramedical);
        this.medical.set(medical);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snackBar.open(this.translate.instant('PATIENT_STATS.LOAD_ERROR'), 'OK', {
          duration: 4000,
        });
      },
    });
  }

  export(format: 'csv' | 'pdf'): void {
    const centerId = this.appShell.currentCenterId();
    const patientId = this.patientId();
    if (!centerId || !patientId) return;

    this.exporting.set(true);
    this.api
      .exportPatientStats(centerId, patientId, format, this.fromDate(), this.toDate())
      .subscribe({
        next: (response) => {
          const blob = response.body;
          if (!blob) {
            this.exporting.set(false);
            this.snackBar.open(this.translate.instant('PATIENT_STATS.EXPORT_ERROR'), 'OK', {
              duration: 4000,
            });
            return;
          }
          const contentDisposition = response.headers.get('content-disposition') || '';
          const fileName =
            this.extractFileName(contentDisposition) || `patient-stats-${patientId}.${format}`;
          const a = document.createElement('a');
          a.href = URL.createObjectURL(blob);
          if (format === 'pdf') {
            window.open(a.href, '_blank', 'noopener');
          }
          a.download = fileName;
          a.click();
          setTimeout(() => URL.revokeObjectURL(a.href), 1000);
          this.exporting.set(false);
        },
        error: () => {
          this.exporting.set(false);
          this.snackBar.open(this.translate.instant('PATIENT_STATS.EXPORT_ERROR'), 'OK', {
            duration: 4000,
          });
        },
      });
  }

  goBack(): void {
    this.router.navigate(['/patients', this.patientId()]);
  }

  ufHeightPct(value: number): number {
    const bars = this.ufBars();
    const max = bars.reduce((acc, item) => Math.max(acc, item.value), 0);
    if (max <= 0) return 1;
    return Math.max(2, Math.round((value / max) * 100));
  }

  private hasChartValues(data: ChartData<'line'>): boolean {
    return data.datasets.some((dataset) =>
      (dataset.data as Array<number | null>).some((v) => typeof v === 'number' && Number.isFinite(v)),
    );
  }

  private seriesValues(rows: Array<Record<string, unknown>>, keys: string[]): Array<number | null> {
    return rows.map((row) => this.readNullableNumber(row, keys));
  }

  private readNumber(row: Record<string, unknown>, keys: string[]): number {
    for (const key of keys) {
      const raw = row[key];
      if (typeof raw === 'number' && Number.isFinite(raw)) return raw;
      if (typeof raw === 'string' && raw.trim() !== '') {
        const parsed = Number(raw);
        if (Number.isFinite(parsed)) return parsed;
      }
    }
    return 0;
  }

  private readNullableNumber(row: Record<string, unknown>, keys: string[]): number | null {
    for (const key of keys) {
      const raw = row[key];
      if (typeof raw === 'number' && Number.isFinite(raw)) return raw;
      if (typeof raw === 'string' && raw.trim() !== '') {
        const parsed = Number(raw);
        if (Number.isFinite(parsed)) return parsed;
      }
    }
    return null;
  }

  private readLabel(row: Record<string, unknown>, keys: string[]): string {
    for (const key of keys) {
      const raw = row[key];
      if (typeof raw === 'string' && raw.trim()) {
        return raw.slice(0, 10);
      }
    }
    return '';
  }

  private extractFileName(contentDisposition: string): string | null {
    const match = contentDisposition.match(/filename\*?=(?:UTF-8''|\")?([^";]+)/i);
    if (!match?.[1]) return null;
    return decodeURIComponent(match[1].replace(/\"/g, '').trim());
  }

  private formatDisplayDate(value: string): string {
    if (!value) return '';
    const source = value.trim();
    const match = source.match(/^(\d{4})-(\d{2})-(\d{2})/);
    if (!match) return source;
    return `${match[3]}/${match[2]}/${match[1]}`;
  }
}
