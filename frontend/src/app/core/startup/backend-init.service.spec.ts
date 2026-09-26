import {TestBed} from '@angular/core/testing';
import {signal} from '@angular/core';
import {of, throwError} from 'rxjs';
import {vi} from 'vitest';

import {BackendInitService} from './backend-init.service';
import {AuthApiService} from '../api/auth-api.service';
import {AuthStore} from '../state/auth.store';
import {AppShellStore} from '../state/app-shell.store';
import {WebSocketService} from '../ws/websocket.service';

describe('BackendInitService', () => {
  const wsMock = {
    waitForInitializationSocket: vi.fn(),
  };

  const authCenterId = signal<string | null>(null);
  const authMock = {
    initFromServer: vi.fn(),
    centerId: vi.fn(() => authCenterId()),
  };

  const appShellMock = {
    switchCenter: vi.fn(),
    setAvailableCenters: vi.fn(),
  };

  const authApiMock = {
    getAccessibleCenters: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
    authCenterId.set(null);
    authApiMock.getAccessibleCenters.mockReturnValue(of([]));

    TestBed.configureTestingModule({
      providers: [
        BackendInitService,
        {provide: WebSocketService, useValue: wsMock},
        {provide: AuthStore, useValue: authMock},
        {provide: AppShellStore, useValue: appShellMock},
        {provide: AuthApiService, useValue: authApiMock},
      ],
    });
  });

  it('passe a ready-for-auth quand la socket init est recue', async () => {
    wsMock.waitForInitializationSocket.mockResolvedValue(true);
    authMock.initFromServer.mockResolvedValue(true);
    authCenterId.set('center-1');
    authApiMock.getAccessibleCenters.mockReturnValue(of([{id: 'center-1', name: 'ANNABA 1'}]));

    const service = TestBed.inject(BackendInitService);
    service.start();
    await vi.waitFor(() => expect(service.state()).toBe('ready-for-auth'));

    expect(authMock.initFromServer).toHaveBeenCalledWith({force: true});
    expect(appShellMock.switchCenter).toHaveBeenCalledWith('center-1');
    expect(appShellMock.setAvailableCenters).toHaveBeenCalledWith([{id: 'center-1', name: 'ANNABA 1'}]);
  });

  it('reste utilisable si la liste des centres ne peut pas etre chargee', async () => {
    wsMock.waitForInitializationSocket.mockResolvedValue(true);
    authMock.initFromServer.mockResolvedValue(true);
    authCenterId.set('center-1');
    authApiMock.getAccessibleCenters.mockReturnValue(throwError(() => new Error('offline')));

    const service = TestBed.inject(BackendInitService);
    service.start();
    await vi.waitFor(() => expect(service.state()).toBe('ready-for-auth'));

    expect(appShellMock.setAvailableCenters).not.toHaveBeenCalled();
  });

  it('passe a server-unavailable apres timeout socket', async () => {
    wsMock.waitForInitializationSocket.mockResolvedValue(false);

    const service = TestBed.inject(BackendInitService);
    service.start();
    await Promise.resolve();

    expect(authMock.initFromServer).not.toHaveBeenCalled();
    expect(service.state()).toBe('server-unavailable');
  });

  it('reste idempotent si start appele plusieurs fois', async () => {
    wsMock.waitForInitializationSocket.mockResolvedValue(false);

    const service = TestBed.inject(BackendInitService);
    service.start();
    service.start();
    await Promise.resolve();

    expect(wsMock.waitForInitializationSocket).toHaveBeenCalledTimes(1);
  });
});

