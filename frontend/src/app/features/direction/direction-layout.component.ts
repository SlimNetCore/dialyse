import {DatePipe} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  effect,
  inject,
  OnInit,
  signal,
  untracked
} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatSelectModule} from '@angular/material/select';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {ActivatedRoute, NavigationEnd, Params, Router, RouterOutlet} from '@angular/router';
import {TranslateModule} from '@ngx-translate/core';
import {compatForm} from '@angular/forms/signals/compat';
import {FormField, FormRoot} from '@angular/forms/signals';
import {filter} from 'rxjs/operators';
import {LIVE_REPORT} from './state/direction.store';
import {DirectionRealtimeService} from './state/direction-realtime.service';
import {DirectionViewBase} from './direction-view.base';
import {buildDashboardCsv, CsvSection, PeriodPreset, periodForPreset} from './direction.util';
import {isDirectionArea} from '../../core/auth/role-scope.guard';

/** Date ISO (aaaa-mm-jj) acceptée dans les paramètres d'URL `from` / `to`. */
const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

/**
 * Paramètres d'URL du tableau de bord de la direction pour une période et un centre : `from`, `to` et `centre` (absent
 * pour la vue consolidée). Les autres paramètres déjà présents sont conservés. Aucune donnée personnelle : une période
 * et l'identifiant technique d'un centre.
 */
export function directionQueryParams(current: Params, from: string, to: string, centre: string | null): Params {
  const next: Params = {...current, from, to};
  if (centre) {
    next['centre'] = centre;
  } else {
    delete next['centre'];
  }
  return next;
}

/**
 * Page parente du tableau de bord consolidé de la direction (/direction) : barre de filtres période et centre commune
 * aux trois pages (vue d'ensemble, analyses, rapports), impression du rapport et export CSV, mention d'anonymat et
 * indicateur temps réel. La période et le centre sont reflétés dans l'URL (`?from=&to=&centre=`), relus à l'ouverture
 * et conservés quand on passe d'une page à l'autre.
 */
@Component({
  selector: 'app-direction-layout',
  standalone: true,
  imports: [
    DatePipe, TranslateModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule,
    MatProgressBarModule, MatSelectModule, FormRoot, FormField, RouterOutlet,
  ],
  templateUrl: './direction-layout.component.html',
  styleUrl: './direction.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DirectionLayoutComponent extends DirectionViewBase implements OnInit {
  protected readonly realtime = inject(DirectionRealtimeService);
  protected readonly period = signal({from: '', to: ''});
  protected readonly periodForm = compatForm(this.period);
  protected readonly canApply = computed(() => !!this.period().from && !!this.period().to && !this.store.loading());
  protected readonly centreOptions = computed(() =>
    (this.store.overview()?.centres ?? []).map((c) => ({id: c.centerId, nom: c.nom})));
  protected readonly errorKey = computed(() => {
    const code = this.store.error();
    return code === 'PERIODE_INVALIDE' || code === 'PERIODE_TROP_LONGUE' ? `DIRECTION.ERROR.${code}` : 'DIRECTION.ERROR.LOAD_ERROR';
  });
  protected readonly periodPresets: PeriodPreset[] = ['MONTH', 'QUARTER', 'YEAR', 'LAST_12_MONTHS'];
  protected readonly liveReport = LIVE_REPORT;
  /** Date de référence des raccourcis de période, fixée à l'ouverture de l'écran. */
  private readonly today = new Date();
  /** Période affichée = raccourci actif (ce mois, ce trimestre…), `null` pour une période personnalisée. */
  protected readonly activePreset = computed<PeriodPreset | null>(() => {
    const from = this.store.from();
    const to = this.store.to();
    return this.periodPresets.find((p) => {
      const range = periodForPreset(p, this.today);
      return range.from === from && range.to === to;
    }) ?? null;
  });
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  constructor() {
    super();
    // Aligne les champs de période sur celle réellement renvoyée par le serveur (période par défaut incluse).
    // Dépend de la période du store (et non des données) : une relecture en temps réel n'écrase pas une saisie en cours.
    effect(() => {
      const from = this.store.from();
      const to = this.store.to();
      if (from && to) {
        untracked(() => this.period.set({from, to}));
      }
    });
    // Reflète la période et le centre dans l'URL à chaque changement de filtre.
    effect(() => {
      this.store.from();
      this.store.to();
      this.selectedCentre();
      untracked(() => this.syncUrl());
    });
  }

  ngOnInit(): void {
    // Période et centre repris de l'URL (lien partagé, retour sur la page) ; sinon le mois en cours (bouton « Ce mois »).
    const query = this.route.snapshot.queryParamMap;
    const qFrom = query.get('from');
    const qTo = query.get('to');
    const {from, to} = qFrom && qTo && ISO_DATE.test(qFrom) && ISO_DATE.test(qTo)
      ? {from: qFrom, to: qTo}
      : periodForPreset('MONTH', this.today);
    this.selectedCentre.set(query.get('centre') || null);
    void this.store.load(from, to);
    void this.store.loadSnapshots();
    void this.store.loadAlertHistory();
    // Les entrées de la barre de module mènent aux pages sans paramètres : on y reporte la période et le centre.
    this.router.events
      .pipe(filter((e) => e instanceof NavigationEnd), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.syncUrl());
  }

  /** Enregistre le rapport PDF de la période affichée (en-tête, pied de page et toutes les statistiques). */
  protected async printReport(): Promise<void> {
    this.save(await this.store.liveReport(), `rapport-direction-${this.store.from()}_${this.store.to()}.pdf`);
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

  /** Raccourci de période (ce mois, ce trimestre, cette année, 12 derniers mois). */
  protected applyPreset(preset: PeriodPreset): void {
    const {from, to} = periodForPreset(preset, this.today);
    this.period.set({from, to});
    void this.store.load(from, to);
  }

  /**
   * Exporte en CSV (Excel) les tableaux de la période et du centre affichés — finances par centre, qualité des soins,
   * stock, répartitions — quelle que soit la page ouverte (les données viennent du store, pas de la page rendue).
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

  /**
   * Remplace les paramètres de l'URL courante par la période et le centre affichés, sans nouvelle entrée d'historique.
   * Rien à faire tant que la période n'est pas connue, hors de la zone direction (navigation en cours depuis une autre
   * page) ou quand l'URL est déjà à jour.
   */
  private syncUrl(): void {
    const from = this.store.from();
    const to = this.store.to();
    const url = this.router.url;
    if (!from || !to || !isDirectionArea(url)) return;
    const tree = this.router.parseUrl(url);
    const centre = this.selectedCentre();
    const current = tree.queryParams;
    if (current['from'] === from && current['to'] === to && (current['centre'] ?? null) === centre) return;
    tree.queryParams = directionQueryParams(current, from, to, centre);
    void this.router.navigateByUrl(tree, {replaceUrl: true});
  }
}
