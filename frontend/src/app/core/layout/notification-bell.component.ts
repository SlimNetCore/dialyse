import { Component, inject, computed, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatBadgeModule } from '@angular/material/badge';
import { TranslateModule } from '@ngx-translate/core';
import { WebSocketService, WsEvent } from '../ws/websocket.service';
import { DatePipe, JsonPipe } from '@angular/common';

@Component({
  selector: 'app-notification-bell',
  standalone: true,
  imports: [MatIconModule, MatButtonModule, MatBadgeModule, TranslateModule, DatePipe, JsonPipe],
  template: `
    <button mat-icon-button class="notif-btn"
            [matBadge]="unreadCount()" [matBadgeHidden]="unreadCount() === 0"
            matBadgeColor="warn" matBadgeSize="small"
            (click)="togglePanel()">
      <mat-icon [class.bell-pulse]="unreadCount() > 0">notifications</mat-icon>
    </button>

    @if (open()) {
      <div class="notif-overlay" (click)="closePanel()"></div>
      <section class="notif-panel" role="dialog" aria-label="Notifications">
        <header class="panel-header">
          <div class="title-wrap">
            <mat-icon>notifications_active</mat-icon>
            <h3>{{ 'NOTIFICATION.TITLE' | translate }}</h3>
          </div>
          <div class="panel-actions">
            <button mat-button class="mark-read" (click)="markAllRead()">{{ 'NOTIFICATION.MARK_READ' | translate }}</button>
            <button mat-icon-button (click)="closePanel()"><mat-icon>close</mat-icon></button>
          </div>
        </header>

        <div class="tabs">
          <button class="tab" [class.active]="activeTab() === 'unread'" (click)="activeTab.set('unread')">
            Non lu ({{ unreadEvents().length }})
          </button>
          <button class="tab" [class.active]="activeTab() === 'read'" (click)="activeTab.set('read')">
            Lu ({{ readEvents().length }})
          </button>
        </div>

        <div class="panel-content">
          <div class="message-list">
            @if (visibleEvents().length === 0) {
              <div class="notif-empty">{{ 'NOTIFICATION.EMPTY' | translate }}</div>
            }
            @for (evt of visibleEvents(); track eventId(evt)) {
              <button class="message-item" [class.unread]="!isRead(evt)" [class.selected]="selectedEventId() === eventId(evt)"
                      (click)="selectMessage(evt)">
                <mat-icon [class]="'notif-icon ' + iconClass(evt)">{{ iconFor(evt) }}</mat-icon>
                <div class="message-main">
                  <div class="message-text">{{ textFor(evt) }}</div>
                  <div class="message-meta">
                    <span>{{ evt.timestamp | date:'short' }}</span>
                    <span>{{ isRead(evt) ? 'Lu' : 'Non lu' }}</span>
                  </div>
                </div>
              </button>
            }
          </div>

          <aside class="message-detail">
            @if (selectedEvent()) {
              <h4>{{ textFor(selectedEvent()!) }}</h4>
              <p><strong>Type:</strong> {{ selectedEvent()!.type }}</p>
              <p><strong>Date:</strong> {{ selectedEvent()!.timestamp | date:'full' }}</p>
              <pre>{{ selectedEvent()!.payload | json }}</pre>
            } @else {
              <p class="empty-detail">Sélectionnez un message pour voir le détail.</p>
            }
          </aside>
        </div>
      </section>
    }
  `,
  styles: [`
    .notif-btn { color: rgba(255,255,255,0.9) !important; }
    .bell-pulse { animation: bellPulse 1.2s ease-in-out infinite; transform-origin: top center; }
    @keyframes bellPulse {
      0%, 100% { transform: rotate(0deg); }
      15% { transform: rotate(10deg); }
      30% { transform: rotate(-8deg); }
      45% { transform: rotate(6deg); }
      60% { transform: rotate(-4deg); }
      75% { transform: rotate(2deg); }
    }

    .notif-overlay {
      position: fixed; inset: 0; background: rgba(9, 30, 66, 0.28); z-index: 1100;
    }
    .notif-panel {
      position: fixed; top: 64px; right: 22px; width: 760px; max-width: calc(100vw - 44px);
      height: 72vh; background: #fff; border-radius: 14px; z-index: 1110;
      box-shadow: 0 20px 50px rgba(15, 23, 42, 0.22); display: flex; flex-direction: column;
      overflow: hidden;
    }
    .panel-header {
      display: flex; justify-content: space-between; align-items: center;
      padding: 10px 14px; border-bottom: 1px solid #e7ecef;
      background: linear-gradient(180deg, #f7fcf8, #ffffff);
    }
    .title-wrap { display: flex; align-items: center; gap: 8px; }
    .title-wrap h3 { margin: 0; font-size: 1rem; color: #134e4a; }
    .panel-actions { display: flex; align-items: center; gap: 8px; }
    .mark-read { color: #1b5e20; }

    .tabs { display: flex; border-bottom: 1px solid #eef2f1; }
    .tab {
      flex: 1; border: none; background: transparent; padding: 10px 14px;
      font-weight: 600; color: #607d8b; cursor: pointer;
    }
    .tab.active { color: #1b5e20; border-bottom: 3px solid #1b5e20; }

    .panel-content { display: grid; grid-template-columns: 1.1fr 0.9fr; min-height: 0; flex: 1; }
    .message-list { overflow: auto; border-right: 1px solid #eef2f1; }
    .message-item {
      width: 100%; border: none; background: #fff; text-align: left; cursor: pointer;
      display: flex; gap: 10px; padding: 12px 14px; border-bottom: 1px solid #f2f4f7;
    }
    .message-item.unread { background: #f3fbf5; border-left: 3px solid #2e7d32; }
    .message-item.selected { background: #e8f5e9; }
    .message-main { flex: 1; min-width: 0; }
    .message-text { font-size: 13px; color: #1f2937; line-height: 1.25; }
    .message-meta { font-size: 11px; color: #6b7280; margin-top: 3px; display: flex; justify-content: space-between; }

    .notif-icon { font-size: 19px; width: 19px; height: 19px; margin-top: 2px; }
    .notif-icon.patient { color: #1b5e20; }
    .notif-icon.pec { color: #1565c0; }
    .notif-icon.warning { color: #e65100; }

    .message-detail { padding: 14px; overflow: auto; background: #fbfdfc; }
    .message-detail h4 { margin: 0 0 10px; color: #0f172a; }
    .message-detail p { margin: 6px 0; font-size: 13px; }
    .message-detail pre {
      white-space: pre-wrap; font-size: 12px; background: #f8fafc;
      border: 1px solid #e2e8f0; border-radius: 8px; padding: 10px;
    }
    .notif-empty, .empty-detail { color: #8a9099; padding: 14px; font-size: 13px; }
  `]
})
export class NotificationBellComponent {
  readonly ws = inject(WebSocketService);

