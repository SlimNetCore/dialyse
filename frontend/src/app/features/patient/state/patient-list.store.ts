import {computed, effect, inject} from '@angular/core';
import {patchState, signalStore, withComputed, withHooks, withMethods, withState} from '@ngrx/signals';
import {rxMethod} from '@ngrx/signals/rxjs-interop';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {catchError, EMPTY, expand, of, pipe, reduce, switchMap, tap} from 'rxjs';
import {BackendApiService, ListQuery} from '../../../core/api/backend-api.service';
import {createPagedListState, PagedListState} from '../../../core/state/paged-list-state.util';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {AuthStore} from '../../../core/state/auth.store';

/**
 * Taille de page maximale acceptée par `POST /api/v1/patients/search` : la recherche refuse une taille hors de 1 à 100 (400, PC1,
 * RG-PAT-020). Le mode `local` charge donc tous les patients par pages de 100.
 */
export const TAILLE_DE_PAGE_MAXIMALE = 100;

/** Shared row mapping between `loadPage` (remote) and `loadAllPatients` (local test mode). */
function mapPatientRow(p: any): any {
  return {
    id: p.id,
    code: p.codePatient ?? '',
    nom: p.nom ?? '',
    prenom: p.prenom ?? '',
    sexe: p.sexe ?? '',
    dateAdmission: p.dateAdmission ?? '',
    numeroAssurance: p.numeroAssurance ?? '',
    etatPatient: p.etatPatient ?? 'PERMANENT',
    dateEvenementEtat: p.dateEvenementEtat ?? p.dateEvenement ?? '',
    nonFacturable: !!p.nonFacturable,
    medecinTraitantId: p.medecinTraitantId ?? '',
    medecinTraitantNom: p.medecinTraitantNom ?? '',
    positionId: p.positionId ?? '',
    positionCode: p.positionCode ?? '',
    positionLibelle: p.positionLibelle ?? '',
    transporteurAllerId: p.transporteurAllerId ?? '',
    transporteurAllerNom: p.transporteurAllerNom ?? '',
    transporteurRetourId: p.transporteurRetourId ?? '',
    transporteurRetourNom: p.transporteurRetourNom ?? '',
    joursDialyse: {
      dimanche: p.jourDimanche ?? p.jour_dimanche ?? (p.joursDialyse ?? p.jours_dialyse)?.dimanche ?? false,
      lundi: p.jourLundi ?? p.jour_lundi ?? (p.joursDialyse ?? p.jours_dialyse)?.lundi ?? false,
      mardi: p.jourMardi ?? p.jour_mardi ?? (p.joursDialyse ?? p.jours_dialyse)?.mardi ?? false,
      mercredi: p.jourMercredi ?? p.jour_mercredi ?? (p.joursDialyse ?? p.jours_dialyse)?.mercredi ?? false,
      jeudi: p.jourJeudi ?? p.jour_jeudi ?? (p.joursDialyse ?? p.jours_dialyse)?.jeudi ?? false,
      vendredi: p.jourVendredi ?? p.jour_vendredi ?? (p.joursDialyse ?? p.jours_dialyse)?.vendredi ?? false,
      samedi: p.jourSamedi ?? p.jour_samedi ?? (p.joursDialyse ?? p.jours_dialyse)?.samedi ?? false
    },
    pecStatus: p.pecStatus ?? '',
    pecForfaitId: p.pecForfaitId ?? '',
    pecForfaitCode: p.pecForfaitCode ?? '',
    pecForfaitLibelle: p.pecForfaitLibelle ?? ''
  };
}

/** État de la liste des patients (la synthèse mensuelle vit dans PatientTableauDeBordStore). */
type PatientListState = PagedListState<any> & {
  exportingList: boolean;
  printingRowId: string | null;
  recentPatientId: string | null;
  activeCenterId: string | null;
  activeUserId: string | null;
  error: string | null;
  sortColumnId: string;
  sortDirection: '' | 'asc' | 'desc';
  /**
   * Which fetch strategy `app-configurable-list` expects: `local` loads everything
   * once (`loadAllPatients`) and never refetches on filter/sort/page; `remote` fetches
   * per-page with server-side filter/sort (`loadPage`). Set once by the component via
   * `setDataMode` to match its own `[dataMode]` binding.
   */
  dataMode: 'local' | 'remote';
};

const initialState: PatientListState = {
  ...createPagedListState<any>(),
  exportingList: false,
  printingRowId: null,
  recentPatientId: null,
  activeCenterId: null,
  activeUserId: null,
  error: null,
  sortColumnId: '',
  sortDirection: '',
  dataMode: 'local'
};

