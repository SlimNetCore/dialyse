import {computed, effect, inject} from '@angular/core';
import {patchState, signalStore, withComputed, withHooks, withMethods, withState} from '@ngrx/signals';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {rxMethod} from '@ngrx/signals/rxjs-interop';
import {catchError, EMPTY, forkJoin, of, pipe, switchMap, tap} from 'rxjs';
import {
  BackendApiService,
  ReglementDashboardResponse,
  ReglementInvoiceRow,
  ReglementListQuery,
} from '../../../core/api/backend-api.service';
import {createPagedListState, PagedListState} from '../../../core/state/paged-list-state.util';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {AuthStore} from '../../../core/state/auth.store';
import {ReferentialApiService, RefItem} from '../../../core/api/referential-api.service';

const currentDate = new Date();

export type ReglementState = PagedListState<ReglementInvoiceRow> & {
  activeCenterId: string | null;
  activeUserId: string | null;
  year: number;
  month: number | null;
  caisseId: string | null;
  agenceId: string | null;
  centrePayeurId: string | null;
  dashboardLoading: boolean;
  dashboard: ReglementDashboardResponse | null;
  referentialsLoading: boolean;
  caisses: RefItem[];
  agences: RefItem[];
  centresPayeurs: RefItem[];
  savingFactureIds: string[];
  exporting: boolean;
  error: string | null;
  successMessage: string | null;
};

const initialState: ReglementState = {
  ...createPagedListState<ReglementInvoiceRow>({pageSize: 20}),
  activeCenterId: null,
  activeUserId: null,
  year: currentDate.getFullYear(),
  month: currentDate.getMonth() + 1,
  caisseId: null,
  agenceId: null,
  centrePayeurId: null,
  dashboardLoading: false,
  dashboard: null,
  referentialsLoading: false,
  caisses: [],
  agences: [],
  centresPayeurs: [],
  savingFactureIds: [],
  exporting: false,
  error: null,
  successMessage: null,
};

