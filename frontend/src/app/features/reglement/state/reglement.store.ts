import {computed, effect, inject} from '@angular/core';
import {patchState, signalStore, withComputed, withHooks, withMethods, withState} from '@ngrx/signals';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {rxMethod} from '@ngrx/signals/rxjs-interop';
import {catchError, EMPTY, forkJoin, map, of, pipe, switchMap, tap} from 'rxjs';
import {
  BackendApiService,
  ReglementEtat,
  ReglementInvoiceRow,
  ReglementListQuery,
  ReglementSoldeType,
} from '../../../core/api/backend-api.service';
import {createPagedListState, PagedListState} from '../../../core/state/paged-list-state.util';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {AuthStore} from '../../../core/state/auth.store';
import {ReferentialApiService, RefItem} from '../../../core/api/referential-api.service';
import {buildPaymentPayload, reglementErrorMessage} from './reglement-payment.util';

export type ReglementPreviewRow = ReglementInvoiceRow & {
  hasDraft: boolean;
  draftAmount: number;
};

const currentDate = new Date();

export type ReglementState = PagedListState<ReglementInvoiceRow> & {
  activeCenterId: string | null;
  activeUserId: string | null;
  year: number;
  month: number | null;
  caisseId: string | null;
  agenceId: string | null;
  centrePayeurId: string | null;
  referentialsLoading: boolean;
  caisses: RefItem[];
  agences: RefItem[];
  centresPayeurs: RefItem[];
  savingFactureIds: string[];
  batchSaving: boolean;
  exporting: boolean;
  error: string | null;
  successMessage: string | null;
  paymentDrafts: Record<string, string>;
  saveCycleKey: number;
  /** Mode (facultatif) et référence (facultative) appliqués aux règlements du lot saisi. */
  modeReglement: string | null;
  referencePaiement: string;
  /** Clé d'idempotence par facture, réutilisée tant que l'envoi n'a pas réussi. */
  idempotenceKeys: Record<string, string>;
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
  referentialsLoading: false,
  caisses: [],
  agences: [],
  centresPayeurs: [],
  savingFactureIds: [],
  batchSaving: false,
  exporting: false,
  error: null,
  successMessage: null,
  paymentDrafts: {},
  saveCycleKey: 0,
  modeReglement: null,
  referencePaiement: '',
  idempotenceKeys: {},
};

