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
import {MatSelectModule} from '@angular/material/select';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatSnackBar} from '@angular/material/snack-bar';
import {MatTooltipModule} from '@angular/material/tooltip';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {compatForm} from '@angular/forms/signals/compat';
import {FormField, FormRoot, required} from '@angular/forms/signals';
import {Observable} from 'rxjs';
import {AdminAccount, SocieteAdminApiService} from '../../../core/api/societe-admin-api.service';
import {CentreSociete} from '../../../core/api/societe-api.service';
import {accountErrorKey} from './direction-accounts.component';
import {adminToForm, centreNames, emptyAdminForm, toUpdatePayload} from './admin-accounts.util';

type Mode = 'create' | 'edit' | 'reset';

/**
 * Administrateurs des centres d'une société, gérés par le propriétaire de l'application : création, modification
 * (nom, e-mail et centres — par exemple pour affecter un administrateur existant à un nouveau centre),
 * activation, désactivation et réinitialisation du mot de passe (jamais affiché ni renvoyé par le serveur).
 */
@Component({
  selector: 'app-admin-accounts',
  standalone: true,
  imports: [
    TranslateModule, MatCardModule, MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatProgressBarModule, MatTooltipModule, FormRoot, FormField,
  ],
  templateUrl: './admin-accounts.component.html',
  styleUrl: './societes.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminAccountsComponent {
  readonly societeId = input.required<string>();
  readonly centres = input.required<CentreSociete[]>();

  protected readonly accounts = signal<AdminAccount[]>([]);
  protected readonly loading = signal(false);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly formOpen = signal(false);
  protected readonly mode = signal<Mode>('create');
  /** Compte visé par une modification ou une réinitialisation. */
  protected readonly target = signal<AdminAccount | null>(null);
  protected readonly activeCentres = computed(() => this.centres().filter((c) => c.actif));
  protected readonly model = signal(emptyAdminForm());
  protected readonly form = compatForm(this.model, (f) => {
    required(f.password);
  });
  protected readonly centreNames = centreNames;
  protected readonly canSave = computed(() => {
    const m = this.model();
    if (this.saving()) return false;
    switch (this.mode()) {
      case 'create':
        return !!m.username.trim() && !!m.centerId && !!m.password;
      case 'edit':
        return m.centerIds.length > 0;
      case 'reset':
        return !!m.password;
    }
  });

  private readonly api = inject(SocieteAdminApiService);
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
    this.open('create', null, {...emptyAdminForm(), centerId: this.activeCentres()[0]?.id ?? ''});
  }

  protected openEdit(account: AdminAccount): void {
    this.open('edit', account, adminToForm(account));
  }

  protected openReset(account: AdminAccount): void {
    this.open('reset', account, adminToForm(account));
  }

  protected close(): void {
    this.formOpen.set(false);
    this.target.set(null);
    this.model.set(emptyAdminForm());
  }

  protected save(): void {
    if (!this.canSave()) return;
    const societeId = this.societeId();
    const m = this.model();
    const target = this.target();
    const mode = this.mode();
    this.saving.set(true);
    this.error.set(null);
    let call: Observable<unknown>;
    if (mode === 'reset' && target) {
      call = this.api.resetPassword(societeId, target.userId, m.password);
    } else if (mode === 'edit' && target) {
      call = this.api.update(societeId, target.userId, toUpdatePayload(m));
    } else {
      call = this.api.create(societeId, {
        centerId: m.centerId,
        username: m.username.trim(),
        fullName: m.fullName.trim() || undefined,
        email: m.email.trim() || undefined,
        password: m.password,
      });
    }
    call.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.saving.set(false);
        this.close();
        this.notify(mode === 'reset' ? 'ADMIN_ACCOUNTS.PASSWORD_RESET_OK'
          : mode === 'edit' ? 'ADMIN_ACCOUNTS.UPDATED_OK' : 'ADMIN_ACCOUNTS.CREATED_OK');
        this.reload(societeId);
      },
      error: (err: { error?: { code?: string } }) => {
        this.saving.set(false);
        this.error.set(accountErrorKey(err?.error?.code));
      },
    });
  }

  protected toggle(account: AdminAccount): void {
    this.api.setActive(this.societeId(), account.userId, !account.active)
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.notify(account.active ? 'ADMIN_ACCOUNTS.DEACTIVATED_OK' : 'ADMIN_ACCOUNTS.ACTIVATED_OK');
        this.reload(this.societeId());
      },
      error: () => this.notify('DIRECTION.ACCOUNTS.ERR.GENERIC'),
    });
  }

  private open(mode: Mode, account: AdminAccount | null, model: ReturnType<typeof emptyAdminForm>): void {
    this.mode.set(mode);
    this.target.set(account);
    this.model.set(model);
    this.error.set(null);
    this.formOpen.set(true);
  }

  private reload(societeId: string): void {
    this.loading.set(true);
    this.api.list(societeId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
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
