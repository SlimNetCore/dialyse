import {computed, inject} from '@angular/core';
import {patchState, signalStore, withComputed, withMethods, withState} from '@ngrx/signals';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {firstValueFrom} from 'rxjs';
import {
  MigrationApiService,
  MigrationBatch,
  MigrationEntityDef,
  MigrationRun,
  OpenBatchPayload,
  ValueMapping,
} from '../../../../core/api/migration-api.service';
import {AppShellStore} from '../../../../core/state/app-shell.store';
import {httpErrorMessage} from '../../referentiels/referential-admin.util';

type MigrationState = {
  entities: MigrationEntityDef[];
  /** Lot en cours du centre (un seul à la fois), ou dernier lot consulté. */
  batch: MigrationBatch | null;
  /** Dernier compte rendu par donnée reprise (slug → compte rendu). */
  runs: Record<string, MigrationRun>;
  /** Donnée en cours de vérification / d'import. */
  busyEntity: string | null;
  history: MigrationBatch[];
  historyTotal: number;
  historyPageIndex: number;
  historyPageSize: number;
  valueMappings: ValueMapping[];
  loading: boolean;
  error: string | null;
};

const initialState: MigrationState = {
  entities: [], batch: null, runs: {}, busyEntity: null, history: [], historyTotal: 0, historyPageIndex: 0,
  historyPageSize: 10, valueMappings: [], loading: false, error: null,
};

/** Reprise des données du centre actif : lot, vérifications, imports, correspondances de valeurs. */
export const MigrationStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withDevtools('MigrationStore'),
  withComputed((store) => ({
    batchOpen: computed(() => store.batch()?.status === 'EN_COURS'),
    /** Nombre de données importées dans le lot courant. */
    importedCount: computed(() => Object.values(store.runs()).filter((r) => r.applied).length),
  })),
  withMethods((store, api = inject(MigrationApiService), appShell = inject(AppShellStore)) => {
    const centerId = (): string | null => appShell.currentCenterId();

    async function loadHistory(): Promise<void> {
      const center = centerId();
      if (!center) return;
      try {
        const page = await firstValueFrom(api.batches(center, store.historyPageIndex(), store.historyPageSize()));
        patchState(store, {history: page.items, historyTotal: page.total});
      } catch (err) {
        patchState(store, {error: httpErrorMessage(err, 'ADMIN.MIGRATION.LOAD_ERROR')});
      }
    }

    async function loadDetail(batchId: string): Promise<void> {
      const center = centerId();
      if (!center) return;
      const detail = await firstValueFrom(api.detail(center, batchId));
      patchState(store, {batch: detail.batch, runs: Object.fromEntries(detail.runs.map((r) => [r.entity, r]))});
    }

    async function loadValueMappings(): Promise<void> {
      const center = centerId();
      if (!center) return;
      try {
        patchState(store, {valueMappings: await firstValueFrom(api.valueMappings(center))});
      } catch {
        patchState(store, {valueMappings: []});
      }
    }

    return {
      /** Chargement initial (ou changement de centre) : données reprises, historique, lot en cours. */
      async init(): Promise<void> {
        patchState(store, {...initialState, entities: store.entities(), loading: true});
        try {
          if (store.entities().length === 0) patchState(store, {entities: await firstValueFrom(api.entities())});
          await loadHistory();
          const active = store.history().find((b) => b.status === 'EN_COURS');
          if (active) await loadDetail(active.id);
          await loadValueMappings();
        } catch (err) {
          patchState(store, {error: httpErrorMessage(err, 'ADMIN.MIGRATION.LOAD_ERROR')});
        } finally {
          patchState(store, {loading: false});
        }
      },

      loadHistory,
      loadValueMappings,

      async setHistoryPage(pageIndex: number, pageSize: number): Promise<void> {
        patchState(store, {historyPageIndex: pageIndex, historyPageSize: pageSize});
        await loadHistory();
      },

      /** Affiche le détail d'un lot de l'historique (lecture seule s'il est terminé ou annulé). */
      async select(batchId: string): Promise<void> {
        patchState(store, {error: null});
        try {
          await loadDetail(batchId);
        } catch (err) {
          patchState(store, {error: httpErrorMessage(err, 'ADMIN.MIGRATION.LOAD_ERROR')});
        }
      },

      async open(payload: OpenBatchPayload): Promise<boolean> {
        const center = centerId();
        if (!center) return false;
        patchState(store, {error: null, loading: true});
        try {
          const batch = await firstValueFrom(api.open(center, payload));
          patchState(store, {batch, runs: {}, historyPageIndex: 0});
          await loadHistory();
          return true;
        } catch (err) {
          patchState(store, {error: httpErrorMessage(err, 'ADMIN.MIGRATION.OPEN_ERROR')});
          return false;
        } finally {
          patchState(store, {loading: false});
        }
      },

      /** Vérifie (`dryRun`) ou importe le fichier d'une donnée ; le compte rendu remplace le précédent. */
      async importFile(entity: string, file: File, dryRun: boolean): Promise<MigrationRun | null> {
        const center = centerId();
        const batch = store.batch();
        if (!center || !batch) return null;
        patchState(store, {busyEntity: entity, error: null});
        try {
          const run = await firstValueFrom(api.importFile(center, batch.id, entity, file, dryRun));
          patchState(store, {runs: {...store.runs(), [entity]: run}});
          return run;
        } catch (err) {
          patchState(store, {error: httpErrorMessage(err, 'ADMIN.MIGRATION.IMPORT_ERROR')});
          return null;
        } finally {
          patchState(store, {busyEntity: null});
        }
      },

      async close(): Promise<boolean> {
        return lifecycle((center, id) => api.close(center, id), 'ADMIN.MIGRATION.CLOSE_ERROR');
      },

      async cancel(): Promise<boolean> {
        return lifecycle((center, id) => api.cancel(center, id), 'ADMIN.MIGRATION.CANCEL_ERROR');
      },

      async saveValueMapping(mapping: ValueMapping): Promise<boolean> {
        const center = centerId();
        if (!center) return false;
        try {
          await firstValueFrom(api.saveValueMapping(center, mapping));
          await loadValueMappings();
          return true;
        } catch (err) {
          patchState(store, {error: httpErrorMessage(err, 'ADMIN.MIGRATION.MAPPING_ERROR')});
          return false;
        }
      },

      async deleteValueMapping(mapping: ValueMapping): Promise<void> {
        const center = centerId();
        if (!center) return;
        try {
          await firstValueFrom(api.deleteValueMapping(center, mapping.column, mapping.source));
          await loadValueMappings();
        } catch (err) {
          patchState(store, {error: httpErrorMessage(err, 'ADMIN.MIGRATION.MAPPING_ERROR')});
        }
      },

      clearError(): void {
        patchState(store, {error: null});
      },

      /** Quitte l'affichage d'un lot terminé / annulé pour en ouvrir un nouveau. */
      deselect(): void {
        patchState(store, {batch: null, runs: {}, error: null});
      },
    };

    async function lifecycle(call: (center: string, batchId: string) => ReturnType<MigrationApiService['close']>,
                             errorKey: string): Promise<boolean> {
      const center = centerId();
      const batch = store.batch();
      if (!center || !batch) return false;
      patchState(store, {error: null, loading: true});
      try {
        patchState(store, {batch: await firstValueFrom(call(center, batch.id))});
        await loadHistory();
        return true;
      } catch (err) {
        patchState(store, {error: httpErrorMessage(err, errorKey)});
        return false;
      } finally {
        patchState(store, {loading: false});
      }
    }
  }),
);


