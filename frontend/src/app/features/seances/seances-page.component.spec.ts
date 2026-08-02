import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {provideZonelessChangeDetection} from '@angular/core';
import {SeancesPageComponent} from './seances-page.component';
import {SeanceStore} from './state/seance.store';
import {AppShellStore} from '../../core/state/app-shell.store';
import {AuthStore} from '../../core/state/auth.store';
import {BackendApiService} from '../../core/api/backend-api.service';
import {WebSocketService} from '../../core/ws/websocket.service';
import {TranslateService} from '@ngx-translate/core';
import {MatSnackBar} from '@angular/material/snack-bar';

const CENTER_ID = '11111111-1111-1111-1111-111111111111';

type SeanceStoreMock = {
  // Propriétés déclarées explicitement (pas d'index signature pour éviter TS4111)
  selectSeance: ReturnType<typeof vi.fn>;
  loadSeanceSummary: ReturnType<typeof vi.fn>;
  summaryLoading: ReturnType<typeof vi.fn>;
  isSeanceAlreadyValidated: ReturnType<typeof vi.fn>;
  seanceDashboard: ReturnType<typeof vi.fn>;
  dashboardMonth: ReturnType<typeof vi.fn>;
  dashboardLoading: ReturnType<typeof vi.fn>;
  dashboardDetailsOpen: ReturnType<typeof vi.fn>;
  dashboardDetailsKind: ReturnType<typeof vi.fn>;
  dashboardDetailsLoading: ReturnType<typeof vi.fn>;
  dashboardDetailItems: ReturnType<typeof vi.fn>;
  dashboardDetailPageIndex: ReturnType<typeof vi.fn>;
  dashboardDetailPageItems: ReturnType<typeof vi.fn>;
  dashboardDetailTotalPages: ReturnType<typeof vi.fn>;
  journalDate: ReturnType<typeof vi.fn>;
  journalLoading: ReturnType<typeof vi.fn>;
  journalPatients: ReturnType<typeof vi.fn>;
  journalArticles: ReturnType<typeof vi.fn>;
  seances: ReturnType<typeof vi.fn>;
  selectedSeanceId: ReturnType<typeof vi.fn>;
  selectedPreview: ReturnType<typeof vi.fn>;
  editDateSeance: ReturnType<typeof vi.fn>;
  taAvant: ReturnType<typeof vi.fn>;
  taApres: ReturnType<typeof vi.fn>;
  poidsAvantKg: ReturnType<typeof vi.fn>;
  poidsApresKg: ReturnType<typeof vi.fn>;
  dureeMinutes: ReturnType<typeof vi.fn>;
  debitSangMlMin: ReturnType<typeof vi.fn>;
  ultrafiltrationMl: ReturnType<typeof vi.fn>;
  anticoagulant: ReturnType<typeof vi.fn>;
  typeDialysat: ReturnType<typeof vi.fn>;
  incidents: ReturnType<typeof vi.fn>;
  prescription: ReturnType<typeof vi.fn>;
  toleranceSeance: ReturnType<typeof vi.fn>;
  examenClinique: ReturnType<typeof vi.fn>;
  resultatsBiologiques: ReturnType<typeof vi.fn>;
  ajustementsTherapeutiques: ReturnType<typeof vi.fn>;
  conclusionMedicale: ReturnType<typeof vi.fn>;
  scanState: ReturnType<typeof vi.fn>;
  scanMessage: ReturnType<typeof vi.fn>;
  scanning: ReturnType<typeof vi.fn>;
  qrCode?: ReturnType<typeof vi.fn>;
  setQrCode: ReturnType<typeof vi.fn>;
  setDateSeance: ReturnType<typeof vi.fn>;
  setEditDateSeance: ReturnType<typeof vi.fn>;
  setJournalDate: ReturnType<typeof vi.fn>;
  setDashboardMonth: ReturnType<typeof vi.fn>;
  setNewHolidayDate: ReturnType<typeof vi.fn>;
  setNewHolidayLabel: ReturnType<typeof vi.fn>;
  setNewClosureDate: ReturnType<typeof vi.fn>;
  setNewClosureReason: ReturnType<typeof vi.fn>;
  patchParamedical: ReturnType<typeof vi.fn>;
  patchMedical: ReturnType<typeof vi.fn>;
  scanQr: ReturnType<typeof vi.fn>;
  saveDate: ReturnType<typeof vi.fn>;
  saveParamedical: ReturnType<typeof vi.fn>;
  saveMedical: ReturnType<typeof vi.fn>;
  validateSeance: ReturnType<typeof vi.fn>;
  removeConsommable: ReturnType<typeof vi.fn>;
  addConsommable?: ReturnType<typeof vi.fn>;
  loadJournal: ReturnType<typeof vi.fn>;
  loadDashboard: ReturnType<typeof vi.fn>;
  loadDashboardDetails: ReturnType<typeof vi.fn>;
  openDashboardDetails: ReturnType<typeof vi.fn>;
  closeDashboardDetails: ReturnType<typeof vi.fn>;
  loadSeances: ReturnType<typeof vi.fn>;
  clearSummary: ReturnType<typeof vi.fn>;
  clearError: ReturnType<typeof vi.fn>;
  error: ReturnType<typeof vi.fn>;
  summary?: ReturnType<typeof vi.fn>;
  consommables?: ReturnType<typeof vi.fn>;
  saveDateLoading: ReturnType<typeof vi.fn>;
  savingDate: ReturnType<typeof vi.fn>;
  savingParamedical: ReturnType<typeof vi.fn>;
  savingMedical: ReturnType<typeof vi.fn>;
  validatingSeance: ReturnType<typeof vi.fn>;
  newConsommableArticleId?: ReturnType<typeof vi.fn>;
  newConsommableQuantite?: ReturnType<typeof vi.fn>;
  availableArticles?: ReturnType<typeof vi.fn>;
};

