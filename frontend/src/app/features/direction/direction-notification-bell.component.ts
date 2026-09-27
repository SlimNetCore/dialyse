import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  signal,
  ViewEncapsulation
} from '@angular/core';
import {MatBadgeModule} from '@angular/material/badge';
import {MatButtonModule} from '@angular/material/button';
import {MatButtonToggleModule} from '@angular/material/button-toggle';
import {MatIconModule} from '@angular/material/icon';
import {MatMenuModule} from '@angular/material/menu';
import {MatSlideToggleModule} from '@angular/material/slide-toggle';
import {MatTooltipModule} from '@angular/material/tooltip';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {timeAgo} from './direction-realtime.util';
import {DirectionNotification, DirectionRealtimeService} from './state/direction-realtime.service';

const FAMILY_ICONS: Record<string, string> = {
  PATIENTS: 'groups', SEANCES: 'event_available', FINANCE: 'payments', CAISSES: 'account_balance',
  CLINIQUE: 'monitor_heart', ANEMIE: 'bloodtype', STOCK: 'inventory_2', ALERTES: 'notification_important',
};

type Filter = 'all' | 'unread';

/**
 * Centre de notifications de la direction : changements de son tableau de bord reçus en temps réel, présentés en
 * cartes (famille, centre, variation, ancienneté), filtrables (toutes / non lues), avec l'état de la connexion et
 * l'activation des notifications du navigateur.
 */
@Component({
  selector: 'app-direction-notification-bell',
  standalone: true,
  imports: [
    TranslateModule, MatBadgeModule, MatButtonModule, MatButtonToggleModule, MatIconModule, MatMenuModule,
    MatSlideToggleModule, MatTooltipModule,
  ],
  templateUrl: './direction-notification-bell.component.html',
  styleUrl: './direction-notification-bell.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  // Le panneau du menu est rendu dans un overlay hors du composant : ses styles doivent être globaux.
  encapsulation: ViewEncapsulation.None,
})
export class DirectionNotificationBellComponent {
  protected readonly realtime = inject(DirectionRealtimeService);
  protected readonly filter = signal<Filter>('all');
  protected readonly visible = computed(() => {
    const all = this.realtime.notifications();
    return this.filter() === 'unread' ? all.filter((n) => !n.read) : all;
  });
  private readonly translate = inject(TranslateService);
  /** Horloge de l'affichage (rafraîchit « il y a 3 min » sans recharger les données). */
  private readonly now = signal(Date.now());

  constructor() {
    const timer = setInterval(() => this.now.set(Date.now()), 30_000);
    inject(DestroyRef).onDestroy(() => clearInterval(timer));
  }

  protected icon(n: DirectionNotification): string {
    return n.severity === 'success' ? 'check_circle' : (FAMILY_ICONS[n.family] ?? 'notifications');
  }

  protected ago(n: DirectionNotification): string {
    return timeAgo(n.at, this.now(), this.translate.currentLang || this.translate.defaultLang || 'fr');
  }

  protected statusKey(): string {
    return `DIRECTION.RT.STATUS_${this.realtime.status().toUpperCase()}`;
  }

  protected setFilter(value: Filter): void {
    this.filter.set(value);
  }

  protected toggleBrowser(enabled: boolean): void {
    if (enabled) {
      void this.realtime.enableBrowserNotifications();
    } else {
      this.realtime.disableBrowserNotifications();
    }
  }
}
