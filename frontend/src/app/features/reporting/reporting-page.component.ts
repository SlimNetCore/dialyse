import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { BackendApiService } from '../../core/api/backend-api.service';
import { AppShellStore } from '../../core/state/app-shell.store';

@Component({
  selector: 'app-reporting-page',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    MatCardModule, MatButtonModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatIconModule, MatListModule, MatSnackBarModule
  ],
  template: `
    <div class="reporting-page">
      <mat-card class="list-card">
        <div class="header-row">
          <h3>Reporting et impressions</h3>
          <button mat-flat-button color="primary" (click)="newTemplate()">
            <mat-icon>add</mat-icon> Nouveau modele
          </button>
        </div>

        <div class="filters">
          <mat-form-field appearance="outline">
            <mat-label>Type d'etat</mat-label>
            <mat-select [(ngModel)]="selectedType" (selectionChange)="loadTemplates()">
              <mat-option value="">Tous</mat-option>
              @for (m of models(); track m.code) {
                <mat-option [value]="m.code">{{ m.label }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
        </div>

        <mat-list>
          @for (t of templates(); track t.id) {
            <mat-list-item (click)="selectTemplate(t)" [class.active]="selected()?.id === t.id">
              <div matListItemTitle>{{ t.name }}</div>
              <div matListItemLine>{{ t.code }} - {{ t.report_type }}</div>
            </mat-list-item>
          }
        </mat-list>
      </mat-card>

      <mat-card class="editor-card">
        @if (selected()) {
          <div class="header-row">
            <h3>Configuration du modele</h3>
            <div>
              <button mat-button color="warn" (click)="deleteSelected()"><mat-icon>delete</mat-icon> Supprimer</button>
              <button mat-flat-button color="primary" (click)="save()"><mat-icon>save</mat-icon> Enregistrer</button>
            </div>
          </div>

          <div class="grid">
            <mat-form-field appearance="outline"><mat-label>Code</mat-label><input matInput [(ngModel)]="draft.code" /></mat-form-field>
            <mat-form-field appearance="outline"><mat-label>Nom</mat-label><input matInput [(ngModel)]="draft.name" /></mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Type</mat-label>
              <mat-select [(ngModel)]="draft.report_type">
                @for (m of models(); track m.code) {
                  <mat-option [value]="m.code">{{ m.label }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
            <mat-form-field appearance="outline"><mat-label>Format</mat-label><mat-select [(ngModel)]="draft.page_format"><mat-option value="A4">A4</mat-option><mat-option value="A5">A5</mat-option></mat-select></mat-form-field>
            <mat-form-field appearance="outline"><mat-label>Orientation</mat-label><mat-select [(ngModel)]="draft.orientation"><mat-option value="PORTRAIT">Portrait</mat-option><mat-option value="LANDSCAPE">Paysage</mat-option></mat-select></mat-form-field>
            <mat-form-field appearance="outline"><mat-label>Disposition</mat-label><mat-select [(ngModel)]="draft.layout_mode"><mat-option value="STANDARD">Standard</mat-option><mat-option value="COMPACT">Compact</mat-option><mat-option value="CUSTOM">Custom</mat-option></mat-select></mat-form-field>
          </div>

          <mat-form-field appearance="outline" class="full"><mat-label>Schema champs (JSON)</mat-label><textarea matInput rows="4" [(ngModel)]="draft.field_schema"></textarea></mat-form-field>

          <mat-form-field appearance="outline" class="full">
            <mat-label>Source SQL (jointures autorisees, placeholders :centerId et :patientId)</mat-label>
            <textarea matInput rows="6" [(ngModel)]="draft.data_source_sql"></textarea>
          </mat-form-field>

          <div class="images-row">
            <mat-form-field appearance="outline" class="flex">
              <mat-label>Image entete (URL ou Base64)</mat-label>
              <input matInput [(ngModel)]="draft.header_image" />
            </mat-form-field>
            <button mat-stroked-button (click)="headerInput.click()"><mat-icon>upload</mat-icon> Upload entete</button>
            <input #headerInput type="file" accept="image/*" hidden (change)="onImageUpload($event, 'header_image')" />
          </div>

          <div class="images-row">
            <mat-form-field appearance="outline" class="flex">
              <mat-label>Image footer (URL ou Base64)</mat-label>
              <input matInput [(ngModel)]="draft.footer_image" />
            </mat-form-field>
            <button mat-stroked-button (click)="footerInput.click()"><mat-icon>upload</mat-icon> Upload footer</button>
            <input #footerInput type="file" accept="image/*" hidden (change)="onImageUpload($event, 'footer_image')" />
          </div>

          <div class="images-preview">
            @if (draft.header_image) { <img [src]="draft.header_image" alt="header" class="preview-img" /> }
            @if (draft.footer_image) { <img [src]="draft.footer_image" alt="footer" class="preview-img" /> }
          </div>

          <mat-form-field appearance="outline" class="full"><mat-label>Template HTML</mat-label><textarea matInput rows="14" [(ngModel)]="draft.template_html"></textarea></mat-form-field>

          <div class="tips">
            <strong>Tokens disponibles:</strong>
            <code>{{ '{{patient.nom}}' }}</code>,
            <code>{{ '{{patient.prenom}}' }}</code>,
            <code>{{ '{{center.name}}' }}</code>,
            <code>{{ '{{attestation.dateDebut}}' }}</code>,
            <code>{{ '{{pec.statut}}' }}</code>,
            et toutes les colonnes SQL via <code>{{ '{{data.alias_colonne}}' }}</code>.
          </div>

          <div class="preview-row">
            <mat-form-field appearance="outline">
              <mat-label>Patient ID pour apercu</mat-label>
              <input matInput [(ngModel)]="previewPatientId" />
            </mat-form-field>
            <button mat-stroked-button (click)="preview()"><mat-icon>preview</mat-icon> Apercu HTML</button>
            <button mat-flat-button color="primary" (click)="exportPdf()"><mat-icon>picture_as_pdf</mat-icon> Export PDF</button>
          </div>
        }
      </mat-card>
    </div>
  `,
  styles: [`
    .reporting-page { display: grid; grid-template-columns: 350px 1fr; gap: 16px; }
    .header-row { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; }
    .filters { margin-bottom: 8px; }
    .editor-card, .list-card { min-height: 75vh; }
    .grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; }
    .full { width: 100%; }
    .preview-row { display: flex; align-items: center; gap: 10px; }
    .images-row { display: flex; align-items: center; gap: 10px; }
    .images-row .flex { flex: 1; }
    .images-preview { display: flex; gap: 10px; margin-bottom: 10px; }
    .preview-img { max-height: 70px; max-width: 300px; border: 1px solid #e5e7eb; border-radius: 6px; padding: 3px; }
    .tips { margin: 8px 0 12px; padding: 8px 10px; background: #f8fafc; border-left: 3px solid #1b5e20; font-size: 12px; }
    :host ::ng-deep .mat-mdc-list-item.active { background: #e8f5e9; border-left: 3px solid #1b5e20; }
  `]
})
export class ReportingPageComponent implements OnInit {
  private readonly api = inject(BackendApiService);
  private readonly store = inject(AppShellStore);
  private readonly snackbar = inject(MatSnackBar);

