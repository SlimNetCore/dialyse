import {inject} from '@angular/core';
import {patchState, signalStore, withMethods, withState} from '@ngrx/signals';
import {rxMethod} from '@ngrx/signals/rxjs-interop';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {catchError, of, pipe, switchMap, tap} from 'rxjs';
import {
  CreateSerologiePayload,
  DossierMedicalApiService,
  Serologie,
  UpdateSerologiePayload,
} from '../../../core/api/dossier-medical-api.service';
import {createPagedListState, PagedListState} from '../../../core/state/paged-list-state.util';

type SerologiesState = PagedListState<Serologie> & {
  synthese: Serologie[];
  error: string | null;
  activeCenterId: string | null;
  activePatientId: string | null;
};

const initialState: SerologiesState = {
  ...createPagedListState<Serologie>(),
  synthese: [],
  error: null,
  activeCenterId: null,
  activePatientId: null,
};

/** Résultats sérologiques du patient. `synthese` = dernier résultat connu par marqueur. */
export const SerologiesStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withDevtools('SerologiesStore'),
  withMethods((store, api = inject(DossierMedicalApiService)) => {
    const loadSynthese = rxMethod<{ centerId: string; patientId: string }>(
      pipe(
        switchMap(({centerId, patientId}) =>
          api.getSerologiesSynthese(centerId, patientId).pipe(
            tap((synthese) => patchState(store, {synthese})),
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
          api.listSerologies(centerId, patientId, page, size).pipe(
            tap((res) => patchState(store, {
              rows: res.items, total: res.total, pageIndex: res.page, pageSize: res.size, loading: false,
            })),
            catchError((err: any) => {
              patchState(store, {
                loading: false,
                error: err?.error?.detail || err?.statusText || 'Erreur chargement des sérologies',
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
      loadSynthese({centerId, patientId});
    };

    const create = rxMethod<{ patientId: string; payload: CreateSerologiePayload }>(
      pipe(
        tap(() => patchState(store, {error: null})),
        switchMap(({patientId, payload}) =>
          api.createSerologie(patientId, payload).pipe(
            tap(() => refresh()),
            catchError((err: any) => {
              patchState(store, {error: err?.error?.detail || err?.statusText || 'Erreur enregistrement'});
              return of(null);
            }),
          ),
        ),
      ),
    );

    const update = rxMethod<{ patientId: string; serologieId: string; payload: UpdateSerologiePayload }>(
      pipe(
        tap(() => patchState(store, {error: null})),
        switchMap(({patientId, serologieId, payload}) =>
          api.updateSerologie(patientId, serologieId, payload).pipe(
            tap(() => refresh()),
            catchError((err: any) => {
              patchState(store, {error: err?.error?.detail || err?.statusText || 'Erreur enregistrement'});
              return of(null);
            }),
          ),
        ),
      ),
    );

    const remove = rxMethod<{ centerId: string; patientId: string; serologieId: string }>(
      pipe(
        tap(() => patchState(store, {error: null})),
        switchMap(({centerId, patientId, serologieId}) =>
          api.deleteSerologie(centerId, patientId, serologieId).pipe(
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
      loadSynthese,
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
