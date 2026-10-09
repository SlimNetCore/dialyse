import {inject} from '@angular/core';
import {patchState, signalStore, withMethods, withState} from '@ngrx/signals';
import {firstValueFrom} from 'rxjs';
import {
  AnalyseRequete, RequeteBase, SanteBase, StatutSupervision, SupervisionApiService, TriRequetes,
} from '../../../core/api/supervision-api.service';

type PerformanceBaseState = {
  statut: StatutSupervision | null;
  rows: RequeteBase[];
  total: number;
  pageIndex: number;
  pageSize: number;
  tri: TriRequetes;
  loading: boolean;
  error: boolean;
  /** Erreur de la dernière remise à zéro (droits PostgreSQL insuffisants, mesure indisponible). */
  resetError: boolean;
  /** Plan d'exécution de la requête analysée (bouton « Analyser »). */
  analyse: AnalyseRequete | null;
  analyseLoading: boolean;
  analyseError: boolean;
  /** Santé du moteur (onglet « Santé »). */
  sante: SanteBase | null;
  santeLoading: boolean;
  santeError: boolean;
};

const initialState: PerformanceBaseState = {
  statut: null, rows: [], total: 0, pageIndex: 0, pageSize: 20, tri: 'TEMPS_TOTAL',
  loading: false, error: false, resetError: false,
  analyse: null, analyseLoading: false, analyseError: false,
  sante: null, santeLoading: false, santeError: false,
};

/** Requêtes les plus coûteuses de la base (propriétaire) : statut de la mesure, classement paginé, remise à zéro. */
export const PerformanceBaseStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withMethods((store, api = inject(SupervisionApiService)) => ({
    async load(): Promise<void> {
      patchState(store, {loading: true, error: false});
      try {
        const statut = await firstValueFrom(api.statut());
        if (!statut.disponible) {
          patchState(store, {statut, rows: [], total: 0, loading: false});
          return;
        }
        const page = await firstValueFrom(api.requetes(store.tri(), store.pageIndex(), store.pageSize()));
        patchState(store, {statut, rows: page.items, total: page.total, loading: false});
      } catch {
        patchState(store, {rows: [], total: 0, loading: false, error: true});
      }
    },

    setTri(tri: TriRequetes): void {
      patchState(store, {tri, pageIndex: 0});
    },

    setPagination(pageIndex: number, pageSize: number): void {
      patchState(store, {pageIndex, pageSize});
    },

    /** Établit le plan d'exécution d'une requête mesurée (sans l'exécuter). */
    async analyser(id: string): Promise<void> {
      patchState(store, {analyse: null, analyseLoading: true, analyseError: false});
      try {
        patchState(store, {analyse: await firstValueFrom(api.analyse(id)), analyseLoading: false});
      } catch {
        patchState(store, {analyseLoading: false, analyseError: true});
      }
    },

    fermerAnalyse(): void {
      patchState(store, {analyse: null, analyseLoading: false, analyseError: false});
    },

    async chargerSante(): Promise<void> {
      patchState(store, {santeLoading: true, santeError: false});
      try {
        patchState(store, {sante: await firstValueFrom(api.sante()), santeLoading: false});
      } catch {
        patchState(store, {sante: null, santeLoading: false, santeError: true});
      }
    },

    /** Remet les compteurs à zéro puis recharge ; renvoie false si PostgreSQL a refusé. */
    async reinitialiser(): Promise<boolean> {
      patchState(store, {resetError: false});
      try {
        await firstValueFrom(api.reinitialiser());
        patchState(store, {pageIndex: 0});
        await this.load();
        return true;
      } catch {
        patchState(store, {resetError: true});
        return false;
      }
    },
  })),
);
