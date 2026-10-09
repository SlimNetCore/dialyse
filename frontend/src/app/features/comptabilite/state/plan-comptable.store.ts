import {computed, inject} from '@angular/core';
import {patchState, signalStore, withComputed, withMethods, withState} from '@ngrx/signals';
import {rxMethod} from '@ngrx/signals/rxjs-interop';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {catchError, EMPTY, forkJoin, Observable, pipe, switchMap, tap} from 'rxjs';
import {ComptabiliteApiService, CompteItem, PayeurCompteItem} from '../../../core/api/comptabilite-api.service';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {createPagedListState, PagedListState} from '../../../core/state/paged-list-state.util';
import {parametrageErrorKey} from './comptabilite-parametrage.store';

/** Le plan d'un centre tient en une page de cette taille : c'est le maximum servi par l'API. */
export const PLAN_MAX = 500;

type PlanComptableState = PagedListState<CompteItem> & {
  recherche: string;
  /** Tous les comptes actifs du centre : la liste de choix partout où un compte est demandé. */
  actifs: CompteItem[];
  /** Payeurs du centre et leur compte client (liste paginée à part). */
  payeurs: PayeurCompteItem[];
  payeursTotal: number;
  payeursPageIndex: number;
  payeursPageSize: number;
  payeursRecherche: string;
  saving: boolean;
  successMessage: string | null;
  error: string | null;
};

const initialState: PlanComptableState = {
  ...createPagedListState<CompteItem>(),
  recherche: '',
  actifs: [],
  payeurs: [],
  payeursTotal: 0,
  payeursPageIndex: 0,
  payeursPageSize: 10,
  payeursRecherche: '',
  saving: false,
  successMessage: null,
  error: null,
};

/**
 * Plan comptable du centre actif et compte client de chacun de ses payeurs. Ajouter un compte ou un client ne demande
 * qu'un paramétrage ici. Toute requête porte le centre courant.
 */
export const PlanComptableStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withDevtools('PlanComptableStore'),
  withComputed((store) => ({
    /** Libellé de chaque compte actif, par numéro. */
    libelles: computed(() =>
      Object.fromEntries(store.actifs().map((c) => [c.numero, c.libelle])) as Record<string, string>),
  })),
  withMethods((store, api = inject(ComptabiliteApiService), shell = inject(AppShellStore)) => {
    const centerId = (): string => shell.currentCenterId() ?? '';

    /** Page affichée du plan et liste complète des comptes actifs, rechargées ensemble après chaque modification. */
    function recharger(): Observable<unknown> {
      return forkJoin({
        page: api.getComptes(centerId(), store.pageIndex(), store.pageSize(), store.recherche()),
        actifs: api.getComptes(centerId(), 0, PLAN_MAX, '', true),
      }).pipe(
        tap(({page, actifs}) => patchState(store, {
          rows: page.items ?? [], total: page.total ?? 0, actifs: actifs.items ?? [], loading: false,
        })),
      );
    }

    function rechargerPayeurs(): Observable<unknown> {
      return api.getPayeurs(centerId(), store.payeursPageIndex(), store.payeursPageSize(), store.payeursRecherche())
        .pipe(tap((page) => patchState(store, {payeurs: page.items ?? [], payeursTotal: page.total ?? 0})));
    }

    function echec(err: unknown) {
      patchState(store, {saving: false, loading: false, error: parametrageErrorKey(err)});
      return EMPTY;
    }

    const debut = () => patchState(store, {saving: true, error: null, successMessage: null});

    return {
      load: rxMethod<void>(
        pipe(
          tap(() => patchState(store, {loading: true, error: null})),
          switchMap(() => {
            if (!centerId()) {
              patchState(store, {loading: false, rows: [], total: 0, actifs: [], payeurs: [], payeursTotal: 0});
              return EMPTY;
            }
            return forkJoin([recharger(), rechargerPayeurs()]).pipe(
              catchError(() => {
                patchState(store, {loading: false, error: 'COMPTABILITE.PARAMETRAGE.ERR.LOAD'});
                return EMPTY;
              }),
            );
          }),
        ),
      ),

      /** Recharge les seuls comptes actifs (écrans qui ne font que choisir un compte). */
      loadActifs: rxMethod<void>(
        pipe(
          switchMap(() => centerId()
            ? api.getComptes(centerId(), 0, PLAN_MAX, '', true).pipe(
              tap((page) => patchState(store, {actifs: page.items ?? []})),
              catchError(() => {
                patchState(store, {actifs: [], error: 'COMPTABILITE.PARAMETRAGE.ERR.LOAD'});
                return EMPTY;
              }))
            : EMPTY),
        ),
      ),

      setPagination: rxMethod<{ pageIndex: number; pageSize: number }>(
        pipe(
          tap(({pageIndex, pageSize}) => patchState(store, {pageIndex, pageSize, loading: true})),
          switchMap(() => recharger().pipe(catchError(echec))),
        ),
      ),

      setRecherche: rxMethod<string>(
        pipe(
          tap((recherche) => patchState(store, {recherche, pageIndex: 0, loading: true})),
          switchMap(() => recharger().pipe(catchError(echec))),
        ),
      ),

      saveCompte: rxMethod<CompteItem>(
        pipe(
          tap(debut),
          switchMap((compte) => api.saveCompte(centerId(), compte).pipe(
            switchMap(() => recharger()),
            tap(() => patchState(store, {saving: false, successMessage: 'COMPTABILITE.PLAN.ENREGISTRE'})),
            catchError(echec),
          )),
        ),
      ),

      deleteCompte: rxMethod<string>(
        pipe(
          tap(debut),
          switchMap((numero) => api.deleteCompte(centerId(), numero).pipe(
            switchMap(() => recharger()),
            tap(() => patchState(store, {saving: false, successMessage: 'COMPTABILITE.PLAN.SUPPRIME'})),
            catchError(echec),
          )),
        ),
      ),

      setPayeursPagination: rxMethod<{ pageIndex: number; pageSize: number }>(
        pipe(
          tap(({pageIndex, pageSize}) => patchState(store, {payeursPageIndex: pageIndex, payeursPageSize: pageSize})),
          switchMap(() => rechargerPayeurs().pipe(catchError(echec))),
        ),
      ),

      setPayeursRecherche: rxMethod<string>(
        pipe(
          tap((payeursRecherche) => patchState(store, {payeursRecherche, payeursPageIndex: 0})),
          switchMap(() => rechargerPayeurs().pipe(catchError(echec))),
        ),
      ),

      /** Affecte un compte client au payeur ; vide = retour au compte client par défaut du centre. */
      saveComptePayeur: rxMethod<{ payeurId: string; compte: string }>(
        pipe(
          tap(debut),
          switchMap(({payeurId, compte}) => api.saveComptePayeur(centerId(), payeurId, compte).pipe(
            switchMap(() => rechargerPayeurs()),
            tap(() => patchState(store, {saving: false, successMessage: 'COMPTABILITE.PAYEURS.ENREGISTRE'})),
            catchError(echec),
          )),
        ),
      ),

      clearMessages(): void {
        patchState(store, {error: null, successMessage: null});
      },
    };
  }),
);
