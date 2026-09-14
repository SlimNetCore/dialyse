import {inject} from '@angular/core';
import {patchState, signalStore, withMethods, withState} from '@ngrx/signals';
import {rxMethod} from '@ngrx/signals/rxjs-interop';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {catchError, of, pipe, switchMap, tap} from 'rxjs';
import {
  DonneurVivant,
  DossierMedicalApiService,
  UpsertDonneurVivantPayload,
} from '../../../core/api/dossier-medical-api.service';

type DonneursVivantsState = {
  donneurs: DonneurVivant[];
  loading: boolean;
  saving: boolean;
  error: string | null;
  activeCenterId: string | null;
  activePatientId: string | null;
};

const initialState: DonneursVivantsState = {
  donneurs: [],
  loading: false,
  saving: false,
  error: null,
  activeCenterId: null,
  activePatientId: null,
};

/** Candidats donneurs vivants pour la greffe rénale d'un patient receveur. */
export const DonneursVivantsStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withDevtools('DonneursVivantsStore'),
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
          api.listDonneursVivants(centerId, patientId).pipe(
            tap((donneurs) => patchState(store, {donneurs, loading: false})),
            catchError((err: any) => {
              patchState(store, {
                loading: false,
                error: err?.error?.detail || err?.statusText || 'Erreur chargement des donneurs',
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

    const create = (payload: UpsertDonneurVivantPayload) => {
      const patientId = store.activePatientId();
      if (!patientId) return;
      patchState(store, {saving: true, error: null});
      api.createDonneurVivant(patientId, payload).subscribe({
        next: () => {
          patchState(store, {saving: false});
          refresh();
        },
        error: (err: any) => patchState(store, {
          saving: false,
          error: err?.error?.detail || err?.statusText || 'Erreur ajout du donneur',
        }),
      });
    };

    const update = (donneurId: string, payload: UpsertDonneurVivantPayload) => {
      const patientId = store.activePatientId();
      if (!patientId) return;
      patchState(store, {saving: true, error: null});
      api.updateDonneurVivant(patientId, donneurId, payload).subscribe({
        next: () => {
          patchState(store, {saving: false});
          refresh();
        },
        error: (err: any) => patchState(store, {
          saving: false,
          error: err?.error?.detail || err?.statusText || 'Erreur mise à jour du donneur',
        }),
      });
    };

    const remove = (donneurId: string) => {
      const centerId = store.activeCenterId();
      const patientId = store.activePatientId();
      if (!centerId || !patientId) return;
      api.deleteDonneurVivant(patientId, donneurId, centerId).subscribe({
        next: () => refresh(),
        error: (err: any) => patchState(store, {
          error: err?.error?.detail || err?.statusText || 'Erreur suppression du donneur',
        }),
      });
    };

    return {
      load,
      refresh,
      create,
      update,
      remove,
      reset(): void {
        patchState(store, initialState);
      },
    };
  }),
);
