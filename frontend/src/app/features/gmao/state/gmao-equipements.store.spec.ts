import {TestBed} from '@angular/core/testing';
import {provideZonelessChangeDetection} from '@angular/core';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {of, throwError} from 'rxjs';
import {GmaoEquipementsStore} from './gmao-equipements.store';
import {Equipement, GmaoApiService} from '../../../core/api/gmao-api.service';

function equipement(overrides: Partial<Equipement> = {}): Equipement {
  return {
    id: 'eq-1',
    code: 'EQ-001',
    designation: 'Générateur',
    type: 'GENERATEUR_DIALYSE',
    fabricant: null,
    modele: null,
    numeroSerie: null,
    dateInstallation: '2024-01-01T00:00:00',
    statut: 'EN_SERVICE',
    localisation: null,
    observations: null,
    dateCreation: '2024-01-01T00:00:00',
    dateModification: null,
    salleId: null,
    prixAcquisition: null,
    ...overrides,
  };
}

describe('GmaoEquipementsStore', () => {
  let apiMock: {
    listEquipements: ReturnType<typeof vi.fn>;
    createEquipement: ReturnType<typeof vi.fn>;
    updateEquipement: ReturnType<typeof vi.fn>;
    markEquipementOutOfService: ReturnType<typeof vi.fn>;
    reactivateEquipement: ReturnType<typeof vi.fn>;
    reformerEquipement: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    apiMock = {
      listEquipements: vi.fn().mockReturnValue(of({items: [equipement()], total: 1, page: 0, size: 20})),
      createEquipement: vi.fn().mockReturnValue(of(equipement())),
      updateEquipement: vi.fn().mockReturnValue(of(equipement())),
      markEquipementOutOfService: vi.fn().mockReturnValue(of(equipement({statut: 'HORS_SERVICE'}))),
      reactivateEquipement: vi.fn().mockReturnValue(of(equipement())),
      reformerEquipement: vi.fn().mockReturnValue(of(equipement({statut: 'REFORME'}))),
    };

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        GmaoEquipementsStore,
        {provide: GmaoApiService, useValue: apiMock},
      ],
    });
  });

  it('charge une page et expose le total paginé', () => {
    const store = TestBed.inject(GmaoEquipementsStore);

    store.loadPage({page: 0, size: 20});

    expect(apiMock.listEquipements).toHaveBeenCalledWith(0, 20, null);
    expect(store.rows().length).toBe(1);
    expect(store.total()).toBe(1);
    expect(store.loading()).toBe(false);
  });

  it('transmet le filtre de statut courant à la requête paginée', () => {
    const store = TestBed.inject(GmaoEquipementsStore);

    store.setStatutFilter('HORS_SERVICE');
    store.loadPage({page: 0, size: 20});

    expect(apiMock.listEquipements).toHaveBeenCalledWith(0, 20, 'HORS_SERVICE');
  });

  it('expose une erreur et vide la liste quand le chargement échoue', () => {
    apiMock.listEquipements.mockReturnValue(throwError(() => new Error('boom')));
    const store = TestBed.inject(GmaoEquipementsStore);

    store.loadPage({page: 0, size: 20});

    expect(store.rows()).toEqual([]);
    expect(store.total()).toBe(0);
    expect(store.error()).toBe('GMAO.EQUIPEMENTS.LOAD_ERROR');
  });

  it('réforme un équipement et remonte un message de succès', () => {
    const store = TestBed.inject(GmaoEquipementsStore);

    store.reformer({id: 'eq-1', motif: 'Fin de vie'});

    expect(apiMock.reformerEquipement).toHaveBeenCalledWith('eq-1', 'Fin de vie');
    expect(store.successMessage()).toBe('GMAO.EQUIPEMENTS.STATUS_UPDATED');
  });

  it('marque un équipement hors service et remonte un message de succès', () => {
    const store = TestBed.inject(GmaoEquipementsStore);

    store.markOutOfService({id: 'eq-1', raison: 'Panne'});

    expect(apiMock.markEquipementOutOfService).toHaveBeenCalledWith('eq-1', 'Panne');
    expect(store.successMessage()).toBe('GMAO.EQUIPEMENTS.STATUS_UPDATED');
    expect(store.saving()).toBe(false);
  });

  it('isEmpty reflète l\'absence de lignes', () => {
    apiMock.listEquipements.mockReturnValue(of({items: [], total: 0, page: 0, size: 20}));
    const store = TestBed.inject(GmaoEquipementsStore);

    expect(store.isEmpty()).toBe(true);
    store.loadPage({page: 0, size: 20});
    expect(store.isEmpty()).toBe(true);
  });
});
