import {CurrencyPipe, DatePipe, DecimalPipe} from '@angular/common';
import {ChangeDetectionStrategy, Component, computed, effect, inject, OnInit, signal, untracked} from '@angular/core';
import {MatCardModule} from '@angular/material/card';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatSelectModule} from '@angular/material/select';
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
import {
  buildDashboardCsv,
  collectionLevel,
  CsvSection,
  Delta,
  delta,
  filterByCentre,
  formatHeadcount,
  formatPct,
  kdigoLevel,
  lastCompleteMonths,
  monthlyTotals,
  PeriodPreset,
  periodForPreset,
  rankIndex
} from './direction.util';
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
    CurrencyPipe, DatePipe, DecimalPipe, TranslateModule, MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule,
    MatIconModule, MatProgressBarModule, MatTooltipModule, BaseChartDirective, FormRoot, FormField,
    MatButtonToggleModule, MatSelectModule,
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
  /** Mois déjà figés, sélectionnables dans le comparateur (un mois non figé n'a pas encore d'instantané). */
  protected readonly frozenMonths = computed(() =>
    [...this.store.snapshots()].map((s) => s.mois).sort().reverse());
  /**
   * Delta société entre les deux mois figés choisis dans le comparateur (activité et finances) ; `null` tant que
   * les deux ne sont pas chargés.
   */
  protected readonly compareDeltas = computed(() => {
    const a = this.store.compareA();
    const b = this.store.compareB();
    if (!a || !b) return null;
    const ta = a.overview.totaux;
    const tb = b.overview.totaux;
    return {
      patients: {a: ta.patients, b: tb.patients},
      seances: {a: ta.seances, b: tb.seances, d: delta(tb.seances, ta.seances)},
      caTtc: {a: ta.caTtc, b: tb.caTtc, d: delta(tb.caTtc, ta.caTtc)},
      encaisse: {a: ta.encaisse, b: tb.encaisse, d: delta(tb.encaisse, ta.encaisse)},
      tauxEncaissement: {a: ta.tauxEncaissement, b: tb.tauxEncaissement},
    };
  });
  protected readonly threshold = computed(() => this.store.overview()?.seuilAnonymat ?? 5);
  /** Centre isolé par la direction (`null` = tous les centres, vue consolidée). */
  protected readonly selectedCentre = signal<string | null>(null);
  protected readonly centreOptions = computed(() =>
    (this.store.overview()?.centres ?? []).map((c) => ({id: c.centerId, nom: c.nom})));
  /** Centres à afficher dans le comparatif : tous, ou seulement celui isolé par le filtre. */
  protected readonly displayCentres = computed(() =>
    filterByCentre(this.store.rankedCentres(), this.selectedCentre()));
  /** Alertes du centre isolé par le filtre, ou de tous les centres. */
  protected readonly displayAlerts = computed(() => filterByCentre(this.store.alerts(), this.selectedCentre()));
  /** Totaux affichés dans les KPI : la société entière, ou le seul centre isolé par le filtre. */
  protected readonly displayTotals = computed(() => {
    const o = this.store.overview();
    if (!o) return null;
    const sel = this.selectedCentre();
    if (!sel) return o.totaux;
    return o.centres.find((c) => c.centerId === sel) ?? o.totaux;
  });
  protected readonly revenueChart = computed<ChartData<'bar'>>(() => {
    const centres = this.displayCentres();
    return {
      labels: centres.map((c) => c.nom),
      datasets: [
        {label: this.translate.instant('DIRECTION.KPI.CA_TTC'), data: centres.map((c) => c.caTtc)},
        {label: this.translate.instant('DIRECTION.KPI.ENCAISSE'), data: centres.map((c) => c.encaisse)},
      ],
    };
  });
  protected readonly trendChart = computed<ChartData<'line'>>(() => {
    const sel = this.selectedCentre();
    const points = this.store.overview()?.mensuel ?? [];
    const months = monthlyTotals(sel ? points.filter((p) => p.centerId === sel) : points);
    return {
      labels: months.map((m) => m.mois),
      datasets: [{
        label: this.translate.instant('DIRECTION.KPI.CA_TTC'),
        data: months.map((m) => m.caTtc),
        tension: 0.3
      }],
    };
  });
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
  protected readonly periodPresets: PeriodPreset[] = ['MONTH', 'QUARTER', 'YEAR', 'LAST_12_MONTHS'];
  /**
   * Deltas des KPI d'activité/finances vs la période de même durée précédente ; `null` si non comparable ou si un
   * centre est isolé (la comparaison n'est calculée que pour la société entière).
   */
  protected readonly kpiDeltas = computed<{ seances: Delta; caTtc: Delta; encaisse: Delta } | null>(() => {
    const o = this.store.overview();
    const prev = o?.periodePrecedente;
    if (!o || !prev || this.selectedCentre()) return null;
    return {
      seances: delta(o.totaux.seances, prev.seances),
      caTtc: delta(o.totaux.caTtc, prev.caTtc),
      encaisse: delta(o.totaux.encaisse, prev.encaisse),
    };
  });
  protected readonly ageCodes = AGE_CODES;
  protected readonly liveReport = LIVE_REPORT;
  /** Part de chaque centre actif dans le CA TTC total de la société (0-100), pour la barre « Part du CA ». */
  protected readonly caShares = computed<Record<string, number>>(() => {
    const centres = this.store.rankedCentres();
    const total = centres.reduce((sum, c) => sum + (c.caTtc ?? 0), 0);
    if (total <= 0) return {};
    return Object.fromEntries(centres.map((c) => [c.centerId ?? '', ((c.caTtc ?? 0) / total) * 100]));
  });
  /**
   * Lignes des tableaux cliniques et de stock : un centre par ligne, puis le total de la société (masqué quand un
   * centre est isolé par le filtre, puisque la seule ligne affichée est déjà ce centre).
   */
  protected readonly indicatorRows = computed(() => {
    const ind = this.store.indicators();
    if (!ind) return [];
    const sel = this.selectedCentre();
    const centres = sel ? ind.centres.filter((c) => c.centerId === sel) : ind.centres;
    const rows = centres.map((c) => ({c, total: false}));
    return sel ? rows : [...rows, {c: ind.totaux, total: true}];
  });
  /** Répartitions filtrées sur le centre isolé, le cas échéant (toutes les listes de `breakdown` partagent `centerId`). */
  protected readonly breakdown = computed(() => {
    const b = this.store.breakdown();
    const sel = this.selectedCentre();
    if (!b || !sel) return b;
    return {
      ...b,
      sexe: filterByCentre(b.sexe, sel),
      ages: filterByCentre(b.ages, sel),
      caisses: filterByCentre(b.caisses, sel),
      anemie: filterByCentre(b.anemie, sel),
    };
  });
  /** Mesure choisie par caisse (patients, séances ou CA HT), empilée par centre. */
  protected readonly caisseCaChart = computed<ChartData<'bar'>>(() => {
    const lines = pivotCaisses(this.pivotCentres(), this.breakdown()?.caisses ?? [], this.breakdown()?.caisseTotaux ?? [], this.caisseMetric());
    return {
      labels: lines.map((l) => l.nom || this.translate.instant('DIRECTION.BREAKDOWN.CAISSE_INCONNUE')),
      datasets: this.pivotCentres().map((c) => ({label: c.nom, data: lines.map((l) => l.cells[c.id] ?? 0)})),
    };
  });
  /** Historique des alertes limité au centre isolé par le filtre, le cas échéant. */
  protected readonly displayAlertHistory = computed(() => filterByCentre(this.store.alertHistory(), this.selectedCentre()));

  ngOnInit(): void {
    void this.store.load('', '');
    void this.store.loadSnapshots();
    void this.store.loadAlertHistory();
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
  /** Rang (1 = premier) de chaque centre par CA, calculé une fois sur la liste complète (stable même filtré). */
  private readonly rankByCentre = computed<Record<string, number>>(() => rankIndex(this.store.rankedCentres()));

  protected rankOf(centerId: string | null): number {
    return this.rankByCentre()[centerId ?? ''] ?? 0;
  }

  protected caShare(centerId: string | null): number {
    return this.caShares()[centerId ?? ''] ?? 0;
  }

  protected apply(): void {
    if (this.canApply()) {
      void this.store.load(this.period().from, this.period().to);
    }
  }

  /** Isole un centre (`null` = vue consolidée de tous les centres). */
  protected setCentre(centerId: string | null): void {
    this.selectedCentre.set(centerId);
  }

  /** Charge le mois choisi dans l'emplacement A ou B du comparateur de mois figés. */
  protected setCompareMonth(slot: 'a' | 'b', mois: string | null): void {
    if (mois) void this.store.loadCompare(slot, mois);
  }

  /**
   * Exporte en CSV (Excel) les tableaux actuellement affichés — finances par centre, qualité des soins, stock,
   * répartitions — dans le même périmètre que l'écran (période, centre isolé le cas échéant).
   */
  protected exportCsv(): void {
    const t = (key: string): string => this.translate.instant(key);
    const money = (v: number): number => Math.round(v * 100) / 100;
    const sections: CsvSection[] = [];

    sections.push({
      title: t('DIRECTION.CENTRES_TITLE'),
      headers: [t('DIRECTION.COL.CENTRE'), t('DIRECTION.KPI.PATIENTS'), t('DIRECTION.COL.SOUS_KT'),
        t('DIRECTION.KPI.SEANCES'), t('DIRECTION.COL.FACTURES'), 'CA HT', t('DIRECTION.KPI.CA_TTC'),
        t('DIRECTION.KPI.ENCAISSE'), t('DIRECTION.KPI.RESTE'), t('DIRECTION.KPI.TAUX')],
      rows: this.displayCentres().map((c) => [c.nom, this.headcount(c.patients), this.headcount(c.patientsSousKt),
        c.seances, c.factures, money(c.caHt), money(c.caTtc), money(c.encaisse), money(c.resteARecouvrer),
        c.tauxEncaissement === null ? '' : c.tauxEncaissement]),
    });

    sections.push({
      title: t('DIRECTION.CLINICAL.TITLE'),
      headers: [t('DIRECTION.COL.CENTRE'), t('DIRECTION.CLINICAL.KTV'), t('DIRECTION.CLINICAL.HB'),
        t('DIRECTION.CLINICAL.PHOSPHORE'), t('DIRECTION.CLINICAL.PTH'), t('DIRECTION.CLINICAL.ALBUMINE'),
        t('DIRECTION.CLINICAL.VHB'), t('DIRECTION.CLINICAL.VHC'), t('DIRECTION.CLINICAL.VIH'),
        t('DIRECTION.CLINICAL.OBSERVANCE'), t('DIRECTION.CLINICAL.GREFFE_ATTENTE'), t('DIRECTION.CLINICAL.GREFFES')],
      rows: this.indicatorRows().map((row) => [row.total ? t('DIRECTION.TOTAL') : row.c.nom,
        this.pct(row.c.clinique.ktV.pctDansCible), this.pct(row.c.clinique.hemoglobine.pctDansCible),
        this.pct(row.c.clinique.phosphore.pctDansCible), this.pct(row.c.clinique.pth.pctDansCible),
        this.pct(row.c.clinique.albumine.pctDansCible), this.headcount(row.c.clinique.vhbPositifs),
        this.headcount(row.c.clinique.vhcPositifs), this.headcount(row.c.clinique.vihPositifs),
        this.headcount(row.c.clinique.patientsObservanceEnRetard), this.headcount(row.c.clinique.greffeListeAttente),
        this.headcount(row.c.clinique.greffesPeriode)]),
    });

    sections.push({
      title: t('DIRECTION.STOCK.TITLE'),
      headers: [t('DIRECTION.COL.CENTRE'), t('DIRECTION.STOCK.ARTICLES'), t('DIRECTION.STOCK.SOUS_SEUIL'),
        t('DIRECTION.STOCK.PERIMES'), t('DIRECTION.STOCK.PEREMPTION_PROCHE'), t('DIRECTION.STOCK.VALEUR')],
      rows: this.indicatorRows().map((row) => [row.total ? t('DIRECTION.TOTAL') : row.c.nom,
        row.c.stock.articlesActifs, row.c.stock.articlesSousSeuil, row.c.stock.lotsPerimes,
        row.c.stock.lotsPeremptionProche, money(row.c.stock.valeurStock)]),
    });

    const b = this.breakdown();
    if (b) {
      sections.push({
        title: t('DIRECTION.BREAKDOWN.SEXE_TITLE'),
        headers: [t('DIRECTION.COL.CENTRE'), t('DIRECTION.BREAKDOWN.MASCULIN'), t('DIRECTION.BREAKDOWN.FEMININ'),
          t('DIRECTION.BREAKDOWN.AUTRE')],
        rows: b.sexe.map((r) => [r.nom, this.headcount(r.masculin), this.headcount(r.feminin), this.headcount(r.autre)]),
      });
      sections.push({
        title: t('DIRECTION.BREAKDOWN.AGE_TITLE'),
        headers: [t('DIRECTION.COL.CENTRE'), ...this.ageCodes.map((code) => t(`DIRECTION.BREAKDOWN.AGE.${code}`))],
        rows: b.ages.map((r) => [r.nom, ...this.ageCodes.map((code) =>
          this.headcount(r.tranches.find((tr) => tr.code === code)?.count ?? null))]),
      });
      sections.push({
        title: t('DIRECTION.BREAKDOWN.CAISSES_TITLE') + ' — ' + t(`DIRECTION.BREAKDOWN.METRIC_${this.caisseMetric().toUpperCase()}`),
        headers: [t('DIRECTION.BREAKDOWN.CAISSE'), ...this.pivotCentres().map((c) => c.nom), t('DIRECTION.TOTAL')],
        rows: this.caissePivot().map((line) => [this.caisseLabel(line.nom),
          ...this.pivotCentres().map((c) => this.caisseCell(line.cells[c.id])),
          this.caisseCell(line.total)]),
      });
      sections.push({
        title: t('DIRECTION.BREAKDOWN.ANEMIE_TITLE'),
        headers: [t('DIRECTION.COL.CENTRE'), t('DIRECTION.BREAKDOWN.SOUS_EPO'), t('DIRECTION.BREAKDOWN.SOUS_FER'),
          t('DIRECTION.BREAKDOWN.PAT_EPO'), t('DIRECTION.BREAKDOWN.PAT_FER'), t('DIRECTION.BREAKDOWN.ADM_EPO'),
          t('DIRECTION.BREAKDOWN.ADM_FER'), t('DIRECTION.BREAKDOWN.NON_ADM'), t('DIRECTION.BREAKDOWN.TAUX_ADM')],
        rows: b.anemie.map((r) => [r.nom, this.headcount(r.patientsSousEpo), this.headcount(r.patientsSousFer),
          this.headcount(r.patientsEpo), this.headcount(r.patientsFer), r.administreesEpo, r.administreesFer,
          r.nonAdministrees, this.pct(r.tauxAdministration)]),
      });
    }

    const csv = '﻿' + buildDashboardCsv(sections);
    const blob = new Blob([csv], {type: 'text/csv;charset=utf-8'});
    this.save(blob, `tableau-de-bord-direction-${this.store.from()}_${this.store.to()}.csv`);
  }

  /** Raccourci de période (ce mois, ce trimestre, cette année, 12 derniers mois). */
  protected applyPreset(preset: PeriodPreset): void {
    const {from, to} = periodForPreset(preset, new Date());
    this.period.set({from, to});
    void this.store.load(from, to);
  }

  // ───────────────────────────── Répartitions par centre ─────────────────────────────
  protected readonly caisseMetrics: CaisseMetric[] = ['patients', 'seances', 'caHt'];
  protected readonly caisseMetric = signal<CaisseMetric>('caHt');

  /** Formate un delta de KPI : « +4,2 % » / « -1,0 % », ou rien si non comparable (période précédente à zéro). */
  protected formatDelta(d: Delta | undefined): string {
    if (!d || d.pct === null) return '';
    const sign = d.pct > 0 ? '+' : '';
    return `${sign}${d.pct.toFixed(1)} %`;
  }
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

  private caisseCell(value: number | null): number | string {
    return this.caisseMetric() === 'caHt' ? (value === null ? '' : Math.round(value * 100) / 100)
      : this.caisseMetric() === 'patients' ? this.headcount(value) : (value ?? 0);
  }

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

  /** Niveau d'un marqueur KDIGO (part des patients dans la cible) : vert/orange/rouge/neutre. */
  protected kdigo(pctDansCible: number | null): string {
    return kdigoLevel(pctDansCible);
  }
}
