import {provideZonelessChangeDetection} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {HttpErrorResponse} from '@angular/common/http';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {of, throwError} from 'rxjs';
import {ComptabiliteApiService, ModelePieceItem, PieceItem} from '../../../core/api/comptabilite-api.service';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {PiecesComptablesStore} from './pieces-comptables.store';

const CENTRE = '11111111-1111-1111-1111-111111111111';

const loyer: ModelePieceItem = {
  id: 'm1', code: 'LOYER', libelle: 'Loyer', journal: 'AC', actif: true,
  lignes: [{sens: 'DEBIT', compte: '613'}, {sens: 'CREDIT', compte: '401'}],
};
const piece: PieceItem = {
  id: 'e1', numeroPiece: 'AC-2026-000001', journalCode: 'AC', dateEcriture: '2026-10-05', libelle: 'Loyer', total: 100,
};

describe('PiecesComptablesStore', () => {
  let api: Record<string, ReturnType<typeof vi.fn>>;

  beforeEach(() => {
    api = {
      getModeles: vi.fn().mockImplementation((_c: string, page: number, size: number) =>
        of({items: [loyer], total: 1, page, size})),
      createModele: vi.fn().mockReturnValue(of(loyer)),
      updateModele: vi.fn().mockReturnValue(of(loyer)),
      deleteModele: vi.fn().mockReturnValue(of(undefined)),
      saisirPiece: vi.fn().mockReturnValue(of(piece)),
      extournerPiece: vi.fn().mockReturnValue(of({...piece, id: 'e2', numeroPiece: 'AC-2026-000002'})),
    };
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), {provide: ComptabiliteApiService, useValue: api}],
    });
    TestBed.inject(AppShellStore).switchCenter(CENTRE);
  });

  it('charge la page de modèles du centre actif, et à part les modèles actifs pour la saisie', () => {
    const store = TestBed.inject(PiecesComptablesStore);

    store.load();
    expect(api['getModeles']).toHaveBeenCalledWith(CENTRE, 0, 10);
    expect(store.rows()).toHaveLength(1);
    expect(store.isEmpty()).toBe(false);

    store.loadActifs();
    expect(api['getModeles']).toHaveBeenCalledWith(CENTRE, 0, 100, true);
    expect(store.actifs()).toEqual([loyer]);
  });

  it('crée un modèle sans identifiant, modifie celui qui en a un, puis recharge la liste', () => {
    const store = TestBed.inject(PiecesComptablesStore);
    const {id: _id, ...modele} = loyer;

    store.saveModele({id: null, modele});
    expect(api['createModele']).toHaveBeenCalledWith(CENTRE, modele);
    expect(store.successMessage()).toBe('COMPTABILITE.MODELES.ENREGISTRE');

    store.saveModele({id: 'm1', modele: {...modele, actif: false}});
    expect(api['updateModele']).toHaveBeenCalledWith(CENTRE, 'm1', {...modele, actif: false});

    store.deleteModele('m1');
    expect(api['deleteModele']).toHaveBeenCalledWith(CENTRE, 'm1');
    expect(store.successMessage()).toBe('COMPTABILITE.MODELES.SUPPRIME');
    expect(api['getModeles']).toHaveBeenCalledTimes(3);
  });

  it('saisit une pièce pour le centre actif et garde la pièce créée pour la confirmer', () => {
    const store = TestBed.inject(PiecesComptablesStore);

    store.saisirPiece({modeleId: 'm1', date: '2026-10-05', libelle: null, montants: [100, 100]});

    expect(api['saisirPiece']).toHaveBeenCalledWith(CENTRE,
      {modeleId: 'm1', date: '2026-10-05', libelle: null, montants: [100, 100]});
    expect(store.dernierePiece()?.numeroPiece).toBe('AC-2026-000001');
    expect(store.saving()).toBe(false);
  });

  it('traduit le refus d\'une pièce déséquilibrée sans annoncer de pièce créée', () => {
    api['saisirPiece'].mockReturnValue(
      throwError(() => new HttpErrorResponse({status: 422, error: {code: 'ECRITURE_DESEQUILIBREE'}})));
    const store = TestBed.inject(PiecesComptablesStore);

    store.saisirPiece({modeleId: 'm1', date: '2026-10-05', libelle: null, montants: [100, 90]});

    expect(store.error()).toBe('COMPTABILITE.PARAMETRAGE.ERR.ECRITURE_DESEQUILIBREE');
    expect(store.dernierePiece()).toBeNull();
  });

  it('extourne une pièce et expose l\'écriture inverse ; un second essai est refusé par le serveur', () => {
    const store = TestBed.inject(PiecesComptablesStore);

    store.extournerPiece('e1');
    expect(api['extournerPiece']).toHaveBeenCalledWith(CENTRE, 'e1');
    expect(store.dernierePiece()?.numeroPiece).toBe('AC-2026-000002');

    api['extournerPiece'].mockReturnValue(
      throwError(() => new HttpErrorResponse({status: 422, error: {code: 'PIECE_DEJA_EXTOURNEE'}})));
    store.extournerPiece('e1');
    expect(store.error()).toBe('COMPTABILITE.PARAMETRAGE.ERR.PIECE_DEJA_EXTOURNEE');
    expect(store.dernierePiece()).toBeNull();

    store.clearMessages();
    expect(store.error()).toBeNull();
  });
});
