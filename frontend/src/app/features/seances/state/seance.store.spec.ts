import {TestBed} from '@angular/core/testing';
import {provideZonelessChangeDetection} from '@angular/core';
import {of, throwError} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {SeanceStore} from './seance.store';
import {BackendApiService} from '../../../core/api/backend-api.service';
import {AppShellStore} from '../../../core/state/app-shell.store';

const CENTER_ID = '11111111-1111-1111-1111-111111111111';
const SEANCE_ID = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
const MOCK_SUMMARY = {
  seance: {id: SEANCE_ID, centerId: CENTER_ID, patientId: 'pid', dateSeance: '2026-07-24', status: 'CREE'},
  patient: {id: 'pid', nom: 'Dupont', prenom: 'Jean'},
  paramedical: null,
  medical: null,
  forfait: {id: 'forfait-1', code: 'F001', nom: 'Forfait hémodialyse', prix: 3500},
};
const MOCK_DASHBOARD = {
  year: 2026, month: 7,
  expectedSeances: 50, presenceCount: 40, absenceCount: 10, totalSeances: 40,
  sexeDistribution: {M: 20, F: 20, AUTRE: 0},
  ageDistribution: {'0-17': 0, '18-39': 5, '40-59': 15, '60+': 20, INCONNU: 0},
};
describe('SeanceStore', () => {
  let mockApi: {
    listSeances: ReturnType<typeof vi.fn>;
    scanSeanceQr: ReturnType<typeof vi.fn>;
    getSeanceSummary: ReturnType<typeof vi.fn>;
    updateSeance: ReturnType<typeof vi.fn>;
    upsertVoletParamedical: ReturnType<typeof vi.fn>;
    upsertVoletMedical: ReturnType<typeof vi.fn>;
    validateSeance: ReturnType<typeof vi.fn>;
    signSeanceByMedecin: ReturnType<typeof vi.fn>;
    getSeanceJournalByDate: ReturnType<typeof vi.fn>;
    getSeanceMonthlyDashboard: ReturnType<typeof vi.fn>;
    getSeanceDashboardDetails: ReturnType<typeof vi.fn>;
    getSeanceCalendar: ReturnType<typeof vi.fn>;
    addSeanceHoliday: ReturnType<typeof vi.fn>;
    deleteSeanceHoliday: ReturnType<typeof vi.fn>;
    addSeanceClosure: ReturnType<typeof vi.fn>;
    deleteSeanceClosure: ReturnType<typeof vi.fn>;
    exportSeanceDashboard: ReturnType<typeof vi.fn>;
    listArticlesStock: ReturnType<typeof vi.fn>;
  };
  beforeEach(() => {
    mockApi = {
      listSeances: vi.fn().mockReturnValue(of([])),
      scanSeanceQr: vi.fn().mockReturnValue(of({id: SEANCE_ID, status: 'CREE', dateSeance: '2026-07-24'})),
      getSeanceSummary: vi.fn().mockReturnValue(of(MOCK_SUMMARY)),
      updateSeance: vi.fn().mockReturnValue(of({id: SEANCE_ID, status: 'CREE', dateSeance: '2026-07-25'})),
      upsertVoletParamedical: vi.fn().mockReturnValue(of({
        id: 'vp1',
        seanceId: SEANCE_ID,
        updatedAt: '2026-07-24T10:00:00Z'
      })),
      upsertVoletMedical: vi.fn().mockReturnValue(of({
        id: 'vm1',
        seanceId: SEANCE_ID,
        updatedAt: '2026-07-24T10:00:00Z'
      })),
      validateSeance: vi.fn().mockReturnValue(of({
        id: SEANCE_ID,
        status: 'VALIDEE',
        validatedAt: '2026-07-24T10:00:00Z'
      })),
      signSeanceByMedecin: vi.fn().mockReturnValue(of({
        id: SEANCE_ID,
        status: 'SIGNEE',
        signedByMedecinAt: '2026-07-24T11:00:00Z'
      })),
      getSeanceJournalByDate: vi.fn().mockReturnValue(of({
        dateSeance: '2026-07-24',
        patients: [],
        sortiesArticles: []
      })),
      getSeanceMonthlyDashboard: vi.fn().mockReturnValue(of(MOCK_DASHBOARD)),
      getSeanceDashboardDetails: vi.fn().mockReturnValue(of({
        year: 2026, month: 7, kind: 'presence', total: 2,
        items: [
          {
            patientId: 'pid1',
            patientNom: 'A',
            patientPrenom: 'B',
            dateSeance: '2026-07-01',
            weekday: 'TUESDAY',
            scheduled: true,
            present: true,
            status: 'VALIDEE'
          },
          {
            patientId: 'pid2',
            patientNom: 'C',
            patientPrenom: 'D',
            dateSeance: '2026-07-02',
            weekday: 'WEDNESDAY',
            scheduled: true,
            present: true,
            status: 'CREE'
          },
        ],
      })),
      getSeanceCalendar: vi.fn().mockReturnValue(of({year: 2026, month: 7, holidays: [], closures: []})),
      addSeanceHoliday: vi.fn().mockReturnValue(of({id: 'h1', dayDate: '2026-07-14'})),
      deleteSeanceHoliday: vi.fn().mockReturnValue(of({deleted: true})),
      addSeanceClosure: vi.fn().mockReturnValue(of({id: 'c1', dayDate: '2026-07-01'})),
      deleteSeanceClosure: vi.fn().mockReturnValue(of({deleted: true})),
      exportSeanceDashboard: vi.fn().mockReturnValue(of(new Blob())),
      listArticlesStock: vi.fn().mockReturnValue(of([
        {
          id: 'art1',
          centerId: CENTER_ID,
          code: 'FLT-001',
          libelle: 'Filtre dialyse',
          unite: 'pce',
          stockQuantity: 100,
          pmpCourant: 15.5,
          active: true
        },
        {
          id: 'art2',
          centerId: CENTER_ID,
          code: 'TUB-002',
          libelle: 'Tubulure',
          unite: 'pce',
          stockQuantity: 50,
          pmpCourant: 8.0,
          active: true
        },
      ])),
    };
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        SeanceStore,
        AppShellStore,
        {provide: BackendApiService, useValue: mockApi},
      ],
    });
  });
  it('should start with empty seances list', () => {
    const store = TestBed.inject(SeanceStore);
    expect(store.seances()).toEqual([]);
  });
  it('should start with no selected seance', () => {
    const store = TestBed.inject(SeanceStore);
    expect(store.selectedSeanceId()).toBeNull();
    expect(store.summary()).toBeNull();
  });
  it('should start with dashboard not loaded', () => {
    const store = TestBed.inject(SeanceStore);
    expect(store.seanceDashboard()).toBeNull();
    expect(store.dashboardLoading()).toBe(false);
  });
  it('should update qrCode signal', () => {
    const store = TestBed.inject(SeanceStore);
    store.setQrCode('PAT-001');
    expect(store.qrCode()).toBe('PAT-001');
  });
  it('should update dateSeance signal', () => {
    const store = TestBed.inject(SeanceStore);
    store.setDateSeance('2026-07-24');
    expect(store.dateSeance()).toBe('2026-07-24');
  });
  it('should load seances for center', () => {
    const store = TestBed.inject(SeanceStore);
    store.loadSeances({centerId: CENTER_ID});
    expect(mockApi.listSeances).toHaveBeenCalledWith(CENTER_ID);
    expect(store.seances()).toEqual([]);
    expect(store.seancesLoading()).toBe(false);
  });
  it('should handle loadSeances error gracefully', () => {
    mockApi.listSeances.mockReturnValueOnce(throwError(() => new Error('Network error')));
    const store = TestBed.inject(SeanceStore);
    store.loadSeances({centerId: CENTER_ID});
    expect(store.seances()).toEqual([]);
    expect(store.seancesLoading()).toBe(false);
    expect(store.error()).toBeTruthy();
  });
  it('should scan QR and update selectedSeanceId on success', () => {
    const store = TestBed.inject(SeanceStore);
    store.scanQr({centerId: CENTER_ID, qrCode: 'PAT-001'});
    expect(mockApi.scanSeanceQr).toHaveBeenCalledWith({
      centerId: CENTER_ID,
      qrCode: 'PAT-001'
    });
    expect(store.selectedSeanceId()).toBe(SEANCE_ID);
    expect(store.scanState()).toBe('success');
    expect(store.scanning()).toBe(false);
  });

  it('should load seance summary after scan to expose current forfait immediately', () => {
    const store = TestBed.inject(SeanceStore);
    store.scanQr({centerId: CENTER_ID, qrCode: 'PAT-001'});

    expect(mockApi.getSeanceSummary).toHaveBeenCalledWith(SEANCE_ID, CENTER_ID);
    expect(store.summary()?.forfait?.nom).toBe('Forfait hémodialyse');
    expect(store.summary()?.forfait?.prix).toBe(3500);
  });
  it('should set error state on scan failure', () => {
    mockApi.scanSeanceQr.mockReturnValueOnce(throwError(() => ({status: 400, statusText: 'Bad Request'})));
    const store = TestBed.inject(SeanceStore);
    store.scanQr({centerId: CENTER_ID, qrCode: 'INVALID'});
    expect(store.scanState()).toBe('error');
    expect(store.scanning()).toBe(false);
  });
  it('should load seance summary and populate form fields', () => {
    const store = TestBed.inject(SeanceStore);
    store.loadSeanceSummary({seanceId: SEANCE_ID, centerId: CENTER_ID});
    expect(mockApi.getSeanceSummary).toHaveBeenCalledWith(SEANCE_ID, CENTER_ID);
    expect(store.summary()?.seance.id).toBe(SEANCE_ID);
    expect(store.summary()?.patient.nom).toBe('Dupont');
    expect(store.taAvant()).toBe('');
    expect(store.prescription()).toBe('');
  });
  it('should patch paramedical fields independently', () => {
    const store = TestBed.inject(SeanceStore);
    store.patchParamedical({taAvant: '130/80', poidsAvantKg: 70.5});
    expect(store.taAvant()).toBe('130/80');
    expect(store.poidsAvantKg()).toBe(70.5);
    expect(store.taApres()).toBe('');
    expect(store.poidsApresKg()).toBeNull();
  });
  it('should patch medical fields independently', () => {
    const store = TestBed.inject(SeanceStore);
    store.patchMedical({prescription: 'Heparine 2500 UI', conclusionMedicale: 'RAS'});
    expect(store.prescription()).toBe('Heparine 2500 UI');
    expect(store.conclusionMedicale()).toBe('RAS');
    expect(store.toleranceSeance()).toBe('');
  });
  it('should call upsertVoletParamedical with correct payload', () => {
    const store = TestBed.inject(SeanceStore);
    store.saveParamedical({
      seanceId: SEANCE_ID,
      payload: {centerId: CENTER_ID, taAvant: '120/80', poidsAvantKg: 68},
    });
    expect(mockApi.upsertVoletParamedical).toHaveBeenCalledWith(SEANCE_ID, {
      centerId: CENTER_ID,
      taAvant: '120/80',
      poidsAvantKg: 68
    });
    expect(store.savingParamedical()).toBe(false);
    expect(store.scanState()).toBe('success');
  });
  it('should call validateSeance and set success state', () => {
    const store = TestBed.inject(SeanceStore);
    store.validateSeance({seanceId: SEANCE_ID, payload: {centerId: CENTER_ID, userId: 'inf-01', consommations: []}});
    expect(mockApi.validateSeance).toHaveBeenCalledWith(SEANCE_ID, {
      centerId: CENTER_ID,
      userId: 'inf-01',
      consommations: []
    });
    expect(store.validatingSeance()).toBe(false);
    expect(store.scanState()).toBe('success');
  });
  it('isSeanceAlreadyValidated should be false when no summary', () => {
    const store = TestBed.inject(SeanceStore);
    expect(store.isSeanceAlreadyValidated()).toBe(false);
  });
  it('isSeanceAlreadyValidated should be true when summary status is VALIDEE', () => {
    mockApi.getSeanceSummary.mockReturnValueOnce(of({
      ...MOCK_SUMMARY,
      seance: {...MOCK_SUMMARY.seance, status: 'VALIDEE'}
    }));
    const store = TestBed.inject(SeanceStore);
    store.loadSeanceSummary({seanceId: SEANCE_ID, centerId: CENTER_ID});
    expect(store.isSeanceAlreadyValidated()).toBe(true);
  });
  it('isSeanceAlreadyValidated should be true when summary status is SIGNEE', () => {
    mockApi.getSeanceSummary.mockReturnValueOnce(of({
      ...MOCK_SUMMARY,
      seance: {...MOCK_SUMMARY.seance, status: 'SIGNEE'}
    }));
    const store = TestBed.inject(SeanceStore);
    store.loadSeanceSummary({seanceId: SEANCE_ID, centerId: CENTER_ID});
    expect(store.isSeanceAlreadyValidated()).toBe(true);
  });
  it('should load dashboard and compute stats', () => {
    const store = TestBed.inject(SeanceStore);
    store.loadDashboard({centerId: CENTER_ID, year: 2026, month: 7});
    expect(mockApi.getSeanceMonthlyDashboard).toHaveBeenCalledWith(CENTER_ID, 2026, 7);
    const dashboard = store.seanceDashboard();
    expect(dashboard).toBeTruthy();
    expect(dashboard?.presenceCount).toBe(40);
    expect(dashboard?.absenceCount).toBe(10);
    expect(dashboard?.expectedSeances).toBe(50);
  });
  it('should load dashboard details and populate items', () => {
    const store = TestBed.inject(SeanceStore);
    store.openDashboardDetails('presence');
    store.loadDashboardDetails({centerId: CENTER_ID, year: 2026, month: 7, kind: 'presence'});
    expect(store.dashboardDetailsOpen()).toBe(true);
    expect(store.dashboardDetailItems().length).toBe(2);
    expect(store.dashboardDetailsLoading()).toBe(false);
  });
  it('dashboardDetailTotalPages should be 1 when items <= page size', () => {
    const store = TestBed.inject(SeanceStore);
    store.openDashboardDetails('presence');
    store.loadDashboardDetails({centerId: CENTER_ID, year: 2026, month: 7, kind: 'presence'});
    expect(store.dashboardDetailTotalPages()).toBe(1);
  });
  it('closeDashboardDetails should set dashboardDetailsOpen to false', () => {
    const store = TestBed.inject(SeanceStore);
    store.openDashboardDetails('absence');
    expect(store.dashboardDetailsOpen()).toBe(true);
    store.closeDashboardDetails();
    expect(store.dashboardDetailsOpen()).toBe(false);
  });
  it('should load journal by date', () => {
    const store = TestBed.inject(SeanceStore);
    store.loadJournal({centerId: CENTER_ID, date: '2026-07-24'});
    expect(mockApi.getSeanceJournalByDate).toHaveBeenCalledWith(CENTER_ID, '2026-07-24');
    expect(store.journalPatients()).toEqual([]);
    expect(store.journalArticles()).toEqual([]);
    expect(store.journalLoading()).toBe(false);
  });
  it('should load calendar holidays and closures', () => {
    mockApi.getSeanceCalendar.mockReturnValueOnce(of({
      year: 2026, month: 7,
      holidays: [{id: 'h1', dayDate: '2026-07-05', label: 'Fete'}],
      closures: [],
    }));
    const store = TestBed.inject(SeanceStore);
    store.loadCalendar({centerId: CENTER_ID, year: 2026, month: 7});
    expect(store.calendarHolidays().length).toBe(1);
    expect(store.calendarHolidays()[0].label).toBe('Fete');
    expect(store.calendarClosures().length).toBe(0);
  });
  it('should add holiday and reset label', () => {
    const store = TestBed.inject(SeanceStore);
    store.setNewHolidayLabel('Fete nationale');
    store.addHoliday({centerId: CENTER_ID, date: '2026-07-05', label: 'Fete nationale'});
    expect(mockApi.addSeanceHoliday).toHaveBeenCalledWith(CENTER_ID, '2026-07-05', 'Fete nationale');
    expect(store.newHolidayLabel()).toBe('');
  });
  it('should use centerId from loadSeances argument (not hardcoded)', () => {
    const store = TestBed.inject(SeanceStore);
    const otherCenter = '22222222-2222-2222-2222-222222222222';
    store.loadSeances({centerId: CENTER_ID});
    store.loadSeances({centerId: otherCenter});
    expect(mockApi.listSeances).toHaveBeenCalledWith(CENTER_ID);
    expect(mockApi.listSeances).toHaveBeenCalledWith(otherCenter);
  });
  it('selectSeance should update selectedSeanceId', () => {
    const store = TestBed.inject(SeanceStore);
    store.selectSeance(SEANCE_ID);
    expect(store.selectedSeanceId()).toBe(SEANCE_ID);
  });
  it('clearSummary should reset selection and summary', () => {
    const store = TestBed.inject(SeanceStore);
    store.selectSeance(SEANCE_ID);
    store.clearSummary();
    expect(store.selectedSeanceId()).toBeNull();
    expect(store.summary()).toBeNull();
  });
  it('selectedPreview should be null when no seance selected', () => {
    const store = TestBed.inject(SeanceStore);
    expect(store.selectedPreview()).toBeNull();
  });

  // ── Consommables ──────────────────────────────────────────────────
  it('should start with empty consommables', () => {
    const store = TestBed.inject(SeanceStore);
    expect(store.consommables()).toEqual([]);
  });

  it('should load articles stock and populate availableArticles', () => {
    const store = TestBed.inject(SeanceStore);
    store.loadArticlesStock({centerId: CENTER_ID});
    expect(mockApi.listArticlesStock).toHaveBeenCalledWith(CENTER_ID);
    expect(store.availableArticles().length).toBe(2);
    expect(store.availableArticles()[0].code).toBe('FLT-001');
  });

  it('should add a consommable to the list', () => {
    const store = TestBed.inject(SeanceStore);
    store.loadArticlesStock({centerId: CENTER_ID});
    const article = store.availableArticles()[0];
    store.addConsommable(article, 3);
    expect(store.consommables().length).toBe(1);
    expect(store.consommables()[0].articleId).toBe('art1');
    expect(store.consommables()[0].quantite).toBe(3);
    // Input fields should be reset
    expect(store.newConsommableArticleId()).toBe('');
    expect(store.newConsommableQuantite()).toBeNull();
  });

  it('should accumulate quantity when adding same article twice', () => {
    const store = TestBed.inject(SeanceStore);
    store.loadArticlesStock({centerId: CENTER_ID});
    const article = store.availableArticles()[0];
    store.addConsommable(article, 2);
    store.addConsommable(article, 5);
    expect(store.consommables().length).toBe(1);
    expect(store.consommables()[0].quantite).toBe(7);
  });

  it('should add separate rows for different articles', () => {
    const store = TestBed.inject(SeanceStore);
    store.loadArticlesStock({centerId: CENTER_ID});
    store.addConsommable(store.availableArticles()[0], 2);
    store.addConsommable(store.availableArticles()[1], 3);
    expect(store.consommables().length).toBe(2);
  });

  it('should remove a consommable by articleId', () => {
    const store = TestBed.inject(SeanceStore);
    store.loadArticlesStock({centerId: CENTER_ID});
    store.addConsommable(store.availableArticles()[0], 2);
    store.addConsommable(store.availableArticles()[1], 3);
    store.removeConsommable('art1');
    expect(store.consommables().length).toBe(1);
    expect(store.consommables()[0].articleId).toBe('art2');
  });

  it('should clear all consommables', () => {
    const store = TestBed.inject(SeanceStore);
    store.loadArticlesStock({centerId: CENTER_ID});
    store.addConsommable(store.availableArticles()[0], 2);
    store.clearConsommables();
    expect(store.consommables()).toEqual([]);
  });

  it('should clear consommables after successful seance validation', () => {
    const store = TestBed.inject(SeanceStore);
    store.loadArticlesStock({centerId: CENTER_ID});
    store.addConsommable(store.availableArticles()[0], 2);
    expect(store.consommables().length).toBe(1);
    store.validateSeance({
      seanceId: SEANCE_ID,
      payload: {centerId: CENTER_ID, userId: 'inf-01', consommations: [{articleId: 'art1', quantite: 2}]}
    });
    expect(store.consommables()).toEqual([]);
    expect(store.validatingSeance()).toBe(false);
  });

  it('should not add consommable with zero or negative quantity', () => {
    const store = TestBed.inject(SeanceStore);
    store.loadArticlesStock({centerId: CENTER_ID});
    store.addConsommable(store.availableArticles()[0], 0);
    store.addConsommable(store.availableArticles()[0], -1);
    expect(store.consommables()).toEqual([]);
  });

  it('isSeanceAlreadyValidated should be false when status is CREE', () => {
    const store = TestBed.inject(SeanceStore);
    store.loadSeanceSummary({seanceId: SEANCE_ID, centerId: CENTER_ID});
    expect(store.isSeanceAlreadyValidated()).toBe(false);
  });
});
