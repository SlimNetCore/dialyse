import {computed, Directive, inject} from '@angular/core';
import {TranslateService} from '@ngx-translate/core';
import {Chart, ChartData, ChartOptions, registerables} from 'chart.js';
import {DirectionStore} from './state/direction.store';
import {DirectionFilters} from './state/direction-filters';
import {MotifAbsenceStat} from '../../core/api/direction-api.service';
import {
  capaciteExemple,
  capaciteHeadline,
  capaciteRows,
  jaugeCapacite,
  nbCentresAtteints,
  niveauClasse,
} from './direction-capacite.util';
import {
  absencesHeadline,
  absencesMotifs,
  absencesRows,
  motifLabelKey,
  motifShare,
  motifsChartSeries,
} from './direction-absences.util';
import {gmaoTopRows} from './direction-gmao.util';
import {stockGroupeRows} from './direction-stock-groupes.util';
import {
  collectionLevel,
  Delta,
  delta,
  filterByCentre,
  formatHeadcount,
  formatPct,
  kdigoLevel,
  monthlyTotals,
  rankIndex
} from './direction.util';
import {AGE_CODES, ageSeries, CaisseMetric, pivotCaisses, PivotCentre, sexeSeries} from './direction-breakdown.util';

Chart.register(...registerables);

/**
 * Vue partagée par les pages du tableau de bord de la direction (vue d'ensemble, analyses, rapports) et par leur page
 * parente (filtres, impression, export CSV) : agrégats anonymes par centre et pour la société, filtrés sur le centre
 * isolé par la direction (aucune donnée nominative ; les effectifs inférieurs au seuil d'anonymat sont masqués par le
 * serveur). Chaque page n'évalue que les valeurs que son gabarit lit.
 */
