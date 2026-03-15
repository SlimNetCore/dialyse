import { Injectable, inject, signal, OnDestroy } from '@angular/core';
import { Client, IMessage } from '@stomp/stompjs';

import { AuthSessionService } from '../auth/auth-session.service';

export interface WsEvent {
  type: string;
  centerId: string;
  payload: Record<string, string>;
  timestamp: string;
}

@Injectable({ providedIn: 'root' })
export class WebSocketService implements OnDestroy {
  private readonly auth = inject(AuthSessionService);
  private client: Client | null = null;
  private connectAttempted = false;

  readonly events = signal<WsEvent[]>([]);
  readonly lastEvent = signal<WsEvent | null>(null);

  async connect(): Promise<void> {
    const centerId = this.auth.centerId();
    if (!centerId || this.client?.active || this.connectAttempted) return;

    this.connectAttempted = true;

    const backendUp = await this.isBackendUp();
    if (!backendUp) {
      // Keep UI functional even if backend WS is unavailable
      return;
    }

    this.client = new Client({
      brokerURL: 'ws://localhost:8080/ws',
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
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
      onStompError: (frame) => {
        console.error('STOMP error', frame);
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
  }

  ngOnDestroy(): void {
    this.disconnect();
  }
}
