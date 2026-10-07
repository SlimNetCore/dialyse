import {computed, inject} from '@angular/core';
import {HttpErrorResponse} from '@angular/common/http';
import {patchState, signalStore, withComputed, withMethods, withState} from '@ngrx/signals';
import {rxMethod} from '@ngrx/signals/rxjs-interop';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {catchError, EMPTY, Observable, pipe, switchMap, tap} from 'rxjs';
import {
  AffectationInfirmierPayload,
  CompteInfirmier,
  CreerComptePayload,
  Infirmier,
  InfirmierApiService,
  InfirmierPayload,
} from '../../core/api/infirmier-api.service';
import {JourSemaine} from '../../core/api/planning-api.service';
import {AppShellStore} from '../../core/state/app-shell.store';
import {createPagedListState, PagedListState} from '../../core/state/paged-list-state.util';

/** Taille de la page de comptes proposés à la liaison (liste déroulante bornée). */
const TAILLE_COMPTES_LIABLES = 100;

type InfirmiersState = PagedListState<Infirmier> & {
  saving: boolean;
  successMessage: string | null;
  error: string | null;
  /** Jours où la dernière affectation enregistrée met des infirmiers en trop (avertissement, jamais un refus). */
  joursEnSureffectif: JourSemaine[];
  comptesLiables: CompteInfirmier[];
  /** Mot de passe temporaire du dernier compte créé : affiché une fois puis effacé. */
  compteCree: { infirmierId: string; motDePasse: string } | null;
};

const initialState: InfirmiersState = {
  ...createPagedListState<Infirmier>(),
  saving: false,
  successMessage: null,
  error: null,
  joursEnSureffectif: [],
  comptesLiables: [],
  compteCree: null,
};

/** Codes d'erreur métier renvoyés par le serveur, traduits (clés `INFIRMIER.ERR.<code>`). */
const KNOWN_ERRORS = [
  'INFIRMIER_MATRICULE_EXISTANT', 'INFIRMIER_INTROUVABLE', 'AFFECTATION_CHEVAUCHEMENT', 'AFFECTATION_SALLE_INCONNUE',
  'AFFECTATION_CRENEAU_INCONNU', 'AFFECTATION_INTROUVABLE', 'COMPTE_INTROUVABLE', 'COMPTE_DEJA_LIE',
  'COMPTE_DEJA_UTILISE', 'COMPTE_ETAT_INCOHERENT', 'COMPTE_IDENTIFIANT_INVALIDE', 'COMPTE_IDENTIFIANT_EXISTANT',
];

export function infirmierErrorKey(err: unknown): string {
  const code = err instanceof HttpErrorResponse ? err.error?.code : undefined;
  return KNOWN_ERRORS.includes(code) ? `INFIRMIER.ERR.${code}` : 'INFIRMIER.ERR.SAVE';
}

/**
 * Référentiel des infirmiers du centre actif, de leur roulement (salle, créneau, jours) et de leur compte
 * d'accès. Toute requête porte le centre courant ; la liste est paginée.
 */
export const InfirmiersStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withDevtools('InfirmiersStore'),
  withComputed((store) => ({
    isEmpty: computed(() => store.rows().length === 0),
  })),
  withMethods((store, api = inject(InfirmierApiService), shell = inject(AppShellStore)) => {
    const centerId = (): string => shell.currentCenterId() ?? '';

    const loadPage = rxMethod<{ page: number; size: number }>(
      pipe(
        tap(({page, size}) => patchState(store, {loading: true, error: null, pageIndex: page, pageSize: size})),
        switchMap(({page, size}) =>
          api.list(centerId(), page, size).pipe(
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

    const loadComptesLiables = rxMethod<void>(
      pipe(
        switchMap(() =>
          api.comptesLiables(centerId(), 0, TAILLE_COMPTES_LIABLES).pipe(
            tap((res) => patchState(store, {comptesLiables: res.items ?? []})),
            catchError(() => {
              patchState(store, {comptesLiables: []});
              return EMPTY;
            }),
          ),
        ),
      ),
    );

    /** Enregistre puis recharge la page courante (le roulement et le compte sont portés par l'infirmier). */
    function saveFlow<T>(request: Observable<T>, message = 'INFIRMIER.SAVED_OK', onSuccess?: (result: T) => void) {
      return request.pipe(
        tap((result) => {
          patchState(store, {saving: false, successMessage: message});
          onSuccess?.(result);
          loadPage({page: store.pageIndex(), size: store.pageSize()});
        }),
        catchError((err) => {
          patchState(store, {saving: false, error: infirmierErrorKey(err)});
          return EMPTY;
        }),
      );
    }

    const begin = () => patchState(store, {saving: true, error: null, successMessage: null, joursEnSureffectif: []});

    return {
      loadPage,
      loadComptesLiables,

      create: rxMethod<InfirmierPayload>(pipe(tap(begin),
        switchMap((payload) => saveFlow(api.create(centerId(), payload))))),

      update: rxMethod<{ id: string; payload: InfirmierPayload }>(pipe(tap(begin),
        switchMap(({id, payload}) => saveFlow(api.update(centerId(), id, payload))))),

      setActif: rxMethod<{ id: string; actif: boolean }>(pipe(tap(begin),
        switchMap(({id, actif}) => saveFlow(api.setActif(centerId(), id, actif))))),

      addAffectation: rxMethod<{ infirmierId: string; payload: AffectationInfirmierPayload }>(pipe(tap(begin),
        switchMap(({infirmierId, payload}) => saveFlow(api.addAffectation(centerId(), infirmierId, payload),
          'INFIRMIER.SAVED_OK', (a) => patchState(store, {joursEnSureffectif: a.joursEnSureffectif ?? []}))))),

      deleteAffectation: rxMethod<{ infirmierId: string; affectationId: string }>(pipe(tap(begin),
        switchMap(({infirmierId, affectationId}) =>
          saveFlow(api.deleteAffectation(centerId(), infirmierId, affectationId))))),

      lierCompte: rxMethod<{ infirmierId: string; userId: string }>(pipe(tap(begin),
        switchMap(({infirmierId, userId}) => saveFlow(api.lierCompte(centerId(), infirmierId, userId),
          'INFIRMIER.COMPTE.LIE_OK', () => loadComptesLiables())))),

      creerCompte: rxMethod<{ infirmierId: string; payload: CreerComptePayload }>(pipe(tap(begin),
        switchMap(({infirmierId, payload}) => saveFlow(api.creerCompte(centerId(), infirmierId, payload),
          'INFIRMIER.COMPTE.CREE_OK',
          (cree) => patchState(store, {compteCree: {infirmierId, motDePasse: cree.motDePasseTemporaire}}))))),

      delierCompte: rxMethod<string>(pipe(tap(begin),
        switchMap((infirmierId) => saveFlow(api.delierCompte(centerId(), infirmierId),
          'INFIRMIER.COMPTE.DELIE_OK', () => loadComptesLiables())))),

      /** Efface le mot de passe temporaire affiché (il n'est jamais relisible côté serveur). */
      effacerMotDePasse(): void {
        patchState(store, {compteCree: null});
      },

      setPagination(pageIndex: number, pageSize: number): void {
        patchState(store, {pageIndex, pageSize});
      },

      clearMessages(): void {
        patchState(store, {error: null, successMessage: null, joursEnSureffectif: []});
      },
    };
  }),
);
