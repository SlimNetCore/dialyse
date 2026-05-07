import {TitleCasePipe} from '@angular/common';
import {Component, inject} from '@angular/core';
import {Router} from '@angular/router';
import {TranslateModule} from '@ngx-translate/core';
import {MatCardModule} from '@angular/material/card';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatSelectModule} from '@angular/material/select';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {FormsModule} from '@angular/forms';
import {AuthApiService} from '../../core/api/auth-api.service';
import {AuthStore} from '../../core/state/auth.store';
import {AppShellStore} from '../../core/state/app-shell.store';
import {LangStore} from '../../core/state/lang.store';
import {ThemeStore} from '../../core/state/theme.store';
import {MatMenuModule} from '@angular/material/menu';
import {LoginPageStore} from './state/login-page.store';

@Component({
  selector: 'app-login-page',
  standalone: true,
  imports: [
    TranslateModule, MatCardModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatButtonModule, MatIconModule, MatProgressBarModule,
    FormsModule, MatMenuModule, TitleCasePipe
  ],
  template: `
    <div class="login-wrapper">
      <section class="login-left">
        <span class="login-kicker">Plateforme de coordination</span>
        <div class="hero-orbit"></div>
        <div class="hero-copy">
          <mat-icon class="brand-icon">monitor_heart</mat-icon>
          <h1>{{ 'APP.TITLE' | translate }}</h1>
          <p>{{ 'LOGIN.BRANDING_DESC' | translate }}</p>
        </div>

        <div class="hero-grid">
          <article>
            <span>Centres</span>
            <strong>{{ store.availableCenters().length || '—' }}</strong>
            <p>Connexion multi-centre sécurisée et contextualisée.</p>
          </article>
          <article>
            <span>Cadence</span>
            <strong>Temps réel</strong>
            <p>Alertes, prises en charge et notifications synchronisées.</p>
          </article>
          <article>
            <span>Design</span>
            <strong>{{ theme.currentTheme() | titlecase }}</strong>
            <p>Ambiance visuelle éditoriale adaptée au poste clinique.</p>
          </article>
        </div>
      </section>

      <section class="login-right">
        <div class="top-actions">
          <button mat-icon-button [matMenuTriggerFor]="langMenu"><mat-icon>translate</mat-icon></button>
          <button mat-icon-button [matMenuTriggerFor]="themeMenu"><mat-icon>palette</mat-icon></button>
          <mat-menu #langMenu="matMenu">
            @for (l of lang.languages(); track l.code) {
              <button mat-menu-item (click)="lang.setLang(l.code)">{{ l.flag }} {{ l.label }}</button>
            }
          </mat-menu>
          <mat-menu #themeMenu="matMenu">
            @for (mode of theme.modes(); track mode.code) {
              <button mat-menu-item (click)="theme.setMode(mode.code)">
                <mat-icon>{{ theme.currentMode() === mode.code ? 'radio_button_checked' : 'radio_button_unchecked' }}</mat-icon>
                {{ mode.i18nKey | translate }}
              </button>
            }
            <div class="menu-divider"></div>
            @for (t of theme.themes(); track t.code) {
              <button mat-menu-item (click)="theme.setTheme(t.code)">
                <mat-icon>{{ theme.currentTheme() === t.code ? 'radio_button_checked' : 'radio_button_unchecked' }}</mat-icon>
                {{ t.i18nKey | translate }}
              </button>
            }
          </mat-menu>
        </div>

        <mat-card class="login-card">
          <mat-card-header>
            <div class="card-eyebrow">Accès sécurisé</div>
            <mat-card-title>{{ 'LOGIN.TITLE' | translate }}</mat-card-title>
            <mat-card-subtitle>Identifiez votre centre, puis ouvrez votre espace opérateur.</mat-card-subtitle>
          </mat-card-header>

          <mat-card-content>
            @if (loading()) { <mat-progress-bar mode="indeterminate" /> }
            @if (error()) { <div class="error-msg">{{ error() }}</div> }

            <mat-form-field appearance="outline" class="full">
              <mat-label>{{ 'LOGIN.CENTER' | translate }}</mat-label>
              <mat-select [(ngModel)]="selectedCenter">
                @for (c of store.availableCenters(); track c.id) {
                  <mat-option [value]="c.id">{{ c.name }}</mat-option>
                }
              </mat-select>
            </mat-form-field>

            <mat-form-field appearance="outline" class="full">
              <mat-label>{{ 'LOGIN.USERNAME' | translate }}</mat-label>
              <input matInput [(ngModel)]="username" />
            </mat-form-field>

            <mat-form-field appearance="outline" class="full">
              <mat-label>{{ 'LOGIN.PASSWORD' | translate }}</mat-label>
              <input matInput type="password" [(ngModel)]="password" (keydown.enter)="doLogin()" />
            </mat-form-field>

            <button mat-flat-button class="login-btn" (click)="doLogin()" [disabled]="loading()">
              <mat-icon>login</mat-icon> {{ 'LOGIN.SUBMIT' | translate }}
            </button>
          </mat-card-content>
        </mat-card>
      </section>
    </div>
  `,
  styles: [`
    :host {
      display: block;
      min-height: 100vh;
    }

    .login-wrapper {
      min-height: 100vh;
      display: grid;
      grid-template-columns: minmax(0, 1.25fr) minmax(420px, 0.75fr);
      background: transparent;
    }
    .login-left {
      position: relative;
      overflow: hidden;
      padding: 56px;
      display: flex;
      flex-direction: column;
      justify-content: space-between;
      gap: 36px;
      background: radial-gradient(circle at 18% 22%, var(--app-glow-primary), transparent 26%),
      radial-gradient(circle at 80% 18%, var(--app-glow-accent), transparent 22%),
      linear-gradient(145deg, color-mix(in srgb, var(--app-bg) 92%, var(--app-bg-alt)), color-mix(in srgb, var(--app-bg) 78%, var(--app-bg-alt)));
      border-right: 1px solid var(--app-border);
    }

    .login-kicker {
      position: relative;
      z-index: 1;
      display: inline-flex;
      width: fit-content;
      padding: 8px 14px;
      border-radius: 999px;
      border: 1px solid var(--app-border-strong);
      background: rgba(255, 255, 255, 0.04);
      color: var(--app-primary);
      font-size: 11px;
      font-weight: 700;
      letter-spacing: 0.18em;
      text-transform: uppercase;
    }

    .hero-orbit {
      position: absolute;
      inset: auto -120px -120px auto;
      width: 420px;
      aspect-ratio: 1;
      border-radius: 50%;
      background: radial-gradient(circle, rgba(97, 216, 223, 0.24), transparent 42%),
      radial-gradient(circle at 30% 30%, rgba(255, 255, 255, 0.12), transparent 25%);
      filter: blur(10px);
      pointer-events: none;
    }

    .hero-copy {
      position: relative;
      z-index: 1;
      max-width: 620px;
    }

    .brand-icon {
      font-size: 84px;
      width: 84px;
      height: 84px;
      margin-bottom: 20px;
      color: var(--app-primary);
    }

    .login-left h1 {
      margin: 0 0 14px;
      font-size: clamp(3rem, 5vw, 4.8rem);
      line-height: 0.96;
      max-width: 620px;
    }

    .login-left p {
      margin: 0;
      font-size: 1.06rem;
      line-height: 1.8;
      color: var(--app-muted);
      white-space: pre-line;
      max-width: 520px;
    }

    .hero-grid {
      position: relative;
      z-index: 1;
      display: grid;
      grid-template-columns: repeat(3, minmax(0, 1fr));
      gap: 14px;
    }

    .hero-grid article {
      padding: 18px;
      border-radius: 24px;
      background: rgba(255, 255, 255, 0.04);
      border: 1px solid var(--app-border);
      backdrop-filter: blur(14px);
    }

    .hero-grid span {
      display: block;
      margin-bottom: 10px;
      color: var(--app-muted);
      font-size: 11px;
      letter-spacing: 0.14em;
      text-transform: uppercase;
    }

    .hero-grid strong {
      display: block;
      margin-bottom: 8px;
      font-size: 1.35rem;
      font-weight: 800;
      color: var(--app-text);
    }

    .hero-grid p {
      font-size: 12px;
      line-height: 1.6;
      color: var(--app-muted);
      max-width: none;
    }
    .login-right {
      position: relative;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 40px;
    }

    .top-actions {
      position: absolute;
      top: 20px;
      right: 20px;
      display: flex;
      gap: 8px;
    }

    .top-actions button {
      background: var(--app-frost);
      border: 1px solid var(--app-border);
    }

    .menu-divider {
      margin: 6px 12px;
      border-top: 1px solid var(--app-border);
    }

    .login-card {
      width: min(100%, 460px);
      padding: 28px;
    }

    .card-eyebrow {
      margin-bottom: 12px;
      color: var(--app-primary);
      font-size: 11px;
      font-weight: 700;
      letter-spacing: 0.16em;
      text-transform: uppercase;
    }

    :host ::ng-deep .login-card .mat-mdc-card-header {
      display: block;
      margin-bottom: 14px;
      padding: 0;
    }

    :host ::ng-deep .login-card .mat-mdc-card-title {
      margin-bottom: 8px;
      font-size: 2rem;
    }

    :host ::ng-deep .login-card .mat-mdc-card-subtitle {
      color: var(--app-muted);
      line-height: 1.6;
    }

    :host ::ng-deep .login-card .mat-mdc-card-content {
      padding: 0;
    }
    .full { width: 100%; }
    .login-btn {
      width: 100%;
      margin-top: 10px;
      min-height: 48px;
    }


    .error-msg {
      background: rgba(239, 68, 68, 0.12);
      color: #ffb4b4;
      padding: 10px 12px;
      border-radius: 14px;
      border: 1px solid rgba(239, 68, 68, 0.18);
      margin-bottom: 12px;
      font-size: 13px;
    }

    @media (max-width: 1100px) {
      .login-wrapper {
        grid-template-columns: 1fr;
      }
      .login-left {
        min-height: 42vh;
      }
    }

    @media (max-width: 760px) {
      .login-left {
        padding: 28px 22px;
      }
      .hero-grid {
        grid-template-columns: 1fr;
      }
      .login-right {
        padding: 18px;
      }
      .top-actions {
        position: static;
        margin-bottom: 14px;
        justify-content: flex-end;
        width: 100%;
      }
      .login-card {
        width: 100%;
      }
    }
  `]
})
export class LoginPageComponent {
  private readonly authApi = inject(AuthApiService);
  private readonly loginStore = inject(LoginPageStore);
  readonly lang = inject(LangStore);
  readonly store = inject(AppShellStore);
  readonly theme = inject(ThemeStore);
  private readonly authStore = inject(AuthStore);
  private readonly router = inject(Router);
  readonly loading = this.loginStore.loading;
  readonly error = this.loginStore.error;

  get selectedCenter(): string {
    return this.loginStore.selectedCenter();
  }

  set selectedCenter(value: string) {
    this.loginStore.setSelectedCenter(value);
  }

  get username(): string {
    return this.loginStore.username();
  }

  set username(value: string) {
    this.loginStore.setUsername(value);
  }

  get password(): string {
    return this.loginStore.password();
  }

  set password(value: string) {
    this.loginStore.setPassword(value);
  }

  doLogin(): void {
    if (!this.selectedCenter || !this.username || !this.password) return;
    this.loginStore.setLoading(true);
    this.loginStore.setError('');
    this.authApi.login({centerId: this.selectedCenter, username: this.username, password: this.password}).subscribe({
      next: (res) => {
        this.authStore.setSession(res);
        this.store.switchCenter(res.centerId);
        this.loginStore.setLoading(false);
        this.router.navigate(['/dashboard']);
      },
      error: (err) => {
        this.loginStore.setError(err?.error?.detail || err?.error?.message || 'Erreur de connexion');
        this.loginStore.setLoading(false);
      }
    });
  }
}

