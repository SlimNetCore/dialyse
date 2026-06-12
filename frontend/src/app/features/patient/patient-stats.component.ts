import {Component, computed, inject, signal} from '@angular/core';
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
import {BackendApiService, PatientMedicalStats, PatientParamedicalStats} from '../../core/api/backend-api.service';
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
    TranslateModule
  ],
  template: `
    <div class="stats-page">
      <div class="stats-header">
        <button mat-icon-button (click)="goBack()"><mat-icon>arrow_back</mat-icon></button>
        <div class="stats-title">
          <h2>{{ 'PATIENT_STATS.TITLE' | translate }}</h2>
          <p>{{ patientId() }}</p>
        </div>
      </div>

      <mat-card class="filters-card">
        <div class="filters-row">
          <mat-form-field appearance="outline">
            <mat-label>{{ 'PATIENT_STATS.PERIOD' | translate }}</mat-label>
            <mat-select [value]="period()" (valueChange)="setPeriod($event)">
              <mat-option value="1m">{{ 'PATIENT_STATS.PERIOD_1M' | translate }}</mat-option>
              <mat-option value="3m">{{ 'PATIENT_STATS.PERIOD_3M' | translate }}</mat-option>
              <mat-option value="6m">{{ 'PATIENT_STATS.PERIOD_6M' | translate }}</mat-option>
              <mat-option value="1y">{{ 'PATIENT_STATS.PERIOD_1Y' | translate }}</mat-option>
              <mat-option value="custom">{{ 'PATIENT_STATS.PERIOD_CUSTOM' | translate }}</mat-option>
            </mat-select>
          </mat-form-field>

          @if (period() === 'custom') {
            <mat-form-field appearance="outline">
              <mat-label>{{ 'COMMON.DATE_START' | translate }}</mat-label>
              <input matInput type="date" [value]="fromDate()" (change)="fromDate.set($any($event.target).value)" />
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>{{ 'COMMON.DATE_END' | translate }}</mat-label>
              <input matInput type="date" [value]="toDate()" (change)="toDate.set($any($event.target).value)" />
            </mat-form-field>
          }

          <button mat-flat-button color="primary" (click)="reload()" [disabled]="loading()">
            <mat-icon>refresh</mat-icon>
            {{ 'PATIENT_STATS.REFRESH' | translate }}
          </button>

          @if (canExport()) {
            <button mat-stroked-button color="primary" data-testid="stats-export-csv" (click)="export('csv')" [disabled]="exporting()">
              <mat-icon>download</mat-icon>
              CSV
            </button>
            <button mat-stroked-button color="primary" data-testid="stats-export-pdf" (click)="export('pdf')" [disabled]="exporting()">
              <mat-icon>picture_as_pdf</mat-icon>
              PDF
            </button>
          }
        </div>
      </mat-card>

      @if (loading()) {
        <div class="loading-wrap"><mat-progress-spinner mode="indeterminate" diameter="40"></mat-progress-spinner></div>
      } @else {
        <div class="grid">
          <mat-card>
            <h3>{{ 'PATIENT_STATS.PARAMEDICAL' | translate }}</h3>
            <div class="kpis">
              <div class="kpi"><span>{{ 'PATIENT_STATS.SEANCES' | translate }}</span><strong>{{ paramedical().seanceCount }}</strong></div>
              <div class="kpi"><span>{{ 'PATIENT_STATS.AVG_POIDS_AVANT' | translate }}</span><strong>{{ paramedical().avgPoidsAvantKg | number:'1.0-2' }}</strong></div>
              <div class="kpi"><span>{{ 'PATIENT_STATS.AVG_POIDS_APRES' | translate }}</span><strong>{{ paramedical().avgPoidsApresKg | number:'1.0-2' }}</strong></div>
              <div class="kpi"><span>{{ 'PATIENT_STATS.AVG_UF' | translate }}</span><strong>{{ paramedical().avgUfReelleMl | number:'1.0-0' }}</strong></div>
            </div>
          </mat-card>

          <mat-card>
            <h3>{{ 'PATIENT_STATS.MEDICAL' | translate }}</h3>
            <div class="kpis">
              <div class="kpi"><span>{{ 'PATIENT_STATS.AVG_HB' | translate }}</span><strong>{{ medical().avgHbGDl | number:'1.0-2' }}</strong></div>
              <div class="kpi"><span>{{ 'PATIENT_STATS.AVG_KTV' | translate }}</span><strong>{{ medical().avgKtV | number:'1.0-2' }}</strong></div>
              <div class="kpi"><span>{{ 'PATIENT_STATS.AVG_FERRITINE' | translate }}</span><strong>{{ medical().avgFerritineNgMl | number:'1.0-2' }}</strong></div>
            </div>
          </mat-card>
        </div>

        <div class="grid charts-grid">
          <mat-card class="chart-card" data-testid="stats-chart-poids">
            <h3>{{ 'PATIENT_STATS.CHART_POIDS' | translate }}</h3>
            @if (poidsChart().hasData) {
              <svg viewBox="0 0 100 40" preserveAspectRatio="none" class="chart-svg" role="img" aria-label="Courbe des poids">
                <polyline [attr.points]="poidsChart().pointsByKey['poids_avant_kg']" class="line line-1" />
                <polyline [attr.points]="poidsChart().pointsByKey['poids_apres_kg']" class="line line-2" />
              </svg>
              <div class="legend">
                <span><i class="dot dot-1"></i>{{ 'PATIENT_STATS.CHART_POIDS_BEFORE' | translate }}</span>
                <span><i class="dot dot-2"></i>{{ 'PATIENT_STATS.CHART_POIDS_AFTER' | translate }}</span>
              </div>
            } @else {
              <p class="empty-chart">{{ 'COMMON.COMING_SOON' | translate }}</p>
            }
          </mat-card>

          <mat-card class="chart-card" data-testid="stats-chart-uf">
            <h3>{{ 'PATIENT_STATS.CHART_UF' | translate }}</h3>
            @if (ufBars().length > 0) {
              <div class="bar-chart" role="img" aria-label="UF reelle par seance">
                @for (bar of ufBars(); track $index) {
                  <div class="bar-slot">
                    <div class="bar" [style.height.%]="ufHeightPct(bar.value)"></div>
                    <small>{{ bar.label }}</small>
                  </div>
                }
              </div>
            } @else {
              <p class="empty-chart">{{ 'COMMON.COMING_SOON' | translate }}</p>
            }
          </mat-card>

          <mat-card class="chart-card" data-testid="stats-chart-hb">
            <h3>{{ 'PATIENT_STATS.CHART_HB' | translate }}</h3>
            @if (hbChart().hasData) {
              <svg viewBox="0 0 100 40" preserveAspectRatio="none" class="chart-svg" role="img" aria-label="Courbe hemoglobine">
                <polyline [attr.points]="hbChart().pointsByKey['hb_g_dl']" class="line line-3" />
              </svg>
            } @else {
              <p class="empty-chart">{{ 'COMMON.COMING_SOON' | translate }}</p>
            }
          </mat-card>
        </div>

        <mat-card class="table-card">
          <h3>{{ 'PATIENT_STATS.HB_TREND' | translate }}</h3>
          <div class="table-wrap">
            <table>
              <thead><tr><th>Date</th><th>Hb</th><th>Kt/V</th><th>Ferritine</th></tr></thead>
              <tbody>
                @for (row of medical().hbTrend; track $index) {
                  <tr>
                    <td>{{ row['date_prelevement'] || row['DATE_PRELEVEMENT'] }}</td>
                    <td>{{ row['hb_g_dl'] || row['HB_G_DL'] }}</td>
                    <td>{{ row['kt_v_mensuel'] || row['KT_V_MENSUEL'] }}</td>
                    <td>{{ row['ferritine_ng_ml'] || row['FERRITINE_NG_ML'] }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </mat-card>
      }
    </div>
  `,
  styles: [`
    .stats-page { max-width: 1200px; margin: 0 auto; display: grid; gap: 12px; }
    .stats-header { display: flex; gap: 12px; align-items: center; }
    .stats-title h2 { margin: 0; color: var(--app-primary); }
    .stats-title p { margin: 0; color: var(--app-muted); font-size: 12px; }
    .filters-card { padding: 8px; }
    .filters-row { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
    .grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
    .kpis { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
    .kpi { background: var(--app-surface-soft); border: 1px solid var(--app-border); border-radius: 10px; padding: 10px; }
    .kpi span { display: block; font-size: 12px; color: var(--app-muted); }
    .kpi strong { font-size: 18px; color: var(--app-primary); }
    .table-card { padding: 10px; }
    .charts-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
    .chart-card { padding: 10px; }
    .chart-svg { width: 100%; height: 180px; background: var(--app-surface-soft); border: 1px solid var(--app-border); border-radius: 8px; }
    .line { fill: none; stroke-width: 1.6; }
    .line-1 { stroke: #1d4ed8; }
    .line-2 { stroke: #0f766e; }
    .line-3 { stroke: #7c3aed; }
    .legend { display: flex; gap: 12px; margin-top: 8px; font-size: 12px; color: var(--app-muted); }
    .dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; margin-right: 6px; }
    .dot-1 { background: #1d4ed8; }
    .dot-2 { background: #0f766e; }
    .bar-chart { display: flex; align-items: flex-end; gap: 8px; height: 180px; padding: 8px; border: 1px solid var(--app-border); border-radius: 8px; background: var(--app-surface-soft); overflow-x: auto; }
    .bar-slot { min-width: 42px; display: flex; flex-direction: column; align-items: center; justify-content: flex-end; height: 100%; }
    .bar { width: 20px; background: linear-gradient(180deg, #0284c7, #0ea5e9); border-radius: 6px 6px 0 0; min-height: 3px; }
    .bar-slot small { margin-top: 6px; font-size: 10px; color: var(--app-muted); }
    .empty-chart { color: var(--app-muted); font-size: 13px; margin: 12px 0; }
    .table-wrap { overflow: auto; border: 1px solid var(--app-border); border-radius: 10px; }
    table { width: 100%; border-collapse: collapse; }
    th, td { padding: 8px 10px; border-bottom: 1px solid var(--app-border); font-size: 13px; }
    .loading-wrap { display: flex; justify-content: center; padding: 32px 0; }
    @media (max-width: 900px) { .grid, .charts-grid { grid-template-columns: 1fr; } .kpis { grid-template-columns: 1fr; } }
  `]
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
    taEvolution: []
  });
  readonly medical = signal<PatientMedicalStats>({
    avgHbGDl: 0,
    avgKtV: 0,
    avgFerritineNgMl: 0,
    hbTrend: [],
    epoTrend: []
  });
  readonly poidsChart = computed(() => this.buildLineChart(this.paramedical().poidsEvolution, ['poids_avant_kg', 'poids_apres_kg'], ['DATE_SEANCE', 'date_seance']));
  readonly hbChart = computed(() => this.buildLineChart(this.medical().hbTrend, ['hb_g_dl'], ['DATE_PRELEVEMENT', 'date_prelevement']));
  readonly ufBars = computed<BarPoint[]>(() =>
    this.paramedical().poidsEvolution
      .map((row, idx) => {
        const value = this.readNumber(row, ['uf_reelle_ml', 'UF_REELLE_ML']);
        return {
          label: this.readLabel(row, ['date_seance', 'DATE_SEANCE']) || `S${idx + 1}`,
          value
        };
      })
      .filter((r) => r.value > 0)
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
      paramedical: this.api.getPatientParamedicalStats(centerId, patientId, this.fromDate(), this.toDate()),
      medical: this.api.getPatientMedicalStats(centerId, patientId, this.fromDate(), this.toDate())
    }).subscribe({
      next: ({paramedical, medical}) => {
        this.paramedical.set(paramedical);
        this.medical.set(medical);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snackBar.open(this.translate.instant('PATIENT_STATS.LOAD_ERROR'), 'OK', {duration: 4000});
      }
    });
  }

  export(format: 'csv' | 'pdf'): void {
    const centerId = this.appShell.currentCenterId();
    const patientId = this.patientId();
    if (!centerId || !patientId) return;

    this.exporting.set(true);
    this.api.exportPatientStats(centerId, patientId, format, this.fromDate(), this.toDate()).subscribe({
      next: (response) => {
        const blob = response.body;
        if (!blob) {
          this.exporting.set(false);
          this.snackBar.open(this.translate.instant('PATIENT_STATS.EXPORT_ERROR'), 'OK', {duration: 4000});
          return;
        }
        const contentDisposition = response.headers.get('content-disposition') || '';
        const fileName = this.extractFileName(contentDisposition) || `patient-stats-${patientId}.${format}`;
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
        this.snackBar.open(this.translate.instant('PATIENT_STATS.EXPORT_ERROR'), 'OK', {duration: 4000});
      }
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

  private buildLineChart(rows: Array<Record<string, unknown>>, valueKeys: string[], labelKeys: string[]): LineChartModel {
    const labels = rows.map((row, index) => this.readLabel(row, labelKeys) || `P${index + 1}`);
    const valuesByKey: Record<string, number[]> = {};
    valueKeys.forEach((key) => {
      const upper = key.toUpperCase();
      valuesByKey[key] = rows.map((row) => this.readNumber(row, [key, upper]));
    });
    const allValues = valueKeys.flatMap((key) => valuesByKey[key]).filter((n) => Number.isFinite(n));
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
    return values.map((value, index) => {
      const x = values.length === 1 ? 50 : (index / (values.length - 1)) * 100;
      const ratio = range <= 0 ? 0.5 : (value - min) / range;
      const y = 36 - (ratio * 30);
      return `${x.toFixed(2)},${y.toFixed(2)}`;
    }).join(' ');
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



