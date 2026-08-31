import {computed, inject} from '@angular/core';
import {patchState, signalStore, withComputed, withHooks, withMethods, withState} from '@ngrx/signals';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {rxMethod} from '@ngrx/signals/rxjs-interop';
import {catchError, EMPTY, forkJoin, map, pipe, switchMap, tap} from 'rxjs';
import {
  BackendApiService,
  FacturationDashboardResponse,
  FacturationPreviewResponse,
  FacturationSettings,
  ReferentialForfait,
} from '../../../core/api/backend-api.service';
import {AppShellStore} from '../../../core/state/app-shell.store';

export type FacturationPeriodMode = 'month' | 'custom';

export type FacturationRevenuePoint = {
  month: string;
  revenueHt: number;
  revenueTtc: number;
  billedSeances: number;
  createdInvoices: number;
};

type FacturationState = {
  activeCenterId: string | null;
  periodMode: FacturationPeriodMode;
  month: string;
  startDate: string;
  endDate: string;
  previewLoading: boolean;
  validating: boolean;
  dashboardLoading: boolean;
  revenueTrendLoading: boolean;
  settingsLoading: boolean;
  savingSettings: boolean;
  preview: FacturationPreviewResponse | null;
  dashboard: FacturationDashboardResponse | null;
  revenueTrend: FacturationRevenuePoint[];
  settings: FacturationSettings | null;
  settingsDraft: FacturationSettings | null;
  forfaits: ReferentialForfait[];
  seanceActionLoadingId: string | null;
  error: string | null;
  successMessage: string | null;
};

const initialState: FacturationState = {
  activeCenterId: null,
  periodMode: 'month',
  month: currentMonthIso(),
  startDate: todayIsoDate(),
  endDate: todayIsoDate(),
  previewLoading: false,
  validating: false,
  dashboardLoading: false,
  revenueTrendLoading: false,
  settingsLoading: false,
  savingSettings: false,
  preview: null,
  dashboard: null,
  revenueTrend: [],
  settings: null,
  settingsDraft: null,
  forfaits: [],
  seanceActionLoadingId: null,
  error: null,
  successMessage: null,
};

