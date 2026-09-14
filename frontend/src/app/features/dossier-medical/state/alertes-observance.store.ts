import {inject} from '@angular/core';
import {patchState, signalStore, withMethods, withState} from '@ngrx/signals';
import {rxMethod} from '@ngrx/signals/rxjs-interop';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {catchError, of, pipe, switchMap, tap} from 'rxjs';
import {AlerteObservance, DossierMedicalApiService} from '../../../core/api/dossier-medical-api.service';

type AlertesObservanceState = {
  alertes: AlerteObservance[];
  loading: boolean;
  error: string | null;
  activeCenterId: string | null;
  activePatientId: string | null;
};

const initialState: AlertesObservanceState = {
  alertes: [],
  loading: false,
  error: null,
  activeCenterId: null,
  activePatientId: null,
};

/** Alertes de non-respect de la fréquence d'administration EPO/fer prescrite par le médecin. */
export const AlertesObservanceStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withDevtools('AlertesObservanceStore'),
  withMethods((store, api = inject(DossierMedicalApiService)) => {
    const load = rxMethod<{ centerId: string; patientId: string }>(
      pipe(
        tap(({centerId, patientId}) => patchState(store, {
          loading: true,
          error: null,
          activeCenterId: centerId,
          activePatientId: patientId,
        })),
        switchMap(({centerId, patientId}) =>
          api.listAlertesObservance(centerId, patientId).pipe(
            tap((alertes) => patchState(store, {alertes, loading: false})),
            catchError((err: any) => {
              patchState(store, {
                loading: false,
                error: err?.error?.detail || err?.statusText || 'Erreur chargement des alertes d\'observance',
              });
              return of(null);
            }),
          ),
        ),
      ),
    );

    const resoudre = rxMethod<{ alerteId: string }>(
      pipe(
        switchMap(({alerteId}) => {
          const centerId = store.activeCenterId();
          const patientId = store.activePatientId();
          if (!centerId || !patientId) return of(null);
          return api.resoudreAlerteObservance(centerId, patientId, alerteId).pipe(
            tap(() => {
              const centerId2 = store.activeCenterId();
              const patientId2 = store.activePatientId();
              if (centerId2 && patientId2) load({centerId: centerId2, patientId: patientId2});
            }),
            catchError((err: any) => {
              patchState(store, {error: err?.error?.detail || err?.statusText || 'Erreur resolution alerte'});
              return of(null);
            }),
          );
        }),
      ),
    );

    return {
      load,
      resoudre,
      reset(): void {
        patchState(store, initialState);
      },
    };
  }),
);
