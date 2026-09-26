import {ChangeDetectionStrategy, Component, computed, inject, OnInit, signal} from '@angular/core';
import {Router} from '@angular/router';
import {MatCardModule} from '@angular/material/card';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {TranslateModule} from '@ngx-translate/core';
import {compatForm} from '@angular/forms/signals/compat';
import {FormField, FormRoot, required} from '@angular/forms/signals';
import {firstValueFrom} from 'rxjs';
import {AuthApiService} from '../../core/api/auth-api.service';
import {
  isOwnerPasswordValid,
  OWNER_PASSWORD_MIN_LENGTH,
  ownerPasswordChecks,
  setupErrorKey
} from './owner-password.util';

/**
 * Installation initiale : à la première ouverture, crée le compte propriétaire (SUPERADMIN) avec un mot de passe
 * exigeant. Dès qu'un propriétaire existe, la page renvoie vers la connexion (le serveur refuse de toute façon).
 */
@Component({
  selector: 'app-setup-page',
  standalone: true,
  imports: [
    TranslateModule, MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule,
    MatProgressBarModule, FormRoot, FormField,
  ],
  templateUrl: './setup-page.component.html',
  styleUrl: './setup-page.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SetupPageComponent implements OnInit {
  protected readonly minLength = OWNER_PASSWORD_MIN_LENGTH;
  protected readonly checking = signal(true);
  protected readonly tokenRequired = signal(false);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly model = signal({
    username: '', fullName: '', email: '', password: '', confirm: '', setupToken: '',
  });
  protected readonly form = compatForm(this.model, (f) => {
    required(f.username);
    required(f.password);
    required(f.confirm);
  });
  protected readonly checks = computed(() => ownerPasswordChecks(this.model().password, this.model().username));
  protected readonly mismatch = computed(() => !!this.model().confirm && this.model().confirm !== this.model().password);
  protected readonly canSubmit = computed(() => {
    const m = this.model();
    return !!m.username.trim() && isOwnerPasswordValid(m.password, m.username) && m.password === m.confirm
      && (!this.tokenRequired() || !!m.setupToken.trim()) && !this.loading();
  });
  private readonly api = inject(AuthApiService);
  private readonly router = inject(Router);

  ngOnInit(): void {
    firstValueFrom(this.api.getSetupStatus()).then(
      (status) => {
        if (!status.required) {
          void this.router.navigate(['/login']);
          return;
        }
        this.tokenRequired.set(status.tokenRequired);
        this.checking.set(false);
      },
      () => this.checking.set(false),
    );
  }

  protected submit(): void {
    if (!this.canSubmit()) return;
    const m = this.model();
    this.loading.set(true);
    this.error.set(null);
    firstValueFrom(this.api.createOwner({
      username: m.username.trim(),
      fullName: m.fullName.trim() || undefined,
      email: m.email.trim() || undefined,
      password: m.password,
      setupToken: m.setupToken.trim() || undefined,
    })).then(
      () => {
        this.loading.set(false);
        void this.router.navigate(['/login'], {queryParams: {setup: 'done'}});
      },
      (err: { error?: { code?: string } }) => {
        this.loading.set(false);
        this.error.set(setupErrorKey(err?.error?.code));
      },
    );
  }
}
