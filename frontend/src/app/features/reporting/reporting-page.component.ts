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
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { BackendApiService } from '../../core/api/backend-api.service';
import { AppShellStore } from '../../core/state/app-shell.store';
import { ReportDesignerComponent } from './report-designer.component';

@Component({
  selector: 'app-reporting-page',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    MatCardModule, MatButtonModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatIconModule, MatListModule, MatSnackBarModule,
    MatChipsModule, MatProgressSpinnerModule, MatTooltipModule,
    ReportDesignerComponent
  ],
  template: `
    <div class="reporting-page">
      <!-- LEFT: Template list -->
      <mat-card class="list-card">
        <div class="header-row">
          <h3><mat-icon>description</mat-icon> Modèles</h3>
          <button mat-flat-button color="primary" (click)="newTemplate()">
            <mat-icon>add</mat-icon> Nouveau
          </button>
        </div>

        <div class="filters">
          <mat-form-field appearance="outline" class="full">
            <mat-label>Filtrer par type</mat-label>
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
              <mat-icon matListItemIcon>insert_drive_file</mat-icon>
              <div matListItemTitle>{{ t.name }}</div>
              <div matListItemLine>{{ t.code }} — {{ t.report_type }}</div>
            </mat-list-item>
          }
          @if (templates().length === 0) {
            <p class="empty-hint">Aucun modèle trouvé</p>
          }
        </mat-list>
      </mat-card>

      <!-- RIGHT: Editor -->
      <mat-card class="editor-card">
        @if (!selected()) {
          <div class="empty-editor">
            <mat-icon>edit_note</mat-icon>
            <p>Sélectionnez un modèle ou créez-en un nouveau</p>
          </div>
        } @else {
          <div class="header-row">
            <h3><mat-icon>settings</mat-icon> Configuration</h3>
            <div class="actions">
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
            <mat-form-field appearance="outline"><mat-label>Format</mat-label><mat-select [(ngModel)]="draft.page_format"><mat-option value="A4">A4</mat-option><mat-option value="A5">A5</mat-option><mat-option value="LETTER">Letter</mat-option></mat-select></mat-form-field>
            <mat-form-field appearance="outline"><mat-label>Orientation</mat-label><mat-select [(ngModel)]="draft.orientation"><mat-option value="PORTRAIT">Portrait</mat-option><mat-option value="LANDSCAPE">Paysage</mat-option></mat-select></mat-form-field>
            <mat-form-field appearance="outline"><mat-label>Disposition</mat-label><mat-select [(ngModel)]="draft.layout_mode"><mat-option value="STANDARD">Standard</mat-option><mat-option value="COMPACT">Compact</mat-option><mat-option value="CUSTOM">Custom</mat-option></mat-select></mat-form-field>
          </div>

          <!-- STEP 1: Source SQL -->
          <div class="section-title"><mat-icon>storage</mat-icon> Étape 1 — Source de données SQL</div>

          <div class="sql-helper">
            <span class="helper-label">Tables disponibles:</span>
            @for (t of availableTables(); track t.table) {
              <button class="table-chip" (click)="insertTableName(t.table)" [matTooltip]="t.description">{{ t.table }}</button>
            }
          </div>

          <mat-form-field appearance="outline" class="full">
            <mat-label>Requête SQL (placeholders: :centerId, :patientId optionnel)</mat-label>
            <textarea matInput rows="5" [(ngModel)]="draft.data_source_sql" placeholder="SELECT p.nom, p.prenom FROM patients p WHERE p.center_id = :centerId"></textarea>
          </mat-form-field>

          <div class="sql-actions">
            <button mat-flat-button color="primary" (click)="testSql()" [disabled]="sqlTesting()">
              @if (sqlTesting()) {
                <mat-spinner diameter="18"></mat-spinner>
              } @else {
                <mat-icon>play_arrow</mat-icon>
              }
              Tester la requête
            </button>
            <mat-form-field appearance="outline" style="width:280px">
              <mat-label>Patient ID (optionnel)</mat-label>
              <input matInput [(ngModel)]="previewPatientId" />
            </mat-form-field>
          </div>

          @if (sqlError()) {
            <div class="sql-error"><mat-icon>error</mat-icon> {{ sqlError() }}</div>
          }

          @if (sqlColumns().length > 0) {
            <div class="sql-results">
              <div class="columns-title">
                <mat-icon>view_column</mat-icon>
                <strong>{{ sqlColumns().length }} colonnes détectées — {{ sqlTotalRows() }} lignes</strong>
              </div>
              <div class="columns-chips">
                @for (col of sqlColumns(); track col) {
                  <span class="column-chip" (click)="copyToken(col)" [matTooltip]="'Copier: {{data.' + col + '}}'">
                    {{ col }}
                  </span>
                }
              </div>

              @if (sqlSampleRows().length > 0) {
                <div class="sample-table-wrapper">
                  <table class="sample-table">
                    <thead>
                      <tr>
                        @for (col of sqlColumns(); track col) { <th>{{ col }}</th> }
                      </tr>
                    </thead>
                    <tbody>
                      @for (row of sqlSampleRows(); track $index) {
                        <tr>
                          @for (col of sqlColumns(); track col) { <td>{{ row[col] }}</td> }
                        </tr>
                      }
                    </tbody>
                  </table>
                </div>
              }
            </div>
          }

          <!-- STEP 2: Images -->
          <div class="section-title"><mat-icon>image</mat-icon> Étape 2 — Images entête / pied de page</div>

          <div class="images-row">
            <mat-form-field appearance="outline" class="flex"><mat-label>Image entête</mat-label><input matInput [(ngModel)]="draft.header_image" /></mat-form-field>
            <button mat-stroked-button (click)="headerInput.click()"><mat-icon>upload</mat-icon></button>
            <input #headerInput type="file" accept="image/*" hidden (change)="onImageUpload($event, 'header_image')" />
            <mat-form-field appearance="outline" class="flex"><mat-label>Image footer</mat-label><input matInput [(ngModel)]="draft.footer_image" /></mat-form-field>
            <button mat-stroked-button (click)="footerInput.click()"><mat-icon>upload</mat-icon></button>
            <input #footerInput type="file" accept="image/*" hidden (change)="onImageUpload($event, 'footer_image')" />
          </div>

          <div class="images-preview">
            @if (draft.header_image) { <img [src]="draft.header_image" alt="Entête" class="preview-img" /> }
            @if (draft.footer_image) { <img [src]="draft.footer_image" alt="Footer" class="preview-img" /> }
          </div>

          <!-- STEP 3: Template -->
          <div class="section-title"><mat-icon>design_services</mat-icon> Étape 3 — Conception du modèle</div>

          <div class="editor-tabs">
            <button class="editor-tab" [class.active]="editorMode === 'designer'" (click)="editorMode = 'designer'">
              <mat-icon>design_services</mat-icon> Designer WYSIWYG
            </button>
            <button class="editor-tab" [class.active]="editorMode === 'code'" (click)="editorMode = 'code'">
              <mat-icon>code</mat-icon> Code HTML
            </button>
          </div>

          @if (editorMode === 'code') {
            <div class="tips">
              <strong>Tokens SQL :</strong>
              @for (col of sqlColumns(); track col) {
                <code class="token-inline">{{ '{{data.' + col + '}}' }}</code>
              }
              @if (sqlColumns().length === 0) {
                <span>Testez d'abord votre requête SQL (Étape 1) pour voir les colonnes.</span>
              }
              <br/>
              <strong>Système :</strong>
              <code class="token-inline">{{ '{{patient.nom}}' }}</code>
              <code class="token-inline">{{ '{{center.name}}' }}</code>
              <code class="token-inline">{{ '{{generatedAt}}' }}</code>
              — <strong>Boucle :</strong> <code class="token-inline">{{ '{{#each data.rows}}' }}...{{ '{{row.col}}' }}...{{ '{{/each}}' }}</code>
            </div>
            <mat-form-field appearance="outline" class="full">
              <mat-label>Template HTML</mat-label>
              <textarea matInput rows="14" [(ngModel)]="draft.template_html"></textarea>
            </mat-form-field>
          } @else {
            <app-report-designer
              [headerImage]="draft.header_image || ''"
              [footerImage]="draft.footer_image || ''"
              (htmlGenerated)="onDesignerHtml($event)">
            </app-report-designer>
          }

          <!-- STEP 4: Preview & Export -->
          <div class="section-title"><mat-icon>preview</mat-icon> Étape 4 — Aperçu & Export</div>
          <div class="preview-row">
            <button mat-stroked-button (click)="previewFromSql()"><mat-icon>preview</mat-icon> Aperçu rapide</button>
            <button mat-stroked-button (click)="preview()" [disabled]="!draft.id"><mat-icon>print</mat-icon> Aperçu + Impression</button>
            <button mat-flat-button color="primary" (click)="exportPdf()" [disabled]="!draft.id"><mat-icon>picture_as_pdf</mat-icon> Export PDF</button>
          </div>
        }
      </mat-card>
    </div>
  `,
  styles: [`
    .reporting-page { display: grid; grid-template-columns: 300px 1fr; gap: 16px; padding: 8px; }
    .header-row { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; }
    .header-row h3 { display: flex; align-items: center; gap: 6px; color: #1b5e20; margin: 0; }
    .filters { margin-bottom: 8px; }
    .editor-card, .list-card { min-height: 75vh; padding: 16px; }
    .grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; }
    .full { width: 100%; }
    .empty-hint { text-align: center; color: #9e9e9e; padding: 30px 0; }
    .empty-editor { display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 400px; color: #bdbdbd; }
    .empty-editor mat-icon { font-size: 64px; width: 64px; height: 64px; }

    .section-title {
      display: flex; align-items: center; gap: 8px; margin: 20px 0 10px;
      font-weight: 700; font-size: 14px; color: #1b5e20; border-bottom: 2px solid #e8f5e9; padding-bottom: 6px;
    }

    .sql-helper { display: flex; flex-wrap: wrap; gap: 5px; align-items: center; margin-bottom: 8px; }
    .helper-label { font-size: 12px; color: #607d8b; font-weight: 600; }
    .table-chip {
      font-size: 10px; padding: 3px 8px; border: 1px solid #c8e6c9; border-radius: 12px;
      background: #f1f8e9; color: #1b5e20; cursor: pointer; font-family: monospace;
    }
    .table-chip:hover { background: #c8e6c9; }

    .sql-actions { display: flex; align-items: center; gap: 12px; margin: 8px 0; }
    .sql-error { background: #fce4ec; color: #c62828; padding: 8px 12px; border-radius: 6px; font-size: 12px; display: flex; align-items: center; gap: 6px; margin: 8px 0; }

    .sql-results { margin: 10px 0; }
    .columns-title { display: flex; align-items: center; gap: 6px; font-size: 13px; color: #1b5e20; margin-bottom: 6px; }
    .columns-chips { display: flex; flex-wrap: wrap; gap: 5px; margin-bottom: 10px; }
    .column-chip {
      padding: 4px 10px; background: #e3f2fd; border-radius: 14px; font-family: monospace;
      font-size: 11px; color: #1565c0; cursor: pointer; border: 1px solid #bbdefb;
    }
    .column-chip:hover { background: #bbdefb; }

    .sample-table-wrapper { overflow-x: auto; max-height: 200px; margin-bottom: 10px; }
    .sample-table { width: 100%; border-collapse: collapse; font-size: 11px; }
    .sample-table th { background: #e8f5e9; padding: 5px 8px; border: 1px solid #c8e6c9; font-weight: 600; color: #1b5e20; white-space: nowrap; }
    .sample-table td { padding: 5px 8px; border: 1px solid #e0e4e1; white-space: nowrap; }

    .images-row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
    .images-row .flex { flex: 1; min-width: 200px; }
    .images-preview { display: flex; gap: 10px; margin: 8px 0; }
    .preview-img { max-height: 70px; max-width: 300px; border: 1px solid #e5e7eb; border-radius: 6px; padding: 3px; }

    .editor-tabs { display: flex; gap: 0; margin: 10px 0 8px; border-bottom: 2px solid #e5e7eb; }
    .editor-tab {
      display: flex; align-items: center; gap: 5px; padding: 8px 18px; border: none; background: transparent;
      font-weight: 600; color: #607d8b; cursor: pointer; font-size: 13px; border-bottom: 3px solid transparent;
      transition: all 0.15s;
    }
    .editor-tab.active { color: #1b5e20; border-bottom-color: #1b5e20; }
    .editor-tab:hover { background: #f1f8e9; }

    .tips {
      margin: 8px 0 12px; padding: 10px 12px; background: #f8fafc; border-left: 3px solid #1b5e20;
      font-size: 12px; line-height: 1.8;
    }
    .token-inline { background: #e3f2fd; padding: 1px 5px; border-radius: 3px; font-size: 11px; margin: 0 2px; }

    .preview-row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin-top: 8px; }
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
  readonly availableTables = signal<Array<{ table: string; description: string }>>([]);

  readonly sqlColumns = signal<string[]>([]);
  readonly sqlSampleRows = signal<any[]>([]);
  readonly sqlTotalRows = signal(0);
  readonly sqlError = signal<string | null>(null);
  readonly sqlTesting = signal(false);

  selectedType = '';
  previewPatientId = '';
  draft: any = {};
  editorMode: 'code' | 'designer' = 'designer';

  ngOnInit(): void {
    this.api.getReportingModels().subscribe(v => this.models.set(v));
    this.api.getDatasourceTables().subscribe(v => this.availableTables.set(v));
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
    if (this.draft.data_source_sql) this.testSql();
  }

  newTemplate(): void {
    const centerId = this.store.currentCenterId();
    this.selected.set({ id: null, center_id: centerId, active: true, report_type: 'CUSTOM', page_format: 'A4', orientation: 'PORTRAIT', layout_mode: 'STANDARD' });
    this.draft = {
      ...this.selected(),
      code: '', name: '', field_schema: '',
      data_source_sql: 'SELECT p.nom, p.prenom, p.sexe, p.date_naissance, p.tel_mobile\nFROM patients p\nWHERE p.center_id = :centerId',
      header_image: '', footer_image: '', template_html: ''
    };
    this.sqlColumns.set([]); this.sqlSampleRows.set([]); this.sqlError.set(null);
  }

  insertTableName(table: string): void {
    this.draft.data_source_sql = (this.draft.data_source_sql || '') + ' ' + table;
  }

  testSql(): void {
    const centerId = this.store.currentCenterId();
    if (!centerId || !this.draft.data_source_sql?.trim()) return;
    this.sqlTesting.set(true); this.sqlError.set(null); this.sqlColumns.set([]); this.sqlSampleRows.set([]);

    this.api.testReportSql(this.draft.data_source_sql, centerId, this.previewPatientId || undefined).subscribe({
      next: (r) => {
        this.sqlTesting.set(false);
        if (r.error) { this.sqlError.set(r.error); }
        else { this.sqlColumns.set(r.columns); this.sqlSampleRows.set(r.sampleRows); this.sqlTotalRows.set(r.totalRows); }
      },
      error: (err) => { this.sqlTesting.set(false); this.sqlError.set(err?.error?.message || 'Erreur SQL'); }
    });
  }

  copyToken(col: string): void {
    const token = `{{data.${col}}}`;
    navigator.clipboard.writeText(token).then(() => this.snackbar.open(`Copié: ${token}`, 'OK', { duration: 1500 }));
  }

  save(): void {
    const centerId = this.store.currentCenterId();
    if (!centerId) return;
    const payload = {
      ...this.draft, centerId,
      reportType: this.draft.report_type, pageFormat: this.draft.page_format,
      layoutMode: this.draft.layout_mode, fieldSchema: this.draft.field_schema,
      templateHtml: this.draft.template_html, dataSourceSql: this.draft.data_source_sql,
      headerImage: this.draft.header_image, footerImage: this.draft.footer_image,
      active: this.draft.active !== false
    };
    const call = this.draft.id ? this.api.updateReportTemplate(this.draft.id, payload) : this.api.createReportTemplate(payload);
    call.subscribe({
      next: (res) => { if (!this.draft.id && res?.id) this.draft.id = res.id; this.snackbar.open('Modèle enregistré ✓', 'OK', { duration: 2000 }); this.loadTemplates(); },
      error: () => this.snackbar.open('Erreur enregistrement', 'OK', { duration: 2500 })
    });
  }

  deleteSelected(): void {
    const centerId = this.store.currentCenterId();
    if (!centerId || !this.draft.id) return;
    this.api.deleteReportTemplate(this.draft.id, centerId).subscribe(() => {
      this.snackbar.open('Modèle supprimé', 'OK', { duration: 2000 }); this.selected.set(null); this.loadTemplates();
    });
  }

  previewFromSql(): void {
    const centerId = this.store.currentCenterId();
    if (!centerId) return;
    this.api.renderFromSql({
      templateHtml: this.draft.template_html || '', sql: this.draft.data_source_sql || '',
      centerId, headerImage: this.draft.header_image || '', footerImage: this.draft.footer_image || ''
    }).subscribe(html => {
      const w = window.open('', '_blank'); if (!w) return; w.document.write(html); w.document.close(); w.focus();
    });
  }

  preview(): void {
    const centerId = this.store.currentCenterId();
    if (!centerId || !this.draft.id) return;
    this.api.renderReportByTemplate(this.draft.id, centerId, this.previewPatientId || undefined).subscribe(html => {
      const w = window.open('', '_blank'); if (!w) return; w.document.write(html); w.document.close(); w.focus(); w.print();
    });
  }

  exportPdf(): void {
    const centerId = this.store.currentCenterId();
    if (!centerId || !this.draft.id) { this.snackbar.open('Enregistrez d\'abord le modèle', 'OK', { duration: 2500 }); return; }
    let url = `http://localhost:8080/api/v1/reporting/export/pdf/${this.draft.id}?centerId=${centerId}`;
    if (this.previewPatientId) url += `&patientId=${this.previewPatientId}`;
    window.open(url, '_blank');
  }

  onImageUpload(event: Event, key: 'header_image' | 'footer_image'): void {
    const file = (event.target as HTMLInputElement).files?.[0]; if (!file) return;
    const reader = new FileReader();
    reader.onload = () => { this.draft[key] = reader.result as string; };
    reader.readAsDataURL(file);
  }

  onDesignerHtml(html: string): void {
    this.draft.template_html = html;
    this.snackbar.open('HTML généré depuis le designer ✓', 'OK', { duration: 2000 });
    this.editorMode = 'code';
  }
}

