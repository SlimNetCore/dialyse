import {computed, inject} from '@angular/core';
import {HttpErrorResponse} from '@angular/common/http';
import {patchState, signalStore, withComputed, withMethods, withState} from '@ngrx/signals';
import {rxMethod} from '@ngrx/signals/rxjs-interop';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {catchError, EMPTY, Observable, pipe, switchMap, tap} from 'rxjs';
import {
  AlertePresence,
  CandidatRemplacement,
  ChargeInfirmier,
  InfirmierApiService,
  SemainePresence,
} from '../../core/api/infirmier-api.service';
import {JourSemaine} from '../../core/api/planning-api.service';
import {AppShellStore} from '../../core/state/app-shell.store';
import {decalerJours} from '../planning/planning.util';
import {moisCourant} from './presence.util';

/** Horizon (jours) des alertes de sous-effectif. */
export const HORIZON_ALERTES = 14;

export interface CaseSelection {
  date: string;
  jour: JourSemaine;
  salleId: string;
  creneauId: string;
}

interface PresenceState {
  semaine: SemainePresence | null;
  alertes: AlertePresence[];
  selection: CaseSelection | null;
  candidats: CandidatRemplacement[];
  charge: ChargeInfirmier[];
  chargeMois: string;
  chargeTotal: number;
  chargePageIndex: number;
  chargePageSize: number;
  chargeMoyenne: number;
  loading: boolean;
  loadingCandidats: boolean;
  loadingCharge: boolean;
  saving: boolean;
  error: string | null;
  successMessage: string | null;
}

const initialState: PresenceState = {
  semaine: null,
  alertes: [],
  selection: null,
  candidats: [],
  charge: [],
  chargeMois: moisCourant(),
  chargeTotal: 0,
  chargePageIndex: 0,
  chargePageSize: 20,
  chargeMoyenne: 0,
  loading: false,
  loadingCandidats: false,
  loadingCharge: false,
  saving: false,
  error: null,
  successMessage: null,
};

export function presenceErrorKey(err: unknown): string {
  const code = err instanceof HttpErrorResponse ? err.error?.code : undefined;
  return code === 'REMPLACEMENT_INELIGIBLE' || code === 'REMPLACEMENT_INTROUVABLE'
    ? `INFIRMIER.ERR.${code}` : 'INFIRMIER.ERR.SAVE';
}

/**
 * Planning de présence des infirmiers du centre actif : semaine, alertes de sous-effectif, remplaçants proposés pour
 * la case sélectionnée et charge mensuelle. Toute requête porte le centre courant.
 */
export const PresenceStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withDevtools('PresenceStore'),
  withComputed((store) => ({
    nbConflits: computed(() => store.semaine()?.conflits.length ?? 0),
  })),
  withMethods((store, api = inject(InfirmierApiService), shell = inject(AppShellStore)) => {
    const centerId = (): string => shell.currentCenterId() ?? '';

    const chargerSemaine = rxMethod<string | null>(
      pipe(
        tap(() => patchState(store, {loading: true, error: null})),
        switchMap((date) =>
          api.semaine(centerId(), date ?? undefined).pipe(
            tap((semaine) => patchState(store, {semaine, loading: false})),
            catchError(() => {
              patchState(store, {semaine: null, loading: false, error: 'INFIRMIER.PRESENCE.ERR.LOAD'});
              return EMPTY;
            }),
          ),
        ),
      ),
    );

    const chargerAlertes = rxMethod<void>(
      pipe(
        switchMap(() =>
          api.alertes(centerId(), HORIZON_ALERTES).pipe(
            tap((alertes) => patchState(store, {alertes})),
            catchError(() => {
              patchState(store, {alertes: []});
              return EMPTY;
            }),
          ),
        ),
      ),
    );

    const chargerCandidats = rxMethod<CaseSelection | null>(
      pipe(
        tap((sel) => patchState(store, {selection: sel, candidats: [], loadingCandidats: !!sel})),
        switchMap((sel) => {
          if (!sel) return EMPTY;
          return api.remplacants(centerId(), sel.date, sel.salleId, sel.creneauId).pipe(
            tap((candidats) => patchState(store, {candidats, loadingCandidats: false})),
            catchError(() => {
              patchState(store, {candidats: [], loadingCandidats: false, error: 'INFIRMIER.PRESENCE.ERR.CANDIDATS'});
              return EMPTY;
            }),
          );
        }),
      ),
    );

    /** Après une modification : recharge la semaine affichée, les alertes et les candidats de la case ouverte. */
    function rafraichir(): void {
      chargerSemaine(store.semaine()?.debut ?? null);
      chargerAlertes();
      chargerCandidats(store.selection());
    }

    function saveFlow<T>(request: Observable<T>) {
      return request.pipe(
        tap(() => {
          patchState(store, {saving: false, successMessage: 'INFIRMIER.PRESENCE.SAVED_OK'});
          rafraichir();
        }),
        catchError((err) => {
          patchState(store, {saving: false, error: presenceErrorKey(err)});
          return EMPTY;
        }),
      );
    }

    const begin = () => patchState(store, {saving: true, error: null, successMessage: null});

    return {
      chargerSemaine,
      chargerAlertes,
      selectionnerCase: chargerCandidats,

      /** Semaine précédente (-1) ou suivante (+1) de celle affichée. */
      changerSemaine(delta: number): void {
        const debut = store.semaine()?.debut;
        if (debut) chargerSemaine(decalerJours(debut, 7 * delta));
      },

      affecter: rxMethod<{ infirmierId: string; remplaceId: string | null }>(pipe(tap(begin),
        switchMap(({infirmierId, remplaceId}) => {
          const sel = store.selection();
          if (!sel) {
            patchState(store, {saving: false});
            return EMPTY;
          }
          return saveFlow(api.affecterRemplacement(centerId(), {
            date: sel.date, salleId: sel.salleId, creneauId: sel.creneauId, infirmierId, remplaceId,
          }));
        }))),

      annuler: rxMethod<string>(pipe(tap(begin),
        switchMap((id) => saveFlow(api.annulerRemplacement(centerId(), id))))),

      chargerCharge: rxMethod<{ mois: string; page: number; size: number }>(
        pipe(
          tap(({mois, page, size}) => patchState(store, {
            loadingCharge: true, error: null, chargeMois: mois, chargePageIndex: page, chargePageSize: size,
          })),
          switchMap(({mois, page, size}) =>
            api.charge(centerId(), mois, page, size).pipe(
              tap((res) => patchState(store, {
                charge: res.items ?? [], chargeTotal: res.total ?? 0, chargePageIndex: res.page ?? page,
                chargeMoyenne: res.moyenne ?? 0, loadingCharge: false,
              })),
              catchError(() => {
                patchState(store, {
                  charge: [],
                  chargeTotal: 0,
                  loadingCharge: false,
                  error: 'INFIRMIER.CHARGE.ERR.LOAD'
                });
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
