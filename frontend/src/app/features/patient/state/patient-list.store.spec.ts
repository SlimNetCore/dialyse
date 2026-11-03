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
