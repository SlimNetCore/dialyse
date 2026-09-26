import {inject, Injectable, signal} from '@angular/core';
import {firstValueFrom} from 'rxjs';

import {AuthApiService} from '../api/auth-api.service';
import {AuthStore} from '../state/auth.store';
import {AppShellStore} from '../state/app-shell.store';
import {WebSocketService} from '../ws/websocket.service';

export type BackendInitState = 'waiting-server' | 'ready-for-auth' | 'server-unavailable';

@Injectable({providedIn: 'root'})
export class BackendInitService {
  readonly state = signal<BackendInitState>('waiting-server');
  private readonly auth = inject(AuthStore);
  private readonly appShellStore = inject(AppShellStore);
  private readonly websocket = inject(WebSocketService);
  private readonly authApi = inject(AuthApiService);
  private started = false;

  start(): void {
    if (this.started) {
      return;
    }
    this.started = true;
    void this.run();
  }

  private async run(): Promise<void> {
    this.state.set('waiting-server');

    const serverReady = await this.websocket.waitForInitializationSocket(240_000);
    if (!serverReady) {
      this.state.set('server-unavailable');
      return;
    }

    await this.auth.initFromServer({force: true});
    const centerId = this.auth.centerId();
    if (centerId) {
      this.appShellStore.switchCenter(centerId);
      try {
        this.appShellStore.setAvailableCenters(await firstValueFrom(this.authApi.getAccessibleCenters()));
      } catch {
        // La liste des centres est un confort : l'application reste utilisable sans elle.
      }
    }

    this.state.set('ready-for-auth');
  }
}

