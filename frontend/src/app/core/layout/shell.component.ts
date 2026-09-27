import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  inject,
  OnInit,
  signal,
  ViewChild,
} from '@angular/core';
import {NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet} from '@angular/router';
import {MatToolbarModule} from '@angular/material/toolbar';
import {MatIconModule} from '@angular/material/icon';
import {MatButtonModule} from '@angular/material/button';
import {MatMenuModule} from '@angular/material/menu';
import {MatTooltipModule} from '@angular/material/tooltip';
import {TranslateModule} from '@ngx-translate/core';
import {AuthStore} from '../state/auth.store';
import {LangStore} from '../state/lang.store';
import {ThemeStore} from '../state/theme.store';
import {WebSocketService} from '../ws/websocket.service';
import {NotificationBellComponent} from './notification-bell.component';
import {DirectionNotificationBellComponent} from '../../features/direction/direction-notification-bell.component';
import {DirectionRealtimeService} from '../../features/direction/state/direction-realtime.service';
import {MatDialog} from '@angular/material/dialog';
import {MfaDialogComponent} from './mfa-dialog.component';
import {AuthApiService} from '../api/auth-api.service';
import {filter} from 'rxjs/operators';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatToolbarModule,
    MatIconModule,
    MatButtonModule,
    MatMenuModule,
    MatTooltipModule,
    TranslateModule,
    NotificationBellComponent,
    DirectionNotificationBellComponent,
  ],
  templateUrl: './shell.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './shell.component.css',
})
export class ShellComponent implements OnInit, AfterViewInit {
  private readonly roleAuth = inject(AuthStore);
  /**
   * Le propriétaire (SUPERADMIN) ne voit que la gestion des sociétés et des licences ; les autres profils ne voient
   * jamais ces entrées. Le serveur applique la même séparation sur l'API.
   */
  readonly ownerMode = this.roleAuth.hasRole('SUPERADMIN');
  /** La direction d'une société ne voit que son tableau de bord consolidé. */
  readonly directionMode = this.roleAuth.hasRole('DIRECTION');
  /** Sessions sans centre : ni cloche de notifications ni WebSocket de centre. */
  readonly centreless = this.ownerMode || this.directionMode;

  readonly breadcrumbs = signal<string[]>([]);
  readonly compactNav = signal(false);
  /** Tous les modules ; {@link modules} n'en garde que ceux du profil connecté. */
  private readonly allModules = [
    {
      key: 'direction',
      route: '/direction',
      icon: 'insights',
      label: 'NAV.DIRECTION',
      items: [
        {route: '/direction', label: 'Tableau de bord', icon: 'space_dashboard', direction: true},
      ],
    },
    {
      key: 'dashboard',
      route: '/dashboard',
      icon: 'dashboard',
      label: 'NAV.DASHBOARD',
      items: [
        {route: '/dashboard', label: 'Dashboard', icon: 'space_dashboard'},
        {route: '/modeles-document', label: 'Modèles documents', icon: 'description'},
      ],
    },
    {
      key: 'patients',
      route: '/patients',
      icon: 'people',
      label: 'NAV.PATIENTS',
      items: [
        {route: '/patients', label: 'Dashboard patients', icon: 'space_dashboard'},
        {route: '/patients/new', label: 'Nouveau patient', icon: 'person_add'},
        {route: '/patients/pec-list', label: 'Liste PEC', icon: 'fact_check'},
        {route: '/patients/attestations-list', label: 'Attestations', icon: 'badge'},
      ],
    },
    {
      key: 'seances',
      route: '/seances',
      icon: 'event_note',
      label: 'NAV.SEANCES',
      items: [
        {route: '/seances', label: 'Dashboard séances', icon: 'space_dashboard'},
      ],
    },
    {
      key: 'facturation',
      route: '/facturation',
      icon: 'receipt',
      label: 'NAV.FACTURATION',
      items: [
        {route: '/facturation', label: 'Dashboard facturation', icon: 'space_dashboard'},
      ],
    },
    {
      key: 'reglement',
      route: '/reglement',
      icon: 'payments',
      label: 'NAV.REGLEMENT',
      items: [
        {route: '/reglement', label: 'Dashboard règlement', icon: 'space_dashboard'},
      ],
    },
    {
      key: 'stock',
      route: '/stock',
      icon: 'inventory_2',
      label: 'NAV.STOCK',
      items: [
        {route: '/stock', label: 'Dashboard stock', icon: 'space_dashboard'},
        {route: '/stock/bons-commande', label: 'Bons commande', icon: 'request_quote'},
        {route: '/stock/bons-reception', label: 'Bons réception', icon: 'inventory_2'},
        {route: '/stock/bons-sortie', label: 'Bons sortie', icon: 'logout'},
        {route: '/stock/fournisseurs', label: 'Fournisseurs', icon: 'local_shipping'},
      ],
    },
    {
      key: 'comptabilite',
      route: '/comptabilite',
      icon: 'calculate',
      label: 'NAV.COMPTABILITE',
      items: [
        {route: '/comptabilite', label: 'Journal comptable', icon: 'menu_book'},
      ],
    },
    {
      key: 'admin',
      route: '/admin',
      icon: 'tune',
      label: 'NAV.ADMIN',
      items: [
        {route: '/admin/societes', label: 'Sociétés', icon: 'apartment', owner: true},
        {route: '/admin/licenses', label: 'Licences', icon: 'verified_user', owner: true},
        {route: '/admin/users', label: 'Utilisateurs', icon: 'manage_accounts'},
        {route: '/admin/roles', label: 'Rôles', icon: 'admin_panel_settings'},
        {route: '/admin/parametrage/calendrier-clinique', label: 'Calendrier clinique/centre', icon: 'calendar_month'},
        {route: '/admin/parametrage/facturation', label: 'Paramétrage facturation', icon: 'tune'},
        {route: '/admin/parametrage/tva', label: 'Types de TVA', icon: 'percent'},
      ],
    },
  ];
  readonly modules = this.allModules
    .filter((m) => (this.directionMode ? m.key === 'direction' : m.key !== 'direction'))
    .filter((m) => !this.ownerMode || m.key === 'admin')
    .map((m) => ({
      ...m,
      items: (m.items as ReadonlyArray<{ route: string; label: string; icon: string; owner?: boolean }>)
        .filter((item) => this.directionMode || !!item.owner === this.ownerMode),
    }));

