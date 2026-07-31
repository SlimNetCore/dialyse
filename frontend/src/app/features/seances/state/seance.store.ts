import {computed, inject} from '@angular/core';
import {patchState, signalStore, withComputed, withHooks, withMethods, withState} from '@ngrx/signals';
import {rxMethod} from '@ngrx/signals/rxjs-interop';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {catchError, EMPTY, of, pipe, switchMap, tap} from 'rxjs';
import {
  ArticleStock,
  BackendApiService,
  SeanceCalendarResponse,
  SeanceDashboardDetailItem,
  SeanceDashboardDetailsResponse,
  SeanceJournalByDate,
  SeanceListItem,
  SeanceMonthlyDashboard,
  SeanceSummary,
  UpsertVoletMedicalPayload,
  UpsertVoletParamedicalPayload,
  ValidateSeancePayload,
} from '../../../core/api/backend-api.service';
import {AppShellStore} from '../../../core/state/app-shell.store';

function todayIsoDate(): string {
  return new Date().toISOString().slice(0, 10);
}

function currentMonthIso(): string {
  return todayIsoDate().slice(0, 7);
}

export type SeanceScanState = 'idle' | 'image' | 'success' | 'error';

export type ConsommableItem = {
  articleId: string;
  articleCode: string;
  articleLibelle: string;
  articleUnite: string;
  quantite: number;
};

type SeanceState = {
  /** Liste des séances du centre courant */
  seances: SeanceListItem[];
  seancesLoading: boolean;

  /** Séance sélectionnée */
  selectedSeanceId: string | null;
  summary: SeanceSummary | null;
  summaryLoading: boolean;

  /** Scan QR */
  qrCode: string;
  dateSeance: string;
  scanState: SeanceScanState;
  scanMessage: string;
  scanning: boolean;

  /** Journal journalier */
  journalDate: string;
  journalLoading: boolean;
  journalPatients: SeanceJournalByDate['patients'];
  journalArticles: SeanceJournalByDate['sortiesArticles'];

  /** Dashboard mensuel */
  dashboardMonth: string;
  dashboardLoading: boolean;
  seanceDashboard: SeanceMonthlyDashboard | null;

  /** Détails dashboard (présences/absences) */
  dashboardDetailsOpen: boolean;
  dashboardDetailsKind: 'presence' | 'absence';
  dashboardDetailsLoading: boolean;
  dashboardDetailItems: SeanceDashboardDetailItem[];
  dashboardDetailPageIndex: number;

  /** Calendrier (fériés / fermetures) */
  calendarHolidays: SeanceCalendarResponse['holidays'];
  calendarClosures: SeanceCalendarResponse['closures'];
  newHolidayDate: string;
  newHolidayLabel: string;
  newClosureDate: string;
  newClosureReason: string;

  /** Volet paramédical édité */
  taAvant: string;
  taApres: string;
  poidsAvantKg: number | null;
  poidsApresKg: number | null;
  dureeMinutes: number | null;
  debitSangMlMin: number | null;
  ultrafiltrationMl: number | null;
  anticoagulant: string;
  typeDialysat: string;
  incidents: string;
  savingParamedical: boolean;

  /** Volet médical édité */
  prescription: string;
  toleranceSeance: string;
  examenClinique: string;
  resultatsBiologiques: string;
  ajustementsTherapeutiques: string;
  conclusionMedicale: string;
  savingMedical: boolean;

  /** Date séance éditable */
  editDateSeance: string;
  savingDate: boolean;

  /** Validation séance */
  validatingSeance: boolean;

  /** Erreur globale */
  error: string | null;

  /** Centre courant */
  activeCenterId: string | null;

  /** Consommables de la séance en cours de saisie */
  consommables: ConsommableItem[];
  newConsommableArticleId: string;
  newConsommableQuantite: number | null;

  /** Articles disponibles dans le stock du centre */
  availableArticles: ArticleStock[];
  articlesLoading: boolean;
};

const DASHBOARD_PAGE_SIZE = 10;

