import {ChangeDetectionStrategy, Component, computed, inject, signal} from '@angular/core';
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
import {BackendApiService, PatientMedicalStats, PatientParamedicalStats,} from '../../core/api/backend-api.service';
import {AppShellStore} from '../../core/state/app-shell.store';
import {AuthStore} from '../../core/state/auth.store';

type LineChartModel = {
  hasData: boolean;
  labels: string[];
  min: number;
  max: number;
  pointsByKey: Record<string, string>;
};

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
  readonly poidsChart = computed(() =>
    this.buildLineChart(
      this.paramedical().poidsEvolution,
      ['poids_avant_kg', 'poids_apres_kg'],
      ['DATE_SEANCE', 'date_seance'],
    ),
  );
  readonly hbChart = computed(() =>
    this.buildLineChart(
      this.medical().hbTrend,
      ['hb_g_dl'],
      ['DATE_PRELEVEMENT', 'date_prelevement'],
    ),
  );
  readonly ufBars = computed<BarPoint[]>(() =>
    this.paramedical()
      .poidsEvolution.map((row, idx) => {
        const value = this.readNumber(row, ['uf_reelle_ml', 'UF_REELLE_ML']);
        return {
          label: this.readLabel(row, ['date_seance', 'DATE_SEANCE']) || `S${idx + 1}`,
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
  readonly patientId = signal<string>(this.route.snapshot.paramMap.get('id') ?? '');
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);
  private readonly translate = inject(TranslateService);

  constructor() {
    this.setPeriod('3m');
    this.reload();
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

  private buildLineChart(
    rows: Array<Record<string, unknown>>,
    valueKeys: string[],
    labelKeys: string[],
  ): LineChartModel {
    const labels = rows.map((row, index) => this.readLabel(row, labelKeys) || `P${index + 1}`);
    const valuesByKey: Record<string, number[]> = {};
    valueKeys.forEach((key) => {
      const upper = key.toUpperCase();
      valuesByKey[key] = rows.map((row) => this.readNumber(row, [key, upper]));
    });
    const allValues = valueKeys
      .flatMap((key) => valuesByKey[key])
      .filter((n) => Number.isFinite(n));
    if (allValues.length === 0) {
      return {hasData: false, labels: [], min: 0, max: 0, pointsByKey: {}};
    }
    const min = Math.min(...allValues);
    const max = Math.max(...allValues);
    const pointsByKey = valueKeys.reduce<Record<string, string>>((acc, key) => {
      acc[key] = this.toPolyline(valuesByKey[key], min, max);
      return acc;
    }, {});
    return {hasData: true, labels, min, max, pointsByKey};
  }

  private toPolyline(values: number[], min: number, max: number): string {
    if (values.length === 0) return '';
    const range = max - min;
    return values
      .map((value, index) => {
        const x = values.length === 1 ? 50 : (index / (values.length - 1)) * 100;
        const ratio = range <= 0 ? 0.5 : (value - min) / range;
        const y = 36 - ratio * 30;
        return `${x.toFixed(2)},${y.toFixed(2)}`;
      })
      .join(' ');
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
}
