import {TitleCasePipe} from '@angular/common';
import {ChangeDetectionStrategy, Component, computed, inject, signal} from '@angular/core';
import {Router} from '@angular/router';
import {TranslateModule} from '@ngx-translate/core';
import {MatCardModule} from '@angular/material/card';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatSelectModule} from '@angular/material/select';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {compatForm} from '@angular/forms/signals/compat';
import {FormField, FormRoot, required} from '@angular/forms/signals';
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
    TranslateModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    FormRoot,
    FormField,
    MatMenuModule,
    TitleCasePipe,
  ],
  templateUrl: './login-page.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './login-page.component.css',
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

  readonly loginModel = signal({
    selectedCenter: '',
    username: '',
    password: '',
  });

  readonly loginForm = compatForm(this.loginModel, (form) => {
    required(form.selectedCenter);
    required(form.username);
    required(form.password);
  });

  readonly canSubmit = computed(() => {
    const form = this.loginModel();
    return !!form.selectedCenter && !!form.username && !!form.password && !this.loading();
  });

  doLogin(): void {
    const {selectedCenter, username, password} = this.loginModel();
    if (!selectedCenter || !username || !password || this.loading()) return;
    this.loginStore.setLoading(true);
    this.loginStore.setError('');
    this.authApi
      .login({centerId: selectedCenter, username, password})
      .subscribe({
        next: (res) => {
          this.authStore.setSession(res);
          this.store.switchCenter(res.centerId);
          this.loginStore.setLoading(false);
          this.router.navigate(['/dashboard']);
        },
        error: (err) => {
          this.loginStore.setError(
            err?.error?.detail || err?.error?.message || 'Erreur de connexion',
          );
          this.loginStore.setLoading(false);
        },
      });
  }
}
