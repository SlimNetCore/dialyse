import {TestBed} from '@angular/core/testing';
import {vi} from 'vitest';
import {FacturationWorkspaceComponent} from './facturation-workspace.component';
import {FacturationStore} from './state/facturation.store';
import {AppShellStore} from '../../core/state/app-shell.store';
import {AuthStore} from '../../core/state/auth.store';
import {TranslateService} from '@ngx-translate/core';
import {BackendApiService} from '../../core/api/backend-api.service';
import {MatSnackBar} from '@angular/material/snack-bar';
import {of} from 'rxjs';

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
    setActiveCenterId: vi.fn(),
    loadSettings: vi.fn(),
  };

  const appShellMock = {
    currentCenterId: vi.fn(() => '11111111-1111-1111-1111-111111111111'),
  };

  const authMock = {
    username: vi.fn(() => 'billing.user'),
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
});
