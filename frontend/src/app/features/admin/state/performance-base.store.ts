import {inject} from '@angular/core';
import {patchState, signalStore, withMethods, withState} from '@ngrx/signals';
import {firstValueFrom} from 'rxjs';
import {
  RequeteBase, StatutSupervision, SupervisionApiService, TriRequetes,
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
};

const initialState: PerformanceBaseState = {
  statut: null, rows: [], total: 0, pageIndex: 0, pageSize: 20, tri: 'TEMPS_TOTAL',
  loading: false, error: false, resetError: false,
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
