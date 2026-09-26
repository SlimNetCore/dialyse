import {CurrencyPipe, DatePipe} from '@angular/common';
import {ChangeDetectionStrategy, Component, computed, effect, inject, OnInit, signal, untracked} from '@angular/core';
import {MatCardModule} from '@angular/material/card';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatTooltipModule} from '@angular/material/tooltip';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {compatForm} from '@angular/forms/signals/compat';
import {FormField, FormRoot} from '@angular/forms/signals';
import {BaseChartDirective} from 'ng2-charts';
import {Chart, ChartData, ChartOptions, registerables} from 'chart.js';
import {DirectionStore} from './state/direction.store';
import {collectionLevel, formatHeadcount, formatPct} from './direction.util';

Chart.register(...registerables);

/**
 * Tableau de bord consolidé de la direction : agrégats anonymes par centre et pour la société (aucune donnée
 * nominative ; les effectifs inférieurs au seuil d'anonymat sont masqués par le serveur).
 */
@Component({
  selector: 'app-direction-dashboard',
  standalone: true,
  imports: [
    CurrencyPipe, DatePipe, TranslateModule, MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule,
    MatIconModule, MatProgressBarModule, MatTooltipModule, BaseChartDirective, FormRoot, FormField,
  ],
  templateUrl: './direction-dashboard.component.html',
  styleUrl: './direction-dashboard.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DirectionDashboardComponent implements OnInit {
  protected readonly store = inject(DirectionStore);
  protected readonly period = signal({from: '', to: ''});
  protected readonly periodForm = compatForm(this.period);
  protected readonly canApply = computed(() => !!this.period().from && !!this.period().to && !this.store.loading());
  protected readonly threshold = computed(() => this.store.overview()?.seuilAnonymat ?? 5);
  protected readonly errorKey = computed(() => {
    const code = this.store.error();
    return code === 'PERIODE_INVALIDE' || code === 'PERIODE_TROP_LONGUE' ? `DIRECTION.ERROR.${code}` : 'DIRECTION.ERROR.LOAD_ERROR';
  });
  protected readonly barOptions: ChartOptions<'bar'> = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {legend: {display: true, position: 'bottom'}},
    scales: {x: {ticks: {autoSkip: true, maxRotation: 45, minRotation: 0}}},
  };
  protected readonly lineOptions: ChartOptions<'line'> = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {legend: {display: true, position: 'bottom'}},
    scales: {x: {ticks: {autoSkip: true, maxRotation: 45, minRotation: 0}}},
  };
  private readonly translate = inject(TranslateService);
  protected readonly revenueChart = computed<ChartData<'bar'>>(() => {
    const centres = this.store.rankedCentres();
    return {
      labels: centres.map((c) => c.nom),
      datasets: [
        {label: this.translate.instant('DIRECTION.KPI.CA_TTC'), data: centres.map((c) => c.caTtc)},
        {label: this.translate.instant('DIRECTION.KPI.ENCAISSE'), data: centres.map((c) => c.encaisse)},
      ],
    };
  });
  protected readonly trendChart = computed<ChartData<'line'>>(() => {
    const months = this.store.months();
    return {
      labels: months.map((m) => m.mois),
      datasets: [{
        label: this.translate.instant('DIRECTION.KPI.CA_TTC'),
        data: months.map((m) => m.caTtc),
        tension: 0.3
      }],
    };
  });

  constructor() {
    // Aligne les champs de période sur celle réellement renvoyée par le serveur (période par défaut incluse).
    effect(() => {
      const overview = this.store.overview();
      if (overview) {
        untracked(() => this.period.set({from: overview.from, to: overview.to}));
      }
    });
  }

  ngOnInit(): void {
    void this.store.load('', '');
  }

  protected apply(): void {
    if (this.canApply()) {
      void this.store.load(this.period().from, this.period().to);
    }
  }

  /** Lignes des tableaux cliniques et de stock : un centre par ligne, puis le total de la société. */
  protected readonly indicatorRows = computed(() => {
    const ind = this.store.indicators();
    if (!ind) return [];
    return [...ind.centres.map((c) => ({c, total: false})), {c: ind.totaux, total: true}];
  });

  protected pct(value: number | null): string {
    return formatPct(value);
  }

  protected alertKey(code: string): string {
    return `DIRECTION.ALERTS.${code}`;
  }

  protected headcount(value: number | null): string {
    return formatHeadcount(value, this.threshold());
  }

  protected level(rate: number | null): string {
    return collectionLevel(rate);
  }
}
