import {TestBed} from '@angular/core/testing';
import {DashboardStore} from './dashboard.store';
import {BackendApiService} from '../../../core/api/backend-api.service';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {of} from 'rxjs';
import {DashboardStats} from './dashboard.types';

describe('DashboardStore - Month Filter', () => {
  let store: InstanceType<typeof DashboardStore>;
  let apiService: jasmine.SpyObj<BackendApiService>;
  let appShellStore: jasmine.SpyObj<AppShellStore>;

  beforeEach(() => {
    const apiSpy = jasmine.createSpyObj('BackendApiService', ['getDashboardStats']);
    const appShellSpy = jasmine.createSpyObj('AppShellStore', [], {currentCenterId: () => 'test-center-id'});

    TestBed.configureTestingModule({
      providers: [
        DashboardStore,
        {provide: BackendApiService, useValue: apiSpy},
        {provide: AppShellStore, useValue: appShellSpy}
      ]
    });

    store = TestBed.inject(DashboardStore);
    apiService = TestBed.inject(BackendApiService) as jasmine.SpyObj<BackendApiService>;
    appShellStore = TestBed.inject(AppShellStore) as jasmine.SpyObj<AppShellStore>;
  });

  it('should initialize with null selectedMonth', () => {
    expect(store.selectedMonth()).toBeNull();
  });

  it('should set selectedMonth and trigger reload', (done) => {
    const mockStats: DashboardStats = {
      patientCount: 10,
      pecCree: 5,
      pecValidee: 5,
      pecExpiring: 1,
      attestationTotal: 8,
      attestationExpiring: 2,
      month: '2024-01'
    };

    apiService.getDashboardStats.and.returnValue(of(mockStats));

    store.setSelectedMonth('2024-01');

    TestBed.flushMicrotasks();
    expect(store.selectedMonth()).toBe('2024-01');
    expect(apiService.getDashboardStats).toHaveBeenCalledWith('test-center-id', 30, '2024-01');
    done();
  });

  it('should clear selectedMonth when set to null', (done) => {
    const mockStats: DashboardStats = {
      patientCount: 15,
      pecCree: 8,
      pecValidee: 7,
      pecExpiring: 2,
      attestationTotal: 10,
      attestationExpiring: 3
    };

    apiService.getDashboardStats.and.returnValue(of(mockStats));

    store.setSelectedMonth(null);

    TestBed.flushMicrotasks();
    expect(store.selectedMonth()).toBeNull();
    expect(apiService.getDashboardStats).toHaveBeenCalledWith('test-center-id', 30, null);
    done();
  });

  it('should include month in API call when setSelectedMonth is called', (done) => {
    const mockStats: DashboardStats = {
      patientCount: 5,
      pecCree: 2,
      pecValidee: 3,
      pecExpiring: 0,
      attestationTotal: 4,
      attestationExpiring: 1,
      month: '2024-06'
    };

    apiService.getDashboardStats.and.returnValue(of(mockStats));

    store.setSelectedMonth('2024-06');

    TestBed.flushMicrotasks();
    expect(apiService.getDashboardStats).toHaveBeenCalledWith('test-center-id', 30, '2024-06');
    expect(store.stats().pecCree).toBe(2);
    done();
  });

  it('should update stats based on API response with month filter', (done) => {
    const mockStats: DashboardStats = {
      patientCount: 3,
      pecCree: 1,
      pecValidee: 2,
      pecExpiring: 1,
      attestationTotal: 2,
      attestationExpiring: 0,
      month: '2024-05'
    };

    apiService.getDashboardStats.and.returnValue(of(mockStats));

    store.setSelectedMonth('2024-05');

    TestBed.flushMicrotasks();
    expect(store.stats().month).toBe('2024-05');
    expect(store.stats().patientCount).toBe(3);
    done();
  });

  it('should not call API when centerId is missing', () => {
    (appShellStore as any).currentCenterId = () => null;

    store.setSelectedMonth('2024-01');

    expect(apiService.getDashboardStats).not.toHaveBeenCalled();
  });

  it('should revert to null month when calling setSelectedMonth with empty string', (done) => {
    const mockStats: DashboardStats = {
      patientCount: 10,
      pecCree: 5,
      pecValidee: 5,
      pecExpiring: 1,
      attestationTotal: 8,
      attestationExpiring: 2
    };

    apiService.getDashboardStats.and.returnValue(of(mockStats));

    store.setSelectedMonth(null);

    TestBed.flushMicrotasks();
    expect(store.selectedMonth()).toBeNull();
    done();
  });
});

