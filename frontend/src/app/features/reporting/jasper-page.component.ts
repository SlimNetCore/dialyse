import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { BackendApiService } from '../../core/api/backend-api.service';
import { AppShellStore } from '../../core/state/app-shell.store';

@Component({
  selector: 'app-jasper-page',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    MatCardModule, MatButtonModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatIconModule, MatSnackBarModule, MatProgressSpinnerModule,
    MatTooltipModule, MatCheckboxModule
  ],
  template: `
    <div class="jasper-page">
      <h2><mat-icon>analytics</mat-icon> Reporting avancé (JasperReports)</h2>
      <p class="subtitle">Générez des rapports professionnels PDF, Excel et HTML à partir de requêtes SQL.</p>

      <!-- STEP 1: SQL -->
      <mat-card class="step-card">
        <div class="step-header"><span class="step-num">1</span> Source de données SQL</div>

        <mat-form-field appearance="outline" class="full">
          <mat-label>Requête SQL (:centerId sera remplacé automatiquement)</mat-label>
          <textarea matInput rows="5" [(ngModel)]="sql" placeholder="SELECT p.nom, p.prenom, p.sexe, p.date_naissance, p.tel_mobile FROM patients p WHERE p.center_id = :centerId"></textarea>
        </mat-form-field>

        <div class="sql-actions">
          <button mat-flat-button color="primary" (click)="testSql()" [disabled]="testing()">
            @if (testing()) { <mat-spinner diameter="18"></mat-spinner> }
            @else { <mat-icon>play_arrow</mat-icon> }
            Tester la requête
          </button>
        </div>

        @if (sqlError()) {
          <div class="error-box"><mat-icon>error</mat-icon> {{ sqlError() }}</div>
        }

        @if (columns().length > 0) {
          <div class="columns-info">
            <mat-icon>view_column</mat-icon>
            <strong>{{ columns().length }} colonnes — {{ totalRows() }} lignes</strong>
          </div>
          <div class="columns-list">
            @for (col of columns(); track col; let i = $index) {
              <div class="col-item">
                <span class="col-name">{{ col }}</span>
                <mat-form-field appearance="outline" class="col-header-field">
                  <mat-label>Entête</mat-label>
                  <input matInput [(ngModel)]="headers[i]" />
                </mat-form-field>
              </div>
            }
          </div>

          @if (sampleRows().length > 0) {
            <div class="sample-wrapper">
              <table class="sample-table">
                <thead>
                  <tr>@for (col of columns(); track col) { <th>{{ col }}</th> }</tr>
                </thead>
                <tbody>
                  @for (row of sampleRows(); track $index) {
                    <tr>@for (col of columns(); track col) { <td>{{ row[col] }}</td> }</tr>
                  }
                </tbody>
              </table>
            </div>
          }
        }
      </mat-card>

      <!-- STEP 2: Configuration -->
      @if (columns().length > 0) {
        <mat-card class="step-card">
          <div class="step-header"><span class="step-num">2</span> Configuration du rapport</div>

          <div class="config-grid">
            <mat-form-field appearance="outline">
              <mat-label>Titre du rapport</mat-label>
              <input matInput [(ngModel)]="title" />
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Format page</mat-label>
              <mat-select [(ngModel)]="pageFormat">
                <mat-option value="A4">A4</mat-option>
                <mat-option value="A5">A5</mat-option>
                <mat-option value="LETTER">Letter</mat-option>
              </mat-select>
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Format export</mat-label>
              <mat-select [(ngModel)]="exportFormat">
                <mat-option value="PDF">PDF</mat-option>
                <mat-option value="EXCEL">Excel (XLS)</mat-option>
                <mat-option value="HTML">HTML (aperçu)</mat-option>
              </mat-select>
            </mat-form-field>
          </div>

          <mat-checkbox [(ngModel)]="landscape">Paysage</mat-checkbox>

          <div class="images-row">
            <mat-form-field appearance="outline" class="flex">
              <mat-label>Image entête (base64 ou URL)</mat-label>
              <input matInput [(ngModel)]="headerImage" />
            </mat-form-field>
            <button mat-stroked-button (click)="headerInput.click()"><mat-icon>upload</mat-icon></button>
            <input #headerInput type="file" accept="image/*" hidden (change)="onUpload($event, 'header')" />

            <mat-form-field appearance="outline" class="flex">
              <mat-label>Image footer</mat-label>
              <input matInput [(ngModel)]="footerImage" />
            </mat-form-field>
            <button mat-stroked-button (click)="footerInput.click()"><mat-icon>upload</mat-icon></button>
            <input #footerInput type="file" accept="image/*" hidden (change)="onUpload($event, 'footer')" />
          </div>

          <div class="images-preview">
            @if (headerImage) { <img [src]="headerImage" alt="Entête" class="img-prev" /> }
            @if (footerImage) { <img [src]="footerImage" alt="Footer" class="img-prev" /> }
          </div>
        </mat-card>

        <!-- STEP 3: Generate -->
        <mat-card class="step-card">
          <div class="step-header"><span class="step-num">3</span> Générer le rapport</div>
          <div class="gen-actions">
            <button mat-flat-button color="primary" (click)="generate()" [disabled]="generating()">
              @if (generating()) { <mat-spinner diameter="18"></mat-spinner> }
              @else { <mat-icon>picture_as_pdf</mat-icon> }
              Générer ({{ exportFormat }})
            </button>
            <button mat-stroked-button (click)="previewJrxml()">
              <mat-icon>code</mat-icon> Voir le .jrxml généré
            </button>
          </div>

          @if (jrxmlPreview()) {
            <mat-form-field appearance="outline" class="full">
              <mat-label>.jrxml auto-généré</mat-label>
              <textarea matInput rows="12" [value]="jrxmlPreview()" readonly></textarea>
            </mat-form-field>
          }
        </mat-card>
      }
    </div>
  `,
  styles: [`
    .jasper-page { max-width: 1200px; margin: 0 auto; padding: 16px; }
    h2 { display: flex; align-items: center; gap: 8px; color: #1b5e20; }
    .subtitle { color: #607d8b; margin-bottom: 16px; }

    .step-card { padding: 20px; margin-bottom: 16px; }
    .step-header { display: flex; align-items: center; gap: 10px; font-weight: 700; font-size: 15px; color: #1b5e20; margin-bottom: 14px; border-bottom: 2px solid #e8f5e9; padding-bottom: 8px; }
    .step-num { background: #1b5e20; color: #fff; width: 28px; height: 28px; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 14px; }

    .full { width: 100%; }
    .sql-actions { display: flex; gap: 10px; margin-bottom: 10px; }
    .error-box { background: #fce4ec; color: #c62828; padding: 8px 12px; border-radius: 6px; font-size: 12px; display: flex; align-items: center; gap: 6px; margin: 8px 0; }

    .columns-info { display: flex; align-items: center; gap: 6px; color: #1b5e20; font-size: 13px; margin: 10px 0 8px; }
    .columns-list { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 12px; }
    .col-item { display: flex; align-items: center; gap: 6px; }
    .col-name { font-family: monospace; font-size: 12px; color: #1565c0; background: #e3f2fd; padding: 4px 8px; border-radius: 12px; }
    .col-header-field { width: 140px; }
    :host ::ng-deep .col-header-field .mat-mdc-form-field-infix { padding-top: 6px !important; padding-bottom: 6px !important; min-height: 32px !important; }

    .sample-wrapper { overflow-x: auto; max-height: 180px; margin-bottom: 10px; }
    .sample-table { width: 100%; border-collapse: collapse; font-size: 11px; }
    .sample-table th { background: #e8f5e9; padding: 5px 8px; border: 1px solid #c8e6c9; font-weight: 600; color: #1b5e20; white-space: nowrap; }
    .sample-table td { padding: 5px 8px; border: 1px solid #e0e4e1; white-space: nowrap; }

    .config-grid { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 12px; }
    .images-row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin-top: 10px; }
    .images-row .flex { flex: 1; min-width: 200px; }
    .images-preview { display: flex; gap: 10px; margin: 8px 0; }
    .img-prev { max-height: 60px; max-width: 250px; border: 1px solid #e5e7eb; border-radius: 4px; }

    .gen-actions { display: flex; gap: 12px; align-items: center; }
  `]
})
export class JasperPageComponent {
  private readonly api = inject(BackendApiService);
  private readonly store = inject(AppShellStore);
  private readonly snackbar = inject(MatSnackBar);

