import {ChangeDetectionStrategy, Component, computed, inject, input, linkedSignal, output, signal} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatPaginatorModule, PageEvent} from '@angular/material/paginator';
import {MatSelectModule} from '@angular/material/select';
import {MatFormFieldModule} from '@angular/material/form-field';
import {TranslateModule} from '@ngx-translate/core';
import {MigrationEntityDef, MigrationRun, ValueMapping} from '../../../core/api/migration-api.service';
import {ValidationIssue} from '../../../core/api/referential-admin-api.service';
import {MigrationLabels} from './migration-labels.service';

/**
 * Compte rendu d'une vérification / d'un import : ce qui manque (colonnes), ce qui est faux (lignes), les points
 * d'attention, et — pour une valeur non reconnue — l'association directe à une valeur de la plateforme.
 */
@Component({
  selector: 'app-migration-report',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButtonModule, MatIconModule, MatPaginatorModule, MatSelectModule, MatFormFieldModule, TranslateModule],
  template: `
    @let r = run();
    <section class="report" [attr.data-testid]="'migration-report-' + entity().slug" aria-live="polite">
      @if (r.applied) {
        <p class="feedback success" data-testid="migration-applied">
          <mat-icon>check_circle</mat-icon>
          {{ 'ADMIN.MIGRATION.REPORT.APPLIED' | translate: {created: r.created, updated: r.updated} }}
        </p>
      } @else if (r.valid) {
        <p class="feedback success" data-testid="migration-valid">
          <mat-icon>task_alt</mat-icon>
          {{ 'ADMIN.MIGRATION.REPORT.VALID' | translate: {rows: r.totalRows, created: r.created, updated: r.updated} }}
        </p>
      } @else {
        <p class="feedback error" data-testid="migration-invalid">
          <mat-icon>error</mat-icon>
          {{ 'ADMIN.MIGRATION.REPORT.INVALID' | translate: {rows: r.totalRows, errors: r.errors.length} }}
        </p>
      }

      @if (r.missingColumns.length > 0) {
        <div class="block missing" data-testid="migration-missing-columns">
          <strong>{{ 'ADMIN.MIGRATION.REPORT.MISSING_COLUMNS' | translate }}</strong>
          <ul>
            @for (key of r.missingColumns; track key) {
              <li>{{ labels.column(entity(), key) }}</li>
            }
          </ul>
        </div>
      }
      @if (r.ignoredColumns.length > 0) {
        <div class="block">
          <strong>{{ 'ADMIN.MIGRATION.REPORT.IGNORED_COLUMNS' | translate }}</strong>
          {{ r.ignoredColumns.join(', ') }}
        </div>
      }

      @if (r.errors.length > 0) {
        <h4>{{ 'ADMIN.MIGRATION.REPORT.ERRORS' | translate: {count: r.errors.length} }}</h4>
        <div class="issues" data-testid="migration-errors">
          @for (issue of pagedErrors(); track $index) {
            <div class="issue-row">
              <span class="row-num">{{ issue.row > 0 ? issue.row : '—' }}</span>
              <span class="col">{{ labels.column(entity(), issue.field) }}</span>
              <span class="msg">
                {{ labels.issue(issue, entity()) }}
                @if (issue.code === 'UNKNOWN_VALUE' && canMap()) {
                  <span class="mapping">
                    <mat-form-field appearance="outline" subscriptSizing="dynamic" class="mapping-field">
                      <mat-select [placeholder]="'ADMIN.MIGRATION.REPORT.MAP_TO' | translate"
                                  (selectionChange)="choose(issue, $event.value)"
                                  [attr.data-testid]="'map-select-' + issue.row">
                        @for (option of allowedValues(issue); track option) {
                          <mat-option [value]="option">{{ labels.value(option) }}</mat-option>
                        }
                      </mat-select>
                    </mat-form-field>
                    <button mat-stroked-button type="button" [disabled]="!chosen()[mappingKey(issue)]"
                            (click)="map(issue)" [attr.data-testid]="'map-button-' + issue.row">
                      {{ 'ADMIN.MIGRATION.REPORT.MAP' | translate }}
                    </button>
                  </span>
                }
              </span>
            </div>
          }
          <mat-paginator [length]="r.errors.length" [pageIndex]="errorPage()" [pageSize]="pageSize()"
                         [pageSizeOptions]="[10, 20, 50, 100]" (page)="onErrorPage($event)"/>
        </div>
      }

      @if (r.warnings.length > 0) {
        <h4>{{ 'ADMIN.MIGRATION.REPORT.WARNINGS' | translate: {count: r.warnings.length} }}</h4>
        <div class="issues warnings" data-testid="migration-warnings">
          @for (issue of pagedWarnings(); track $index) {
            <div class="issue-row">
              <span class="row-num">{{ issue.row > 0 ? issue.row : '—' }}</span>
              <span class="col">{{ labels.column(entity(), issue.field) }}</span>
              <span class="msg">{{ labels.issue(issue, entity()) }}</span>
            </div>
          }
          <mat-paginator [length]="r.warnings.length" [pageIndex]="warningPage()" [pageSize]="pageSize()"
                         [pageSizeOptions]="[10, 20, 50, 100]" (page)="onWarningPage($event)"/>
        </div>
      }
    </section>
  `,
  styles: [`
    .report {
      display: flex;
      flex-direction: column;
      gap: 10px;
    }

    h4 {
      margin: 6px 0 0;
      font-size: 14px;
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

    .issues {
      border: 1px solid var(--app-border);
      border-radius: 8px;
      overflow: hidden;
    }

    .issues.warnings {
      border-color: #ffe0b2;
    }

    .issue-row {
      display: grid;
      grid-template-columns: 56px minmax(120px, 200px) 1fr;
      gap: 12px;
      padding: 8px 12px;
      border-bottom: 1px solid var(--app-border);
      font-size: 13px;
      align-items: center;
    }

    .row-num {
      font-weight: 700;
    }

    .col {
      color: var(--app-muted);
    }

    .mapping {
      display: inline-flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 8px;
      margin-left: 8px;
    }

    .mapping-field {
      width: 190px;
    }

    @media (max-width: 767px) {
      .issue-row {
        grid-template-columns: 44px 1fr;
      }

      .issue-row .msg {
        grid-column: 1 / -1;
      }
    }
  `],
})
export class MigrationReportComponent {
  readonly entity = input.required<MigrationEntityDef>();
  readonly run = input.required<MigrationRun>();
  /** Les correspondances ne sont proposées que tant que le lot est en cours. */
  readonly canMap = input(false);
  readonly mapValue = output<ValueMapping>();

