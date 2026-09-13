import {ChangeDetectionStrategy, Component, inject, OnInit, signal} from '@angular/core';
import {DatePipe} from '@angular/common';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatSnackBar, MatSnackBarModule} from '@angular/material/snack-bar';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {compatForm} from '@angular/forms/signals/compat';
import {FormField, FormRoot, required} from '@angular/forms/signals';
import {LicenseApiService, LicenseStatus} from '../../core/api/license-api.service';

@Component({
  selector: 'app-license-activate',
  standalone: true,
  imports: [
    DatePipe,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSnackBarModule,
    MatProgressSpinnerModule,
    FormRoot,
    FormField,
  ],
  templateUrl: './license-activate.component.html',
  styleUrl: './license-activate.component.css',
  changeDetection: ChangeDetectionStrategy.Eager,
})
export class LicenseActivateComponent implements OnInit {
  readonly loading = signal(true);
  readonly activating = signal(false);
  readonly status = signal<LicenseStatus | null>(null);
  readonly form = signal({licenseKey: ''});
  readonly activateForm = compatForm(this.form, (f) => {
    required(f.licenseKey);
  });
  private readonly api = inject(LicenseApiService);
  private readonly snackbar = inject(MatSnackBar);

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.api.status().subscribe({
      next: (s) => {
        this.status.set(s);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snackbar.open('Impossible de récupérer le statut de la licence', 'OK', {duration: 3000});
      },
    });
  }

  activate(): void {
    if (this.activating()) return;
    const key = this.form().licenseKey.trim();
    if (!key) {
      this.snackbar.open('Collez la clé de licence reçue de votre fournisseur', 'OK', {duration: 2500});
      return;
    }
    this.activating.set(true);
    // centerId is ignored server-side for a non-SUPERADMIN caller: the backend always
    // activates for the caller's own center, regardless of what's sent here.
    this.api.activate({centerId: '', licenseKey: key}).subscribe({
      next: () => {
        this.activating.set(false);
        this.form.set({licenseKey: ''});
        this.snackbar.open('Licence activée', 'OK', {duration: 2000});
        this.reload();
      },
      error: (err) => {
        this.activating.set(false);
        this.snackbar.open(err?.error?.detail || "Échec de l'activation — vérifiez la clé", 'OK', {duration: 3500});
      },
    });
  }
}
