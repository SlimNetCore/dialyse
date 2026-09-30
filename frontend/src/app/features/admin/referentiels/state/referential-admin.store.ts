import {computed, inject} from '@angular/core';
import {patchState, signalStore, withComputed, withMethods, withState} from '@ngrx/signals';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {firstValueFrom} from 'rxjs';
import {
  ImportReport,
  ReferentialAdminApiService,
  ReferentialEntry,
  ReferentialKindDef,
  ValidationIssue,
} from '../../../../core/api/referential-admin-api.service';
import {AppShellStore} from '../../../../core/state/app-shell.store';
import {httpErrorMessage, httpIssues} from '../referential-admin.util';

export interface RefOption {
  value: string;
  label: string;
}

type ReferentialAdminState = {
  kinds: ReferentialKindDef[];
  activeSlug: string | null;
  rows: ReferentialEntry[];
  total: number;
  pageIndex: number;
  pageSize: number;
  search: string;
  loading: boolean;
  saving: boolean;
  /** Message d'erreur (texte serveur ou clé i18n). */
  error: string | null;
  /** Anomalies champ par champ de la dernière saisie refusée. */
  formIssues: ValidationIssue[];
  /** Options des listes déroulantes des champs REFERENCE, par slug cible. */
  referenceOptions: Record<string, RefOption[]>;
  importReport: ImportReport | null;
  importing: boolean;
  importError: string | null;
};

const initialState: ReferentialAdminState = {
  kinds: [], activeSlug: null, rows: [], total: 0, pageIndex: 0, pageSize: 20, search: '',
  loading: false, saving: false, error: null, formIssues: [], referenceOptions: {},
  importReport: null, importing: false, importError: null,
};

/** Taille maximale acceptée par le backend pour une page (options des listes déroulantes). */
const OPTIONS_PAGE_SIZE = 100;

/**
 * Administration des référentiels du centre actif : liste paginée + recherche, saisie, suppression,
 * vérification puis import de fichiers CSV / Excel.
 */
