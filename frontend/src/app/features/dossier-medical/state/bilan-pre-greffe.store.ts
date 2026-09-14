import {inject} from '@angular/core';
import {patchState, signalStore, withMethods, withState} from '@ngrx/signals';
import {rxMethod} from '@ngrx/signals/rxjs-interop';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {catchError, of, pipe, switchMap, tap} from 'rxjs';
import {BilanPreGreffe, DossierMedicalApiService} from '../../../core/api/dossier-medical-api.service';

type BilanPreGreffeState = {
  bilan: BilanPreGreffe | null;
  loading: boolean;
  saving: boolean;
  error: string | null;
  activeCenterId: string | null;
  activePatientId: string | null;
};

const initialState: BilanPreGreffeState = {
  bilan: null,
  loading: false,
  saving: false,
  error: null,
  activeCenterId: null,
  activePatientId: null,
};

/** Bilan de préparation à la greffe rénale (receveur) : statut, bilan immunologique, décisions de RCP. */
export const BilanPreGreffeStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withDevtools('BilanPreGreffeStore'),
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
          api.getBilanPreGreffe(centerId, patientId).pipe(
            tap((bilan) => patchState(store, {bilan, loading: false})),
            catchError((err: any) => {
              patchState(store, {
                loading: false,
                error: err?.error?.detail || err?.statusText || 'Erreur chargement du bilan pré-greffe',
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

    const changerStatut = (statut: string) => {
      const centerId = store.activeCenterId();
      const patientId = store.activePatientId();
      if (!centerId || !patientId) return;
      patchState(store, {saving: true, error: null});
      api.changerStatutBilanGreffe(patientId, centerId, statut).subscribe({
        next: (bilan) => patchState(store, {bilan, saving: false}),
        error: (err: any) => patchState(store, {
          saving: false,
          error: err?.error?.detail || err?.statusText || 'Erreur changement de statut',
        }),
      });
    };

    const mettreAJourBilanImmunologique = (groupeSanguinConfirme: string | null, typageHla: string | null,
                                           praClasseI: number | null, praClasseII: number | null) => {
      const centerId = store.activeCenterId();
      const patientId = store.activePatientId();
      if (!centerId || !patientId) return;
      patchState(store, {saving: true, error: null});
      api.mettreAJourBilanImmunologique(patientId, centerId, groupeSanguinConfirme, typageHla, praClasseI, praClasseII)
        .subscribe({
          next: (bilan) => patchState(store, {bilan, saving: false}),
          error: (err: any) => patchState(store, {
            saving: false,
            error: err?.error?.detail || err?.statusText || 'Erreur enregistrement du bilan immunologique',
          }),
        });
    };

    const mettreAJourNotes = (contreIndications: string | null, conclusionNephrologue: string | null) => {
      const centerId = store.activeCenterId();
      const patientId = store.activePatientId();
      if (!centerId || !patientId) return;
      patchState(store, {saving: true, error: null});
      api.mettreAJourNotesGreffe(patientId, centerId, contreIndications, conclusionNephrologue).subscribe({
        next: (bilan) => patchState(store, {bilan, saving: false}),
        error: (err: any) => patchState(store, {
          saving: false,
          error: err?.error?.detail || err?.statusText || 'Erreur enregistrement des notes',
        }),
      });
    };

    const ajouterDecisionRcp = (dateReunion: string, avis: string, compteRendu: string | null,
                                prochaineDateRevue: string | null) => {
      const centerId = store.activeCenterId();
      const patientId = store.activePatientId();
      if (!centerId || !patientId) return;
      patchState(store, {saving: true, error: null});
      api.ajouterDecisionRcp(patientId, centerId, dateReunion, avis, compteRendu, prochaineDateRevue).subscribe({
        next: (bilan) => patchState(store, {bilan, saving: false}),
        error: (err: any) => patchState(store, {
          saving: false,
          error: err?.error?.detail || err?.statusText || 'Erreur ajout de la décision de RCP',
        }),
      });
    };

    return {
      load,
      refresh,
      changerStatut,
      mettreAJourBilanImmunologique,
      mettreAJourNotes,
      ajouterDecisionRcp,
      reset(): void {
        patchState(store, initialState);
      },
    };
  }),
);
