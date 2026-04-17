import {Component, computed, inject, signal} from '@angular/core';
import {MatIconModule} from '@angular/material/icon';
import {MatButtonModule} from '@angular/material/button';
import {MatBadgeModule} from '@angular/material/badge';
import {MatTooltipModule} from '@angular/material/tooltip';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {WebSocketService, WsEvent} from '../ws/websocket.service';
import {DatePipe, JsonPipe} from '@angular/common';

@Component({
  selector: 'app-notification-bell',
  standalone: true,
  imports: [MatIconModule, MatButtonModule, MatBadgeModule, MatTooltipModule, TranslateModule, DatePipe, JsonPipe],
  template: `
    <button mat-icon-button class="notif-btn"
            [matBadge]="unreadCount()" [matBadgeHidden]="unreadCount() === 0"
            matBadgeColor="warn" matBadgeSize="small"
            (click)="togglePanel()">
      <mat-icon [class.bell-pulse]="unreadCount() > 0">notifications</mat-icon>
      <span class="ws-dot" [style.background]="ws.statusColor()" [matTooltip]="wsTooltip()"></span>
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
            {{ 'NOTIFICATION.UNREAD_TAB' | translate }} ({{ unreadEvents().length }})
          </button>
          <button class="tab" [class.active]="activeTab() === 'read'" (click)="activeTab.set('read')">
            {{ 'NOTIFICATION.READ_TAB' | translate }} ({{ readEvents().length }})
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
                    <span>{{ isRead(evt) ? ('NOTIFICATION.READ_STATUS' | translate) : ('NOTIFICATION.UNREAD_STATUS' | translate) }}</span>
                  </div>
                </div>
              </button>
            }
          </div>

          <aside class="message-detail">
            @if (selectedEvent()) {
              <h4>{{ textFor(selectedEvent()!) }}</h4>
              <p><strong>{{ 'NOTIFICATION.DETAIL_TYPE' | translate }}:</strong> {{ selectedEvent()!.type }}</p>
              <p><strong>{{ 'NOTIFICATION.DETAIL_DATE' | translate }}:</strong> {{ selectedEvent()!.timestamp | date:'full' }}</p>
              <pre>{{ selectedEvent()!.payload | json }}</pre>
            } @else {
              <p class="empty-detail">{{ 'NOTIFICATION.DETAIL_SELECT' | translate }}</p>
            }
          </aside>
        </div>
      </section>
    }
  `,
  styles: [`
    .notif-btn { color: var(--app-text) !important; position: relative; }
    .ws-dot {
      position: absolute; bottom: 6px; right: 6px; width: 9px; height: 9px;
      border-radius: 50%; border: 1.5px solid var(--app-surface); z-index: 5;
    }
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
      position: fixed; inset: 0; background: rgba(2, 6, 23, 0.22); z-index: 1100;
    }
    .notif-panel {
      position: fixed; top: 64px; right: 22px; width: 760px; max-width: calc(100vw - 44px);
      height: 72vh; background: var(--app-surface); border-radius: 14px; z-index: 1110;
      border: 1px solid var(--app-border);
      box-shadow: 0 20px 50px rgba(15, 23, 42, 0.18); display: flex; flex-direction: column;
      overflow: hidden;
    }
    .panel-header {
      display: flex; justify-content: space-between; align-items: center;
      padding: 10px 14px; border-bottom: 1px solid var(--app-border);
      background: var(--app-surface-soft);
    }
    .title-wrap { display: flex; align-items: center; gap: 8px; }
    .title-wrap h3 { margin: 0; font-size: 1rem; color: var(--app-text); }
    .panel-actions { display: flex; align-items: center; gap: 8px; }
    .mark-read { color: var(--app-primary); }

    .tabs { display: flex; border-bottom: 1px solid var(--app-border); }
    .tab {
      flex: 1; border: none; background: transparent; padding: 10px 14px;
      font-weight: 600; color: var(--app-muted); cursor: pointer;
    }
    .tab.active { color: var(--app-primary); border-bottom: 3px solid var(--app-primary); }

    .panel-content { display: grid; grid-template-columns: 1.1fr 0.9fr; min-height: 0; flex: 1; }
    .message-list { overflow: auto; border-right: 1px solid var(--app-border); }
    .message-item {
      width: 100%; border: none; background: var(--app-surface); text-align: left; cursor: pointer;
      display: flex; gap: 10px; padding: 12px 14px; border-bottom: 1px solid #f2f4f7;
    }
    .message-item.unread { background: var(--app-primary-soft); border-left: 3px solid var(--app-primary); }
    .message-item.selected { background: color-mix(in srgb, var(--app-primary-soft) 75%, white); }
    .message-main { flex: 1; min-width: 0; }
    .message-text { font-size: 13px; color: #1f2937; line-height: 1.25; }
    .message-meta { font-size: 11px; color: #6b7280; margin-top: 3px; display: flex; justify-content: space-between; }

    .notif-icon { font-size: 19px; width: 19px; height: 19px; margin-top: 2px; }
    .notif-icon.patient { color: var(--app-primary); }
    .notif-icon.pec { color: #1565c0; }
    .notif-icon.warning { color: #e65100; }

    .message-detail { padding: 14px; overflow: auto; background: var(--app-surface-soft); }
    .message-detail h4 { margin: 0 0 10px; color: #0f172a; }
    .message-detail p { margin: 6px 0; font-size: 13px; }
    .message-detail pre {
      white-space: pre-wrap; font-size: 12px; background: var(--app-surface);
      border: 1px solid var(--app-border); border-radius: 8px; padding: 10px;
    }
    .notif-empty, .empty-detail { color: #8a9099; padding: 14px; font-size: 13px; }

    @media (max-width: 1100px) {
      .notif-panel {
        width: calc(100vw - 20px);
        max-width: calc(100vw - 20px);
        right: 10px;
        top: 62px;
        height: 78vh;
      }
      .panel-content { grid-template-columns: 1fr; }
      .message-list { border-right: 0; border-bottom: 1px solid var(--app-border); max-height: 45%; }
      .message-detail { min-height: 55%; }
    }

    @media (max-width: 700px) {
      .notif-panel {
        top: 56px;
        right: 0;
        width: 100vw;
        max-width: 100vw;
        height: calc(100vh - 56px);
        border-radius: 0;
        border-left: 0;
        border-right: 0;
        border-bottom: 0;
      }
      .panel-header { padding: 8px 10px; }
      .tab { padding: 9px 8px; font-size: 12px; }
      .message-item { padding: 10px; }
      .message-text { font-size: 12px; }
      .message-meta { font-size: 10px; }
    }
  `]
})
export class NotificationBellComponent {
  readonly ws = inject(WebSocketService);
  private readonly translate = inject(TranslateService);

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
      case 'PATIENT_CREATED': {
        const nomComplet = `${evt.payload['nom'] ?? ''} ${evt.payload['prenom'] ?? ''}`.trim();
        return this.translate.instant('NOTIFICATION.PATIENT_CREATED', {nom: nomComplet});
      }
      case 'PEC_VALIDATED':
        return this.translate.instant('NOTIFICATION.PEC_VALIDATED', {nom: evt.payload['patientNom'] ?? ''});
      case 'PEC_CLOSED':
        return this.translate.instant('NOTIFICATION.PEC_CLOSED', {nom: evt.payload['patientNom'] ?? ''});
      default: return evt.type;
    }
  }
}
