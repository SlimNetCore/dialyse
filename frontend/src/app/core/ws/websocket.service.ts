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
  readonly healthStatus = signal<'up' | 'down'>('up');

  readonly events = signal<WsEvent[]>([]);
  readonly lastEvent = signal<WsEvent | null>(null);
  readonly connectionStatus = signal<WsConnectionStatus>('impossible');
  private healthMonitorId: ReturnType<typeof setInterval> | null = null;
  readonly statusColor = computed(() => {
    switch (this.connectionStatus()) {
      case 'stable': return '#2e7d32';
      case 'interrupted': return '#ef6c00';
      case 'impossible': return '#c62828';
    }
  });

  constructor() {
    this.startHealthMonitor();
  }

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

  async waitForInitializationSocket(timeoutMs = 240_000): Promise<boolean> {
    const deadline = Date.now() + timeoutMs;
    while (Date.now() < deadline) {
      const backendUp = await this.isBackendUp();
      if (backendUp) {
        const connected = await this.tryInitializationSocketProbe(Math.min(10_000, Math.max(1_000, deadline - Date.now())));
        if (connected) {
          return true;
        }
      }

      await new Promise((resolve) => setTimeout(resolve, 2_000));
    }

    return false;
  }

  private async tryInitializationSocketProbe(timeoutMs: number): Promise<boolean> {
    return await new Promise<boolean>((resolve) => {
      let settled = false;
      let probeClient: Client;

      const finish = (result: boolean) => {
        if (settled) {
          return;
        }
        settled = true;
        clearTimeout(timeout);
        if (probeClient?.active) {
          void probeClient.deactivate();
        }
        resolve(result);
      };

      probeClient = new Client({
        brokerURL: this.wsBaseUrl,
        reconnectDelay: 0,
        heartbeatIncoming: 0,
        heartbeatOutgoing: 0,
        onConnect: () => {
          finish(true);
        },
        onStompError: () => {
          finish(false);
        },
        onWebSocketError: () => {
          finish(false);
        },
        onWebSocketClose: () => {
          finish(false);
        }
      });

      const timeout = setTimeout(() => {
        finish(false);
      }, timeoutMs);

      probeClient.activate();
    });
  }

  ngOnDestroy(): void {
    if (this.healthMonitorId) {
      clearInterval(this.healthMonitorId);
      this.healthMonitorId = null;
    }
    this.disconnect();
  }

  private async isBackendUp(): Promise<boolean> {
    try {
      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), 3000);
      const resp = await fetch(this.healthCheckUrl, {
        method: 'GET',
        signal: controller.signal
      });
      clearTimeout(timeout);
      // Même protégé (401/403), le backend est joignable.
      return resp.ok || resp.status === 401 || resp.status === 403;
    } catch {
      return false;
    }
  }

  private startHealthMonitor(): void {
    void this.refreshHealthStatus();
    this.healthMonitorId = setInterval(() => {
      void this.refreshHealthStatus();
    }, 5_000);
  }

  disconnect(): void {
    if (this.client?.active) {
      this.client.deactivate();
    }
    this.connectionStatus.set('impossible');
  }

  private async refreshHealthStatus(): Promise<void> {
    const backendUp = await this.isBackendUp();
    this.healthStatus.set(backendUp ? 'up' : 'down');
  }

  private buildDefaultWsBaseUrl(): string {
    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
    return `${protocol}://${window.location.host}/ws`;
  }
}
