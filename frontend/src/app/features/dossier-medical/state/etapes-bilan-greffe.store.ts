import {inject} from '@angular/core';
import {patchState, signalStore, withMethods, withState} from '@ngrx/signals';
import {rxMethod} from '@ngrx/signals/rxjs-interop';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {catchError, of, pipe, switchMap, tap} from 'rxjs';
import {
  DossierMedicalApiService,
  EtapeBilanGreffe,
  UpdateEtapeBilanGreffePayload,
} from '../../../core/api/dossier-medical-api.service';

type EtapesBilanGreffeState = {
  etapes: EtapeBilanGreffe[];
  loading: boolean;
  saving: boolean;
  error: string | null;
  activeCenterId: string | null;
  activePatientId: string | null;
};

const initialState: EtapesBilanGreffeState = {
  etapes: [],
  loading: false,
  saving: false,
  error: null,
  activeCenterId: null,
  activePatientId: null,
};

/** Checklist du bilan pré-greffe rénale (receveur) : étapes standard + ajout libre par le médecin. */
export const EtapesBilanGreffeStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withDevtools('EtapesBilanGreffeStore'),
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
          api.listEtapesBilanGreffe(centerId, patientId).pipe(
            tap((etapes) => patchState(store, {etapes, loading: false})),
            catchError((err: any) => {
              patchState(store, {
                loading: false,
                error: err?.error?.detail || err?.statusText || 'Erreur chargement de la checklist',
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

    const genererEtapesStandard = () => {
      const centerId = store.activeCenterId();
      const patientId = store.activePatientId();
      if (!centerId || !patientId) return;
      patchState(store, {saving: true, error: null});
      api.genererEtapesStandard(patientId, centerId).subscribe({
        next: (etapes) => patchState(store, {etapes, saving: false}),
        error: (err: any) => patchState(store, {
          saving: false,
          error: err?.error?.detail || err?.statusText || 'Erreur génération de la checklist standard',
        }),
      });
    };

    const create = (categorie: string, libelle: string) => {
      const centerId = store.activeCenterId();
      const patientId = store.activePatientId();
      if (!centerId || !patientId) return;
      patchState(store, {saving: true, error: null});
      api.createEtapeBilanGreffe(patientId, centerId, categorie, libelle).subscribe({
        next: () => {
          patchState(store, {saving: false});
          refresh();
        },
        error: (err: any) => patchState(store, {
          saving: false,
          error: err?.error?.detail || err?.statusText || 'Erreur ajout de l\'étape',
        }),
      });
    };

    const update = (etapeId: string, payload: UpdateEtapeBilanGreffePayload) => {
      const patientId = store.activePatientId();
      if (!patientId) return;
      patchState(store, {saving: true, error: null});
      api.updateEtapeBilanGreffe(patientId, etapeId, payload).subscribe({
        next: () => {
          patchState(store, {saving: false});
          refresh();
        },
        error: (err: any) => patchState(store, {
          saving: false,
          error: err?.error?.detail || err?.statusText || 'Erreur mise à jour de l\'étape',
        }),
      });
    };

    const remove = (etapeId: string) => {
      const centerId = store.activeCenterId();
      const patientId = store.activePatientId();
      if (!centerId || !patientId) return;
      api.deleteEtapeBilanGreffe(patientId, etapeId, centerId).subscribe({
        next: () => refresh(),
        error: (err: any) => patchState(store, {
          error: err?.error?.detail || err?.statusText || 'Erreur suppression de l\'étape',
        }),
      });
    };

    return {
      load,
      refresh,
      genererEtapesStandard,
      create,
      update,
      remove,
      reset(): void {
        patchState(store, initialState);
      },
    };
  }),
);