@Directive()
export abstract class DirectionViewBase {
  protected readonly store = inject(DirectionStore);
  protected readonly translate = inject(TranslateService);
  private readonly filters = inject(DirectionFilters);
  /** Centre isolé par la direction (`null` = tous les centres, vue consolidée), commun aux trois pages. */
  protected readonly selectedCentre = this.filters.centre;
  protected readonly threshold = computed(() => this.store.overview()?.seuilAnonymat ?? 5);
  /** Centres à afficher dans le comparatif : tous, ou seulement celui isolé par le filtre. */
  protected readonly displayCentres = computed(() =>
    filterByCentre(this.store.rankedCentres(), this.selectedCentre()));
  /** Alertes du centre isolé par le filtre, ou de tous les centres. */
  protected readonly displayAlerts = computed(() => filterByCentre(this.store.alerts(), this.selectedCentre()));
  /** Historique des alertes limité au centre isolé par le filtre, le cas échéant. */
  protected readonly displayAlertHistory = computed(() => filterByCentre(this.store.alertHistory(), this.selectedCentre()));
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
  /**
   * Lignes du tableau GMAO (aide à la décision) : un centre par ligne, puis le total de la société
   * (masqué quand un centre est isolé par le filtre) — même patron que {@code indicatorRows}.
   */
  protected readonly gmaoRows = computed(() => {
    const g = this.store.gmao();
    if (!g) return [];
    const sel = this.selectedCentre();
    const centres = sel ? g.centres.filter((c) => c.centerId === sel) : g.centres;
    const rows = centres.map((c) => ({c, total: false}));
    return sel ? rows : [...rows, {c: g.totaux, total: true}];
  });
  /** Classement des équipements les plus coûteux, tous centres confondus (ou du centre isolé), du plus coûteux au moins coûteux. */
  protected readonly gmaoTopRows = computed(() => gmaoTopRows(this.store.gmao(), this.selectedCentre()));
  /** Valorisation du stock des groupes d'articles : total consolidé puis détail par centre. */
  protected readonly stockGroupeRows = computed(() => stockGroupeRows(this.store.stockGroupes(), this.selectedCentre()));
  /** Absences de patients : un centre par ligne puis le total, indicateurs mis en avant et motifs. */
  protected readonly absencesRows = computed(() => absencesRows(this.store.absences(), this.selectedCentre()));
  protected readonly absencesHeadline = computed(() => absencesHeadline(this.store.absences(), this.selectedCentre()));
  /** Capacité théorique : un centre par ligne puis le total, capacité mise en avant et centres à saturation. */
  protected readonly capaciteRows = computed(() => capaciteRows(this.store.capacite(), this.selectedCentre()));
  protected readonly capaciteHeadline = computed(() => capaciteHeadline(this.store.capacite(), this.selectedCentre()));
  protected readonly nbCentresAtteints = computed(() => nbCentresAtteints(this.store.capacite()));
  protected readonly capaciteExemple = computed(() => capaciteExemple(this.store.capacite(), this.selectedCentre()));
  protected readonly jaugeCapacite = jaugeCapacite;
  protected readonly niveauClasse = niveauClasse;
  protected readonly absencesMotifs = computed(() => absencesMotifs(this.store.absences(), this.selectedCentre()));
  /** Répartition des absences par motif : une part par nombre d'absences, une autre par valorisation HT. */
  protected readonly motifsNbChart = computed(() => this.motifsChart('nb'));
  protected readonly motifsValeurChart = computed(() => this.motifsChart('valeur'));
  protected readonly doughnutOptions: ChartOptions<'doughnut'> = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {legend: {display: true, position: 'bottom'}},
  };
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

  // ───────────────────────────── Répartitions par centre ─────────────────────────────
  protected readonly caisseMetrics: CaisseMetric[] = ['patients', 'seances', 'caHt'];
  /** Mesure choisie par caisse, commune aux pages (l'export CSV reprend celle affichée dans les analyses). */
  protected readonly caisseMetric = this.filters.caisseMetric;
  protected readonly pivotCentres = computed<PivotCentre[]>(() =>
    (this.breakdown()?.sexe ?? []).map((r) => ({id: r.centerId, nom: r.nom})));
  protected readonly caissePivot = computed(() => {
    const b = this.breakdown();
    return b ? pivotCaisses(this.pivotCentres(), b.caisses, b.caisseTotaux, this.caisseMetric()) : [];
  });
  /** Mesure choisie par caisse (patients, séances ou CA HT), empilée par centre. */
  protected readonly caisseCaChart = computed<ChartData<'bar'>>(() => {
    const lines = pivotCaisses(this.pivotCentres(), this.breakdown()?.caisses ?? [], this.breakdown()?.caisseTotaux ?? [], this.caisseMetric());
    return {
      labels: lines.map((l) => l.nom || this.translate.instant('DIRECTION.BREAKDOWN.CAISSE_INCONNUE')),
      datasets: this.pivotCentres().map((c) => ({label: c.nom, data: lines.map((l) => l.cells[c.id] ?? 0)})),
    };
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
  /** Rang (1 = premier) de chaque centre par CA, calculé une fois sur la liste complète (stable même filtré). */
  private readonly rankByCentre = computed<Record<string, number>>(() => rankIndex(this.store.rankedCentres()));

  protected rankOf(centerId: string | null): number {
    return this.rankByCentre()[centerId ?? ''] ?? 0;
  }

  protected caShare(centerId: string | null): number {
    return this.caShares()[centerId ?? ''] ?? 0;
  }

  /** Formate un delta de KPI : « +4,2 % » / « -1,0 % », ou rien si non comparable (période précédente à zéro). */
  protected formatDelta(d: Delta | undefined): string {
    if (!d || d.pct === null) return '';
    const sign = d.pct > 0 ? '+' : '';
    return `${sign}${d.pct.toFixed(1)} %`;
  }

  protected caisseCell(value: number | null): number | string {
    return this.caisseMetric() === 'caHt' ? (value === null ? '' : Math.round(value * 100) / 100)
      : this.caisseMetric() === 'patients' ? this.headcount(value) : (value ?? 0);
  }

  protected caisseLabel(nom: string): string {
    return nom || this.translate.instant('DIRECTION.BREAKDOWN.CAISSE_INCONNUE');
  }

  protected pct(value: number | null): string {
    return formatPct(value);
  }

  protected motifPart(motif: MotifAbsenceStat): number | null {
    return motifShare(motif, this.absencesMotifs());
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

  /** Enregistre un fichier produit (rapport PDF, export CSV) sur le poste. */
  protected save(blob: Blob | null, filename: string): void {
    if (!blob) return;
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    link.click();
    URL.revokeObjectURL(url);
  }

  private motifsChart(by: 'nb' | 'valeur'): ChartData<'doughnut'> {
    const series = motifsChartSeries(this.absencesMotifs(), by);
    return {
      labels: series.map((p) => this.translate.instant(motifLabelKey(p.motif))),
      datasets: [{data: series.map((p) => p.value)}],
    };
  }
}
