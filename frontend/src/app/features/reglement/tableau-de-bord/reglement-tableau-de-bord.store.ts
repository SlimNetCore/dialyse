import {computed, inject} from '@angular/core';
import {patchState, signalStore, withComputed, withMethods, withState} from '@ngrx/signals';
import {rxMethod} from '@ngrx/signals/rxjs-interop';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {catchError, EMPTY, forkJoin, pipe, switchMap, tap} from 'rxjs';
import {BackendApiService, ReglementDashboardResponse} from '../../../core/api/backend-api.service';
import {ReferentialApiService, RefItem} from '../../../core/api/referential-api.service';
import {reglementErrorMessage} from '../state/reglement-payment.util';

export type ReglementTableauDeBordState = {
  /** Filtres propres au tableau de bord : indépendants de ceux de l'écran de saisie. */
  year: number;
  month: number | null;
  caisseId: string | null;
  agenceId: string | null;
  dashboardLoading: boolean;
  dashboard: ReglementDashboardResponse | null;
  referentialsLoading: boolean;
  caisses: RefItem[];
  agences: RefItem[];
  error: string | null;
};

const moisCourant = () => new Date().getMonth() + 1;

const initialState: ReglementTableauDeBordState = {
  year: new Date().getFullYear(),
  month: moisCourant(),
  caisseId: null,
  agenceId: null,
  dashboardLoading: false,
  dashboard: null,
  referentialsLoading: false,
  caisses: [],
  agences: [],
  error: null,
};

/**
 * Tableau de bord des règlements : 6 indicateurs (total, réglées, non réglées, partielles, trop-perçus, taux
 * d'encaissement), répartition des statuts et montants, filtrés par année / mois / caisse / agence. Découpé du magasin
 * de l'écran de saisie ; fourni par la page du tableau de bord (un état par ouverture de la page).
 * Traçabilité : ReglementTableauDeBordComponent → ce store → BackendApiService.getReglementDashboard
 * → GET /api/v1/reglements/dashboard.
 */
export const ReglementTableauDeBordStore = signalStore(
  withState(initialState),
  withDevtools('ReglementTableauDeBordStore'),
  withComputed((store) => ({
    dashboardCards: computed(() => {
      const dashboard = store.dashboard();
      if (!dashboard) {
        return {
          totalFactures: 0,
          reglees: 0,
          nonReglees: 0,
          partiellementReglees: 0,
          tropPercus: 0,
          totalFacture: 0,
          totalRegle: 0,
          totalReste: 0,
          totalTropPercu: 0,
          tauxEncaissement: 0,
        };
      }
      const totalFacture = Number(dashboard.totalFacture ?? 0);
      const totalRegle = Number(dashboard.totalRegle ?? 0);
      return {
        totalFactures: dashboard.totalFactures,
        reglees: dashboard.reglees,
        nonReglees: dashboard.nonReglees,
        partiellementReglees: dashboard.partiellementReglees,
        tropPercus: dashboard.tropPercus,
        totalFacture,
        totalRegle,
        totalReste: Number(dashboard.totalReste ?? 0),
        totalTropPercu: Number(dashboard.totalTropPercu ?? 0),
        tauxEncaissement: totalFacture > 0 ? totalRegle / totalFacture : 0,
      };
    }),
  })),
  withMethods((store, api = inject(BackendApiService), referentials = inject(ReferentialApiService)) => ({
    setYear(year: number): void {
      if (!Number.isFinite(year) || year < 2000 || year > 3000) return;
      patchState(store, {year});
    },
    setMonth(month: number | null): void {
      if (month !== null && (!Number.isFinite(month) || month < 1 || month > 12)) return;
      patchState(store, {month});
    },
    setCaisseId(caisseId: string | null): void {
      patchState(store, {caisseId: caisseId || null});
    },
    setAgenceId(agenceId: string | null): void {
      patchState(store, {agenceId: agenceId || null});
    },
    clearFilters(): void {
      patchState(store, {month: moisCourant(), caisseId: null, agenceId: null});
    },
    reset(): void {
      patchState(store, {dashboard: null, dashboardLoading: false, caisses: [], agences: [], referentialsLoading: false});
    },

    loadDashboard: rxMethod<{ centerId: string }>(
      pipe(
        tap(() => patchState(store, {dashboardLoading: true, error: null})),
        switchMap(({centerId}) =>
          api.getReglementDashboard(centerId, {
            year: store.year(),
            month: store.month(),
            caisseId: store.caisseId(),
            agenceId: store.agenceId(),
            centrePayeurId: null,
          }).pipe(
            tap((dashboard) => patchState(store, {dashboard, dashboardLoading: false})),
            catchError((err: unknown) => {
              patchState(store, {dashboardLoading: false, dashboard: null, error: reglementErrorMessage(err)});
              return EMPTY;
            })
          )
        )
      )
    ),

    loadReferentials: rxMethod<{ centerId: string }>(
      pipe(
        tap(() => patchState(store, {referentialsLoading: true})),
        switchMap(({centerId}) =>
          forkJoin({
            caisses: referentials.getCaisses(centerId),
            agences: referentials.getAgences(centerId),
          }).pipe(
            tap(({caisses, agences}) => patchState(store, {referentialsLoading: false, caisses, agences})),
            catchError(() => {
              patchState(store, {referentialsLoading: false, caisses: [], agences: []});
              return EMPTY;
            })
          )
        )
      )
    ),
  }))
);
