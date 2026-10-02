import {computed, inject} from '@angular/core';
import {HttpErrorResponse} from '@angular/common/http';
import {patchState, signalStore, withComputed, withMethods, withState} from '@ngrx/signals';
import {rxMethod} from '@ngrx/signals/rxjs-interop';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {catchError, EMPTY, Observable, pipe, switchMap, tap} from 'rxjs';
import {
  GroupeArticle,
  GroupeArticlePayload,
  GroupesArticlesApiService,
} from '../../../core/api/groupes-articles-api.service';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {createPagedListState, PagedListState} from '../../../core/state/paged-list-state.util';

type GroupesArticlesState = PagedListState<GroupeArticle> & {
  saving: boolean;
  successMessage: string | null;
  error: string | null;
};

const initialState: GroupesArticlesState = {
  ...createPagedListState<GroupeArticle>(),
  saving: false,
  successMessage: null,
  error: null,
};

/** Codes d'erreur métier renvoyés par le serveur, traduits (clés `GROUPES_ARTICLES.ERR.<code>`). */
const KNOWN_ERRORS = ['GROUPE_ARTICLE_NOM_EXISTANT', 'GROUPE_ARTICLE_ARTICLE_INCONNU', 'GROUPE_ARTICLE_INTROUVABLE'];

export function groupeErrorKey(err: unknown): string {
  const code = err instanceof HttpErrorResponse ? err.error?.code : undefined;
  return KNOWN_ERRORS.includes(code) ? `GROUPES_ARTICLES.ERR.${code}` : 'GROUPES_ARTICLES.ERR.SAVE';
}

/**
 * Groupes d'articles du centre actif (administration). Toute requête porte le centre courant.
 */
export const GroupesArticlesStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withDevtools('GroupesArticlesStore'),
  withComputed((store) => ({
    isEmpty: computed(() => store.rows().length === 0),
  })),
  withMethods((store, api = inject(GroupesArticlesApiService), shell = inject(AppShellStore)) => {
    const centerId = (): string => shell.currentCenterId() ?? '';

    function saveFlow<T>(request: Observable<T>) {
      return request.pipe(
        tap(() => patchState(store, {saving: false, successMessage: 'GROUPES_ARTICLES.SAVED_OK'})),
        catchError((err) => {
          patchState(store, {saving: false, error: groupeErrorKey(err)});
          return EMPTY;
        }),
      );
    }

    return {
      loadPage: rxMethod<{ page: number; size: number }>(
        pipe(
          tap(({page, size}) => patchState(store, {loading: true, error: null, pageIndex: page, pageSize: size})),
          switchMap(({page, size}) =>
            api.list(centerId(), page, size).pipe(
              tap((res) => patchState(store, {
                rows: res.items ?? [], total: res.total ?? 0, pageIndex: res.page ?? page, loading: false,
              })),
              catchError(() => {
                patchState(store, {rows: [], total: 0, loading: false, error: 'GROUPES_ARTICLES.ERR.LOAD'});
                return EMPTY;
              }),
            ),
          ),
        ),
      ),

      create: rxMethod<GroupeArticlePayload>(
        pipe(
          tap(() => patchState(store, {saving: true, error: null, successMessage: null})),
          switchMap((payload) => saveFlow(api.create(centerId(), payload))),
        ),
      ),

      update: rxMethod<{ id: string; payload: GroupeArticlePayload }>(
        pipe(
          tap(() => patchState(store, {saving: true, error: null, successMessage: null})),
          switchMap(({id, payload}) => saveFlow(api.update(centerId(), id, payload))),
        ),
      ),

      remove: rxMethod<string>(
        pipe(
          tap(() => patchState(store, {saving: true, error: null, successMessage: null})),
          switchMap((id) => saveFlow(api.delete(centerId(), id))),
        ),
      ),

      setPagination(pageIndex: number, pageSize: number): void {
        patchState(store, {pageIndex, pageSize});
      },

      clearMessages(): void {
        patchState(store, {error: null, successMessage: null});
      },
    };
  }),
);
