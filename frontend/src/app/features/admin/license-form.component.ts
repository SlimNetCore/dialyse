import {ChangeDetectionStrategy, Component, inject, signal} from '@angular/core';
import {Router, RouterLink} from '@angular/router';
import {MatCardModule} from '@angular/material/card';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatSelectModule} from '@angular/material/select';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatTooltipModule} from '@angular/material/tooltip';
import {MatSnackBar, MatSnackBarModule} from '@angular/material/snack-bar';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {compatForm} from '@angular/forms/signals/compat';
import {FormField, FormRoot, required} from '@angular/forms/signals';
import {LicenseApiService} from '../../core/api/license-api.service';
import {AppShellStore} from '../../core/state/app-shell.store';

function addDays(date: Date, days: number): Date {
  const copy = new Date(date);
  copy.setDate(copy.getDate() + days);
  return copy;
}

function toDateInputValue(date: Date): string {
  return date.toISOString().slice(0, 10);
}

@Component({
  selector: 'app-license-form',
  standalone: true,
  imports: [
    RouterLink,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    MatSnackBarModule,
    MatProgressSpinnerModule,
    FormRoot,
    FormField,
  ],
  templateUrl: './license-form.component.html',
  styleUrl: './license-form.component.css',
  changeDetection: ChangeDetectionStrategy.Eager,
})
export class LicenseFormComponent {
  readonly saving = signal(false);
  readonly issuedKey = signal<string | null>(null);
  readonly form = signal({
    centerId: '',
    type: 'STANDARD' as 'STANDARD' | 'TRIAL',
    maxUsers: 5,
    validFrom: toDateInputValue(new Date()),
    validUntil: toDateInputValue(addDays(new Date(), 365)),
  });
  readonly licenseForm = compatForm(this.form, (f) => {
    required(f.centerId);
    required(f.validFrom);
    required(f.validUntil);
  });
  protected readonly appShell = inject(AppShellStore);
  private readonly api = inject(LicenseApiService);
  private readonly router = inject(Router);
  private readonly snackbar = inject(MatSnackBar);

  applyTrialDefaults(): void {
    this.form.update((f) => ({
      ...f,
      type: 'TRIAL',
      maxUsers: f.maxUsers || 3,
      validUntil: toDateInputValue(addDays(new Date(f.validFrom), 30)),
    }));
  }

  applyStandardDefaults(): void {
    this.form.update((f) => ({
      ...f,
      type: 'STANDARD',
      validUntil: toDateInputValue(addDays(new Date(f.validFrom), 365)),
    }));
  }

  save(): void {
    if (this.saving()) return;
    const f = this.form();
    if (!f.centerId || !f.validFrom || !f.validUntil) {
      this.snackbar.open('Renseignez le centre et les dates de validité', 'OK', {duration: 2500});
      return;
    }
    if (new Date(f.validUntil) <= new Date(f.validFrom)) {
      this.snackbar.open('La date de fin doit être postérieure à la date de début', 'OK', {duration: 3000});
      return;
    }

    this.saving.set(true);
    this.api
      .issue({
        centerId: f.centerId,
        type: f.type,
        maxUsers: f.maxUsers,
        validFrom: new Date(f.validFrom).toISOString(),
        validUntil: new Date(f.validUntil).toISOString(),
      })
      .subscribe({
        next: (license) => {
          this.saving.set(false);
          this.issuedKey.set(license.licenseKey);
        },
        error: (err) => {
          this.saving.set(false);
          this.snackbar.open(err?.error?.detail || "Échec de l'émission de la licence", 'OK', {duration: 3500});
        },
      });
  }

  copyIssuedKey(): void {
    const key = this.issuedKey();
    if (!key) return;
    navigator.clipboard
      .writeText(key)
      .then(() => this.snackbar.open('Clé de licence copiée', 'OK', {duration: 2000}))
      .catch(() => this.snackbar.open('Impossible de copier — copiez-la manuellement', 'OK', {duration: 3000}));
  }

  goToList(): void {
    this.router.navigate(['/admin/licenses']);
  }
}