const initialState: SeanceState = {
  seances: [],
  seancesLoading: false,
  selectedSeanceId: null,
  summary: null,
  summaryLoading: false,
  qrCode: '',
  dateSeance: todayIsoDate(),
  scanState: 'idle',
  scanMessage: 'Prêt à scanner',
  scanning: false,
  journalDate: todayIsoDate(),
  journalLoading: false,
  journalPatients: [],
  journalArticles: [],
  dashboardMonth: currentMonthIso(),
  dashboardLoading: false,
  seanceDashboard: null,
  dashboardDetailsOpen: false,
  dashboardDetailsKind: 'presence',
  dashboardDetailsLoading: false,
  dashboardDetailItems: [],
  dashboardDetailPageIndex: 0,
  calendarHolidays: [],
  calendarClosures: [],
  newHolidayDate: todayIsoDate(),
  newHolidayLabel: '',
  newClosureDate: todayIsoDate(),
  newClosureReason: '',
  taAvant: '',
  taApres: '',
  poidsAvantKg: null,
  poidsApresKg: null,
  dureeMinutes: null,
  debitSangMlMin: null,
  ultrafiltrationMl: null,
  anticoagulant: '',
  typeDialysat: '',
  incidents: '',
  savingParamedical: false,
  prescription: '',
  toleranceSeance: '',
  examenClinique: '',
  resultatsBiologiques: '',
  ajustementsTherapeutiques: '',
  conclusionMedicale: '',
  savingMedical: false,
  editDateSeance: todayIsoDate(),
  savingDate: false,
  validatingSeance: false,
  error: null,
  activeCenterId: null,
  consommables: [],
  newConsommableArticleId: '',
  newConsommableQuantite: null,
  availableArticles: [],
  articlesLoading: false,
};

