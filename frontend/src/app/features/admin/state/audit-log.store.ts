import {inject} from '@angular/core';
import {patchState, signalStore, withMethods, withState} from '@ngrx/signals';
import {firstValueFrom} from 'rxjs';
import {AuditApiService, AuditEntry} from '../../../core/api/audit-api.service';

type AuditLogState = {
  rows: AuditEntry[];
  total: number;
  pageIndex: number;
  pageSize: number;
  loading: boolean;
  error: boolean;
  /** Codes d'action disponibles dans le périmètre courant, pour peupler le filtre. */
  actionCodes: string[];
  filters: { centerId: string; societeId: string; userId: string; actionCode: string; from: string; to: string };
};

const initialState: AuditLogState = {
  rows: [], total: 0, pageIndex: 0, pageSize: 20, loading: false, error: false, actionCodes: [],
  filters: {centerId: '', societeId: '', userId: '', actionCode: '', from: '', to: ''},
};

/** Journal d'audit paginé (ADMIN : son centre uniquement ; SUPERADMIN : toute la plateforme, filtrable). */
export const AuditLogStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withMethods((store, api = inject(AuditApiService)) => ({
    async load(): Promise<void> {
      patchState(store, {loading: true, error: false});
      const f = store.filters();
      try {
        const result = await firstValueFrom(api.search({
          centerId: f.centerId || undefined, societeId: f.societeId || undefined,
          userId: f.userId || undefined, actionCode: f.actionCode || undefined,
          from: f.from || undefined, to: f.to || undefined,
          page: store.pageIndex(), size: store.pageSize(),
        }));
        patchState(store, {rows: result.items, total: result.total, loading: false});
      } catch {
        patchState(store, {rows: [], total: 0, loading: false, error: true});
      }
    },

    async loadActionCodes(): Promise<void> {
      try {
        const societeId = store.filters().societeId || undefined;
        patchState(store, {actionCodes: await firstValueFrom(api.actionCodes(societeId))});
      } catch {
        patchState(store, {actionCodes: []});
      }
    },

    setPagination(pageIndex: number, pageSize: number): void {
      patchState(store, {pageIndex, pageSize});
    },

    setFilters(filters: Partial<AuditLogState['filters']>): void {
      patchState(store, {filters: {...store.filters(), ...filters}, pageIndex: 0});
    },

    clearFilters(): void {
      patchState(store, {filters: initialState.filters, pageIndex: 0});
    },
  })),
);
