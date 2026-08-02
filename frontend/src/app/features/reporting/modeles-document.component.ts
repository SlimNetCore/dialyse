import {ChangeDetectionStrategy, Component, inject, OnInit, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {MatCardModule} from '@angular/material/card';
import {MatTableModule} from '@angular/material/table';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatSelectModule} from '@angular/material/select';
import {MatCheckboxModule} from '@angular/material/checkbox';
import {MatDialog, MatDialogModule} from '@angular/material/dialog';
import {MatSnackBar, MatSnackBarModule} from '@angular/material/snack-bar';
import {MatTooltipModule} from '@angular/material/tooltip';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {compatForm} from '@angular/forms/signals/compat';
import {FormField, FormRoot, required} from '@angular/forms/signals';
import {BackendApiService} from '../../core/api/backend-api.service';
import {AuthStore} from '../../core/state/auth.store';
import {ConfirmDialogComponent} from '../../shared/confirm-dialog.component';
import {ModelesDocumentStore} from './state/modeles-document.store';

@Component({
  selector: 'app-modeles-document',
  standalone: true,
  imports: [
    CommonModule,
    TranslateModule,
    MatCardModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatCheckboxModule,
    MatDialogModule,
    MatSnackBarModule,
    MatTooltipModule,
    FormRoot,
    FormField,
  ],
  templateUrl: './modeles-document.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './modeles-document.component.css',
})
export class ModelesDocumentComponent implements OnInit {
  private readonly api = inject(BackendApiService);
  private readonly auth = inject(AuthStore);
  private readonly snack = inject(MatSnackBar);
  private readonly dialog = inject(MatDialog);
  private readonly modelesStore = inject(ModelesDocumentStore);
  private readonly translate = inject(TranslateService);

  readonly modeles = this.modelesStore.modeles;
  readonly documentTypes = this.modelesStore.documentTypes;
  readonly showForm = this.modelesStore.showForm;
  readonly editingId = this.modelesStore.editingId;
  readonly displayedColumns = [
    'code',
    'libelle',
    'typeDocument',
    'cheminJrxml',
    'format',
    'active',
    'actions',
  ];

  readonly form = signal(this.emptyForm());
  readonly documentForm = compatForm(this.form, (form) => {
    required(form.code);
    required(form.libelle);
    required(form.typeDocument);
    required(form.cheminJrxml);
  });

  ngOnInit(): void {
    this.loadModeles();
    this.api.getDocumentTypes().subscribe((t) => this.modelesStore.setDocumentTypes(t));
  }

  openForm(): void {
    this.form.set(this.emptyForm());
    this.modelesStore.setEditingId(null);
    this.modelesStore.setShowForm(true);
  }

  val(row: any, upper: string, lower: string): any {
    return row[upper] ?? row[lower] ?? '';
  }

  closeForm(): void {
    this.modelesStore.setShowForm(false);
  }

  save(): void {
    const centerId = this.auth.centerId();
    if (!centerId) return;

    const payload = {...this.form(), centerId, active: true};

    const obs$ = this.editingId()
      ? this.api.updateModeleDocument(this.editingId()!, payload)
      : this.api.createModeleDocument(payload);

    obs$.subscribe({
      next: () => {
        this.snack.open(
          this.editingId()
            ? this.translate.instant('REPORTING.MODELES_DOCUMENT.UPDATED_OK')
            : this.translate.instant('REPORTING.MODELES_DOCUMENT.CREATED_OK'),
          this.translate.instant('COMMON.OK'),
          {
          duration: 3000,
          },
        );
        this.modelesStore.setShowForm(false);
        this.loadModeles();
      },
      error: (err) => this.snack.open(
        this.translate.instant('REPORTING.MODELES_DOCUMENT.GENERIC_ERROR', {detail: err?.message ?? ''}),
        this.translate.instant('COMMON.OK'),
        {duration: 5000},
      ),
    });
  }

  isFormValid(): boolean {
    const form = this.form();
    return (
      !!form.code && !!form.libelle && !!form.typeDocument && !!form.cheminJrxml
    );
  }

  edit(row: any): void {
    this.modelesStore.setEditingId(this.val(row, 'ID', 'id'));
    this.form.set({
      code: this.val(row, 'CODE', 'code'),
      libelle: this.val(row, 'LIBELLE', 'libelle'),
      typeDocument: this.val(row, 'TYPE_DOCUMENT', 'type_document'),
      cheminJrxml: this.val(row, 'CHEMIN_JRXML', 'chemin_jrxml'),
      formatImpression: this.val(row, 'FORMAT_IMPRESSION', 'format_impression'),
      description: this.val(row, 'DESCRIPTION', 'description'),
    });
    this.modelesStore.setShowForm(true);
  }

  remove(row: any): void {
    const centerId = this.auth.centerId();
    const id = this.val(row, 'ID', 'id');
    if (!centerId || !id) return;

    const ref = this.dialog.open(ConfirmDialogComponent, {
      width: 'min(96vw, 440px)',
      data: {
        title: this.translate.instant('REPORTING.MODELES_DOCUMENT.DELETE_CONFIRM_TITLE'),
        message: this.translate.instant('REPORTING.MODELES_DOCUMENT.DELETE_CONFIRM_MESSAGE'),
        confirmLabel: this.translate.instant('COMMON.DELETE'),
        cancelLabel: this.translate.instant('PATIENT_FORM.BTN_CANCEL'),
        color: 'warn',
        icon: 'delete',
      },
    });

    ref.afterClosed().subscribe((confirmed) => {
      if (!confirmed) return;
      this.api.deleteModeleDocument(id, centerId).subscribe({
        next: () => {
          this.snack.open(
            this.translate.instant('REPORTING.MODELES_DOCUMENT.DELETED_OK'),
            this.translate.instant('COMMON.OK'),
            {duration: 3000},
          );
          this.loadModeles();
        },
      });
    });
  }

  testPrint(row: any): void {
    const centerId = this.auth.centerId();
    const typeDoc = this.val(row, 'TYPE_DOCUMENT', 'type_document');
    if (!centerId) return;

    this.api.printDocument(centerId, typeDoc, {}).subscribe({
      next: (blob: Blob) => {
        const url = URL.createObjectURL(blob);
        window.open(url, '_blank');
      },
      error: (err) => this.snack.open(
        this.translate.instant('REPORTING.MODELES_DOCUMENT.PRINT_ERROR', {detail: err?.message ?? ''}),
        this.translate.instant('COMMON.OK'),
        {duration: 5000},
      ),
    });
  }

  private loadModeles(): void {
    const centerId = this.auth.centerId();
    if (!centerId) return;
    this.api.listModelesDocument(centerId).subscribe((m) => this.modelesStore.setModeles(m));
  }

  private emptyForm() {
    return {
      code: '',
      libelle: '',
      typeDocument: '',
      cheminJrxml: '',
      formatImpression: 'PDF',
      description: '',
    };
  }
}
