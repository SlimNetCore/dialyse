import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {provideZonelessChangeDetection} from '@angular/core';
import {DashboardStore} from './dashboard.store';
import {BackendApiService} from '../../../core/api/backend-api.service';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {of} from 'rxjs';
import {DashboardStats} from './dashboard.types';

const CENTER_ID = 'test-center-id';

describe('DashboardStore - Month Filter', () => {
  let store: InstanceType<typeof DashboardStore>;
  let apiServiceMock: { getDashboardStats: ReturnType<typeof vi.fn> };
  let appShellMock: { currentCenterId: ReturnType<typeof vi.fn> };

  const makeStats = (overrides: Partial<DashboardStats> = {}): DashboardStats => ({
    patientCount: 10,
    pecCree: 5,
    pecValidee: 5,
    pecExpiring: 1,
    attestationTotal: 8,
    attestationExpiring: 2,
    ...overrides
  });

  beforeEach(() => {
    apiServiceMock = {getDashboardStats: vi.fn()};
    appShellMock = {currentCenterId: vi.fn(() => CENTER_ID)};

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        DashboardStore,
        {provide: BackendApiService, useValue: apiServiceMock},
        {provide: AppShellStore, useValue: appShellMock}
      ]
    });

    store = TestBed.inject(DashboardStore);
  });

  it('should initialize with null selectedMonth', () => {
    expect(store.selectedMonth()).toBeNull();
  });

  it('should set selectedMonth and trigger API reload with month', () => {
    const stats = makeStats({month: '2024-01'});
    apiServiceMock.getDashboardStats.mockReturnValue(of(stats));

    store.setSelectedMonth('2024-01');

    expect(store.selectedMonth()).toBe('2024-01');
    expect(apiServiceMock.getDashboardStats).toHaveBeenCalledWith(CENTER_ID, 30, '2024-01');
  });

  it('should clear selectedMonth when set to null', () => {
    const stats = makeStats();
    apiServiceMock.getDashboardStats.mockReturnValue(of(stats));

    store.setSelectedMonth(null);

    expect(store.selectedMonth()).toBeNull();
    expect(apiServiceMock.getDashboardStats).toHaveBeenCalledWith(CENTER_ID, 30, null);
  });

  it('should normalize empty string to null', () => {
    const stats = makeStats();
    apiServiceMock.getDashboardStats.mockReturnValue(of(stats));

    store.setSelectedMonth('');

    expect(store.selectedMonth()).toBeNull();
  });

  it('should include month in API response stats', () => {
    const stats = makeStats({month: '2024-06', pecCree: 2});
    apiServiceMock.getDashboardStats.mockReturnValue(of(stats));

    store.setSelectedMonth('2024-06');

    expect(apiServiceMock.getDashboardStats).toHaveBeenCalledWith(CENTER_ID, 30, '2024-06');
    expect(store.stats().pecCree).toBe(2);
  });

  it('should update stats.month from API response', () => {
    const stats = makeStats({month: '2024-05', patientCount: 3});
    apiServiceMock.getDashboardStats.mockReturnValue(of(stats));

    store.setSelectedMonth('2024-05');

    expect(store.stats().month).toBe('2024-05');
    expect(store.stats().patientCount).toBe(3);
  });

  it('should not call API when centerId is missing', () => {
    appShellMock.currentCenterId.mockReturnValue(null);

    store.setSelectedMonth('2024-01');

    expect(apiServiceMock.getDashboardStats).not.toHaveBeenCalled();
  });
});
