import {inject} from '@angular/core';
import {patchState, signalStore, withMethods, withState} from '@ngrx/signals';
import {rxMethod} from '@ngrx/signals/rxjs-interop';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {catchError, of, pipe, switchMap, tap} from 'rxjs';
import {ConstanteSeance, DossierMedicalApiService} from '../../../core/api/dossier-medical-api.service';
import {createPagedListState, PagedListState} from '../../../core/state/paged-list-state.util';

const CHART_WINDOW_SIZE = 100;

type ConstantesState = PagedListState<ConstanteSeance> & {
  error: string | null;
  activeCenterId: string | null;
  activePatientId: string | null;
  chartRows: ConstanteSeance[];
  chartLoading: boolean;
};

const initialState: ConstantesState = {
  ...createPagedListState<ConstanteSeance>({pageSize: 20}),
  error: null,
  activeCenterId: null,
  activePatientId: null,
  chartRows: [],
  chartLoading: false,
};

/**
 * Vue longitudinale, lecture seule, des constantes du patient (poids, TA, débit, UF, durée),
 * agrégée à partir du volet paramédical déjà saisi par l'infirmier séance après séance.
 */
export const ConstantesStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withDevtools('ConstantesStore'),
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
          api.listConstantes(centerId, patientId, page, size).pipe(
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
                error: err?.error?.detail || err?.statusText || 'Erreur chargement des constantes',
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

    const loadChart = rxMethod<{ centerId: string; patientId: string }>(
      pipe(
        tap(({centerId, patientId}) => patchState(store, {
          chartLoading: true,
          activeCenterId: centerId,
          activePatientId: patientId,
        })),
        switchMap(({centerId, patientId}) =>
          api.listConstantes(centerId, patientId, 0, CHART_WINDOW_SIZE).pipe(
            tap((res) => patchState(store, {
              chartRows: [...res.items].reverse(),
              chartLoading: false,
            })),
            catchError((err: any) => {
              patchState(store, {
                chartLoading: false,
                error: err?.error?.detail || err?.statusText || 'Erreur chargement de la courbe des constantes',
              });
              return of(null);
            }),
          ),
        ),
      ),
    );

    return {
      load,
      loadChart,
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