  sql = 'SELECT p.nom, p.prenom, p.sexe, p.date_naissance, p.tel_mobile, p.numero_assurance\nFROM patients p\nWHERE p.center_id = :centerId';
  title = 'Liste des patients';
  pageFormat = 'A4';
  exportFormat: 'PDF' | 'EXCEL' | 'HTML' = 'PDF';
  landscape = false;
  headerImage = '';
  footerImage = '';
  headers: string[] = [];

  readonly columns = signal<string[]>([]);
  readonly sampleRows = signal<any[]>([]);
  readonly totalRows = signal(0);
  readonly sqlError = signal<string | null>(null);
  readonly testing = signal(false);
  readonly generating = signal(false);
  readonly jrxmlPreview = signal<string>('');

  testSql(): void {
    const centerId = this.store.currentCenterId();
    if (!centerId || !this.sql.trim()) return;
    this.testing.set(true);
    this.sqlError.set(null);
    this.columns.set([]);
    this.sampleRows.set([]);

    this.api.jasperTestSql(this.sql, centerId).subscribe({
      next: r => {
        this.testing.set(false);
        if (r.error) { this.sqlError.set(r.error); return; }
        this.columns.set(r.columns);
        this.sampleRows.set(r.sampleRows);
        this.totalRows.set(r.totalRows);
        // Initialize headers with column names
        this.headers = r.columns.map(c => c.replace(/_/g, ' '));
      },
      error: err => { this.testing.set(false); this.sqlError.set(err?.message || 'Erreur'); }
    });
  }