  readonly open = signal(false);
  readonly activeTab = signal<'unread' | 'read'>('unread');
  readonly selectedEventId = signal<string | null>(null);
  private readonly readIds = signal<Set<string>>(new Set<string>());

  readonly unreadEvents = computed(() => this.ws.events().filter(e => !this.isRead(e)));
  readonly readEvents = computed(() => this.ws.events().filter(e => this.isRead(e)));
  readonly visibleEvents = computed(() => this.activeTab() === 'unread' ? this.unreadEvents() : this.readEvents());
  readonly selectedEvent = computed(() => {
    const id = this.selectedEventId();
    if (!id) return null;
    return this.ws.events().find(e => this.eventId(e) === id) ?? null;
  });
  readonly unreadCount = computed(() => this.unreadEvents().length);

  togglePanel(): void {
    const next = !this.open();
    this.open.set(next);
    if (!next) return;

    const first = this.visibleEvents()[0] ?? this.ws.events()[0] ?? null;
    if (first) {
      this.selectedEventId.set(this.eventId(first));
    }
  }

  closePanel(): void {
    this.open.set(false);
  }

  eventId(evt: WsEvent): string {
    return `${evt.type}-${evt.timestamp}-${evt.payload['pecId'] ?? evt.payload['patientCode'] ?? ''}`;
  }

  isRead(evt: WsEvent): boolean {
    return this.readIds().has(this.eventId(evt));
  }

  selectMessage(evt: WsEvent): void {
    this.selectedEventId.set(this.eventId(evt));
    const next = new Set(this.readIds());
    next.add(this.eventId(evt));
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
      case 'PEC_VALIDATED': return `PEC validee: ${evt.payload['patientNom']}`;
      case 'PEC_CLOSED': return `PEC cloturee: ${evt.payload['patientNom']}`;
      default: return evt.type;
    }
  }
}
