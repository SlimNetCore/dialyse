import {provideZonelessChangeDetection} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {of, throwError} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {MouvementPatient, MouvementPatientApiService} from '../../core/api/mouvement-patient-api.service';
import {AppShellStore} from '../../core/state/app-shell.store';
import {EMPTY_FILTERS, MouvementsPatientsStore} from './mouvements-patients.store';

const CENTRE = '11111111-1111-1111-1111-111111111111';
const AUTRE_CENTRE = '22222222-2222-2222-2222-222222222222';

function mouvement(overrides: Partial<MouvementPatient> = {}): MouvementPatient {
  return {
    id: 'm1', patientId: 'p1', patientNom: 'Ali Amrani', patientCode: 'PAT-1', type: 'PLACE_LIBEREE',
    dateEffet: '2026-10-04', etatPrecedent: 'DECEDE', etatNouveau: 'DECEDE', salle: 'Salle 1', creneau: 'Matin',
    generateur: 'G01', joursDialyse: 'LUNDI,MERCREDI', automatique: true, creeLe: '2026-10-04T04:00:00Z', ...overrides,
  };
}

describe('MouvementsPatientsStore', () => {
  let api: Record<string, ReturnType<typeof vi.fn>>;

  beforeEach(() => {
    api = {list: vi.fn().mockReturnValue(of({items: [mouvement()], total: 41, page: 1, size: 20}))};
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), {provide: MouvementPatientApiService, useValue: api}],
    });
    TestBed.inject(AppShellStore).switchCenter(CENTRE);
  });

  it('charge une page paginée du centre actif', () => {
    const store = TestBed.inject(MouvementsPatientsStore);

    store.loadPage({page: 1, size: 20});

    expect(api['list']).toHaveBeenCalledWith(CENTRE, EMPTY_FILTERS, 1, 20);
    expect(store.rows()).toHaveLength(1);
    expect(store.total()).toBe(41);
    expect(store.pageIndex()).toBe(1);
    expect(store.loading()).toBe(false);
    expect(store.isEmpty()).toBe(false);
  });

  it("n'affiche que le centre actif : un changement de centre recharge avec le nouveau centre", () => {
    const store = TestBed.inject(MouvementsPatientsStore);
    store.loadPage({page: 0, size: 20});
    TestBed.inject(AppShellStore).switchCenter(AUTRE_CENTRE);

    store.loadPage({page: 0, size: 20});

    expect(api['list']).toHaveBeenLastCalledWith(AUTRE_CENTRE, EMPTY_FILTERS, 0, 20);
  });

  it('applique les filtres et revient à la première page', () => {
    const store = TestBed.inject(MouvementsPatientsStore);
    store.loadPage({page: 3, size: 20});
    const filtres = {type: 'DECES' as const, from: '2026-10-01', to: '2026-10-31'};

    store.applyFilters(filtres);

    expect(api['list']).toHaveBeenLastCalledWith(CENTRE, filtres, 0, 20);
    expect(store.filters()).toEqual(filtres);
  });

  it("signale l'échec du chargement par une clé de traduction", () => {
    api['list'].mockReturnValue(throwError(() => new Error('boom')));
    const store = TestBed.inject(MouvementsPatientsStore);

    store.loadPage({page: 0, size: 20});

    expect(store.error()).toBe('MOUVEMENTS.ERR_LOAD');
    expect(store.rows()).toEqual([]);
    expect(store.isEmpty()).toBe(true);
  });
});