export const ReglementStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withDevtools('ReglementStore'),
  withComputed((store) => ({
    hasFilters: computed(() => !!store.caisseId() || !!store.agenceId() || !!store.centrePayeurId() || store.month() !== null),
    previewRows: computed((): ReglementPreviewRow[] => {
      const rows = store.rows();
      const drafts = store.paymentDrafts();
      return rows.map((row) => {
        const raw = drafts[row.factureId] ?? '';
        const draftAmount = raw ? Number(raw) : 0;
        const hasDraft = Number.isFinite(draftAmount) && draftAmount !== 0;
        if (!hasDraft) {
          return {...row, hasDraft: false, draftAmount: 0};
        }
        const newMontantRegle = row.montantRegle + draftAmount;
        const diff = row.montantFacture - newMontantRegle;
        const newReste = diff > 0 ? diff : 0;
        const newTropPercu = diff < 0 ? Math.abs(diff) : 0;
        const newEtat: ReglementEtat = diff > 0 ? 'PARTIELLEMENT_REGLEE' : 'REGLEE';
        const newSoldeType: ReglementSoldeType = diff < 0 ? 'TROP_PERCU' : diff === 0 ? 'REGLE' : 'RESTE';
        return {
          ...row,
          montantRegle: newMontantRegle,
          reste: newReste,
          tropPercu: newTropPercu,
          etat: newEtat,
          soldeType: newSoldeType,
          hasDraft: true,
          draftAmount,
        };
      });
    }),
    hasDrafts: computed(() => {
      const drafts = store.paymentDrafts();
      return Object.values(drafts).some((v) => {
        const n = Number(v);
        return Number.isFinite(n) && n !== 0;
      });
    }),
    draftCount: computed(() => {
      const drafts = store.paymentDrafts();
      return Object.values(drafts).filter((v) => {
        const n = Number(v);
        return Number.isFinite(n) && n !== 0;
      }).length;
    }),
    visibleRows: computed((): ReglementPreviewRow[] => {
      const rows = store.rows();
      const drafts = store.paymentDrafts();
      const all = rows.map((row): ReglementPreviewRow => {
        const raw = drafts[row.factureId] ?? '';
        const draftAmount = raw ? Number(raw) : 0;
        const hasDraft = Number.isFinite(draftAmount) && draftAmount !== 0;
        if (!hasDraft) {
          return {...row, hasDraft: false, draftAmount: 0};
        }
        const newMontantRegle = row.montantRegle + draftAmount;
        const diff = row.montantFacture - newMontantRegle;
        const newReste = diff > 0 ? diff : 0;
        const newTropPercu = diff < 0 ? Math.abs(diff) : 0;
        const newEtat: ReglementEtat = diff > 0 ? 'PARTIELLEMENT_REGLEE' : 'REGLEE';
        const newSoldeType: ReglementSoldeType = diff < 0 ? 'TROP_PERCU' : diff === 0 ? 'REGLE' : 'RESTE';
        return {
          ...row,
          montantRegle: newMontantRegle,
          reste: newReste,
          tropPercu: newTropPercu,
          etat: newEtat,
          soldeType: newSoldeType,
          hasDraft: true,
          draftAmount,
        };
      });
      return all;
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
    setPaymentDraft(factureId: string, value: string): void {
      // Nouvelle saisie : la clé d'idempotence de la facture est régénérée au prochain envoi.
      const {[factureId]: _old, ...keys} = store.idempotenceKeys();
      patchState(store, {paymentDrafts: {...store.paymentDrafts(), [factureId]: value}, idempotenceKeys: keys});
    },
    setModeReglement(mode: string | null): void {
      patchState(store, {modeReglement: mode || null});
    },
    setReferencePaiement(reference: string): void {
      patchState(store, {referencePaiement: reference});
    },
    clearAllDrafts(): void {
      patchState(store, {paymentDrafts: {}});
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

    savePayment: rxMethod<{ centerId: string; factureId: string; montant: number; userId?: string }>(pipe(
        tap(({factureId}) => patchState(store, {
          savingFactureIds: [...store.savingFactureIds(), factureId],
          error: null,
          successMessage: null,
        })),
        switchMap(({centerId, factureId, montant}) => {
          const prepared = buildPaymentPayload(centerId, factureId, montant, store.modeReglement(),
            store.referencePaiement(), store.idempotenceKeys());
          patchState(store, {idempotenceKeys: prepared.keys});
          return api.registerFacturePayment(factureId, prepared.payload).pipe(
            tap((updatedRow) => {
              const {[factureId]: _done, ...keys} = store.idempotenceKeys();
              patchState(store, {
                idempotenceKeys: keys,
                rows: store.rows().map((row) => row.factureId === factureId ? updatedRow : row),
                savingFactureIds: store.savingFactureIds().filter((id) => id !== factureId),
                successMessage: 'REGLEMENT_MODULE.SUCCESS.PAYMENT_SAVED',
              });
            }),
            catchError((err: unknown) => {
              patchState(store, {
                savingFactureIds: store.savingFactureIds().filter((id) => id !== factureId),
                error: errorMessage(err),
              });
              return EMPTY;
            })
          );
        })
      )
    ),

    batchSavePayments: rxMethod<{ centerId: string; userId?: string }>(
      pipe(
        tap(() => patchState(store, {batchSaving: true, error: null, successMessage: null})),
        switchMap(({centerId}) => {
          const drafts = store.paymentDrafts();
          const entries = Object.entries(drafts)
            .filter(([, v]) => {
              const n = Number(v);
              return Number.isFinite(n) && n !== 0;
            })
            .map(([factureId, v]) => ({factureId, montant: Number(v)}));

          if (entries.length === 0) {
            patchState(store, {batchSaving: false});
            return EMPTY;
          }

          // Clés d'idempotence : créées ici, conservées en cas d'échec (renvoi sans doublon), retirées au succès.
          let keys = store.idempotenceKeys();
          const prepared = entries.map(({factureId, montant}) => {
            const built = buildPaymentPayload(centerId, factureId, montant, store.modeReglement(),
              store.referencePaiement(), keys);
            keys = built.keys;
            return {factureId, payload: built.payload};
          });
          patchState(store, {idempotenceKeys: keys});

          const requests$ = prepared.map(({factureId, payload}) =>
            api.registerFacturePayment(factureId, payload).pipe(
              map((row) => ({ok: true, factureId, row, message: null} as const)),
              catchError((err: unknown) => of({
                ok: false,
                factureId,
                row: null,
                message: reglementErrorMessage(err)
              } as const))
            )
          );

          return forkJoin(requests$).pipe(
            tap((results) => {
              const successCount = results.filter((r) => r.ok).length;
              const failCount = results.filter((r) => !r.ok).length;

              let rows = store.rows();
              for (const result of results) {
                if (result.ok && result.row) {
                  rows = rows.map((r) => r.factureId === result.factureId ? result.row! : r);
                }
              }

              const newDrafts: Record<string, string> = {};
              for (const [fid, v] of Object.entries(store.paymentDrafts())) {
                const succeeded = results.find((r) => r.factureId === fid && r.ok);
                if (!succeeded) {
                  newDrafts[fid] = v;
                }
              }

              const remainingKeys = {...store.idempotenceKeys()};
              for (const result of results) {
                if (result.ok) delete remainingKeys[result.factureId];
              }
              // Tout a échoué : le motif du backend (403, 422 remboursement, 400…) ; échec partiel : message de lot.
              const firstFailure = results.find((r) => !r.ok)?.message ?? null;

              patchState(store, {
                rows,
                paymentDrafts: newDrafts,
                idempotenceKeys: remainingKeys,
                batchSaving: false,
                saveCycleKey: store.saveCycleKey() + 1,
                ...(failCount === 0 ? {modeReglement: null, referencePaiement: ''} : {}),
                successMessage: failCount === 0 ? 'REGLEMENT_MODULE.SUCCESS.BATCH_SAVED' : null,
                error: failCount === 0 ? null : successCount === 0 ? firstFailure : 'REGLEMENT_MODULE.ERROR.BATCH_PARTIAL',
              });
            }),
            catchError((err: unknown) => {
              patchState(store, {batchSaving: false, error: errorMessage(err)});
              return EMPTY;
            })
          );
        })
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

const errorMessage = reglementErrorMessage;




















