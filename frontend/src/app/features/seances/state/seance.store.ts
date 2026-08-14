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
import {RefItem, ReferentialApiService} from '../../../core/api/referential-api.service';
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
  seancesTotal: number;
  seancesPageIndex: number;
  seancesPageSize: number;

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

  /** Forfait séance éditable */
  availableForfaits: RefItem[];
  forfaitsLoading: boolean;
  selectedForfaitId: string;
  savingForfait: boolean;

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

  /** Édition en ligne d'un consommable existant */
  editingConsommableArticleId: string | null;
  editingConsommableQuantite: number | null;
  savingConsommable: boolean;

  /** Articles disponibles dans le stock du centre */
  availableArticles: ArticleStock[];
  articlesLoading: boolean;
};

const DASHBOARD_PAGE_SIZE = 10;

const initialState: SeanceState = {
  seances: [],
  seancesLoading: false,
  seancesTotal: 0,
  seancesPageIndex: 0,
  seancesPageSize: 20,
  selectedSeanceId: null,
  summary: null,
  summaryLoading: false,
  qrCode: '',
  dateSeance: todayIsoDate(),
  scanState: 'idle',
  scanMessage: 'SEANCES.SCAN_READY_MESSAGE',
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
  availableForfaits: [],
  forfaitsLoading: false,
  selectedForfaitId: '',
  savingForfait: false,
  validatingSeance: false,
  error: null,
  activeCenterId: null,
  consommables: [],
  newConsommableArticleId: '',
  newConsommableQuantite: null,
  editingConsommableArticleId: null,
  editingConsommableQuantite: null,
  savingConsommable: false,
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
      return status === 'VALIDEE' || status === 'SIGNEE' || status === 'FACTUREE';
    }),
    dashboardDetailPageItems: computed(() => {
      const start = store.dashboardDetailPageIndex() * DASHBOARD_PAGE_SIZE;
      return store.dashboardDetailItems().slice(start, start + DASHBOARD_PAGE_SIZE);
    }),
    dashboardDetailTotalPages: computed(() =>
      Math.max(1, Math.ceil(store.dashboardDetailItems().length / DASHBOARD_PAGE_SIZE))
    ),
  })),
  withMethods((store, api = inject(BackendApiService), referentialApi = inject(ReferentialApiService)) => ({
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

    // --- Édition en ligne quantité ---
    startEditConsommable(articleId: string): void {
      const item = store.consommables().find((c) => c.articleId === articleId);
      patchState(store, {
        editingConsommableArticleId: articleId,
        editingConsommableQuantite: item?.quantite ?? null,
      });
    },
    cancelEditConsommable(): void {
      patchState(store, {editingConsommableArticleId: null, editingConsommableQuantite: null});
    },
    setEditingConsommableQuantite(editingConsommableQuantite: number | null): void {
      patchState(store, {editingConsommableQuantite});
    },

    /** Remove a consommable from backend (validated seance) then from local state. */
    removeConsommableFromSeance: rxMethod<{ seanceId: string; articleId: string; centerId: string; userId: string }>(
      pipe(
        tap(() => patchState(store, {savingConsommable: true, error: null})),
        switchMap(({seanceId, articleId, centerId, userId}) =>
          api.removeSeanceConsommable(seanceId, articleId, centerId, userId).pipe(
            tap(() => patchState(store, {
              savingConsommable: false,
              consommables: store.consommables().filter((c) => c.articleId !== articleId),
              scanState: 'success',
              scanMessage: 'SEANCES.CONSUMABLE_REMOVED',
            })),
            catchError((err: unknown) => {
              patchState(store, {savingConsommable: false, error: errorMessage(err)});
              return EMPTY;
            })
          )
        )
      )
    ),

    /** Update consommable quantity in backend (validated seance) then in local state. */
    updateConsommableQuantiteInSeance: rxMethod<{
      seanceId: string; articleId: string; centerId: string; userId: string; quantite: number;
    }>(
      pipe(
        tap(() => patchState(store, {savingConsommable: true, error: null})),
        switchMap(({seanceId, articleId, centerId, userId, quantite}) =>
          api.updateSeanceConsommableQuantite(seanceId, articleId, {centerId, userId, quantite}).pipe(
            tap(() => patchState(store, {
              savingConsommable: false,
              editingConsommableArticleId: null,
              editingConsommableQuantite: null,
              consommables: store.consommables().map((c) =>
                c.articleId === articleId ? {...c, quantite} : c
              ),
              scanState: 'success',
              scanMessage: 'SEANCES.CONSUMABLE_QTY_UPDATED',
            })),
            catchError((err: unknown) => {
              patchState(store, {savingConsommable: false, error: errorMessage(err)});
              return EMPTY;
            })
          )
        )
      )
    ),

    // --- QR / scan ---
    setQrCode(qrCode: string): void {
      patchState(store, {qrCode});
    },
    setDateSeance(dateSeance: string): void {
      patchState(store, {dateSeance});
    },
    setSelectedForfaitId(selectedForfaitId: string): void {
      patchState(store, {selectedForfaitId});
    },
    resetScan(): void {
      patchState(store, {scanState: 'idle', scanMessage: 'SEANCES.SCAN_READY_MESSAGE', scanning: false, qrCode: ''});
    },

    loadForfaits: rxMethod<{ centerId: string }>(
      pipe(
        tap(() => patchState(store, {forfaitsLoading: true, error: null})),
        switchMap(({centerId}) =>
          referentialApi.getForfaits(centerId).pipe(
            tap((forfaits) => patchState(store, {availableForfaits: forfaits, forfaitsLoading: false})),
            catchError((err: unknown) => {
              patchState(store, {availableForfaits: [], forfaitsLoading: false, error: errorMessage(err)});
              return EMPTY;
            })
          )
        )
      )
    ),

    // --- Chargement liste séances ---
    loadSeances: rxMethod<{ centerId: string; page?: number; size?: number; month?: string }>(
      pipe(
        tap(() => patchState(store, {seancesLoading: true, error: null})),
        switchMap(({centerId, page, size, month}) =>
          api.listSeances(
            centerId,
            page ?? store.seancesPageIndex(),
            size ?? store.seancesPageSize(),
            month ?? store.dashboardMonth()
          ).pipe(
            tap((res) => patchState(store, {
              seances: res.items,
              seancesTotal: res.total,
              seancesLoading: false,
            })),
            catchError((err: unknown) => {
              patchState(store, {seances: [], seancesTotal: 0, seancesLoading: false, error: errorMessage(err)});
              return EMPTY;
            })
          )
        )
      )
    ),

    setSeancesPagination(pageIndex: number, pageSize: number): void {
      patchState(store, {seancesPageIndex: pageIndex, seancesPageSize: pageSize});
    },

    // --- Scan QR ---
    scanQr: rxMethod<{ centerId: string; qrCode: string }>(
      pipe(
        tap(() => patchState(store, {scanning: true, error: null})),
        switchMap(({centerId, qrCode}) =>
          api.scanSeanceQr({centerId, qrCode}).pipe(
            switchMap((created) =>
              api.listSeances(centerId, 0, store.seancesPageSize()).pipe(
                switchMap((res) =>
                  api.getSeanceSummary(created.id, centerId).pipe(
                    tap((summary) => {
                      patchState(store, {
                        seances: res.items,
                        seancesTotal: res.total,
                        seancesPageIndex: 0,
                        scanning: false,
                        scanState: 'success',
                        scanMessage: 'SEANCES.SESSION_CREATED_LISTED',
                        ...summaryStateFromSummary(summary),
                      });
                    }),
                    catchError(() => {
                      patchState(store, {
                        seances: res.items,
                        seancesTotal: res.total,
                        seancesPageIndex: 0,
                        summary: null,
                        selectedSeanceId: created.id,
                        editDateSeance: created.dateSeance,
                        dateSeance: created.dateSeance,
                        scanning: false,
                        scanState: 'success',
                        scanMessage: 'SEANCES.SESSION_CREATED_LISTED',
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
                    scanMessage: 'SEANCES.SESSION_CREATED_LISTED',
                  });
                  return EMPTY;
                })
              )
            ),
            catchError((err: unknown) => {
              patchState(store, {
                scanning: false,
                scanState: 'error',
                scanMessage: 'SEANCES.INVALID_QR_OR_PATIENT',
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
                scanMessage: 'SEANCES.SESSION_DATE_UPDATED',
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

    saveForfait: rxMethod<{ seanceId: string; centerId: string; forfaitId: string; userId: string }>(
      pipe(
        tap(() => patchState(store, {savingForfait: true, error: null})),
        switchMap(({seanceId, centerId, forfaitId, userId}) =>
          api.updateSeanceForfait(seanceId, {centerId, forfaitId, userId}).pipe(
            tap((updated) => patchState(store, {
              seances: patchSeanceForfaitInList(store.seances(), seanceId, updated.forfait ?? null),
              summary: patchSummaryForfait(store.summary(), seanceId, updated.forfait ?? null),
              selectedForfaitId: updated.forfait?.id ?? forfaitId,
              savingForfait: false,
              scanState: 'success',
              scanMessage: 'SEANCES.FORFAIT_UPDATED',
            })),
            catchError((err: unknown) => {
              patchState(store, {savingForfait: false, error: errorMessage(err)});
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
              scanMessage: 'SEANCES.PARAMEDICAL_SAVED'
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
              scanMessage: 'SEANCES.MEDICAL_SAVED'
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
              scanMessage: 'SEANCES.SESSION_VALIDATED',
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
            tap(() => patchState(store, {scanState: 'success', scanMessage: 'SEANCES.SESSION_SIGNED_BY_DOCTOR'})),
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
      if (typeof inner['detail'] === 'string') return inner['detail'];
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
    selectedForfaitId: summary.forfait?.id ?? '',
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
    consommables: (summary.consommables ?? [])
      .filter((row) => !!row.articleId && Number(row.quantite ?? 0) > 0)
      .map((row) => ({
        articleId: row.articleId,
        articleCode: row.articleCode ?? '',
        articleLibelle: row.articleLibelle ?? '',
        articleUnite: row.articleUnite ?? '',
        quantite: Number(row.quantite ?? 0),
      })),
  };
}

function patchSummaryForfait(
  summary: SeanceSummary | null,
  seanceId: string,
  forfait: SeanceSummary['forfait'] | null,
): SeanceSummary | null {
  if (!summary || summary.seance.id !== seanceId) {
    return summary;
  }
  return {
    ...summary,
    forfait,
  };
}

function patchSeanceForfaitInList(
  seances: SeanceListItem[],
  seanceId: string,
  forfait: SeanceSummary['forfait'] | null,
): SeanceListItem[] {
  return seances.map((seance) =>
    seance.id === seanceId
      ? {
        ...seance,
        forfait,
      }
      : seance
  );
}

