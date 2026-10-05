import {computed, inject} from '@angular/core';
import {HttpErrorResponse} from '@angular/common/http';
import {patchState, signalStore, withComputed, withMethods, withState} from '@ngrx/signals';
import {rxMethod} from '@ngrx/signals/rxjs-interop';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {catchError, EMPTY, Observable, pipe, switchMap, tap} from 'rxjs';
import {ArticleFichePayload, ArticleStock, Fournisseur, StockApiService} from '../../../core/api/stock-api.service';
import {BackendApiService, TvaType} from '../../../core/api/backend-api.service';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {createPagedListState, PagedListState} from '../../../core/state/paged-list-state.util';

export type ArticleActiveFilter = 'all' | 'active' | 'inactive';

type ArticlesState = PagedListState<ArticleStock> & {
  searchTerm: string;
  activeFilter: ArticleActiveFilter;
  saving: boolean;
  successMessage: string | null;
  error: string | null;
  fournisseurs: Fournisseur[];
  tvaTypes: TvaType[];
};

const initialState: ArticlesState = {
  ...createPagedListState<ArticleStock>({pageSize: 20}),
  searchTerm: '',
  activeFilter: 'active',
  saving: false,
  successMessage: null,
  error: null,
  fournisseurs: [],
  tvaTypes: [],
};

const KNOWN_ERRORS = [
  'ARTICLE_CODE_REQUIS', 'ARTICLE_LIBELLE_REQUIS', 'ARTICLE_UNITE_REQUISE', 'ARTICLE_DOSAGE_INCOMPLET',
  'ARTICLE_DOSAGE_INVALIDE', 'ARTICLE_COEFFICIENT_ACHAT_INVALIDE', 'ARTICLE_VALEUR_NEGATIVE',
  'ARTICLE_STOCK_MAX_INFERIEUR_SEUIL', 'ARTICLE_CODE_EXISTANT', 'ARTICLE_INTROUVABLE',
];

/** Clé i18n (`STOCK.ARTICLES.ERR.<code>`) d'une erreur métier renvoyée par le serveur. */
export function articleErrorKey(err: unknown): string {
  const code = err instanceof HttpErrorResponse ? err.error?.code : undefined;
  return KNOWN_ERRORS.includes(code) ? `STOCK.ARTICLES.ERR.${code}` : 'STOCK.ARTICLES.ERR.SAVE';
}

const TAILLE_LISTES_REFERENTIELLES = 200;

/**
 * Fiches articles du centre actif : liste paginée (recherche, filtre actif/inactif), création et modification.
 * Toute requête porte le centre courant.
 */
export const ArticlesStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withDevtools('ArticlesStore'),
  withComputed((store) => ({
    isEmpty: computed(() => store.rows().length === 0),
  })),
  withMethods((store, api = inject(StockApiService), backend = inject(BackendApiService),
               shell = inject(AppShellStore)) => {
    const centerId = (): string => shell.currentCenterId() ?? '';
    const activeParam = (): boolean | null => {
      const filter = store.activeFilter();
      return filter === 'all' ? null : filter === 'active';
    };

    const loadPage = rxMethod<{ page: number; size: number }>(
      pipe(
        tap(({page, size}) => patchState(store, {loading: true, error: null, pageIndex: page, pageSize: size})),
        switchMap(({page, size}) =>
          api.searchArticles(centerId(), {q: store.searchTerm(), active: activeParam(), page, size}).pipe(
            tap((res) => patchState(store, {
              rows: res.items ?? [], total: res.total ?? 0, pageIndex: res.page ?? page, loading: false,
            })),
            catchError(() => {
              patchState(store, {rows: [], total: 0, loading: false, error: 'STOCK.ARTICLES.ERR.LOAD'});
              return EMPTY;
            }),
          ),
        ),
      ),
    );

    const loadReferentiels = rxMethod<void>(
      pipe(
        switchMap(() => api.listFournisseurs(centerId()).pipe(
          tap((fournisseurs) => patchState(store, {fournisseurs})),
          catchError(() => EMPTY),
        )),
        switchMap(() => backend.listTvaTypes(centerId(), 0, TAILLE_LISTES_REFERENTIELLES).pipe(
          tap((res) => patchState(store, {tvaTypes: res.items ?? []})),
          catchError(() => EMPTY),
        )),
      ),
    );

    function saveFlow<T>(request: Observable<T>, message = 'STOCK.ARTICLES.SAVED_OK') {
      return request.pipe(
        tap(() => {
          patchState(store, {saving: false, successMessage: message});
          loadPage({page: store.pageIndex(), size: store.pageSize()});
        }),
        catchError((err) => {
          patchState(store, {saving: false, error: articleErrorKey(err)});
          return EMPTY;
        }),
      );
    }

    const begin = () => patchState(store, {saving: true, error: null, successMessage: null});

    return {
      loadPage,
      loadReferentiels,

      create: rxMethod<ArticleFichePayload>(pipe(tap(begin),
        switchMap((payload) => saveFlow(api.createArticle({...payload, centerId: centerId()}))))),

      update: rxMethod<{ id: string; payload: ArticleFichePayload }>(pipe(tap(begin),
        switchMap(({id, payload}) => saveFlow(api.updateArticle(id, {...payload, centerId: centerId()}))))),

      setActive: rxMethod<{ id: string; active: boolean }>(pipe(tap(begin),
        switchMap(({id, active}) => saveFlow(api.setArticleActive(centerId(), id, active))))),

      search(searchTerm: string): void {
        patchState(store, {searchTerm});
        loadPage({page: 0, size: store.pageSize()});
      },

      setActiveFilter(activeFilter: ArticleActiveFilter): void {
        patchState(store, {activeFilter});
        loadPage({page: 0, size: store.pageSize()});
      },

      clearMessages(): void {
        patchState(store, {error: null, successMessage: null});
      },
    };
  }),
);
