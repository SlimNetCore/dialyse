import {TestBed} from '@angular/core/testing';
import {signal} from '@angular/core';
import {vi} from 'vitest';
import {TranslateService} from '@ngx-translate/core';
import {FacturationTableauDeBordComponent} from './facturation-tableau-de-bord.component';
import {FacturationTableauDeBordStore} from './facturation-tableau-de-bord.store';
import {FacturationStore} from '../state/facturation.store';
import {AppShellStore} from '../../../core/state/app-shell.store';

const CENTER_ID = '11111111-1111-1111-1111-111111111111';

describe('FacturationTableauDeBordComponent', () => {
  const creerStore = () => ({
    mois: signal('2026-08'),
    dashboard: signal<unknown>({
      byInsurance: [
        {code: 'CNAS', label: 'CNAS', seancesCount: 3, patientsCount: 1},
        {code: 'X', label: 'Caisse CASNOS', seancesCount: 2, patientsCount: 2},
        {code: 'AUTRE', label: 'Autre', seancesCount: 9, patientsCount: 9},
      ],
    }),
    dashboardLoading: signal(false),
    revenueTrend: signal([{month: '2026-08', revenueHt: 1200, revenueTtc: 1428, billedSeances: 4, createdInvoices: 2}]),
    revenueTrendLoading: signal(false),
    error: signal(null),
    errorParams: signal(null),
    setMois: vi.fn(),
    loadDashboard: vi.fn(),
    loadRevenueTrend: vi.fn(),
  });
  let store: ReturnType<typeof creerStore>;
  const facturationStore = {setMonth: vi.fn(), month: vi.fn(() => '2026-01')};

  const creer = () => {
    const component = TestBed.runInInjectionContext(() => new FacturationTableauDeBordComponent());
    TestBed.tick();
    return component;
  };

  beforeEach(() => {
    vi.clearAllMocks();
    store = creerStore();
    TestBed.configureTestingModule({
      providers: [
        {provide: FacturationTableauDeBordStore, useValue: store},
        {provide: AppShellStore, useValue: {currentCenterId: signal(CENTER_ID)}},
        {provide: FacturationStore, useValue: facturationStore},
        {provide: TranslateService, useValue: {instant: (key: string) => key}},
      ],
    });
  });

  it('charge les indicateurs et la courbe de six mois du mois affiché à l ouverture', () => {
    creer();

    expect(store.loadDashboard).toHaveBeenCalledWith({centerId: CENTER_ID, month: '2026-08'});
    expect(store.loadRevenueTrend).toHaveBeenCalledWith({centerId: CENTER_ID, endingMonth: '2026-08', months: 6});
  });

  it('a son propre mois : le changer recharge le tableau de bord sans toucher au mois de l écran de calcul', () => {
    const component = creer();

    component['onMoisChange']('2026-09');

    expect(store.setMois).toHaveBeenCalledWith('2026-09');
    expect(store.loadDashboard).toHaveBeenLastCalledWith({centerId: CENTER_ID, month: '2026-09'});
    expect(store.loadRevenueTrend).toHaveBeenLastCalledWith({centerId: CENTER_ID, endingMonth: '2026-09', months: 6});
    expect(facturationStore.setMonth).not.toHaveBeenCalled();
  });

  it('ignore une saisie de mois incomplète', () => {
    const component = creer();
    store.loadDashboard.mockClear();

    component['onMoisChange']('');

    expect(store.setMois).toHaveBeenCalledWith('');
    expect(store.loadDashboard).not.toHaveBeenCalled();
  });

  it('regroupe les caisses CNAS, CASNOS et CAMSSP et trace le CA HT', () => {
    const component = creer();

    expect(component['byCaisseRows']()).toEqual([
      {code: 'CNAS', label: 'CNAS', seancesCount: 3, patientsCount: 1},
      {code: 'CASNOS', label: 'CASNOS', seancesCount: 2, patientsCount: 2},
      {code: 'CAMSSP', label: 'CAMSSP', seancesCount: 0, patientsCount: 0},
    ]);
    expect(component['revenueChartData']().datasets[0].data).toEqual([1200]);
  });
});
