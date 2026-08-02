import {TestBed} from '@angular/core/testing';
import {of} from 'rxjs';
import {vi} from 'vitest';
import {MatDialog} from '@angular/material/dialog';
import {StockDashboardComponent} from './stock-dashboard.component';
import {StockApiService} from '../../core/api/stock-api.service';
import {AuthStore} from '../../core/state/auth.store';
import {WebSocketService} from '../../core/ws/websocket.service';

describe('StockDashboardComponent', () => {
  const stockApiMock = {
    stockValorise: vi.fn(() => of([])),
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
      providers: [
        {provide: StockApiService, useValue: stockApiMock},
        {provide: AuthStore, useValue: authMock},
        {provide: WebSocketService, useValue: wsMock},
        {provide: MatDialog, useValue: dialogMock},
      ],
    });
  });

  it('charge les donnees du centre courant au demarrage', () => {
    TestBed.runInInjectionContext(() => new StockDashboardComponent());

    expect(stockApiMock.stockValorise).toHaveBeenCalledWith('center-1');
    expect(stockApiMock.alertes).toHaveBeenCalledWith('center-1');
    expect(stockApiMock.dashboardAnalytics).toHaveBeenCalledWith('center-1', 30, 10, 'VALUE');
  });

  it('ouvre le dialogue PMP avec le centerId courant', () => {
    const component = TestBed.runInInjectionContext(() => new StockDashboardComponent());

    component['openPmpExplain']({articleId: 'a-1', libelle: 'Dialyseur'} as any);

    expect(dialogMock.open).toHaveBeenCalled();
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




