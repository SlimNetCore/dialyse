import { Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';

export type LoginFormPayload = {
  centerId: string;
  username: string;
  password: string;
};

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    TranslateModule
  ],
  template: `
    <div class="login-page">
      <div class="login-left">
        <div class="login-branding">
          <mat-icon class="login-logo">monitor_heart</mat-icon>
          <h1>{{ 'APP.TITLE' | translate }}</h1>
          <p>{{ 'LOGIN.BRANDING_DESC' | translate }}</p>
        </div>
        <div class="login-decoration">
          <div class="deco-circle c1"></div>
          <div class="deco-circle c2"></div>
          <div class="deco-circle c3"></div>
        </div>
      </div>

      <div class="login-right">
        <mat-card class="login-card" appearance="outlined">
          <mat-card-header>
            <mat-card-title>{{ 'LOGIN.TITLE' | translate }}</mat-card-title>
            <mat-card-subtitle>{{ 'LOGIN.SUBTITLE' | translate }}</mat-card-subtitle>
          </mat-card-header>

          <mat-card-content>
            <form [formGroup]="form" (ngSubmit)="submit()" class="login-form">
              <mat-form-field appearance="outline">
                <mat-label>{{ 'LOGIN.CENTER' | translate }}</mat-label>
                <mat-select formControlName="centerId">
                  @for (center of centers; track center.id) {
                    <mat-option [value]="center.id">{{ center.name }}</mat-option>
                  }
                </mat-select>
                <mat-icon matPrefix>business</mat-icon>
              </mat-form-field>

              <mat-form-field appearance="outline">
                <mat-label>{{ 'LOGIN.USERNAME' | translate }}</mat-label>
                <input matInput formControlName="username" autocomplete="username" />
                <mat-icon matPrefix>person</mat-icon>
              </mat-form-field>

              <mat-form-field appearance="outline">
                <mat-label>{{ 'LOGIN.PASSWORD' | translate }}</mat-label>
                <input matInput [type]="hidePassword ? 'password' : 'text'"
                       formControlName="password" autocomplete="current-password" />
                <mat-icon matPrefix>lock</mat-icon>
                <button mat-icon-button matSuffix type="button"
                        (click)="hidePassword = !hidePassword">
                  <mat-icon>{{ hidePassword ? 'visibility_off' : 'visibility' }}</mat-icon>
                </button>
              </mat-form-field>

              <button class="login-btn" mat-flat-button color="primary"
                      type="submit" [disabled]="form.invalid || loading">
                @if (loading) {
                  <mat-spinner diameter="20" color="accent"></mat-spinner>
                } @else {
                  <ng-container>
                    <mat-icon>login</mat-icon>
                    {{ 'LOGIN.SUBMIT' | translate }}
                  </ng-container>
                }
              </button>
            </form>

            @if (feedback) {
              <div class="login-error">
                <mat-icon>error_outline</mat-icon>
                <span>{{ feedback }}</span>
              </div>
            }
          </mat-card-content>
        </mat-card>

        <p class="login-footer">{{ 'LOGIN.DEMO_HINT' | translate }}</p>
      </div>
    </div>
  `,
  styles: [`
    .login-page {
      display: grid;
      grid-template-columns: 1fr 1fr;
      min-height: calc(100vh - 64px);
    }

    /* Left panel */
    .login-left {
      background: linear-gradient(160deg, #1b5e20 0%, #2e7d32 40%, #43a047 100%);
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      position: relative;
      overflow: hidden;
      padding: 48px;
    }

    .login-branding {
      color: #fff;
      text-align: center;
      z-index: 1;
    }

    .login-logo {
      font-size: 64px;
      width: 64px;
      height: 64px;
      opacity: 0.95;
    }

    .login-branding h1 {
      font-size: 2.4rem;
      font-weight: 700;
      margin: 16px 0 8px;
      letter-spacing: -0.5px;
    }

    .login-branding p {
      font-size: 1.05rem;
      opacity: 0.85;
      line-height: 1.6;
    }

    .login-decoration {
      position: absolute;
      inset: 0;
    }

    .deco-circle {
      position: absolute;
      border-radius: 50%;
      border: 1px solid rgba(255,255,255,0.12);
    }

    .c1 { width: 300px; height: 300px; top: -60px; left: -80px; }
    .c2 { width: 200px; height: 200px; bottom: 20%; right: -40px; background: rgba(255,255,255,0.04); }
    .c3 { width: 400px; height: 400px; bottom: -120px; left: 30%; border-color: rgba(255,255,255,0.07); }

    /* Right panel */
    .login-right {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 48px 32px;
      background: #f7faf8;
    }

    .login-card {
      width: min(440px, 100%);
      padding: 8px;
    }

    .login-form {
      display: grid;
      gap: 4px;
      margin-top: 8px;
    }

    .login-btn {
      height: 48px;
      font-size: 15px;
      margin-top: 8px;
    }

    .login-error {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-top: 16px;
      padding: 10px 14px;
      border-radius: 10px;
      background: #fef2f2;
      color: #b91c1c;
      border: 1px solid #fecaca;
      font-size: 14px;
    }

    .login-footer {
      margin-top: 24px;
      font-size: 13px;
      color: #64748b;
    }

    @media (max-width: 900px) {
      .login-page {
        grid-template-columns: 1fr;
      }
      .login-left {
        display: none;
      }
    }
  `]
})
export class LoginComponent {
  private readonly fb = inject(FormBuilder);

  @Input() centers: Array<{ id: string; name: string }> = [];
  @Input() loading = false;
  @Input() feedback = '';

  @Output() login = new EventEmitter<LoginFormPayload>();

  hidePassword = true;

  readonly form = this.fb.nonNullable.group({
    centerId: ['', Validators.required],
    username: ['admin', Validators.required],
    password: ['admin123', Validators.required]
  });

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.login.emit(this.form.getRawValue());
  }
}
