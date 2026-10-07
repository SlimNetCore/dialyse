import {computed, inject} from '@angular/core';
import {HttpErrorResponse} from '@angular/common/http';
import {patchState, signalStore, withComputed, withMethods, withState} from '@ngrx/signals';
import {rxMethod} from '@ngrx/signals/rxjs-interop';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {catchError, EMPTY, firstValueFrom, pipe, switchMap, takeWhile, tap, timer} from 'rxjs';
import {
  CalendrierProposition,
  LancerOptimisationPayload,
  PlanningOptimisationApiService,
  RunOptimisation,
} from '../../../core/api/planning-optimisation-api.service';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {openPdf} from '../../stock/inventaire/inventaire.util';
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
  /** Planning calendaire de la proposition affichée (semaines disponibles et lignes de la semaine choisie). */
  calendrier: CalendrierProposition | null;
  loadingCalendrier: boolean;
  printing: boolean;
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
  calendrier: null,
  loadingCalendrier: false,
  printing: false,
  error: null,
  successMessage: null,
};

const CODES_CONNUS = [
  'OPTIMISATION_DEJA_EN_COURS', 'OPTIMISATION_INTROUVABLE', 'OPTIMISATION_NON_APPLICABLE',
  'OPTIMISATION_DEJA_APPLIQUEE', 'OPTIMISATION_PERIMEE', 'OPTIMISATION_CALENDRIER_ABSENT',
  'OPTIMISATION_SUPPRESSION_EN_COURS',
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
          patchState(store, {courant: run, launching: false, calendrier: null});
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
          patchState(store, {courant: run, loading: false, calendrier: null});
          suivre(run.statut === 'EN_COURS' ? run.id : null);
        } catch (err) {
          patchState(store, {loading: false, error: optimisationErrorKey(err)});
        }
      },

      /**
       * Charge le planning calendaire de la proposition terminée affichée (première semaine par défaut). Sans effet
       * tant qu'aucune proposition n'est affichée.
       */
      async chargerCalendrier(semaine?: string | null): Promise<void> {
        const run = store.courant();
        if (!run || run.statut !== 'TERMINEE' || !run.resultat) return;
        patchState(store, {loadingCalendrier: true, error: null});
        try {
          const calendrier = await firstValueFrom(api.calendrier(centerId(), run.id, semaine));
          // une réponse tardive d'une autre exécution (ou d'un autre centre) ne remplace pas l'affichage courant
          if (store.courant()?.id !== run.id) return;
          patchState(store, {calendrier, loadingCalendrier: false});
        } catch (err) {
          patchState(store, {loadingCalendrier: false, calendrier: null, error: optimisationErrorKey(err)});
        }
      },

      /** Ouvre le planning calendaire imprimable de la proposition affichée (PDF du modèle de document du centre). */
      async imprimerCalendrier(): Promise<void> {
        const run = store.courant();
        if (!run || run.statut !== 'TERMINEE') return;
        patchState(store, {printing: true, error: null});
        const erreur = await openPdf(api.imprimer(centerId(), run.id), `planning-propose-${run.id}.pdf`);
        patchState(store, {printing: false, error: erreur === null ? null : 'PLANNING.OPTIM.ERR.IMPRESSION'});
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

      /**
       * Supprime une exécution de l'historique (administrateur) ; si elle était affichée, l'écran est vidé. Renvoie
       * vrai si le serveur l'a supprimée.
       */
      async supprimer(id: string): Promise<boolean> {
        patchState(store, {error: null, successMessage: null});
        try {
          await firstValueFrom(api.supprimer(centerId(), id));
        } catch (err) {
          patchState(store, {error: optimisationErrorKey(err)});
          return false;
        }
        if (store.courant()?.id === id) {
          suivre(null);
          patchState(store, {courant: null, calendrier: null});
        }
        // dernière ligne d'une page > 1 supprimée : on recule d'une page plutôt que d'afficher une page vide
        const page = store.historique().length === 1 && store.pageIndex() > 0 ? store.pageIndex() - 1 : store.pageIndex();
        patchState(store, {pageIndex: page, successMessage: 'PLANNING.OPTIM.OK.SUPPRIMEE'});
        rafraichirHistorique();
        return true;
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
