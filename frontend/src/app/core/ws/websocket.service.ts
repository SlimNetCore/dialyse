import { Injectable, inject, signal, computed, OnDestroy } from '@angular/core';
import { Client, IMessage } from '@stomp/stompjs';

import { AuthSessionService } from '../auth/auth-session.service';

export interface WsEvent {
  type: string;
  centerId: string;
  payload: Record<string, string>;
  timestamp: string;
}

export type WsConnectionStatus = 'stable' | 'interrupted' | 'impossible';

@Injectable({ providedIn: 'root' })
export class WebSocketService implements OnDestroy {
  private readonly auth = inject(AuthSessionService);
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
      return;
    }

    this.client = new Client({
      brokerURL: 'ws://localhost:8080/ws',
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
      const resp = await fetch('http://localhost:8080/actuator/health', {
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
}
