import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {SeancesPageComponent} from './seances-page.component';
import {SeanceStore} from './state/seance.store';
import {AppShellStore} from '../../core/state/app-shell.store';
import {AuthStore} from '../../core/state/auth.store';
import {BackendApiService} from '../../core/api/backend-api.service';
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
      calendarHolidays: vi.fn(() => []),
      calendarClosures: vi.fn(() => []),
      newHolidayDate: vi.fn(() => ''),
      newHolidayLabel: vi.fn(() => ''),
      newClosureDate: vi.fn(() => ''),
      newClosureReason: vi.fn(() => ''),
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
      loadJournal: vi.fn(),
      loadDashboard: vi.fn(),
      loadDashboardDetails: vi.fn(),
      openDashboardDetails: vi.fn(),
      closeDashboardDetails: vi.fn(),
      loadCalendar: vi.fn(),
      addHoliday: vi.fn(),
      deleteHoliday: vi.fn(),
      addClosure: vi.fn(),
      deleteClosure: vi.fn(),
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
        {provide: SeanceStore, useValue: storeMock},
        {provide: AppShellStore, useValue: {currentCenterId: () => CENTER_ID}},
        {provide: AuthStore, useValue: {hasRole: (role: string) => role === 'INFIRMIER', username: () => 'inf-01'}},
        {provide: BackendApiService, useValue: {}},
        {provide: TranslateService, useValue: {currentLang: 'fr'}},
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
});




