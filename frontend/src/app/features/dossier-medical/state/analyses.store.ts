import {inject} from '@angular/core';
import {patchState, signalStore, withMethods, withState} from '@ngrx/signals';
import {rxMethod} from '@ngrx/signals/rxjs-interop';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {catchError, of, pipe, switchMap, tap} from 'rxjs';
import {
  DossierMedicalApiService,
  ResultatAnalyse,
  UpsertResultatAnalysePayload,
} from '../../../core/api/dossier-medical-api.service';
import {createPagedListState, PagedListState} from '../../../core/state/paged-list-state.util';

type AnalysesState = PagedListState<ResultatAnalyse> & {
  error: string | null;
  activeCenterId: string | null;
  activePatientId: string | null;
};

const initialState: AnalysesState = {
  ...createPagedListState<ResultatAnalyse>({pageSize: 20}),
  error: null,
  activeCenterId: null,
  activePatientId: null,
};

/** Résultats de bilans biologiques du patient (NFS, bilan martial, adéquation, phospho-calcique...). */
export const AnalysesStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withDevtools('AnalysesStore'),
  withMethods((store, api = inject(DossierMedicalApiService)) => {
    const load = rxMethod<{ centerId: string; patientId: string; page: number; size: number }>(
      pipe(
        tap(({centerId, patientId}) => patchState(store, {
          loading: true,
          error: null,
          activeCenterId: centerId,
          activePatientId: patientId,
        })),
        switchMap(({centerId, patientId, page, size}) =>
          api.listAnalyses(centerId, patientId, page, size).pipe(
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
                error: err?.error?.detail || err?.statusText || 'Erreur chargement des bilans biologiques',
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

    const save = rxMethod<{ patientId: string; analyseId: string | null; payload: UpsertResultatAnalysePayload }>(
      pipe(
        tap(() => patchState(store, {error: null})),
        switchMap(({patientId, analyseId, payload}) => {
          const request$ = analyseId
            ? api.updateAnalyse(patientId, analyseId, payload)
            : api.createAnalyse(patientId, payload);
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

    const remove = rxMethod<{ centerId: string; patientId: string; analyseId: string }>(
      pipe(
        tap(() => patchState(store, {error: null})),
        switchMap(({centerId, patientId, analyseId}) =>
          api.deleteAnalyse(centerId, patientId, analyseId).pipe(
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
