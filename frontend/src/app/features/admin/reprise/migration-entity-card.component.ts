import {ChangeDetectionStrategy, Component, computed, inject, input, linkedSignal} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {TranslateModule} from '@ngx-translate/core';
import {firstValueFrom} from 'rxjs';
import {
  MigrationApiService,
  MigrationEntityDef,
  MigrationRun,
  ValueMapping
} from '../../../core/api/migration-api.service';
import {MigrationStore} from './state/migration.store';
import {MigrationLabels} from './migration-labels.service';
import {MigrationReportComponent} from './migration-report.component';

const ACCEPTED = ['.csv', '.txt', '.xlsx', '.xls'];

export type MigrationStepStatus = 'TODO' | 'TO_FIX' | 'CHECKED' | 'IMPORTED';

/** État d'une étape de reprise d'après son dernier compte rendu. */
export function stepStatus(run: MigrationRun | null | undefined): MigrationStepStatus {
  if (!run) return 'TODO';
  if (run.applied) return 'IMPORTED';
  return run.valid ? 'CHECKED' : 'TO_FIX';
}

/**
 * Étape de reprise d'une donnée : modèle à télécharger, choix du fichier (vérifié aussitôt), compte rendu,
 * association des valeurs inconnues (le fichier est alors revérifié), puis import.
 */
@Component({
  selector: 'app-migration-entity-card',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButtonModule, MatIconModule, MatProgressBarModule, TranslateModule, MigrationReportComponent],
  template: `
    <article class="step" [class]="'step status-' + status()" [attr.data-testid]="'migration-step-' + entity().slug">
      <header class="step-header">
        <span class="order">{{ entity().order }}</span>
        <div class="title">
          <h3>{{ labels.entity(entity()) }}</h3>
          <span class="chip" [attr.data-testid]="'migration-status-' + entity().slug">
            {{ 'ADMIN.MIGRATION.STATUS.' + status() | translate }}
          </span>
        </div>
        <div class="templates">
          <button mat-button type="button" (click)="downloadTemplate('xlsx')">
            <mat-icon>grid_on</mat-icon>{{ 'ADMIN.MIGRATION.TEMPLATE_XLSX' | translate }}
          </button>
          <button mat-button type="button" (click)="downloadTemplate('csv')">
            <mat-icon>description</mat-icon>{{ 'ADMIN.MIGRATION.TEMPLATE_CSV' | translate }}
          </button>
        </div>
      </header>

      <p class="columns">
        <strong>{{ 'ADMIN.MIGRATION.REQUIRED_COLUMNS' | translate }}</strong> {{ requiredColumns() }}
      </p>

      @if (editable()) {
        <div class="actions">
          <input #fileInput type="file" hidden [accept]="accepted" (change)="onFileSelected(fileInput)"
                 [attr.data-testid]="'migration-file-' + entity().slug"/>
          <button mat-stroked-button type="button" (click)="fileInput.click()" [disabled]="busy()">
            <mat-icon>upload_file</mat-icon>{{ 'ADMIN.MIGRATION.CHOOSE_FILE' | translate }}
          </button>
          <span class="file-name">{{ fileLabel() ?? ('ADMIN.MIGRATION.NO_FILE' | translate) }}</span>
          <span class="spacer"></span>
          <button mat-stroked-button color="primary" type="button" (click)="check()" [disabled]="!file() || busy()"
                  [attr.data-testid]="'migration-check-' + entity().slug">
            <mat-icon>fact_check</mat-icon>{{ 'ADMIN.MIGRATION.CHECK' | translate }}
          </button>
          <button mat-flat-button color="primary" type="button" (click)="apply()" [disabled]="!canImport()"
                  [attr.data-testid]="'migration-import-' + entity().slug">
            <mat-icon>cloud_upload</mat-icon>{{ 'ADMIN.MIGRATION.IMPORT' | translate: {rows: rowCount()} }}
          </button>
        </div>
      }
      @if (localError(); as err) {
        <p class="feedback error" role="alert">{{ err | translate }}</p>
      }
      @if (busy()) {
        <mat-progress-bar mode="indeterminate"/>
      }
      @if (run(); as r) {
        <app-migration-report [entity]="entity()" [run]="r" [canMap]="editable()" (mapValue)="onMap($event)"/>
      }
    </article>
  `,
  styles: [`
    .step {
      border: 1px solid var(--app-border);
      border-left: 4px solid var(--app-border);
      border-radius: 12px;
      padding: 14px 16px;
      background: var(--app-surface);
      display: flex;
      flex-direction: column;
      gap: 10px;
    }

    .step.status-TO_FIX { border-left-color: #c62828; }
    .step.status-CHECKED { border-left-color: var(--app-primary); }
    .step.status-IMPORTED { border-left-color: #2e7d32; }

    .step-header {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 12px;
    }

    .order {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 30px;
      height: 30px;
      border-radius: 50%;
      background: color-mix(in srgb, var(--app-primary) 14%, white);
      color: var(--app-primary);
      font-weight: 700;
    }

    .title {
      display: flex;
      align-items: center;
      gap: 10px;
      flex: 1 1 auto;
    }

    .title h3 {
      margin: 0;
      font-size: 16px;
    }

    .chip {
      padding: 2px 10px;
      border-radius: 10px;
      font-size: 12px;
      font-weight: 600;
      background: var(--app-surface-soft, #f1f5f9);
      color: var(--app-muted);
    }

    .status-TO_FIX .chip { background: #fce4ec; color: #c62828; }
    .status-CHECKED .chip { background: color-mix(in srgb, var(--app-primary) 12%, white); color: var(--app-primary); }
    .status-IMPORTED .chip { background: #e8f5e9; color: #1b5e20; }

    .templates {
      display: flex;
      flex-wrap: wrap;
      gap: 4px;
    }

    .columns {
      margin: 0;
      color: var(--app-muted);
      font-size: 13px;
    }

    .actions {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 8px;
    }

    .spacer {
      flex: 1 1 auto;
    }

    .file-name {
      font-weight: 600;
      overflow-wrap: anywhere;
    }

    .feedback.error {
      padding: 10px 14px;
      border-radius: 8px;
      background: #fce4ec;
      color: #c62828;
      margin: 0;
    }

    @media (max-width: 767px) {
      .actions button {
        width: 100%;
      }

      .spacer {
        display: none;
      }
    }
  `],
})
export class MigrationEntityCardComponent {
  readonly entity = input.required<MigrationEntityDef>();
  /** Le lot est en cours : fichiers modifiables. */
  readonly editable = input(false);

