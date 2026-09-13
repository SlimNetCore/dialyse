import {inject} from '@angular/core';
import {patchState, signalStore, withMethods, withState} from '@ngrx/signals';
import {rxMethod} from '@ngrx/signals/rxjs-interop';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {catchError, of, pipe, switchMap, tap} from 'rxjs';
import {DossierMedicalApiService, SuiviAnemie} from '../../../core/api/dossier-medical-api.service';

type SuiviAnemieState = {
  suivi: SuiviAnemie | null;
  loading: boolean;
  error: string | null;
  activeCenterId: string | null;
  activePatientId: string | null;
};

const initialState: SuiviAnemieState = {
  suivi: null,
  loading: false,
  error: null,
  activeCenterId: null,
  activePatientId: null,
};

/** Vue agrégée du suivi de l'anémie : courbe Hb/ferritine, évaluations KDIGO, EPO/fer actifs. */
export const SuiviAnemieStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withDevtools('SuiviAnemieStore'),
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
          api.getSuiviAnemie(centerId, patientId).pipe(
            tap((suivi) => patchState(store, {suivi, loading: false})),
            catchError((err: any) => {
              patchState(store, {
                loading: false,
                error: err?.error?.detail || err?.statusText || 'Erreur chargement du suivi de l\'anémie',
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
      load({centerId, patientId});
    };

    return {
      load,
      refresh,
      reset(): void {
        patchState(store, initialState);
      },
    };
  }),
);
