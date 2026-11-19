import {ChangeDetectionStrategy, Component, computed, effect, inject, untracked} from '@angular/core';
import {DecimalPipe} from '@angular/common';
import {MatCardModule} from '@angular/material/card';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {BaseChartDirective} from 'ng2-charts';
import {Chart, ChartData, ChartOptions, registerables} from 'chart.js';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {FacturationTableauDeBordStore, MOIS_DE_LA_COURBE} from './facturation-tableau-de-bord.store';

Chart.register(...registerables);

/**
 * Tableau de bord de la facturation (/facturation/tableau-de-bord) : indicateurs du mois, courbe du CA sur six mois,
 * répartitions par caisse et par statut patient, avec un sélecteur de mois propre. Extrait de l'écran de calcul et de
 * validation (/facturation), qui ne garde que le travail en cours.
 */
@Component({
  selector: 'app-facturation-tableau-de-bord',
  standalone: true,
  imports: [
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
  providers: [FacturationTableauDeBordStore],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './facturation-tableau-de-bord.component.html',
  styleUrl: './facturation-tableau-de-bord.component.css',
})
export class FacturationTableauDeBordComponent {
  protected readonly store = inject(FacturationTableauDeBordStore);
  private readonly appShell = inject(AppShellStore);
  private readonly translate = inject(TranslateService);

  protected readonly currentCenterId = computed(() => this.appShell.currentCenterId());

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
    // Chargement à l'ouverture et à chaque changement de centre, pour le mois affiché.
    effect(() => {
      const centerId = this.currentCenterId();
      if (centerId) {
        untracked(() => this.charger(centerId, this.store.mois()));
      }
    });
  }

  protected onMoisChange(value: string): void {
    this.store.setMois(value);
    const centerId = this.currentCenterId();
    if (centerId && /^\d{4}-\d{2}$/.test(value)) {
      this.charger(centerId, value);
    }
  }

  protected onRafraichir(): void {
    const centerId = this.currentCenterId();
    if (centerId) {
      this.charger(centerId, this.store.mois());
    }
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

  private charger(centerId: string, month: string): void {
    this.store.loadDashboard({centerId, month});
    this.store.loadRevenueTrend({centerId, endingMonth: month, months: MOIS_DE_LA_COURBE});
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
