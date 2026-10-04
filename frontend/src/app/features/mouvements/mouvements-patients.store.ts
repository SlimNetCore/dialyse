import {computed, inject} from '@angular/core';
import {patchState, signalStore, withComputed, withMethods, withState} from '@ngrx/signals';
import {rxMethod} from '@ngrx/signals/rxjs-interop';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {catchError, EMPTY, pipe, switchMap, tap} from 'rxjs';
import {
  MouvementFilters,
  MouvementPatient,
  MouvementPatientApiService,
} from '../../core/api/mouvement-patient-api.service';
import {AppShellStore} from '../../core/state/app-shell.store';
import {createPagedListState, PagedListState} from '../../core/state/paged-list-state.util';

type MouvementsPatientsState = PagedListState<MouvementPatient> & {
  filters: MouvementFilters;
  error: string | null;
};

export const EMPTY_FILTERS: MouvementFilters = {type: '', from: '', to: ''};

const initialState: MouvementsPatientsState = {
  ...createPagedListState<MouvementPatient>({pageSize: 20}),
  filters: EMPTY_FILTERS,
  error: null,
};

/** Mouvements de patients du centre actif : liste paginée filtrable (lecture seule). */
export const MouvementsPatientsStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withDevtools('MouvementsPatientsStore'),
  withComputed((store) => ({
    isEmpty: computed(() => store.rows().length === 0),
  })),
  withMethods((store, api = inject(MouvementPatientApiService), shell = inject(AppShellStore)) => {
    const loadPage = rxMethod<{ page: number; size: number }>(
      pipe(
        tap(({page, size}) => patchState(store, {loading: true, error: null, pageIndex: page, pageSize: size})),
        switchMap(({page, size}) =>
          api.list(shell.currentCenterId() ?? '', store.filters(), page, size).pipe(
            tap((liste) => patchState(store, {
              rows: liste.items ?? [], total: liste.total ?? 0, pageIndex: liste.page ?? page, loading: false,
            })),
            catchError(() => {
              patchState(store, {rows: [], total: 0, loading: false, error: 'MOUVEMENTS.ERR_LOAD'});
              return EMPTY;
            }),
          ),
        ),
      ),
    );

    return {
      loadPage,

      applyFilters(filters: MouvementFilters): void {
        patchState(store, {filters});
        loadPage({page: 0, size: store.pageSize()});
      },
    };
  }),
);
