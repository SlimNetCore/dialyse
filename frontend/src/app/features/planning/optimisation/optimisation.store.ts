import {computed, inject} from '@angular/core';
import {HttpErrorResponse} from '@angular/common/http';
import {patchState, signalStore, withComputed, withMethods, withState} from '@ngrx/signals';
import {rxMethod} from '@ngrx/signals/rxjs-interop';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {catchError, EMPTY, firstValueFrom, pipe, switchMap, takeWhile, tap, timer} from 'rxjs';
import {
  LancerOptimisationPayload,
  PlanningOptimisationApiService,
  RunOptimisation,
} from '../../../core/api/planning-optimisation-api.service';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {INTERVALLE_SUIVI_MS} from './optimisation.util';

interface OptimisationState {
  /** Exécution affichée : calcul en cours, ou proposition consultée (avec son détail). */
  courant: RunOptimisation | null;
  historique: RunOptimisation[];
  total: number;
  pageIndex: number;
  pageSize: number;
  loading: boolean;
  launching: boolean;
  applying: boolean;
  error: string | null;
  successMessage: string | null;
}

const initialState: OptimisationState = {
  courant: null,
  historique: [],
  total: 0,
  pageIndex: 0,
  pageSize: 10,
  loading: false,
  launching: false,
  applying: false,
  error: null,
  successMessage: null,
};

const CODES_CONNUS = [
  'OPTIMISATION_DEJA_EN_COURS', 'OPTIMISATION_INTROUVABLE', 'OPTIMISATION_NON_APPLICABLE',
  'OPTIMISATION_DEJA_APPLIQUEE', 'OPTIMISATION_PERIMEE',
];

/** Clé i18n de l'erreur d'une action : code métier connu, refus de placement, paramètres refusés, sinon générique. */
export function optimisationErrorKey(err: unknown): string {
  if (!(err instanceof HttpErrorResponse)) return 'PLANNING.OPTIM.ERR.GENERIC';
  const code: string | undefined = err.error?.code;
  if (code && CODES_CONNUS.includes(code)) return `PLANNING.OPTIM.ERR.${code}`;
  if (code?.startsWith('OPTIMISATION_PLACEMENT_')) return 'PLANNING.OPTIM.ERR.PLACEMENT';
  if (err.status === 400) return 'PLANNING.OPTIM.ERR.PARAMETRES';
  return 'PLANNING.OPTIM.ERR.GENERIC';
}

/**
 * Optimisation du planning du centre actif : lancement, suivi du calcul en cours (lecture périodique jusqu'à sa fin),
 * arrêt anticipé, historique paginé et application de la proposition. Toute requête porte le centre courant.
 */
export const OptimisationStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withDevtools('OptimisationStore'),
  withComputed((store) => ({
    enCours: computed(() => store.courant()?.statut === 'EN_COURS'),
    appliquable: computed(() => {
      const run = store.courant();
      return run?.statut === 'TERMINEE' && !!run.resultat && !run.appliqueLe;
    }),
  })),
  withMethods((store, api = inject(PlanningOptimisationApiService), shell = inject(AppShellStore)) => {
    const centerId = (): string => shell.currentCenterId() ?? '';

    const chargerHistorique = rxMethod<{ page: number; size: number }>(
      pipe(
        tap(({page, size}) => patchState(store, {loading: true, error: null, pageIndex: page, pageSize: size})),
        switchMap(({page, size}) =>
          api.historique(centerId(), page, size).pipe(
            tap((liste) => patchState(store, {
              historique: liste.items ?? [], total: liste.total ?? 0, pageIndex: liste.page ?? page, loading: false,
            })),
            catchError((err) => {
              patchState(store, {loading: false, error: optimisationErrorKey(err)});
              return EMPTY;
            }),
          ),
        ),
      ),
    );

    const rafraichirHistorique = (): void => {
      chargerHistorique({page: store.pageIndex(), size: store.pageSize()});
    };

    /** Lit l'avancement du calcul toutes les 1,5 s jusqu'à sa fin ; `null` interrompt le suivi. */
    const suivre = rxMethod<string | null>(
      pipe(
        switchMap((id) => {
          if (!id) return EMPTY;
          return timer(0, INTERVALLE_SUIVI_MS).pipe(
            switchMap(() => api.consulter(centerId(), id)),
            tap((run) => patchState(store, {courant: run})),
            takeWhile((run) => run.statut === 'EN_COURS', true),
            tap({complete: () => rafraichirHistorique()}),
            catchError(() => {
              patchState(store, {error: 'PLANNING.OPTIM.ERR.SUIVI'});
              return EMPTY;
            }),
          );
        }),
      ),
    );

    return {
      chargerHistorique,

      setPagination(pageIndex: number, pageSize: number): void {
        patchState(store, {pageIndex, pageSize});
        chargerHistorique({page: pageIndex, size: pageSize});
      },

      /** Lance un calcul puis le suit ; renvoie vrai si le serveur l'a accepté. */
      async lancer(payload: LancerOptimisationPayload): Promise<boolean> {
        patchState(store, {launching: true, error: null, successMessage: null});
        try {
          const run = await firstValueFrom(api.lancer(centerId(), payload));
          patchState(store, {courant: run, launching: false});
          suivre(run.id);
          rafraichirHistorique();
          return true;
        } catch (err) {
          patchState(store, {launching: false, error: optimisationErrorKey(err)});
          return false;
        }
      },

      /** Affiche une exécution de l'historique avec son détail (et reprend le suivi si elle est en cours). */
      async ouvrir(id: string): Promise<void> {
        patchState(store, {loading: true, error: null, successMessage: null});
        try {
          const run = await firstValueFrom(api.consulter(centerId(), id));
          patchState(store, {courant: run, loading: false});
          suivre(run.statut === 'EN_COURS' ? run.id : null);
        } catch (err) {
          patchState(store, {loading: false, error: optimisationErrorKey(err)});
        }
      },

      /** Arrête le calcul en cours : la meilleure solution trouvée est conservée, le suivi la récupère. */
      async arreter(): Promise<void> {
        const run = store.courant();
        if (!run || run.statut !== 'EN_COURS') return;
        try {
          await firstValueFrom(api.arreter(centerId(), run.id));
        } catch (err) {
          patchState(store, {error: optimisationErrorKey(err)});
        }
      },

      /** Applique la proposition affichée (administrateur) ; renvoie vrai si elle a été appliquée. */
      async appliquer(): Promise<boolean> {
        const run = store.courant();
        if (!run || !store.appliquable()) return false;
        patchState(store, {applying: true, error: null, successMessage: null});
        try {
          const appliquee = await firstValueFrom(api.appliquer(centerId(), run.id));
          patchState(store, {
            courant: {...run, appliqueLe: appliquee.appliqueLe}, applying: false,
            successMessage: 'PLANNING.OPTIM.OK.APPLIQUEE',
          });
          rafraichirHistorique();
          return true;
        } catch (err) {
          patchState(store, {applying: false, error: optimisationErrorKey(err)});
          return false;
        }
      },

      /** Changement de centre : on abandonne le suivi et l'affichage du centre précédent. */
      reinitialiser(): void {
        suivre(null);
        patchState(store, {...initialState});
      },
    };
  }),
);
