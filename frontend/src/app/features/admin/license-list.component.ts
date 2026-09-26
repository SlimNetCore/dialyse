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
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {compatForm} from '@angular/forms/signals/compat';
import {FormField, FormRoot, required} from '@angular/forms/signals';
import {License, LicenseApiService} from '../../core/api/license-api.service';
import {ConfirmDialogComponent} from '../../shared/confirm-dialog.component';

type EffectiveStatus = 'ACTIVE' | 'TRIAL' | 'EXPIRED' | 'REVOKED';

/**
 * Licences de l'application (propriétaire / SUPERADMIN) : vue par société puis par centre, révocation, activation
 * d'une clé émise ailleurs. L'attribution se fait dans l'écran dédié (une licence par centre d'une société).
 */
@Component({
  selector: 'app-license-list',
  standalone: true,
  imports: [
    DatePipe,
    RouterLink,
    TranslateModule,
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

  /** Tri stable : société puis centre, pour lire les licences groupées par société. */
  readonly sorted = computed(() =>
    [...this.licenses()].sort((a, b) =>
      (a.societeName ?? '').localeCompare(b.societeName ?? '') || (a.centerName ?? '').localeCompare(b.centerName ?? '')),
  );
  readonly totalCenters = computed(() => new Set(this.licenses().map((l) => l.centerId)).size);
  readonly activeCount = computed(() => this.licenses().filter((l) => this.status(l) === 'ACTIVE').length);
  readonly expiredCount = computed(() =>
    this.licenses().filter((l) => this.status(l) === 'EXPIRED' || this.status(l) === 'REVOKED').length,
  );

  private readonly api = inject(LicenseApiService);
  private readonly dialog = inject(MatDialog);
  private readonly snackbar = inject(MatSnackBar);
  private readonly translate = inject(TranslateService);

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
        this.notify('LICENCES.LOAD_ERROR', 3000);
      },
    });
  }

  status(license: License): EffectiveStatus {
    if (license.status === 'REVOKED') return 'REVOKED';
    if (new Date(license.validUntil).getTime() < Date.now()) return 'EXPIRED';
    return license.type === 'TRIAL' ? 'TRIAL' : 'ACTIVE';
  }

  daysRemaining(license: License): number {
    return Math.ceil((new Date(license.validUntil).getTime() - Date.now()) / 86_400_000);
  }

  copyKey(license: License): void {
    navigator.clipboard
      .writeText(license.licenseKey)
      .then(() => this.notify('LICENCES.KEY_COPIED', 2000))
      .catch(() => this.notify('LICENCES.COPY_FAILED', 3000));
  }

  revoke(license: License): void {
    const ref = this.dialog.open(ConfirmDialogComponent, {
      width: 'min(96vw, 460px)',
      data: {
        title: this.translate.instant('LICENCES.REVOKE_TITLE'),
        message: this.translate.instant('LICENCES.REVOKE_MESSAGE', {name: license.centerName ?? license.centerId}),
        confirmLabel: this.translate.instant('LICENCES.REVOKE_CONFIRM'),
        cancelLabel: this.translate.instant('PATIENT_FORM.BTN_CANCEL'),
        color: 'warn',
        icon: 'block',
      },
    });
    ref.afterClosed().subscribe((confirmed) => {
      if (!confirmed) return;
      this.api.revoke(license.id, this.translate.instant('LICENCES.REVOKE_REASON')).subscribe({
        next: () => {
          this.notify('LICENCES.REVOKED_OK', 2000);
          this.reload();
        },
        error: () => this.notify('LICENCES.REVOKE_ERROR', 3000),
      });
    });
  }

  activate(): void {
    if (this.activating()) return;
    const {centerId, licenseKey} = this.activateModel();
    if (!centerId.trim() || !licenseKey.trim()) {
      this.notify('LICENCES.ACTIVATE_MISSING', 2500);
      return;
    }
    this.activating.set(true);
    this.api.activate({centerId: centerId.trim(), licenseKey: licenseKey.trim()}).subscribe({
      next: () => {
        this.activating.set(false);
        this.activateModel.set({centerId: '', licenseKey: ''});
        this.notify('LICENCES.ACTIVATED_OK', 2000);
        this.reload();
      },
      error: (err) => {
        this.activating.set(false);
        this.snackbar.open(err?.error?.detail || this.translate.instant('LICENCES.ACTIVATE_ERROR'),
          this.translate.instant('COMMON.OK'), {duration: 3500});
      },
    });
  }

  private notify(key: string, duration: number): void {
    this.snackbar.open(this.translate.instant(key), this.translate.instant('COMMON.OK'), {duration});
  }
}