export const FacturationStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withDevtools('FacturationStore'),
  withComputed((store) => ({
    periodLabel: computed(() => {
      if (store.periodMode() === 'month') {
        return store.month();
      }
      return `${store.startDate()}..${store.endDate()}`;
    }),
    canCalculate: computed(() => {
      if (!store.activeCenterId()) {
        return false;
      }
      if (store.periodMode() === 'month') {
        return /^\d{4}-\d{2}$/.test(store.month());
      }
      const from = store.startDate();
      const to = store.endDate();
      return /^\d{4}-\d{2}-\d{2}$/.test(from) && /^\d{4}-\d{2}-\d{2}$/.test(to) && from <= to;
    }),
    kpis: computed(() => ({
      totalFactures: store.preview()?.totalFactures ?? 0,
      totalHt: store.preview()?.totalHt ?? 0,
      totalTva: store.preview()?.totalTva ?? 0,
      totalTtc: store.preview()?.totalTtc ?? 0,
    })),
  })),
  withMethods((store, api = inject(BackendApiService)) => ({
    setActiveCenterId(activeCenterId: string | null): void {
      patchState(store, {activeCenterId});
    },
    setPeriodMode(periodMode: FacturationPeriodMode): void {
      patchState(store, {periodMode, preview: null});
    },
    setMonth(month: string): void {
      patchState(store, {month, preview: null});
    },
    setStartDate(startDate: string): void {
      patchState(store, {startDate, preview: null});
    },
    setEndDate(endDate: string): void {
      patchState(store, {endDate, preview: null});
    },
    resetPreviewActions(): void {
      patchState(store, {seanceActionLoadingId: null});
    },
    clearError(): void {
      patchState(store, {error: null});
    },
    clearSuccess(): void {
      patchState(store, {successMessage: null});
    },
    patchSettingsDraft(fields: Partial<FacturationSettings>): void {
      patchState(store, {
        settingsDraft: {
          ...defaultSettings(),
          ...(store.settingsDraft() ?? store.settings ?? defaultSettings()),
          ...fields,
        },
      });
    },

    loadSettings: rxMethod<{centerId: string}>(
      pipe(
        tap(() => patchState(store, {settingsLoading: true, error: null})),
        switchMap(({centerId}) =>
          api.getFacturationSettings(centerId).pipe(
            tap((settings) => patchState(store, {
              settings,
              settingsDraft: settings,
              settingsLoading: false,
            })),
            catchError((err: unknown) => {
              patchState(store, {
                settingsLoading: false,
                error: errorMessage(err),
                settings: defaultSettings(),
                settingsDraft: defaultSettings(),
              });
              return EMPTY;
            })
          )
        )
      )
    ),

    loadForfaits: rxMethod<{ centerId: string }>(
      pipe(
        switchMap(({centerId}) =>
          api.listForfaitsReferential(centerId).pipe(
            tap((forfaits) => patchState(store, {forfaits})),
            catchError(() => {
              patchState(store, {forfaits: []});
              return EMPTY;
            })
          )
        )
      )
    ),

    loadDashboard: rxMethod<{centerId: string; month: string}>(
      pipe(
        tap(() => patchState(store, {dashboardLoading: true, error: null})),
        switchMap(({centerId, month}) =>
          api.getFacturationDashboard(centerId, month).pipe(
            tap((dashboard) => patchState(store, {dashboard, dashboardLoading: false})),
            catchError((err: unknown) => {
              patchState(store, {dashboardLoading: false, dashboard: null, error: errorMessage(err)});
              return EMPTY;
            })
          )
        )
      )
    ),

    loadRevenueTrend: rxMethod<{centerId: string; endingMonth: string; months: number}>(
      pipe(
        tap(() => patchState(store, {revenueTrendLoading: true})),
        switchMap(({centerId, endingMonth, months}) => {
          const monthSeries = buildMonthSeries(endingMonth, months);
          return forkJoin(
            monthSeries.map((month) =>
              api.getFacturationDashboard(centerId, month).pipe(
                map((dashboard) => ({
                  month,
                  revenueHt: dashboard.revenueHt,
                  revenueTtc: dashboard.revenueTtc,
                  billedSeances: dashboard.billedSeances,
                  createdInvoices: dashboard.createdInvoices,
                }))
              )
            )
          ).pipe(
            tap((revenueTrend) => patchState(store, {revenueTrend, revenueTrendLoading: false})),
            catchError(() => {
              patchState(store, {revenueTrendLoading: false, revenueTrend: []});
              return EMPTY;
            })
          );
        })
      )
    ),

    calculatePreview: rxMethod<{centerId: string}>(
      pipe(
        tap(() => patchState(store, {previewLoading: true, error: null, successMessage: null})),
        switchMap(({centerId}) =>
          api.previewFacturation({
            centerId,
            ...periodPayload(store.periodMode(), store.month(), store.startDate(), store.endDate()),
            regroupementMultiForfait: store.settingsDraft()?.regroupementMultiForfait ?? true,
          }).pipe(
            tap((preview) => patchState(store, {preview, previewLoading: false})),
            catchError((err: unknown) => {
              patchState(store, {previewLoading: false, preview: null, error: errorMessage(err)});
              return EMPTY;
            })
          )
        )
      )
    ),

    removeSeanceFromPreview: rxMethod<{ centerId: string; userId: string; seanceId: string }>(
      pipe(
        tap(({seanceId}) => patchState(store, {
          previewLoading: true,
          error: null,
          successMessage: null,
          seanceActionLoadingId: seanceId
        })),
        switchMap(({centerId, userId, seanceId}) =>
          api.excludeSeanceFromPreview(seanceId, {
            centerId,
            userId,
            ...periodPayload(store.periodMode(), store.month(), store.startDate(), store.endDate()),
            regroupementMultiForfait: store.settingsDraft()?.regroupementMultiForfait ?? true,
          }).pipe(
            tap((preview) => patchState(store, {
              preview,
              previewLoading: false,
              seanceActionLoadingId: null,
              successMessage: 'FACTURATION.SUCCESS.SEANCE_EXCLUDED',
            })),
            catchError((err: unknown) => {
              patchState(store, {previewLoading: false, seanceActionLoadingId: null, error: errorMessage(err)});
              return EMPTY;
            })
          )
        )
      )
    ),

    updateSeanceForfaitInPreview: rxMethod<{ centerId: string; userId: string; seanceId: string; forfaitId: string }>(
      pipe(
        tap(({seanceId}) => patchState(store, {
          previewLoading: true,
          error: null,
          successMessage: null,
          seanceActionLoadingId: seanceId
        })),
        switchMap(({centerId, userId, seanceId, forfaitId}) =>
          api.updatePreviewSeanceForfait(seanceId, {
            centerId,
            userId,
            forfaitId,
            ...periodPayload(store.periodMode(), store.month(), store.startDate(), store.endDate()),
            regroupementMultiForfait: store.settingsDraft()?.regroupementMultiForfait ?? true,
          }).pipe(
            tap((preview) => patchState(store, {
              preview,
              previewLoading: false,
              seanceActionLoadingId: null,
              successMessage: 'FACTURATION.SUCCESS.SEANCE_FORFAIT_UPDATED',
            })),
            catchError((err: unknown) => {
              patchState(store, {previewLoading: false, seanceActionLoadingId: null, error: errorMessage(err)});
              return EMPTY;
            })
          )
        )
      )
    ),

    validateFacturation: rxMethod<{centerId: string; userId: string}>(
      pipe(
        tap(() => patchState(store, {validating: true, error: null, successMessage: null})),
        switchMap(({centerId, userId}) => {
          const preview = store.preview();
          if (!preview) {
            patchState(store, {validating: false, error: 'FACTURATION.ERROR.NO_PREVIEW'});
            return EMPTY;
          }
          return api.validateFacturation({
            centerId,
            userId,
            ...periodPayload(store.periodMode(), store.month(), store.startDate(), store.endDate()),
            regroupementMultiForfait: store.settingsDraft()?.regroupementMultiForfait ?? true,
            previewGeneratedAt: preview.generatedAt,
          }).pipe(
            tap(() => patchState(store, {
              validating: false,
              preview: null,
              successMessage: 'FACTURATION.SUCCESS.VALIDATED',
            })),
            catchError((err: unknown) => {
              patchState(store, {validating: false, error: errorMessage(err)});
              return EMPTY;
            })
          );
        })
      )
    ),

    saveSettings: rxMethod<{centerId: string; userId: string}>(
      pipe(
        tap(() => patchState(store, {savingSettings: true, error: null, successMessage: null})),
        switchMap(({centerId, userId}) => {
          const draft = store.settingsDraft() ?? defaultSettings();
          return api.updateFacturationSettings({
            centerId,
            userId,
            codeFormat: draft.codeFormat,
            regroupementMultiForfait: draft.regroupementMultiForfait,
          }).pipe(
            tap((settings) => patchState(store, {
              savingSettings: false,
              settings,
              settingsDraft: settings,
              successMessage: 'FACTURATION.SUCCESS.SETTINGS_SAVED',
            })),
            catchError((err: unknown) => {
              patchState(store, {savingSettings: false, error: errorMessage(err)});
              return EMPTY;
            })
          );
        })
      )
    ),
  })),
  withHooks((store, appShell = inject(AppShellStore)) => ({
    onInit() {
      const centerId = appShell.currentCenterId();
      if (!centerId) {
        return;
      }
      patchState(store, {activeCenterId: centerId});
    },
  }))
);

