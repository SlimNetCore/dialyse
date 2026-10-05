import {TestBed} from '@angular/core/testing';
import {provideZonelessChangeDetection} from '@angular/core';
import {of, throwError} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {SeanceHistoriqueStore} from './seance-historique.store';
import {BackendApiService} from '../../../core/api/backend-api.service';

const CENTER = '11111111-1111-1111-1111-111111111111';

describe('SeanceHistoriqueStore', () => {
  let api: { listSeances: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    api = {
      listSeances: vi.fn().mockReturnValue(of({
        items: [{id: 's1', dateSeance: '2026-10-04', status: 'VALIDEE', patientId: 'p1'}], total: 41, page: 0, size: 20,
      })),
    };
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), {provide: BackendApiService, useValue: api}],
    });
  });

  it('charge la première page, triée par date décroissante, et expose le total du serveur', () => {
    const store = TestBed.inject(SeanceHistoriqueStore);

    store.reload(CENTER);

    expect(api.listSeances).toHaveBeenCalledWith(CENTER, 0, 20, expect.objectContaining({
      sortBy: 'dateSeance',
      sortDir: 'desc'
    }));
    expect(store.rows()).toHaveLength(1);
    expect(store.total()).toBe(41);
    expect(store.loading()).toBe(false);
  });

  it('un nouveau tri ou filtre renvoie à la première page et interroge le serveur', () => {
    const store = TestBed.inject(SeanceHistoriqueStore);
    store.setPage(CENTER, 3, 50);

    store.applyQuery(CENTER, {
      filters: {patient: 'dupont', status: 'CREE'},
      sortColumnId: 'patient',
      sortDirection: 'asc'
    });

    expect(store.pageIndex()).toBe(0);
    expect(api.listSeances).toHaveBeenLastCalledWith(CENTER, 0, 50, {
      from: null, to: null, status: 'CREE', q: 'dupont', sortBy: 'patient', sortDir: 'asc',
    });
  });

  it('changer de page garde les filtres et le tri', () => {
    const store = TestBed.inject(SeanceHistoriqueStore);
    store.applyQuery(CENTER, {filters: {patient: 'dupont'}, sortColumnId: 'status', sortDirection: 'desc'});

    store.setPage(CENTER, 2, 20);

    expect(api.listSeances).toHaveBeenLastCalledWith(CENTER, 2, 20,
      expect.objectContaining({q: 'dupont', sortBy: 'status', sortDir: 'desc'}));
  });

  it('la période est transmise au serveur et revient à la première page', () => {
    const store = TestBed.inject(SeanceHistoriqueStore);
    store.setPage(CENTER, 4, 20);

    store.setPeriod(CENTER, '2026-10-01', '2026-10-31');

    expect(store.pageIndex()).toBe(0);
    expect(api.listSeances).toHaveBeenLastCalledWith(CENTER, 0, 20,
      expect.objectContaining({from: '2026-10-01', to: '2026-10-31'}));
  });

  it('une erreur réseau vide la liste et expose un message traduisible', () => {
    api.listSeances.mockReturnValueOnce(throwError(() => new Error('boom')));
    const store = TestBed.inject(SeanceHistoriqueStore);

    store.reload(CENTER);

    expect(store.rows()).toEqual([]);
    expect(store.total()).toBe(0);
    expect(store.loading()).toBe(false);
    expect(store.error()).toBe('SEANCES.HISTORY_LOAD_ERROR');
  });
});
