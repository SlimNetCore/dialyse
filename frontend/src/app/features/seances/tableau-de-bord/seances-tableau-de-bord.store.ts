import {computed, inject} from '@angular/core';
import {patchState, signalStore, withComputed, withMethods, withState} from '@ngrx/signals';
import {rxMethod} from '@ngrx/signals/rxjs-interop';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {catchError, EMPTY, pipe, switchMap, tap} from 'rxjs';
import {
  BackendApiService,
  SeanceDashboardDetailItem,
  SeanceDashboardDetailsResponse,
  SeanceMonthlyDashboard,
} from '../../../core/api/backend-api.service';
import {currentMonthIso} from '../historique/seance-historique.util';
import {errorMessage} from '../state/seance.store';

export type SeancesTableauDeBordState = {
  /** Mois affiché (AAAA-MM). */
  dashboardMonth: string;
  dashboardLoading: boolean;
  seanceDashboard: SeanceMonthlyDashboard | null;
  /** Détail des présences ou des absences du mois (popin paginée côté client). */
  dashboardDetailsOpen: boolean;
  dashboardDetailsKind: 'presence' | 'absence';
  dashboardDetailsLoading: boolean;
  dashboardDetailItems: SeanceDashboardDetailItem[];
  dashboardDetailPageIndex: number;
  /** Dernière erreur de chargement (message du serveur ou clé i18n). */
  error: string | null;
};

export const DASHBOARD_PAGE_SIZE = 10;

const initialState: SeancesTableauDeBordState = {
  dashboardMonth: currentMonthIso(),
  dashboardLoading: false,
  seanceDashboard: null,
  dashboardDetailsOpen: false,
  dashboardDetailsKind: 'presence',
  dashboardDetailsLoading: false,
  dashboardDetailItems: [],
  dashboardDetailPageIndex: 0,
  error: null,
};

/**
 * Tableau de bord des séances : statistiques mensuelles (prévues, présences, absences, total, répartitions) et détail
 * des présences/absences. Découpé du magasin du poste infirmier ; fourni par la page du tableau de bord (un état par
 * ouverture de la page).
 * Traçabilité : SeancesStatsComponent → ce store → BackendApiService.getSeanceMonthlyDashboard /
 * getSeanceDashboardDetails → GET /api/v1/seances/dashboard(/details).
 */
export const SeancesTableauDeBordStore = signalStore(
  withState(initialState),
  withDevtools('SeancesTableauDeBordStore'),
  withComputed((store) => ({
    dashboardDetailPageItems: computed(() => {
      const start = store.dashboardDetailPageIndex() * DASHBOARD_PAGE_SIZE;
      return store.dashboardDetailItems().slice(start, start + DASHBOARD_PAGE_SIZE);
    }),
    dashboardDetailTotalPages: computed(() =>
      Math.max(1, Math.ceil(store.dashboardDetailItems().length / DASHBOARD_PAGE_SIZE))
    ),
  })),
  withMethods((store, api = inject(BackendApiService)) => ({
    setDashboardMonth(dashboardMonth: string): void {
      patchState(store, {dashboardMonth});
    },
    loadDashboard: rxMethod<{ centerId: string; year: number; month: number }>(
      pipe(
        tap(() => patchState(store, {dashboardLoading: true, error: null})),
        switchMap(({centerId, year, month}) =>
          api.getSeanceMonthlyDashboard(centerId, year, month).pipe(
            tap((data) => patchState(store, {seanceDashboard: data, dashboardLoading: false})),
            catchError((err: unknown) => {
              patchState(store, {dashboardLoading: false, error: errorMessage(err)});
              return EMPTY;
            })
          )
        )
      )
    ),
    openDashboardDetails(kind: 'presence' | 'absence'): void {
      patchState(store, {
        dashboardDetailsOpen: true,
        dashboardDetailsKind: kind,
        dashboardDetailPageIndex: 0,
        dashboardDetailItems: []
      });
    },
    closeDashboardDetails(): void {
      patchState(store, {dashboardDetailsOpen: false});
    },
    loadDashboardDetails: rxMethod<{ centerId: string; year: number; month: number; kind: 'presence' | 'absence' }>(
      pipe(
        tap(() => patchState(store, {dashboardDetailsLoading: true, error: null})),
        switchMap(({centerId, year, month, kind}) =>
          api.getSeanceDashboardDetails(centerId, year, month, kind).pipe(
            tap((res: SeanceDashboardDetailsResponse) =>
              patchState(store, {dashboardDetailsLoading: false, dashboardDetailItems: res.items ?? []})
            ),
            catchError((err: unknown) => {
              patchState(store, {dashboardDetailsLoading: false, dashboardDetailItems: [], error: errorMessage(err)});
              return EMPTY;
            })
          )
        )
      )
    ),
    setDashboardDetailPage(dashboardDetailPageIndex: number): void {
      patchState(store, {dashboardDetailPageIndex});
    },
  })),
);
