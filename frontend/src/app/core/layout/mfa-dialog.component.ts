import {ChangeDetectionStrategy, Component, computed, inject, OnInit, signal} from '@angular/core';
import {MatDialogModule, MatDialogRef} from '@angular/material/dialog';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {TranslateModule} from '@ngx-translate/core';
import {compatForm} from '@angular/forms/signals/compat';
import {FormField, FormRoot} from '@angular/forms/signals';
import {firstValueFrom} from 'rxjs';
import {AuthApiService, MfaEnrollment} from '../api/auth-api.service';
import {mfaErrorKey} from './mfa.util';

type Step = 'loading' | 'disabled' | 'enrolling' | 'codes' | 'enabled';

/**
 * Double authentification (TOTP) de l'utilisateur connecté : activation (secret à saisir dans l'application
 * d'authentification, confirmation par un premier code, codes de secours montrés une seule fois) et désactivation.
 */
@Component({
  selector: 'app-mfa-dialog',
  standalone: true,
  imports: [
    TranslateModule, MatDialogModule, MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule,
    MatProgressBarModule, FormRoot, FormField,
  ],
  templateUrl: './mfa-dialog.component.html',
  styleUrl: './mfa-dialog.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MfaDialogComponent implements OnInit {
  protected readonly dialogRef = inject(MatDialogRef<MfaDialogComponent>);
  protected readonly step = signal<Step>('loading');
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly enrollment = signal<MfaEnrollment | null>(null);
  protected readonly recoveryCodes = signal<string[]>([]);
  protected readonly copied = signal(false);
  protected readonly model = signal({code: ''});
  protected readonly form = compatForm(this.model);
  protected readonly canSubmit = computed(() => this.model().code.trim().length >= 6 && !this.busy());
  private readonly api = inject(AuthApiService);

  ngOnInit(): void {
    firstValueFrom(this.api.mfaStatus()).then(
      (s) => this.step.set(s.enabled ? 'enabled' : 'disabled'),
      () => {
        this.error.set('MFA.ERR.GENERIC');
        this.step.set('disabled');
      },
    );
  }

  protected startEnrollment(): void {
    this.run(() => firstValueFrom(this.api.mfaEnroll()), (e) => {
      this.enrollment.set(e);
      this.model.set({code: ''});
      this.step.set('enrolling');
    });
  }

  protected confirm(): void {
    if (!this.canSubmit()) return;
    this.run(() => firstValueFrom(this.api.mfaConfirm(this.model().code.trim())), (r) => {
      this.recoveryCodes.set(r.recoveryCodes);
      this.enrollment.set(null);
      this.model.set({code: ''});
      this.step.set('codes');
    });
  }

  protected disable(): void {
    if (!this.canSubmit()) return;
    this.run(() => firstValueFrom(this.api.mfaDisable(this.model().code.trim())), () => {
      this.model.set({code: ''});
      this.step.set('disabled');
    });
  }

  protected copyCodes(): void {
    void navigator.clipboard?.writeText(this.recoveryCodes().join('\n')).then(() => this.copied.set(true));
  }

  private run<T>(call: () => Promise<T>, onSuccess: (value: T) => void): void {
    this.busy.set(true);
    this.error.set(null);
    call().then(
      (value) => {
        this.busy.set(false);
        onSuccess(value);
      },
      (err: { error?: { code?: string } }) => {
        this.busy.set(false);
        this.error.set(mfaErrorKey(err?.error?.code));
      },
    );
  }
}
