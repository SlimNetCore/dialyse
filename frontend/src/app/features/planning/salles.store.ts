import {computed, inject} from '@angular/core';
import {patchState, signalStore, withComputed, withMethods, withState} from '@ngrx/signals';
import {rxMethod} from '@ngrx/signals/rxjs-interop';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {catchError, EMPTY, pipe, switchMap, tap} from 'rxjs';
import {PlanningApiService, SalleVue} from '../../core/api/planning-api.service';
import {AppShellStore} from '../../core/state/app-shell.store';
import {createPagedListState, PagedListState} from '../../core/state/paged-list-state.util';

type SallesState = PagedListState<SalleVue> & { error: string | null };

/** Vue d'ensemble des salles du centre actif (générateurs affectés, capacité) ; liste paginée en lecture seule. */
export const SallesGenerateursStore = signalStore(
  {providedIn: 'root'},
  withState<SallesState>({...createPagedListState<SalleVue>({pageSize: 12}), error: null}),
  withDevtools('SallesGenerateursStore'),
  withComputed((store) => ({
    isEmpty: computed(() => store.rows().length === 0),
    nbDepassements: computed(() => store.rows().filter((s) => s.depassement).length),
  })),
  withMethods((store, api = inject(PlanningApiService), shell = inject(AppShellStore)) => {
    const loadPage = rxMethod<{ page: number; size: number }>(
      pipe(
        tap(({page, size}) => patchState(store, {loading: true, error: null, pageIndex: page, pageSize: size})),
        switchMap(({page, size}) =>
          api.salles(shell.currentCenterId() ?? '', page, size).pipe(
            tap((res) => patchState(store, {
              rows: res.items ?? [], total: res.total ?? 0, pageIndex: res.page ?? page, loading: false,
            })),
            catchError(() => {
              patchState(store, {rows: [], total: 0, loading: false, error: 'PLANNING.SALLES.ERR.LOAD'});
              return EMPTY;
            }),
          ),
        ),
      ),
    );
    return {
      loadPage,
      setPagination(pageIndex: number, pageSize: number): void {
        patchState(store, {pageIndex, pageSize});
        loadPage({page: pageIndex, size: pageSize});
      },
    };
  }),
);
