import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  effect,
  inject,
  input,
  signal,
  untracked
} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {MatCardModule} from '@angular/material/card';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatSnackBar} from '@angular/material/snack-bar';
import {MatTooltipModule} from '@angular/material/tooltip';
import {Observable} from 'rxjs';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {compatForm} from '@angular/forms/signals/compat';
import {FormField, FormRoot, required} from '@angular/forms/signals';
import {DirectionAccount, DirectionApiService} from '../../../core/api/direction-api.service';

const PASSWORD_CODES = ['PASSWORD_TOO_SHORT', 'PASSWORD_TOO_LONG', 'PASSWORD_WEAK', 'PASSWORD_CONTAINS_USERNAME'];

/** Clé de traduction du refus renvoyé par le serveur (règle de mot de passe, sinon message générique). */
export function accountErrorKey(code: string | undefined): string {
  return code && PASSWORD_CODES.includes(code) ? `DIRECTION.ACCOUNTS.ERR.${code}` : 'DIRECTION.ACCOUNTS.ERR.GENERIC';
}

/**
 * Comptes « direction » d'une société, gérés par le propriétaire de l'application : création, activation,
 * désactivation et réinitialisation du mot de passe (jamais affiché ni renvoyé par le serveur).
 */
@Component({
  selector: 'app-direction-accounts',
  standalone: true,
  imports: [
    TranslateModule, MatCardModule, MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule,
    MatProgressBarModule, MatTooltipModule, FormRoot, FormField,
  ],
  templateUrl: './direction-accounts.component.html',
  styleUrl: './societes.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DirectionAccountsComponent {
  readonly societeId = input.required<string>();

  protected readonly accounts = signal<DirectionAccount[]>([]);
  protected readonly loading = signal(false);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  /** Formulaire de création (`resetTarget` = null) ou de réinitialisation du mot de passe d'un compte. */
  protected readonly formOpen = signal(false);
  protected readonly resetTarget = signal<DirectionAccount | null>(null);
  protected readonly model = signal({username: '', fullName: '', email: '', password: ''});
  protected readonly form = compatForm(this.model, (f) => {
    required(f.password);
  });
  protected readonly canSave = computed(() => {
    const m = this.model();
    const identified = this.resetTarget() !== null || !!m.username.trim();
    return identified && !!m.password && !this.saving();
  });

  private readonly api = inject(DirectionApiService);
  private readonly snack = inject(MatSnackBar);
  private readonly translate = inject(TranslateService);
  private readonly destroyRef = inject(DestroyRef);

  constructor() {
    effect(() => {
      const id = this.societeId();
      untracked(() => this.reload(id));
    });
  }

  protected openCreate(): void {
    this.resetTarget.set(null);
    this.model.set({username: '', fullName: '', email: '', password: ''});
    this.error.set(null);
    this.formOpen.set(true);
  }

  protected openReset(account: DirectionAccount): void {
    this.resetTarget.set(account);
    this.model.set({username: account.username, fullName: '', email: '', password: ''});
    this.error.set(null);
    this.formOpen.set(true);
  }

  protected close(): void {
    this.formOpen.set(false);
    this.resetTarget.set(null);
    this.model.set({username: '', fullName: '', email: '', password: ''});
  }

  protected save(): void {
    if (!this.canSave()) return;
    const societeId = this.societeId();
    const m = this.model();
    const target = this.resetTarget();
    this.saving.set(true);
    this.error.set(null);
    const call: Observable<unknown> = target
      ? this.api.resetAccountPassword(societeId, target.userId, m.password)
      : this.api.createAccount(societeId, {
        username: m.username.trim(),
        fullName: m.fullName.trim() || undefined,
        email: m.email.trim() || undefined,
        password: m.password,
      });
    call.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.saving.set(false);
        this.close();
        this.notify(target ? 'DIRECTION.ACCOUNTS.PASSWORD_RESET_OK' : 'DIRECTION.ACCOUNTS.CREATED_OK');
        this.reload(societeId);
      },
      error: (err: { error?: { code?: string } }) => {
        this.saving.set(false);
        this.error.set(accountErrorKey(err?.error?.code));
      },
    });
  }

  protected toggle(account: DirectionAccount): void {
    this.api.setAccountActive(this.societeId(), account.userId, !account.active)
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.notify(account.active ? 'DIRECTION.ACCOUNTS.DEACTIVATED_OK' : 'DIRECTION.ACCOUNTS.ACTIVATED_OK');
        this.reload(this.societeId());
      },
      error: () => this.notify('DIRECTION.ACCOUNTS.ERR.GENERIC'),
    });
  }

  private reload(societeId: string): void {
    this.loading.set(true);
    this.api.listAccounts(societeId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (list) => {
        this.accounts.set(list);
        this.loading.set(false);
      },
      error: () => {
        this.accounts.set([]);
        this.loading.set(false);
      },
    });
  }

  private notify(key: string): void {
    this.snack.open(this.translate.instant(key), this.translate.instant('COMMON.OK'), {duration: 3000});
  }
}
