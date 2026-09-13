import {inject} from '@angular/core';
import {patchState, signalStore, withMethods, withState} from '@ngrx/signals';
import {rxMethod} from '@ngrx/signals/rxjs-interop';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {catchError, of, pipe, switchMap, tap} from 'rxjs';
import {
  AdministrationTraitement,
  CreateAdministrationTraitementPayload,
  DossierMedicalApiService,
} from '../../../core/api/dossier-medical-api.service';
import {createPagedListState, PagedListState} from '../../../core/state/paged-list-state.util';

type AdministrationsAnemieState = PagedListState<AdministrationTraitement> & {
  error: string | null;
  activeCenterId: string | null;
  activePatientId: string | null;
};

const initialState: AdministrationsAnemieState = {
  ...createPagedListState<AdministrationTraitement>({pageSize: 10}),
  error: null,
  activeCenterId: null,
  activePatientId: null,
};

/** Historique des administrations réelles du traitement de l'anémie (EPO, fer injectable). */
export const AdministrationsAnemieStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withDevtools('AdministrationsAnemieStore'),
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
          api.listAdministrationsAnemie(centerId, patientId, page, size).pipe(
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
                error: err?.error?.detail || err?.statusText || 'Erreur chargement des administrations',
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

    const create = rxMethod<{ patientId: string; payload: CreateAdministrationTraitementPayload }>(
      pipe(
        tap(() => patchState(store, {error: null})),
        switchMap(({patientId, payload}) =>
          api.createAdministrationAnemie(patientId, payload).pipe(
            tap(() => refresh()),
            catchError((err: any) => {
              patchState(store, {error: err?.error?.detail || err?.statusText || 'Erreur enregistrement'});
              return of(null);
            }),
          ),
        ),
      ),
    );

    return {
      load,
      create,
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