export const ReferentialAdminStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withDevtools('ReferentialAdminStore'),
  withComputed((store) => ({
    activeKind: computed(() => store.kinds().find((k) => k.slug === store.activeSlug()) ?? null),
    isEmpty: computed(() => store.rows().length === 0),
  })),
  withMethods((store, api = inject(ReferentialAdminApiService), appShell = inject(AppShellStore)) => {
    const centerId = (): string | null => appShell.currentCenterId();

    async function load(): Promise<void> {
      const slug = store.activeSlug();
      const center = centerId();
      if (!slug || !center) return;
      patchState(store, {loading: true, error: null});
      try {
        const page = await firstValueFrom(api.list(slug, {
          centerId: center, search: store.search(), page: store.pageIndex(), size: store.pageSize(),
        }));
        patchState(store, {rows: page.items, total: page.total, loading: false});
      } catch (err) {
        patchState(store, {
          rows: [], total: 0, loading: false,
          error: httpErrorMessage(err, 'ADMIN.REFERENTIALS.LOAD_ERROR')
        });
      }
    }

    async function loadReferenceOptions(): Promise<void> {
      const center = centerId();
      const kind = store.kinds().find((k) => k.slug === store.activeSlug());
      if (!center || !kind) return;
      const targets = kind.fields.filter((f) => f.type === 'REFERENCE' && f.reference).map((f) => f.reference!);
      for (const target of targets) {
        try {
          const page = await firstValueFrom(api.list(target, {centerId: center, page: 0, size: OPTIONS_PAGE_SIZE}));
          const targetKind = store.kinds().find((k) => k.slug === target);
          const options = page.items.map((item) => ({value: item.id, label: optionLabel(item, targetKind)}));
          patchState(store, {referenceOptions: {...store.referenceOptions(), [target]: options}});
        } catch {
          patchState(store, {referenceOptions: {...store.referenceOptions(), [target]: []}});
        }
      }
    }

    return {
      async loadKinds(): Promise<void> {
        try {
          const kinds = await firstValueFrom(api.kinds());
          patchState(store, {kinds});
          if (!store.activeSlug() && kinds.length > 0) patchState(store, {activeSlug: kinds[0].slug});
        } catch (err) {
          patchState(store, {error: httpErrorMessage(err, 'ADMIN.REFERENTIALS.LOAD_ERROR')});
        }
      },

      async selectKind(slug: string): Promise<void> {
        patchState(store, {
          activeSlug: slug, pageIndex: 0, search: '', rows: [], total: 0,
          importReport: null, importError: null, formIssues: []
        });
        await Promise.all([load(), loadReferenceOptions()]);
      },

      load,
      loadReferenceOptions,

      async setSearch(search: string): Promise<void> {
        patchState(store, {search, pageIndex: 0});
        await load();
      },

      async setPagination(pageIndex: number, pageSize: number): Promise<void> {
        patchState(store, {pageIndex, pageSize});
        await load();
      },

      /** Crée (`id` absent) ou modifie une ligne ; `true` si enregistrée, sinon `formIssues`/`error` renseignés. */
      async save(values: Record<string, string | null>, id?: string): Promise<boolean> {
        const slug = store.activeSlug();
        const center = centerId();
        if (!slug || !center) return false;
        patchState(store, {saving: true, error: null, formIssues: []});
        try {
          await firstValueFrom(id ? api.update(slug, id, center, values) : api.create(slug, center, values));
          patchState(store, {saving: false});
          await load();
          return true;
        } catch (err) {
          patchState(store, {
            saving: false, formIssues: httpIssues(err),
            error: httpErrorMessage(err, 'ADMIN.REFERENTIALS.SAVE_ERROR')
          });
          return false;
        }
      },

      async remove(id: string): Promise<boolean> {
        const slug = store.activeSlug();
        const center = centerId();
        if (!slug || !center) return false;
        patchState(store, {error: null});
        try {
          await firstValueFrom(api.delete(slug, id, center));
          if (store.rows().length === 1 && store.pageIndex() > 0) patchState(store, {pageIndex: store.pageIndex() - 1});
          await load();
          return true;
        } catch (err) {
          patchState(store, {error: httpErrorMessage(err, 'ADMIN.REFERENTIALS.DELETE_ERROR')});
          return false;
        }
      },

      /** Vérifie (`dryRun`) ou importe un fichier ; le compte rendu est exposé dans `importReport`. */
      async importFile(file: File, dryRun: boolean): Promise<ImportReport | null> {
        const slug = store.activeSlug();
        const center = centerId();
        if (!slug || !center) return null;
        patchState(store, {importing: true, importError: null});
        try {
          const report = await firstValueFrom(api.importFile(slug, center, file, dryRun));
          patchState(store, {importing: false, importReport: report});
          if (report.applied) {
            patchState(store, {pageIndex: 0});
            await load();
          }
          return report;
        } catch (err) {
          patchState(store, {
            importing: false, importReport: null,
            importError: httpErrorMessage(err, 'ADMIN.REFERENTIALS.IMPORT.ERROR')
          });
          return null;
        }
      },

      clearImport(): void {
        patchState(store, {importReport: null, importError: null, importing: false});
      },

      clearFormIssues(): void {
        patchState(store, {formIssues: [], error: null});
      },

      /** Changement de centre : les données du centre précédent ne doivent plus être affichées. */
      resetForCenterChange(): void {
        patchState(store, {rows: [], total: 0, pageIndex: 0, referenceOptions: {}, importReport: null, error: null});
      },
    };
  }),
);

function optionLabel(item: ReferentialEntry, kind: ReferentialKindDef | undefined): string {
  const keys = kind ? kind.fields.filter((f) => f.type === 'TEXT').slice(0, 2).map((f) => f.key) : [];
  const parts = keys.map((k) => item.values[k]).filter((v): v is string => !!v);
  return parts.length ? parts.join(' · ') : item.id;
}

