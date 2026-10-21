import {computed, inject} from '@angular/core';
import {patchState, signalStore, withComputed, withHooks, withMethods, withState} from '@ngrx/signals';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {rxMethod} from '@ngrx/signals/rxjs-interop';
import {catchError, concatMap, EMPTY, forkJoin, map, of, pipe, switchMap, tap} from 'rxjs';
import {
  BackendApiService,
  FacturationDashboardResponse,
  FacturationPreviewResponse,
  FacturationSettings,
  MoisDeFacturationReponse,
  ReferentialForfait,
} from '../../../core/api/backend-api.service';
import {douzeMois, indexerLesMois, moisCouverts} from './facturation-periodes';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {MotifAbsence} from '../../../core/api/absence-patient-api.service';
import {facturationErrorState} from './facturation-errors';
import {SeanceExclue} from './facturation-exclusion';

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
  excludedSeances: SeanceExclue[];
  /** Mois de facturation connus (clé AAAA-MM), cumulés sur les années déjà chargées. */
  periodes: Record<string, MoisDeFacturationReponse>;
  anneesChargees: number[];
  anneePeriodes: number;
  periodesLoading: boolean;
  /** Mois dont la clôture ou la réouverture est en cours. */
  periodeActionMois: string | null;
  /** Avertissement chiffré après une clôture : séances éligibles encore non facturées. */
  clotureAvertissement: { mois: string; seancesRestantes: number } | null;
  error: string | null;
  errorParams: Record<string, unknown> | null;
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
  excludedSeances: [],
  periodes: {},
  anneesChargees: [],
  anneePeriodes: new Date().getUTCFullYear(),
  periodesLoading: false,
  periodeActionMois: null,
  clotureAvertissement: null,
  error: null,
  errorParams: null,
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
    /** Mois (AAAA-MM) visés par la période choisie : le mois, ou tout mois couvert par [début, fin]. */
    moisVises: computed(() => store.periodMode() === 'month'
      ? (/^\d{4}-\d{2}$/.test(store.month()) ? [store.month()] : [])
      : moisCouverts(store.startDate(), store.endDate())),
    /** Les douze mois de l'année affichée. */
    moisDeLAnnee: computed(() => douzeMois(store.anneePeriodes(), store.periodes())),
  })),
  withComputed((store) => ({
    /** Mois clos parmi ceux visés par la période : la validation est alors refusée par le backend. */
    moisClosVises: computed(() => store.moisVises().filter((m) => store.periodes()[m]?.statut === 'CLOTURE')),
  })),
  withMethods((store, api = inject(BackendApiService)) => ({
    setActiveCenterId(activeCenterId: string | null): void {
      patchState(store, {activeCenterId});
    },
    setPeriodMode(periodMode: FacturationPeriodMode): void {
      patchState(store, {periodMode, preview: null, excludedSeances: []});
    },
    setMonth(month: string): void {
      patchState(store, {month, preview: null, excludedSeances: []});
    },
    setStartDate(startDate: string): void {
      patchState(store, {startDate, preview: null, excludedSeances: []});
    },
    setEndDate(endDate: string): void {
      patchState(store, {endDate, preview: null, excludedSeances: []});
    },
    resetPreviewActions(): void {
      patchState(store, {seanceActionLoadingId: null});
    },
    clearError(): void {
      patchState(store, {error: null, errorParams: null});
    },
    clearSuccess(): void {
      patchState(store, {successMessage: null});
    },
    setAnneePeriodes(anneePeriodes: number): void {
      patchState(store, {anneePeriodes});
    },
    /** Vrai si le mois AAAA-MM est clos (faux tant que son année n'est pas chargée : le backend reste juge). */
    moisEstClos(mois: string): boolean {
      return store.periodes()[mois]?.statut === 'CLOTURE';
    },

    /** Charge les mois d'une ou plusieurs années (cumul dans `periodes`). */
    loadPeriodes: rxMethod<{ centerId: string; annee: number }>(
      pipe(
        tap(() => patchState(store, {periodesLoading: true})),
        concatMap(({centerId, annee}) =>
          api.getFacturationPeriodes(centerId, annee).pipe(
            tap((reponse) => patchState(store, {
              periodes: {...store.periodes(), ...indexerLesMois(reponse.periodes)},
              anneesChargees: [...new Set([...store.anneesChargees(), annee])],
              periodesLoading: false,
            })),
            catchError((err: unknown) => {
              patchState(store, {periodesLoading: false, ...facturationErrorState(err)});
              return EMPTY;
            })
          )
        )
      )
    ),

    /** Clôture un mois ; garde l'avertissement chiffré de la réponse puis recharge l'année du mois. */
    cloturerMois: rxMethod<{ centerId: string; mois: string }>(
      pipe(
        tap(({mois}) => patchState(store, {
          periodeActionMois: mois, error: null, errorParams: null, successMessage: null, clotureAvertissement: null,
        })),
        switchMap(({centerId, mois}) =>
          api.cloturerMoisFacturation(mois, centerId).pipe(
            switchMap((reponse) => api.getFacturationPeriodes(centerId, Number(mois.slice(0, 4))).pipe(
              map((periodes) => ({reponse, periodes})),
              catchError(() => of({reponse, periodes: null}))
            )),
            tap(({reponse, periodes}) => patchState(store, {
              periodeActionMois: null,
              successMessage: 'FACTURATION.SUCCESS.MOIS_CLOTURE',
              clotureAvertissement: {mois, seancesRestantes: reponse.seancesEligiblesRestantes},
              periodes: {
                ...store.periodes(),
                ...(periodes ? indexerLesMois(periodes.periodes) : {
                  [mois]: {
                    ...(store.periodes()[mois] ?? {rouvertPar: null, rouvertLe: null, motifDeReouverture: null}),
                    mois, statut: 'CLOTURE', cloturePar: reponse.cloturePar, clotureLe: reponse.clotureLe,
                  } as MoisDeFacturationReponse,
                }),
              },
            })),
            catchError((err: unknown) => {
              patchState(store, {periodeActionMois: null, ...facturationErrorState(err)});
              return EMPTY;
            })
          )
        )
      )
    ),

    /** Rouvre un mois clos avec un motif (déjà nettoyé par l'appelant), puis recharge l'année du mois. */
    rouvrirMois: rxMethod<{ centerId: string; mois: string; motif: string }>(
      pipe(
        tap(({mois}) => patchState(store, {
          periodeActionMois: mois, error: null, errorParams: null, successMessage: null, clotureAvertissement: null,
        })),
        switchMap(({centerId, mois, motif}) =>
          api.rouvrirMoisFacturation(mois, {centerId, motif}).pipe(
            switchMap(() => api.getFacturationPeriodes(centerId, Number(mois.slice(0, 4))).pipe(
              map((periodes) => periodes.periodes),
              catchError(() => of(null))
            )),
            tap((periodes) => patchState(store, {
              periodeActionMois: null,
              successMessage: 'FACTURATION.SUCCESS.MOIS_ROUVERT',
              periodes: {
                ...store.periodes(),
                ...(periodes ? indexerLesMois(periodes) : {
                  [mois]: {
                    ...(store.periodes()[mois] as MoisDeFacturationReponse),
                    statut: 'OUVERT', rouvertLe: new Date().toISOString(), motifDeReouverture: motif,
                  },
                }),
              },
            })),
            catchError((err: unknown) => {
              patchState(store, {periodeActionMois: null, ...facturationErrorState(err)});
              return EMPTY;
            })
          )
        )
      )
    ),
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
                ...facturationErrorState(err),
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
              patchState(store, {dashboardLoading: false, dashboard: null, ...facturationErrorState(err)});
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
              patchState(store, {previewLoading: false, preview: null, ...facturationErrorState(err)});
              return EMPTY;
            })
          )
        )
      )
    ),

    removeSeanceFromPreview: rxMethod<{
      centerId: string;
      userId: string;
      seanceId: string;
      motif: MotifAbsence;
      commentaire?: string
    }>(
      pipe(
        tap(({seanceId}) => patchState(store, {
          previewLoading: true,
          error: null,
          errorParams: null,
          successMessage: null,
          seanceActionLoadingId: seanceId
        })),
        switchMap(({centerId, userId, seanceId, motif, commentaire}) => {
          // la séance disparaît de la simulation renvoyée : on la retient maintenant pour pouvoir la réinclure
          const exclue = findSeanceInPreview(store.preview(), seanceId);
          const note = commentaire?.trim();
          return api.excludeSeanceFromPreview(seanceId, {
            centerId,
            userId,
            motif,
            ...(note ? {commentaire: note} : {}),
            ...periodPayload(store.periodMode(), store.month(), store.startDate(), store.endDate()),
            regroupementMultiForfait: store.settingsDraft()?.regroupementMultiForfait ?? true,
          }).pipe(
            tap((preview) => patchState(store, {
              preview,
              previewLoading: false,
              seanceActionLoadingId: null,
              excludedSeances: [
                ...store.excludedSeances().filter((item) => item.seanceId !== seanceId),
                {seanceId, seanceDate: exclue?.seanceDate ?? '', patientLabel: exclue?.patientLabel ?? '', motif},
              ],
              successMessage: 'FACTURATION.SUCCESS.SEANCE_EXCLUDED',
            })),
            catchError((err: unknown) => {
              patchState(store, {previewLoading: false, seanceActionLoadingId: null, ...facturationErrorState(err)});
              return EMPTY;
            })
          );
        })
      )
    ),

    reincludeSeanceInPreview: rxMethod<{ centerId: string; seanceId: string }>(
      pipe(
        tap(({seanceId}) => patchState(store, {
          previewLoading: true,
          error: null,
          errorParams: null,
          successMessage: null,
          seanceActionLoadingId: seanceId
        })),
        switchMap(({centerId, seanceId}) =>
          api.includeSeanceInPreview(seanceId, {
            centerId,
            ...periodPayload(store.periodMode(), store.month(), store.startDate(), store.endDate()),
            regroupementMultiForfait: store.settingsDraft()?.regroupementMultiForfait ?? true,
          }).pipe(
            tap((preview) => patchState(store, {
              preview,
              previewLoading: false,
              seanceActionLoadingId: null,
              excludedSeances: store.excludedSeances().filter((item) => item.seanceId !== seanceId),
              successMessage: 'FACTURATION.SUCCESS.SEANCE_REINCLUDED',
            })),
            catchError((err: unknown) => {
              patchState(store, {previewLoading: false, seanceActionLoadingId: null, ...facturationErrorState(err)});
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
              patchState(store, {previewLoading: false, seanceActionLoadingId: null, ...facturationErrorState(err)});
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
              excludedSeances: [],
              successMessage: 'FACTURATION.SUCCESS.VALIDATED',
            })),
            catchError((err: unknown) => {
              patchState(store, {validating: false, ...facturationErrorState(err)});
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
              patchState(store, {savingSettings: false, ...facturationErrorState(err)});
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

/** Retrouve la séance (date, patient) dans la simulation courante, pour la lister une fois exclue. */
function findSeanceInPreview(preview: FacturationPreviewResponse | null, seanceId: string):
  { seanceDate: string; patientLabel: string } | null {
  for (const invoice of preview?.invoices ?? []) {
    const seance = invoice.seances.find((item) => item.seanceId === seanceId);
    if (seance) {
      return {
        seanceDate: seance.seanceDate,
        patientLabel: `${invoice.patientCode ?? ''} ${invoice.patientFullName ?? ''}`.trim(),
      };
    }
  }
  return null;
}

