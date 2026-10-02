import {ChangeDetectionStrategy, Component, computed, inject, signal} from '@angular/core';
import {Router} from '@angular/router';
import {compatForm} from '@angular/forms/signals/compat';
import {FormField, FormRoot, required} from '@angular/forms/signals';
import {MatButtonModule} from '@angular/material/button';
import {MatCardModule} from '@angular/material/card';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatIconModule} from '@angular/material/icon';
import {MatInputModule} from '@angular/material/input';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {TranslateModule} from '@ngx-translate/core';
import {AuthApiService} from '../../core/api/auth-api.service';
import {homeRouteFor} from '../../core/auth/role-scope.guard';
import {AuthStore} from '../../core/state/auth.store';
import {MOT_DE_PASSE_MIN, motDePasseTropFaible} from './password-policy.util';

/** Codes d'erreur métier du serveur, traduits (clés `CHANGE_PASSWORD.ERR.<code>`). */
const ERREURS_CONNUES = ['PASSWORD_ACTUEL_INVALIDE', 'PASSWORD_IDENTIQUE', 'PASSWORD_TROP_FAIBLE'];

export function changePasswordErrorKey(err: unknown): string {
  const code = (err as { error?: { code?: string } } | null)?.error?.code;
  return code && ERREURS_CONNUES.includes(code) ? `CHANGE_PASSWORD.ERR.${code}` : 'CHANGE_PASSWORD.ERR.GENERIC';
}

/**
 * Remplacement obligatoire du mot de passe temporaire à la première connexion. L'application reste inaccessible
 * (garde de route et blocage serveur) tant que le mot de passe n'a pas été remplacé.
 */
@Component({
  selector: 'app-change-password-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    MatButtonModule, MatCardModule, MatFormFieldModule, MatIconModule, MatInputModule, MatProgressBarModule,
    FormRoot, FormField, TranslateModule,
  ],
  templateUrl: './change-password-page.component.html',
  styleUrl: './change-password-page.component.css',
})
export class ChangePasswordPageComponent {
  protected readonly minLength = MOT_DE_PASSE_MIN;
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly formModel = signal({currentPassword: '', newPassword: '', confirmation: ''});
  protected readonly passwordForm = compatForm(this.formModel, (form) => {
    required(form.currentPassword);
    required(form.newPassword);
    required(form.confirmation);
  });
  protected readonly different = computed(() => {
    const {newPassword, confirmation} = this.formModel();
    return confirmation.length === 0 || newPassword === confirmation;
  });
  protected readonly canSubmit = computed(() =>
    this.passwordForm().valid() && !this.faible() && this.different() && !this.loading());
  private readonly auth = inject(AuthStore);
  protected readonly faible = computed(() => {
    const {newPassword} = this.formModel();
    return newPassword.length > 0 && motDePasseTropFaible(newPassword, this.auth.username());
  });
  private readonly authApi = inject(AuthApiService);
  private readonly router = inject(Router);

  protected submit(): void {
    if (!this.canSubmit()) return;
    const {currentPassword, newPassword} = this.formModel();
    this.loading.set(true);
    this.error.set(null);
    this.authApi.changePassword(currentPassword, newPassword).subscribe({
      next: () => {
        this.auth.markPasswordChanged();
        this.loading.set(false);
        void this.router.navigate([homeRouteFor(this.auth.roles())]);
      },
      error: (err) => {
        this.error.set(changePasswordErrorKey(err));
        this.loading.set(false);
      },
    });
  }

  protected logout(): void {
    const quitter = () => {
      this.auth.clearSession();
      window.location.href = '/login';
    };
    this.authApi.logout().subscribe({next: quitter, error: quitter});
  }
}