export const SeanceStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withDevtools('SeanceStore'),
  withComputed((store) => ({
    selectedPreview: computed(() => {
      const id = store.selectedSeanceId();
      return id ? (store.seances().find((s) => s.id === id) ?? null) : null;
    }),
    isSeanceAlreadyValidated: computed(() => {
      const status = store.summary()?.seance.status;
      return status === 'VALIDEE' || status === 'SIGNEE';
    }),
    dashboardDetailPageItems: computed(() => {
      const start = store.dashboardDetailPageIndex() * DASHBOARD_PAGE_SIZE;
      return store.dashboardDetailItems().slice(start, start + DASHBOARD_PAGE_SIZE);
    }),
    dashboardDetailTotalPages: computed(() =>
      Math.max(1, Math.ceil(store.dashboardDetailItems().length / DASHBOARD_PAGE_SIZE))
    ),
  })),
  withMethods((store, api = inject(BackendApiService)) => ({
    // --- Articles stock ---
    loadArticlesStock: rxMethod<{ centerId: string }>(
      pipe(
        tap(() => patchState(store, {articlesLoading: true, error: null})),
        switchMap(({centerId}) =>
          api.listArticlesStock(centerId).pipe(
            tap((articles) => patchState(store, {availableArticles: articles, articlesLoading: false})),
            catchError((err: unknown) => {
              patchState(store, {availableArticles: [], articlesLoading: false, error: errorMessage(err)});
              return EMPTY;
            })
          )
        )
      )
    ),

    // --- Consommables ---
    setNewConsommableArticleId(newConsommableArticleId: string): void {
      patchState(store, {newConsommableArticleId});
    },
    setNewConsommableQuantite(newConsommableQuantite: number | null): void {
      patchState(store, {newConsommableQuantite});
    },
    addConsommable(article: ArticleStock, quantite: number): void {
      if (!article?.id || quantite <= 0) return;
      const existing = store.consommables().findIndex((c) => c.articleId === article.id);
      if (existing >= 0) {
        const updated = store.consommables().map((c, i) =>
          i === existing ? {...c, quantite: c.quantite + quantite} : c
        );
        patchState(store, {consommables: updated, newConsommableArticleId: '', newConsommableQuantite: null});
      } else {
        patchState(store, {
          consommables: [
            ...store.consommables(),
            {
              articleId: article.id,
              articleCode: article.code,
              articleLibelle: article.libelle ?? '',
              articleUnite: article.unite ?? '',
              quantite,
            },
          ],
          newConsommableArticleId: '',
          newConsommableQuantite: null,
        });
      }
    },
    removeConsommable(articleId: string): void {
      patchState(store, {consommables: store.consommables().filter((c) => c.articleId !== articleId)});
    },
    clearConsommables(): void {
      patchState(store, {consommables: [], newConsommableArticleId: '', newConsommableQuantite: null});
    },

    // --- QR / scan ---
    setQrCode(qrCode: string): void {
      patchState(store, {qrCode});
    },
    setDateSeance(dateSeance: string): void {
      patchState(store, {dateSeance});
    },
    resetScan(): void {
      patchState(store, {scanState: 'idle', scanMessage: 'Prêt à scanner', scanning: false, qrCode: ''});
    },

    // --- Chargement liste séances ---
    loadSeances: rxMethod<{ centerId: string }>(
      pipe(
        tap(() => patchState(store, {seancesLoading: true, error: null})),
        switchMap(({centerId}) =>
          api.listSeances(centerId).pipe(
            tap((items) => patchState(store, {seances: items, seancesLoading: false})),
            catchError((err: unknown) => {
              patchState(store, {seances: [], seancesLoading: false, error: errorMessage(err)});
              return EMPTY;
            })
          )
        )
      )
    ),

    // --- Scan QR ---
    scanQr: rxMethod<{ centerId: string; qrCode: string }>(
      pipe(
        tap(() => patchState(store, {scanning: true, error: null})),
        switchMap(({centerId, qrCode}) =>
          api.scanSeanceQr({centerId, qrCode}).pipe(
            switchMap((created) =>
              api.listSeances(centerId).pipe(
                switchMap((items) =>
                  api.getSeanceSummary(created.id, centerId).pipe(
                    tap((summary) => {
                      patchState(store, {
                        seances: items,
                        scanning: false,
                        scanState: 'success',
                        scanMessage: 'Séance créée et ajoutée à la liste',
                        ...summaryStateFromSummary(summary),
                      });
                    }),
                    catchError(() => {
                      patchState(store, {
                        seances: items,
                        summary: null,
                        selectedSeanceId: created.id,
                        editDateSeance: created.dateSeance,
                        dateSeance: created.dateSeance,
                        scanning: false,
                        scanState: 'success',
                        scanMessage: 'Séance créée et ajoutée à la liste',
                      });
                      return EMPTY;
                    })
                  )
                ),
                catchError(() => {
                  patchState(store, {
                    summary: null,
                    selectedSeanceId: created.id,
                    editDateSeance: created.dateSeance,
                    dateSeance: created.dateSeance,
                    scanning: false,
                    scanState: 'success',
                    scanMessage: 'Séance créée et ajoutée à la liste',
                  });
                  return EMPTY;
                })
              )
            ),
            catchError((err: unknown) => {
              patchState(store, {
                scanning: false,
                scanState: 'error',
                scanMessage: 'Scan QR invalide ou patient introuvable',
                error: errorMessage(err),
              });
              return EMPTY;
            })
          )
        )
      )
    ),

    // --- Détail séance ---
    loadSeanceSummary: rxMethod<{ seanceId: string; centerId: string }>(
      pipe(
        tap(() => patchState(store, {summaryLoading: true, error: null})),
        switchMap(({seanceId, centerId}) =>
          api.getSeanceSummary(seanceId, centerId).pipe(
            tap((summary) => patchState(store, {summaryLoading: false, ...summaryStateFromSummary(summary)})),
            catchError((err: unknown) => {
              patchState(store, {summaryLoading: false, error: errorMessage(err)});
              return EMPTY;
            })
          )
        )
      )
    ),

    // --- Mise à jour date séance ---
    setEditDateSeance(editDateSeance: string): void {
      patchState(store, {editDateSeance});
    },
    saveDate: rxMethod<{ seanceId: string; centerId: string; dateSeance: string }>(
      pipe(
        tap(() => patchState(store, {savingDate: true, error: null})),
        switchMap(({seanceId, centerId, dateSeance}) =>
          api.updateSeance(seanceId, {centerId, dateSeance}).pipe(
            tap((updated) => {
              const nextSeances = upsertSeanceInList(store.seances(), updated.id, updated.status, updated.dateSeance || dateSeance);
              const nextSummary = patchSummaryStatusAndDate(store.summary(), updated.id, updated.status, updated.dateSeance || dateSeance);
              patchState(store, {
                seances: nextSeances,
                summary: nextSummary,
                savingDate: false,
                dateSeance: updated.dateSeance || dateSeance,
                editDateSeance: updated.dateSeance || dateSeance,
                scanState: 'success',
                scanMessage: 'Date de séance mise à jour',
              });
            }),
            catchError((err: unknown) => {
              patchState(store, {savingDate: false, error: errorMessage(err)});
              return EMPTY;
            })
          )
        )
      )
    ),

    // --- Volet paramédical ---
    patchParamedical(fields: Partial<Pick<SeanceState,
      'taAvant' | 'taApres' | 'poidsAvantKg' | 'poidsApresKg' |
      'dureeMinutes' | 'debitSangMlMin' | 'ultrafiltrationMl' |
      'anticoagulant' | 'typeDialysat' | 'incidents'>>): void {
      patchState(store, fields);
    },
    saveParamedical: rxMethod<{ seanceId: string; payload: UpsertVoletParamedicalPayload }>(
      pipe(
        tap(() => patchState(store, {savingParamedical: true, error: null})),
        switchMap(({seanceId, payload}) =>
          api.upsertVoletParamedical(seanceId, payload).pipe(
            tap(() => patchState(store, {
              savingParamedical: false,
              scanState: 'success',
              scanMessage: 'Volet paramédical enregistré'
            })),
            catchError((err: unknown) => {
              patchState(store, {savingParamedical: false, error: errorMessage(err)});
              return EMPTY;
            })
          )
        )
      )
    ),

    // --- Volet médical ---
    patchMedical(fields: Partial<Pick<SeanceState,
      'prescription' | 'toleranceSeance' | 'examenClinique' |
      'resultatsBiologiques' | 'ajustementsTherapeutiques' | 'conclusionMedicale'>>): void {
      patchState(store, fields);
    },
    saveMedical: rxMethod<{ seanceId: string; payload: UpsertVoletMedicalPayload }>(
      pipe(
        tap(() => patchState(store, {savingMedical: true, error: null})),
        switchMap(({seanceId, payload}) =>
          api.upsertVoletMedical(seanceId, payload).pipe(
            tap(() => patchState(store, {
              savingMedical: false,
              scanState: 'success',
              scanMessage: 'Volet médical enregistré'
            })),
            catchError((err: unknown) => {
              patchState(store, {savingMedical: false, error: errorMessage(err)});
              return EMPTY;
            })
          )
        )
      )
    ),

    // --- Validation séance (infirmier) ---
    validateSeance: rxMethod<{ seanceId: string; payload: ValidateSeancePayload }>(
      pipe(
        tap(() => patchState(store, {validatingSeance: true, error: null})),
        switchMap(({seanceId, payload}) =>
          api.validateSeance(seanceId, payload).pipe(
            tap((updated) => patchState(store, {
              seances: upsertSeanceInList(store.seances(), updated.id, updated.status),
              summary: patchSummaryStatusAndDate(store.summary(), updated.id, updated.status),
              validatingSeance: false,
              scanState: 'success',
              scanMessage: 'Séance validée',
              consommables: []
            })),
            catchError((err: unknown) => {
              patchState(store, {validatingSeance: false, error: errorMessage(err)});
              return EMPTY;
            })
          )
        )
      )
    ),

    // --- Signature médecin ---
    signByMedecin: rxMethod<{ seanceId: string; centerId: string; userId: string }>(
      pipe(
        switchMap(({seanceId, centerId, userId}) =>
          api.signSeanceByMedecin(seanceId, {centerId, userId}).pipe(
            tap(() => patchState(store, {scanState: 'success', scanMessage: 'Séance signée par le médecin'})),
            catchError((err: unknown) => {
              patchState(store, {error: errorMessage(err)});
              return EMPTY;
            })
          )
        )
      )
    ),

    // --- Journal journalier ---
    setJournalDate(journalDate: string): void {
      patchState(store, {journalDate});
    },
    loadJournal: rxMethod<{ centerId: string; date: string }>(
      pipe(
        tap(() => patchState(store, {journalLoading: true, error: null})),
        switchMap(({centerId, date}) =>
          api.getSeanceJournalByDate(centerId, date).pipe(
            tap((journal) => patchState(store, {
              journalLoading: false,
              journalPatients: journal.patients ?? [],
              journalArticles: journal.sortiesArticles ?? [],
            })),
            catchError((err: unknown) => {
              patchState(store, {
                journalLoading: false,
                journalPatients: [],
                journalArticles: [],
                error: errorMessage(err)
              });
              return EMPTY;
            })
          )
        )
      )
    ),

    // --- Dashboard mensuel ---
    setDashboardMonth(dashboardMonth: string): void {
      patchState(store, {dashboardMonth});
    },
    loadDashboard: rxMethod<{ centerId: string; year: number; month: number }>(
      pipe(
        tap(() => patchState(store, {dashboardLoading: true, error: null})),
        switchMap(({centerId, year, month}) =>
          api.getSeanceMonthlyDashboard(centerId, year, month).pipe(
            tap((data) => patchState(store, {seanceDashboard: data, dashboardLoading: false})),
            catchError((err: unknown) => {
              patchState(store, {dashboardLoading: false, error: errorMessage(err)});
              return EMPTY;
            })
          )
        )
      )
    ),

    // --- Détails dashboard ---
    openDashboardDetails(kind: 'presence' | 'absence'): void {
      patchState(store, {
        dashboardDetailsOpen: true,
        dashboardDetailsKind: kind,
        dashboardDetailPageIndex: 0,
        dashboardDetailItems: []
      });
    },
    closeDashboardDetails(): void {
      patchState(store, {dashboardDetailsOpen: false});
    },
    loadDashboardDetails: rxMethod<{ centerId: string; year: number; month: number; kind: 'presence' | 'absence' }>(
      pipe(
        tap(() => patchState(store, {dashboardDetailsLoading: true, error: null})),
        switchMap(({centerId, year, month, kind}) =>
          api.getSeanceDashboardDetails(centerId, year, month, kind).pipe(
            tap((res: SeanceDashboardDetailsResponse) =>
              patchState(store, {dashboardDetailsLoading: false, dashboardDetailItems: res.items ?? []})
            ),
            catchError((err: unknown) => {
              patchState(store, {dashboardDetailsLoading: false, dashboardDetailItems: [], error: errorMessage(err)});
              return EMPTY;
            })
          )
        )
      )
    ),
    setDashboardDetailPage(dashboardDetailPageIndex: number): void {
      patchState(store, {dashboardDetailPageIndex});
    },

    // --- Calendrier ---
    setNewHolidayDate(newHolidayDate: string): void {
      patchState(store, {newHolidayDate});
    },
    setNewHolidayLabel(newHolidayLabel: string): void {
      patchState(store, {newHolidayLabel});
    },
    setNewClosureDate(newClosureDate: string): void {
      patchState(store, {newClosureDate});
    },
    setNewClosureReason(newClosureReason: string): void {
      patchState(store, {newClosureReason});
    },
    loadCalendar: rxMethod<{ centerId: string; year: number; month: number }>(
      pipe(
        switchMap(({centerId, year, month}) =>
          api.getSeanceCalendar(centerId, year, month).pipe(
            tap((calendar) => patchState(store, {
              calendarHolidays: calendar.holidays ?? [],
              calendarClosures: calendar.closures ?? [],
            })),
            catchError(() => of(null))
          )
        )
      )
    ),
    addHoliday: rxMethod<{ centerId: string; date: string; label: string }>(
      pipe(
        switchMap(({centerId, date, label}) =>
          api.addSeanceHoliday(centerId, date, label).pipe(
            tap(() => patchState(store, {newHolidayLabel: ''})),
            catchError((err: unknown) => {
              patchState(store, {error: errorMessage(err)});
              return EMPTY;
            })
          )
        )
      )
    ),
    deleteHoliday: rxMethod<{ centerId: string; id: string }>(
      pipe(
        switchMap(({centerId, id}) =>
          api.deleteSeanceHoliday(centerId, id).pipe(
            catchError((err: unknown) => {
              patchState(store, {error: errorMessage(err)});
              return EMPTY;
            })
          )
        )
      )
    ),
    addClosure: rxMethod<{ centerId: string; date: string; reason: string }>(
      pipe(
        switchMap(({centerId, date, reason}) =>
          api.addSeanceClosure(centerId, date, reason).pipe(
            tap(() => patchState(store, {newClosureReason: ''})),
            catchError((err: unknown) => {
              patchState(store, {error: errorMessage(err)});
              return EMPTY;
            })
          )
        )
      )
    ),
    deleteClosure: rxMethod<{ centerId: string; id: string }>(
      pipe(
        switchMap(({centerId, id}) =>
          api.deleteSeanceClosure(centerId, id).pipe(
            catchError((err: unknown) => {
              patchState(store, {error: errorMessage(err)});
              return EMPTY;
            })
          )
        )
      )
    ),

    // --- Sélection ---
    selectSeance(seanceId: string | null): void {
      patchState(store, {selectedSeanceId: seanceId});
    },
    clearSummary(): void {
      patchState(store, {summary: null, selectedSeanceId: null});
    },
    clearError(): void {
      patchState(store, {error: null});
    },
    setActiveCenterId(activeCenterId: string | null): void {
      patchState(store, {activeCenterId});
    },
  })),
  withHooks((store, appShell = inject(AppShellStore)) => ({
    onInit() {
      // Réactivité automatique au changement de centre
      const centerId = appShell.currentCenterId();
      if (centerId) {
        patchState(store, {activeCenterId: centerId});
      }
    }
  }))
);

