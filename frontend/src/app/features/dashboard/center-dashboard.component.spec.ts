import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {provideZonelessChangeDetection, signal} from '@angular/core';
import {CenterDashboardComponent} from './center-dashboard.component';
import {DashboardStore} from './state/dashboard.store';
import {WebSocketService} from '../../core/ws/websocket.service';
import {TranslateModule} from '@ngx-translate/core';

describe('CenterDashboardComponent - Month Filter', () => {
  let component: CenterDashboardComponent;
  let dashboardStoreMock: {
    loading: ReturnType<typeof signal<boolean>>;
    stats: ReturnType<typeof signal<object>>;
    expirationDays: ReturnType<typeof signal<number>>;
    selectedMonth: ReturnType<typeof signal<string | null>>;
    loadInitial: ReturnType<typeof vi.fn>;
    setExpirationDays: ReturnType<typeof vi.fn>;
    setSelectedMonth: ReturnType<typeof vi.fn>;
    applyWsEvent: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    dashboardStoreMock = {
      loading: signal(false),
      stats: signal({
        patientCount: 10,
        pecCree: 5,
        pecValidee: 5,
        pecExpiring: 1,
        attestationTotal: 8,
        attestationExpiring: 2,
        month: undefined
      }),
      expirationDays: signal(30),
      selectedMonth: signal(null),
      loadInitial: vi.fn(),
      setExpirationDays: vi.fn(),
      setSelectedMonth: vi.fn(),
      applyWsEvent: vi.fn()
    };

    await TestBed.configureTestingModule({
      imports: [CenterDashboardComponent, TranslateModule.forRoot()],
      providers: [
        provideZonelessChangeDetection(),
        {provide: DashboardStore, useValue: dashboardStoreMock},
        {provide: WebSocketService, useValue: {lastEvent: signal(null)}}
      ]
    }).compileComponents();

    const fixture = TestBed.createComponent(CenterDashboardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should initialize dashboardFormState with expirationDays and null selectedMonth', () => {
    expect(component.dashboardFormState().expirationDays).toBe(30);
    expect(component.dashboardFormState().selectedMonth).toBeNull();
  });

  it('should call loadInitial on ngOnInit', () => {
    expect(dashboardStoreMock.loadInitial).toHaveBeenCalled();
  });

  it('should propagate selectedMonth change to store', async () => {
    component.dashboardFormState.set({
      expirationDays: 30,
      selectedMonth: '2024-06'
    });
    TestBed.flushEffects();
    expect(dashboardStoreMock.setSelectedMonth).toHaveBeenCalledWith('2024-06');
  });

  it('should propagate null selectedMonth to store', async () => {
    component.dashboardFormState.set({
      expirationDays: 30,
      selectedMonth: null
    });
    TestBed.flushEffects();
    expect(dashboardStoreMock.setSelectedMonth).toHaveBeenCalledWith(null);
  });
});