export const PatientListStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withDevtools('PatientListStore'),
  withComputed((store) => ({
    hasActiveFilters: computed(() =>
      Object.values(store.columnFilters()).some((v) => !!v?.toString().trim())
    ),
    isEmpty: computed(() => store.rows().length === 0)
  })),
  withMethods((store, api = inject(BackendApiService)) => ({
    // ✅ Charger les patients avec pagination et filtres
    loadPage: rxMethod<{ centerId: string; page: number; size: number; userId: string }>(
      pipe(
        tap(({centerId, userId}) => patchState(store, {
          loading: true,
          error: null,
          activeCenterId: centerId,
          activeUserId: userId
        })),
        switchMap(({centerId, page, size, userId}) => {
          const query: ListQuery = {
            page,
            size,
            filters: store.columnFilters(),
            sortColumnId: store.sortColumnId() || undefined,
            sortDirection: store.sortDirection() || undefined
          };
          return api.listPatients(centerId, userId, query).pipe(
            tap((res: any) => {
              patchState(store, {
                rows: (res.items ?? []).map(mapPatientRow),
                total: res.total ?? 0,
                pageIndex: res.page ?? page,
                loading: false
              });
            }),
            catchError((err: any) => {
              patchState(store, {
                rows: [],
                total: 0,
                pageIndex: page,
                loading: false,
                error: err?.error?.message || err?.statusText || 'Erreur chargement patients'
              });
              return of(null);
            })
          );
        })
      )
    ),

    /**
     * Mode LOCAL (test) : charge la totalité des patients du centre en paginant en
     * interne (taille de page max autorisée côté backend = 200), sans filtre/tri
     * serveur — tout le filtrage/tri/pagination se fait ensuite côté client dans
     * app-configurable-list (`dataMode="local"`).
     */
    loadAllPatients: rxMethod<{ centerId: string; userId: string }>(
      pipe(
        tap(({centerId, userId}) => patchState(store, {
          loading: true,
          error: null,
          activeCenterId: centerId,
          activeUserId: userId
        })),
        switchMap(({centerId, userId}) => {
          const pageSize = TAILLE_DE_PAGE_MAXIMALE;
          const fetchPage = (page: number) => api.listPatients(centerId, userId, {page, size: pageSize});

          return fetchPage(0).pipe(
            expand((res: any) => {
              const loadedSoFar = ((res.page ?? 0) + 1) * pageSize;
              return loadedSoFar < (res.total ?? 0) ? fetchPage((res.page ?? 0) + 1) : EMPTY;
            }),
            reduce((acc: any[], res: any) => [...acc, ...(res.items ?? [])], [] as any[]),
            tap((allItems: any[]) => {
              patchState(store, {
                rows: allItems.map(mapPatientRow),
                total: allItems.length,
                pageIndex: 0,
                loading: false
              });
            }),
            catchError((err: any) => {
              patchState(store, {
                rows: [],
                total: 0,
                pageIndex: 0,
                loading: false,
                error: err?.error?.message || err?.statusText || 'Erreur chargement patients'
              });
              return of(null);
            })
          );
        })
      )
    ),

    // ✅ Imprimer fiche patient (Blob)
    printFiche: rxMethod<{ centerId: string; patientId: string }>(
      pipe(
        tap(() => patchState(store, {error: null})),
        switchMap(({centerId, patientId}) =>
          api.printDocument(centerId, 'FICHE_PATIENT', {patientId}).pipe(
            tap((blob: Blob) => {
              const url = URL.createObjectURL(blob);
              window.open(url, '_blank');
            }),
            catchError((err: any) => {
              patchState(store, {
                error: 'Erreur impression: ' + (err?.error?.message || err?.statusText)
              });
              return of(null);
            })
          )
        )
      )
    ),

    /**
     * Export de la liste : TOUTES les lignes qui répondent aux filtres et au tri affichés (pas seulement la page
     * courante), lues par pages de {@link TAILLE_DE_PAGE_MAXIMALE} avec la même requête que la liste. `onRows` reçoit
     * les lignes, dans l'ordre affiché, pour produire le fichier avec les colonnes visibles.
     */
    exportFilteredRows: rxMethod<{ centerId: string; userId: string; onRows: (rows: any[]) => void }>(
      pipe(
        tap(() => patchState(store, {exportingList: true, error: null})),
        switchMap(({centerId, userId, onRows}) => {
          const fetchPage = (page: number) => api.listPatients(centerId, userId, {
            page,
            size: TAILLE_DE_PAGE_MAXIMALE,
            filters: store.columnFilters(),
            sortColumnId: store.sortColumnId() || undefined,
            sortDirection: store.sortDirection() || undefined
          });
          return fetchPage(0).pipe(
            expand((res: any) => {
              const loadedSoFar = ((res.page ?? 0) + 1) * TAILLE_DE_PAGE_MAXIMALE;
              return loadedSoFar < (res.total ?? 0) ? fetchPage((res.page ?? 0) + 1) : EMPTY;
            }),
            reduce((acc: any[], res: any) => [...acc, ...(res.items ?? [])], [] as any[]),
            tap((items: any[]) => {
              onRows(items.map(mapPatientRow));
              patchState(store, {exportingList: false});
            }),
            catchError((err: any) => {
              patchState(store, {
                exportingList: false,
                error: 'Erreur export: ' + (err?.error?.message || err?.statusText)
              });
              return of(null);
            })
          );
        })
      )
    ),

    setLoading(loading: boolean): void {
      patchState(store, {loading});
    },

    setPrintingRowId(printingRowId: string | null): void {
      patchState(store, {printingRowId});
    },

    setRecentPatient(recentPatientId: string | null): void {
      patchState(store, {recentPatientId});
    },

    clearRecentPatient(): void {
      patchState(store, {recentPatientId: null});
    },

    setPageData(rows: any[], total: number, pageIndex: number): void {
      patchState(store, {rows, total, pageIndex});
    },

    /**
     * Combined mode `remote` entry point: patches filters + sort + page in a single
     * state transition (one `patchState` = one reactive fetch, not three).
     */
    applyRemoteQuery(query: {
      sort: { columnId: string; direction: '' | 'asc' | 'desc' };
      filters: Record<string, string>;
      page: { index: number; size: number };
    }): void {
      patchState(store, {
        columnFilters: query.filters,
        sortColumnId: query.sort.columnId,
        sortDirection: query.sort.direction,
        pageIndex: query.page.index,
        pageSize: query.page.size > 0 ? query.page.size : store.pageSize()
      });
    },

    setPagination(pageIndex: number, pageSize: number): void {
      patchState(store, {pageIndex, pageSize});
    },

    /** Set once by the component at startup to match its `[dataMode]` binding. */
    setDataMode(mode: 'local' | 'remote'): void {
      patchState(store, {dataMode: mode});
    },

    setFilter(column: string, value: string): void {
      patchState(store, {
        pageIndex: 0,
        columnFilters: {
          ...store.columnFilters(),
          [column]: value
        }
      });
    },

    clearFilter(column: string): void {
      patchState(store, {
        pageIndex: 0,
        columnFilters: {
          ...store.columnFilters(),
          [column]: ''
        }
      });
    },

    clearAllFilters(): void {
      patchState(store, {columnFilters: {}, pageIndex: 0});
    },

    refreshCurrentPage(): void {
      const centerId = store.activeCenterId();
      const userId = store.activeUserId();
      if (!centerId || !userId) return;
      if (store.dataMode() === 'local') {
        this.loadAllPatients({centerId, userId});
        return;
      }
      this.loadPage({
        centerId,
        userId,
        page: store.pageIndex(),
        size: store.pageSize()
      });
    }
  })),
  withHooks((store, appShell = inject(AppShellStore), auth = inject(AuthStore)) => ({
    onInit() {
      effect(() => {
        // Chargement automatique — la stratégie dépend de `dataMode` (fixé une fois
        // par le composant via `setDataMode`, cf. son constructeur) :
        // - `local` : ne dépend QUE du centre/utilisateur ; tout le reste (filtres,
        //   tri, pagination) est géré côté client par app-configurable-list et ne
        //   déclenche plus aucun rechargement — `loadAllPatients` charge tout une fois.
        // - `remote` : dépend aussi de la pagination/du tri/des filtres, chacun de ces
        //   changements déclenche un `loadPage` (filtrage/tri/pagination serveur).
        const centerId = appShell.currentCenterId();
        const userId = auth.username() ?? 'demo';
        const mode = store.dataMode();

        if (!centerId) {
          patchState(store, {
            rows: [],
            total: 0,
            loading: false,
            activeCenterId: null,
            activeUserId: userId
          });
          return;
        }

        if (mode === 'local') {
          store.loadAllPatients({centerId, userId});
          return;
        }

        const page = store.pageIndex();
        const size = store.pageSize();
        store.columnFilters();
        store.sortColumnId();
        store.sortDirection();
        store.loadPage({centerId, userId, page, size});
      });
    }
  }))
);









