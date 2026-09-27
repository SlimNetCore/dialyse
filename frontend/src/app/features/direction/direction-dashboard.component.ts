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
import {DirectionStore, LIVE_REPORT} from './state/direction.store';
import {collectionLevel, formatHeadcount, formatPct, lastCompleteMonths} from './direction.util';
import {MatButtonToggleModule} from '@angular/material/button-toggle';
import {
  AGE_CODES,
  ageSeries,
  CaisseMetric,
  pivotCaisses,
  PivotCentre,
  sexeSeries
} from './direction-breakdown.util';
import {DirectionRealtimeService} from './state/direction-realtime.service';

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
    MatButtonToggleModule,
  ],
  templateUrl: './direction-dashboard.component.html',
  styleUrl: './direction-dashboard.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DirectionDashboardComponent implements OnInit {
  protected readonly store = inject(DirectionStore);
  protected readonly realtime = inject(DirectionRealtimeService);
  protected readonly period = signal({from: '', to: ''});
  protected readonly periodForm = compatForm(this.period);
  protected readonly canApply = computed(() => !!this.period().from && !!this.period().to && !this.store.loading());
  /** Douze derniers mois écoulés, avec leur instantané s'il est déjà figé. */
  protected readonly reportRows = computed(() => {
    const frozen = new Map(this.store.snapshots().map((s) => [s.mois, s.generatedAt]));
    return lastCompleteMonths(new Date(), 12).map((mois) => ({mois, generatedAt: frozen.get(mois) ?? null}));
  });
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
  protected readonly ageCodes = AGE_CODES;
  protected readonly liveReport = LIVE_REPORT;

  ngOnInit(): void {
    void this.store.load('', '');
    void this.store.loadSnapshots();
  }

  /** Fige le mois au besoin, puis enregistre le rapport PDF sur le poste. */
  protected async downloadReport(mois: string): Promise<void> {
    this.save(await this.store.report(mois), `rapport-direction-${mois}.pdf`);
  }

  /** Enregistre le rapport PDF de la période affichée (en-tête, pied de page et toutes les statistiques). */
  protected async printReport(): Promise<void> {
    this.save(await this.store.liveReport(), `rapport-direction-${this.store.from()}_${this.store.to()}.pdf`);
  }

  private save(blob: Blob | null, filename: string): void {
    if (!blob) return;
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    link.click();
    URL.revokeObjectURL(url);
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

  // ───────────────────────────── Répartitions par centre ─────────────────────────────
  protected readonly caisseMetrics: CaisseMetric[] = ['patients', 'seances', 'caHt'];
  protected readonly caisseMetric = signal<CaisseMetric>('caHt');
  protected readonly breakdown = this.store.breakdown;
  protected readonly pivotCentres = computed<PivotCentre[]>(() =>
    (this.breakdown()?.sexe ?? []).map((r) => ({id: r.centerId, nom: r.nom})));
  protected readonly caissePivot = computed(() => {
    const b = this.breakdown();
    return b ? pivotCaisses(this.pivotCentres(), b.caisses, b.caisseTotaux, this.caisseMetric()) : [];
  });
  protected readonly stackedOptions: ChartOptions<'bar'> = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {legend: {display: true, position: 'bottom'}},
    scales: {x: {stacked: true}, y: {stacked: true, beginAtZero: true}},
  };
  protected readonly sexeChart = computed<ChartData<'bar'>>(() => {
    const s = sexeSeries(this.breakdown()?.sexe ?? []);
    return {
      labels: s.labels,
      datasets: [
        {label: this.translate.instant('DIRECTION.BREAKDOWN.MASCULIN'), data: s.masculin},
        {label: this.translate.instant('DIRECTION.BREAKDOWN.FEMININ'), data: s.feminin},
        {label: this.translate.instant('DIRECTION.BREAKDOWN.AUTRE'), data: s.autre},
      ],
    };
  });
  protected readonly ageChart = computed<ChartData<'bar'>>(() => {
    const s = ageSeries(this.breakdown()?.ages ?? [], AGE_CODES);
    return {
      labels: s.labels,
      datasets: AGE_CODES.map((code) => ({
        label: this.translate.instant(`DIRECTION.BREAKDOWN.AGE.${code}`), data: s.series[code],
      })),
    };
  });
  /** CA HT par caisse, empilé par centre. */
  protected readonly caisseCaChart = computed<ChartData<'bar'>>(() => {
    const lines = pivotCaisses(this.pivotCentres(), this.breakdown()?.caisses ?? [], this.breakdown()?.caisseTotaux ?? [], 'caHt');
    return {
      labels: lines.map((l) => l.nom || this.translate.instant('DIRECTION.BREAKDOWN.CAISSE_INCONNUE')),
      datasets: this.pivotCentres().map((c) => ({label: c.nom, data: lines.map((l) => l.cells[c.id] ?? 0)})),
    };
  });

  constructor() {
    // Aligne les champs de période sur celle réellement renvoyée par le serveur (période par défaut incluse).
    // Dépend de la période du store (et non des données) : une relecture en temps réel n'écrase pas une saisie en cours.
    effect(() => {
      const from = this.store.from();
      const to = this.store.to();
      if (from && to) {
        untracked(() => this.period.set({from, to}));
      }
    });
  }

  protected caisseLabel(nom: string): string {
    return nom || this.translate.instant('DIRECTION.BREAKDOWN.CAISSE_INCONNUE');
  }

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
