import {TestBed} from '@angular/core/testing';
import {signal} from '@angular/core';
import {vi} from 'vitest';

import {BackendInitService} from './backend-init.service';
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
  };

  beforeEach(() => {
    vi.clearAllMocks();
    authCenterId.set(null);

    TestBed.configureTestingModule({
      providers: [
        BackendInitService,
        {provide: WebSocketService, useValue: wsMock},
        {provide: AuthStore, useValue: authMock},
        {provide: AppShellStore, useValue: appShellMock},
      ],
    });
  });

  it('passe a ready-for-auth quand la socket init est recue', async () => {
    wsMock.waitForInitializationSocket.mockResolvedValue(true);
    authMock.initFromServer.mockResolvedValue(true);
    authCenterId.set('center-1');

    const service = TestBed.inject(BackendInitService);
    service.start();
    await Promise.resolve();
    await Promise.resolve();

    expect(authMock.initFromServer).toHaveBeenCalledWith({force: true});
    expect(appShellMock.switchCenter).toHaveBeenCalledWith('center-1');
    expect(service.state()).toBe('ready-for-auth');
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

