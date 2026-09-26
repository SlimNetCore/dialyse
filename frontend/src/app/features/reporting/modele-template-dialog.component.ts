import {ChangeDetectionStrategy, Component, computed, DestroyRef, inject, signal} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {DatePipe} from '@angular/common';
import {MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef} from '@angular/material/dialog';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatTableModule} from '@angular/material/table';
import {MatPaginatorModule, PageEvent} from '@angular/material/paginator';
import {MatSnackBar} from '@angular/material/snack-bar';
import {MatTooltipModule} from '@angular/material/tooltip';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {compatForm} from '@angular/forms/signals/compat';
import {FormField, FormRoot, maxLength} from '@angular/forms/signals';
import {BackendApiService, ModeleVersion, ModeleViolation} from '../../core/api/backend-api.service';
import {ConfirmDialogComponent} from '../../shared/confirm-dialog.component';
import {extractViolations, precheckTemplateFile, templateFileName} from './modele-template.util';

export interface ModeleTemplateDialogData {
  modeleId: string;
  centerId: string;
  code: string;
  libelle: string;
}

/**
 * Personnalisation d'un modèle d'impression : téléchargement du modèle courant, téléversement d'une version
 * modifiée (validée par le serveur), historique paginé, retour arrière et retour au modèle d'origine.
 */
