import {inject} from '@angular/core';
import {patchState, signalStore, withMethods, withState} from '@ngrx/signals';
import {rxMethod} from '@ngrx/signals/rxjs-interop';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {catchError, of, pipe, switchMap, tap} from 'rxjs';
import {
  Allergie,
  CreateAllergiePayload,
  DossierMedicalApiService,
  UpdateAllergiePayload,
} from '../../../core/api/dossier-medical-api.service';
import {createPagedListState, PagedListState} from '../../../core/state/paged-list-state.util';

type AllergiesState = PagedListState<Allergie> & {
  critiques: Allergie[];
  error: string | null;
  activeCenterId: string | null;
  activePatientId: string | null;
};

const initialState: AllergiesState = {
  ...createPagedListState<Allergie>(),
  critiques: [],
  error: null,
  activeCenterId: null,
  activePatientId: null,
};

/** Allergies et intolérances du patient. Les criticités hautes alimentent le bandeau du dossier. */
export const AllergiesStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withDevtools('AllergiesStore'),
  withMethods((store, api = inject(DossierMedicalApiService)) => {
    const loadCritiques = rxMethod<{ centerId: string; patientId: string }>(
      pipe(
        switchMap(({centerId, patientId}) =>
          api.listAllergiesCritiques(centerId, patientId).pipe(
            tap((critiques) => patchState(store, {critiques})),
            catchError(() => of(null)),
          ),
        ),
      ),
    );

    const load = rxMethod<{ centerId: string; patientId: string; page: number; size: number }>(
      pipe(
        tap(({centerId, patientId}) => patchState(store, {
          loading: true, error: null, activeCenterId: centerId, activePatientId: patientId,
        })),
        switchMap(({centerId, patientId, page, size}) =>
          api.listAllergies(centerId, patientId, page, size).pipe(
            tap((res) => patchState(store, {
              rows: res.items, total: res.total, pageIndex: res.page, pageSize: res.size, loading: false,
            })),
            catchError((err: any) => {
              patchState(store, {
                loading: false,
                error: err?.error?.detail || err?.statusText || 'Erreur chargement des allergies',
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
      loadCritiques({centerId, patientId});
    };

    const create = rxMethod<{ patientId: string; payload: CreateAllergiePayload }>(
      pipe(
        tap(() => patchState(store, {error: null})),
        switchMap(({patientId, payload}) =>
          api.createAllergie(patientId, payload).pipe(
            tap(() => refresh()),
            catchError((err: any) => {
              patchState(store, {error: err?.error?.detail || err?.statusText || 'Erreur enregistrement'});
              return of(null);
            }),
          ),
        ),
      ),
    );

    const update = rxMethod<{ patientId: string; allergieId: string; payload: UpdateAllergiePayload }>(
      pipe(
        tap(() => patchState(store, {error: null})),
        switchMap(({patientId, allergieId, payload}) =>
          api.updateAllergie(patientId, allergieId, payload).pipe(
            tap(() => refresh()),
            catchError((err: any) => {
              patchState(store, {error: err?.error?.detail || err?.statusText || 'Erreur enregistrement'});
              return of(null);
            }),
          ),
        ),
      ),
    );

    const remove = rxMethod<{ centerId: string; patientId: string; allergieId: string }>(
      pipe(
        tap(() => patchState(store, {error: null})),
        switchMap(({centerId, patientId, allergieId}) =>
          api.deleteAllergie(centerId, patientId, allergieId).pipe(
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
      loadCritiques,
      create,
      update,
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
