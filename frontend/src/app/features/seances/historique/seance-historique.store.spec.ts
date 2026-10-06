import {TestBed} from '@angular/core/testing';
import {provideZonelessChangeDetection} from '@angular/core';
import {HttpErrorResponse} from '@angular/common/http';
import {of, throwError} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {SeanceHistoriqueStore, suppressionErrorKey} from './seance-historique.store';
import {BackendApiService} from '../../../core/api/backend-api.service';

const CENTER = '11111111-1111-1111-1111-111111111111';

describe('SeanceHistoriqueStore', () => {
  let api: { listSeances: ReturnType<typeof vi.fn>; supprimerSeance: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    api = {
      listSeances: vi.fn().mockReturnValue(of({
        items: [{id: 's1', dateSeance: '2026-10-04', status: 'VALIDEE', patientId: 'p1'}], total: 41, page: 0, size: 20,
      })),
      supprimerSeance: vi.fn().mockReturnValue(of(null)),
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

  it('supprime une séance du centre actif avec son motif puis recharge la page', async () => {
    const store = TestBed.inject(SeanceHistoriqueStore);

    const ok = await store.supprimer(CENTER, 's1', 'Séance saisie en double');

    expect(ok).toBe(true);
    expect(api.supprimerSeance).toHaveBeenCalledWith('s1', CENTER, 'Séance saisie en double');
    expect(api.listSeances).toHaveBeenCalledTimes(1);
    expect(store.successMessage()).toBe('SEANCES.DELETE.OK');
    expect(store.deleting()).toBe(false);
  });

  it('traduit le refus du serveur sans recharger', async () => {
    const store = TestBed.inject(SeanceHistoriqueStore);
    api.supprimerSeance.mockReturnValue(throwError(() => new HttpErrorResponse({
      status: 422, error: {code: 'SEANCE_FACTUREE_NON_SUPPRIMABLE'},
    })));

    const ok = await store.supprimer(CENTER, 's1', 'Erreur de patient');

    expect(ok).toBe(false);
    expect(store.error()).toBe('SEANCES.DELETE.ERR.SEANCE_FACTUREE_NON_SUPPRIMABLE');
    expect(api.listSeances).not.toHaveBeenCalled();
  });

  it('associe chaque refus de suppression à un message', () => {
    expect(suppressionErrorKey(new HttpErrorResponse({status: 403}))).toBe('SEANCES.DELETE.ERR.DROITS');
    expect(suppressionErrorKey(new HttpErrorResponse({status: 500}))).toBe('SEANCES.DELETE.ERR.GENERIC');
    expect(suppressionErrorKey(new Error('x'))).toBe('SEANCES.DELETE.ERR.GENERIC');
  });
});
