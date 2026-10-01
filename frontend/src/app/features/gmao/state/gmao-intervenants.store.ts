import {computed, inject} from '@angular/core';
import {patchState, signalStore, withComputed, withMethods, withState} from '@ngrx/signals';
import {rxMethod} from '@ngrx/signals/rxjs-interop';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {catchError, EMPTY, pipe, switchMap, tap} from 'rxjs';
import {GmaoApiService, Intervenant, IntervenantPayload} from '../../../core/api/gmao-api.service';
import {createPagedListState, PagedListState} from '../../../core/state/paged-list-state.util';

type GmaoIntervenantsState = PagedListState<Intervenant> & {
  saving: boolean;
  successMessage: string | null;
  error: string | null;
};

const initialState: GmaoIntervenantsState = {
  ...createPagedListState<Intervenant>(),
  saving: false,
  successMessage: null,
  error: null,
};

export const GmaoIntervenantsStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withDevtools('GmaoIntervenantsStore'),
  withComputed((store) => ({
    isEmpty: computed(() => store.rows().length === 0),
  })),
  withMethods((store, api = inject(GmaoApiService)) => ({
    loadPage: rxMethod<{ page: number; size: number }>(
      pipe(
        tap(({page, size}) => patchState(store, {loading: true, error: null, pageIndex: page, pageSize: size})),
        switchMap(({page, size}) =>
          api.listIntervenants(page, size).pipe(
            tap((res) => patchState(store, {
              rows: res.items ?? [],
              total: res.total ?? 0,
              pageIndex: res.page ?? page,
              loading: false,
            })),
            catchError(() => {
              patchState(store, {rows: [], total: 0, loading: false, error: 'GMAO.INTERVENANTS.LOAD_ERROR'});
              return EMPTY;
            }),
          ),
        ),
      ),
    ),

    createIntervenant: rxMethod<IntervenantPayload>(
      pipe(
        tap(() => patchState(store, {saving: true, error: null, successMessage: null})),
        switchMap((payload) =>
          api.createIntervenant(payload).pipe(
            tap(() => patchState(store, {saving: false, successMessage: 'GMAO.INTERVENANTS.SAVED_OK'})),
            catchError(() => {
              patchState(store, {saving: false, error: 'GMAO.INTERVENANTS.SAVE_ERROR'});
              return EMPTY;
            }),
          ),
        ),
      ),
    ),

    updateIntervenant: rxMethod<{ id: string; payload: IntervenantPayload }>(
      pipe(
        tap(() => patchState(store, {saving: true, error: null, successMessage: null})),
        switchMap(({id, payload}) =>
          api.updateIntervenant(id, payload).pipe(
            tap(() => patchState(store, {saving: false, successMessage: 'GMAO.INTERVENANTS.SAVED_OK'})),
            catchError(() => {
              patchState(store, {saving: false, error: 'GMAO.INTERVENANTS.SAVE_ERROR'});
              return EMPTY;
            }),
          ),
        ),
      ),
    ),

    deactivateIntervenant: rxMethod<string>(
      pipe(
        tap(() => patchState(store, {saving: true, error: null, successMessage: null})),
        switchMap((id) =>
          api.deactivateIntervenant(id).pipe(
            tap(() => patchState(store, {saving: false, successMessage: 'GMAO.INTERVENANTS.DEACTIVATED'})),
            catchError(() => {
              patchState(store, {saving: false, error: 'GMAO.INTERVENANTS.SAVE_ERROR'});
              return EMPTY;
            }),
          ),
        ),
      ),
    ),

    setPagination(pageIndex: number, pageSize: number): void {
      patchState(store, {pageIndex, pageSize});
    },

    clearMessages(): void {
      patchState(store, {error: null, successMessage: null});
    },
  })),
);
