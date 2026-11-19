import {TestBed} from '@angular/core/testing';
import {provideZonelessChangeDetection} from '@angular/core';
import {of, throwError} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {BackendApiService} from '../../../core/api/backend-api.service';
import {SeancesTableauDeBordStore} from './seances-tableau-de-bord.store';

const CENTER_ID = '11111111-1111-1111-1111-111111111111';
const MOCK_DASHBOARD = {
  year: 2026, month: 7,
  expectedSeances: 50, presenceCount: 40, absenceCount: 10, totalSeances: 40,
  sexeDistribution: {M: 20, F: 20, AUTRE: 0},
  ageDistribution: {'0-17': 0, '18-39': 5, '40-59': 15, '60+': 20, INCONNU: 0},
};
const detail = (i: number) => ({
  patientId: `pid${i}`, patientNom: 'A', patientPrenom: 'B', dateSeance: '2026-07-01', weekday: 'TUESDAY',
  scheduled: true, present: true, status: 'VALIDEE',
});

describe('SeancesTableauDeBordStore', () => {
  let api: { getSeanceMonthlyDashboard: ReturnType<typeof vi.fn>; getSeanceDashboardDetails: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    api = {
      getSeanceMonthlyDashboard: vi.fn().mockReturnValue(of(MOCK_DASHBOARD)),
      getSeanceDashboardDetails: vi.fn().mockReturnValue(of({
        year: 2026, month: 7, kind: 'presence', total: 2, items: [detail(1), detail(2)],
      })),
    };
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), SeancesTableauDeBordStore, {provide: BackendApiService, useValue: api}],
    });
  });

  it('démarre sur le mois courant, sans statistiques chargées', () => {
    const store = TestBed.inject(SeancesTableauDeBordStore);
    expect(store.dashboardMonth()).toMatch(/^\d{4}-\d{2}$/);
    expect(store.seanceDashboard()).toBeNull();
    expect(store.dashboardLoading()).toBe(false);
  });

  it('charge les statistiques du mois demandé', () => {
    const store = TestBed.inject(SeancesTableauDeBordStore);
    store.loadDashboard({centerId: CENTER_ID, year: 2026, month: 7});
    expect(api.getSeanceMonthlyDashboard).toHaveBeenCalledWith(CENTER_ID, 2026, 7);
    expect(store.seanceDashboard()?.presenceCount).toBe(40);
    expect(store.seanceDashboard()?.expectedSeances).toBe(50);
    expect(store.dashboardLoading()).toBe(false);
  });

  it('garde l\'erreur de chargement des statistiques', () => {
    api.getSeanceMonthlyDashboard.mockReturnValueOnce(throwError(() => ({error: {message: 'boom'}})));
    const store = TestBed.inject(SeancesTableauDeBordStore);
    store.loadDashboard({centerId: CENTER_ID, year: 2026, month: 7});
    expect(store.error()).toBe('boom');
    expect(store.dashboardLoading()).toBe(false);
  });

  it('ouvre le détail des présences, le charge et le referme', () => {
    const store = TestBed.inject(SeancesTableauDeBordStore);
    store.openDashboardDetails('presence');
    store.loadDashboardDetails({centerId: CENTER_ID, year: 2026, month: 7, kind: 'presence'});
    expect(store.dashboardDetailsOpen()).toBe(true);
    expect(store.dashboardDetailItems()).toHaveLength(2);
    expect(store.dashboardDetailTotalPages()).toBe(1);
    store.closeDashboardDetails();
    expect(store.dashboardDetailsOpen()).toBe(false);
  });

  it('pagine le détail par dix lignes', () => {
    api.getSeanceDashboardDetails.mockReturnValueOnce(of({
      year: 2026, month: 7, kind: 'absence', total: 12, items: Array.from({length: 12}, (_, i) => detail(i)),
    }));
    const store = TestBed.inject(SeancesTableauDeBordStore);
    store.openDashboardDetails('absence');
    store.loadDashboardDetails({centerId: CENTER_ID, year: 2026, month: 7, kind: 'absence'});
    expect(store.dashboardDetailTotalPages()).toBe(2);
    store.setDashboardDetailPage(1);
    expect(store.dashboardDetailPageItems()).toHaveLength(2);
  });
});
