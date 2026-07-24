import {TestBed} from '@angular/core/testing';
import {of} from 'rxjs';
import {vi} from 'vitest';
import {MatSnackBar} from '@angular/material/snack-bar';
import {MatDialog} from '@angular/material/dialog';
import {BonsReceptionComponent} from './bons-reception.component';
import {StockApiService} from '../../core/api/stock-api.service';
import {ReferentialApiService} from '../../core/api/referential-api.service';
import {AuthStore} from '../../core/state/auth.store';
import {WebSocketService} from '../../core/ws/websocket.service';

describe('BonsReceptionComponent', () => {
  const stockApiMock = {
    listBonsReception: vi.fn(() => of([])),
    listFournisseurs: vi.fn(() => of([])),
    listEmplacements: vi.fn(() => of([])),
    listPmpRecalcLocks: vi.fn(() => of([])),
    createBonReception: vi.fn(() => of({id: 'br-1'})),
    updateBonReception: vi.fn(() => of({id: 'br-1'})),
    validerBonReception: vi.fn(() => of({id: 'br-1'})),
  };

  const refApiMock = {
    getArticles: vi.fn(() => of([
      {id: 'a-lot', code: 'AL', libelle: 'Article lot', gereParLot: true},
      {id: 'a-nolot', code: 'AN', libelle: 'Article sans lot', gereParLot: false},
    ])),
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

  const dialogMock = {
    open: vi.fn(),
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [BonsReceptionComponent],
      providers: [
        {provide: StockApiService, useValue: stockApiMock},
        {provide: ReferentialApiService, useValue: refApiMock},
        {provide: AuthStore, useValue: authMock},
        {provide: WebSocketService, useValue: wsMock},
        {provide: MatSnackBar, useValue: snackMock},
        {provide: MatDialog, useValue: dialogMock},
      ],
    }).compileComponents();
  });

  it('charge les donnees referentielles au demarrage', () => {
    const fixture = TestBed.createComponent(BonsReceptionComponent);
    fixture.detectChanges();

    expect(stockApiMock.listBonsReception).toHaveBeenCalledWith('center-1');
    expect(stockApiMock.listFournisseurs).toHaveBeenCalledWith('center-1');
    expect(stockApiMock.listEmplacements).toHaveBeenCalledWith('center-1');
    expect(refApiMock.getArticles).toHaveBeenCalledWith('center-1');
  });

  it('impose lot et peremption si article gere par lot', () => {
    const fixture = TestBed.createComponent(BonsReceptionComponent);
    const component = fixture.componentInstance as any;
    fixture.detectChanges();

    component.formModel.set({
      fournisseurId: null,
      dateReception: '2026-06-14',
      lignes: [{articleId: 'a-lot', quantite: 1, prixUnitaire: 10, numeroLot: '', datePeremption: null}],
    });
    expect(component.canSave()).toBe(false);

    component.formModel.set({
      fournisseurId: null,
      dateReception: '2026-06-14',
      lignes: [{articleId: 'a-lot', quantite: 1, prixUnitaire: 10, numeroLot: 'LOT-01', datePeremption: '2026-12-01'}],
    });
    expect(component.canSave()).toBe(true);
  });

  it('save create envoie la payload attendue', () => {
    const fixture = TestBed.createComponent(BonsReceptionComponent);
    const component = fixture.componentInstance as any;
    fixture.detectChanges();

    component.formModel.set({
      fournisseurId: 'f-1',
      dateReception: '2026-06-14',
      lignes: [{articleId: 'a-nolot', quantite: 2, prixUnitaire: 5, numeroLot: '', datePeremption: null}],
    });

    component.save();

    expect(stockApiMock.createBonReception).toHaveBeenCalledWith(
      expect.objectContaining({
        centerId: 'center-1',
        fournisseurId: 'f-1',
        userId: 'user-1',
        dateReception: '2026-06-14',
        lignes: [
          {
            articleId: 'a-nolot',
            quantite: 2,
            prixUnitaire: 5,
            numeroLot: undefined,
            datePeremption: undefined,
          },
        ],
      }),
    );
  });

  it('detecte les lignes verrouillees', () => {
    const fixture = TestBed.createComponent(BonsReceptionComponent);
    const component = fixture.componentInstance as any;
    fixture.detectChanges();

    component.lockedArticleIds.set(['a-lock']);
    component.formModel.set({
      fournisseurId: null,
      dateReception: '2026-06-14',
      lignes: [{articleId: 'a-lock', quantite: 1, prixUnitaire: 1, numeroLot: '', datePeremption: null}],
    });

    expect(component.hasLockedLines()).toBe(true);
  });
});


