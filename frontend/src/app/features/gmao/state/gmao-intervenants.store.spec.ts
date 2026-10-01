import {TestBed} from '@angular/core/testing';
import {provideZonelessChangeDetection} from '@angular/core';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {of} from 'rxjs';
import {GmaoIntervenantsStore} from './gmao-intervenants.store';
import {GmaoApiService, Intervenant} from '../../../core/api/gmao-api.service';

function intervenant(overrides: Partial<Intervenant> = {}): Intervenant {
  return {
    id: 'iv-1',
    centreId: 'centre-1',
    nom: 'Ahmed B.',
    type: 'INTERNE',
    telephone: null,
    email: null,
    tarifHoraireDefaut: null,
    actif: true,
    ...overrides,
  };
}

describe('GmaoIntervenantsStore', () => {
  let apiMock: {
    listIntervenants: ReturnType<typeof vi.fn>;
    createIntervenant: ReturnType<typeof vi.fn>;
    updateIntervenant: ReturnType<typeof vi.fn>;
    deactivateIntervenant: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    apiMock = {
      listIntervenants: vi.fn().mockReturnValue(of({items: [intervenant()], total: 1, page: 0, size: 20})),
      createIntervenant: vi.fn().mockReturnValue(of(intervenant())),
      updateIntervenant: vi.fn().mockReturnValue(of(intervenant())),
      deactivateIntervenant: vi.fn().mockReturnValue(of(intervenant({actif: false}))),
    };

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        GmaoIntervenantsStore,
        {provide: GmaoApiService, useValue: apiMock},
      ],
    });
  });

  it('charge une page paginée d\'intervenants', () => {
    const store = TestBed.inject(GmaoIntervenantsStore);

    store.loadPage({page: 0, size: 20});

    expect(apiMock.listIntervenants).toHaveBeenCalledWith(0, 20);
    expect(store.rows().length).toBe(1);
  });

  it('crée un intervenant et notifie le succès', () => {
    const store = TestBed.inject(GmaoIntervenantsStore);

    store.createIntervenant({nom: 'Ahmed B.', type: 'INTERNE'});

    expect(apiMock.createIntervenant).toHaveBeenCalledWith({nom: 'Ahmed B.', type: 'INTERNE'});
    expect(store.successMessage()).toBe('GMAO.INTERVENANTS.SAVED_OK');
  });

  it('désactive un intervenant', () => {
    const store = TestBed.inject(GmaoIntervenantsStore);

    store.deactivateIntervenant('iv-1');

    expect(apiMock.deactivateIntervenant).toHaveBeenCalledWith('iv-1');
    expect(store.successMessage()).toBe('GMAO.INTERVENANTS.DEACTIVATED');
  });
});
