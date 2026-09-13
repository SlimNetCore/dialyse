import {inject} from '@angular/core';
import {patchState, signalStore, withMethods, withState} from '@ngrx/signals';
import {rxMethod} from '@ngrx/signals/rxjs-interop';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {catchError, of, pipe, switchMap, tap} from 'rxjs';
import {
  AbordVasculaire,
  DossierMedicalApiService,
  UpsertAbordVasculairePayload,
} from '../../../core/api/dossier-medical-api.service';
import {createPagedListState, PagedListState} from '../../../core/state/paged-list-state.util';

type AbordsVasculairesState = PagedListState<AbordVasculaire> & {
  error: string | null;
  activeCenterId: string | null;
  activePatientId: string | null;
};

const initialState: AbordsVasculairesState = {
  ...createPagedListState<AbordVasculaire>(),
  error: null,
  activeCenterId: null,
  activePatientId: null,
};

/** Historique des abords vasculaires (FAV, PTFE, cathéters) du patient. */
export const AbordsVasculairesStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withDevtools('AbordsVasculairesStore'),
  withMethods((store, api = inject(DossierMedicalApiService)) => {
    // Closures locales plutôt que `this` : à l'intérieur d'un `rxMethod`, les callbacks
    // sont des arrow functions dont le `this` lexical ne pointe pas vers le store.
    const load = rxMethod<{ centerId: string; patientId: string; page: number; size: number }>(
      pipe(
        tap(({centerId, patientId}) => patchState(store, {
          loading: true,
          error: null,
          activeCenterId: centerId,
          activePatientId: patientId,
        })),
        switchMap(({centerId, patientId, page, size}) =>
          api.listAbordsVasculaires(centerId, patientId, page, size).pipe(
            tap((res) => patchState(store, {
              rows: res.items,
              total: res.total,
              pageIndex: res.page,
              pageSize: res.size,
              loading: false,
            })),
            catchError((err: any) => {
              patchState(store, {
                loading: false,
                error: err?.error?.detail || err?.statusText || 'Erreur chargement des abords vasculaires',
              });
              return of(null);
            }),
          ),
        ),
      ),
    );

    const refresh = () => {
      const centerId = store.activeCenterId();
      const patientId = store.activePatientId();
      if (!centerId || !patientId) return;
      load({centerId, patientId, page: store.pageIndex(), size: store.pageSize()});
    };

    const save = rxMethod<{ patientId: string; abordId: string | null; payload: UpsertAbordVasculairePayload }>(
      pipe(
        tap(() => patchState(store, {error: null})),
        switchMap(({patientId, abordId, payload}) => {
          const request$ = abordId
            ? api.updateAbordVasculaire(patientId, abordId, payload)
            : api.createAbordVasculaire(patientId, payload);
          return request$.pipe(
            tap(() => refresh()),
            catchError((err: any) => {
              patchState(store, {error: err?.error?.detail || err?.statusText || 'Erreur enregistrement'});
              return of(null);
            }),
          );
        }),
      ),
    );

    const remove = rxMethod<{ centerId: string; patientId: string; abordId: string }>(
      pipe(
        tap(() => patchState(store, {error: null})),
        switchMap(({centerId, patientId, abordId}) =>
          api.deleteAbordVasculaire(centerId, patientId, abordId).pipe(
            tap(() => refresh()),
            catchError((err: any) => {
              patchState(store, {error: err?.error?.detail || err?.statusText || 'Erreur suppression'});
              return of(null);
            }),
          ),
        ),
      ),
    );

    return {
      load,
      save,
      delete: remove,
      setPagination(pageIndex: number, pageSize: number): void {
        patchState(store, {pageIndex, pageSize});
        refresh();
      },
      refresh,
      reset(): void {
        patchState(store, initialState);
      },
    };
  }),
);
