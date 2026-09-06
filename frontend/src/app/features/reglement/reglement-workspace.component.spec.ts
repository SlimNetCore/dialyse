import {TestBed} from '@angular/core/testing';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TranslateService} from '@ngx-translate/core';
import {ReglementWorkspaceComponent} from './reglement-workspace.component';
import {ReglementStore} from './state/reglement.store';
import {AppShellStore} from '../../core/state/app-shell.store';
import {AuthStore} from '../../core/state/auth.store';

describe('ReglementWorkspaceComponent', () => {
  const storeMock = {
    year: vi.fn(() => 2026),
    month: vi.fn(() => 8),
    caisseId: vi.fn(() => null),
    agenceId: vi.fn(() => null),
    centrePayeurId: vi.fn(() => null),
    rows: vi.fn(() => []),
    total: vi.fn(() => 0),
    pageIndex: vi.fn(() => 0),
    pageSize: vi.fn(() => 20),
    loading: vi.fn(() => false),
    dashboardLoading: vi.fn(() => false),
    referentialsLoading: vi.fn(() => false),
    error: vi.fn(() => null),
    successMessage: vi.fn(() => null),
    dashboard: vi.fn(() => null),
    dashboardCards: vi.fn(() => ({
      totalFactures: 0,
      reglees: 0,
      nonReglees: 0,
      partiellementReglees: 0,
      tropPercus: 0,
      totalFacture: 0,
      totalRegle: 0,
      totalReste: 0,
      totalTropPercu: 0,
      tauxEncaissement: 0,
    })),
    statusBreakdown: vi.fn(() => []),
    caisses: vi.fn(() => []),
    agences: vi.fn(() => []),
    centresPayeurs: vi.fn(() => []),
    savingFactureIds: vi.fn(() => []),
    setYear: vi.fn(),
    setMonth: vi.fn(),
    setCaisseId: vi.fn(),
    setAgenceId: vi.fn(),
    setCentrePayeurId: vi.fn(),
    setPagination: vi.fn(),
    clearFilters: vi.fn(),
    clearMessages: vi.fn(),
    loadPage: vi.fn(),
    loadDashboard: vi.fn(),
    setPaymentDraft: vi.fn(),
    batchSavePayments: vi.fn(),
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      providers: [
        {provide: ReglementStore, useValue: storeMock},
        {provide: AppShellStore, useValue: {currentCenterId: vi.fn(() => '11111111-1111-1111-1111-111111111111')}},
        {provide: AuthStore, useValue: {username: vi.fn(() => 'sec-user')}},
        {
          provide: TranslateService,
          useValue: {instant: vi.fn((key: string, params?: Record<string, unknown>) => params?.['amount'] ? `${key}:${params['amount']}` : key)}
        },
      ],
    });
  });

  it('propage les filtres année/mois au store', () => {
    const component = TestBed.runInInjectionContext(() => new ReglementWorkspaceComponent());
    component['onYearChange']('2025');
    component['onMonthChange']('7');

    expect(storeMock.setYear).toHaveBeenCalledWith(2025);
    expect(storeMock.setMonth).toHaveBeenCalledWith(7);
  });

  it('enregistre les brouillons de paiement puis déclenche la sauvegarde batch avec centerId et userId', () => {
    const component = TestBed.runInInjectionContext(() => new ReglementWorkspaceComponent());
    component['onPaymentInput']('fac-1', '1000');
    component['onBatchSave']();

    expect(storeMock.setPaymentDraft).toHaveBeenCalledWith('fac-1', '1000');
    expect(storeMock.batchSavePayments).toHaveBeenCalledWith({
      centerId: '11111111-1111-1111-1111-111111111111',
      userId: 'sec-user',
    });
  });
});