function errorMessage(err: unknown): string {
  if (err && typeof err === 'object') {
    const e = err as Record<string, unknown>;
    if (e['error'] && typeof e['error'] === 'object') {
      const inner = e['error'] as Record<string, unknown>;
      if (typeof inner['message'] === 'string') return inner['message'];
    }
    if (typeof e['statusText'] === 'string') return e['statusText'];
    if (typeof e['message'] === 'string') return e['message'];
  }
  return 'Erreur inattendue';
}

function upsertSeanceInList(
  seances: SeanceListItem[],
  seanceId: string,
  status: string,
  dateSeance?: string | null,
): SeanceListItem[] {
  return seances.map((s) =>
    s.id === seanceId
      ? {
        ...s,
        status,
        dateSeance: dateSeance ?? s.dateSeance,
      }
      : s
  );
}

function patchSummaryStatusAndDate(
  summary: SeanceSummary | null,
  seanceId: string,
  status: string,
  dateSeance?: string | null,
): SeanceSummary | null {
  if (!summary || summary.seance.id !== seanceId) {
    return summary;
  }
  return {
    ...summary,
    seance: {
      ...summary.seance,
      status,
      dateSeance: dateSeance ?? summary.seance.dateSeance,
    }
  };
}

function summaryStateFromSummary(summary: SeanceSummary): Partial<SeanceState> {
  return {
    summary,
    selectedSeanceId: summary.seance.id,
    editDateSeance: summary.seance.dateSeance,
    dateSeance: summary.seance.dateSeance,
    taAvant: summary.paramedical?.taAvant ?? '',
    taApres: summary.paramedical?.taApres ?? '',
    poidsAvantKg: summary.paramedical?.poidsAvantKg ?? null,
    poidsApresKg: summary.paramedical?.poidsApresKg ?? null,
    dureeMinutes: summary.paramedical?.dureeMinutes ?? null,
    debitSangMlMin: summary.paramedical?.debitSangMlMin ?? null,
    ultrafiltrationMl: summary.paramedical?.ultrafiltrationMl ?? null,
    anticoagulant: summary.paramedical?.anticoagulant ?? '',
    typeDialysat: summary.paramedical?.typeDialysat ?? '',
    incidents: summary.paramedical?.incidents ?? '',
    prescription: summary.medical?.prescription ?? '',
    toleranceSeance: summary.medical?.toleranceSeance ?? '',
    examenClinique: summary.medical?.examenClinique ?? '',
    resultatsBiologiques: summary.medical?.resultatsBiologiques ?? '',
    ajustementsTherapeutiques: summary.medical?.ajustementsTherapeutiques ?? '',
    conclusionMedicale: summary.medical?.conclusionMedicale ?? '',
    consommables: [],
  };
}

