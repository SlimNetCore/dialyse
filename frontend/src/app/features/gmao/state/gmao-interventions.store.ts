import {computed, inject} from '@angular/core';
import {patchState, signalStore, withComputed, withMethods, withState} from '@ngrx/signals';
import {rxMethod} from '@ngrx/signals/rxjs-interop';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {catchError, EMPTY, pipe, switchMap, tap} from 'rxjs';
import {
  AjouterLigneCoutPayload,
  CreateInterventionPayload,
  GmaoApiService,
  Intervention,
  StatutEquipement,
} from '../../../core/api/gmao-api.service';
import {createPagedListState, PagedListState} from '../../../core/state/paged-list-state.util';

type GmaoInterventionsState = PagedListState<Intervention> & {
  statutFilter: string | null;
  equipementFilter: string | null;
  saving: boolean;
  successMessage: string | null;
  error: string | null;
};

const initialState: GmaoInterventionsState = {
  ...createPagedListState<Intervention>(),
  statutFilter: null,
  equipementFilter: null,
  saving: false,
  successMessage: null,
  error: null,
};

export const GmaoInterventionsStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withDevtools('GmaoInterventionsStore'),
  withComputed((store) => ({
    isEmpty: computed(() => store.rows().length === 0),
  })),
  withMethods((store, api = inject(GmaoApiService)) => ({
    loadPage: rxMethod<{ page: number; size: number }>(
      pipe(
        tap(({page, size}) => patchState(store, {loading: true, error: null, pageIndex: page, pageSize: size})),
        switchMap(({page, size}) =>
          api.listInterventions(page, size, {
            statut: store.statutFilter(),
            equipementId: store.equipementFilter(),
          }).pipe(
            tap((res) => patchState(store, {
              rows: res.items ?? [],
              total: res.total ?? 0,
              pageIndex: res.page ?? page,
              loading: false,
            })),
            catchError(() => {
              patchState(store, {rows: [], total: 0, loading: false, error: 'GMAO.INTERVENTIONS.LOAD_ERROR'});
              return EMPTY;
            }),
          ),
        ),
      ),
    ),

    createIntervention: rxMethod<CreateInterventionPayload>(
      pipe(
        tap(() => patchState(store, {saving: true, error: null, successMessage: null})),
        switchMap((payload) =>
          api.createIntervention(payload).pipe(
            tap(() => patchState(store, {saving: false, successMessage: 'GMAO.INTERVENTIONS.SAVED_OK'})),
            catchError(() => {
              patchState(store, {saving: false, error: 'GMAO.INTERVENTIONS.SAVE_ERROR'});
              return EMPTY;
            }),
          ),
        ),
      ),
    ),

    startIntervention: rxMethod<string>(
      pipe(
        tap(() => patchState(store, {saving: true, error: null, successMessage: null})),
        switchMap((id) =>
          api.startIntervention(id).pipe(
            tap(() => patchState(store, {saving: false, successMessage: 'GMAO.INTERVENTIONS.STATUS_UPDATED'})),
            catchError(() => {
              patchState(store, {saving: false, error: 'GMAO.INTERVENTIONS.SAVE_ERROR'});
              return EMPTY;
            }),
          ),
        ),
      ),
    ),

    finishIntervention: rxMethod<{
      id: string;
      actions: string;
      etatEquipementApres: StatutEquipement;
      dateFin: string;
      cause?: string | null
    }>(
      pipe(
        tap(() => patchState(store, {saving: true, error: null, successMessage: null})),
        switchMap(({id, actions, etatEquipementApres, dateFin, cause}) =>
          api.finishIntervention(id, actions, etatEquipementApres, dateFin, cause).pipe(
            tap(() => patchState(store, {saving: false, successMessage: 'GMAO.INTERVENTIONS.STATUS_UPDATED'})),
            catchError(() => {
              patchState(store, {saving: false, error: 'GMAO.INTERVENTIONS.SAVE_ERROR'});
              return EMPTY;
            }),
          ),
        ),
      ),
    ),

    cancelIntervention: rxMethod<{ id: string; raison: string }>(
      pipe(
        tap(() => patchState(store, {saving: true, error: null, successMessage: null})),
        switchMap(({id, raison}) =>
          api.cancelIntervention(id, raison).pipe(
            tap(() => patchState(store, {saving: false, successMessage: 'GMAO.INTERVENTIONS.STATUS_UPDATED'})),
            catchError(() => {
              patchState(store, {saving: false, error: 'GMAO.INTERVENTIONS.SAVE_ERROR'});
              return EMPTY;
            }),
          ),
        ),
      ),
    ),

    ajouterLigneCout: rxMethod<{ id: string; payload: AjouterLigneCoutPayload }>(
      pipe(
        tap(() => patchState(store, {saving: true, error: null, successMessage: null})),
        switchMap(({id, payload}) =>
          api.ajouterLigneCout(id, payload).pipe(
            tap(() => patchState(store, {saving: false, successMessage: 'GMAO.INTERVENTIONS.COST_LINE_ADDED'})),
            catchError(() => {
              patchState(store, {saving: false, error: 'GMAO.INTERVENTIONS.SAVE_ERROR'});
              return EMPTY;
            }),
          ),
        ),
      ),
    ),

    setStatutFilter(statutFilter: string | null): void {
      patchState(store, {statutFilter});
    },

    setEquipementFilter(equipementFilter: string | null): void {
      patchState(store, {equipementFilter});
    },

    setPagination(pageIndex: number, pageSize: number): void {
      patchState(store, {pageIndex, pageSize});
    },

    clearMessages(): void {
      patchState(store, {error: null, successMessage: null});
    },
  })),
);
