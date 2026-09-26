import {computed, inject} from '@angular/core';
import {patchState, signalStore, withComputed, withMethods, withState} from '@ngrx/signals';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {firstValueFrom} from 'rxjs';
import {
  DirectionApiService,
  DirectionIndicators,
  DirectionOverview,
  SnapshotInfo
} from '../../../core/api/direction-api.service';
import {monthlyTotals, rankByRevenue, sortAlerts} from '../direction.util';

type DirectionState = {
  overview: DirectionOverview | null;
  indicators: DirectionIndicators | null;
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
  overview: null, indicators: null, snapshots: [], reportBusy: null, reportError: false,
  from: '', to: '', loading: false, error: null,
};

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
    async load(from = store.from(), to = store.to()): Promise<void> {
      patchState(store, {loading: true, error: null, from, to});
      try {
        const [overview, indicators] = await Promise.all([
          firstValueFrom(api.overview(from || undefined, to || undefined)),
          firstValueFrom(api.indicators(from || undefined, to || undefined)),
        ]);
        patchState(store, {overview, indicators, loading: false, from: from || overview.from, to: to || overview.to});
      } catch (e) {
        const code = (e as { error?: { code?: string } })?.error?.code;
        patchState(store, {loading: false, error: code ?? 'LOAD_ERROR'});
      }
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
