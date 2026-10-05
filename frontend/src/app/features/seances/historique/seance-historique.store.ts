import {inject} from '@angular/core';
import {patchState, signalStore, withMethods, withState} from '@ngrx/signals';
import {rxMethod} from '@ngrx/signals/rxjs-interop';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {catchError, EMPTY, pipe, switchMap, tap} from 'rxjs';
import {BackendApiService, SeanceListItem} from '../../../core/api/backend-api.service';
import {toSeanceHistoryQuery} from './seance-historique.util';

type SortDirection = 'asc' | 'desc' | '';

export type SeanceHistoriqueState = {
  rows: SeanceListItem[];
  total: number;
  pageIndex: number;
  pageSize: number;
  loading: boolean;
  error: string | null;
  filters: Record<string, string>;
  /** Période choisie hors tableau (dates ISO, vide = sans borne). */
  periodFrom: string;
  periodTo: string;
  sortColumnId: string | null;
  sortDirection: SortDirection;
};

const initialState: SeanceHistoriqueState = {
  rows: [],
  total: 0,
  pageIndex: 0,
  pageSize: 20,
  loading: false,
  error: null,
  filters: {},
  periodFrom: '',
  periodTo: '',
  sortColumnId: null,
  sortDirection: '',
};

/**
 * Historique des séances : la recherche, le filtre, le tri et la pagination sont faits par le serveur (jamais sur la
 * seule page affichée). Tout changement de tri ou de filtre revient à la première page.
 * Traçabilité : SeancesHistoriqueComponent → ce store → BackendApiService.listSeances → GET /api/v1/seances.
 */
export const SeanceHistoriqueStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withDevtools('SeanceHistoriqueStore'),
  withMethods((store, api = inject(BackendApiService)) => {
    const load = rxMethod<string>(
      pipe(
        tap(() => patchState(store, {loading: true, error: null})),
        switchMap((centerId) =>
          api.listSeances(
            centerId,
            store.pageIndex(),
            store.pageSize(),
            toSeanceHistoryQuery(store.filters(), store.sortColumnId(), store.sortDirection(),
              {from: store.periodFrom(), to: store.periodTo()}),
          ).pipe(
            tap((res) => patchState(store, {rows: res.items, total: res.total, loading: false})),
            catchError(() => {
              patchState(store, {rows: [], total: 0, loading: false, error: 'SEANCES.HISTORY_LOAD_ERROR'});
              return EMPTY;
            }),
          ),
        ),
      ),
    );
    return {
      load,
      /** Nouveau tri et/ou nouveaux filtres : retour à la première page puis rechargement. */
      applyQuery(centerId: string, query: {
        filters: Record<string, string>;
        sortColumnId: string | null;
        sortDirection: SortDirection
      }): void {
        patchState(store, {
          filters: query.filters,
          sortColumnId: query.sortColumnId,
          sortDirection: query.sortDirection,
          pageIndex: 0,
        });
        load(centerId);
      },
      /** Nouvelle période (dates ISO, vide = sans borne) : retour à la première page puis rechargement. */
      setPeriod(centerId: string, periodFrom: string, periodTo: string): void {
        patchState(store, {periodFrom, periodTo, pageIndex: 0});
        load(centerId);
      },
      setPage(centerId: string, pageIndex: number, pageSize: number): void {
        patchState(store, {pageIndex, pageSize});
        load(centerId);
      },
      reload(centerId: string): void {
        load(centerId);
      },
    };
  }),
);
