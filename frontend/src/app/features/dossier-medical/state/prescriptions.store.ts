import {inject} from '@angular/core';
import {patchState, signalStore, withMethods, withState} from '@ngrx/signals';
import {rxMethod} from '@ngrx/signals/rxjs-interop';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {catchError, of, pipe, switchMap, tap} from 'rxjs';
import {
  DossierMedicalApiService,
  PrescriptionMedicale,
  UpsertPrescriptionMedicalePayload,
} from '../../../core/api/dossier-medical-api.service';
import {createPagedListState, PagedListState} from '../../../core/state/paged-list-state.util';

type PrescriptionsState = PagedListState<PrescriptionMedicale> & {
  error: string | null;
  activeCenterId: string | null;
  activePatientId: string | null;
};

const initialState: PrescriptionsState = {
  ...createPagedListState<PrescriptionMedicale>({pageSize: 20}),
  error: null,
  activeCenterId: null,
  activePatientId: null,
};

/**
 * Prescriptions médicales du patient (cibles de dialyse + traitement de l'anémie EPO/fer).
 * <p>
 * Triées par date décroissante côté backend : `rows()[0]` est donc toujours la plus récente —
 * c'est elle seule que l'écran autorise à modifier (les autres sont l'historique figé).
 */
export const PrescriptionsStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withDevtools('PrescriptionsStore'),
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
          api.listPrescriptions(centerId, patientId, page, size).pipe(
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
                error: err?.error?.detail || err?.statusText || 'Erreur chargement des prescriptions',
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

    const save = rxMethod<{
      patientId: string;
      prescriptionId: string | null;
      payload: UpsertPrescriptionMedicalePayload
    }>(
      pipe(
        tap(() => patchState(store, {error: null})),
        switchMap(({patientId, prescriptionId, payload}) => {
          const request$ = prescriptionId
            ? api.updatePrescription(patientId, prescriptionId, payload)
            : api.createPrescription(patientId, payload);
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

    const remove = rxMethod<{ centerId: string; patientId: string; prescriptionId: string }>(
      pipe(
        tap(() => patchState(store, {error: null})),
        switchMap(({centerId, patientId, prescriptionId}) =>
          api.deletePrescription(centerId, patientId, prescriptionId).pipe(
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
