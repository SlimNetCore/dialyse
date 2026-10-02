import {HttpErrorResponse} from '@angular/common/http';
import {inject} from '@angular/core';
import {patchState, signalStore, withMethods, withState} from '@ngrx/signals';
import {rxMethod} from '@ngrx/signals/rxjs-interop';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {catchError, EMPTY, Observable, pipe, switchMap, tap} from 'rxjs';
import {
  AbsenceInfirmier,
  InfirmierApiService,
  MonAbsencePayload,
  MonPlanning
} from '../../core/api/infirmier-api.service';
import {AppShellStore} from '../../core/state/app-shell.store';
import {createPagedListState, PagedListState} from '../../core/state/paged-list-state.util';
import {decalerJours} from '../planning/planning.util';

type MonPlanningState = PagedListState<AbsenceInfirmier> & {
  planning: MonPlanning | null;
  /** Le compte connecté n'est relié à aucune fiche infirmier du centre. */
  nonLie: boolean;
  loadingPlanning: boolean;
  saving: boolean;
  error: string | null;
  successMessage: string | null;
};

const initialState: MonPlanningState = {
  ...createPagedListState<AbsenceInfirmier>({pageSize: 10}),
  planning: null,
  nonLie: false,
  loadingPlanning: false,
  saving: false,
  error: null,
  successMessage: null,
};

const KNOWN_ERRORS = ['ABSENCE_PASSEE', 'ABSENCE_NON_ANNULABLE', 'ABSENCE_INTROUVABLE'];

function codeDe(err: unknown): string | undefined {
  return err instanceof HttpErrorResponse ? err.error?.code : undefined;
}

export function monPlanningErrorKey(err: unknown): string {
  const code = codeDe(err);
  return code && KNOWN_ERRORS.includes(code) ? `INFIRMIER.ERR.${code}` : 'INFIRMIER.ERR.SAVE';
}

/**
 * « Mon planning » de l'infirmier connecté : créneaux de la semaine et absences personnelles. Toute requête porte le
 * centre courant ; le compte vient de la session, jamais d'un paramètre.
 */
export const MonPlanningStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withDevtools('MonPlanningStore'),
  withMethods((store, api = inject(InfirmierApiService), shell = inject(AppShellStore)) => {
    const centerId = (): string => shell.currentCenterId() ?? '';

    const chargerPlanning = rxMethod<string | null>(
      pipe(
        tap(() => patchState(store, {loadingPlanning: true, error: null, nonLie: false})),
        switchMap((date) =>
          api.monPlanning(centerId(), date ?? undefined).pipe(
            tap((planning) => patchState(store, {planning, loadingPlanning: false})),
            catchError((err) => {
              const nonLie = codeDe(err) === 'INFIRMIER_NON_LIE';
              patchState(store, {
                planning: null, loadingPlanning: false, nonLie,
                error: nonLie ? null : 'INFIRMIER.MOI.ERR.LOAD',
              });
              return EMPTY;
            }),
          ),
        ),
      ),
    );

    const loadAbsences = rxMethod<{ page: number; size: number }>(
      pipe(
        tap(({page, size}) => patchState(store, {loading: true, pageIndex: page, pageSize: size})),
        switchMap(({page, size}) =>
          api.mesAbsences(centerId(), page, size).pipe(
            tap((res) => patchState(store, {
              rows: res.items ?? [], total: res.total ?? 0, pageIndex: res.page ?? page, loading: false,
            })),
            catchError(() => {
              patchState(store, {rows: [], total: 0, loading: false});
              return EMPTY;
            }),
          ),
        ),
      ),
    );

    /** Après une modification : recharge la semaine affichée (l'absence change mes créneaux) et la page d'absences. */
    function saveFlow<T>(request: Observable<T>, message: string) {
      return request.pipe(
        tap(() => {
          patchState(store, {saving: false, successMessage: message});
          chargerPlanning(store.planning()?.debut ?? null);
          loadAbsences({page: store.pageIndex(), size: store.pageSize()});
        }),
        catchError((err) => {
          patchState(store, {saving: false, error: monPlanningErrorKey(err)});
          return EMPTY;
        }),
      );
    }

    const begin = () => patchState(store, {saving: true, error: null, successMessage: null});

    return {
      chargerPlanning,
      loadAbsences,

      /** Semaine précédente (-1) ou suivante (+1) de celle affichée. */
      changerSemaine(delta: number): void {
        const debut = store.planning()?.debut;
        if (debut) chargerPlanning(decalerJours(debut, 7 * delta));
      },

      declarer: rxMethod<MonAbsencePayload>(pipe(tap(begin),
        switchMap((payload) => saveFlow(api.declarerMonAbsence(centerId(), payload), 'INFIRMIER.MOI.ABSENCE_OK')))),

      annuler: rxMethod<string>(pipe(tap(begin),
        switchMap((id) => saveFlow(api.annulerMonAbsence(centerId(), id), 'INFIRMIER.MOI.ANNULEE_OK')))),

      setPagination(pageIndex: number, pageSize: number): void {
        patchState(store, {pageIndex, pageSize});
      },

      clearMessages(): void {
        patchState(store, {error: null, successMessage: null});
      },
    };
  }),
);
