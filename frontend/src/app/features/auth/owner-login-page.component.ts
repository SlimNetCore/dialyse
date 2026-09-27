import {ChangeDetectionStrategy, Component, computed, inject, signal} from '@angular/core';
import {ActivatedRoute, Router, RouterLink} from '@angular/router';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {MatCardModule} from '@angular/material/card';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {compatForm} from '@angular/forms/signals/compat';
import {FormField, FormRoot, required} from '@angular/forms/signals';
import {AuthApiService} from '../../core/api/auth-api.service';
import {AuthStore} from '../../core/state/auth.store';
import {LoginPageStore} from './state/login-page.store';
import {homeRouteFor} from '../../core/auth/role-scope.guard';

/**
 * Connexion dédiée du propriétaire de la plateforme (aucune société, aucun centre) : accessible uniquement depuis
 * le lien discret de la page de connexion générale, jamais mélangée aux autres modes de connexion.
 */
@Component({
  selector: 'app-owner-login-page',
  standalone: true,
  imports: [
    TranslateModule, MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule,
    MatProgressBarModule, FormRoot, FormField, RouterLink,
  ],
  templateUrl: './owner-login-page.component.html',
  styleUrl: './owner-login-page.component.css',
  changeDetection: ChangeDetectionStrategy.Eager,
})
export class OwnerLoginPageComponent {
  readonly setupDone = inject(ActivatedRoute).snapshot.queryParamMap.get('setup') === 'done';
  readonly otpRequired = signal(false);
  readonly model = signal({username: '', password: '', otp: ''});
  readonly form = compatForm(this.model, (f) => {
    required(f.username);
    required(f.password);
  });
  readonly canSubmit = computed(() => {
    const m = this.model();
    return !!m.username && !!m.password && (!this.otpRequired() || !!m.otp.trim()) && !this.loading();
  });
  private readonly authApi = inject(AuthApiService);
  private readonly loginStore = inject(LoginPageStore);
  readonly loading = this.loginStore.loading;
  readonly error = this.loginStore.error;
  private readonly authStore = inject(AuthStore);
  private readonly router = inject(Router);
  private readonly translate = inject(TranslateService);

  doLogin(): void {
    if (!this.canSubmit()) return;
    const {username, password, otp} = this.model();
    this.loginStore.setLoading(true);
    this.loginStore.setError('');
    this.authApi.login({
      username,
      password,
      otp: this.otpRequired() ? otp.trim() : undefined,
    }).subscribe({
      next: (res) => {
        this.authStore.setSession(res);
        this.loginStore.setLoading(false);
        this.router.navigate([homeRouteFor(res.roles ?? [])]);
      },
      error: (err: { error?: { code?: string; detail?: string; message?: string } }) => {
        const code = err?.error?.code;
        if (code === 'MFA_REQUIRED') {
          this.otpRequired.set(true);
          this.model.update((m) => ({...m, otp: ''}));
        } else if (code === 'MFA_INVALID' || code === 'MFA_LOCKED') {
          this.model.update((m) => ({...m, otp: ''}));
          this.loginStore.setError(this.translate.instant(`LOGIN.${code}`));
        } else {
          this.loginStore.setError(err?.error?.detail || err?.error?.message || this.translate.instant('OWNER_LOGIN.ERROR'));
        }
        this.loginStore.setLoading(false);
      },
    });
  }
}