  readonly navItems = this.modules.map(m => ({route: m.route, icon: m.icon, label: m.label}));
  readonly activeModuleItems = signal<{ route: string; label: string; icon: string }[]>([]);

  readonly auth = inject(AuthStore);
  readonly lang = inject(LangStore);
  readonly theme = inject(ThemeStore);
  readonly router = inject(Router);
  readonly visibleNavItems = signal(this.navItems);
  private readonly ws = inject(WebSocketService);
  private readonly authApi = inject(AuthApiService);
  private readonly dialog = inject(MatDialog);
  @ViewChild('sidebar') private sidebarRef?: ElementRef<HTMLElement>;
  @ViewChild('navSizer') private navSizerRef?: ElementRef<HTMLElement>;
  private readonly destroyRef = inject(DestroyRef);
  readonly overflowNavItems = signal<typeof this.navItems>([]);

  private readonly directionRealtime = inject(DirectionRealtimeService);
  private breadcrumbRoutes: string[] = [];
  private resizeObserver?: ResizeObserver;
  private resizeFrame: number | null = null;

  ngOnInit(): void {
    if (!this.centreless) {
      this.ws.connect();
    }
    if (this.directionMode) {
      // Temps réel du tableau de bord : abonnement au canal de la société, notifications de la direction.
      this.directionRealtime.start(this.auth.societeId());
    }
    this.computeBreadcrumb(this.router.url);
    this.syncActiveModule(this.router.url);
    this.router.events
      .pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd))
      .subscribe((e) => {
        this.computeBreadcrumb(e.urlAfterRedirects);
        this.syncActiveModule(e.urlAfterRedirects);
      });
  }

  ngAfterViewInit(): void {
    this.installNavObserver();
    this.scheduleCompactNavCheck();
  }

  hasOverflowActiveRoute(): boolean {
    return this.overflowNavItems().some((item) => this.isRouteActive(item.route));
  }

  goBreadcrumb(index: number): void {
    if (index < 0 || index >= this.breadcrumbRoutes.length) return;
    const target = '/' + this.breadcrumbRoutes.slice(0, index + 1).join('/');
    this.router.navigateByUrl(target);
  }

  isRouteActive(route: string): boolean {
    const currentUrl = this.router.url.split('?')[0];
    return currentUrl === route || currentUrl.startsWith(route + '/');
  }

  openSecurity(): void {
    this.dialog.open(MfaDialogComponent, {width: '480px', maxWidth: '95vw'});
  }

  onLogout(): void {
    this.authApi.logout().subscribe({
      next: () => this.finalizeLogout(),
      error: () => this.finalizeLogout(),
    });
  }

  private computeBreadcrumb(url: string): void {
    const map: Record<string, string> = {
      dashboard: 'Dashboard',
      patients: 'Patients',
      new: 'Nouveau',
      'pec-admin': 'Validation PEC',
      'pec-list': 'Liste PEC',
      'attestations-list': 'Liste attestations',
      admin: 'Administration',
      parametrage: 'Paramétrage',
      facturation: 'Facturation',
      'calendrier-clinique': 'Calendrier clinique/centre',
      users: 'Utilisateurs',
      'modeles-document': 'Modèles documents',
      tva: 'Types de TVA',
    };
    const segs = url.split('?')[0].split('/').filter(Boolean);
    this.breadcrumbRoutes = segs;
    this.breadcrumbs.set(segs.map((s) => map[s] ?? s));
  }

  protected openModule(route: string): void {
    this.router.navigateByUrl(route);
  }

  private syncActiveModule(url: string): void {
    const currentUrl = url.split('?')[0];
    const moduleFound = this.modules.find(m =>
      currentUrl === m.route
      || currentUrl.startsWith(m.route + '/')
      || (m.key === 'dashboard' && currentUrl === '/modeles-document'));

    const active = moduleFound ?? this.modules[0];
    this.activeModuleItems.set(active.items);
  }

  private finalizeLogout(): void {
    this.ws.disconnect();
    this.directionRealtime.stop();
    this.auth.clearSession();
    window.location.href = '/login';
  }

  private installNavObserver(): void {
    if (typeof window === 'undefined') {
      return;
    }

    const sidebar = this.sidebarRef?.nativeElement;
    const navSizer = this.navSizerRef?.nativeElement;

    if (!sidebar || !navSizer) {
      return;
    }

    window.addEventListener('resize', this.onWindowResize);

    if (typeof ResizeObserver !== 'undefined') {
      this.resizeObserver = new ResizeObserver(() => this.scheduleCompactNavCheck());
      this.resizeObserver.observe(sidebar);
      this.resizeObserver.observe(navSizer);
    }

    this.destroyRef.onDestroy(() => {
      window.removeEventListener('resize', this.onWindowResize);
      this.resizeObserver?.disconnect();
      if (this.resizeFrame !== null) {
        window.cancelAnimationFrame(this.resizeFrame);
      }
    });
  }

  private readonly onWindowResize = (): void => {
    this.scheduleCompactNavCheck();
  };

  private scheduleCompactNavCheck(): void {
    if (typeof window === 'undefined') {
      return;
    }

    if (this.resizeFrame !== null) {
      window.cancelAnimationFrame(this.resizeFrame);
    }

    this.resizeFrame = window.requestAnimationFrame(() => {
      this.resizeFrame = null;
      this.updateCompactNav();
    });
  }

  private updateCompactNav(): void {
    if (typeof window === 'undefined') {
      return;
    }

    const sidebar = this.sidebarRef?.nativeElement;
    const navSizer = this.navSizerRef?.nativeElement;

    if (!sidebar || !navSizer) {
      return;
    }

    if (window.innerWidth > 900) {
      this.compactNav.set(false);
      this.visibleNavItems.set(this.navItems);
      this.overflowNavItems.set([]);
      return;
    }

    const itemElements = Array.from(
      navSizer.querySelectorAll<HTMLElement>('[data-nav-sizer-item]'),
    );
    const overflowElement = navSizer.querySelector<HTMLElement>('[data-nav-sizer-overflow]');

    if (itemElements.length !== this.navItems.length || !overflowElement) {
      this.visibleNavItems.set(this.navItems);
      this.overflowNavItems.set([]);
      this.compactNav.set(false);
      return;
    }

    const sidebarStyles = window.getComputedStyle(sidebar);
    const paddingX = parseFloat(sidebarStyles.paddingLeft) + parseFloat(sidebarStyles.paddingRight);
    const gap = parseFloat(sidebarStyles.columnGap || sidebarStyles.gap || '0');
    const availableWidth = Math.max(sidebar.clientWidth - paddingX, 0);
    const itemWidths = itemElements.map((item) => Math.ceil(item.getBoundingClientRect().width));
    const overflowWidth = Math.ceil(overflowElement.getBoundingClientRect().width);

    let usedWidth = 0;
    let visibleCount = 0;

    for (let index = 0; index < itemWidths.length; index += 1) {
      const itemWidth = itemWidths[index];
      const gapBeforeItem = visibleCount > 0 ? gap : 0;
      const remainingAfterCurrent = itemWidths.length - index - 1;
      const overflowReservation = remainingAfterCurrent > 0 ? gap + overflowWidth : 0;
      const candidateWidth = usedWidth + gapBeforeItem + itemWidth + overflowReservation;

      if (candidateWidth <= availableWidth) {
        usedWidth += gapBeforeItem + itemWidth;
        visibleCount += 1;
      } else {
        break;
      }
    }

    if (visibleCount === itemWidths.length) {
      this.visibleNavItems.set(this.navItems);
      this.overflowNavItems.set([]);
      this.compactNav.set(false);
      return;
    }

    if (visibleCount === 0) {
      this.visibleNavItems.set([]);
      this.overflowNavItems.set(this.navItems);
      this.compactNav.set(true);
      return;
    }

    this.visibleNavItems.set(this.navItems.slice(0, visibleCount));
    this.overflowNavItems.set(this.navItems.slice(visibleCount));
    this.compactNav.set(true);
  }
}
