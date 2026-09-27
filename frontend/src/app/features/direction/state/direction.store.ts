import {computed, inject} from '@angular/core';
import {patchState, signalStore, withComputed, withMethods, withState} from '@ngrx/signals';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {firstValueFrom} from 'rxjs';
import {
  DirectionApiService,
  DirectionBreakdown,
  DirectionIndicators,
  DirectionOverview,
  SnapshotInfo
} from '../../../core/api/direction-api.service';
import {monthlyTotals, rankByRevenue, sortAlerts} from '../direction.util';

type DirectionState = {
  overview: DirectionOverview | null;
  indicators: DirectionIndicators | null;
  /** Répartitions par centre : sexe, âge, caisse d'assurance, anémie. */
  breakdown: DirectionBreakdown | null;
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
};

const initialState: DirectionState = {
  overview: null, indicators: null, breakdown: null, updatedAt: null, snapshots: [], reportBusy: null,
  reportError: false, from: '', to: '', loading: false, error: null,
};

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
    alerts: computed(() => sortAlerts(store.indicators()?.alertes ?? [])),
  })),
  withMethods((store, api = inject(DirectionApiService)) => ({
    /** @param silent rechargement en arrière-plan (temps réel) : ni indicateur de chargement ni erreur affichée */
    async load(from = store.from(), to = store.to(), silent = false): Promise<void> {
      const seq = ++requestSeq;
      patchState(store, silent ? {} : {loading: true, error: null, from, to});
      try {
        const [overview, indicators, breakdown] = await Promise.all([
          firstValueFrom(api.overview(from || undefined, to || undefined)),
          firstValueFrom(api.indicators(from || undefined, to || undefined)),
          firstValueFrom(api.breakdown(from || undefined, to || undefined)),
        ]);
        if (seq !== requestSeq) return;
        patchState(store, {
          overview, indicators, breakdown, loading: false, error: null, updatedAt: new Date().toISOString(),
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
  })),
);
