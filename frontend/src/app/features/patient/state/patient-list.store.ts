import {computed, effect, inject} from '@angular/core';
import {patchState, signalStore, withComputed, withHooks, withMethods, withState} from '@ngrx/signals';
import {rxMethod} from '@ngrx/signals/rxjs-interop';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {catchError, of, pipe, switchMap, tap} from 'rxjs';
import {BackendApiService, ListQuery, PatientSummary} from '../../../core/api/backend-api.service';
import {createPagedListState, PagedListState} from '../../../core/state/paged-list-state.util';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {AuthStore} from '../../../core/state/auth.store';

type PatientListState = PagedListState<any> & {
  printingList: boolean;
  exportingList: boolean;
  printingRowId: string | null;
  recentPatientId: string | null;
  summary: PatientSummary | null;
  summaryLoading: boolean;
  summaryError: string | null;
  activeCenterId: string | null;
  activeUserId: string | null;
  error: string | null;
};

const initialState: PatientListState = {
  ...createPagedListState<any>(),
  printingList: false,
  exportingList: false,
  printingRowId: null,
  recentPatientId: null,
  summary: null,
  summaryLoading: true,
  summaryError: null,
  activeCenterId: null,
  activeUserId: null,
  error: null
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
            filters: store.columnFilters()
          };
          return api.listPatients(centerId, userId, query).pipe(
            tap((res: any) => {
              const mapped = (res.items ?? []).map((p: any) => ({
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
                positionId: p.positionId ?? '',
                transporteurAllerId: p.transporteurAllerId ?? '',
                transporteurRetourId: p.transporteurRetourId ?? '',
                joursDialyse: {
                  dimanche:
                    p.jourDimanche ?? p.jour_dimanche ?? (p.joursDialyse ?? p.jours_dialyse)?.dimanche ?? false,
                  lundi:
                    p.jourLundi ?? p.jour_lundi ?? (p.joursDialyse ?? p.jours_dialyse)?.lundi ?? false,
                  mardi:
                    p.jourMardi ?? p.jour_mardi ?? (p.joursDialyse ?? p.jours_dialyse)?.mardi ?? false,
                  mercredi:
                    p.jourMercredi ??
                    p.jour_mercredi ??
                    (p.joursDialyse ?? p.jours_dialyse)?.mercredi ??
                    false,
                  jeudi:
                    p.jourJeudi ?? p.jour_jeudi ?? (p.joursDialyse ?? p.jours_dialyse)?.jeudi ?? false,
                  vendredi:
                    p.jourVendredi ??
                    p.jour_vendredi ??
                    (p.joursDialyse ?? p.jours_dialyse)?.vendredi ??
                    false,
                  samedi:
                    p.jourSamedi ?? p.jour_samedi ?? (p.joursDialyse ?? p.jours_dialyse)?.samedi ?? false
                },
                pecStatus: p.pecStatus ?? '',
                pecForfaitId: p.pecForfaitId ?? ''
              }));
              patchState(store, {
                rows: mapped,
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

    // ✅ Imprimer liste complète
    printList: rxMethod<{ centerId: string }>(
      pipe(
        tap(() => {
          patchState(store, {printingList: true, error: null});
        }),
        switchMap(({centerId}) =>
          api.printDocument(centerId, 'LISTE_PATIENTS', {}).pipe(
            tap((blob: Blob) => {
              const url = URL.createObjectURL(blob);
              window.open(url, '_blank');
              patchState(store, {printingList: false});
            }),
            catchError((err: any) => {
              patchState(store, {
                printingList: false,
                error: 'Erreur impression: ' + (err?.error?.message || err?.statusText)
              });
              return of(null);
            })
          )
        )
      )
    ),

    loadSummary: rxMethod<{ centerId: string }>(
      pipe(
        tap(() => patchState(store, {summaryLoading: true, summaryError: null})),
        switchMap(({centerId}) =>
          api.getPatientSummary(centerId).pipe(
            tap((summary) => patchState(store, {
              summary,
              summaryLoading: false,
              summaryError: null
            })),
            catchError((err: any) => {
              patchState(store, {
                summary: null,
                summaryLoading: false,
                summaryError: err?.error?.message || err?.statusText || 'Erreur chargement synthèse patients'
              });
              return of(null);
            })
          )
        )
      )
    ),

    // ✅ Exporter en Excel
    exportListExcel: rxMethod<{ centerId: string }>(
      pipe(
        tap(() => {
          patchState(store, {exportingList: true, error: null});
        }),
        switchMap(({centerId}) =>
          api.printDocument(centerId, 'LISTE_PATIENTS', {}, 'EXCEL').pipe(
            tap((blob: Blob) => {
              const a = document.createElement('a');
              a.href = URL.createObjectURL(blob);
              a.download = 'liste-patients.xls';
              a.click();
              patchState(store, {exportingList: false});
            }),
            catchError((err: any) => {
              patchState(store, {
                exportingList: false,
                error: 'Erreur export: ' + (err?.error?.message || err?.statusText)
              });
              return of(null);
            })
          )
        )
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

    setPagination(pageIndex: number, pageSize: number): void {
      patchState(store, {pageIndex, pageSize});
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
        const centerId = appShell.currentCenterId();
        const userId = auth.username() ?? 'demo';
        const page = store.pageIndex();
        const size = store.pageSize();

        // Dependances reactives du chargement automatique
        store.columnFilters();

        if (!centerId) {
          patchState(store, {
            rows: [],
            total: 0,
            loading: false,
            activeCenterId: null,
            activeUserId: userId,
            summary: null,
            summaryLoading: false,
            summaryError: null
          });
          return;
        }

        store.loadPage({centerId, userId, page, size});
      });

      effect(() => {
        const centerId = appShell.currentCenterId();
        if (!centerId) {
          patchState(store, {
            summary: null,
            summaryLoading: false,
            summaryError: null
          });
          return;
        }

        store.loadSummary({centerId});
      });
    }
  }))
);









