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
      case 'INFIRMIER_SOUS_EFFECTIF':
        return 'groups';
      case 'INFIRMIER_ABSENCE_DECLAREE':
      case 'ABSENCES_A_QUALIFIER':
        return 'event_busy';
      case 'PATIENT_REPLACE_ISOLEMENT':
      case 'ISOLEMENT_IMPOSSIBLE':
        return 'masks';
      case 'SAISIE_INFIRMIER':
        return 'medical_services';
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
      case 'INFIRMIER_SOUS_EFFECTIF':
      case 'INFIRMIER_ABSENCE_DECLAREE':
      case 'ABSENCES_A_QUALIFIER':
      case 'PATIENT_REPLACE_ISOLEMENT':
      case 'ISOLEMENT_IMPOSSIBLE':
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
      case 'INFIRMIER_ABSENCE_DECLAREE':
        return this.translate.instant('NOTIFICATION.INFIRMIER_ABSENCE_DECLAREE', {
          infirmier: evt.payload['infirmier'] ?? '',
          debut: evt.payload['debut'] ?? '',
          fin: evt.payload['fin'] ?? '',
        });
      case 'PATIENT_REPLACE_ISOLEMENT':
        return this.translate.instant('NOTIFICATION.PATIENT_REPLACE_ISOLEMENT', {
          nom: evt.payload['patientNom'] ?? '',
          salle: evt.payload['salle'] ?? '',
        });
      case 'ISOLEMENT_IMPOSSIBLE':
        return this.translate.instant('NOTIFICATION.ISOLEMENT_IMPOSSIBLE', {nom: evt.payload['patientNom'] ?? ''});
      case 'ABSENCES_A_QUALIFIER':
        return this.translate.instant('NOTIFICATION.ABSENCES_A_QUALIFIER', {
          count: evt.payload['nbAQualifier'] ?? '',
          late: evt.payload['nbEnRetard'] ?? '',
        });
      case 'INFIRMIER_SOUS_EFFECTIF':
        return this.translate.instant('NOTIFICATION.INFIRMIER_SOUS_EFFECTIF', {
          count: evt.payload['nbCreneaux'] ?? '',
          date: evt.payload['premiereDate'] ?? '',
        });
      case 'SAISIE_INFIRMIER':
        return this.translate.instant(`NOTIFICATION.SAISIE.${evt.payload['saisie'] ?? 'DEFAULT'}`, {
          nom: `${evt.payload['patientNom'] ?? ''} ${evt.payload['patientPrenom'] ?? ''}`.trim(),
          auteur: evt.payload['auteur'] ?? '',
          date: evt.payload['date'] ?? '',
        });
      default:
        return evt.type;
    }
  }
}
