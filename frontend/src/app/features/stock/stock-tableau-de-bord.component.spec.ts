import {TestBed} from '@angular/core/testing';
import {of} from 'rxjs';
import {vi} from 'vitest';
import {TranslateService} from '@ngx-translate/core';
import {StockTableauDeBordComponent} from './stock-tableau-de-bord.component';
import {StockApiService} from '../../core/api/stock-api.service';
import {AuthStore} from '../../core/state/auth.store';
import {WebSocketService} from '../../core/ws/websocket.service';

describe('StockTableauDeBordComponent', () => {
  const stockApiMock = {
    stockValorise: vi.fn(() => of([{articleId: 'a-1', quantite: 4, valeur: 100}, {articleId: 'a-2', quantite: 6, valeur: 50}])),
    alertes: vi.fn(() => of([
      {articleId: 'a-1', type: 'PEREMPTION', datePeremption: '2026-12-10'},
      {articleId: 'a-2', type: 'PEREMPTION', datePeremption: '2026-12-01'},
      {articleId: 'a-3', type: 'RUPTURE'},
    ])),
    dashboardAnalytics: vi.fn(() => of({trend: [], topArticles: []})),
  };

  const authMock = {
    centerId: vi.fn(() => 'center-1'),
    username: vi.fn(() => 'user-1'),
  };

  const wsMock = {
    lastEvent: vi.fn(() => null),
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      providers: [
        {provide: StockApiService, useValue: stockApiMock},
        {provide: AuthStore, useValue: authMock},
        {provide: WebSocketService, useValue: wsMock},
        {provide: TranslateService, useValue: {instant: (key: string) => key}},
      ],
    });
  });

  it('charge les indicateurs, les alertes et les statistiques du centre courant au demarrage', () => {
    TestBed.runInInjectionContext(() => new StockTableauDeBordComponent());

    expect(stockApiMock.stockValorise).toHaveBeenCalledWith('center-1');
    expect(stockApiMock.alertes).toHaveBeenCalledWith('center-1');
    expect(stockApiMock.dashboardAnalytics).toHaveBeenCalledWith('center-1', 30, 10, 'VALUE');
  });

  it('calcule les totaux et liste les lots en peremption du plus proche au plus lointain', () => {
    const component = TestBed.runInInjectionContext(() => new StockTableauDeBordComponent());

    expect(component['valeurTotale']()).toBe(150);
    expect(component['quantiteTotale']()).toBe(10);
    expect(component['preemptions']().map(a => a.articleId)).toEqual(['a-2', 'a-1']);
  });

  it('recharge les statistiques quand la periode change', () => {
    const component = TestBed.runInInjectionContext(() => new StockTableauDeBordComponent());

    component['onDaysChange'](90);

    expect(stockApiMock.dashboardAnalytics).toHaveBeenLastCalledWith('center-1', 90, 10, 'VALUE');
  });
});