  readonly templates = signal<any[]>([]);
  readonly models = signal<Array<{ code: string; label: string }>>([]);
  readonly selected = signal<any | null>(null);

  selectedType = '';
  previewPatientId = '';
  draft: any = {};

  ngOnInit(): void {
    this.api.getReportingModels().subscribe(v => this.models.set(v));
    this.loadTemplates();
  }

  loadTemplates(): void {
    const centerId = this.store.currentCenterId();
    if (!centerId) return;
    this.api.listReportTemplates(centerId, this.selectedType || undefined).subscribe(v => this.templates.set(v));
  }

  selectTemplate(t: any): void {
    this.selected.set(t);
    this.draft = { ...t };
  }

  newTemplate(): void {
    const centerId = this.store.currentCenterId();
    this.selected.set({ id: null, center_id: centerId, active: true, report_type: 'ATTESTATION', page_format: 'A4', orientation: 'PORTRAIT', layout_mode: 'STANDARD' });
    this.draft = {
      ...this.selected(),
      code: '',
      name: '',
      field_schema: '{"sections":["header","content","footer"]}',
      data_source_sql: 'SELECT p.nom AS patient_nom, p.prenom AS patient_prenom FROM patient p WHERE p.center_id = :centerId AND p.id = :patientId',
      header_image: '',
      footer_image: '',
      template_html: '<h2>Etat</h2><p>{{patient.nom}} {{patient.prenom}}</p><p>{{data.patient_nom}}</p>'
    };
  }

  save(): void {
    const centerId = this.store.currentCenterId();
    if (!centerId) return;
    const payload = {
      ...this.draft,
      centerId,
      reportType: this.draft.report_type,
      pageFormat: this.draft.page_format,
      layoutMode: this.draft.layout_mode,
      fieldSchema: this.draft.field_schema,
      templateHtml: this.draft.template_html,
      dataSourceSql: this.draft.data_source_sql,
      headerImage: this.draft.header_image,
      footerImage: this.draft.footer_image,
      active: this.draft.active !== false
    };

    const call = this.draft.id
      ? this.api.updateReportTemplate(this.draft.id, payload)
      : this.api.createReportTemplate(payload);

    call.subscribe({
      next: () => {
        this.snackbar.open('Modele enregistre', 'OK', { duration: 2000 });
        this.loadTemplates();
      },
      error: () => this.snackbar.open('Erreur enregistrement', 'OK', { duration: 2500 })
    });
  }

  deleteSelected(): void {
    const centerId = this.store.currentCenterId();
    if (!centerId || !this.draft.id) return;
    this.api.deleteReportTemplate(this.draft.id, centerId).subscribe(() => {
      this.snackbar.open('Modele supprime', 'OK', { duration: 2000 });
      this.selected.set(null);
      this.loadTemplates();
    });
  }

  preview(): void {
    const centerId = this.store.currentCenterId();
    if (!centerId || !this.previewPatientId || !this.draft.id) return;
    this.api.renderReportByTemplate(this.draft.id, centerId, this.previewPatientId).subscribe(html => {
      const w = window.open('', '_blank');
      if (!w) return;
      w.document.write(html);
      w.document.close();
      w.focus();
      w.print();
    });
  }

  onImageUpload(event: Event, key: 'header_image' | 'footer_image'): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      this.draft[key] = reader.result as string;
    };
    reader.readAsDataURL(file);
  }

  exportPdf(): void {
    const centerId = this.store.currentCenterId();
    if (!centerId || !this.previewPatientId || !this.draft.id) {
      this.snackbar.open('Renseignez un Patient ID et sauvegardez le modèle', 'OK', { duration: 2500 });
      return;
    }
    const url = `http://localhost:8080/api/v1/reporting/export/pdf/${this.draft.id}?centerId=${centerId}&patientId=${this.previewPatientId}`;
    window.open(url, '_blank');
  }
}


