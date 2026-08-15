import {computed, effect, inject} from '@angular/core';
import {patchState, signalStore, withComputed, withHooks, withMethods, withState} from '@ngrx/signals';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {rxMethod} from '@ngrx/signals/rxjs-interop';
import {catchError, EMPTY, of, pipe, switchMap, tap} from 'rxjs';
import {
  ComptabiliteApiService,
  ComptabiliteListQuery,
  EcritureComptableItem,
  JournalCode,
  MappingComptableItem,
  RegleTVAItem,
  StatutEcriture,
} from '../../../core/api/comptabilite-api.service';
import {createPagedListState, PagedListState} from '../../../core/state/paged-list-state.util';
import {AppShellStore} from '../../../core/state/app-shell.store';

export type ComptabiliteState = PagedListState<EcritureComptableItem> & {
  activeCenterId: string | null;
  year: number;
  month: number | null;
  journalCode: JournalCode | null;
  statut: StatutEcriture | null;
  mapping: MappingComptableItem | null;
  mappingLoading: boolean;
  regles: RegleTVAItem[];
  reglesLoading: boolean;
  exporting: boolean;
  cloturant: boolean;
  expandedEcritureId: string | null;
  error: string | null;
  successMessage: string | null;
};

const currentDate = new Date();

const initialState: ComptabiliteState = {
  ...createPagedListState<EcritureComptableItem>({pageSize: 20}),
  activeCenterId: null,
  year: currentDate.getFullYear(),
  month: currentDate.getMonth() + 1,
  journalCode: null,
  statut: null,
  mapping: null,
  mappingLoading: false,
  regles: [],
  reglesLoading: false,
  exporting: false,
  cloturant: false,
  expandedEcritureId: null,
  error: null,
  successMessage: null,
};

