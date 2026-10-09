import {computed, inject} from '@angular/core';
import {patchState, signalStore, withComputed, withMethods, withState} from '@ngrx/signals';
import {rxMethod} from '@ngrx/signals/rxjs-interop';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {catchError, EMPTY, Observable, pipe, switchMap, tap} from 'rxjs';
import {
  ComptabiliteApiService,
  ModelePieceItem,
  ModelePiecePayload,
  PieceItem,
  PiecePayload,
} from '../../../core/api/comptabilite-api.service';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {createPagedListState, PagedListState} from '../../../core/state/paged-list-state.util';
import {parametrageErrorKey} from './comptabilite-parametrage.store';

/** Les modèles actifs d'un centre tiennent en une page : c'est le maximum servi par l'API. */
export const MODELES_MAX = 100;

type PiecesComptablesState = PagedListState<ModelePieceItem> & {
  /** Modèles actifs du centre : ceux qu'on peut choisir pour saisir une pièce. */
  actifs: ModelePieceItem[];
  /** Dernière pièce saisie ou extournée, pour la confirmer à l'écran. */
  dernierePiece: PieceItem | null;
  saving: boolean;
  successMessage: string | null;
  error: string | null;
};

const initialState: PiecesComptablesState = {
  ...createPagedListState<ModelePieceItem>(),
  actifs: [],
  dernierePiece: null,
  saving: false,
  successMessage: null,
  error: null,
};

/**
 * Modèles de pièces du centre actif et saisie de pièces à partir de ces modèles. Créer un nouveau type de pièce ne
 * demande qu'un modèle. Toute requête porte le centre courant.
 */
export const PiecesComptablesStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withDevtools('PiecesComptablesStore'),
  withComputed((store) => ({
    isEmpty: computed(() => store.rows().length === 0),
  })),
  withMethods((store, api = inject(ComptabiliteApiService), shell = inject(AppShellStore)) => {
    const centerId = (): string => shell.currentCenterId() ?? '';

    function recharger(): Observable<unknown> {
      return api.getModeles(centerId(), store.pageIndex(), store.pageSize()).pipe(
        tap((page) => patchState(store, {rows: page.items ?? [], total: page.total ?? 0, loading: false})),
      );
    }

    function echec(err: unknown) {
      patchState(store, {saving: false, loading: false, error: parametrageErrorKey(err)});
      return EMPTY;
    }

    const debut = () => patchState(store, {saving: true, error: null, successMessage: null, dernierePiece: null});

    function enregistre(message: string) {
      return pipe(
        switchMap(() => recharger()),
        tap(() => patchState(store, {saving: false, successMessage: message})),
      );
    }

    return {
      load: rxMethod<void>(
        pipe(
          tap(() => patchState(store, {loading: true, error: null})),
          switchMap(() => centerId()
            ? recharger().pipe(catchError(() => {
              patchState(store, {loading: false, error: 'COMPTABILITE.PARAMETRAGE.ERR.LOAD'});
              return EMPTY;
            }))
            : EMPTY),
        ),
      ),

      /** Modèles actifs du centre, pour l'écran de saisie. */
      loadActifs: rxMethod<void>(
        pipe(
          tap(() => patchState(store, {loading: true, error: null, dernierePiece: null})),
          switchMap(() => centerId()
            ? api.getModeles(centerId(), 0, MODELES_MAX, true).pipe(
              tap((page) => patchState(store, {actifs: page.items ?? [], loading: false})),
              catchError(() => {
                patchState(store, {actifs: [], loading: false, error: 'COMPTABILITE.PARAMETRAGE.ERR.LOAD'});
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

      saveModele: rxMethod<{ id: string | null; modele: ModelePiecePayload }>(
        pipe(
          tap(debut),
          switchMap(({id, modele}) =>
            (id ? api.updateModele(centerId(), id, modele) : api.createModele(centerId(), modele)).pipe(
              enregistre('COMPTABILITE.MODELES.ENREGISTRE'),
              catchError(echec),
            )),
        ),
      ),

      deleteModele: rxMethod<string>(
        pipe(
          tap(debut),
          switchMap((id) => api.deleteModele(centerId(), id).pipe(
            enregistre('COMPTABILITE.MODELES.SUPPRIME'),
            catchError(echec),
          )),
        ),
      ),

      saisirPiece: rxMethod<PiecePayload>(
        pipe(
          tap(debut),
          switchMap((piece) => api.saisirPiece(centerId(), piece).pipe(
            tap((dernierePiece) => patchState(store, {dernierePiece, saving: false})),
            catchError(echec),
          )),
        ),
      ),

      extournerPiece: rxMethod<string>(
        pipe(
          tap(debut),
          switchMap((ecritureId) => api.extournerPiece(centerId(), ecritureId).pipe(
            tap((dernierePiece) => patchState(store, {dernierePiece, saving: false})),
            catchError(echec),
          )),
        ),
      ),

      clearMessages(): void {
        patchState(store, {error: null, successMessage: null, dernierePiece: null});
      },
    };
  }),
);
