import {ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked} from '@angular/core';
import {DatePipe} from '@angular/common';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatSelectModule} from '@angular/material/select';
import {MatTableModule} from '@angular/material/table';
import {MatPaginatorModule, PageEvent} from '@angular/material/paginator';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatTooltipModule} from '@angular/material/tooltip';
import {MatDialog} from '@angular/material/dialog';
import {MatSnackBar} from '@angular/material/snack-bar';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {firstValueFrom} from 'rxjs';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {MigrationBatch, ValueMapping} from '../../../core/api/migration-api.service';
import {ConfirmDialogComponent} from '../../../shared/confirm-dialog.component';
import {requiredValidator, SignalForm} from '../../../shared/forms/signal-form';
import {MigrationStore} from './state/migration.store';
import {MigrationLabels} from './migration-labels.service';
import {MigrationEntityCardComponent} from './migration-entity-card.component';

/** Date de début de reprise : aujourd'hui moins N années (AAAA-MM-JJ). */
export function reprisePeriodStart(years: number, today: Date = new Date()): string {
  const d = new Date(today.getFullYear() - years, today.getMonth(), today.getDate());
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

interface OpenBatchForm {
  libelle: string;
  sourceSystem: string;
  years: number;
}

/**
 * Reprise des données d'un système existant : un lot par centre ; pour chaque donnée (assurés, patients,
 * affectations…), modèle, vérification, correspondances de valeurs puis import ; clôture ou annulation du lot.
 */
@Component({
  selector: 'app-reprise-donnees',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatTableModule,
    MatPaginatorModule, MatProgressBarModule, MatTooltipModule, TranslateModule, MigrationEntityCardComponent],
  template: `
    <section class="app-page">
      <div class="app-hero-card">
        <span class="app-eyebrow">{{ 'ADMIN.MIGRATION.EYEBROW' | translate }}</span>
        <h1 class="app-section-title">{{ 'ADMIN.MIGRATION.TITLE' | translate }}</h1>
        <p class="app-section-copy">{{ 'ADMIN.MIGRATION.DESC' | translate }}</p>
      </div>

      @if (store.error(); as err) {
        <p class="feedback error" role="alert" data-testid="migration-error">{{ err | translate }}</p>
      }
      @if (store.loading()) {
        <mat-progress-bar mode="indeterminate"/>
      }

      @if (store.batch(); as batch) {
        <div class="batch-card" data-testid="migration-batch">
          <div class="batch-info">
            <h2>{{ batch.libelle }}</h2>
            <span class="chip" [class]="'chip status-' + batch.status" data-testid="migration-batch-status">
              {{ 'ADMIN.MIGRATION.BATCH_STATUS.' + batch.status | translate }}
            </span>
            <p class="meta">
              @if (batch.sourceSystem) {
                {{ 'ADMIN.MIGRATION.SOURCE' | translate }} : <strong>{{ batch.sourceSystem }}</strong> ·
              }
              @if (batch.dateDebutReprise) {
                {{ 'ADMIN.MIGRATION.PERIOD' | translate: {date: (batch.dateDebutReprise | date: 'dd/MM/yyyy')} }} ·
              }
              {{ 'ADMIN.MIGRATION.OPENED_BY' | translate: {user: batch.createdBy ?? '-', date: (batch.createdAt | date: 'dd/MM/yyyy HH:mm')} }}
            </p>
          </div>
          <div class="batch-actions">
            @if (store.batchOpen()) {
              <button mat-stroked-button color="warn" type="button" (click)="cancelBatch()" data-testid="migration-cancel">
                <mat-icon>undo</mat-icon>{{ 'ADMIN.MIGRATION.CANCEL_BATCH' | translate }}
              </button>
              <button mat-flat-button color="primary" type="button" (click)="closeBatch()"
                      [disabled]="store.importedCount() === 0" data-testid="migration-close"
                      [matTooltip]="store.importedCount() === 0 ? ('ADMIN.MIGRATION.CLOSE_HINT' | translate) : ''">
                <mat-icon>verified</mat-icon>{{ 'ADMIN.MIGRATION.CLOSE_BATCH' | translate }}
              </button>
            } @else {
              <button mat-flat-button color="primary" type="button" (click)="newBatch()" [disabled]="hasOpenBatch()">
                <mat-icon>add</mat-icon>{{ 'ADMIN.MIGRATION.NEW_BATCH' | translate }}
              </button>
            }
          </div>
        </div>

        <p class="order-hint"><mat-icon>info</mat-icon>{{ 'ADMIN.MIGRATION.ORDER_HINT' | translate }}</p>
        <div class="steps">
          @for (entity of store.entities(); track entity.slug) {
            <app-migration-entity-card [entity]="entity" [editable]="store.batchOpen()"/>
          }
        </div>
        <p class="next-lots"><mat-icon>schedule</mat-icon>{{ 'ADMIN.MIGRATION.NEXT_LOTS' | translate }}</p>
      } @else if (!store.loading()) {
        <form class="open-form" (submit)="$event.preventDefault(); openBatch()" data-testid="migration-open-form">
          <h2>{{ 'ADMIN.MIGRATION.OPEN_TITLE' | translate }}</h2>
          <p class="meta">{{ 'ADMIN.MIGRATION.OPEN_HELP' | translate }}</p>
          <div class="form-grid">
            <mat-form-field appearance="outline">
              <mat-label>{{ 'ADMIN.MIGRATION.LIBELLE' | translate }}</mat-label>
              <input matInput [value]="form.value().libelle" (input)="form.set('libelle', $any($event.target).value)"
                     (blur)="form.markTouched('libelle')" maxlength="200" data-testid="migration-libelle"/>
              @if (form.showError('libelle')) {
                <mat-error>{{ form.firstError('libelle')! | translate }}</mat-error>
              }
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>{{ 'ADMIN.MIGRATION.SOURCE' | translate }}</mat-label>
              <input matInput [value]="form.value().sourceSystem" maxlength="100"
                     (input)="form.set('sourceSystem', $any($event.target).value)"/>
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>{{ 'ADMIN.MIGRATION.YEARS' | translate }}</mat-label>
              <mat-select [value]="form.value().years" (selectionChange)="form.set('years', $event.value)">
                @for (n of yearOptions; track n) {
                  <mat-option [value]="n">{{ 'ADMIN.MIGRATION.YEARS_OPTION' | translate: {n} }}</mat-option>
                }
              </mat-select>
              <mat-hint>{{ 'ADMIN.MIGRATION.PERIOD' | translate: {date: (periodStart() | date: 'dd/MM/yyyy')} }}</mat-hint>
            </mat-form-field>
          </div>
          <button mat-flat-button color="primary" type="submit" data-testid="migration-open">
            <mat-icon>play_arrow</mat-icon>{{ 'ADMIN.MIGRATION.OPEN' | translate }}
          </button>
        </form>
      }

      @if (store.valueMappings().length > 0) {
        <h2 class="section-title">{{ 'ADMIN.MIGRATION.MAPPINGS_TITLE' | translate }}</h2>
        <div class="table-wrapper">
          <table mat-table [dataSource]="pagedMappings()" class="data-table" data-testid="migration-mappings">
            <ng-container matColumnDef="column">
              <th mat-header-cell *matHeaderCellDef>{{ 'ADMIN.MIGRATION.MAPPING_COLUMN' | translate }}</th>
              <td mat-cell *matCellDef="let m">{{ labels.column(null, m.column) }}</td>
            </ng-container>
            <ng-container matColumnDef="source">
              <th mat-header-cell *matHeaderCellDef>{{ 'ADMIN.MIGRATION.MAPPING_SOURCE' | translate }}</th>
              <td mat-cell *matCellDef="let m"><code>{{ m.source }}</code></td>
            </ng-container>
            <ng-container matColumnDef="target">
              <th mat-header-cell *matHeaderCellDef>{{ 'ADMIN.MIGRATION.MAPPING_TARGET' | translate }}</th>
              <td mat-cell *matCellDef="let m">{{ labels.value(m.target) }}</td>
            </ng-container>
            <ng-container matColumnDef="actions">
              <th mat-header-cell *matHeaderCellDef></th>
              <td mat-cell *matCellDef="let m" class="actions-cell">
                <button mat-icon-button type="button" color="warn" [attr.aria-label]="'COMMON.DELETE' | translate"
                        [matTooltip]="'COMMON.DELETE' | translate" (click)="deleteMapping(m)">
                  <mat-icon>delete</mat-icon>
                </button>
              </td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="mappingColumns"></tr>
            <tr mat-row *matRowDef="let row; columns: mappingColumns"></tr>
          </table>
          <mat-paginator [length]="store.valueMappings().length" [pageIndex]="mappingPage()" [pageSize]="mappingPageSize()"
                         [pageSizeOptions]="[10, 20, 50, 100]" (page)="onMappingPage($event)"/>
        </div>
      }

      @if (store.historyTotal() > 0) {
        <h2 class="section-title">{{ 'ADMIN.MIGRATION.HISTORY_TITLE' | translate }}</h2>
        <div class="table-wrapper">
          <table mat-table [dataSource]="store.history()" class="data-table" data-testid="migration-history">
            <ng-container matColumnDef="libelle">
              <th mat-header-cell *matHeaderCellDef>{{ 'ADMIN.MIGRATION.LIBELLE' | translate }}</th>
              <td mat-cell *matCellDef="let b">{{ b.libelle }}</td>
            </ng-container>
            <ng-container matColumnDef="status">
              <th mat-header-cell *matHeaderCellDef>{{ 'ADMIN.MIGRATION.STATUS_COL' | translate }}</th>
              <td mat-cell *matCellDef="let b">
                <span [class]="'chip status-' + b.status">{{ 'ADMIN.MIGRATION.BATCH_STATUS.' + b.status | translate }}</span>
              </td>
            </ng-container>
            <ng-container matColumnDef="createdAt">
              <th mat-header-cell *matHeaderCellDef>{{ 'ADMIN.MIGRATION.CREATED_AT' | translate }}</th>
              <td mat-cell *matCellDef="let b">{{ b.createdAt | date: 'dd/MM/yyyy HH:mm' }}</td>
            </ng-container>
            <ng-container matColumnDef="closedAt">
              <th mat-header-cell *matHeaderCellDef>{{ 'ADMIN.MIGRATION.CLOSED_AT' | translate }}</th>
              <td mat-cell *matCellDef="let b">{{ b.closedAt ? (b.closedAt | date: 'dd/MM/yyyy HH:mm') : '-' }}</td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="historyColumns"></tr>
            <tr mat-row *matRowDef="let row; columns: historyColumns" class="clickable"
                [class.selected]="row.id === store.batch()?.id" (click)="select(row)"></tr>
          </table>
          <mat-paginator [length]="store.historyTotal()" [pageIndex]="store.historyPageIndex()"
                         [pageSize]="store.historyPageSize()" [pageSizeOptions]="[10, 20, 50, 100]"
                         (page)="onHistoryPage($event)"/>
        </div>
      }
    </section>
  `,
  styles: [`
    .feedback.error {
      padding: 10px 16px;
      border-radius: 8px;
      background: #fce4ec;
      color: #c62828;
    }

    .batch-card, .open-form {
      display: flex;
      flex-wrap: wrap;
      justify-content: space-between;
      gap: 16px;
      padding: 16px 20px;
      margin: 16px 0;
      border: 1px solid var(--app-border);
      border-radius: 16px;
      background: var(--app-surface);
    }

    .open-form {
      flex-direction: column;
      align-items: flex-start;
    }

    .batch-info h2, .open-form h2 {
      display: inline;
      margin: 0 12px 0 0;
      font-size: 18px;
    }

    .meta {
      margin: 6px 0 0;
      color: var(--app-muted);
      font-size: 13px;
    }

    .batch-actions {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 8px;
    }

    .chip {
      padding: 3px 10px;
      border-radius: 10px;
      font-size: 12px;
      font-weight: 600;
      background: var(--app-surface-soft, #f1f5f9);
    }

    .chip.status-EN_COURS { background: color-mix(in srgb, var(--app-primary) 12%, white); color: var(--app-primary); }
    .chip.status-TERMINE { background: #e8f5e9; color: #1b5e20; }
    .chip.status-ANNULE { background: #fce4ec; color: #c62828; }

    .form-grid {
      display: grid;
      grid-template-columns: repeat(3, minmax(0, 1fr));
      gap: 12px;
      width: 100%;
    }

    .order-hint, .next-lots {
      display: flex;
      align-items: center;
      gap: 8px;
      color: var(--app-muted);
      font-size: 13px;
    }

    .steps {
      display: flex;
      flex-direction: column;
      gap: 12px;
    }

    .section-title {
      margin: 28px 0 10px;
      font-size: 17px;
    }

    .table-wrapper {
      overflow-x: auto;
      border: 1px solid var(--app-border);
      border-radius: 12px;
      background: var(--app-surface);
    }

    .data-table {
      width: 100%;
    }

    .data-table .mat-mdc-header-cell {
      color: var(--app-primary);
      font-weight: 700;
    }

    .clickable {
      cursor: pointer;
    }

    .selected {
      background: color-mix(in srgb, var(--app-primary) 8%, transparent);
    }

    .actions-cell {
      text-align: right;
    }

    @media (max-width: 767px) {
      .form-grid {
        grid-template-columns: 1fr;
      }

      .batch-actions, .batch-actions button, .open-form button {
        width: 100%;
      }
    }
  `],
})
export class RepriseDonneesComponent {
  protected readonly store = inject(MigrationStore);
  protected readonly labels = inject(MigrationLabels);
  protected readonly yearOptions = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];
  protected readonly historyColumns = ['libelle', 'status', 'createdAt', 'closedAt'];
  protected readonly mappingColumns = ['column', 'source', 'target', 'actions'];
  protected readonly form = new SignalForm<OpenBatchForm>(
    {libelle: '', sourceSystem: '', years: 3},
    {libelle: [requiredValidator()]},
  );
  protected readonly periodStart = computed(() => reprisePeriodStart(this.form.value().years));
  protected readonly hasOpenBatch = computed(() => this.store.history().some((b) => b.status === 'EN_COURS'));
  protected readonly mappingPage = signal(0);
  protected readonly mappingPageSize = signal(10);
  protected readonly pagedMappings = computed(() => {
    const start = this.mappingPage() * this.mappingPageSize();
    return this.store.valueMappings().slice(start, start + this.mappingPageSize());
  });
  private readonly appShell = inject(AppShellStore);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly translate = inject(TranslateService);

  constructor() {
    effect(() => {
      if (!this.appShell.currentCenterId()) return;
      untracked(() => void this.store.init());
    });
  }

  protected async openBatch(): Promise<void> {
    this.form.markAllTouched();
    if (this.form.invalid()) return;
    const value = this.form.value();
    if (await this.store.open({
      libelle: value.libelle.trim(),
      sourceSystem: value.sourceSystem.trim() || null,
      dateDebutReprise: this.periodStart(),
    })) {
      this.form.reset();
      this.notify('ADMIN.MIGRATION.OPENED');
    }
  }

  protected newBatch(): void {
    this.store.deselect();
  }

  protected async closeBatch(): Promise<void> {
    if (await this.confirm('ADMIN.MIGRATION.CLOSE_CONFIRM_TITLE', 'ADMIN.MIGRATION.CLOSE_CONFIRM', 'primary', 'verified')
      && await this.store.close()) {
      this.notify('ADMIN.MIGRATION.CLOSED');
    }
  }

  protected async cancelBatch(): Promise<void> {
    if (await this.confirm('ADMIN.MIGRATION.CANCEL_CONFIRM_TITLE', 'ADMIN.MIGRATION.CANCEL_CONFIRM', 'warn', 'undo')
      && await this.store.cancel()) {
      this.notify('ADMIN.MIGRATION.CANCELLED');
    }
  }

  protected select(batch: MigrationBatch): void {
    void this.store.select(batch.id);
  }

  protected deleteMapping(mapping: ValueMapping): void {
    void this.store.deleteValueMapping(mapping);
  }

  protected onHistoryPage(event: PageEvent): void {
    void this.store.setHistoryPage(event.pageIndex, event.pageSize);
  }

  protected onMappingPage(event: PageEvent): void {
    this.mappingPage.set(event.pageIndex);
    this.mappingPageSize.set(event.pageSize);
  }

  private async confirm(titleKey: string, messageKey: string, color: 'warn' | 'primary', icon: string): Promise<boolean> {
    return !!await firstValueFrom(this.dialog.open(ConfirmDialogComponent, {
      data: {
        title: this.translate.instant(titleKey),
        message: this.translate.instant(messageKey),
        confirmLabel: this.translate.instant('COMMON.OK'),
        cancelLabel: this.translate.instant('COMMON.CANCEL'),
        color,
        icon,
      },
      maxWidth: '95vw',
    }).afterClosed());
  }

  private notify(key: string): void {
    this.snackBar.open(this.translate.instant(key), '', {duration: 2500});
  }
}


