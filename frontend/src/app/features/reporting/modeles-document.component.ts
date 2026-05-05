import {Component, inject, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
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
import {TranslateModule} from '@ngx-translate/core';
import {BackendApiService} from '../../core/api/backend-api.service';
import {AuthStore} from '../../core/state/auth.store';
import {ConfirmDialogComponent} from '../../shared/confirm-dialog.component';
import {ModelesDocumentStore} from './state/modeles-document.store';

@Component({
  selector: 'app-modeles-document',
  standalone: true,
  imports: [
    CommonModule, FormsModule, TranslateModule,
    MatCardModule, MatTableModule, MatButtonModule, MatIconModule,
    MatFormFieldModule, MatInputModule, MatSelectModule, MatCheckboxModule,
    MatDialogModule, MatSnackBarModule, MatTooltipModule
  ],
  template: `
    <div class="page-container">
      <mat-card class="header-card">
        <div class="header-row">
          <div>
            <h2>
              <mat-icon class="title-icon">description</mat-icon>
              Gestion des modèles de documents
            </h2>
            <p class="subtitle">Associez un fichier .jrxml JasperReports à chaque type de document par centre</p>
          </div>
          <button mat-flat-button color="primary" (click)="openForm()">
            <mat-icon>add</mat-icon> Nouveau modèle
          </button>
        </div>
      </mat-card>

      <!-- Formulaire d'ajout/édition -->
      @if (showForm()) {
        <mat-card class="form-card">
          <h3>{{ editingId() ? 'Modifier le modèle' : 'Nouveau modèle de document' }}</h3>
          <div class="form-grid">
            <mat-form-field appearance="outline">
              <mat-label>Code</mat-label>
              <input matInput [(ngModel)]="form.code" placeholder="Ex: FICHE_PATIENT">
              <mat-icon matPrefix>code</mat-icon>
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Libellé</mat-label>
              <input matInput [(ngModel)]="form.libelle" placeholder="Ex: Fiche signalétique patient">
              <mat-icon matPrefix>label</mat-icon>
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Type de document</mat-label>
              <mat-select [(ngModel)]="form.typeDocument">
                @for (t of documentTypes(); track t.code) {
                  <mat-option [value]="t.code">{{ t.label }}</mat-option>
                }
              </mat-select>
              <mat-icon matPrefix>category</mat-icon>
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Chemin du fichier .jrxml</mat-label>
              <input matInput [(ngModel)]="form.cheminJrxml" placeholder="Ex: reports/fiche_patient.jrxml">
              <mat-icon matPrefix>folder_open</mat-icon>
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Format d'impression</mat-label>
              <mat-select [(ngModel)]="form.formatImpression">
                <mat-option value="PDF">PDF</mat-option>
                <mat-option value="EXCEL">Excel (XLS)</mat-option>
                <mat-option value="HTML">HTML</mat-option>
              </mat-select>
              <mat-icon matPrefix>print</mat-icon>
            </mat-form-field>

            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Description</mat-label>
              <textarea matInput [(ngModel)]="form.description" rows="2"></textarea>
              <mat-icon matPrefix>info</mat-icon>
            </mat-form-field>
          </div>

          <div class="form-actions">
            <button mat-stroked-button (click)="closeForm()">Annuler</button>
            <button mat-flat-button color="primary" (click)="save()" [disabled]="!isFormValid()">
              <mat-icon>save</mat-icon>
              {{ editingId() ? 'Modifier' : 'Enregistrer' }}
            </button>
          </div>
        </mat-card>
      }

      <!-- Tableau des modèles -->
      <mat-card class="table-card">
        <table mat-table [dataSource]="modeles()" class="modeles-table">
          <ng-container matColumnDef="code">
            <th mat-header-cell *matHeaderCellDef>Code</th>
            <td mat-cell *matCellDef="let m">{{ val(m, 'CODE', 'code') }}</td>
          </ng-container>

          <ng-container matColumnDef="libelle">
            <th mat-header-cell *matHeaderCellDef>Libellé</th>
            <td mat-cell *matCellDef="let m">{{ val(m, 'LIBELLE', 'libelle') }}</td>
          </ng-container>

          <ng-container matColumnDef="typeDocument">
            <th mat-header-cell *matHeaderCellDef>Type</th>
            <td mat-cell *matCellDef="let m">
              <span class="type-chip">{{ val(m, 'TYPE_DOCUMENT', 'type_document') }}</span>
            </td>
          </ng-container>

          <ng-container matColumnDef="cheminJrxml">
            <th mat-header-cell *matHeaderCellDef>Fichier .jrxml</th>
            <td mat-cell *matCellDef="let m">
              <code class="jrxml-path">{{ val(m, 'CHEMIN_JRXML', 'chemin_jrxml') }}</code>
            </td>
          </ng-container>

          <ng-container matColumnDef="format">
            <th mat-header-cell *matHeaderCellDef>Format</th>
            <td mat-cell *matCellDef="let m">
              <span class="format-badge" [class]="val(m, 'FORMAT_IMPRESSION', 'format_impression')?.toLowerCase()">
                {{ val(m, 'FORMAT_IMPRESSION', 'format_impression') }}
              </span>
            </td>
          </ng-container>

          <ng-container matColumnDef="active">
            <th mat-header-cell *matHeaderCellDef>Actif</th>
            <td mat-cell *matCellDef="let m">
              <mat-icon [style.color]="val(m, 'ACTIVE', 'active') ? '#1b5e20' : '#bdbdbd'">
                {{ val(m, 'ACTIVE', 'active') ? 'check_circle' : 'cancel' }}
              </mat-icon>
            </td>
          </ng-container>

          <ng-container matColumnDef="actions">
            <th mat-header-cell *matHeaderCellDef>Actions</th>
            <td mat-cell *matCellDef="let m">
              <button mat-icon-button matTooltip="Modifier" (click)="edit(m)">
                <mat-icon>edit</mat-icon>
              </button>
              <button mat-icon-button matTooltip="Tester l'impression" (click)="testPrint(m)" color="primary">
                <mat-icon>print</mat-icon>
              </button>
              <button mat-icon-button matTooltip="Supprimer" (click)="remove(m)" color="warn">
                <mat-icon>delete</mat-icon>
              </button>
            </td>
          </ng-container>

          <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
          <tr mat-row *matRowDef="let row; columns: displayedColumns" [attr.data-row-id]="val(row, 'ID', 'id')"></tr>
        </table>

        @if (modeles().length === 0) {
          <div class="empty-state">
            <mat-icon>description</mat-icon>
            <p>Aucun modèle de document configuré pour ce centre</p>
          </div>
        }
      </mat-card>
    </div>
  `,
  styles: [`
    .page-container { max-width: 1100px; margin: 0 auto; }
    .header-card { margin-bottom: 16px; padding: 20px 24px; }
    .header-row { display: flex; justify-content: space-between; align-items: center; }
    h2 { margin: 0; display: flex; align-items: center; gap: 8px; color: #1b5e20; }
    .title-icon { font-size: 28px; width: 28px; height: 28px; }
    .subtitle { margin: 4px 0 0; color: #757575; font-size: 13px; }

    .form-card { margin-bottom: 16px; padding: 20px 24px; }
    h3 { color: #1b5e20; margin-bottom: 16px; }
    .form-grid {
      display: grid; grid-template-columns: 1fr 1fr; gap: 0 16px;
    }
    .full-width { grid-column: 1 / -1; }
    .form-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 8px; }

    .table-card { padding: 0; overflow: hidden; }
    .modeles-table { width: 100%; }
    .type-chip {
      background: #e8f5e9; color: #1b5e20; padding: 2px 10px;
      border-radius: 12px; font-size: 12px; font-weight: 500;
    }
    .jrxml-path {
      background: #f5f5f5; padding: 2px 8px; border-radius: 4px;
      font-size: 12px; color: #616161;
    }
    .format-badge {
      padding: 2px 10px; border-radius: 12px; font-size: 11px;
      font-weight: 600; text-transform: uppercase;
    }
    .format-badge.pdf { background: #ffebee; color: #c62828; }
    .format-badge.excel { background: #e8f5e9; color: #2e7d32; }
    .format-badge.html { background: #e3f2fd; color: #1565c0; }

    .empty-state {
      padding: 40px; text-align: center; color: #9e9e9e;
    }
    .empty-state mat-icon { font-size: 48px; width: 48px; height: 48px; margin-bottom: 8px; }
  `]
})
export class ModelesDocumentComponent implements OnInit {
  private readonly api = inject(BackendApiService);
  private readonly auth = inject(AuthStore);
  private readonly snack = inject(MatSnackBar);
  private readonly dialog = inject(MatDialog);
  private readonly modelesStore = inject(ModelesDocumentStore);

  readonly modeles = this.modelesStore.modeles;
  readonly documentTypes = this.modelesStore.documentTypes;
  readonly showForm = this.modelesStore.showForm;
  readonly editingId = this.modelesStore.editingId;
  readonly displayedColumns = ['code', 'libelle', 'typeDocument', 'cheminJrxml', 'format', 'active', 'actions'];

  form = this.emptyForm();

  ngOnInit(): void {
    this.loadModeles();
    this.api.getDocumentTypes().subscribe(t => this.modelesStore.setDocumentTypes(t));
  }

  openForm(): void {
    this.form = this.emptyForm();
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

    const payload = { ...this.form, centerId, active: true };

    const obs$ = this.editingId()
      ? this.api.updateModeleDocument(this.editingId()!, payload)
      : this.api.createModeleDocument(payload);

    obs$.subscribe({
      next: () => {
        this.snack.open(this.editingId() ? 'Modèle mis à jour' : 'Modèle créé', 'OK', { duration: 3000 });
        this.modelesStore.setShowForm(false);
        this.loadModeles();
      },
      error: err => this.snack.open('Erreur: ' + err.message, 'OK', { duration: 5000 })
    });
  }

  isFormValid(): boolean {
    return !!this.form.code && !!this.form.libelle && !!this.form.typeDocument && !!this.form.cheminJrxml;
  }

  edit(row: any): void {
    this.modelesStore.setEditingId(this.val(row, 'ID', 'id'));
    this.form = {
      code: this.val(row, 'CODE', 'code'),
      libelle: this.val(row, 'LIBELLE', 'libelle'),
      typeDocument: this.val(row, 'TYPE_DOCUMENT', 'type_document'),
      cheminJrxml: this.val(row, 'CHEMIN_JRXML', 'chemin_jrxml'),
      formatImpression: this.val(row, 'FORMAT_IMPRESSION', 'format_impression'),
      description: this.val(row, 'DESCRIPTION', 'description')
    };
    this.modelesStore.setShowForm(true);
  }

  private loadModeles(): void {
    const centerId = this.auth.centerId();
    if (!centerId) return;
    this.api.listModelesDocument(centerId).subscribe(m => this.modelesStore.setModeles(m));
  }

  remove(row: any): void {
    const centerId = this.auth.centerId();
    const id = this.val(row, 'ID', 'id');
    if (!centerId || !id) return;

    const ref = this.dialog.open(ConfirmDialogComponent, {
      width: '440px',
      data: {
        title: 'Supprimer le modèle',
        message: 'Êtes-vous sûr de vouloir supprimer ce modèle de document ? Cette action est irréversible.',
        confirmLabel: 'Supprimer',
        cancelLabel: 'Annuler',
        color: 'warn',
        icon: 'delete'
      }
    });

    ref.afterClosed().subscribe(confirmed => {
      if (!confirmed) return;
      this.api.deleteModeleDocument(id, centerId).subscribe({
        next: () => {
          this.snack.open('Modèle supprimé', 'OK', { duration: 3000 });
          this.loadModeles();
        }
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
      error: err => this.snack.open('Erreur impression: ' + err.message, 'OK', { duration: 5000 })
    });
  }

  private emptyForm() {
    return {
      code: '',
      libelle: '',
      typeDocument: '',
      cheminJrxml: '',
      formatImpression: 'PDF',
      description: ''
    };
  }
}

