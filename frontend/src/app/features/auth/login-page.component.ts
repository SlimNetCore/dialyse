import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { FormsModule } from '@angular/forms';
import { AuthApiService } from '../../core/api/auth-api.service';
import { AuthSessionService } from '../../core/auth/auth-session.service';
import { AppShellStore } from '../../core/state/app-shell.store';
import { LangService } from '../../core/i18n/lang.service';
import { MatMenuModule } from '@angular/material/menu';

@Component({
  selector: 'app-login-page',
  standalone: true,
  imports: [
    TranslateModule, MatCardModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatButtonModule, MatIconModule, MatProgressBarModule,
    FormsModule, MatMenuModule
  ],
  template: `
    <div class="login-wrapper">
      <div class="login-left">
        <mat-icon class="brand-icon">monitor_heart</mat-icon>
        <h1>{{ 'APP.TITLE' | translate }}</h1>
        <p>{{ 'LOGIN.BRANDING_DESC' | translate }}</p>
      </div>
      <div class="login-right">
        <div class="lang-switcher">
          <button mat-icon-button [matMenuTriggerFor]="langMenu"><mat-icon>translate</mat-icon></button>
          <mat-menu #langMenu="matMenu">
            @for (l of lang.languages; track l.code) {
              <button mat-menu-item (click)="lang.setLang(l.code)">{{ l.flag }} {{ l.label }}</button>
            }
          </mat-menu>
        </div>
        <mat-card class="login-card">
          <mat-card-header><mat-card-title>{{ 'LOGIN.TITLE' | translate }}</mat-card-title></mat-card-header>
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
            <p class="hint">{{ 'LOGIN.DEMO_HINT' | translate }}</p>
          </mat-card-content>
        </mat-card>
      </div>
    </div>
  `,
  styles: [`
    .login-wrapper { display: flex; height: 100vh; }
    .login-left {
      flex: 1; background: linear-gradient(135deg, #1b5e20, #2e7d32, #388e3c);
      color: #fff; display: flex; flex-direction: column; align-items: center; justify-content: center;
      padding: 40px; text-align: center;
    }
    .brand-icon { font-size: 80px; width: 80px; height: 80px; margin-bottom: 24px; opacity: 0.9; }
    .login-left h1 { font-size: 2.4rem; margin: 0 0 12px; }
    .login-left p { font-size: 1.1rem; opacity: 0.85; white-space: pre-line; max-width: 400px; }
    .login-right {
      width: 480px; display: flex; align-items: center; justify-content: center;
      background: #f7faf8; position: relative;
    }
    .lang-switcher { position: absolute; top: 16px; right: 16px; }
    .login-card { width: 380px; padding: 24px; }
    .full { width: 100%; }
    .login-btn {
      width: 100%; margin-top: 8px;
      --mdc-filled-button-container-color: #1b5e20 !important;
      --mdc-filled-button-label-text-color: #fff !important;
    }
    .hint { font-size: 12px; color: #888; text-align: center; margin-top: 12px; }
    .error-msg { background: #fef2f2; color: #991b1b; padding: 8px 12px; border-radius: 8px; margin-bottom: 12px; font-size: 13px; }
    @media (max-width: 900px) { .login-left { display: none; } .login-right { width: 100%; } }
  `]
})
export class LoginPageComponent {
  private readonly authApi = inject(AuthApiService);
  private readonly authSession = inject(AuthSessionService);
  readonly store = inject(AppShellStore);
  readonly lang = inject(LangService);
  private readonly router = inject(Router);

  selectedCenter = '';
  username = '';
  password = '';
  loading = signal(false);
  error = signal('');

  doLogin(): void {
    if (!this.selectedCenter || !this.username || !this.password) return;
    this.loading.set(true);
    this.error.set('');
    this.authApi.login({ centerId: this.selectedCenter, username: this.username, password: this.password }).subscribe({
      next: (res) => {
        this.authSession.setSession(res);
        this.store.switchCenter(res.centerId);
        this.loading.set(false);
        this.router.navigate(['/patients']);
      },
      error: (err) => {
        this.error.set(err?.error?.detail || err?.error?.message || 'Erreur de connexion');
        this.loading.set(false);
      }
    });
  }
}