function periodPayload(mode: FacturationPeriodMode, month: string, startDate: string, endDate: string): {
  month?: string;
  periodStart?: string;
  periodEnd?: string;
} {
  if (mode === 'month') {
    return {month};
  }
  return {periodStart: startDate, periodEnd: endDate};
}

function todayIsoDate(): string {
  return new Date().toISOString().slice(0, 10);
}

function currentMonthIso(): string {
  return todayIsoDate().slice(0, 7);
}

function defaultSettings(): FacturationSettings {
  return {
    codeFormat: 'FAC-{YEAR}-{SEQ}',
    regroupementMultiForfait: true,
    updatedAt: null,
  };
}

function buildMonthSeries(endingMonth: string, months: number): string[] {
  if (!/^\d{4}-\d{2}$/.test(endingMonth) || months <= 0) {
    return [currentMonthIso()];
  }
  const [year, month] = endingMonth.split('-').map((value) => Number(value));
  const cursor = new Date(Date.UTC(year, month - 1, 1));
  const result: string[] = [];
  for (let i = months - 1; i >= 0; i -= 1) {
    const d = new Date(cursor);
    d.setUTCMonth(d.getUTCMonth() - i);
    const y = d.getUTCFullYear();
    const m = `${d.getUTCMonth() + 1}`.padStart(2, '0');
    result.push(`${y}-${m}`);
  }
  return result;
}

function errorMessage(err: unknown): string {
  if (err && typeof err === 'object') {
    const e = err as Record<string, unknown>;
    if (typeof e['message'] === 'string') {
      return e['message'];
    }
    if (e['error'] && typeof e['error'] === 'object') {
      const inner = e['error'] as Record<string, unknown>;
      if (typeof inner['message'] === 'string') {
        return inner['message'];
      }
    }
  }
  return 'FACTURATION.ERROR.GENERIC';
}