export const ReglementStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withDevtools('ReglementStore'),
  withComputed((store) => ({
    hasFilters: computed(() => !!store.caisseId() || !!store.agenceId() || !!store.centrePayeurId() || store.month() !== null),
    dashboardCards: computed(() => {
      const dashboard = store.dashboard();
      if (!dashboard) {
        return {
          totalFactures: 0,
          reglees: 0,
          nonReglees: 0,
          partiellementReglees: 0,
          tropPercus: 0,
          totalFacture: 0,
          totalRegle: 0,
          totalReste: 0,
          totalTropPercu: 0,
          tauxEncaissement: 0,
        };
      }
      const totalFacture = Number(dashboard.totalFacture ?? 0);
      const totalRegle = Number(dashboard.totalRegle ?? 0);
      return {
        totalFactures: dashboard.totalFactures,
        reglees: dashboard.reglees,
        nonReglees: dashboard.nonReglees,
        partiellementReglees: dashboard.partiellementReglees,
        tropPercus: dashboard.tropPercus,
        totalFacture,
        totalRegle,
        totalReste: Number(dashboard.totalReste ?? 0),
        totalTropPercu: Number(dashboard.totalTropPercu ?? 0),
        tauxEncaissement: totalFacture > 0 ? totalRegle / totalFacture : 0,
      };
    }),
  })),
  withMethods((store, api = inject(BackendApiService), referentials = inject(ReferentialApiService)) => ({
    setPagination(pageIndex: number, pageSize: number): void {
      patchState(store, {pageIndex, pageSize});
    },
    setYear(year: number): void {
      if (!Number.isFinite(year) || year < 2000 || year > 3000) return;
      patchState(store, {year, pageIndex: 0});
    },
    setMonth(month: number | null): void {
      if (month !== null && (!Number.isFinite(month) || month < 1 || month > 12)) return;
      patchState(store, {month, pageIndex: 0});
    },
    setCaisseId(caisseId: string | null): void {
      patchState(store, {caisseId: caisseId || null, pageIndex: 0});
    },
    setAgenceId(agenceId: string | null): void {
      patchState(store, {agenceId: agenceId || null, pageIndex: 0});
    },
    setCentrePayeurId(centrePayeurId: string | null): void {
      patchState(store, {centrePayeurId: centrePayeurId || null, pageIndex: 0});
    },
    clearFilters(): void {
      patchState(store, {
        month: currentDate.getMonth() + 1,
        caisseId: null,
        agenceId: null,
        centrePayeurId: null,
        pageIndex: 0,
      });
    },
    clearMessages(): void {
      patchState(store, {error: null, successMessage: null});
    },

    loadReferentials: rxMethod<{ centerId: string }>(
      pipe(
        tap(() => patchState(store, {referentialsLoading: true})),
        switchMap(({centerId}) =>
          forkJoin({
            caisses: referentials.getCaisses(centerId),
            agences: referentials.getAgences(centerId),
            centresPayeurs: referentials.getCentresPayeurs(centerId),
          }).pipe(
            tap(({caisses, agences, centresPayeurs}) => patchState(store, {
              referentialsLoading: false,
              caisses,
              agences,
              centresPayeurs,
            })),
            catchError(() => {
              patchState(store, {referentialsLoading: false, caisses: [], agences: [], centresPayeurs: []});
              return EMPTY;
            })
          )
        )
      )
    ),

    loadPage: rxMethod<{ centerId: string; userId: string; page: number; size: number }>(
      pipe(
        tap(({centerId, userId}) => patchState(store, {
          loading: true,
          error: null,
          successMessage: null,
          activeCenterId: centerId,
          activeUserId: userId,
        })),
        switchMap(({centerId, page, size}) =>
          api.listReglements(centerId, {
            year: store.year(),
            month: store.month(),
            caisseId: store.caisseId(),
            agenceId: store.agenceId(),
            centrePayeurId: store.centrePayeurId(),
            page,
            size,
          }).pipe(
            tap((response) => patchState(store, {
              rows: response.items ?? [],
              total: response.total ?? 0,
              pageIndex: response.page ?? page,
              pageSize: response.size ?? size,
              loading: false,
            })),
            catchError((err: unknown) => {
              patchState(store, {
                rows: [],
                total: 0,
                loading: false,
                error: errorMessage(err),
              });
              return of(null);
            })
          )
        )
      )
    ),

    loadDashboard: rxMethod<{ centerId: string }>(
      pipe(
        tap(() => patchState(store, {dashboardLoading: true, error: null})),
        switchMap(({centerId}) =>
          api.getReglementDashboard(centerId, {
            year: store.year(),
            month: store.month(),
            caisseId: store.caisseId(),
            agenceId: store.agenceId(),
            centrePayeurId: store.centrePayeurId(),
          }).pipe(
            tap((dashboard) => patchState(store, {dashboard, dashboardLoading: false})),
            catchError((err: unknown) => {
              patchState(store, {dashboardLoading: false, dashboard: null, error: errorMessage(err)});
              return of(null);
            })
          )
        )
      )
    ),

    savePayment: rxMethod<{ centerId: string; factureId: string; montant: number; userId: string }>(pipe(
        tap(({factureId}) => patchState(store, {
          savingFactureIds: [...store.savingFactureIds(), factureId],
          error: null,
          successMessage: null,
        })),
        switchMap(({centerId, factureId, montant, userId}) =>
          api.registerFacturePayment(factureId, {
            centerId,
            montant,
            userId,
          }).pipe(
            switchMap((updatedRow) =>
              api.getReglementDashboard(centerId, {
                year: store.year(),
                month: store.month(),
                caisseId: store.caisseId(),
                agenceId: store.agenceId(),
                centrePayeurId: store.centrePayeurId(),
              }).pipe(
                tap((dashboard) => {
                  patchState(store, {
                    rows: store.rows().map((row) => row.factureId === factureId ? updatedRow : row),
                    dashboard,
                    savingFactureIds: store.savingFactureIds().filter((id) => id !== factureId),
                    successMessage: 'REGLEMENT_MODULE.SUCCESS.PAYMENT_SAVED',
                  });
                }),
                catchError(() => {
                  patchState(store, {
                    rows: store.rows().map((row) => row.factureId === factureId ? updatedRow : row),
                    savingFactureIds: store.savingFactureIds().filter((id) => id !== factureId),
                    successMessage: 'REGLEMENT_MODULE.SUCCESS.PAYMENT_SAVED',
                  });
                  return of(null);
                })
              )
            ),
            catchError((err: unknown) => {
              patchState(store, {
                savingFactureIds: store.savingFactureIds().filter((id) => id !== factureId),
                error: errorMessage(err),
              });
              return EMPTY;
            })
          )
        )
      )
    ),

    exportReglements: rxMethod<{ centerId: string; format: 'excel' | 'csv' }>(
      pipe(
        tap(() => patchState(store, {exporting: true, error: null})),
        switchMap(({centerId, format}) => {
          const query: ReglementListQuery = {
            year: store.year(),
            month: store.month(),
            caisseId: store.caisseId(),
            agenceId: store.agenceId(),
            centrePayeurId: store.centrePayeurId(),
            page: 0,
            size: 10000,
          };
          return api.exportReglements(centerId, query, format).pipe(
            tap((blob) => {
              const url = URL.createObjectURL(blob);
              const a = document.createElement('a');
              a.href = url;
              a.download = `reglements-${store.year()}${store.month() ? '-' + String(store.month()).padStart(2, '0') : ''}.${format === 'excel' ? 'xlsx' : 'csv'}`;
              a.click();
              URL.revokeObjectURL(url);
              patchState(store, {exporting: false});
            }),
            catchError((err: unknown) => {
              patchState(store, {exporting: false, error: errorMessage(err)});
              return EMPTY;
            })
          );
        })
      )
    ),
  })),
  withHooks((store, appShell = inject(AppShellStore), auth = inject(AuthStore)) => ({
    onInit() {
      effect(() => {
        const centerId = appShell.currentCenterId();
        const userId = auth.username() ?? 'system';
        if (!centerId) {
          patchState(store, {
            rows: [],
            total: 0,
            loading: false,
            dashboard: null,
            dashboardLoading: false,
            activeCenterId: null,
            activeUserId: userId,
          });
          return;
        }

        store.year();
        store.month();
        store.caisseId();
        store.agenceId();
        store.centrePayeurId();
        store.pageIndex();
        store.pageSize();

        store.loadPage({centerId, userId, page: store.pageIndex(), size: store.pageSize()});
        store.loadDashboard({centerId});
      });

      effect(() => {
        const centerId = appShell.currentCenterId();
        if (!centerId) {
          patchState(store, {caisses: [], agences: [], centresPayeurs: [], referentialsLoading: false});
          return;
        }
        store.loadReferentials({centerId});
      });
    },
  }))
);

function errorMessage(err: unknown): string {
  if (err && typeof err === 'object') {
    const value = err as Record<string, unknown>;
    if (typeof value['message'] === 'string') {
      return value['message'];
    }
    if (value['error'] && typeof value['error'] === 'object') {
      const nested = value['error'] as Record<string, unknown>;
      if (typeof nested['message'] === 'string') {
        return nested['message'];
      }
    }
  }
  return 'REGLEMENT_MODULE.ERROR.GENERIC';
}







