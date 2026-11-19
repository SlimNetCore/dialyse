import {inject} from '@angular/core';
import {patchState, signalStore, withMethods, withState} from '@ngrx/signals';
import {rxMethod} from '@ngrx/signals/rxjs-interop';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {catchError, EMPTY, forkJoin, map, pipe, switchMap, tap} from 'rxjs';
import {BackendApiService, FacturationDashboardResponse} from '../../../core/api/backend-api.service';
import {facturationErrorState} from '../state/facturation-errors';

/** Point de la courbe du chiffre d'affaires (un mois AAAA-MM). */
export type FacturationRevenuePoint = {
  month: string;
  revenueHt: number;
  revenueTtc: number;
  billedSeances: number;
  createdInvoices: number;
};

/** Nombre de mois de la courbe d'évolution du chiffre d'affaires (mois choisi inclus). */
export const MOIS_DE_LA_COURBE = 6;

export type FacturationTableauDeBordState = {
  /** Mois affiché (AAAA-MM), propre au tableau de bord : indépendant du mois de l'écran de calcul. */
  mois: string;
  dashboardLoading: boolean;
  dashboard: FacturationDashboardResponse | null;
  revenueTrendLoading: boolean;
  revenueTrend: FacturationRevenuePoint[];
  error: string | null;
  errorParams: Record<string, unknown> | null;
};

const initialState: FacturationTableauDeBordState = {
  mois: moisCourant(),
  dashboardLoading: false,
  dashboard: null,
  revenueTrendLoading: false,
  revenueTrend: [],
  error: null,
  errorParams: null,
};

/**
 * Tableau de bord de la facturation : indicateurs du mois (CA HT/TTC, séances et patients facturés, factures),
 * courbe du CA sur six mois, répartitions par caisse et par statut patient. Découpé du magasin de l'écran de calcul ;
 * fourni par la page du tableau de bord (un état par ouverture de la page).
 * Traçabilité : FacturationTableauDeBordComponent → ce store → BackendApiService.getFacturationDashboard
 * → GET /api/v1/facturation/dashboard.
 */
export const FacturationTableauDeBordStore = signalStore(
  withState(initialState),
  withDevtools('FacturationTableauDeBordStore'),
  withMethods((store, api = inject(BackendApiService)) => ({
    setMois(mois: string): void {
      patchState(store, {mois});
    },

    loadDashboard: rxMethod<{ centerId: string; month: string }>(
      pipe(
        tap(() => patchState(store, {dashboardLoading: true, error: null, errorParams: null})),
        switchMap(({centerId, month}) =>
          api.getFacturationDashboard(centerId, month).pipe(
            tap((dashboard) => patchState(store, {dashboard, dashboardLoading: false})),
            catchError((err: unknown) => {
              patchState(store, {dashboardLoading: false, dashboard: null, ...facturationErrorState(err)});
              return EMPTY;
            })
          )
        )
      )
    ),

    loadRevenueTrend: rxMethod<{ centerId: string; endingMonth: string; months: number }>(
      pipe(
        tap(() => patchState(store, {revenueTrendLoading: true})),
        switchMap(({centerId, endingMonth, months}) =>
          forkJoin(
            moisDeLaCourbe(endingMonth, months).map((month) =>
              api.getFacturationDashboard(centerId, month).pipe(
                map((dashboard) => ({
                  month,
                  revenueHt: dashboard.revenueHt,
                  revenueTtc: dashboard.revenueTtc,
                  billedSeances: dashboard.billedSeances,
                  createdInvoices: dashboard.createdInvoices,
                }))
              )
            )
          ).pipe(
            tap((revenueTrend) => patchState(store, {revenueTrend, revenueTrendLoading: false})),
            catchError(() => {
              patchState(store, {revenueTrendLoading: false, revenueTrend: []});
              return EMPTY;
            })
          )
        )
      )
    ),
  }))
);

function moisCourant(): string {
  return new Date().toISOString().slice(0, 7);
}

/** Les `months` mois (AAAA-MM) se terminant par `endingMonth`, du plus ancien au plus récent. */
export function moisDeLaCourbe(endingMonth: string, months: number): string[] {
  if (!/^\d{4}-\d{2}$/.test(endingMonth) || months <= 0) {
    return [moisCourant()];
  }
  const [year, month] = endingMonth.split('-').map((value) => Number(value));
  const result: string[] = [];
  for (let i = months - 1; i >= 0; i -= 1) {
    const d = new Date(Date.UTC(year, month - 1 - i, 1));
    result.push(`${d.getUTCFullYear()}-${`${d.getUTCMonth() + 1}`.padStart(2, '0')}`);
  }
  return result;
}
