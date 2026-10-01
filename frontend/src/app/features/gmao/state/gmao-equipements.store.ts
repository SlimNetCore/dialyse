import {computed, inject} from '@angular/core';
import {patchState, signalStore, withComputed, withMethods, withState} from '@ngrx/signals';
import {rxMethod} from '@ngrx/signals/rxjs-interop';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {catchError, EMPTY, pipe, switchMap, tap} from 'rxjs';
import {
  CreateEquipementPayload,
  Equipement,
  GmaoApiService,
  UpdateEquipementPayload,
} from '../../../core/api/gmao-api.service';
import {createPagedListState, PagedListState} from '../../../core/state/paged-list-state.util';

type GmaoEquipementsState = PagedListState<Equipement> & {
  statutFilter: string | null;
  saving: boolean;
  successMessage: string | null;
  error: string | null;
};

const initialState: GmaoEquipementsState = {
  ...createPagedListState<Equipement>(),
  statutFilter: null,
  saving: false,
  successMessage: null,
  error: null,
};

export const GmaoEquipementsStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withDevtools('GmaoEquipementsStore'),
  withComputed((store) => ({
    isEmpty: computed(() => store.rows().length === 0),
  })),
  withMethods((store, api = inject(GmaoApiService)) => ({
    loadPage: rxMethod<{ page: number; size: number }>(
      pipe(
        tap(({page, size}) => patchState(store, {loading: true, error: null, pageIndex: page, pageSize: size})),
        switchMap(({page, size}) =>
          api.listEquipements(page, size, store.statutFilter()).pipe(
            tap((res) => patchState(store, {
              rows: res.items ?? [],
              total: res.total ?? 0,
              pageIndex: res.page ?? page,
              loading: false,
            })),
            catchError(() => {
              patchState(store, {rows: [], total: 0, loading: false, error: 'GMAO.EQUIPEMENTS.LOAD_ERROR'});
              return EMPTY;
            }),
          ),
        ),
      ),
    ),

    createEquipement: rxMethod<CreateEquipementPayload>(
      pipe(
        tap(() => patchState(store, {saving: true, error: null, successMessage: null})),
        switchMap((payload) =>
          api.createEquipement(payload).pipe(
            tap(() => patchState(store, {saving: false, successMessage: 'GMAO.EQUIPEMENTS.SAVED_OK'})),
            catchError(() => {
              patchState(store, {saving: false, error: 'GMAO.EQUIPEMENTS.SAVE_ERROR'});
              return EMPTY;
            }),
          ),
        ),
      ),
    ),

    updateEquipement: rxMethod<{ id: string; payload: UpdateEquipementPayload }>(
      pipe(
        tap(() => patchState(store, {saving: true, error: null, successMessage: null})),
        switchMap(({id, payload}) =>
          api.updateEquipement(id, payload).pipe(
            tap(() => patchState(store, {saving: false, successMessage: 'GMAO.EQUIPEMENTS.SAVED_OK'})),
            catchError(() => {
              patchState(store, {saving: false, error: 'GMAO.EQUIPEMENTS.SAVE_ERROR'});
              return EMPTY;
            }),
          ),
        ),
      ),
    ),

    markOutOfService: rxMethod<{ id: string; raison: string }>(
      pipe(
        tap(() => patchState(store, {saving: true, error: null, successMessage: null})),
        switchMap(({id, raison}) =>
          api.markEquipementOutOfService(id, raison).pipe(
            tap(() => patchState(store, {saving: false, successMessage: 'GMAO.EQUIPEMENTS.STATUS_UPDATED'})),
            catchError(() => {
              patchState(store, {saving: false, error: 'GMAO.EQUIPEMENTS.SAVE_ERROR'});
              return EMPTY;
            }),
          ),
        ),
      ),
    ),

    reactivate: rxMethod<string>(
      pipe(
        tap(() => patchState(store, {saving: true, error: null, successMessage: null})),
        switchMap((id) =>
          api.reactivateEquipement(id).pipe(
            tap(() => patchState(store, {saving: false, successMessage: 'GMAO.EQUIPEMENTS.STATUS_UPDATED'})),
            catchError(() => {
              patchState(store, {saving: false, error: 'GMAO.EQUIPEMENTS.SAVE_ERROR'});
              return EMPTY;
            }),
          ),
        ),
      ),
    ),

    reformer: rxMethod<{ id: string; motif: string }>(
      pipe(
        tap(() => patchState(store, {saving: true, error: null, successMessage: null})),
        switchMap(({id, motif}) =>
          api.reformerEquipement(id, motif).pipe(
            tap(() => patchState(store, {saving: false, successMessage: 'GMAO.EQUIPEMENTS.STATUS_UPDATED'})),
            catchError(() => {
              patchState(store, {saving: false, error: 'GMAO.EQUIPEMENTS.SAVE_ERROR'});
              return EMPTY;
            }),
          ),
        ),
      ),
    ),

    setStatutFilter(statutFilter: string | null): void {
      patchState(store, {statutFilter});
    },

    setPagination(pageIndex: number, pageSize: number): void {
      patchState(store, {pageIndex, pageSize});
    },

    clearMessages(): void {
      patchState(store, {error: null, successMessage: null});
    },
  })),
);
