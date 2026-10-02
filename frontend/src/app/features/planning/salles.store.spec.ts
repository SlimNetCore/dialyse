import {provideZonelessChangeDetection} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {of, throwError} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {PlanningApiService, SalleVue} from '../../core/api/planning-api.service';
import {AppShellStore} from '../../core/state/app-shell.store';
import {SallesGenerateursStore} from './salles.store';

const CENTRE = '11111111-1111-1111-1111-111111111111';

const salle = (overrides: Partial<SalleVue> = {}): SalleVue => ({
  id: 's1', code: 'S1', nom: 'Salle 1', isolement: false, capacite: 4, nbGenerateurs: 2, placesRestantes: 2,
  depassement: false, generateurs: [], ...overrides,
});

describe('SallesGenerateursStore', () => {
  let api: { salles: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    api = {
      salles: vi.fn().mockReturnValue(of({
        items: [salle(), salle({id: 's2', depassement: true})],
        total: 25,
        page: 0,
        size: 12
      }))
    };
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), {provide: PlanningApiService, useValue: api}],
    });
    TestBed.inject(AppShellStore).switchCenter(CENTRE);
  });

  it('charge une page de salles du centre actif et compte les dépassements', () => {
    const store = TestBed.inject(SallesGenerateursStore);

    store.loadPage({page: 0, size: 12});

    expect(api.salles).toHaveBeenCalledWith(CENTRE, 0, 12);
    expect(store.rows()).toHaveLength(2);
    expect(store.total()).toBe(25);
    expect(store.nbDepassements()).toBe(1);
    expect(store.isEmpty()).toBe(false);
  });

  it('recharge la page demandée quand la pagination change', () => {
    const store = TestBed.inject(SallesGenerateursStore);

    store.setPagination(2, 24);

    expect(api.salles).toHaveBeenLastCalledWith(CENTRE, 2, 24);
    expect(store.pageSize()).toBe(24);
  });

  it('signale une erreur de chargement et vide la liste', () => {
    api.salles.mockReturnValue(throwError(() => new Error('boom')));
    const store = TestBed.inject(SallesGenerateursStore);

    store.loadPage({page: 0, size: 12});

    expect(store.error()).toBe('PLANNING.SALLES.ERR.LOAD');
    expect(store.rows()).toEqual([]);
    expect(store.loading()).toBe(false);
  });
});