  protected readonly labels = inject(MigrationLabels);
  /** Pages et choix repartent de zéro à chaque nouveau compte rendu. */
  protected readonly errorPage = linkedSignal(() => this.resetOn(0));
  protected readonly warningPage = linkedSignal(() => this.resetOn(0));
  protected readonly pageSize = signal(10);
  protected readonly chosen = linkedSignal<Record<string, string>>(() => this.resetOn({}));

  protected readonly pagedErrors = computed(() => this.page(this.run().errors, this.errorPage()));
  protected readonly pagedWarnings = computed(() => this.page(this.run().warnings, this.warningPage()));

  protected allowedValues(issue: ValidationIssue): string[] {
    return this.entity().columns.find((c) => c.key === issue.field)?.allowedValues ?? [];
  }

  protected mappingKey(issue: ValidationIssue): string {
    return `${issue.field}|${issue.params['source'] ?? issue.params['value']}`;
  }

  protected choose(issue: ValidationIssue, target: string): void {
    this.chosen.update((current) => ({...current, [this.mappingKey(issue)]: target}));
  }

  protected map(issue: ValidationIssue): void {
    const target = this.chosen()[this.mappingKey(issue)];
    if (!issue.field || !target) return;
    this.mapValue.emit({column: issue.field, source: issue.params['source'] ?? issue.params['value'], target});
  }

  protected onErrorPage(event: PageEvent): void {
    this.errorPage.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
  }

  protected onWarningPage(event: PageEvent): void {
    this.warningPage.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
  }

  private page<T>(items: T[], pageIndex: number): T[] {
    const start = pageIndex * this.pageSize();
    return items.slice(start, start + this.pageSize());
  }

  private resetOn<T>(value: T): T {
    this.run();
    return value;
  }
}




