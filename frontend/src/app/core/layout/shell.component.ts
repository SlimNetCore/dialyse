import {Component, inject, OnInit, signal} from '@angular/core';
import {NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet} from '@angular/router';
import {MatToolbarModule} from '@angular/material/toolbar';
import {MatIconModule} from '@angular/material/icon';
import {MatButtonModule} from '@angular/material/button';
import {MatMenuModule} from '@angular/material/menu';
import {MatTooltipModule} from '@angular/material/tooltip';
import {TranslateModule} from '@ngx-translate/core';
import {AuthSessionService} from '../auth/auth-session.service';
import {LangService} from '../i18n/lang.service';
import {ThemeService} from '../theme/theme.service';
import {WebSocketService} from '../ws/websocket.service';
import {NotificationBellComponent} from './notification-bell.component';
import {AuthApiService} from '../api/auth-api.service';
import {filter} from 'rxjs/operators';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [
    RouterOutlet, RouterLink, RouterLinkActive,
    MatToolbarModule, MatIconModule, MatButtonModule, MatMenuModule, MatTooltipModule,
    TranslateModule, NotificationBellComponent
  ],
  template: `
    <!-- TOOLBAR -->
    <mat-toolbar class="topbar">
      <div class="brand">
        <mat-icon>monitor_heart</mat-icon>
        <span>{{ 'APP.TITLE' | translate }}</span>
      </div>
      <div class="topbar-right">
        <!-- Notification bell -->
        <app-notification-bell />

        <!-- Language switcher -->
        <button mat-icon-button [matMenuTriggerFor]="langMenu" class="lang-btn">
          <mat-icon>translate</mat-icon>
        </button>
        <mat-menu #langMenu="matMenu">
          @for (l of lang.languages; track l.code) {
            <button mat-menu-item (click)="lang.setLang(l.code)" [class.active-lang]="lang.currentLang() === l.code">
              <span class="lang-flag">{{ l.flag }}</span> <span>{{ l.label }}</span>
            </button>
          }
        </mat-menu>

        <!-- Theme switcher -->
        <button mat-icon-button [matMenuTriggerFor]="themeMenu" class="theme-btn" [matTooltip]="'THEME.TITLE' | translate">
          <mat-icon>palette</mat-icon>
        </button>
        <mat-menu #themeMenu="matMenu">
          @for (mode of theme.modes; track mode.code) {
            <button mat-menu-item (click)="theme.setMode(mode.code)"
                    [class.active-theme]="theme.currentMode() === mode.code">
              <mat-icon>{{ theme.currentMode() === mode.code ? 'radio_button_checked' : 'radio_button_unchecked' }}</mat-icon>
              {{ mode.i18nKey | translate }}
            </button>
          }
          <div class="menu-divider"></div>
          @for (t of theme.themes; track t.code) {
            <button mat-menu-item (click)="theme.setTheme(t.code)" [class.active-theme]="theme.currentTheme() === t.code">
              <mat-icon>{{ theme.currentTheme() === t.code ? 'radio_button_checked' : 'radio_button_unchecked' }}</mat-icon>
              {{ t.i18nKey | translate }}
            </button>
          }
        </mat-menu>

        <!-- User profile menu -->
        <button mat-button [matMenuTriggerFor]="userMenu" class="user-btn">
          <mat-icon>account_circle</mat-icon>
          <span class="user-name">{{ auth.fullName() || auth.username() }}</span>
          <span class="separator">•</span>
          <span class="center-name">{{ auth.centerName() }}</span>
        </button>
        <mat-menu #userMenu="matMenu">
          @if (auth.hasRole('ROLE_ADMIN') || auth.hasRole('ADMIN')) {
            <button mat-menu-item (click)="router.navigate(['/patients/pec-admin'])">
              <mat-icon>verified</mat-icon> {{ 'NAV.PEC_ADMIN' | translate }}
            </button>
            <button mat-menu-item (click)="router.navigate(['/modeles-document'])">
              <mat-icon>description</mat-icon> Modèles de documents
            </button>
            <button mat-menu-item (click)="router.navigate(['/admin/users'])">
              <mat-icon>manage_accounts</mat-icon> {{ 'NAV.ADMIN' | translate }}
            </button>
          }
          <button mat-menu-item (click)="onLogout()">
            <mat-icon>logout</mat-icon> {{ 'TOOLBAR.LOGOUT' | translate }}
          </button>
        </mat-menu>
      </div>
    </mat-toolbar>

    <div class="shell-body">
      <!-- SIDEBAR -->
      <nav class="sidebar" [class.expanded]="sidebarExpanded()"
           (mouseenter)="sidebarExpanded.set(true)"
           (mouseleave)="sidebarExpanded.set(false)">
        @for (item of navItems; track item.route) {
          <a [routerLink]="item.route" routerLinkActive="active-nav" class="nav-item"
             [matTooltip]="sidebarExpanded() ? '' : item.label">
            <mat-icon>{{ item.icon }}</mat-icon>
            @if (sidebarExpanded()) {
              <span class="nav-label">{{ item.label | translate }}</span>
            }
          </a>
        }
      </nav>

      <!-- MAIN CONTENT -->
      <main class="content">
        @if (breadcrumbs().length > 0) {
          <div class="breadcrumb">
            @for (b of breadcrumbs(); track $index; let i = $index) {
              <button type="button" class="crumb-btn" [class.last]="i === breadcrumbs().length - 1" (click)="goBreadcrumb(i)">{{ b }}</button>
              @if (i < breadcrumbs().length - 1) { <mat-icon class="sep">chevron_right</mat-icon> }
            }
          </div>
        }
        <router-outlet />
      </main>
    </div>
  `,
  styles: [`
    :host { display: flex; flex-direction: column; height: 100vh; }
    .topbar {
      display: flex; justify-content: space-between; align-items: center;
      background: var(--app-surface);
      border-bottom: 1px solid var(--app-border);
      color: var(--app-text);
      padding: 0 20px;
      height: 60px;
      z-index: 100;
    }
    .brand { display: flex; align-items: center; gap: 10px; font-weight: 700; font-size: 1.05rem; color: var(--app-primary); }
    .topbar-right { display: flex; align-items: center; gap: 8px; }
    .user-btn {
      display: flex; align-items: center; gap: 6px; font-size: 13px;
      color: var(--app-text) !important;
      --mdc-text-button-label-text-color: var(--app-text) !important;
    }
    .user-name { margin-left: 2px; }
    .separator { margin: 0 8px; color: var(--app-muted); }
    .center-name { margin-left: 2px; font-weight: 500; }
    .lang-btn, .theme-btn { color: var(--app-text) !important; }
    .lang-flag { margin-right: 8px; font-size: 18px; }
    .active-theme { color: var(--app-primary); font-weight: 600; }

    .menu-divider {
      margin: 6px 12px;
      border-top: 1px solid var(--app-border);
    }

    .shell-body { display: flex; flex: 1; overflow: hidden; }

    .sidebar {
      position: relative;
      width: 56px;
      min-height: 100%;
      background: var(--app-surface);
      border-right: 1px solid var(--app-primary-outline);
      display: flex; flex-direction: column; padding-top: 8px; transition: width 0.2s ease;
      overflow: hidden; z-index: 50;
    }

    .sidebar::after {
      content: '';
      position: absolute;
      top: 0;
      right: 0;
      width: 3px;
      height: 100%;
      background: linear-gradient(180deg, var(--app-primary), color-mix(in srgb, var(--app-primary) 35%, transparent));
      pointer-events: none;
    }
    .sidebar.expanded { width: 220px; box-shadow: 4px 0 16px rgba(0,0,0,0.08); }

    .nav-item {
      display: flex; align-items: center; gap: 12px; padding: 12px 16px;
      color: var(--app-muted); text-decoration: none; font-size: 14px; font-weight: 500;
      border-left: 3px solid transparent; transition: all 0.15s;
      white-space: nowrap;
    }
    .nav-item:hover { background: var(--app-primary-soft); color: var(--app-primary); }
    .nav-item.active-nav {
      background: var(--app-primary-soft); color: var(--app-primary);
      border-left-color: var(--app-primary); font-weight: 600;
    }
    .nav-item mat-icon { min-width: 24px; }

    .content { flex: 1; overflow-y: auto; padding: 20px; background: var(--app-bg); }
    .breadcrumb { display:flex; align-items:center; gap:4px; margin-bottom:10px; color:var(--app-muted); font-size:12px; }
    .crumb-btn { border:0; background:transparent; cursor:pointer; color:var(--app-muted); font-size:12px; padding:0; }
    .crumb-btn:hover { color:var(--app-primary); text-decoration:underline; }
    .crumb-btn.last { color:var(--app-primary); font-weight:600; cursor:default; text-decoration:none; }
    .sep { font-size:16px; width:16px; height:16px; color:#9ca3af; }
  `]
})
export class ShellComponent implements OnInit {
  readonly auth = inject(AuthSessionService);
  readonly lang = inject(LangService);
  readonly theme = inject(ThemeService);
  readonly router = inject(Router);
  private readonly ws = inject(WebSocketService);
  private readonly authApi = inject(AuthApiService);
  readonly sidebarExpanded = signal(false);
  readonly breadcrumbs = signal<string[]>([]);
  private breadcrumbRoutes: string[] = [];

