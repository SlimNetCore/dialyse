import {ComponentFixture, TestBed} from '@angular/core/testing';
import {CenterDashboardComponent} from './center-dashboard.component';
import {DashboardStore} from './state/dashboard.store';
import {WebSocketService} from '../../core/ws/websocket.service';
import {TranslateModule} from '@ngx-translate/core';
import {signal} from '@angular/core';

describe('CenterDashboardComponent - Month Filter', () => {
  let component: CenterDashboardComponent;
  let fixture: ComponentFixture<CenterDashboardComponent>;
  let dashboardStore: jasmine.SpyObj<DashboardStore>;
  let wsService: jasmine.SpyObj<WebSocketService>;

  beforeEach(async () => {
    const dashboardStoreSpy = jasmine.createSpyObj(
      'DashboardStore',
      ['loadInitial', 'setExpirationDays', 'setSelectedMonth', 'applyWsEvent'],
      {
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
        selectedMonth: signal(null)
      }
    );

    const wsServiceSpy = jasmine.createSpyObj('WebSocketService', [], {
      lastEvent: signal(null)
    });

    await TestBed.configureTestingModule({
      imports: [CenterDashboardComponent, TranslateModule.forRoot()],
      providers: [
        {provide: DashboardStore, useValue: dashboardStoreSpy},
        {provide: WebSocketService, useValue: wsServiceSpy}
      ]
    }).compileComponents();

    dashboardStore = TestBed.inject(DashboardStore) as jasmine.SpyObj<DashboardStore>;
    wsService = TestBed.inject(WebSocketService) as jasmine.SpyObj<WebSocketService>;

    fixture = TestBed.createComponent(CenterDashboardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should initialize dashboardFormState with expirationDays and selectedMonth', () => {
    expect(component.dashboardFormState().expirationDays).toBe(30);
    expect(component.dashboardFormState().selectedMonth).toBeNull();
  });

  it('should display month filter field in template', () => {
    const compiled = fixture.nativeElement;
    const monthInput = compiled.querySelector('input[type="month"]');
    expect(monthInput).toBeTruthy();
  });

  it('should update store selectedMonth when form month field changes', (done) => {
    component.dashboardFormState.set({
      expirationDays: 30,
      selectedMonth: '2024-06'
    });

    fixture.detectChanges();
    fixture.whenStable().then(() => {
      expect(dashboardStore.setSelectedMonth).toHaveBeenCalledWith('2024-06');
      done();
    });
  });

  it('should update store when selectedMonth is cleared', (done) => {
    component.dashboardFormState.set({
      expirationDays: 30,
      selectedMonth: null
    });

    fixture.detectChanges();
    fixture.whenStable().then(() => {
      expect(dashboardStore.setSelectedMonth).toHaveBeenCalledWith(null);
      done();
    });
  });

  it('should call loadInitial on ngOnInit', () => {
    expect(dashboardStore.loadInitial).toHaveBeenCalled();
  });

  it('should render month field with correct placeholder', () => {
    const monthInput = fixture.nativeElement.querySelector('input[type="month"]');
    expect(monthInput).toBeTruthy();
    const label = fixture.nativeElement.querySelector('mat-label');
    expect(label).toBeTruthy();
  });
});

