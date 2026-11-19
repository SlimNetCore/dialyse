import {TestBed} from '@angular/core/testing';
import {signal} from '@angular/core';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TranslateService} from '@ngx-translate/core';
import {ReglementTableauDeBordComponent} from './reglement-tableau-de-bord.component';
import {ReglementTableauDeBordStore} from './reglement-tableau-de-bord.store';
import {ReglementStore} from '../state/reglement.store';
import {AppShellStore} from '../../../core/state/app-shell.store';

const CENTER_ID = '11111111-1111-1111-1111-111111111111';

describe('ReglementTableauDeBordComponent', () => {
  const creerStore = () => {
    const year = signal(2026);
    const month = signal<number | null>(8);
    return {
      year,
      month,
      caisseId: signal<string | null>(null),
      agenceId: signal<string | null>(null),
      dashboard: signal<unknown>({statusBreakdown: [{code: 'REGLEE', count: 3}, {code: 'NON_REGLEE', count: 1}]}),
      dashboardCards: signal({
        totalFactures: 4, reglees: 3, nonReglees: 1, partiellementReglees: 0, tropPercus: 0,
        totalFacture: 4000, totalRegle: 3000, totalReste: 1000, totalTropPercu: 0, tauxEncaissement: 0.75,
      }),
      dashboardLoading: signal(false),
      referentialsLoading: signal(false),
      caisses: signal([]),
      agences: signal([]),
      error: signal(null),
      setYear: vi.fn((value: number) => year.set(value)),
      setMonth: vi.fn((value: number | null) => month.set(value)),
      setCaisseId: vi.fn(),
      setAgenceId: vi.fn(),
      clearFilters: vi.fn(),
      reset: vi.fn(),
      loadDashboard: vi.fn(),
      loadReferentials: vi.fn(),
    };
  };
  let store: ReturnType<typeof creerStore>;
  const saisie = {setYear: vi.fn(), setMonth: vi.fn(), loadPage: vi.fn()};

  const creer = () => {
    const component = TestBed.runInInjectionContext(() => new ReglementTableauDeBordComponent());
    TestBed.tick();
    return component;
  };

  beforeEach(() => {
    vi.clearAllMocks();
    store = creerStore();
    TestBed.configureTestingModule({
      providers: [
        {provide: ReglementTableauDeBordStore, useValue: store},
        {provide: ReglementStore, useValue: saisie},
        {provide: AppShellStore, useValue: {currentCenterId: signal(CENTER_ID)}},
        {provide: TranslateService, useValue: {instant: (key: string) => key}},
      ],
    });
  });

  it('charge les indicateurs et les référentiels des filtres à l ouverture', () => {
    creer();

    expect(store.loadDashboard).toHaveBeenCalledWith({centerId: CENTER_ID});
    expect(store.loadReferentials).toHaveBeenCalledWith({centerId: CENTER_ID});
  });

  it('a ses propres filtres : les changer recharge les indicateurs sans toucher à l écran de saisie', () => {
    const component = creer();
    store.loadDashboard.mockClear();

    component['onYearChange']('2025');
    component['onMonthChange']('');
    TestBed.tick();

    expect(store.setYear).toHaveBeenCalledWith(2025);
    expect(store.setMonth).toHaveBeenCalledWith(null);
    expect(store.loadDashboard).toHaveBeenCalledWith({centerId: CENTER_ID});
    expect(saisie.setYear).not.toHaveBeenCalled();
    expect(saisie.setMonth).not.toHaveBeenCalled();
    expect(saisie.loadPage).not.toHaveBeenCalled();
  });

  it('propage caisse, agence et réinitialisation au store du tableau de bord', () => {
    const component = creer();

    component['onCaisseChange']('c1');
    component['onAgenceChange']('');
    component['onResetFilters']();

    expect(store.setCaisseId).toHaveBeenCalledWith('c1');
    expect(store.setAgenceId).toHaveBeenCalledWith(null);
    expect(store.clearFilters).toHaveBeenCalled();
  });

  it('trace le donut des statuts et l histogramme des montants', () => {
    const component = creer();

    expect(component['statusChartData']().labels)
      .toEqual(['REGLEMENT_MODULE.DASHBOARD.BUCKETS.REGLEE', 'REGLEMENT_MODULE.DASHBOARD.BUCKETS.NON_REGLEE']);
    expect(component['statusChartData']().datasets[0].data).toEqual([3, 1]);
    expect(component['amountChartData']().datasets[0].data).toEqual([4000, 3000, 1000, 0]);
  });
});
