import {TestBed} from '@angular/core/testing';
import {of} from 'rxjs';
import {vi} from 'vitest';
import {MatSnackBar} from '@angular/material/snack-bar';
import {BonsSortieComponent} from './bons-sortie.component';
import {StockApiService} from '../../core/api/stock-api.service';
import {ReferentialApiService} from '../../core/api/referential-api.service';
import {AuthStore} from '../../core/state/auth.store';
import {WebSocketService} from '../../core/ws/websocket.service';

describe('BonsSortieComponent', () => {
  const stockApiMock = {
    listBonsSortie: vi.fn(() => of([])),
    listLotsDisponibles: vi.fn(() => of([])),
    listPmpRecalcLocks: vi.fn(() => of([])),
    createBonSortie: vi.fn(() => of({id: 'bs-1'})),
    updateBonSortie: vi.fn(() => of({id: 'bs-1'})),
  };

  const refApiMock = {
    getArticles: vi.fn(() => of([{id: 'a1', code: 'A1', libelle: 'Article 1', unite: 'u'}])),
  };

  const authMock = {
    centerId: vi.fn(() => 'center-1'),
    username: vi.fn(() => 'user-1'),
  };

  const wsMock = {
    lastEvent: vi.fn(() => null),
  };

  const snackMock = {
    open: vi.fn(),
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [BonsSortieComponent],
      providers: [
        {provide: StockApiService, useValue: stockApiMock},
        {provide: ReferentialApiService, useValue: refApiMock},
        {provide: AuthStore, useValue: authMock},
        {provide: WebSocketService, useValue: wsMock},
        {provide: MatSnackBar, useValue: snackMock},
      ],
    }).compileComponents();
  });

  it('charge les donnees initiales du centre courant', () => {
    const fixture = TestBed.createComponent(BonsSortieComponent);
    fixture.detectChanges();

    expect(stockApiMock.listBonsSortie).toHaveBeenCalledWith('center-1');
    expect(refApiMock.getArticles).toHaveBeenCalledWith('center-1');
    expect(stockApiMock.listPmpRecalcLocks).toHaveBeenCalledWith('center-1');
  });

  it('calcule canSave selon la validite du modele', () => {
    const fixture = TestBed.createComponent(BonsSortieComponent);
    const component = fixture.componentInstance as any;
    fixture.detectChanges();

    component.formModel.set({
      dateSortie: '2026-06-14',
      items: [{articleId: 'a1', lotId: 'l1', quantite: 2}],
    });
    expect(component.canSave()).toBe(true);

    component.formModel.set({
      dateSortie: '2026-06-14',
      items: [{articleId: 'a1', lotId: null, quantite: 2}],
    });
    expect(component.canSave()).toBe(false);
  });

  it('envoie la payload attendue sur save', () => {
    const fixture = TestBed.createComponent(BonsSortieComponent);
    const component = fixture.componentInstance as any;
    fixture.detectChanges();

    component.formModel.set({
      dateSortie: '2026-06-14',
      items: [{articleId: 'a1', lotId: 'l1', quantite: 1.5}],
    });

    component.save();

    expect(stockApiMock.createBonSortie).toHaveBeenCalledWith(
      expect.objectContaining({
        centerId: 'center-1',
        userId: 'user-1',
        dateSortie: '2026-06-14',
        items: [{articleId: 'a1', lotId: 'l1', quantite: 1.5}],
      }),
    );
    expect(snackMock.open).toHaveBeenCalled();
  });

  it('edite un bon existant via updateBonSortie', () => {
    stockApiMock.listBonsSortie.mockReturnValueOnce(of([{
      id: 'bs-1',
      centerId: 'center-1',
      reference: 'BS-001',
      seanceId: 'seance-1',
      patientId: 'patient-1',
      poste: 'SEANCE',
      dateSortie: '2026-06-14',
      lignes: [{articleId: 'a1', lotId: 'l1', quantite: 2}],
    }] as any));
    stockApiMock.listLotsDisponibles.mockReturnValueOnce(of([{id: 'l1', numeroLot: 'LOT-1'}] as any));

    const fixture = TestBed.createComponent(BonsSortieComponent);
    const component = fixture.componentInstance as any;
    fixture.detectChanges();

    component.editBon(component.bons()[0]);
    component.save();

    expect(stockApiMock.updateBonSortie).toHaveBeenCalledWith(
      'bs-1',
      expect.objectContaining({
        centerId: 'center-1',
        seanceId: 'seance-1',
        patientId: 'patient-1',
        poste: 'SEANCE',
      }),
    );
  });

  it('detecte les lignes verrouillees', () => {
    const fixture = TestBed.createComponent(BonsSortieComponent);
    const component = fixture.componentInstance as any;
    fixture.detectChanges();

    component.lockedArticleIds.set(['a-lock']);
    component.formModel.set({
      dateSortie: '2026-06-14',
      items: [{articleId: 'a-lock', lotId: 'l1', quantite: 1}],
    });

    expect(component.hasLockedItems()).toBe(true);
  });
});


