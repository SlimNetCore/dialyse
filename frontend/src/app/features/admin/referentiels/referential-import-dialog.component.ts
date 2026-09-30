import {ChangeDetectionStrategy, Component, computed, inject, signal} from '@angular/core';
import {MAT_DIALOG_DATA, MatDialogModule, MatDialogRef} from '@angular/material/dialog';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatPaginatorModule, PageEvent} from '@angular/material/paginator';
import {TranslateModule} from '@ngx-translate/core';
import {firstValueFrom} from 'rxjs';
import {ReferentialAdminApiService, ReferentialKindDef} from '../../../core/api/referential-admin-api.service';
import {ReferentialAdminStore} from './state/referential-admin.store';
import {ReferentialLabels} from './referential-labels.service';

export interface ReferentialImportDialogData {
  kind: ReferentialKindDef;
}

const ACCEPTED = ['.csv', '.txt', '.xlsx', '.xls'];

/**
 * Import d'un fichier CSV / Excel : téléchargement du modèle, vérification (rien n'est écrit), compte rendu
 * détaillé (colonnes manquantes, anomalies ligne par ligne) puis import tout-ou-rien.
 */
@Component({
  selector: 'app-referential-import-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatDialogModule, MatButtonModule, MatIconModule, MatProgressBarModule, MatPaginatorModule, TranslateModule],
  template: `
    <h2 mat-dialog-title>{{ 'ADMIN.REFERENTIALS.IMPORT.TITLE' | translate: {kind: kindLabel} }}</h2>
    <mat-dialog-content class="import-content">
      <section class="help">
        <p>{{ 'ADMIN.REFERENTIALS.IMPORT.HELP' | translate }}</p>
        <p class="columns">
          <strong>{{ 'ADMIN.REFERENTIALS.IMPORT.REQUIRED_COLUMNS' | translate }}</strong>
          {{ requiredColumns() }}
        </p>
        @if (optionalColumns()) {
          <p class="columns">
            <strong>{{ 'ADMIN.REFERENTIALS.IMPORT.OPTIONAL_COLUMNS' | translate }}</strong>
            {{ optionalColumns() }}
          </p>
        }
        @if (prerequisites()) {
          <p class="prerequisite" data-testid="import-prerequisite">
            <mat-icon>info</mat-icon>
            {{ 'ADMIN.REFERENTIALS.IMPORT.PREREQUISITE' | translate: {targets: prerequisites()} }}
          </p>
        }
        <div class="template-actions">
          <button mat-stroked-button type="button" (click)="downloadTemplate('xlsx')">
            <mat-icon>grid_on</mat-icon>
            {{ 'ADMIN.REFERENTIALS.IMPORT.TEMPLATE_XLSX' | translate }}
          </button>
          <button mat-stroked-button type="button" (click)="downloadTemplate('csv')">
            <mat-icon>description</mat-icon>
            {{ 'ADMIN.REFERENTIALS.IMPORT.TEMPLATE_CSV' | translate }}
          </button>
        </div>
      </section>

      <section class="file-picker">
        <input #fileInput type="file" hidden [accept]="accepted" (change)="onFileSelected(fileInput)"
               data-testid="import-file-input"/>
        <button mat-flat-button color="primary" type="button" (click)="fileInput.click()"
                [disabled]="store.importing()">
          <mat-icon>upload_file</mat-icon>
          {{ 'ADMIN.REFERENTIALS.IMPORT.CHOOSE_FILE' | translate }}
        </button>
        <span class="file-name">{{ file()?.name ?? ('ADMIN.REFERENTIALS.IMPORT.NO_FILE' | translate) }}</span>
      </section>

      @if (store.importing()) {
        <mat-progress-bar mode="indeterminate"/>
      }
      @if (localError(); as err) {
        <p class="feedback error" role="alert">{{ err | translate }}</p>
      }
      @if (store.importError(); as err) {
        <p class="feedback error" role="alert">{{ err | translate }}</p>
      }

      @if (report(); as r) {
        <section class="report" data-testid="import-report" aria-live="polite">
          @if (r.applied) {
            <p class="feedback success" data-testid="import-applied">
              <mat-icon>check_circle</mat-icon>
              {{ 'ADMIN.REFERENTIALS.IMPORT.APPLIED' | translate: {created: r.created, updated: r.updated} }}
            </p>
          } @else if (r.valid) {
            <p class="feedback success" data-testid="import-valid">
              <mat-icon>task_alt</mat-icon>
              {{
                'ADMIN.REFERENTIALS.IMPORT.VALID' | translate: {
                  rows: r.totalRows,
                  created: r.created,
                  updated: r.updated
                }
              }}
            </p>
          } @else {
            <p class="feedback error" data-testid="import-invalid">
              <mat-icon>error</mat-icon>
              {{ 'ADMIN.REFERENTIALS.IMPORT.INVALID' | translate: {rows: r.totalRows} }}
            </p>
          }

          @if (r.missingColumns.length > 0) {
            <div class="block missing" data-testid="import-missing-columns">
              <strong>{{ 'ADMIN.REFERENTIALS.IMPORT.MISSING_COLUMNS' | translate }}</strong>
              <ul>
                @for (key of r.missingColumns; track key) {
                  <li>{{ columnLabel(key) }}</li>
                }
              </ul>
            </div>
          }
          @if (r.ignoredColumns.length > 0) {
            <div class="block ignored">
              <strong>{{ 'ADMIN.REFERENTIALS.IMPORT.IGNORED_COLUMNS' | translate }}</strong>
              {{ r.ignoredColumns.join(', ') }}
            </div>
          }

          @if (r.errors.length > 0) {
            <div class="errors" data-testid="import-errors">
              <div class="error-row head" aria-hidden="true">
                <span>{{ 'ADMIN.REFERENTIALS.IMPORT.COL_ROW' | translate }}</span>
                <span>{{ 'ADMIN.REFERENTIALS.IMPORT.COL_COLUMN' | translate }}</span>
                <span>{{ 'ADMIN.REFERENTIALS.IMPORT.COL_PROBLEM' | translate }}</span>
              </div>
              @for (issue of pagedErrors(); track $index) {
                <div class="error-row">
                  <span class="row-num">{{ issue.row > 0 ? issue.row : '—' }}</span>
                  <span>{{ issue.field ? columnLabel(issue.field) : '—' }}</span>
                  <span>{{ issueText(issue) }}</span>
                </div>
              }
              <mat-paginator [length]="r.errors.length" [pageIndex]="errorPage()" [pageSize]="errorPageSize()"
                             [pageSizeOptions]="[10, 20, 50, 100]" (page)="onErrorPage($event)"/>
            </div>
          }
        </section>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-stroked-button type="button" (click)="close()">
        {{ (report()?.applied ? 'ADMIN.REFERENTIALS.IMPORT.CLOSE' : 'COMMON.CANCEL') | translate }}
      </button>
      @if (!report()?.applied) {
        <button mat-stroked-button color="primary" type="button" (click)="check()"
                [disabled]="!file() || store.importing()" data-testid="import-check">
          <mat-icon>fact_check</mat-icon>
          {{ 'ADMIN.REFERENTIALS.IMPORT.CHECK' | translate }}
        </button>
        <button mat-flat-button color="primary" type="button" (click)="confirm()"
                [disabled]="!canImport() || store.importing()" data-testid="import-confirm">
          <mat-icon>cloud_upload</mat-icon>
          {{ 'ADMIN.REFERENTIALS.IMPORT.CONFIRM' | translate: {rows: report()?.totalRows ?? 0} }}
        </button>
      }
    </mat-dialog-actions>
  `,
  styles: [`
    .import-content {
      display: flex;
      flex-direction: column;
      gap: 12px;
      min-width: min(720px, 86vw);
    }

    .help p {
      margin: 0 0 6px;
      color: var(--app-muted);
    }

    .columns strong, .block strong {
      margin-right: 6px;
      color: var(--app-text);
    }

    .prerequisite {
      display: flex;
      align-items: center;
      gap: 6px;
      color: var(--app-primary) !important;
    }

    .template-actions, .file-picker {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 8px;
    }

    .file-name {
      font-weight: 600;
      overflow-wrap: anywhere;
    }

    .feedback {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 10px 14px;
      border-radius: 8px;
      margin: 0;
      font-weight: 500;
    }

    .feedback.success {
      background: #e8f5e9;
      color: #1b5e20;
    }

    .feedback.error {
      background: #fce4ec;
      color: #c62828;
    }

    .report {
      display: flex;
      flex-direction: column;
      gap: 10px;
    }

    .block {
      padding: 10px 14px;
      border-radius: 8px;
      border: 1px solid var(--app-border);
    }

    .block.missing {
      border-color: #ef9a9a;
      background: #fff5f5;
    }

    .block ul {
      margin: 6px 0 0;
      padding-left: 20px;
    }

    .errors {
      border: 1px solid var(--app-border);
      border-radius: 8px;
      overflow: hidden;
    }

    .error-row {
      display: grid;
      grid-template-columns: 64px minmax(110px, 180px) 1fr;
      gap: 12px;
      padding: 8px 12px;
      border-top: 1px solid var(--app-border);
      font-size: 13px;
    }

    .error-row.head {
      border-top: 0;
      font-weight: 700;
      color: var(--app-primary);
      background: var(--app-surface-soft, #f7fbfe);
    }

    .row-num {
      font-weight: 700;
    }

    @media (max-width: 767px) {
      .import-content {
        min-width: 0;
      }

      .error-row {
        grid-template-columns: 48px 1fr;
      }

      .error-row > span:last-child {
        grid-column: 1 / -1;
      }
    }
  `],
})
export class ReferentialImportDialogComponent {
  protected readonly data = inject<ReferentialImportDialogData>(MAT_DIALOG_DATA);
  protected readonly store = inject(ReferentialAdminStore);
  protected readonly accepted = ACCEPTED.join(',');
  protected readonly file = signal<File | null>(null);
  protected readonly localError = signal<string | null>(null);
  protected readonly errorPage = signal(0);
  protected readonly errorPageSize = signal(10);
  /** Compte rendu du fichier actuellement sélectionné (celui du store peut concerner un autre référentiel). */
  protected readonly report = computed(() => {
    const r = this.store.importReport();
    return r && r.kind === this.data.kind.slug ? r : null;
  });
  protected readonly canImport = computed(() => !!this.file() && !!this.report()?.valid && !this.report()?.applied);
  protected readonly pagedErrors = computed(() => {
    const start = this.errorPage() * this.errorPageSize();
    return (this.report()?.errors ?? []).slice(start, start + this.errorPageSize());
  });
  private readonly dialogRef = inject(MatDialogRef<ReferentialImportDialogComponent, boolean>);
  private readonly api = inject(ReferentialAdminApiService);
  private readonly labels = inject(ReferentialLabels);
  protected readonly kindLabel = this.labels.kind(this.data.kind);
  protected readonly requiredColumns = computed(() =>
    this.data.kind.fields.filter((f) => f.requiredColumn).map((f) => this.labels.field(f)).join(', '));
  protected readonly optionalColumns = computed(() =>
    this.data.kind.fields.filter((f) => !f.requiredColumn).map((f) => this.labels.field(f)).join(', '));
  protected readonly prerequisites = computed(() => this.data.kind.fields
    .filter((f) => f.type === 'REFERENCE' && f.reference)
    .map((f) => this.store.kinds().find((k) => k.slug === f.reference))
    .filter((k): k is ReferentialKindDef => !!k)
    .map((k) => this.labels.kind(k))
    .join(', '));

