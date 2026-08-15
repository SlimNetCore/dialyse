import {computed, inject} from '@angular/core';
import {patchState, signalStore, withComputed, withMethods, withState} from '@ngrx/signals';
import {rxMethod} from '@ngrx/signals/rxjs-interop';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {catchError, EMPTY, pipe, switchMap, tap} from 'rxjs';
import {BackendApiService, TvaType, TvaTypePayload} from '../../../core/api/backend-api.service';
import {createPagedListState, PagedListState} from '../../../core/state/paged-list-state.util';

type TvaTypesState = PagedListState<TvaType> & {
  centerId: string | null;
  saving: boolean;
  deleting: boolean;
  successMessage: string | null;
  error: string | null;
};

const initialState: TvaTypesState = {
  ...createPagedListState<TvaType>(),
  centerId: null,
  saving: false,
  deleting: false,
  successMessage: null,
  error: null
};

export const TvaTypesStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withDevtools('TvaTypesStore'),
  withComputed((store) => ({
    isEmpty: computed(() => store.rows().length === 0)
  })),
  withMethods((store, api = inject(BackendApiService)) => ({

    loadPage: rxMethod<{ centerId: string; page: number; size: number }>(
      pipe(
        tap(({centerId, page, size}) =>
          patchState(store, {loading: true, error: null, centerId, pageIndex: page, pageSize: size})
        ),
        switchMap(({centerId, page, size}) =>
          api.listTvaTypes(centerId, page, size).pipe(
            tap((res) => patchState(store, {
              rows: res.items ?? [],
              total: res.total ?? 0,
              pageIndex: res.page ?? page,
              loading: false
            })),
            catchError(() => {
              patchState(store, {rows: [], total: 0, loading: false, error: 'TVA_TYPES.ERRORS.LOAD_FAILED'});
              return EMPTY;
            })
          )
        )
      )
    ),

    createTvaType: rxMethod<TvaTypePayload>(
      pipe(
        tap(() => patchState(store, {saving: true, error: null, successMessage: null})),
        switchMap((payload) =>
          api.createTvaType(payload).pipe(
            tap(() => patchState(store, {saving: false, successMessage: 'TVA_TYPES.SUCCESS.CREATED'})),
            catchError(() => {
              patchState(store, {saving: false, error: 'TVA_TYPES.ERRORS.CREATE_FAILED'});
              return EMPTY;
            })
          )
        )
      )
    ),

    updateTvaType: rxMethod<{ id: string; payload: TvaTypePayload }>(
      pipe(
        tap(() => patchState(store, {saving: true, error: null, successMessage: null})),
        switchMap(({id, payload}) =>
          api.updateTvaType(id, payload).pipe(
            tap(() => patchState(store, {saving: false, successMessage: 'TVA_TYPES.SUCCESS.UPDATED'})),
            catchError(() => {
              patchState(store, {saving: false, error: 'TVA_TYPES.ERRORS.UPDATE_FAILED'});
              return EMPTY;
            })
          )
        )
      )
    ),

    deleteTvaType: rxMethod<{ id: string; centerId: string }>(
      pipe(
        tap(() => patchState(store, {deleting: true, error: null, successMessage: null})),
        switchMap(({id, centerId}) =>
          api.deleteTvaType(id, centerId).pipe(
            tap(() => patchState(store, {deleting: false, successMessage: 'TVA_TYPES.SUCCESS.DELETED'})),
            catchError(() => {
              patchState(store, {deleting: false, error: 'TVA_TYPES.ERRORS.DELETE_FAILED'});
              return EMPTY;
            })
          )
        )
      )
    ),

    setPagination(pageIndex: number, pageSize: number): void {
      patchState(store, {pageIndex, pageSize});
    },

    clearMessages(): void {
      patchState(store, {error: null, successMessage: null});
    }
  }))
);
