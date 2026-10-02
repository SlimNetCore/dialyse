import {computed, inject} from '@angular/core';
import {patchState, signalStore, withComputed, withMethods, withState} from '@ngrx/signals';
import {rxMethod} from '@ngrx/signals/rxjs-interop';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {catchError, EMPTY, forkJoin, pipe, switchMap, tap} from 'rxjs';
import {InfirmierApiService, SemainePresence} from '../../core/api/infirmier-api.service';
import {PlanningApiService, SemainePlanning} from '../../core/api/planning-api.service';
import {AppShellStore} from '../../core/state/app-shell.store';
import {aujourdhuiUtc} from '../infirmier/presence.util';
import {construireJournee} from './medecin-dashboard.util';

interface MedecinDashboardState {
  /** Jour affiché (`yyyy-MM-dd`, UTC comme le serveur). */
  date: string;
  planning: SemainePlanning | null;
  presence: SemainePresence | null;
  loading: boolean;
  error: string | null;
}

const initialState: MedecinDashboardState = {
  date: aujourdhuiUtc(),
  planning: null,
  presence: null,
  loading: false,
  error: null,
};

/**
 * Tableau de bord du médecin : planning du jour des patients (placements du centre) et des infirmiers (présence,
 * absences, remplaçants). Lecture seule ; toute requête porte le centre courant.
 */
export const MedecinDashboardStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withDevtools('MedecinDashboardStore'),
  withComputed((store) => ({
    journee: computed(() => {
      const planning = store.planning();
      const presence = store.presence();
      return planning && presence ? construireJournee(planning, presence, store.date()) : null;
    }),
  })),
  withMethods((store, infirmiers = inject(InfirmierApiService), planning = inject(PlanningApiService),
               shell = inject(AppShellStore)) => ({
    charger: rxMethod<void>(
      pipe(
        tap(() => patchState(store, {loading: true, error: null, date: aujourdhuiUtc()})),
        switchMap(() => {
          const centerId = shell.currentCenterId() ?? '';
          return forkJoin({
            planning: planning.semaine(centerId, store.date()),
            presence: infirmiers.semaine(centerId, store.date()),
          }).pipe(
            tap(({planning: p, presence}) => patchState(store, {planning: p, presence, loading: false})),
            catchError(() => {
              patchState(store, {planning: null, presence: null, loading: false, error: 'MEDECIN.DASHBOARD.ERR.LOAD'});
              return EMPTY;
            }),
          );
        }),
      ),
    ),
  })),
);
