import {ChangeDetectionStrategy, Component, computed, effect, inject} from '@angular/core';
import {DecimalPipe} from '@angular/common';
import {RouterLink} from '@angular/router';
import {MatCardModule} from '@angular/material/card';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {BaseChartDirective} from 'ng2-charts';
import {Chart, ChartData, ChartOptions, registerables} from 'chart.js';
import {FacturationStore} from './state/facturation.store';
import {AppShellStore} from '../../core/state/app-shell.store';
import {AuthStore} from '../../core/state/auth.store';

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
    BaseChartDirective,
    TranslateModule,
    DecimalPipe,
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

  protected readonly currentCenterId = computed(() => this.appShell.currentCenterId());
  protected readonly currentUsername = computed(() => this.auth.username() ?? 'system');
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

  protected refreshDashboard(): void {
    const centerId = this.currentCenterId();
    if (!centerId) {
      return;
    }
    this.store.loadDashboard({centerId, month: this.store.month()});
    this.store.loadRevenueTrend({centerId, endingMonth: this.store.month(), months: 6});
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
}
