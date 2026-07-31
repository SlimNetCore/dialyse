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
  [key: string]: unknown;
  selectSeance: ReturnType<typeof vi.fn>;
  loadSeanceSummary: ReturnType<typeof vi.fn>;
  summaryLoading: ReturnType<typeof vi.fn>;
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
});














