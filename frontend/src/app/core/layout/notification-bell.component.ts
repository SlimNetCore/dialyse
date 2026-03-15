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
      <mat-icon>notifications</mat-icon>
    </button>
    <mat-menu #notifMenu="matMenu" class="notif-menu">
      <div class="notif-header" (click)="$event.stopPropagation()">
        <span>{{ 'NOTIFICATION.TITLE' | translate }}</span>
        @if (unreadCount() > 0) {
          <button mat-button class="mark-read" (click)="markAllRead()">{{ 'NOTIFICATION.MARK_READ' | translate }}</button>
        }
      </div>
      @if (ws.events().length === 0) {
        <div class="notif-empty" (click)="$event.stopPropagation()">{{ 'NOTIFICATION.EMPTY' | translate }}</div>
      }
      @for (evt of ws.events().slice(0, 10); track evt.timestamp) {
        <button mat-menu-item class="notif-item">
          <mat-icon [class]="'notif-icon ' + iconClass(evt)">{{ iconFor(evt) }}</mat-icon>
          <div class="notif-body">
            <span class="notif-text">{{ textFor(evt) }}</span>
            <span class="notif-time">{{ evt.timestamp | date:'short' }}</span>
          </div>
        </button>
      }
    </mat-menu>
  `,
  styles: [`
    .notif-btn { color: rgba(255,255,255,0.9) !important; }
    .notif-header {
      display: flex; justify-content: space-between; align-items: center;
      padding: 8px 16px; border-bottom: 1px solid #eee; font-weight: 600; font-size: 14px;
    }
    .mark-read { font-size: 12px; color: #1b5e20; }
    .notif-empty { padding: 16px; text-align: center; color: #999; font-size: 13px; }
    .notif-item { display: flex !important; align-items: center; gap: 10px; }
    .notif-body { display: flex; flex-direction: column; }
    .notif-text { font-size: 13px; }
    .notif-time { font-size: 11px; color: #999; }
    .notif-icon { font-size: 20px; width: 20px; height: 20px; }
    .notif-icon.patient { color: #1b5e20; }
    .notif-icon.pec { color: #1565c0; }
    .notif-icon.warning { color: #e65100; }
  `]
})
export class NotificationBellComponent {
  readonly ws = inject(WebSocketService);
  private readonly readCount = signal(0);

  readonly unreadCount = computed(() => Math.max(0, this.ws.events().length - this.readCount()));

  markAllRead(): void {
    this.readCount.set(this.ws.events().length);
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

