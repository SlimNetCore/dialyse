import {TestBed} from '@angular/core/testing';
import {of} from 'rxjs';
import {vi} from 'vitest';
import {MatDialog} from '@angular/material/dialog';
import {TranslateModule} from '@ngx-translate/core';
import {EtatDuStockComponent, tonDeLaQuantite} from './etat-du-stock.component';
import {StockApiService} from '../../core/api/stock-api.service';
import {AuthStore} from '../../core/state/auth.store';
import {WebSocketService} from '../../core/ws/websocket.service';

describe('EtatDuStockComponent', () => {
  const stockApiMock = {
    stockValorise: vi.fn(() => of([{articleId: 'a-1', code: 'DIA', libelle: 'Dialyseur', unite: 'u', quantite: 3, pmpCourant: 10, valeur: 30}])),
    alertes: vi.fn(() => of([])),
    dashboardAnalytics: vi.fn(() => of({trend: [], topArticles: []})),
  };

  const authMock = {
    centerId: vi.fn(() => 'center-1'),
    username: vi.fn(() => 'user-1'),
  };

  const wsMock = {
    lastEvent: vi.fn(() => null),
  };

  const dialogMock = {
    open: vi.fn(),
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [EtatDuStockComponent, TranslateModule.forRoot()],
      providers: [
        {provide: StockApiService, useValue: stockApiMock},
        {provide: AuthStore, useValue: authMock},
        {provide: WebSocketService, useValue: wsMock},
        {provide: MatDialog, useValue: dialogMock},
      ],
    }).compileComponents();
  });

  function creer() {
    const fixture = TestBed.createComponent(EtatDuStockComponent);
    fixture.detectChanges();
    return {fixture, c: fixture.componentInstance as any};
  }

  it('charge le stock valorise du centre courant sans les statistiques ni les alertes du tableau de bord', () => {
    const {c} = creer();

    expect(stockApiMock.stockValorise).toHaveBeenCalledWith('center-1');
    expect(c.stock().map((a: { articleId: string }) => a.articleId)).toEqual(['a-1']);
    expect(stockApiMock.alertes).not.toHaveBeenCalled();
    expect(stockApiMock.dashboardAnalytics).not.toHaveBeenCalled();
  });

  it('affiche le stock dans une ng-table locale paginee (20 par page, 10/20/50/100), sans configurable-list', () => {
    const {fixture, c} = creer();
    const el: HTMLElement = fixture.nativeElement;

    expect(el.querySelector('ng-table')).not.toBeNull();
    expect(el.querySelector('app-configurable-list')).toBeNull();
    expect(c.defaultPageSize).toBe(20);
    expect(c.pageSizes).toEqual([10, 20, 50, 100]);
    expect(el.textContent).toContain('Dialyseur');
  });

  it('declare les colonnes du stock, montants via gabarit et actions exclues de l export', () => {
    const {c} = creer();
    const colonnes = c.stockColumns();

    expect(colonnes.map((col: { id: string }) => col.id)).toEqual(['code', 'libelle', 'quantite', 'pmp', 'valeur', 'actions']);
    const parId = (id: string) => colonnes.find((col: { id: string }) => col.id === id);
    expect(parId('quantite').cellTemplate).toBeTruthy();
    expect(parId('valeur').cellTemplate).toBeTruthy();
    expect(parId('actions').exportable).toBe(false);
    expect(parId('valeur').valueAccessor({valeur: 30})).toBe(30);
  });

  it('teinte la quantite en rupture en rouge', () => {
    expect(tonDeLaQuantite(0)).toBe('danger');
    expect(tonDeLaQuantite(-1)).toBe('danger');
    expect(tonDeLaQuantite(4)).toBe('success');
  });

  it('ouvre le dialogue PMP avec le centerId courant depuis le bouton de la ligne', () => {
    const {fixture} = creer();
    const bouton = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button'))
      .find((b) => b.textContent?.includes('STOCK.DASHBOARD.STOCK_TABLE.EXPLAIN_PMP')) as HTMLButtonElement;
    expect(bouton).toBeTruthy();

    bouton.click();

    expect(dialogMock.open).toHaveBeenCalledWith(
      expect.anything(),
      expect.objectContaining({
        width: 'min(1200px, calc(100vw - 24px))',
        maxWidth: 'calc(100vw - 24px)',
        maxHeight: 'calc(100dvh - 24px)',
        data: {
          articleId: 'a-1',
          centerId: 'center-1',
          libelle: 'Dialyseur',
        },
      }),
    );
  });
});
