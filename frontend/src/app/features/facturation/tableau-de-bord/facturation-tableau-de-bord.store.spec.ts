import {TestBed} from '@angular/core/testing';
import {provideZonelessChangeDetection} from '@angular/core';
import {of, throwError} from 'rxjs';
import {vi} from 'vitest';
import {BackendApiService} from '../../../core/api/backend-api.service';
import {FacturationTableauDeBordStore, moisDeLaCourbe} from './facturation-tableau-de-bord.store';

const CENTER_ID = '11111111-1111-1111-1111-111111111111';

const dashboardDuMois = (month: string, revenueHt: number) => ({
  centerId: CENTER_ID, month, revenueHt, revenueTtc: revenueHt * 1.19, billedSeances: 2, billedPatients: 1,
  createdInvoices: 1, byInsurance: [], byPatientStatus: [],
});

describe('FacturationTableauDeBordStore', () => {
  let api: { getFacturationDashboard: ReturnType<typeof vi.fn> };

  const creer = () => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        FacturationTableauDeBordStore,
        {provide: BackendApiService, useValue: api},
      ],
    });
    return TestBed.inject(FacturationTableauDeBordStore);
  };

  beforeEach(() => {
    api = {getFacturationDashboard: vi.fn((_: string, month: string) => of(dashboardDuMois(month, 1000)))};
  });

  it('démarre sur le mois courant, sans indicateurs', () => {
    const store = creer();

    expect(store.mois()).toMatch(/^\d{4}-\d{2}$/);
    expect(store.dashboard()).toBeNull();
    expect(store.revenueTrend()).toEqual([]);
  });

  it('charge les indicateurs du mois demandé', () => {
    const store = creer();

    store.loadDashboard({centerId: CENTER_ID, month: '2026-08'});

    expect(api.getFacturationDashboard).toHaveBeenCalledWith(CENTER_ID, '2026-08');
    expect(store.dashboard()?.revenueHt).toBe(1000);
    expect(store.dashboardLoading()).toBe(false);
  });

  it('expose l erreur et vide les indicateurs quand le chargement échoue', () => {
    api.getFacturationDashboard.mockReturnValue(throwError(() => ({status: 500})));
    const store = creer();

    store.loadDashboard({centerId: CENTER_ID, month: '2026-08'});

    expect(store.dashboard()).toBeNull();
    expect(store.error()).not.toBeNull();
  });

  it('construit la courbe du CA sur les six mois se terminant au mois choisi', () => {
    const store = creer();

    store.loadRevenueTrend({centerId: CENTER_ID, endingMonth: '2026-02', months: 6});

    expect(store.revenueTrend().map((p) => p.month))
      .toEqual(['2025-09', '2025-10', '2025-11', '2025-12', '2026-01', '2026-02']);
    expect(store.revenueTrendLoading()).toBe(false);
  });

  it('change de mois sans toucher à un autre état', () => {
    const store = creer();

    store.setMois('2026-03');

    expect(store.mois()).toBe('2026-03');
    expect(api.getFacturationDashboard).not.toHaveBeenCalled();
  });

  it('moisDeLaCourbe retombe sur le mois courant pour une saisie invalide', () => {
    expect(moisDeLaCourbe('2026-13x', 6)).toHaveLength(1);
    expect(moisDeLaCourbe('2026-12', 3)).toEqual(['2026-10', '2026-11', '2026-12']);
  });
});
