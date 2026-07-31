import {computed, inject} from '@angular/core';
import {patchState, signalStore, withComputed, withMethods, withState} from '@ngrx/signals';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {finalize} from 'rxjs/operators';
import {BackendApiService} from '../../../core/api/backend-api.service';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {WsEvent} from '../../../core/ws/websocket.service';
import {DashboardStats, EMPTY_DASHBOARD_STATS} from './dashboard.types';

type DashboardState = {
  loading: boolean;
  expirationDays: number;
  selectedMonth: string | null;
  stats: DashboardStats;
  lastRefreshReason: string;
};

const initialState: DashboardState = {
  loading: true,
  expirationDays: 30,
  selectedMonth: null,
  stats: EMPTY_DASHBOARD_STATS,
  lastRefreshReason: 'init'
};

export const DashboardStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withDevtools('DashboardStore'),
  withComputed((store) => ({
    canLoad: computed(() => store.expirationDays() > 0)
  })),
  withMethods((store) => {
    const api = inject(BackendApiService);
    const appShell = inject(AppShellStore);

    const loadStats = (reason: string): void => {
      const centerId = appShell.currentCenterId();
      if (!centerId || !store.canLoad()) return;
      patchState(store, {loading: true, lastRefreshReason: reason});
      api.getDashboardStats(centerId, store.expirationDays(), store.selectedMonth()).pipe(
        finalize(() => patchState(store, {loading: false}))
      ).subscribe({
        next: (stats) => patchState(store, {stats}),
        error: () => {
          // Keep last stats on transient failures.
        }
      });
    };

    return {
      setExpirationDays(expirationDays: number): void {
        const safeDays = Number.isFinite(expirationDays) && expirationDays > 0
          ? Math.trunc(expirationDays)
          : 1;
        patchState(store, {expirationDays: safeDays});
        loadStats('expiration-days-changed');
      },

      setSelectedMonth(month: string | null): void {
        const normalized = month && month.trim().length > 0 ? month.trim() : null;
        patchState(store, {selectedMonth: normalized});
        loadStats('month-changed');
      },

      loadInitial(): void {
        loadStats('init');
      },

      applyWsEvent(event: WsEvent | null): void {
        if (!event) return;
        if (event.type.startsWith('PATIENT_')) {
          loadStats('ws-patient');
          return;
        }
        if (event.type.startsWith('PEC_')) {
          loadStats('ws-pec');
          return;
        }
        if (event.type.startsWith('ATTESTATION_')) {
          loadStats('ws-attestation');
        }
      }
    };
  })
);
