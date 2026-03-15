import { Component, inject, computed, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatBadgeModule } from '@angular/material/badge';
import { MatMenuModule } from '@angular/material/menu';
import { TranslateModule } from '@ngx-translate/core';
import { WebSocketService, WsEvent } from '../ws/websocket.service';
import { DatePipe } from '@angular/common';

@Component({
  selector: 'app-notification-bell',
  standalone: true,
  imports: [MatIconModule, MatButtonModule, MatBadgeModule, MatMenuModule, TranslateModule, DatePipe],
  template: `
    <button mat-icon-button [matMenuTriggerFor]="notifMenu" class="notif-btn"
            [matBadge]="unreadCount()" [matBadgeHidden]="unreadCount() === 0"
            matBadgeColor="warn" matBadgeSize="small">
      <mat-icon [class.bell-pulse]="unreadCount() > 0">notifications</mat-icon>
    </button>

    <mat-menu #notifMenu="matMenu" class="notif-menu" (menuOpened)="markVisibleAsRead()">
      <div class="notif-header" (click)="$event.stopPropagation()">
        <span>{{ 'NOTIFICATION.TITLE' | translate }}</span>
        @if (unreadCount() > 0) {
          <button mat-button class="mark-read" (click)="markAllRead()">{{ 'NOTIFICATION.MARK_READ' | translate }}</button>
        }
      </div>

      @if (ws.events().length === 0) {
        <div class="notif-empty" (click)="$event.stopPropagation()">{{ 'NOTIFICATION.EMPTY' | translate }}</div>
      }

      @for (evt of ws.events().slice(0, 12); track eventId(evt)) {
        <button mat-menu-item class="notif-item" [class.unread]="!isRead(evt)">
          <mat-icon [class]="'notif-icon ' + iconClass(evt)">{{ iconFor(evt) }}</mat-icon>
          <div class="notif-body">
            <span class="notif-text">{{ textFor(evt) }}</span>
            <div class="notif-meta">
              <span class="notif-time">{{ evt.timestamp | date:'short' }}</span>
              <span class="notif-state">{{ isRead(evt) ? 'Lu' : 'Non lu' }}</span>
            </div>
          </div>
        </button>
      }
    </mat-menu>
  `,
  styles: [`
    .notif-btn { color: rgba(255,255,255,0.9) !important; }
    .bell-pulse {
      animation: bellPulse 1.2s ease-in-out infinite;
      transform-origin: top center;
    }
    @keyframes bellPulse {
      0%, 100% { transform: rotate(0deg); }
      15% { transform: rotate(10deg); }
      30% { transform: rotate(-8deg); }
      45% { transform: rotate(6deg); }
      60% { transform: rotate(-4deg); }
      75% { transform: rotate(2deg); }
    }

    .notif-header {
      display: flex; justify-content: space-between; align-items: center;
      padding: 10px 16px; border-bottom: 1px solid #eef2f1; font-weight: 700; font-size: 14px;
      background: linear-gradient(180deg, #f7fcf8, #ffffff);
    }
    .mark-read { font-size: 12px; color: #1b5e20; }
    .notif-empty { padding: 18px; text-align: center; color: #999; font-size: 13px; }

    .notif-item { display: flex !important; align-items: flex-start; gap: 10px; min-width: 360px; }
    .notif-item.unread { background: #f3fbf5; border-left: 3px solid #2e7d32; }
    .notif-body { display: flex; flex-direction: column; width: 100%; }
    .notif-text { font-size: 13px; white-space: normal; line-height: 1.25; }
    .notif-meta { display: flex; justify-content: space-between; align-items: center; margin-top: 2px; }
    .notif-time { font-size: 11px; color: #999; }
    .notif-state { font-size: 11px; color: #607d8b; }
    .notif-item.unread .notif-state { color: #1b5e20; font-weight: 600; }

    .notif-icon { font-size: 20px; width: 20px; height: 20px; margin-top: 2px; }
    .notif-icon.patient { color: #1b5e20; }
    .notif-icon.pec { color: #1565c0; }
    .notif-icon.warning { color: #e65100; }
  `]
})
export class NotificationBellComponent {
  readonly ws = inject(WebSocketService);
  private readonly readIds = signal<Set<string>>(new Set<string>());

  readonly unreadCount = computed(() => {
    const read = this.readIds();
    return this.ws.events().filter(e => !read.has(this.eventId(e))).length;
  });

  eventId(evt: WsEvent): string {
    return `${evt.type}-${evt.timestamp}-${evt.payload['pecId'] ?? evt.payload['patientCode'] ?? ''}`;
  }

  isRead(evt: WsEvent): boolean {
    return this.readIds().has(this.eventId(evt));
  }

  markVisibleAsRead(): void {
    const next = new Set(this.readIds());
    this.ws.events().slice(0, 12).forEach(evt => next.add(this.eventId(evt)));
    this.readIds.set(next);
  }

  markAllRead(): void {
    const next = new Set(this.readIds());
    this.ws.events().forEach(evt => next.add(this.eventId(evt)));
    this.readIds.set(next);
  }

  iconFor(evt: WsEvent): string {
    switch (evt.type) {
      case 'PATIENT_CREATED': return 'person_add';
      case 'PEC_VALIDATED': return 'verified';
      case 'PEC_CLOSED': return 'event_busy';
      default: return 'info';
    }
  }

  iconClass(evt: WsEvent): string {
    switch (evt.type) {
      case 'PATIENT_CREATED': return 'patient';
      case 'PEC_VALIDATED': return 'pec';
      case 'PEC_CLOSED': return 'warning';
      default: return '';
    }
  }

  textFor(evt: WsEvent): string {
    switch (evt.type) {
      case 'PATIENT_CREATED': return `Nouveau patient: ${evt.payload['nom']} ${evt.payload['prenom']}`;
      case 'PEC_VALIDATED': return `PEC validée: ${evt.payload['patientNom']}`;
      case 'PEC_CLOSED': return `PEC clôturée: ${evt.payload['patientNom']}`;
      default: return evt.type;
    }
  }
}
