import { Component, inject, signal } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule } from '@ngx-translate/core';
import { AuthSessionService } from '../auth/auth-session.service';
import { LangService } from '../i18n/lang.service';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [
    RouterOutlet, RouterLink, RouterLinkActive,
    MatToolbarModule, MatIconModule, MatButtonModule, MatMenuModule, MatTooltipModule,
    TranslateModule
  ],
  template: `
    <!-- TOOLBAR -->
    <mat-toolbar class="topbar">
      <div class="brand">
        <mat-icon>monitor_heart</mat-icon>
        <span>{{ 'APP.TITLE' | translate }}</span>
      </div>
      <div class="topbar-right">
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
        <div class="user-info">
          <mat-icon>account_circle</mat-icon>
          <span>{{ auth.username() }}</span>
          <span>&bull;</span>
          <span>{{ auth.centerName() }}</span>
        </div>
        <button mat-flat-button class="logout-btn" (click)="onLogout()">
          <mat-icon>logout</mat-icon> {{ 'TOOLBAR.LOGOUT' | translate }}
        </button>
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
        <router-outlet />
      </main>
    </div>
  `,
  styles: [`
    :host { display: flex; flex-direction: column; height: 100vh; --green-dark: #1b5e20; --green-mid: #2e7d32; }
    .topbar {
      display: flex; justify-content: space-between; align-items: center;
      background: linear-gradient(135deg, var(--green-dark) 0%, var(--green-mid) 50%, #388e3c 100%);
      color: #fff; padding: 0 24px; height: 56px; z-index: 100;
    }
    .brand { display: flex; align-items: center; gap: 10px; font-weight: 700; font-size: 1.05rem; }
    .topbar-right { display: flex; align-items: center; gap: 12px; }
    .user-info { display: flex; align-items: center; gap: 6px; font-size: 13px; opacity: 0.9; }
    .logout-btn {
      --mdc-filled-button-container-color: rgba(255,255,255,0.15) !important;
      --mdc-filled-button-label-text-color: #fff !important;
    }
    .lang-btn { color: rgba(255,255,255,0.9) !important; }
    .lang-flag { margin-right: 8px; font-size: 18px; }

    .shell-body { display: flex; flex: 1; overflow: hidden; }

    .sidebar {
      width: 56px; min-height: 100%; background: #f5faf6; border-right: 1px solid #e8efe9;
      display: flex; flex-direction: column; padding-top: 8px; transition: width 0.2s ease;
      overflow: hidden; z-index: 50;
    }
    .sidebar.expanded { width: 220px; box-shadow: 4px 0 16px rgba(0,0,0,0.08); }

    .nav-item {
      display: flex; align-items: center; gap: 12px; padding: 12px 16px;
      color: #37474f; text-decoration: none; font-size: 14px; font-weight: 500;
      border-left: 3px solid transparent; transition: all 0.15s;
      white-space: nowrap;
    }
    .nav-item:hover { background: #e8f5e9; color: var(--green-dark); }
    .nav-item.active-nav {
      background: #e8f5e9; color: var(--green-dark);
      border-left-color: var(--green-dark); font-weight: 600;
    }
    .nav-item mat-icon { min-width: 24px; }

    .content { flex: 1; overflow-y: auto; padding: 24px; background: #fbfdfc; }
  `]
})
export class ShellComponent {
  readonly auth = inject(AuthSessionService);
  readonly lang = inject(LangService);
  readonly sidebarExpanded = signal(false);

  readonly navItems = [
    { route: '/patients', icon: 'people', label: 'NAV.PATIENTS' },
    { route: '/patients/pec-admin', icon: 'verified', label: 'NAV.PEC_ADMIN' },
    { route: '/seances', icon: 'event_note', label: 'NAV.SEANCES' },
    { route: '/facturation', icon: 'receipt', label: 'NAV.FACTURATION' },
    { route: '/reglement', icon: 'payments', label: 'NAV.REGLEMENT' }
  ];

  onLogout(): void {
    this.auth.clearSession();
    window.location.href = '/';
  }
}

