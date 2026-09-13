import {ChangeDetectionStrategy, Component, computed, inject, OnInit, signal} from '@angular/core';
import {DatePipe} from '@angular/common';
import {RouterLink} from '@angular/router';
import {MatCardModule} from '@angular/material/card';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatTooltipModule} from '@angular/material/tooltip';
import {MatDialog} from '@angular/material/dialog';
import {MatSnackBar, MatSnackBarModule} from '@angular/material/snack-bar';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {compatForm} from '@angular/forms/signals/compat';
import {FormField, FormRoot, required} from '@angular/forms/signals';
import {License, LicenseApiService} from '../../core/api/license-api.service';
import {ConfirmDialogComponent} from '../../shared/confirm-dialog.component';

type EffectiveStatus = 'ACTIVE' | 'TRIAL' | 'EXPIRED' | 'REVOKED';

@Component({
  selector: 'app-license-list',
  standalone: true,
  imports: [
    DatePipe,
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    MatSnackBarModule,
    MatFormFieldModule,
    MatInputModule,
    FormRoot,
    FormField,
  ],
  templateUrl: './license-list.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './license-list.component.css',
})
export class LicenseListComponent implements OnInit {
  readonly licenses = signal<License[]>([]);
  readonly loading = signal(false);
  readonly activating = signal(false);
  readonly activateModel = signal({centerId: '', licenseKey: ''});
  readonly activateForm = compatForm(this.activateModel, (form) => {
    required(form.centerId);
    required(form.licenseKey);
  });
  readonly totalCenters = computed(() => new Set(this.licenses().map((l) => l.centerId)).size);
  readonly activeCount = computed(() => this.licenses().filter((l) => this.status(l) === 'ACTIVE').length);
  readonly expiredCount = computed(() =>
    this.licenses().filter((l) => this.status(l) === 'EXPIRED' || this.status(l) === 'REVOKED').length,
  );
  private readonly api = inject(LicenseApiService);
  private readonly dialog = inject(MatDialog);
  private readonly snackbar = inject(MatSnackBar);

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.api.listAll().subscribe({
      next: (rows) => {
        this.licenses.set(rows);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snackbar.open('Impossible de charger les licences', 'OK', {duration: 3000});
      },
    });
  }

  status(license: License): EffectiveStatus {
    if (license.status === 'REVOKED') return 'REVOKED';
    const now = Date.now();
    if (new Date(license.validUntil).getTime() < now) return 'EXPIRED';
    return license.type === 'TRIAL' ? 'TRIAL' : 'ACTIVE';
  }

  statusLabel(status: EffectiveStatus): string {
    switch (status) {
      case 'ACTIVE':
        return 'Active';
      case 'TRIAL':
        return "Période d'essai";
      case 'EXPIRED':
        return 'Expirée';
      case 'REVOKED':
        return 'Révoquée';
    }
  }

  daysRemaining(license: License): number {
    return Math.ceil((new Date(license.validUntil).getTime() - Date.now()) / 86_400_000);
  }

  copyKey(license: License): void {
    navigator.clipboard
      .writeText(license.licenseKey)
      .then(() => this.snackbar.open('Clé de licence copiée', 'OK', {duration: 2000}))
      .catch(() => this.snackbar.open('Impossible de copier — copiez-la manuellement', 'OK', {duration: 3000}));
  }

  revoke(license: License): void {
    const ref = this.dialog.open(ConfirmDialogComponent, {
      width: 'min(96vw, 460px)',
      data: {
        title: 'Révoquer la licence',
        message: `Révoquer la licence de « ${license.centerName ?? license.centerId} » ? Le centre sera bloqué au prochain contrôle.`,
        confirmLabel: 'Révoquer',
        cancelLabel: 'Annuler',
        color: 'warn',
        icon: 'block',
      },
    });
    ref.afterClosed().subscribe((confirmed) => {
      if (!confirmed) return;
      this.api.revoke(license.id, 'Révoquée manuellement par le propriétaire').subscribe({
        next: () => {
          this.snackbar.open('Licence révoquée', 'OK', {duration: 2000});
          this.reload();
        },
        error: () => this.snackbar.open('Échec de la révocation', 'OK', {duration: 3000}),
      });
    });
  }

  activate(): void {
    if (this.activating()) return;
    const {centerId, licenseKey} = this.activateModel();
    if (!centerId.trim() || !licenseKey.trim()) {
      this.snackbar.open('Renseignez le centre et la clé de licence', 'OK', {duration: 2500});
      return;
    }
    this.activating.set(true);
    this.api.activate({centerId: centerId.trim(), licenseKey: licenseKey.trim()}).subscribe({
      next: () => {
        this.activating.set(false);
        this.activateModel.set({centerId: '', licenseKey: ''});
        this.snackbar.open('Licence activée', 'OK', {duration: 2000});
        this.reload();
      },
      error: (err) => {
        this.activating.set(false);
        this.snackbar.open(err?.error?.detail || 'Échec de l\'activation', 'OK', {duration: 3500});
      },
    });
  }
}