@Component({
  selector: 'app-modele-template-dialog',
  standalone: true,
  imports: [
    DatePipe,
    TranslateModule,
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatTableModule,
    MatPaginatorModule,
    MatTooltipModule,
    MatProgressBarModule,
    FormRoot,
    FormField,
  ],
  templateUrl: './modele-template-dialog.component.html',
  styleUrl: './modele-template-dialog.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ModeleTemplateDialogComponent {
  protected readonly data = inject<ModeleTemplateDialogData>(MAT_DIALOG_DATA);
  protected readonly displayedColumns = ['version', 'uploadedAt', 'uploadedBy', 'commentaire', 'taille', 'actions'];
  protected readonly pageSizeOptions = [10, 20, 50, 100];
  protected readonly versions = signal<ModeleVersion[]>([]);
  protected readonly total = signal(0);
  protected readonly pageIndex = signal(0);
  protected readonly pageSize = signal(10);
  protected readonly loading = signal(false);
  protected readonly uploading = signal(false);
  protected readonly selectedFile = signal<File | null>(null);
  protected readonly violations = signal<ModeleViolation[]>([]);
  /** Numéro de la version personnalisée en vigueur ; `null` = le modèle d'origine est utilisé. */
  protected readonly activeVersion = signal<number | null>(null);
  protected readonly hasCustomActive = computed(() => this.activeVersion() !== null);
  protected readonly canUpload = computed(() => this.selectedFile() !== null && !this.uploading());
  private readonly dialogRef = inject(MatDialogRef<ModeleTemplateDialogComponent>);
  private readonly dialog = inject(MatDialog);
  private readonly api = inject(BackendApiService);
  private readonly snack = inject(MatSnackBar);
  private readonly translate = inject(TranslateService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly commentState = signal({commentaire: ''});
  protected readonly commentForm = compatForm(this.commentState, (form) => {
    maxLength(form.commentaire, 500);
  });

  constructor() {
    this.loadVersions();
  }

  protected loadVersions(): void {
    this.loading.set(true);
    this.api.listModeleVersions(this.data.modeleId, this.data.centerId, this.pageIndex(), this.pageSize())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => {
          this.versions.set(page.items);
          this.total.set(page.total);
          this.activeVersion.set(page.activeVersion);
          this.loading.set(false);
        },
        error: () => {
          this.loading.set(false);
          this.notify('REPORTING.CUSTOMIZE.LOAD_ERROR');
        },
      });
  }

  protected onPage(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.loadVersions();
  }

  protected onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.item(0) ?? null;
    this.violations.set([]);
    if (!file) {
      this.selectedFile.set(null);
      return;
    }
    const problem = precheckTemplateFile(file);
    if (problem) {
      this.selectedFile.set(null);
      this.violations.set([problem]);
      input.value = '';
      return;
    }
    this.selectedFile.set(file);
  }

  protected downloadCurrent(): void {
    this.download(this.api.downloadModeleSource(this.data.modeleId, this.data.centerId), templateFileName(this.data.code));
  }

  protected downloadOriginal(): void {
    this.download(this.api.downloadModeleSource(this.data.modeleId, this.data.centerId, {origine: true}),
      templateFileName(this.data.code + '-origine'));
  }

  protected downloadVersion(version: ModeleVersion): void {
    this.download(this.api.downloadModeleSource(this.data.modeleId, this.data.centerId, {version: version.version}),
      templateFileName(this.data.code, version.version));
  }

  protected upload(): void {
    const file = this.selectedFile();
    if (!file) return;
    this.uploading.set(true);
    this.violations.set([]);
    this.api.uploadModeleVersion(this.data.modeleId, this.data.centerId, file, this.commentState().commentaire)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (created) => {
          this.uploading.set(false);
          this.selectedFile.set(null);
          this.commentState.set({commentaire: ''});
          this.pageIndex.set(0);
          this.loadVersions();
          this.notify('REPORTING.CUSTOMIZE.UPLOAD_OK', {version: created.version});
        },
        error: (err) => {
          this.uploading.set(false);
          const found = extractViolations(err);
          if (found) {
            this.violations.set(found);
          } else {
            this.notify('REPORTING.CUSTOMIZE.UPLOAD_ERROR');
          }
        },
      });
  }

  protected activate(version: ModeleVersion): void {
    this.api.activateModeleVersion(this.data.modeleId, this.data.centerId, version.version)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.loadVersions();
          this.notify('REPORTING.CUSTOMIZE.ACTIVATED_OK', {version: version.version});
        },
        error: () => this.notify('REPORTING.CUSTOMIZE.ACTION_ERROR'),
      });
  }

  protected resetToOriginal(): void {
    const ref = this.dialog.open(ConfirmDialogComponent, {
      width: 'min(96vw, 460px)',
      data: {
        title: this.translate.instant('REPORTING.CUSTOMIZE.RESET_CONFIRM_TITLE'),
        message: this.translate.instant('REPORTING.CUSTOMIZE.RESET_CONFIRM_MESSAGE'),
        confirmLabel: this.translate.instant('REPORTING.CUSTOMIZE.RESET_BTN'),
        cancelLabel: this.translate.instant('PATIENT_FORM.BTN_CANCEL'),
        color: 'primary',
        icon: 'restore',
      },
    });
    ref.afterClosed().pipe(takeUntilDestroyed(this.destroyRef)).subscribe((confirmed) => {
      if (!confirmed) return;
      this.api.resetModele(this.data.modeleId, this.data.centerId).subscribe({
        next: () => {
          this.loadVersions();
          this.notify('REPORTING.CUSTOMIZE.RESET_OK');
        },
        error: () => this.notify('REPORTING.CUSTOMIZE.ACTION_ERROR'),
      });
    });
  }

  protected close(): void {
    this.dialogRef.close();
  }

  /** Clé de traduction d'une anomalie ; les codes inconnus retombent sur un message générique. */
  protected violationKey(code: string): string {
    return `REPORTING.CUSTOMIZE.VIOLATIONS.${code}`;
  }

  protected formatSize(bytes: number): string {
    return bytes < 1024 ? `${bytes} o` : `${(bytes / 1024).toFixed(1)} Ko`;
  }

  private download(source$: ReturnType<BackendApiService['downloadModeleSource']>, filename: string): void {
    source$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = filename;
        anchor.click();
        URL.revokeObjectURL(url);
      },
      error: () => this.notify('REPORTING.CUSTOMIZE.DOWNLOAD_ERROR'),
    });
  }

  private notify(key: string, params?: Record<string, unknown>): void {
    this.snack.open(this.translate.instant(key, params), this.translate.instant('COMMON.OK'), {duration: 4000});
  }
}
