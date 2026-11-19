import {inject} from '@angular/core';
import {patchState, signalStore, withMethods, withState} from '@ngrx/signals';
import {rxMethod} from '@ngrx/signals/rxjs-interop';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {catchError, of, pipe, switchMap, tap} from 'rxjs';
import {BackendApiService, PatientSummary} from '../../../core/api/backend-api.service';
import {DashboardStats} from '../../dashboard/state/dashboard.types';

function currentYearMonth(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
}

/** Échéance par défaut des indicateurs « expirant sous N jours » (même valeur que l'accueil du centre). */
export const ECHEANCE_PAR_DEFAUT_JOURS = 30;

type PatientTableauDeBordState = {
  /** Synthèse mensuelle des patients (sorties de la liste des patients). */
  summaryMonth: string;
  summary: PatientSummary | null;
  summaryLoading: boolean;
  summaryError: string | null;
  /** Impression du rapport mensuel (document LISTE_PATIENTS). */
  printingReport: boolean;
  printError: string | null;
  /** Indicateurs PEC et attestations (repris de l'accueil du centre). */
  expirationDays: number;
  pecStats: DashboardStats | null;
  pecStatsLoading: boolean;
};

const initialState: PatientTableauDeBordState = {
  summaryMonth: currentYearMonth(),
  summary: null,
  summaryLoading: false,
  summaryError: null,
  printingReport: false,
  printError: null,
  expirationDays: ECHEANCE_PAR_DEFAUT_JOURS,
  pecStats: null,
  pecStatsLoading: false,
};

/**
 * État du tableau de bord du module Patients : synthèse mensuelle et indicateurs PEC / attestations. Fourni par le
 * composant (pas de singleton) : la liste des patients ne porte plus aucun état de synthèse.
 */
export const PatientTableauDeBordStore = signalStore(
  withState(initialState),
  withDevtools('PatientTableauDeBordStore'),
  withMethods((store, api = inject(BackendApiService)) => ({
    loadSummary: rxMethod<{ centerId: string; month: string }>(
      pipe(
        tap(() => patchState(store, {summaryLoading: true, summaryError: null})),
        switchMap(({centerId, month}) =>
          api.getPatientSummary(centerId, month).pipe(
            tap((summary) => patchState(store, {summary, summaryLoading: false, summaryError: null})),
            catchError((err: any) => {
              patchState(store, {
                summary: null,
                summaryLoading: false,
                summaryError: err?.error?.message || err?.statusText || 'Erreur chargement synthèse patients'
              });
              return of(null);
            })
          )
        )
      )
    ),

    loadPecStats: rxMethod<{ centerId: string; expirationDays: number }>(
      pipe(
        tap(() => patchState(store, {pecStatsLoading: true})),
        switchMap(({centerId, expirationDays}) =>
          api.getDashboardStats(centerId, expirationDays).pipe(
            tap((pecStats) => patchState(store, {pecStats, pecStatsLoading: false})),
            catchError(() => {
              // Comme l'accueil du centre : on garde les derniers chiffres sur une erreur passagère.
              patchState(store, {pecStatsLoading: false});
              return of(null);
            })
          )
        )
      )
    ),

    /** Rapport mensuel imprimable (même document que l'ancien bouton de la liste). */
    printReport: rxMethod<{ centerId: string }>(
      pipe(
        tap(() => patchState(store, {printingReport: true, printError: null})),
        switchMap(({centerId}) =>
          api.printDocument(centerId, 'LISTE_PATIENTS', {}).pipe(
            tap((blob: Blob) => {
              window.open(URL.createObjectURL(blob), '_blank');
              patchState(store, {printingReport: false});
            }),
            catchError((err: any) => {
              patchState(store, {
                printingReport: false,
                printError: 'Erreur impression: ' + (err?.error?.message || err?.statusText)
              });
              return of(null);
            })
          )
        )
      )
    ),

    setSummaryMonth(summaryMonth: string): void {
      if (!/^\d{4}-\d{2}$/.test(summaryMonth)) {
        return;
      }
      patchState(store, {summaryMonth});
    },

    setExpirationDays(expirationDays: number): void {
      const safeDays = Number.isFinite(expirationDays) && expirationDays > 0 ? Math.trunc(expirationDays) : 1;
      patchState(store, {expirationDays: safeDays});
    },

    /** Sans centre : rien à afficher. */
    reset(): void {
      patchState(store, {summary: null, summaryLoading: false, summaryError: null, pecStats: null,
        pecStatsLoading: false});
    },
  }))
);
