import {inject} from '@angular/core';
import {patchState, signalStore, withMethods, withState} from '@ngrx/signals';
import {rxMethod} from '@ngrx/signals/rxjs-interop';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {catchError, of, pipe, switchMap, tap} from 'rxjs';
import {
  DossierMedicalApiService,
  DossierMedicalPatient,
  UpsertDossierMedicalPatientPayload,
} from '../../../core/api/dossier-medical-api.service';

type DossierMedicalState = {
  patientId: string | null;
  dossier: DossierMedicalPatient | null;
  loading: boolean;
  saving: boolean;
  error: string | null;
  notFound: boolean;
};

const initialState: DossierMedicalState = {
  patientId: null,
  dossier: null,
  loading: false,
  saving: false,
  error: null,
  notFound: false,
};

/** Dossier de base du patient (néphropathie, mise en dialyse, sérologies hépatite B/C). */
export const DossierMedicalStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withDevtools('DossierMedicalStore'),
  withMethods((store, api = inject(DossierMedicalApiService)) => ({
    load: rxMethod<{ centerId: string; patientId: string }>(
      pipe(
        tap(({patientId}) => patchState(store, {loading: true, error: null, notFound: false, patientId})),
        switchMap(({centerId, patientId}) =>
          api.getDossier(centerId, patientId).pipe(
            tap((dossier) => patchState(store, {dossier, loading: false})),
            catchError((err: any) => {
              if (err?.status === 404) {
                patchState(store, {dossier: null, loading: false, notFound: true});
                return of(null);
              }
              patchState(store, {
                loading: false,
                error: err?.error?.detail || err?.statusText || 'Erreur chargement du dossier médical',
              });
              return of(null);
            }),
          ),
        ),
      ),
    ),

    save: rxMethod<{ patientId: string; payload: UpsertDossierMedicalPatientPayload }>(
      pipe(
        tap(() => patchState(store, {saving: true, error: null})),
        switchMap(({patientId, payload}) =>
          api.upsertDossier(patientId, payload).pipe(
            tap(() => {
              patchState(store, {
                saving: false,
                notFound: false,
                dossier: {
                  ...(store.dossier() as DossierMedicalPatient),
                  patientId,
                  centerId: payload.centerId,
                  nephropathieInitiale: payload.nephropathieInitiale,
                  dateMiseEnDialyse: payload.dateMiseEnDialyse,
                  hepatiteBStatut: payload.hepatiteBStatut,
                  hepatiteCStatut: payload.hepatiteCStatut,
                  observationGlobale: payload.observationGlobale,
                },
              });
            }),
            catchError((err: any) => {
              patchState(store, {
                saving: false,
                error: err?.error?.detail || err?.statusText || 'Erreur enregistrement du dossier médical',
              });
              return of(null);
            }),
          ),
        ),
      ),
    ),

    reset(): void {
      patchState(store, initialState);
    },
  })),
);
