import {TestBed} from '@angular/core/testing';
import {By} from '@angular/platform-browser';
import {of} from 'rxjs';
import {vi} from 'vitest';
import {MatSnackBar} from '@angular/material/snack-bar';
import {TranslateModule} from '@ngx-translate/core';
import {NgTableComponent} from '@sbourahla/ng-table';
import {BonsCommandeComponent, TONS_STATUT_BON_COMMANDE} from './bons-commande.component';
import {StockApiService} from '../../core/api/stock-api.service';
import {ReferentialApiService} from '../../core/api/referential-api.service';
import {AuthStore} from '../../core/state/auth.store';
import {WebSocketService} from '../../core/ws/websocket.service';

describe('BonsCommandeComponent', () => {
  const brouillon = {
    id: 'bc-1', centerId: 'center-1', reference: 'BC-001', statut: 'BROUILLON',
    lignes: [{articleId: 'a1', quantite: 2, prixUnitaire: 5}, {articleId: 'a2', quantite: 1, prixUnitaire: 3}],
  };
  const valide = {...brouillon, id: 'bc-2', reference: 'BC-002', statut: 'VALIDE'};
  const recu = {...brouillon, id: 'bc-3', reference: 'BC-003', statut: 'RECU'};

  const page = (items: any[], index = 0) => of({items, total: items.length, page: index, size: 20});
  const stockApiMock = {
    listBonsCommandePage: vi.fn((_c: string, _p: Record<string, string>) => page([])),
    listFournisseurs: vi.fn(() => of([])),
    createBonCommande: vi.fn(() => of({id: 'bc-new'})),
    validerBonCommande: vi.fn(() => of({id: 'bc-1'})),
    fromBonCommande: vi.fn(() => of({id: 'br-1'})),
  };
  const refApiMock = {getArticles: vi.fn(() => of([{id: 'a1', code: 'A1', libelle: 'Article 1'}]))};
  const authMock = {centerId: vi.fn(() => 'center-1'), username: vi.fn(() => 'user-1')};
  const wsMock = {lastEvent: vi.fn(() => null)};
  const snackMock = {open: vi.fn()};

  beforeEach(async () => {
    vi.clearAllMocks();
    stockApiMock.listBonsCommandePage.mockImplementation(() => page([]));
    await TestBed.configureTestingModule({
      imports: [BonsCommandeComponent, TranslateModule.forRoot()],
      providers: [
        {provide: StockApiService, useValue: stockApiMock},
        {provide: ReferentialApiService, useValue: refApiMock},
        {provide: AuthStore, useValue: authMock},
        {provide: WebSocketService, useValue: wsMock},
        {provide: MatSnackBar, useValue: snackMock},
      ],
    }).compileComponents();
  });

  it('charge bons, fournisseurs et articles du centre courant', () => {
    const fixture = TestBed.createComponent(BonsCommandeComponent);
    fixture.detectChanges();

    expect(stockApiMock.listBonsCommandePage).toHaveBeenCalledWith('center-1', {page: '0', size: '20'});
    expect(stockApiMock.listFournisseurs).toHaveBeenCalledWith('center-1');
    expect(refApiMock.getArticles).toHaveBeenCalledWith('center-1');
  });

  it('declare les colonnes avec le statut en pastille teintee et les actions hors export', () => {
    const fixture = TestBed.createComponent(BonsCommandeComponent);
    const component = fixture.componentInstance as any;
    fixture.detectChanges();

    const colonnes = component.colonnes();
    expect(colonnes.map((c: any) => c.id)).toEqual(['reference', 'statut', 'lignes', 'actions']);
    expect(colonnes[0].valueAccessor(brouillon)).toBe('BC-001');
    expect(colonnes[1].cell.type).toBe('badge');
    expect(colonnes[1].cell.tone).toEqual(TONS_STATUT_BON_COMMANDE);
    expect(colonnes[1].cell.label('VALIDE')).toBe('STOCK.BON_STATUT.VALIDE');
    expect(colonnes[1].exportValueAccessor(recu)).toBe('STOCK.BON_STATUT.RECU');
    expect(colonnes[2].valueAccessor(brouillon)).toBe(2);
    expect(colonnes[3].exportable).toBe(false);
    expect(colonnes[3].cellTemplate).toBeTruthy();
  });

  it('pagine côté serveur par 20 avec choix 10/20/50/100', () => {
    const fixture = TestBed.createComponent(BonsCommandeComponent);
    fixture.detectChanges();

    const table = fixture.debugElement.query(By.directive(NgTableComponent)).componentInstance as NgTableComponent<unknown>;
    expect(table.paginator()).toBe(true);
    expect(table.dataMode()).toBe('remote');
    expect(table.pageSize()).toBe(20);
    expect(table.pageSizeOptions()).toEqual([10, 20, 50, 100]);
  });

  it('le filtre statut ne propose que les statuts du serveur (pas d\'ANNULE) et part au serveur', () => {
    const fixture = TestBed.createComponent(BonsCommandeComponent);
    const component = fixture.componentInstance as any;
    fixture.detectChanges();
    const statut = component.colonnes().find((c: any) => c.id === 'statut');
    expect(statut.filter.options.map((o: any) => o.value)).toEqual(['BROUILLON', 'VALIDE', 'RECU']);

    component.onRemoteQueryChange({
      sort: {columnId: 'reference', direction: 'asc'}, filters: {statut: 'VALIDE,RECU'}, page: {index: 0, size: 20},
    });
    expect(stockApiMock.listBonsCommandePage).toHaveBeenLastCalledWith('center-1',
      {page: '0', size: '20', sort: 'reference,asc', statut: 'VALIDE,RECU'});
  });

  it('recharge la page courante après une validation', () => {
    stockApiMock.listBonsCommandePage.mockReturnValue(page([brouillon]));
    const fixture = TestBed.createComponent(BonsCommandeComponent);
    const component = fixture.componentInstance as any;
    fixture.detectChanges();
    component.onRemoteQueryChange({sort: {columnId: '', direction: ''}, filters: {}, page: {index: 2, size: 20}});
    component.valider(brouillon);
    expect(stockApiMock.listBonsCommandePage).toHaveBeenLastCalledWith('center-1', {page: '2', size: '20'});
  });

  it('les actions de ligne restent branchees selon le statut', async () => {
    stockApiMock.listBonsCommandePage.mockReturnValue(page([brouillon, valide, recu]));
    const fixture = TestBed.createComponent(BonsCommandeComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const boutons = Array.from(fixture.nativeElement.querySelectorAll('ng-table .action-buttons button')) as HTMLButtonElement[];
    expect(boutons.length).toBe(2);

    boutons[0].click();
    expect(stockApiMock.validerBonCommande).toHaveBeenCalledWith('bc-1', 'center-1', 'user-1');

    boutons[1].click();
    expect(stockApiMock.fromBonCommande).toHaveBeenCalledWith({
      centerId: 'center-1', bonCommandeId: 'bc-2', userId: 'user-1',
    });
  });
});
