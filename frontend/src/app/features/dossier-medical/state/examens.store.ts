import {inject} from '@angular/core';
import {patchState, signalStore, withMethods, withState} from '@ngrx/signals';
import {rxMethod} from '@ngrx/signals/rxjs-interop';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {catchError, of, pipe, switchMap, tap} from 'rxjs';
import {
  CreateDemandeExamenPayload,
  CreateObservationPayload,
  DemandeExamen,
  DossierMedicalApiService,
  ObservationBiologique,
} from '../../../core/api/dossier-medical-api.service';
import {createPagedListState, PagedListState} from '../../../core/state/paged-list-state.util';

type ExamensState = PagedListState<DemandeExamen> & {
  error: string | null;
  activeCenterId: string | null;
  activePatientId: string | null;
  /** Observations de la demande actuellement dépliée dans la liste, indexées par demandeId. */
  observationsParDemande: Record<string, ObservationBiologique[]>;
};

const initialState: ExamensState = {
  ...createPagedListState<DemandeExamen>(),
  error: null,
  activeCenterId: null,
  activePatientId: null,
  observationsParDemande: {},
};

/** Demandes d'examen du patient (biologie, imagerie, fonctionnel, anapath) et leurs résultats. */
export const ExamensStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withDevtools('ExamensStore'),
  withMethods((store, api = inject(DossierMedicalApiService)) => {
    const load = rxMethod<{ centerId: string; patientId: string; page: number; size: number }>(
      pipe(
        tap(({centerId, patientId}) => patchState(store, {
          loading: true, error: null, activeCenterId: centerId, activePatientId: patientId,
        })),
        switchMap(({centerId, patientId, page, size}) =>
          api.listDemandesExamen(centerId, patientId, page, size).pipe(
            tap((res) => patchState(store, {
              rows: res.items, total: res.total, pageIndex: res.page, pageSize: res.size, loading: false,
            })),
            catchError((err: any) => {
              patchState(store, {
                loading: false,
                error: err?.error?.detail || err?.statusText || "Erreur chargement des demandes d'examen",
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

    const create = rxMethod<{ patientId: string; payload: CreateDemandeExamenPayload }>(
      pipe(
        tap(() => patchState(store, {error: null})),
        switchMap(({patientId, payload}) =>
          api.createDemandeExamen(patientId, payload).pipe(
            tap(() => refresh()),
            catchError((err: any) => {
              patchState(store, {error: err?.error?.detail || err?.statusText || 'Erreur enregistrement'});
              return of(null);
            }),
          ),
        ),
      ),
    );

    const transition = rxMethod<{
      patientId: string; demandeId: string; centerId: string;
      action: 'preleve' | 'resultat-disponible' | 'valider' | 'annuler'; conclusion?: string | null;
    }>(
      pipe(
        tap(() => patchState(store, {error: null})),
        switchMap(({patientId, demandeId, centerId, action, conclusion}) => {
          const request$ = action === 'preleve' ? api.demandeExamenPreleve(patientId, demandeId, centerId)
            : action === 'resultat-disponible' ? api.demandeExamenResultatDisponible(patientId, demandeId, centerId)
              : action === 'valider' ? api.demandeExamenValider(patientId, demandeId, centerId, conclusion ?? null)
                : api.demandeExamenAnnuler(patientId, demandeId, centerId);
          return request$.pipe(
            tap(() => refresh()),
            catchError((err: any) => {
              patchState(store, {error: err?.error?.detail || err?.statusText || 'Transition refusée'});
              return of(null);
            }),
          );
        }),
      ),
    );

    const loadObservations = rxMethod<{ centerId: string; patientId: string; demandeId: string }>(
      pipe(
        switchMap(({centerId, patientId, demandeId}) =>
          api.listObservationsByDemande(centerId, patientId, demandeId).pipe(
            tap((observations) => patchState(store, {
              observationsParDemande: {...store.observationsParDemande(), [demandeId]: observations},
            })),
            catchError(() => of(null)),
          ),
        ),
      ),
    );

    const createObservation = rxMethod<{
      patientId: string;
      demandeId: string;
      centerId: string;
      payload: CreateObservationPayload
    }>(
      pipe(
        tap(() => patchState(store, {error: null})),
        switchMap(({patientId, demandeId, centerId, payload}) =>
          api.createObservation(patientId, payload).pipe(
            tap(() => loadObservations({centerId, patientId, demandeId})),
            catchError((err: any) => {
              patchState(store, {error: err?.error?.detail || err?.statusText || 'Erreur enregistrement du résultat'});
              return of(null);
            }),
          ),
        ),
      ),
    );

    return {
      load,
      create,
      transition,
      loadObservations,
      createObservation,
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
