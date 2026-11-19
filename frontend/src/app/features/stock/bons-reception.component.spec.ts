import {TestBed} from '@angular/core/testing';
import {By} from '@angular/platform-browser';
import {of} from 'rxjs';
import {afterEach, vi} from 'vitest';
import {MatSnackBar} from '@angular/material/snack-bar';
import {MatDialog} from '@angular/material/dialog';
import {TranslateModule} from '@ngx-translate/core';
import {NgTableComponent} from '@sbourahla/ng-table';
import {BonsReceptionComponent, TONS_STATUT_BON_RECEPTION} from './bons-reception.component';
import {RELECTURE_DES_VERROUS_MS, StockApiService} from '../../core/api/stock-api.service';
import {ReferentialApiService} from '../../core/api/referential-api.service';
import {AuthStore} from '../../core/state/auth.store';
import {WebSocketService} from '../../core/ws/websocket.service';

describe('BonsReceptionComponent', () => {
  const page = (items: any[], index = 0) => of({items, total: items.length, page: index, size: 20});
  const stockApiMock = {
    listBonsReceptionPage: vi.fn((_c: string, _p: Record<string, string>) => page([])),
    listFournisseurs: vi.fn(() => of([])),
    listEmplacements: vi.fn(() => of([])),
    listPmpRecalcLocks: vi.fn(() => of([] as string[])),
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
    stockApiMock.listBonsReceptionPage.mockImplementation(() => page([]));
    await TestBed.configureTestingModule({
      imports: [BonsReceptionComponent, TranslateModule.forRoot()],
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

    expect(stockApiMock.listBonsReceptionPage).toHaveBeenCalledWith('center-1', {page: '0', size: '20'});
    expect(stockApiMock.listFournisseurs).toHaveBeenCalledWith('center-1');
    expect(stockApiMock.listEmplacements).toHaveBeenCalledWith('center-1');
    expect(refApiMock.getArticles).toHaveBeenCalledWith('center-1');
  });

  describe('verrous de recalcul du PMP', () => {
    afterEach(() => {
      vi.useRealTimers();
      stockApiMock.listPmpRecalcLocks.mockReturnValue(of([]));
    });

    it('ne relit pas les verrous en continu quand aucun recalcul n\'est en cours', () => {
      vi.useFakeTimers();
      const fixture = TestBed.createComponent(BonsReceptionComponent);
      fixture.detectChanges();

      vi.advanceTimersByTime(RELECTURE_DES_VERROUS_MS * 6);

      expect(stockApiMock.listPmpRecalcLocks).toHaveBeenCalledTimes(1);
    });

    it('relit les verrous pendant un recalcul et s\'arrête quand l\'écran est quitté', () => {
      vi.useFakeTimers();
      stockApiMock.listPmpRecalcLocks.mockReturnValue(of(['a-lot']));
      const fixture = TestBed.createComponent(BonsReceptionComponent);
      fixture.detectChanges();

      vi.advanceTimersByTime(RELECTURE_DES_VERROUS_MS);
      expect(stockApiMock.listPmpRecalcLocks).toHaveBeenCalledTimes(2);

      fixture.destroy();
      vi.advanceTimersByTime(RELECTURE_DES_VERROUS_MS * 5);
      expect(stockApiMock.listPmpRecalcLocks).toHaveBeenCalledTimes(2);
    });
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

  describe('liste ng-table', () => {
    const brouillon = {
      id: 'br-9', centerId: 'center-1', reference: 'BR-009', dateReception: '2026-09-01', statut: 'BROUILLON',
      lignes: [{articleId: 'a-nolot', quantite: 2, prixUnitaire: 5}],
    };
    const valide = {...brouillon, id: 'br-10', reference: 'BR-010', statut: 'VALIDE'};

    it('declare les colonnes avec le statut en pastille teintee et les actions hors export', () => {
      const fixture = TestBed.createComponent(BonsReceptionComponent);
      const component = fixture.componentInstance as any;
      fixture.detectChanges();

      const colonnes = component.colonnes();
      expect(colonnes.map((c: any) => c.id)).toEqual(['reference', 'date', 'statut', 'lignes', 'actions']);
      const statut = colonnes[2];
      expect(statut.cell.type).toBe('badge');
      expect(statut.cell.tone).toEqual(TONS_STATUT_BON_RECEPTION);
      expect(statut.cell.label('BROUILLON')).toBe('STOCK.BON_STATUT.BROUILLON');
      // Régression KB-STK-58 : seuls les statuts du serveur (le RECU historique est lu VALIDE), pas d'ANNULE.
      expect(statut.filter.options.map((o: any) => o.value)).toEqual(['BROUILLON', 'VALIDE']);
      expect(Object.keys(TONS_STATUT_BON_RECEPTION)).toEqual(['BROUILLON', 'VALIDE']);
      expect(colonnes[3].valueAccessor(brouillon)).toBe(1);
      expect(colonnes[4].exportable).toBe(false);
    });

    it('pagine côté serveur par 20 avec choix 10/20/50/100', () => {
      const fixture = TestBed.createComponent(BonsReceptionComponent);
      fixture.detectChanges();

      const table = fixture.debugElement.query(By.directive(NgTableComponent)).componentInstance as NgTableComponent<unknown>;
      expect(table.paginator()).toBe(true);
      expect(table.dataMode()).toBe('remote');
      expect(table.pageSize()).toBe(20);
      expect(table.pageSizeOptions()).toEqual([10, 20, 50, 100]);
    });

    it('envoie tri (date ⇒ dateReception) et filtres au serveur, repart de la page 0', () => {
      const fixture = TestBed.createComponent(BonsReceptionComponent);
      const component = fixture.componentInstance as any;
      fixture.detectChanges();
      component.onRemoteQueryChange({sort: {columnId: '', direction: ''}, filters: {}, page: {index: 3, size: 20}});
      component.onRemoteQueryChange({
        sort: {columnId: 'date', direction: 'desc'}, filters: {reference: 'BR', statut: 'VALIDE'},
        page: {index: 3, size: 20},
      });
      expect(stockApiMock.listBonsReceptionPage).toHaveBeenLastCalledWith('center-1',
        {page: '0', size: '20', sort: 'dateReception,desc', reference: 'BR', statut: 'VALIDE'});
    });

    it('recharge la page courante après une validation', () => {
      const fixture = TestBed.createComponent(BonsReceptionComponent);
      const component = fixture.componentInstance as any;
      fixture.detectChanges();
      component.onRemoteQueryChange({sort: {columnId: '', direction: ''}, filters: {}, page: {index: 2, size: 20}});
      component.valider(brouillon);
      expect(stockApiMock.listBonsReceptionPage).toHaveBeenLastCalledWith('center-1', {page: '2', size: '20'});
    });

    it('les actions de ligne restent branchees : modifier et valider le brouillon seulement', async () => {
      stockApiMock.listBonsReceptionPage.mockReturnValue(page([brouillon, valide]));
      const fixture = TestBed.createComponent(BonsReceptionComponent);
      const component = fixture.componentInstance as any;
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      const actionsBrouillon = fixture.nativeElement.querySelectorAll('[data-row-id="br-9"] button');
      const actionsValide = fixture.nativeElement.querySelectorAll('[data-row-id="br-10"] button');
      expect(actionsBrouillon.length).toBe(2);
      expect(actionsValide.length).toBe(1);

      (actionsBrouillon[1] as HTMLButtonElement).click();
      expect(stockApiMock.validerBonReception).toHaveBeenCalledWith('br-9', 'center-1', 'user-1');

      (actionsValide[0] as HTMLButtonElement).click();
      expect(component.editingId()).toBe('br-10');
      expect(component.editingStatus()).toBe('VALIDE');
    });
  });
});


