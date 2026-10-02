import {computed, inject} from '@angular/core';
import {patchState, signalStore, withComputed, withMethods, withState} from '@ngrx/signals';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {firstValueFrom} from 'rxjs';
import {
  AlertHistoryEntry,
  DirectionApiService,
  DirectionBreakdown,
  DirectionIndicators,
  DirectionOverview,
  AbsencesOverview,
  GmaoOverview,
  StockGroupesOverview,
  Snapshot,
  SnapshotInfo
} from '../../../core/api/direction-api.service';
import {monthlyTotals, rankByRevenue, sortAlerts} from '../direction.util';

type DirectionState = {
  overview: DirectionOverview | null;
  indicators: DirectionIndicators | null;
  /** Répartitions par centre : sexe, âge, caisse d'assurance, anémie. */
  breakdown: DirectionBreakdown | null;
  /** Aide à la décision GMAO : coût de maintenance, état du parc, indisponibilité (module GMAO v2). */
  gmao: GmaoOverview | null;
  /** Valorisation du stock des groupes d'articles (ex. « KIT CNAS ») sur la période. */
  stockGroupes: StockGroupesOverview | null;
  /** Absences de patients : motifs, valorisation HT, taux d'absentéisme et part du CA HT. */
  absences: AbsencesOverview | null;
  /** Dernière mise à jour des données (chargement ou changement reçu en temps réel). */
  updatedAt: string | null;
  /** Mois déjà figés (instantanés mensuels), du plus récent au plus ancien. */
  snapshots: SnapshotInfo[];
  /** Mois dont le rapport est en cours de production. */
  reportBusy: string | null;
  reportError: boolean;
  from: string;
  to: string;
  loading: boolean;
  /** Code d'erreur stable renvoyé par le serveur (ex. PERIODE_INVALIDE) ou LOAD_ERROR. */
  error: string | null;
  /** Comparateur de deux mois figés : contenu chargé pour chaque emplacement, `undefined` = non demandé. */
  compareA: Snapshot | null | undefined;
  compareB: Snapshot | null | undefined;
  compareBusy: boolean;
  compareError: boolean;
  /** Historique récent des alertes (apparitions/résolutions), le plus récent d'abord. */
  alertHistory: AlertHistoryEntry[];
};

const initialState: DirectionState = {
  overview: null,
  indicators: null,
  breakdown: null,
  gmao: null,
  stockGroupes: null,
  absences: null,
  updatedAt: null,
  snapshots: [],
  reportBusy: null,
  reportError: false, from: '', to: '', loading: false, error: null,
  compareA: undefined, compareB: undefined, compareBusy: false, compareError: false, alertHistory: [],
};

/** Valeur de `reportBusy` pendant la production du rapport de la période affichée (aucun mois ne peut la prendre). */
export const LIVE_REPORT = 'live';

/** Numéro de la dernière requête lancée : une réponse plus ancienne ne remplace jamais une plus récente. */
let requestSeq = 0;

/** Vue consolidée de la société de la direction connectée (le serveur fixe le périmètre depuis la session). */
export const DirectionStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withDevtools('DirectionStore'),
  withComputed((store) => ({
    rankedCentres: computed(() => rankByRevenue(store.overview()?.centres ?? [])),
    months: computed(() => monthlyTotals(store.overview()?.mensuel ?? [])),
    alerts: computed(() => sortAlerts([
      ...(store.indicators()?.alertes ?? []),
      ...(store.gmao()?.alertes ?? []),
    ])),
  })),
  withMethods((store, api = inject(DirectionApiService)) => ({
    /** @param silent rechargement en arrière-plan (temps réel) : ni indicateur de chargement ni erreur affichée */
    async load(from = store.from(), to = store.to(), silent = false): Promise<void> {
      const seq = ++requestSeq;
      patchState(store, silent ? {} : {loading: true, error: null, from, to});
      try {
        const [overview, indicators, breakdown, gmao, stockGroupes, absences] = await Promise.all([
          firstValueFrom(api.overview(from || undefined, to || undefined)),
          firstValueFrom(api.indicators(from || undefined, to || undefined)),
          firstValueFrom(api.breakdown(from || undefined, to || undefined)),
          firstValueFrom(api.gmao(from || undefined, to || undefined)),
          firstValueFrom(api.stockGroupes(from || undefined, to || undefined)),
          firstValueFrom(api.absences(from || undefined, to || undefined)),
        ]);
        if (seq !== requestSeq) return;
        patchState(store, {
          overview,
          indicators,
          breakdown,
          gmao,
          stockGroupes,
          absences,
          loading: false,
          error: null,
          updatedAt: new Date().toISOString(),
          from: from || overview.from, to: to || overview.to,
        });
      } catch (e) {
        if (seq !== requestSeq || silent) return;
        const code = (e as { error?: { code?: string } })?.error?.code;
        patchState(store, {loading: false, error: code ?? 'LOAD_ERROR'});
      }
    },

    /** Relit toutes les données de la période affichée, en arrière-plan (changement reçu en temps réel). */
    async refresh(): Promise<void> {
      await this.load(store.from(), store.to(), true);
      void this.loadAlertHistory();
    },

    async loadSnapshots(): Promise<void> {
      try {
        patchState(store, {snapshots: await firstValueFrom(api.listSnapshots())});
      } catch {
        patchState(store, {snapshots: []});
      }
    },

    /** Fige le mois s'il ne l'est pas encore puis renvoie le rapport PDF ; `null` en cas d'échec. */
    async report(mois: string): Promise<Blob | null> {
      patchState(store, {reportBusy: mois, reportError: false});
      try {
        await firstValueFrom(api.ensureSnapshot(mois));
        const blob = await firstValueFrom(api.downloadReport(mois));
        patchState(store, {snapshots: await firstValueFrom(api.listSnapshots()), reportBusy: null});
        return blob;
      } catch {
        patchState(store, {reportBusy: null, reportError: true});
        return null;
      }
    },

    /** Rapport PDF de la période affichée ; `null` en cas d'échec. */
    async liveReport(): Promise<Blob | null> {
      patchState(store, {reportBusy: LIVE_REPORT, reportError: false});
      try {
        const blob = await firstValueFrom(api.downloadLiveReport(store.from() || undefined, store.to() || undefined));
        patchState(store, {reportBusy: null});
        return blob;
      } catch (e) {
        // eslint-disable-next-line no-console -- diagnostic : sinon l'échec du téléchargement est totalement silencieux
        console.error('Rapport de direction : échec du téléchargement', e);
        patchState(store, {reportBusy: null, reportError: true});
        return null;
      }
    },

    /** Charge le contenu d'un mois figé dans l'emplacement `a` ou `b` du comparateur ; `null` en cas d'échec. */
    async loadCompare(slot: 'a' | 'b', mois: string): Promise<void> {
      patchState(store, {compareBusy: true, compareError: false});
      try {
        const snapshot = await firstValueFrom(api.getSnapshot(mois));
        patchState(store, slot === 'a' ? {compareA: snapshot, compareBusy: false}
          : {compareB: snapshot, compareBusy: false});
      } catch {
        patchState(store, slot === 'a' ? {compareA: null, compareBusy: false, compareError: true}
          : {compareB: null, compareBusy: false, compareError: true});
      }
    },

    clearCompare(): void {
      patchState(store, {compareA: undefined, compareB: undefined, compareError: false});
    },

    async loadAlertHistory(): Promise<void> {
      try {
        patchState(store, {alertHistory: await firstValueFrom(api.alertsHistory())});
      } catch {
        patchState(store, {alertHistory: []});
      }
    },
  })),
);
