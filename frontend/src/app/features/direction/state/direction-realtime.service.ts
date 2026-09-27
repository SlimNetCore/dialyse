import {computed, inject, Injectable, signal} from '@angular/core';
import {MatSnackBar} from '@angular/material/snack-bar';
import {TranslateService} from '@ngx-translate/core';
import {Client, IMessage} from '@stomp/stompjs';
import {environment} from '../../../../environments/environment';
import {DashboardChangedEvent} from '../../../core/api/direction-api.service';
import {alertTransitions, ChangeGroup, delta, formatDelta, groupChanges, metricKey} from '../direction-realtime.util';
import {DirectionStore} from './direction.store';

export type DirectionNotification = {
  id: string;
  at: string;
  centre: string;
  family: string;
  /** critical : alerte levée ; success : alerte résolue ; info : variation d'un indicateur. */
  severity: 'critical' | 'success' | 'info';
  /** Textes déjà traduits, prêts à afficher : famille (ou type d'alerte) et détail des variations. */
  title: string;
  detail: string;
  /** Phrase complète (centre + famille + détail) : message discret et notification du navigateur. */
  text: string;
  read: boolean;
};

export type RealtimeStatus = 'off' | 'connecting' | 'live' | 'reconnecting';

const MAX_NOTIFICATIONS = 50;
const BROWSER_KEY = 'hemo.direction.browserNotifications';

/**
 * Temps réel de la direction : reste abonné au canal de sa société, relit le tableau de bord à chaque changement
 * signalé et transforme chaque changement en notification (cloche, message discret et, si la direction l'a
 * autorisé, notification du navigateur). Le canal ne transporte que des indicateurs agrégés et anonymes : les
 * données elles-mêmes sont relues par l'API sécurisée.
 */
@Injectable({providedIn: 'root'})
export class DirectionRealtimeService {
  readonly status = signal<RealtimeStatus>('off');
  readonly notifications = signal<DirectionNotification[]>([]);
  readonly unreadCount = computed(() => this.notifications().filter((n) => !n.read).length);
  readonly browserNotifications = signal(this.readBrowserPreference());
  private readonly store = inject(DirectionStore);
  private readonly translate = inject(TranslateService);
  private readonly snack = inject(MatSnackBar);
  private client: Client | null = null;
  private refreshTimer: ReturnType<typeof setTimeout> | null = null;
  private seq = 0;