  constructor() {
    this.store.clearImport();
  }

  protected onFileSelected(input: HTMLInputElement): void {
    const selected = input.files?.[0] ?? null;
    input.value = '';
    this.store.clearImport();
    this.localError.set(null);
    if (!selected) return;
    const name = selected.name.toLowerCase();
    if (!ACCEPTED.some((ext) => name.endsWith(ext))) {
      this.file.set(null);
      this.localError.set('ADMIN.REFERENTIALS.IMPORT.UNSUPPORTED');
      return;
    }
    this.file.set(selected);
    void this.check();
  }

  protected async check(): Promise<void> {
    const file = this.file();
    if (!file) return;
    this.errorPage.set(0);
    await this.store.importFile(file, true);
  }

  protected async confirm(): Promise<void> {
    const file = this.file();
    if (!file || !this.canImport()) return;
    this.errorPage.set(0);
    await this.store.importFile(file, false);
  }

  protected onErrorPage(event: PageEvent): void {
    this.errorPage.set(event.pageIndex);
    this.errorPageSize.set(event.pageSize);
  }

  protected columnLabel(key: string): string {
    const field = this.data.kind.fields.find((f) => f.key === key);
    return field ? this.labels.field(field) : key;
  }

  protected issueText(issue: Parameters<ReferentialLabels['issue']>[0]): string {
    return this.labels.issue(issue, this.data.kind);
  }

  protected async downloadTemplate(format: 'csv' | 'xlsx'): Promise<void> {
    try {
      const blob = await firstValueFrom(this.api.template(this.data.kind.slug, format));
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `modele-${this.data.kind.slug}.${format}`;
      link.click();
      URL.revokeObjectURL(url);
    } catch {
      this.localError.set('ADMIN.REFERENTIALS.IMPORT.TEMPLATE_ERROR');
    }
  }

  protected close(): void {
    this.dialogRef.close(!!this.report()?.applied);
  }
}

