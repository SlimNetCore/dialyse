import {computed, inject} from '@angular/core';
import {patchState, signalStore, withComputed, withMethods, withState} from '@ngrx/signals';
import {rxMethod} from '@ngrx/signals/rxjs-interop';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {catchError, EMPTY, pipe, switchMap, tap} from 'rxjs';
import {
  PlanningApiService,
  PlanningParametres,
  SemainePlanning,
} from '../../core/api/planning-api.service';
import {AppShellStore} from '../../core/state/app-shell.store';
import {decalerJours} from './planning.util';

interface PlanningState {
  /** Un jour de la semaine affichée (`yyyy-MM-dd`) ; null = semaine courante. */
  date: string | null;
  semaine: SemainePlanning | null;
  parametres: PlanningParametres | null;
  loading: boolean;
  saving: boolean;
  error: string | null;
  successMessage: string | null;
}

const initialState: PlanningState = {
  date: null,
  semaine: null,
  parametres: null,
  loading: false,
  saving: false,
  error: null,
  successMessage: null,
};

/**
 * Planning du centre actif : semaine réelle (qui dialyse où, fermetures, conflits) et paramétrage (jours d'ouverture,
 * salles d'isolement). Toute requête porte le centre courant.
 */
export const PlanningStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withDevtools('PlanningStore'),
  withComputed((store) => ({
    nbConflits: computed(() => store.semaine()?.conflits.length ?? 0),
  })),
  withMethods((store, api = inject(PlanningApiService), shell = inject(AppShellStore)) => {
    const centerId = (): string => shell.currentCenterId() ?? '';

    const chargerSemaine = rxMethod<string | null>(
      pipe(
        tap((date) => patchState(store, {loading: true, error: null, date})),
        switchMap((date) =>
          api.semaine(centerId(), date ?? undefined).pipe(
            tap((semaine) => patchState(store, {semaine, loading: false})),
            catchError(() => {
              patchState(store, {semaine: null, loading: false, error: 'PLANNING.SEMAINE.ERR.LOAD'});
              return EMPTY;
            }),
          ),
        ),
      ),
    );

    return {
      chargerSemaine,

      /** Semaine précédente (-1) ou suivante (+1) de celle affichée. */
      changerSemaine(delta: number): void {
        const debut = store.semaine()?.debut;
        if (debut) chargerSemaine(decalerJours(debut, 7 * delta));
      },

      chargerParametres: rxMethod<void>(
        pipe(
          tap(() => patchState(store, {loading: true, error: null})),
          switchMap(() =>
            api.parametres(centerId()).pipe(
              tap((parametres) => patchState(store, {parametres, loading: false})),
              catchError(() => {
                patchState(store, {loading: false, error: 'PLANNING.PARAMS.ERR.LOAD'});
                return EMPTY;
              }),
            ),
          ),
        ),
      ),

      enregistrerParametres: rxMethod<PlanningParametres>(
        pipe(
          tap(() => patchState(store, {saving: true, error: null, successMessage: null})),
          switchMap((payload) =>
            api.enregistrerParametres(centerId(), payload).pipe(
              tap((parametres) => patchState(store, {
                parametres, saving: false, successMessage: 'PLANNING.PARAMS.SAVED_OK',
              })),
              catchError(() => {
                patchState(store, {saving: false, error: 'PLANNING.PARAMS.ERR.SAVE'});
                return EMPTY;
              }),
            ),
          ),
        ),
      ),

      clearMessages(): void {
        patchState(store, {error: null, successMessage: null});
      },
    };
  }),
);
