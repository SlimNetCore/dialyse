import {TestBed} from '@angular/core/testing';
import {provideZonelessChangeDetection} from '@angular/core';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {of, throwError} from 'rxjs';
import {GmaoInterventionsStore} from './gmao-interventions.store';
import {GmaoApiService, Intervention} from '../../../core/api/gmao-api.service';

function intervention(overrides: Partial<Intervention> = {}): Intervention {
  return {
    id: 'int-1',
    equipementId: 'eq-1',
    centreId: 'centre-1',
    type: 'PREVENTIVE',
    statut: 'PLANIFIEE',
    dateDebut: '2024-01-01T00:00:00',
    dateFin: null,
    technicien: null,
    description: 'Contrôle annuel',
    actions: null,
    pieceRemplacee: null,
    cout: null,
    observations: null,
    dateCreation: '2024-01-01T00:00:00',
    dateModification: null,
    ...overrides,
  };
}

describe('GmaoInterventionsStore', () => {
  let apiMock: {
    listInterventions: ReturnType<typeof vi.fn>;
    createIntervention: ReturnType<typeof vi.fn>;
    startIntervention: ReturnType<typeof vi.fn>;
    finishIntervention: ReturnType<typeof vi.fn>;
    cancelIntervention: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    apiMock = {
      listInterventions: vi.fn().mockReturnValue(of({items: [intervention()], total: 1, page: 0, size: 20})),
      createIntervention: vi.fn().mockReturnValue(of(intervention())),
      startIntervention: vi.fn().mockReturnValue(of(intervention({statut: 'EN_COURS'}))),
      finishIntervention: vi.fn().mockReturnValue(of(intervention({statut: 'TERMINEE'}))),
      cancelIntervention: vi.fn().mockReturnValue(of(intervention({statut: 'ANNULEE'}))),
    };

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        GmaoInterventionsStore,
        {provide: GmaoApiService, useValue: apiMock},
      ],
    });
  });

  it('charge une page paginée d\'interventions', () => {
    const store = TestBed.inject(GmaoInterventionsStore);

    store.loadPage({page: 0, size: 20});

    expect(apiMock.listInterventions).toHaveBeenCalledWith(0, 20, {statut: null, equipementId: null});
    expect(store.rows().length).toBe(1);
    expect(store.total()).toBe(1);
  });

  it('transmet le filtre de statut et d\'équipement à la requête', () => {
    const store = TestBed.inject(GmaoInterventionsStore);

    store.setStatutFilter('EN_COURS');
    store.setEquipementFilter('eq-1');
    store.loadPage({page: 0, size: 20});

    expect(apiMock.listInterventions).toHaveBeenCalledWith(0, 20, {statut: 'EN_COURS', equipementId: 'eq-1'});
  });

  it('expose une erreur quand le chargement échoue', () => {
    apiMock.listInterventions.mockReturnValue(throwError(() => new Error('boom')));
    const store = TestBed.inject(GmaoInterventionsStore);

    store.loadPage({page: 0, size: 20});

    expect(store.rows()).toEqual([]);
    expect(store.error()).toBe('GMAO.INTERVENTIONS.LOAD_ERROR');
  });

  it('termine une intervention et notifie le succès', () => {
    const store = TestBed.inject(GmaoInterventionsStore);

    store.finishIntervention({id: 'int-1', actions: 'Remplacement filtre'});

    expect(apiMock.finishIntervention).toHaveBeenCalledWith('int-1', 'Remplacement filtre');
    expect(store.successMessage()).toBe('GMAO.INTERVENTIONS.STATUS_UPDATED');
  });
});
