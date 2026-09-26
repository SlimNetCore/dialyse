import {computed, inject} from '@angular/core';
import {patchState, signalStore, withComputed, withMethods, withState} from '@ngrx/signals';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {firstValueFrom} from 'rxjs';
import {DirectionApiService, DirectionIndicators, DirectionOverview} from '../../../core/api/direction-api.service';
import {monthlyTotals, rankByRevenue, sortAlerts} from '../direction.util';

type DirectionState = {
  overview: DirectionOverview | null;
  indicators: DirectionIndicators | null;
  from: string;
  to: string;
  loading: boolean;
  /** Code d'erreur stable renvoyé par le serveur (ex. PERIODE_INVALIDE) ou LOAD_ERROR. */
  error: string | null;
};

const initialState: DirectionState = {overview: null, indicators: null, from: '', to: '', loading: false, error: null};

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
  })),
);
