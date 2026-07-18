import {ChangeDetectionStrategy, Component, computed, inject} from '@angular/core';
import {MatIconModule} from '@angular/material/icon';
import {MatButtonModule} from '@angular/material/button';
import {MatBadgeModule} from '@angular/material/badge';
import {MatTooltipModule} from '@angular/material/tooltip';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {WebSocketService, WsEvent} from '../ws/websocket.service';
import {DatePipe, JsonPipe} from '@angular/common';
import {NotificationBellStore} from '../state/notification-bell.store';

@Component({
  selector: 'app-notification-bell',
  standalone: true,
  imports: [
    MatIconModule,
    MatButtonModule,
    MatBadgeModule,
    MatTooltipModule,
    TranslateModule,
    DatePipe,
    JsonPipe,
  ],
  templateUrl: './notification-bell.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './notification-bell.component.css',
})
export class NotificationBellComponent {
  readonly ws = inject(WebSocketService);
  private readonly notifStore = inject(NotificationBellStore);
  private readonly translate = inject(TranslateService);

  readonly open = this.notifStore.open;
  readonly activeTab = this.notifStore.activeTab;
  readonly selectedEventId = this.notifStore.selectedEventId;
  readonly unreadEvents = this.notifStore.unreadEvents;
  readonly readEvents = this.notifStore.readEvents;
  readonly visibleEvents = this.notifStore.visibleEvents;
  readonly selectedEvent = this.notifStore.selectedEvent;
  readonly unreadCount = this.notifStore.unreadCount;
  readonly wsTooltip = computed(() => {
    switch (this.ws.connectionStatus()) {
      case 'stable':
        return this.translate.instant('NOTIFICATION.WS_STABLE');
      case 'interrupted':
        return this.translate.instant('NOTIFICATION.WS_INTERRUPTED');
      case 'impossible':
        return this.translate.instant('NOTIFICATION.WS_IMPOSSIBLE');
    }
  });

  togglePanel(): void {
    this.notifStore.togglePanel();
  }

  closePanel(): void {
    this.notifStore.closePanel();
  }

  setActiveTab(tab: 'unread' | 'read'): void {
    this.notifStore.setActiveTab(tab);
  }

  eventId(evt: WsEvent): string {
    return this.notifStore.eventId(evt);
  }

  isRead(evt: WsEvent): boolean {
    return this.notifStore.isRead(evt);
  }

  selectMessage(evt: WsEvent): void {
    this.notifStore.selectMessage(evt);
  }

  markAllRead(): void {
    this.notifStore.markAllRead();
  }

  iconFor(evt: WsEvent): string {
    switch (evt.type) {
      case 'PATIENT_CREATED':
        return 'person_add';
      case 'PEC_VALIDATED':
        return 'verified';
      case 'PEC_CLOSED':
        return 'event_busy';
      default:
        return 'info';
    }
  }

  iconClass(evt: WsEvent): string {
    switch (evt.type) {
      case 'PATIENT_CREATED':
        return 'patient';
      case 'PEC_VALIDATED':
        return 'pec';
      case 'PEC_CLOSED':
        return 'warning';
      default:
        return '';
    }
  }

  textFor(evt: WsEvent): string {
    switch (evt.type) {
      case 'PATIENT_CREATED': {
        const nomComplet = `${evt.payload['nom'] ?? ''} ${evt.payload['prenom'] ?? ''}`.trim();
        return this.translate.instant('NOTIFICATION.PATIENT_CREATED', {nom: nomComplet});
      }
      case 'PEC_VALIDATED':
        return this.translate.instant('NOTIFICATION.PEC_VALIDATED', {
          nom: evt.payload['patientNom'] ?? '',
        });
      case 'PEC_CLOSED':
        return this.translate.instant('NOTIFICATION.PEC_CLOSED', {
          nom: evt.payload['patientNom'] ?? '',
        });
      default:
        return evt.type;
    }
  }
}
