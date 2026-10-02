import {computed, inject} from '@angular/core';
import {HttpErrorResponse} from '@angular/common/http';
import {patchState, signalStore, withComputed, withMethods, withState} from '@ngrx/signals';
import {rxMethod} from '@ngrx/signals/rxjs-interop';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {catchError, EMPTY, Observable, pipe, switchMap, tap} from 'rxjs';
import {AbsenceInfirmier, AbsenceInfirmierPayload, InfirmierApiService} from '../../core/api/infirmier-api.service';
import {AppShellStore} from '../../core/state/app-shell.store';
import {createPagedListState, PagedListState} from '../../core/state/paged-list-state.util';

type AbsencesState = PagedListState<AbsenceInfirmier> & {
  saving: boolean;
  successMessage: string | null;
  error: string | null;
};

const initialState: AbsencesState = {
  ...createPagedListState<AbsenceInfirmier>(),
  saving: false,
  successMessage: null,
  error: null,
};

export function absenceErrorKey(err: unknown): string {
  const code = err instanceof HttpErrorResponse ? err.error?.code : undefined;
  return code === 'INFIRMIER_INTROUVABLE' || code === 'ABSENCE_INTROUVABLE'
    ? `INFIRMIER.ERR.${code}` : 'INFIRMIER.ERR.SAVE';
}

/** Absences des infirmiers du centre actif (congé, maladie, formation). Liste paginée, centre toujours transmis. */
export const AbsencesStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withDevtools('AbsencesStore'),
  withComputed((store) => ({
    isEmpty: computed(() => store.rows().length === 0),
  })),
  withMethods((store, api = inject(InfirmierApiService), shell = inject(AppShellStore)) => {
    const centerId = (): string => shell.currentCenterId() ?? '';

    const loadPage = rxMethod<{ page: number; size: number }>(
      pipe(
        tap(({page, size}) => patchState(store, {loading: true, error: null, pageIndex: page, pageSize: size})),
        switchMap(({page, size}) =>
          api.listAbsences(centerId(), page, size).pipe(
            tap((res) => patchState(store, {
              rows: res.items ?? [], total: res.total ?? 0, pageIndex: res.page ?? page, loading: false,
            })),
            catchError(() => {
              patchState(store, {rows: [], total: 0, loading: false, error: 'INFIRMIER.ERR.LOAD'});
              return EMPTY;
            }),
          ),
        ),
      ),
    );

    function saveFlow<T>(request: Observable<T>) {
      return request.pipe(
        tap(() => {
          patchState(store, {saving: false, successMessage: 'INFIRMIER.ABSENCES.SAVED_OK'});
          loadPage({page: store.pageIndex(), size: store.pageSize()});
        }),
        catchError((err) => {
          patchState(store, {saving: false, error: absenceErrorKey(err)});
          return EMPTY;
        }),
      );
    }

    const begin = () => patchState(store, {saving: true, error: null, successMessage: null});

    return {
      loadPage,

      create: rxMethod<AbsenceInfirmierPayload>(pipe(tap(begin),
        switchMap((payload) => saveFlow(api.createAbsence(centerId(), payload))))),

      remove: rxMethod<string>(pipe(tap(begin),
        switchMap((id) => saveFlow(api.deleteAbsence(centerId(), id))))),

      setPagination(pageIndex: number, pageSize: number): void {
        patchState(store, {pageIndex, pageSize});
      },

      clearMessages(): void {
        patchState(store, {error: null, successMessage: null});
      },
    };
  }),
);
