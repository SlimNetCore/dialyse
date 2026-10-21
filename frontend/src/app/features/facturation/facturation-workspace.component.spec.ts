import {TestBed} from '@angular/core/testing';
import {vi} from 'vitest';
import {FacturationWorkspaceComponent} from './facturation-workspace.component';
import {FacturationStore} from './state/facturation.store';
import {AppShellStore} from '../../core/state/app-shell.store';
import {AuthStore} from '../../core/state/auth.store';
import {TranslateService} from '@ngx-translate/core';
import {BackendApiService} from '../../core/api/backend-api.service';
import {MatSnackBar} from '@angular/material/snack-bar';
import {of, throwError} from 'rxjs';
import {HttpErrorResponse} from '@angular/common/http';

describe('FacturationWorkspaceComponent', () => {
  const facturationStoreMock = {
    month: vi.fn(() => '2026-08'),
    periodMode: vi.fn(() => 'month'),
    startDate: vi.fn(() => '2026-08-01'),
    endDate: vi.fn(() => '2026-08-31'),
    dashboard: vi.fn(() => null),
    revenueTrend: vi.fn(() => []),
    successMessage: vi.fn(() => null),
    setMonth: vi.fn(),
    loadDashboard: vi.fn(),
    loadRevenueTrend: vi.fn(),
    validateFacturation: vi.fn(),
    removeSeanceFromPreview: vi.fn(),
    reincludeSeanceInPreview: vi.fn(),
    excludedSeances: vi.fn(() => []),
    updateSeanceForfaitInPreview: vi.fn(),
    setActiveCenterId: vi.fn(),
    loadSettings: vi.fn(),
    loadForfaits: vi.fn(),
    forfaits: vi.fn(() => []),
    previewLoading: vi.fn(() => false),
    seanceActionLoadingId: vi.fn(() => null),
    anneePeriodes: vi.fn(() => 2026),
    anneesChargees: vi.fn(() => [2026]),
    moisVises: vi.fn(() => ['2026-08']),
    moisClosVises: vi.fn(() => [] as string[]),
    moisEstClos: vi.fn((mois: string) => mois === '2026-08'),
    loadPeriodes: vi.fn(),
    cloturerMois: vi.fn(),
    rouvrirMois: vi.fn(),
    setAnneePeriodes: vi.fn(),
  };

  const appShellMock = {
    currentCenterId: vi.fn(() => '11111111-1111-1111-1111-111111111111'),
  };

  const authMock = {
    username: vi.fn(() => 'billing.user'),
    hasRole: vi.fn((role: string) => role === 'SECRETAIRE'),
  };

  const translateMock = {
    instant: vi.fn((key: string) => key),
  };

  const apiMock = {
    printFacturationSynthese: vi.fn(() => of(new Blob(['%PDF'], {type: 'application/pdf'}))),
  };

  const snackBarMock = {
    open: vi.fn(),
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      providers: [
        {provide: FacturationStore, useValue: facturationStoreMock},
        {provide: AppShellStore, useValue: appShellMock},
        {provide: AuthStore, useValue: authMock},
        {provide: TranslateService, useValue: translateMock},
        {provide: BackendApiService, useValue: apiMock},
        {provide: MatSnackBar, useValue: snackBarMock},
      ],
    });
  });

  it('recharge le dashboard quand le mois change', () => {
    const component = TestBed.runInInjectionContext(() => new FacturationWorkspaceComponent());

    component['onMonthChange']('2026-09');

    expect(facturationStoreMock.setMonth).toHaveBeenCalledWith('2026-09');
    expect(facturationStoreMock.loadDashboard).toHaveBeenCalledWith({
      centerId: '11111111-1111-1111-1111-111111111111',
      month: '2026-09',
    });
    expect(facturationStoreMock.loadRevenueTrend).toHaveBeenCalledWith({
      centerId: '11111111-1111-1111-1111-111111111111',
      endingMonth: '2026-09',
      months: 6,
    });
  });

  it('propage centerId et userId lors de la validation', () => {
    const component = TestBed.runInInjectionContext(() => new FacturationWorkspaceComponent());
    const dashboardRefreshCallsBeforeValidation = facturationStoreMock.loadDashboard.mock.calls.length;

    component['onValidateFacturation']();

    expect(facturationStoreMock.validateFacturation).toHaveBeenCalledWith({
      centerId: '11111111-1111-1111-1111-111111111111',
      userId: 'billing.user',
    });
    expect(facturationStoreMock.loadDashboard.mock.calls.length).toBe(dashboardRefreshCallsBeforeValidation);
  });

  it('envoie l exclusion avec le motif et le commentaire saisis, et refuse AUTRE sans commentaire', () => {
    const component = TestBed.runInInjectionContext(() => new FacturationWorkspaceComponent());

    component['onConfirmExclusion']();
    expect(facturationStoreMock.removeSeanceFromPreview).not.toHaveBeenCalled();

    component['onOpenExclusion']('seance-1');
    component['onExclusionMotif']('AUTRE');
    expect(component['exclusionValid']()).toBe(false);
    component['onConfirmExclusion']();
    expect(facturationStoreMock.removeSeanceFromPreview).not.toHaveBeenCalled();

    component['onExclusionCommentaire']('Panne du transport');
    expect(component['exclusionValid']()).toBe(true);
    component['onConfirmExclusion']();

    expect(facturationStoreMock.removeSeanceFromPreview).toHaveBeenCalledWith({
      centerId: '11111111-1111-1111-1111-111111111111',
      userId: 'billing.user',
      seanceId: 'seance-1',
      motif: 'AUTRE',
      commentaire: 'Panne du transport',
    });
    expect(component['exclusionDraft']()).toBeNull();
  });

  it('reinclut une seance avec le centre de la session', () => {
    const component = TestBed.runInInjectionContext(() => new FacturationWorkspaceComponent());

    component['onReincludeSeance']('seance-1');

    expect(facturationStoreMock.reincludeSeanceInPreview).toHaveBeenCalledWith({
      centerId: '11111111-1111-1111-1111-111111111111',
      seanceId: 'seance-1',
    });
  });

  it('clôture un mois seulement après confirmation', () => {
    const component = TestBed.runInInjectionContext(() => new FacturationWorkspaceComponent());

    component['onConfirmerCloture']();
    expect(facturationStoreMock.cloturerMois).not.toHaveBeenCalled();

    component['onDemanderCloture']('2026-07');
    expect(facturationStoreMock.cloturerMois).not.toHaveBeenCalled();
    component['onConfirmerCloture']();

    expect(facturationStoreMock.cloturerMois).toHaveBeenCalledWith({
      centerId: '11111111-1111-1111-1111-111111111111',
      mois: '2026-07',
    });
    expect(component['clotureAConfirmer']()).toBeNull();
  });

  it('rouvre un mois avec un motif obligatoire, nettoyé et borné à 500 caractères', () => {
    const component = TestBed.runInInjectionContext(() => new FacturationWorkspaceComponent());

    component['onOuvrirReouverture']('2026-08');
    expect(component['reouvertureValide']()).toBe(false);
    component['onMotifReouverture']('   ');
    expect(component['reouvertureValide']()).toBe(false);
    component['onConfirmerReouverture']();
    expect(facturationStoreMock.rouvrirMois).not.toHaveBeenCalled();

    component['onMotifReouverture']('x'.repeat(501));
    expect(component['reouvertureValide']()).toBe(false);

    component['onMotifReouverture']('  séances oubliées  ');
    expect(component['reouvertureValide']()).toBe(true);
    component['onConfirmerReouverture']();

    expect(facturationStoreMock.rouvrirMois).toHaveBeenCalledWith({
      centerId: '11111111-1111-1111-1111-111111111111',
      mois: '2026-08',
      motif: 'séances oubliées',
    });
    expect(component['reouvertureDraft']()).toBeNull();
  });

  it('bloque les actions d une séance dont le mois est clos (mois de la date de la séance)', () => {
    const component = TestBed.runInInjectionContext(() => new FacturationWorkspaceComponent());

    expect(component['moisDeSeanceClos']('2026-08-12')).toBe(true);
    expect(component['moisDeSeanceClos']('2026-07-31')).toBe(false);
    expect(component['moisDeSeanceClos'](null)).toBe(false);
  });

  it('imprime la synthese mensuelle avec centerId et periode du mois actif', () => {
    const component = TestBed.runInInjectionContext(() => new FacturationWorkspaceComponent());

    component['onPrintMonthlySummary']();

    expect(apiMock.printFacturationSynthese).toHaveBeenCalledWith({
      centerId: '11111111-1111-1111-1111-111111111111',
      periodStart: '2026-08-01',
      periodEnd: '2026-08-31',
      format: 'PDF',
    });
  });

  it('reserve la synthese a ADMIN et SECRETAIRE : refuse l impression pour les autres profils', () => {
    authMock.hasRole.mockImplementation((role: string) => role === 'MEDECIN');
    const component = TestBed.runInInjectionContext(() => new FacturationWorkspaceComponent());

    expect(component['canPrintSynthese']()).toBe(false);
    component['onPrintMonthlySummary']();
    expect(apiMock.printFacturationSynthese).not.toHaveBeenCalled();

    authMock.hasRole.mockImplementation((role: string) => role === 'ADMIN');
    expect(TestBed.runInInjectionContext(() => new FacturationWorkspaceComponent())['canPrintSynthese']()).toBe(true);
    authMock.hasRole.mockImplementation((role: string) => role === 'SECRETAIRE');
  });

  it('affiche le detail du backend quand l impression de la synthese est refusee en 403 (blob d erreur)', async () => {
    const errorBlob = new Blob([JSON.stringify({detail: 'Centre différent de la session'})], {type: 'application/json'});
    apiMock.printFacturationSynthese.mockReturnValueOnce(throwError(() => new HttpErrorResponse({status: 403, error: errorBlob})) as never);
    const component = TestBed.runInInjectionContext(() => new FacturationWorkspaceComponent());

    component['onPrintMonthlySummary']();
    await vi.waitFor(() => expect(snackBarMock.open).toHaveBeenCalled());

    expect(snackBarMock.open.mock.calls[0][0]).toBe('Centre différent de la session');
  });
});
