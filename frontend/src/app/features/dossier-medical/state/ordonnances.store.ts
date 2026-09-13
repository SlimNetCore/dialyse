import {inject} from '@angular/core';
import {patchState, signalStore, withMethods, withState} from '@ngrx/signals';
import {rxMethod} from '@ngrx/signals/rxjs-interop';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {catchError, of, pipe, switchMap, tap} from 'rxjs';
import {
  CreateOrdonnancePayload,
  DossierMedicalApiService,
  Ordonnance
} from '../../../core/api/dossier-medical-api.service';
import {createPagedListState, PagedListState} from '../../../core/state/paged-list-state.util';

type OrdonnancesState = PagedListState<Ordonnance> & {
  error: string | null;
  activeCenterId: string | null;
  activePatientId: string | null;
};

const initialState: OrdonnancesState = {
  ...createPagedListState<Ordonnance>({pageSize: 10}),
  error: null,
  activeCenterId: null,
  activePatientId: null,
};

/**
 * Ordonnances médicamenteuses du patient — cycle de signature BROUILLON → SIGNEE → IMPRIMEE,
 * ANNULEE possible à tout moment non terminal. Une fois signée, le contenu est immuable.
 */
export const OrdonnancesStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withDevtools('OrdonnancesStore'),
  withMethods((store, api = inject(DossierMedicalApiService)) => {
    const load = rxMethod<{ centerId: string; patientId: string; page: number; size: number }>(
      pipe(
        tap(({centerId, patientId}) => patchState(store, {
          loading: true, error: null, activeCenterId: centerId, activePatientId: patientId,
        })),
        switchMap(({centerId, patientId, page, size}) =>
          api.listOrdonnances(centerId, patientId, page, size).pipe(
            tap((res) => patchState(store, {
              rows: res.items, total: res.total, pageIndex: res.page, pageSize: res.size, loading: false,
            })),
            catchError((err: any) => {
              patchState(store, {
                loading: false,
                error: err?.error?.detail || err?.statusText || 'Erreur chargement des ordonnances',
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

    const create = rxMethod<{ patientId: string; payload: CreateOrdonnancePayload }>(
      pipe(
        tap(() => patchState(store, {error: null})),
        switchMap(({patientId, payload}) =>
          api.createOrdonnance(patientId, payload).pipe(
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
      patientId: string; ordonnanceId: string; centerId: string; action: 'signer' | 'imprimer' | 'annuler';
    }>(
      pipe(
        tap(() => patchState(store, {error: null})),
        switchMap(({patientId, ordonnanceId, centerId, action}) => {
          const request$ = action === 'signer' ? api.signerOrdonnance(patientId, ordonnanceId, centerId)
            : action === 'imprimer' ? api.marquerOrdonnanceImprimee(patientId, ordonnanceId, centerId)
              : api.annulerOrdonnance(patientId, ordonnanceId, centerId);
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

    return {
      load,
      create,
      transition,
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