  /** Ouvre (une seule fois) l'abonnement au canal de la société. */
  start(societeId: string | null): void {
    if (!societeId || this.client?.active) return;
    this.status.set('connecting');
    this.client = new Client({
      brokerURL: environment.wsBaseUrl ?? this.defaultUrl(),
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        const wasReconnect = this.status() === 'reconnecting';
        this.status.set('live');
        this.client?.subscribe(`/topic/societe/${societeId}/dashboard`, (m: IMessage) => this.onMessage(m));
        if (wasReconnect) this.scheduleRefresh(); // des changements ont pu être manqués pendant la coupure
      },
      onStompError: () => this.status.set('reconnecting'),
      onWebSocketClose: () => {
        if (this.client?.active) this.status.set('reconnecting');
      },
    });
    this.client.activate();
  }

  stop(): void {
    void this.client?.deactivate();
    this.client = null;
    if (this.refreshTimer) clearTimeout(this.refreshTimer);
    this.refreshTimer = null;
    this.status.set('off');
    this.notifications.set([]);
  }

  markAllRead(): void {
    this.notifications.update((list) => list.map((n) => ({...n, read: true})));
  }

  markRead(id: string): void {
    this.notifications.update((list) => list.map((n) => (n.id === id ? {...n, read: true} : n)));
  }

  clear(): void {
    this.notifications.set([]);
  }

  /** Demande l'autorisation des notifications du navigateur (geste explicite de l'utilisateur). */
  async enableBrowserNotifications(): Promise<boolean> {
    if (typeof Notification === 'undefined') return false;
    const permission = Notification.permission === 'granted' ? 'granted' : await Notification.requestPermission();
    const enabled = permission === 'granted';
    this.browserNotifications.set(enabled);
    this.writeBrowserPreference(enabled);
    return enabled;
  }

  disableBrowserNotifications(): void {
    this.browserNotifications.set(false);
    this.writeBrowserPreference(false);
  }

  /** Applique un événement reçu : relecture des données et notifications (public pour les tests). */
  handle(event: DashboardChangedEvent): void {
    if (!event.changes?.length) return;
    this.scheduleRefresh();
    const created = groupChanges(event.changes).map((g) => this.toNotification(g, event.at));
    if (created.length === 0) return;
    this.notifications.update((list) => [...created, ...list].slice(0, MAX_NOTIFICATIONS));
    this.announce(created);
  }

  private onMessage(message: IMessage): void {
    try {
      this.handle(JSON.parse(message.body) as DashboardChangedEvent);
    } catch {
      // message illisible : ignoré, le prochain balayage serveur resynchronise
    }
  }

  /** Regroupe les rafales de changements en une seule relecture. */
  private scheduleRefresh(): void {
    if (this.refreshTimer) return;
    this.refreshTimer = setTimeout(() => {
      this.refreshTimer = null;
      void this.store.refresh();
    }, 400);
  }

  private toNotification(group: ChangeGroup, at: string): DirectionNotification {
    return {
      id: `${at}|${group.key}|${++this.seq}`,
      at,
      centre: group.centre,
      family: group.family,
      ...this.describe(group),
      read: false,
    };
  }

  private describe(group: ChangeGroup): Pick<DirectionNotification, 'severity' | 'title' | 'detail' | 'text'> {
    if (group.family === 'ALERTES') {
      const transitions = alertTransitions(group);
      const raised = transitions.some((a) => a.raised);
      const detail = transitions.map((a) =>
        this.translate.instant(`DIRECTION.ALERTS.${a.code}`, {value: ''}).replace(/\s+/g, ' ').trim()).join(' · ');
      const text = transitions.map((a) => this.translate.instant(
        a.raised ? 'DIRECTION.RT.ALERT_RAISED' : 'DIRECTION.RT.ALERT_CLEARED',
        {
          centre: group.centre,
          alert: this.translate.instant(`DIRECTION.ALERTS.${a.code}`, {value: ''}).replace(/\s+/g, ' ').trim()
        }))
        .join(' · ');
      return {
        severity: raised ? 'critical' : 'success',
        title: this.translate.instant(raised ? 'DIRECTION.RT.ALERT_RAISED_TITLE' : 'DIRECTION.RT.ALERT_CLEARED_TITLE'),
        detail,
        text,
      };
    }
    return {
      severity: 'info',
      title: this.translate.instant(`DIRECTION.RT.FAMILY_LABEL.${group.family}`), ...this.describeChanges(group)
    };
  }

  private describeChanges(group: ChangeGroup): { detail: string; text: string } {
    // Un indicateur par ligne : « séances +1 (12 → 13) » ; au plus 3 détails, le reste est résumé.
    const details = group.changes.slice(0, 3).map((c) => {
      const key = `DIRECTION.RT.METRIC.${metricKey(c.name)}`;
      const translated = this.translate.instant(key);
      const label = translated === key ? metricKey(c.name) : translated;
      return `${label} ${formatDelta(delta(c))} (${c.before} → ${c.after})`;
    });
    if (group.changes.length > 3) {
      details.push(this.translate.instant('DIRECTION.RT.MORE', {count: group.changes.length - 3}));
    }
    const detail = details.join(', ');
    return {
      detail,
      text: this.translate.instant(`DIRECTION.RT.FAMILY.${group.family}`, {centre: group.centre, details: detail})
    };
  }

  /** Message discret dans l'application, et notification du navigateur si autorisée et l'onglet est masqué. */
  private announce(created: DirectionNotification[]): void {
    const first = created[0];
    const extra = created.length > 1 ? ` (+${created.length - 1})` : '';
    this.snack.open(`${first.text}${extra}`, this.translate.instant('COMMON.OK'), {duration: 5000});
    if (this.browserNotifications() && typeof Notification !== 'undefined' && Notification.permission === 'granted'
      && typeof document !== 'undefined' && document.hidden) {
      new Notification(this.translate.instant('DIRECTION.RT.BROWSER_TITLE'), {body: `${first.text}${extra}`});
    }
  }

  private defaultUrl(): string {
    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
    return `${protocol}://${window.location.host}/ws`;
  }

  private readBrowserPreference(): boolean {
    try {
      return localStorage.getItem(BROWSER_KEY) === '1' && typeof Notification !== 'undefined'
        && Notification.permission === 'granted';
    } catch {
      return false;
    }
  }

  private writeBrowserPreference(enabled: boolean): void {
    try {
      localStorage.setItem(BROWSER_KEY, enabled ? '1' : '0');
    } catch {
      // stockage indisponible : la préférence n'est simplement pas mémorisée
    }
  }
}
