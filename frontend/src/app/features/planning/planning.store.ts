import {computed, inject} from '@angular/core';
import {patchState, signalStore, withComputed, withMethods, withState} from '@ngrx/signals';
import {rxMethod} from '@ngrx/signals/rxjs-interop';
import {HttpErrorResponse} from '@angular/common/http';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {catchError, EMPTY, firstValueFrom, forkJoin, of, pipe, switchMap, tap} from 'rxjs';
import {
  AbsencePatientApiService,
  AbsenceSemaine,
  SeanceRealisee,
  SuiviSemaine,
  DeclarationAbsencePayload,
} from '../../core/api/absence-patient-api.service';
import {InfirmierApiService, SemainePresence} from '../../core/api/infirmier-api.service';
import {absencePatientErrorKey} from '../absences/absences-patients.store';
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
  /** Présence des infirmiers de la semaine affichée (prévus, remplaçants, absents) : null si indisponible. */
  presence: SemainePresence | null;
  /** Absences non annulées de la semaine affichée (colorent les séances absentes). */
  absences: AbsenceSemaine[];
  /** Séances validées par l'infirmier (patient présent) de la semaine affichée : marquées d'un soleil. */
  seancesRealisees: SeanceRealisee[];
  absenceSaving: boolean;
  absenceError: string | null;
  parametres: PlanningParametres | null;
  loading: boolean;
  saving: boolean;
  error: string | null;
  successMessage: string | null;
}

/**
 * Clé i18n de l'erreur d'enregistrement des paramètres du planning : écriture concurrente (409
 * `PLANIFICATION_ECRITURE_CONCURRENTE`), salle d'isolement d'un autre centre (400), sinon message générique.
 */
export function parametresErrorKey(err: unknown): string {
  if (err instanceof HttpErrorResponse) {
    if (err.status === 409 && err.error?.code === 'PLANIFICATION_ECRITURE_CONCURRENTE') return 'PLANNING.PARAMS.ERR.CONCURRENTE';
    if (err.status === 400 && String(err.error?.detail ?? '').includes("salle d'isolement")) {
      return 'PLANNING.PARAMS.ERR.SALLE_ETRANGERE';
    }
  }
  return 'PLANNING.PARAMS.ERR.SAVE';
}

const initialState: PlanningState = {
  date: null,
  semaine: null,
  presence: null,
  absences: [],
  seancesRealisees: [],
  absenceSaving: false,
  absenceError: null,
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
  withMethods((store, api = inject(PlanningApiService), absenceApi = inject(AbsencePatientApiService),
               infirmierApi = inject(InfirmierApiService), shell = inject(AppShellStore)) => {
    const centerId = (): string => shell.currentCenterId() ?? '';

    const chargerSemaine = rxMethod<string | null>(
      pipe(
        tap((date) => patchState(store, {loading: true, error: null, date})),
        switchMap((date) =>
          forkJoin({
            semaine: api.semaine(centerId(), date ?? undefined),
            // Les absences colorent la grille : leur échec ne doit pas empêcher d'afficher le planning.
            suivi: absenceApi.semaine(centerId(), date ?? undefined)
              .pipe(catchError(() => of({absences: [], seancesRealisees: []} as SuiviSemaine))),
            // Les infirmiers complètent la grille : sans eux, le planning des patients reste affiché.
            presence: infirmierApi.semaine(centerId(), date ?? undefined)
              .pipe(catchError(() => of(null as SemainePresence | null))),
          }).pipe(
            tap(({semaine, suivi, presence}) => patchState(store, {
              semaine, presence, absences: suivi.absences, seancesRealisees: suivi.seancesRealisees, loading: false,
            })),
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

      /**
       * Déclare l'absence d'un patient à une séance du planning, déjà qualifiée par son motif, puis recharge la
       * semaine pour colorer la case. Renvoie vrai en cas de succès ; sinon `absenceError` porte la clé du message.
       */
      async declarerAbsence(payload: DeclarationAbsencePayload): Promise<boolean> {
        patchState(store, {absenceSaving: true, absenceError: null});
        try {
          await firstValueFrom(absenceApi.declarer(centerId(), payload));
          patchState(store, {absenceSaving: false});
          chargerSemaine(store.date());
          return true;
        } catch (err) {
          patchState(store, {absenceSaving: false, absenceError: absencePatientErrorKey(err)});
          return false;
        }
      },

      clearAbsenceError(): void {
        patchState(store, {absenceError: null});
      },

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
              catchError((err) => {
                patchState(store, {saving: false, error: parametresErrorKey(err)});
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
