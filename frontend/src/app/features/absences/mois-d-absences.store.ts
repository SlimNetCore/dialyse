import {computed, inject} from '@angular/core';
import {HttpErrorResponse} from '@angular/common/http';
import {patchState, signalStore, withComputed, withMethods, withState} from '@ngrx/signals';
import {rxMethod} from '@ngrx/signals/rxjs-interop';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {catchError, EMPTY, map, mergeMap, of, pipe, switchMap, tap} from 'rxjs';
import {AbsencePatientApiService, MoisDAbsences} from '../../core/api/absence-patient-api.service';
import {AppShellStore} from '../../core/state/app-shell.store';
import {douzeMois, indexerLesMois, moisDeLaDate} from './mois-d-absences.util';

type MoisDAbsencesState = {
  /** Mois chargés, par AAAA-MM (cumul des années demandées). */
  mois: Record<string, MoisDAbsences>;
  annee: number;
  loading: boolean;
  /** Mois en cours de clôture ou de réouverture. */
  actionMois: string | null;
  /** Avertissement chiffré de la dernière clôture. */
  avertissement: { mois: string; aQualifier: number; enRetard: number } | null;
  successMessage: string | null;
  error: string | null;
};

const initialState: MoisDAbsencesState = {
  mois: {},
  annee: new Date().getFullYear(),
  loading: false,
  actionMois: null,
  avertissement: null,
  successMessage: null,
  error: null,
};

const CODES_CONNUS = [
  'MOIS_D_ABSENCES_NON_TERMINE', 'MOIS_D_ABSENCES_DEJA_CLOS', 'MOIS_D_ABSENCES_NON_CLOS', 'MOTIF_DE_REOUVERTURE_OBLIGATOIRE',
  'MOIS_D_ABSENCES_CLOS', 'ABSENCE_ECRITURE_CONCURRENTE', 'MOIS_INVALIDE', 'ANNEE_INVALIDE',
];

/** Clé i18n de l'erreur d'une clôture ou d'une réouverture : code métier connu, sinon message générique. */
export function moisDAbsencesErrorKey(err: unknown): string {
  const code = err instanceof HttpErrorResponse ? err.error?.code : undefined;
  return CODES_CONNUS.includes(code) ? `ABSENCES.ERR.${code}` : 'ABSENCES.MOIS.ERR_GENERIQUE';
}

/**
 * États des mois d'absences du centre actif (A6). Lu par l'écran des absences pour désactiver les actions d'un mois clos, et
 * par l'écran de gestion des mois (ADMIN) pour clôturer et rouvrir.
 */
export const MoisDAbsencesStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withDevtools('MoisDAbsencesStore'),
  withComputed((store) => ({
    moisDeLAnnee: computed(() => douzeMois(store.annee(), store.mois())),
  })),
  withMethods((store, api = inject(AbsencePatientApiService), shell = inject(AppShellStore)) => {
    const centerId = (): string => shell.currentCenterId() ?? '';

    const charger = rxMethod<{ annee: number }>(
      pipe(
        tap(() => patchState(store, {loading: true})),
        // mergeMap : plusieurs années peuvent se charger ensemble (les lignes de la liste couvrent parfois deux années)
        mergeMap(({annee}) => api.moisDeLAnnee(centerId(), annee).pipe(
          tap((reponse) => patchState(store, {
            mois: {...store.mois(), ...indexerLesMois(reponse.mois)},
            loading: false
          })),
          catchError(() => {
            // la lecture n'est qu'une aide : le serveur reste juge, on ne bloque pas l'écran des absences
            patchState(store, {loading: false});
            return EMPTY;
          }),
        )),
      ),
    );

    /** Recharge l'année du mois après une action, puis rend l'état. */
    const recharger = (mois: string) => api.moisDeLAnnee(centerId(), Number(mois.slice(0, 4))).pipe(
      map((reponse) => reponse.mois),
      catchError(() => of(null)),
    );

    return {
      charger,

      /** Vrai si le mois de la date ISO est clos (faux tant que son année n'est pas chargée : le serveur reste juge). */
      moisDeLaDateEstClos(dateIso: string): boolean {
        return store.mois()[moisDeLaDate(dateIso)]?.statut === 'CLOTURE';
      },

      setAnnee(annee: number): void {
        patchState(store, {annee});
        charger({annee});
      },

      cloturer: rxMethod<{ mois: string }>(pipe(
        tap(({mois}) => patchState(store, {
          actionMois: mois, error: null, successMessage: null, avertissement: null,
        })),
        switchMap(({mois}) => api.cloturerMois(centerId(), mois).pipe(
          switchMap((reponse) => recharger(mois).pipe(map((liste) => ({reponse, liste})))),
          tap(({reponse, liste}) => patchState(store, {
            actionMois: null,
            successMessage: 'ABSENCES.MOIS.SUCCES_CLOTURE',
            avertissement: {mois, aQualifier: reponse.absencesAQualifierRestantes, enRetard: reponse.absencesEnRetard},
            mois: {
              ...store.mois(),
              ...(liste ? indexerLesMois(liste) : {
                [mois]: {
                  ...(store.mois()[mois] ?? {rouvertPar: null, rouvertLe: null, motifDeReouverture: null}),
                  mois, statut: 'CLOTURE', cloturePar: reponse.cloturePar, clotureLe: reponse.clotureLe,
                } as MoisDAbsences,
              }),
            },
          })),
          catchError((err) => {
            patchState(store, {actionMois: null, error: moisDAbsencesErrorKey(err)});
            return EMPTY;
          }),
        )),
      )),

      rouvrir: rxMethod<{ mois: string; motif: string }>(pipe(
        tap(({mois}) => patchState(store, {
          actionMois: mois, error: null, successMessage: null, avertissement: null,
        })),
        switchMap(({mois, motif}) => api.rouvrirMois(centerId(), mois, motif).pipe(
          switchMap(() => recharger(mois)),
          tap((liste) => patchState(store, {
            actionMois: null,
            successMessage: 'ABSENCES.MOIS.SUCCES_REOUVERTURE',
            mois: {
              ...store.mois(),
              ...(liste ? indexerLesMois(liste) : {
                [mois]: {...(store.mois()[mois] as MoisDAbsences), statut: 'OUVERT'} as MoisDAbsences,
              }),
            },
          })),
          catchError((err) => {
            patchState(store, {actionMois: null, error: moisDAbsencesErrorKey(err)});
            return EMPTY;
          }),
        )),
      )),

      effacerLesMessages(): void {
        patchState(store, {error: null, successMessage: null, avertissement: null});
      },
    };
  }),
);