export const ComptabiliteStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withDevtools('ComptabiliteStore'),
  withComputed((store) => ({
    totalEcritures: computed(() => store.total()),
    totalDebitPeriode: computed(() =>
      store.rows().reduce((sum, e) => sum + Number(e.totalDebit ?? 0), 0)
    ),
    hasMapping: computed(() => store.mapping() !== null),
  })),
  withMethods((store, api = inject(ComptabiliteApiService)) => ({
    setYear(year: number): void {
      patchState(store, {year, pageIndex: 0});
    },
    setMonth(month: number | null): void {
      patchState(store, {month, pageIndex: 0});
    },
    setJournalCode(journalCode: JournalCode | null): void {
      patchState(store, {journalCode, pageIndex: 0});
    },
    setStatut(statut: StatutEcriture | null): void {
      patchState(store, {statut, pageIndex: 0});
    },
    setPagination(pageIndex: number, pageSize: number): void {
      patchState(store, {pageIndex, pageSize});
    },
    toggleExpandedRow(ecritureId: string | null): void {
      const currentExpanded = store.expandedEcritureId();
      patchState(store, {
        expandedEcritureId: currentExpanded === ecritureId ? null : ecritureId
      });
    },
    clearMessages(): void {
      patchState(store, {error: null, successMessage: null});
    },

    loadEcritures: rxMethod<{ centerId: string }>(
      pipe(
        tap(({centerId}) => patchState(store, {loading: true, error: null, activeCenterId: centerId})),
        switchMap(({centerId}) => {
          const year = store.year();
          const month = store.month();
          const from = month
            ? `${year}-${String(month).padStart(2, '0')}-01`
            : `${year}-01-01`;
          const lastDay = month
            ? new Date(year, month, 0).getDate()
            : 31;
          const to = month
            ? `${year}-${String(month).padStart(2, '0')}-${String(lastDay).padStart(2, '0')}`
            : `${year}-12-31`;

          const query: ComptabiliteListQuery = {
            centerId, from, to,
            journalCode: store.journalCode(),
            statut: store.statut(),
            page: store.pageIndex(),
            size: store.pageSize(),
          };
          return api.searchEcritures(query).pipe(
            tap((response) => patchState(store, {
              rows: response.items ?? [],
              total: response.total ?? 0,
              pageIndex: response.page ?? store.pageIndex(),
              pageSize: response.size ?? store.pageSize(),
              loading: false,
            })),
            catchError((err: unknown) => {
              patchState(store, {rows: [], total: 0, loading: false, error: errorMessage(err)});
              return of(null);
            })
          );
        })
      )
    ),

    loadMapping: rxMethod<{ centerId: string }>(
      pipe(
        tap(() => patchState(store, {mappingLoading: true})),
        switchMap(({centerId}) =>
          api.getMapping(centerId).pipe(
            tap((mapping) => patchState(store, {mapping, mappingLoading: false})),
            catchError((err: unknown) => {
              patchState(store, {mappingLoading: false, error: errorMessage(err)});
              return of(null);
            })
          )
        )
      )
    ),

    loadRegles: rxMethod<{ centerId: string }>(
      pipe(
        tap(() => patchState(store, {reglesLoading: true})),
        switchMap(({centerId}) =>
          api.getRegles(centerId).pipe(
            tap((regles) => patchState(store, {regles, reglesLoading: false})),
            catchError((err: unknown) => {
              patchState(store, {reglesLoading: false, error: errorMessage(err)});
              return of(null);
            })
          )
        )
      )
    ),

    exporterJournal: rxMethod<{ centerId: string; from: string; to: string; journalCode: JournalCode }>(
      pipe(
        tap(() => patchState(store, {exporting: true, error: null})),
        switchMap(({centerId, from, to, journalCode}) =>
          api.exporterJournal(centerId, from, to, journalCode).pipe(
            tap((blob) => {
              const url = URL.createObjectURL(blob);
              const a = document.createElement('a');
              a.href = url;
              a.download = `journal-${journalCode}-${from}.csv`;
              a.click();
              URL.revokeObjectURL(url);
              patchState(store, {exporting: false});
            }),
            catchError((err: unknown) => {
              patchState(store, {exporting: false, error: errorMessage(err)});
              return of(null);
            })
          )
        )
      )
    ),

    cloturerPeriode: rxMethod<{ centerId: string; annee: number; mois: number; userId: string }>(
      pipe(
        tap(() => patchState(store, {cloturant: true, error: null})),
        switchMap(({centerId, annee, mois, userId}) =>
          api.cloturerPeriode(centerId, annee, mois, userId).pipe(
            tap(() => patchState(store, {
              cloturant: false,
              successMessage: 'COMPTABILITE.SUCCESS.PERIODE_CLOTUREE',
            })),
            catchError((err: unknown) => {
              patchState(store, {cloturant: false, error: errorMessage(err)});
              return EMPTY;
            })
          )
        )
      )
    ),
  })),
  withHooks((store, appShell = inject(AppShellStore)) => ({
    onInit() {
      effect(() => {
        const centerId = appShell.currentCenterId();
        if (!centerId) {
          patchState(store, {rows: [], total: 0, loading: false, activeCenterId: null});
          return;
        }
        store.year();
        store.month();
        store.journalCode();
        store.statut();
        store.pageIndex();
        store.pageSize();
        store.loadEcritures({centerId});
      });
      effect(() => {
        const centerId = appShell.currentCenterId();
        if (!centerId) return;
        store.loadMapping({centerId});
        store.loadRegles({centerId});
      });
    },
  }))
);

function errorMessage(err: unknown): string {
  if (err && typeof err === 'object') {
    const v = err as Record<string, unknown>;
    if (typeof v['message'] === 'string') return v['message'];
    const nested = v['error'];
    if (nested && typeof nested === 'object') {
      const n = nested as Record<string, unknown>;
      if (typeof n['message'] === 'string') return n['message'];
    }
  }
  return 'COMPTABILITE.ERROR.GENERIC';
}