describe('SeancesPageComponent', () => {
  let storeMock: SeanceStoreMock;

  beforeEach(async () => {
    storeMock = {
      selectSeance: vi.fn(),
      loadSeanceSummary: vi.fn(),
      summaryLoading: vi.fn(() => false),
      isSeanceAlreadyValidated: vi.fn(() => false),
      seanceDashboard: vi.fn(() => null),
      dashboardMonth: vi.fn(() => '2026-07'),
      dashboardLoading: vi.fn(() => false),
      dashboardDetailsOpen: vi.fn(() => false),
      dashboardDetailsKind: vi.fn(() => 'presence'),
      dashboardDetailsLoading: vi.fn(() => false),
      dashboardDetailItems: vi.fn(() => []),
      dashboardDetailPageIndex: vi.fn(() => 0),
      dashboardDetailPageItems: vi.fn(() => []),
      dashboardDetailTotalPages: vi.fn(() => 1),
      journalDate: vi.fn(() => '2026-07-24'),
      journalLoading: vi.fn(() => false),
      journalPatients: vi.fn(() => []),
      journalArticles: vi.fn(() => []),
      seances: vi.fn(() => []),
      selectedSeanceId: vi.fn(() => null),
      selectedPreview: vi.fn(() => null),
      editDateSeance: vi.fn(() => '2026-07-24'),
      taAvant: vi.fn(() => ''),
      taApres: vi.fn(() => ''),
      poidsAvantKg: vi.fn(() => null),
      poidsApresKg: vi.fn(() => null),
      dureeMinutes: vi.fn(() => null),
      debitSangMlMin: vi.fn(() => null),
      ultrafiltrationMl: vi.fn(() => null),
      anticoagulant: vi.fn(() => ''),
      typeDialysat: vi.fn(() => ''),
      incidents: vi.fn(() => ''),
      prescription: vi.fn(() => ''),
      toleranceSeance: vi.fn(() => ''),
      examenClinique: vi.fn(() => ''),
      resultatsBiologiques: vi.fn(() => ''),
      ajustementsTherapeutiques: vi.fn(() => ''),
      conclusionMedicale: vi.fn(() => ''),
      scanState: vi.fn(() => 'idle'),
      scanMessage: vi.fn(() => 'Prêt à scanner'),
      scanning: vi.fn(() => false),
      setQrCode: vi.fn(),
      setDateSeance: vi.fn(),
      setEditDateSeance: vi.fn(),
      setJournalDate: vi.fn(),
      setDashboardMonth: vi.fn(),
      setNewHolidayDate: vi.fn(),
      setNewHolidayLabel: vi.fn(),
      setNewClosureDate: vi.fn(),
      setNewClosureReason: vi.fn(),
      patchParamedical: vi.fn(),
      patchMedical: vi.fn(),
      scanQr: vi.fn(),
      saveDate: vi.fn(),
      saveParamedical: vi.fn(),
      saveMedical: vi.fn(),
      validateSeance: vi.fn(),
      removeConsommable: vi.fn(),
      loadJournal: vi.fn(),
      loadDashboard: vi.fn(),
      loadDashboardDetails: vi.fn(),
      openDashboardDetails: vi.fn(),
      closeDashboardDetails: vi.fn(),
      loadSeances: vi.fn(),
      clearSummary: vi.fn(),
      clearError: vi.fn(),
      error: vi.fn(() => null),
      summary: vi.fn(() => null),
      saveDateLoading: vi.fn(() => false),
      savingDate: vi.fn(() => false),
      savingParamedical: vi.fn(() => false),
      savingMedical: vi.fn(() => false),
      validatingSeance: vi.fn(() => false),
    };

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        {provide: SeanceStore, useValue: storeMock},
        {provide: AppShellStore, useValue: {currentCenterId: () => CENTER_ID}},
        {provide: AuthStore, useValue: {hasRole: (role: string) => role === 'INFIRMIER', username: () => 'inf-01'}},
        {provide: BackendApiService, useValue: {}},
        {provide: WebSocketService, useValue: {lastEvent: () => null}},
        {provide: TranslateService, useValue: {currentLang: 'fr', get: vi.fn(), instant: (key: string) => key}},
        {provide: MatSnackBar, useValue: {open: vi.fn()}},
      ],
    });
  });

  it('should load seance summary when infirmier clicks modify', () => {
    const component = TestBed.runInInjectionContext(() => new SeancesPageComponent());

    component['selectSeance']({
      id: 'seance-123',
      centerId: CENTER_ID,
      patientId: 'patient-1',
      patientCode: 'PAT-001',
      patientNom: 'Dupont',
      patientPrenom: 'Jean',
      dateSeance: '2026-07-24',
      status: 'CREE',
      createdAt: undefined,
      validatedAt: undefined,
      signedByInfirmierAt: undefined,
      signedByMedecinAt: undefined,
    } as any);

    expect(storeMock.selectSeance).toHaveBeenCalledWith('seance-123');
    expect(storeMock.loadSeanceSummary).toHaveBeenCalledWith({seanceId: 'seance-123', centerId: CENTER_ID});
  });

  it('should render current forfait pill when seance summary contains forfait', async () => {
    storeMock['summary'] = vi.fn(() => ({
      seance: {id: 'seance-123', centerId: CENTER_ID, patientId: 'patient-1', dateSeance: '2026-07-24', status: 'CREE'},
      patient: {id: 'patient-1', codePatient: 'PAT-001', nom: 'Dupont', prenom: 'Jean', numeroAssurance: 'ASS-001'},
      paramedical: null,
      medical: null,
      forfait: {id: 'forfait-1', code: 'F001', nom: 'Forfait hémodialyse', prix: 3500},
    }));

    const component = TestBed.runInInjectionContext(() => new SeancesPageComponent());

    expect(component['currentForfaitName']()).toBe('Forfait hémodialyse');
    expect(component['currentForfaitPrice']()).toBe('3 500,00');
  });

  it('should format forfait label and price in seances list helpers', () => {
    const component = TestBed.runInInjectionContext(() => new SeancesPageComponent());
    const row = {
      id: 'seance-1',
      centerId: CENTER_ID,
      patientId: 'patient-1',
      dateSeance: '2026-07-25',
      status: 'CREE',
      forfait: {
        id: 'forfait-1',
        code: 'F001',
        nom: 'Forfait HD',
        prix: 3500,
      },
    } as any;

    expect(component['listForfaitName'](row)).toBe('Forfait HD');
    expect(component['listForfaitPrice'](row)).toBe('3 500,00');
  });

  it('should always scan with today date', () => {
    const component = TestBed.runInInjectionContext(() => new SeancesPageComponent());
    const expectedToday = new Date().toISOString().slice(0, 10);

    storeMock['qrCode'] = vi.fn(() => 'PAT-001');

    component['scanQr']();

    expect(storeMock.setDateSeance).toHaveBeenCalledWith(expectedToday);
    expect(storeMock.scanQr).toHaveBeenCalledWith({
      centerId: CENTER_ID,
      qrCode: 'PAT-001',
    });
  });

  it('should save paramedical without validating when leaving tab', () => {
    const component = TestBed.runInInjectionContext(() => new SeancesPageComponent());

    storeMock['summary'] = vi.fn(() => ({
      seance: {id: 'seance-123', centerId: CENTER_ID, patientId: 'patient-1', dateSeance: '2026-07-31', status: 'CREE'},
    }));

    component['onTabChange'](1);

    expect(storeMock.saveParamedical).toHaveBeenCalledTimes(1);
    expect(storeMock.validateSeance).not.toHaveBeenCalled();
  });

  it('should validate seance only when saving paramedical explicitly', async () => {
    vi.useFakeTimers();
    const component = TestBed.runInInjectionContext(() => new SeancesPageComponent());

    storeMock['summary'] = vi.fn(() => ({
      seance: {id: 'seance-123', centerId: CENTER_ID, patientId: 'patient-1', dateSeance: '2026-07-31', status: 'CREE'},
    }));
    storeMock['consommables'] = vi.fn(() => []);

    component['saveParamedical']();
    vi.advanceTimersByTime(500);

    expect(storeMock.saveParamedical).toHaveBeenCalledTimes(1);
    expect(storeMock.validateSeance).toHaveBeenCalledTimes(1);
    vi.useRealTimers();
  });

  it('should add selected consommable article', () => {
    const component = TestBed.runInInjectionContext(() => new SeancesPageComponent());

    storeMock['newConsommableArticleId'] = vi.fn(() => 'article-1');
    storeMock['newConsommableQuantite'] = vi.fn(() => 2);
    storeMock['availableArticles'] = vi.fn(() => [
      {id: 'article-1', code: 'ART-001', libelle: 'Dialyseur', unite: 'u'},
    ]);
    storeMock['addConsommable'] = vi.fn();

    component['addConsommable']();

    expect(storeMock.addConsommable).toHaveBeenCalledWith(
      {id: 'article-1', code: 'ART-001', libelle: 'Dialyseur', unite: 'u'},
      2,
    );
  });

  it('should remove consommable article', () => {
    const component = TestBed.runInInjectionContext(() => new SeancesPageComponent());

    component['removeConsommable']('article-1');

    expect(storeMock.removeConsommable).toHaveBeenCalledWith('article-1');
  });

  it('should lock edition when seance status is FACTUREE', () => {
    const component = TestBed.runInInjectionContext(() => new SeancesPageComponent());

    storeMock['summary'] = vi.fn(() => ({
      seance: {
        id: 'seance-123',
        centerId: CENTER_ID,
        patientId: 'patient-1',
        dateSeance: '2026-07-31',
        status: 'FACTUREE'
      },
    }));

    expect(component['canEditParamedical']()).toBe(false);
    expect(component['canEditMedical']()).toBe(false);
    expect(component['canEditDate']()).toBe(false);
  });

  it('should allow consommables validation action when seance status is VALIDEE', () => {
    const component = TestBed.runInInjectionContext(() => new SeancesPageComponent());

    storeMock['summary'] = vi.fn(() => ({
      seance: {
        id: 'seance-123',
        centerId: CENTER_ID,
        patientId: 'patient-1',
        dateSeance: '2026-07-31',
        status: 'VALIDEE'
      },
    }));
    storeMock['consommables'] = vi.fn(() => [{articleId: 'article-1', quantite: 1}]);

    component['validateSeanceParamedical']();

    expect(storeMock.validateSeance).toHaveBeenCalledTimes(1);
  });

  it('should format article label from existing consommables when article catalog is missing', () => {
    const component = TestBed.runInInjectionContext(() => new SeancesPageComponent());

    storeMock['availableArticles'] = vi.fn(() => []);
    storeMock['consommables'] = vi.fn(() => [
      {
        articleId: 'article-x',
        articleCode: 'ART-X',
        articleLibelle: 'Article historique',
        articleUnite: 'u',
        quantite: 1
      },
    ]);

    expect(component['articleLabel']('article-x')).toBe('[ART-X] Article historique');
  });
});