  readonly navItems = [
    { route: '/dashboard', icon: 'dashboard', label: 'NAV.DASHBOARD' },
    { route: '/patients', icon: 'people', label: 'NAV.PATIENTS' },
    { route: '/seances', icon: 'event_note', label: 'NAV.SEANCES' },
    { route: '/facturation', icon: 'receipt', label: 'NAV.FACTURATION' },
    { route: '/reglement', icon: 'payments', label: 'NAV.REGLEMENT' }
  ];

  ngOnInit(): void {
    this.ws.connect();
    this.computeBreadcrumb(this.router.url);
    this.router.events.pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd)).subscribe(e => {
      this.computeBreadcrumb(e.urlAfterRedirects);
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
      users: 'Utilisateurs',
      'modeles-document': 'Modèles documents'
    };
    const segs = url.split('?')[0].split('/').filter(Boolean);
    this.breadcrumbRoutes = segs;
    this.breadcrumbs.set(segs.map(s => map[s] ?? s));
  }

  goBreadcrumb(index: number): void {
    if (index < 0 || index >= this.breadcrumbRoutes.length) return;
    const target = '/' + this.breadcrumbRoutes.slice(0, index + 1).join('/');
    this.router.navigateByUrl(target);
  }

  onLogout(): void {
    this.authApi.logout().subscribe({
      next: () => this.finalizeLogout(),
      error: () => this.finalizeLogout()
    });
  }

  private finalizeLogout(): void {
    this.ws.disconnect();
    this.auth.clearSession();
    window.location.href = '/login';
  }
}