  protected readonly store = inject(MigrationStore);
  protected readonly labels = inject(MigrationLabels);
  protected readonly accepted = ACCEPTED.join(',');
  /** Fichier choisi ; oublié quand on change de donnée affichée. */
  protected readonly file = linkedSignal<File | null>(() => {
    this.entity();
    return null;
  });
  protected readonly localError = linkedSignal<string | null>(() => {
    this.entity();
    return null;
  });
  protected readonly run = computed<MigrationRun | null>(() => this.store.runs()[this.entity().slug] ?? null);
  protected readonly fileLabel = computed<string | null>(() => this.file()?.name ?? this.run()?.fileName ?? null);
  protected readonly rowCount = computed(() => this.run()?.totalRows ?? 0);
  protected readonly status = computed(() => stepStatus(this.run()));
  protected readonly busy = computed(() => this.store.busyEntity() === this.entity().slug);
  /** Import possible : le fichier choisi vient d'être vérifié sans anomalie et n'a pas encore été importé. */
  protected readonly canImport = computed(() => {
    const run = this.run();
    return !!this.file() && !!run && run.valid && run.dryRun && !run.applied && !this.busy()
      && run.fileName === this.file()!.name;
  });
  protected readonly requiredColumns = computed(() => this.entity().columns
    .filter((c) => c.requiredColumn).map((c) => this.labels.column(this.entity(), c.key)).join(', '));
  private readonly api = inject(MigrationApiService);

  protected onFileSelected(input: HTMLInputElement): void {
    const selected = input.files?.[0] ?? null;
    input.value = '';
    this.localError.set(null);
    if (!selected) return;
    if (!ACCEPTED.some((ext) => selected.name.toLowerCase().endsWith(ext))) {
      this.file.set(null);
      this.localError.set('ADMIN.MIGRATION.UNSUPPORTED');
      return;
    }
    this.file.set(selected);
    void this.check();
  }

  protected async check(): Promise<void> {
    const file = this.file();
    if (file) await this.store.importFile(this.entity().slug, file, true);
  }

  protected async apply(): Promise<void> {
    const file = this.file();
    if (file && this.canImport()) await this.store.importFile(this.entity().slug, file, false);
  }

  /** Valeur inconnue associée : on revérifie aussitôt le même fichier. */
  protected async onMap(mapping: ValueMapping): Promise<void> {
    if (await this.store.saveValueMapping(mapping)) await this.check();
  }

  protected async downloadTemplate(format: 'csv' | 'xlsx'): Promise<void> {
    try {
      const blob = await firstValueFrom(this.api.template(this.entity().slug, format));
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `reprise-${this.entity().slug}.${format}`;
      link.click();
      URL.revokeObjectURL(url);
    } catch {
      this.localError.set('ADMIN.MIGRATION.TEMPLATE_ERROR');
    }
  }
}




