import {TestBed} from '@angular/core/testing';
import {of} from 'rxjs';
import {describe, expect, it, vi} from 'vitest';
import {BackendApiService} from '../../../core/api/backend-api.service';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {AuthStore} from '../../../core/state/auth.store';
import {PatientListStore, TAILLE_DE_PAGE_MAXIMALE} from './patient-list.store';

/** Le mode local charge tous les patients page par page ; la recherche refuse une taille de page supérieure à 100. */
describe('PatientListStore (chargement complet)', () => {
  it('ne demande jamais une page de plus de 100 patients', () => {
    expect(TAILLE_DE_PAGE_MAXIMALE).toBeLessThanOrEqual(100);

    const tailles: number[] = [];
    const listPatients = vi.fn((_centre: string, _utilisateur: string, requete: { page: number; size: number }) => {
      tailles.push(requete.size);
      const items = Array.from({length: requete.size}, (_, i) => ({id: `p-${requete.page}-${i}`}));
      return of({items, total: 250, page: requete.page, size: requete.size});
    });
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        {provide: BackendApiService, useValue: {listPatients}},
        {provide: AppShellStore, useValue: {currentCenterId: () => null}},
        {provide: AuthStore, useValue: {username: () => 'u'}},
      ],
    });

    const store = TestBed.inject(PatientListStore);
    store.loadAllPatients({centerId: 'c1', userId: 'u'});

    expect(listPatients).toHaveBeenCalledTimes(3);
    expect(tailles.every((t) => t <= 100)).toBe(true);
    expect(store.total()).toBe(300);
  });
});

describe('PatientListStore (export de la liste)', () => {
  it('exporte toutes les pages qui répondent aux filtres affichés, pas seulement la page courante', () => {
    const requetes: Array<{ page: number; size: number; filters?: Record<string, string> }> = [];
    const listPatients = vi.fn((_centre: string, _utilisateur: string,
                                requete: { page: number; size: number; filters?: Record<string, string> }) => {
      requetes.push(requete);
      const restant = Math.max(0, 130 - requete.page * requete.size);
      const items = Array.from({length: Math.min(requete.size, restant)}, (_, i) => ({id: `p-${requete.page}-${i}`, nom: 'X'}));
      return of({items, total: 130, page: requete.page, size: requete.size});
    });
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        {provide: BackendApiService, useValue: {listPatients}},
        {provide: AppShellStore, useValue: {currentCenterId: () => null}},
        {provide: AuthStore, useValue: {username: () => 'u'}},
      ],
    });
    const store = TestBed.inject(PatientListStore);
    store.setFilter('etatPatient', 'DECEDE');
    requetes.length = 0;

    let exportees: any[] = [];
    store.exportFilteredRows({centerId: 'c1', userId: 'u', onRows: (rows) => (exportees = rows)});

    expect(requetes.map((r) => r.page)).toEqual([0, 1]);
    expect(requetes.every((r) => r.filters?.['etatPatient'] === 'DECEDE' && r.size <= 100)).toBe(true);
    expect(exportees).toHaveLength(130);
    expect(store.exportingList()).toBe(false);
  });
});
