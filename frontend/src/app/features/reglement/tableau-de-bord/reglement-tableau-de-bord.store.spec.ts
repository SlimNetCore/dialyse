import {TestBed} from '@angular/core/testing';
import {provideZonelessChangeDetection} from '@angular/core';
import {of, throwError} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {BackendApiService} from '../../../core/api/backend-api.service';
import {ReferentialApiService} from '../../../core/api/referential-api.service';
import {ReglementTableauDeBordStore} from './reglement-tableau-de-bord.store';

const CENTER_ID = '11111111-1111-1111-1111-111111111111';

const indicateurs = {
  centerId: CENTER_ID,
  year: 2026,
  month: 8,
  totalFactures: 4,
  nonReglees: 1,
  partiellementReglees: 1,
  reglees: 2,
  tropPercus: 0,
  totalFacture: 4000,
  totalRegle: 3000,
  totalReste: 1000,
  totalTropPercu: 0,
  statusBreakdown: [],
};

describe('ReglementTableauDeBordStore', () => {
  let api: { getReglementDashboard: ReturnType<typeof vi.fn> };
  let referentials: { getCaisses: ReturnType<typeof vi.fn>; getAgences: ReturnType<typeof vi.fn> };

  const creer = () => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        ReglementTableauDeBordStore,
        {provide: BackendApiService, useValue: api},
        {provide: ReferentialApiService, useValue: referentials},
      ],
    });
    return TestBed.inject(ReglementTableauDeBordStore);
  };

  beforeEach(() => {
    api = {getReglementDashboard: vi.fn(() => of(indicateurs))};
    referentials = {
      getCaisses: vi.fn(() => of([{id: 'c1', nom: 'CNAS'}])),
      getAgences: vi.fn(() => of([{id: 'a1', nom: 'Agence A'}])),
    };
  });

  it('démarre sur l année et le mois courants, sans indicateurs', () => {
    const store = creer();

    expect(store.year()).toBe(new Date().getFullYear());
    expect(store.month()).toBe(new Date().getMonth() + 1);
    expect(store.dashboard()).toBeNull();
    expect(store.dashboardCards().tauxEncaissement).toBe(0);
  });

  it('charge les indicateurs avec ses propres filtres année / mois / caisse / agence', () => {
    const store = creer();
    store.setYear(2025);
    store.setMonth(null);
    store.setCaisseId('c1');
    store.setAgenceId('a1');

    store.loadDashboard({centerId: CENTER_ID});

    expect(api.getReglementDashboard).toHaveBeenCalledWith(CENTER_ID,
      {year: 2025, month: null, caisseId: 'c1', agenceId: 'a1', centrePayeurId: null});
    expect(store.dashboardCards()).toMatchObject({totalFactures: 4, reglees: 2, tauxEncaissement: 0.75});
    expect(store.dashboardLoading()).toBe(false);
  });

  it('ignore une année ou un mois invalides et réinitialise les filtres sur le mois courant', () => {
    const store = creer();
    store.setYear(1999);
    store.setMonth(13);
    store.setCaisseId('c1');

    store.clearFilters();

    expect(store.year()).toBe(new Date().getFullYear());
    expect(store.month()).toBe(new Date().getMonth() + 1);
    expect(store.caisseId()).toBeNull();
  });

  it('expose l erreur et vide les indicateurs quand le chargement échoue', () => {
    api.getReglementDashboard.mockReturnValue(throwError(() => ({status: 500})));
    const store = creer();

    store.loadDashboard({centerId: CENTER_ID});

    expect(store.dashboard()).toBeNull();
    expect(store.error()).not.toBeNull();
  });

  it('charge les caisses et agences des filtres', () => {
    const store = creer();

    store.loadReferentials({centerId: CENTER_ID});

    expect(store.caisses()).toEqual([{id: 'c1', nom: 'CNAS'}]);
    expect(store.agences()).toEqual([{id: 'a1', nom: 'Agence A'}]);
  });
});
