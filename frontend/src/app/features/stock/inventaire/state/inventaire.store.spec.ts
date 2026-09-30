import {TestBed} from '@angular/core/testing';
import {of, throwError} from 'rxjs';
import {vi} from 'vitest';
import {Inventaire, InventaireApiService} from '../../../../core/api/inventaire-api.service';
import {AppShellStore} from '../../../../core/state/app-shell.store';
import {InventaireStore} from './inventaire.store';

function inventaire(partial: Partial<Inventaire> = {}): Inventaire {
  return {
    id: 'inv-1', reference: 'INV-2026-0001', dateInventaire: '2026-09-30', statut: 'EN_COURS', commentaire: null,
    createdBy: 'pharma', createdAt: '2026-09-30T08:00:00Z', closedBy: null, closedAt: null, lignes: 2, comptees: 2,
    ecarts: 1, ecartsSansMotif: 0, valeurTheorique: 40, valeurComptee: 36, valeurEcarts: -4, details: [], ...partial,
  };
}

describe('InventaireStore', () => {
  const api = {
    etat: vi.fn(),
    list: vi.fn(),
    get: vi.fn(),
    ouvrir: vi.fn(),
    compter: vi.fn(),
    cloturer: vi.fn(),
    importerFeuille: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
    api.etat.mockReturnValue(of({mouvementsBloques: true, inventaireEnCoursId: 'inv-1'}));
    api.list.mockReturnValue(of({items: [], total: 0}));
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        {provide: InventaireApiService, useValue: api},
        {provide: AppShellStore, useValue: {currentCenterId: () => 'center-1'}},
      ],
    });
  });

  it('charge la situation et l\'historique du centre actif', async () => {
    const store = TestBed.inject(InventaireStore);
    await store.init();

    expect(api.etat).toHaveBeenCalledWith('center-1');
    expect(api.list).toHaveBeenCalledWith('center-1', 0, 10);
    expect(store.mouvementsBloques()).toBe(true);
  });

  it('n\'autorise la clôture que si tout est compté et chaque écart justifié', async () => {
    const store = TestBed.inject(InventaireStore);
    api.get.mockReturnValue(of(inventaire({ecartsSansMotif: 1})));
    await store.load('inv-1');
    expect(store.pretACloturer()).toBe(false);

    api.get.mockReturnValue(of(inventaire({comptees: 1})));
    await store.load('inv-1');
    expect(store.pretACloturer()).toBe(false);

    api.get.mockReturnValue(of(inventaire()));
    await store.load('inv-1');
    expect(store.pretACloturer()).toBe(true);
    expect(store.progression()).toBe(100);
  });

  it('enregistre un comptage sur la ligne et remonte l\'erreur métier', async () => {
    const store = TestBed.inject(InventaireStore);
    api.get.mockReturnValue(of(inventaire()));
    await store.load('inv-1');

    api.compter.mockReturnValue(throwError(() => ({error: {detail: 'Quantité invalide'}})));
    expect(await store.compter('l-1', -1, null)).toBe(false);
    expect(store.error()).toBe('Quantité invalide');
    expect(store.savingLineId()).toBeNull();

    api.compter.mockReturnValue(of(inventaire({comptees: 2})));
    expect(await store.compter('l-1', 3, 'CASSE')).toBe(true);
    expect(api.compter).toHaveBeenLastCalledWith('center-1', 'inv-1', 'l-1', 3, 'CASSE');
  });

  it('importe la feuille remplie, met à jour l\'inventaire et conserve le bilan', async () => {
    const store = TestBed.inject(InventaireStore);
    api.get.mockReturnValue(of(inventaire({comptees: 0})));
    await store.load('inv-1');
    const bilan = {
      inventaire: inventaire({comptees: 2}), lignesMisesAJour: 2, lignesInchangees: 0, lignesVides: 0,
      anomalies: [{ligne: 7, message: 'Article X absent'}],
    };
    api.importerFeuille.mockReturnValue(of(bilan));
    const file = new File(['x'], 'feuille.xlsx');

    expect(await store.importerFeuille(file)).toEqual(bilan);
    expect(api.importerFeuille).toHaveBeenCalledWith('center-1', 'inv-1', file);
    expect(store.current()?.comptees).toBe(2);
    expect(store.importResult()?.anomalies.length).toBe(1);

    store.clearImportResult();
    expect(store.importResult()).toBeNull();
  });

  it('remonte le refus d\'une feuille invalide sans bilan', async () => {
    const store = TestBed.inject(InventaireStore);
    api.get.mockReturnValue(of(inventaire()));
    await store.load('inv-1');
    api.importerFeuille.mockReturnValue(throwError(() => ({error: {detail: 'Cette feuille concerne un autre inventaire'}})));

    expect(await store.importerFeuille(new File(['x'], 'f.xlsx'))).toBeNull();
    expect(store.error()).toBe('Cette feuille concerne un autre inventaire');
    expect(store.importResult()).toBeNull();
  });

  it('rafraîchit la situation après la clôture', async () => {
    const store = TestBed.inject(InventaireStore);
    api.get.mockReturnValue(of(inventaire()));
    await store.load('inv-1');
    api.cloturer.mockReturnValue(of(inventaire({statut: 'CLOTURE'})));
    api.etat.mockReturnValue(of({mouvementsBloques: false}));

    expect(await store.cloturer()).toBe(true);
    expect(store.enCours()).toBe(false);
    expect(store.mouvementsBloques()).toBe(false);
  });
});



