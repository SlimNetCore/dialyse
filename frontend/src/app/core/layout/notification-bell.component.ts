import {ChangeDetectionStrategy, Component, computed, inject} from '@angular/core';
import {MatIconModule} from '@angular/material/icon';
import {MatButtonModule} from '@angular/material/button';
import {MatBadgeModule} from '@angular/material/badge';
import {MatTooltipModule} from '@angular/material/tooltip';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {WebSocketService, WsEvent} from '../ws/websocket.service';
import {DatePipe} from '@angular/common';
import {NotificationBellStore} from '../state/notification-bell.store';
import {AuthStore} from '../state/auth.store';
import {RouterLink} from '@angular/router';
import {aujourdhui} from '../../features/planning/optimisation/optimisation.util';
import {NotificationVue, presenterNotification} from './notification-presentation.util';

/**
 * Cloche des notifications du centre. Chaque notification porte un libellé, un message et **une action** qui mène à
 * l'écran où la traiter (voir {@link presenterNotification}) ; ouvrir l'action marque la notification comme lue.
 */
@Component({
  selector: 'app-notification-bell',
  standalone: true,
  imports: [
    RouterLink,
    MatIconModule,
    MatButtonModule,
    MatBadgeModule,
    MatTooltipModule,
    TranslateModule,
    DatePipe,
  ],
  templateUrl: './notification-bell.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './notification-bell.component.css',
})
export class NotificationBellComponent {
  readonly ws = inject(WebSocketService);
  private readonly notifStore = inject(NotificationBellStore);
  private readonly translate = inject(TranslateService);
  private readonly auth = inject(AuthStore);

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

  /** Libellé, message, icône et action de la notification, pour le profil et la langue de l'utilisateur. */
  vue(evt: WsEvent): NotificationVue {
    return presenterNotification(evt, {
      hasRole: (role) => this.auth.hasRole(role),
      traduire: (cle, parametres) => this.translate.instant(cle, parametres),
      aujourdhui: aujourdhui(),
    });
  }

  /** L'utilisateur part traiter la notification : elle est lue, et le panneau se ferme. */
  ouvrirAction(evt: WsEvent): void {
    this.notifStore.selectMessage(evt);
    this.notifStore.closePanel();
  }
}