  generate(): void {
    const centerId = this.store.currentCenterId();
    if (!centerId || this.columns().length === 0) return;
    this.generating.set(true);

    this.api.jasperGenerate({
      title: this.title,
      sql: this.sql,
      centerId,
      centerName: '',
      columns: this.columns(),
      headers: this.headers,
      format: this.exportFormat,
      pageFormat: this.pageFormat,
      landscape: this.landscape,
      headerImage: this.headerImage,
      footerImage: this.footerImage
    }).subscribe({
      next: blob => {
        this.generating.set(false);
        if (this.exportFormat === 'HTML') {
          const url = URL.createObjectURL(blob);
          window.open(url, '_blank');
        } else {
          const ext = this.exportFormat === 'EXCEL' ? 'xls' : 'pdf';
          const url = URL.createObjectURL(blob);
          const a = document.createElement('a');
          a.href = url;
          a.download = `report.${ext}`;
          a.click();
          URL.revokeObjectURL(url);
        }
        this.snackbar.open('Rapport généré ✓', 'OK', { duration: 2500 });
      },
      error: err => {
        this.generating.set(false);
        this.snackbar.open('Erreur: ' + (err?.message || 'échec'), 'OK', { duration: 3000 });
      }
    });
  }

  previewJrxml(): void {
    this.api.jasperPreviewJrxml({
      title: this.title,
      columns: this.columns(),
      headers: this.headers,
      pageFormat: this.pageFormat,
      landscape: this.landscape,
      headerImage: this.headerImage,
      footerImage: this.footerImage
    }).subscribe(xml => this.jrxmlPreview.set(xml));
  }

  onUpload(event: Event, target: 'header' | 'footer'): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      if (target === 'header') this.headerImage = reader.result as string;
      else this.footerImage = reader.result as string;
    };
    reader.readAsDataURL(file);
  }
}


