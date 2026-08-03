import {TestBed} from '@angular/core/testing';
import {vi} from 'vitest';
import {FacturationWorkspaceComponent} from './facturation-workspace.component';
import {FacturationStore} from './state/facturation.store';
import {AppShellStore} from '../../core/state/app-shell.store';
import {AuthStore} from '../../core/state/auth.store';
import {TranslateService} from '@ngx-translate/core';

describe('FacturationWorkspaceComponent', () => {
  const facturationStoreMock = {
    month: vi.fn(() => '2026-08'),
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

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      providers: [
        {provide: FacturationStore, useValue: facturationStoreMock},
        {provide: AppShellStore, useValue: appShellMock},
        {provide: AuthStore, useValue: authMock},
        {provide: TranslateService, useValue: translateMock},
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

    component['onValidateFacturation']();

    expect(facturationStoreMock.validateFacturation).toHaveBeenCalledWith({
      centerId: '11111111-1111-1111-1111-111111111111',
      userId: 'billing.user',
    });
  });
});
