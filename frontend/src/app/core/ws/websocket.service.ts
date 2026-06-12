import {computed, inject, Injectable, OnDestroy, signal} from '@angular/core';
import {Client, IMessage} from '@stomp/stompjs';

import {AuthStore} from '../state/auth.store';
import {environment} from '../../../environments/environment';

export interface WsEvent {
  type: string;
  centerId: string;
  payload: Record<string, string>;
  timestamp: string;
}

export type WsConnectionStatus = 'stable' | 'interrupted' | 'impossible';

@Injectable({ providedIn: 'root' })
export class WebSocketService implements OnDestroy {
  private readonly auth = inject(AuthStore);
  private readonly wsBaseUrl = environment.wsBaseUrl ?? this.buildDefaultWsBaseUrl();
  private readonly healthCheckUrl = environment.healthCheckUrl;
  private client: Client | null = null;
  private connectAttempted = false;

  readonly events = signal<WsEvent[]>([]);
  readonly lastEvent = signal<WsEvent | null>(null);
  readonly connectionStatus = signal<WsConnectionStatus>('impossible');
  readonly statusColor = computed(() => {
    switch (this.connectionStatus()) {
      case 'stable': return '#2e7d32';
      case 'interrupted': return '#ef6c00';
      case 'impossible': return '#c62828';
    }
  });

  async connect(): Promise<void> {
    const centerId = this.auth.centerId();
    if (!centerId || this.client?.active || this.connectAttempted) return;

    this.connectAttempted = true;

    const backendUp = await this.isBackendUp();
    if (!backendUp) {
      this.connectionStatus.set('impossible');
      // Allow a later call (navigation / retry) to attempt again once backend is up.
      this.connectAttempted = false;
      return;
    }

    this.client = new Client({
      brokerURL: this.wsBaseUrl,
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        this.connectionStatus.set('stable');
        this.client!.subscribe(`/topic/center/${centerId}/events`, (message: IMessage) => {
          try {
            const evt: WsEvent = JSON.parse(message.body);
            this.lastEvent.set(evt);
            this.events.update(list => [evt, ...list].slice(0, 50));
          } catch {
            // ignore parse errors
          }
        });
      },
      onStompError: () => {
        this.connectionStatus.set('interrupted');
      },
      onWebSocketClose: () => {
        this.connectionStatus.set('interrupted');
      },
      onDisconnect: () => {
        this.connectionStatus.set('interrupted');
      }
    });

    this.client.activate();
  }

  private async isBackendUp(): Promise<boolean> {
    try {
      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), 1500);
      const resp = await fetch(this.healthCheckUrl, {
        method: 'GET',
        signal: controller.signal
      });
      clearTimeout(timeout);
      return resp.ok;
    } catch {
      return false;
    }
  }

  disconnect(): void {
    if (this.client?.active) {
      this.client.deactivate();
    }
    this.connectionStatus.set('impossible');
  }

  ngOnDestroy(): void {
    this.disconnect();
  }

  private buildDefaultWsBaseUrl(): string {
    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
    return `${protocol}://${window.location.host}/ws`;
  }
}
