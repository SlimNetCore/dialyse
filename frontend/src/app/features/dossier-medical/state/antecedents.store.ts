import {inject} from '@angular/core';
import {patchState, signalStore, withMethods, withState} from '@ngrx/signals';
import {rxMethod} from '@ngrx/signals/rxjs-interop';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {catchError, of, pipe, switchMap, tap} from 'rxjs';
import {
  Antecedent,
  DossierMedicalApiService,
  UpsertAntecedentPayload
} from '../../../core/api/dossier-medical-api.service';
import {createPagedListState, PagedListState} from '../../../core/state/paged-list-state.util';

type AntecedentsState = PagedListState<Antecedent> & {
  error: string | null;
  activeCenterId: string | null;
  activePatientId: string | null;
};

const initialState: AntecedentsState = {
  ...createPagedListState<Antecedent>(),
  error: null,
  activeCenterId: null,
  activePatientId: null,
};

/** Antécédents et comorbidités du patient, codés CIM-10 quand possible. */
export const AntecedentsStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withDevtools('AntecedentsStore'),
  withMethods((store, api = inject(DossierMedicalApiService)) => {
    const load = rxMethod<{ centerId: string; patientId: string; page: number; size: number }>(
      pipe(
        tap(({centerId, patientId}) => patchState(store, {
          loading: true, error: null, activeCenterId: centerId, activePatientId: patientId,
        })),
        switchMap(({centerId, patientId, page, size}) =>
          api.listAntecedents(centerId, patientId, page, size).pipe(
            tap((res) => patchState(store, {
              rows: res.items, total: res.total, pageIndex: res.page, pageSize: res.size, loading: false,
            })),
            catchError((err: any) => {
              patchState(store, {
                loading: false,
                error: err?.error?.detail || err?.statusText || 'Erreur chargement des antécédents',
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

    const save = rxMethod<{ patientId: string; antecedentId: string | null; payload: UpsertAntecedentPayload }>(
      pipe(
        tap(() => patchState(store, {error: null})),
        switchMap(({patientId, antecedentId, payload}) => {
          const request$ = antecedentId
            ? api.updateAntecedent(patientId, antecedentId, payload)
            : api.createAntecedent(patientId, payload);
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

    const resoudre = rxMethod<{ patientId: string; antecedentId: string; centerId: string; dateResolution: string }>(
      pipe(
        tap(() => patchState(store, {error: null})),
        switchMap(({patientId, antecedentId, centerId, dateResolution}) =>
          api.resoudreAntecedent(patientId, antecedentId, centerId, dateResolution).pipe(
            tap(() => refresh()),
            catchError((err: any) => {
              patchState(store, {error: err?.error?.detail || err?.statusText || 'Erreur de résolution'});
              return of(null);
            }),
          ),
        ),
      ),
    );

    const remove = rxMethod<{ centerId: string; patientId: string; antecedentId: string }>(
      pipe(
        tap(() => patchState(store, {error: null})),
        switchMap(({centerId, patientId, antecedentId}) =>
          api.deleteAntecedent(centerId, patientId, antecedentId).pipe(
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
      resoudre,
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
