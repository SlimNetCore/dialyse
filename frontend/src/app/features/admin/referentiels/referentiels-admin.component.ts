import {ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatChipsModule} from '@angular/material/chips';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatTableModule} from '@angular/material/table';
import {MatPaginatorModule, PageEvent} from '@angular/material/paginator';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatTooltipModule} from '@angular/material/tooltip';
import {MatDialog} from '@angular/material/dialog';
import {MatSnackBar} from '@angular/material/snack-bar';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {firstValueFrom} from 'rxjs';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {
  ReferentialEntry,
  ReferentialFieldDef,
  ReferentialKindDef
} from '../../../core/api/referential-admin-api.service';
import {ConfirmDialogComponent} from '../../../shared/confirm-dialog.component';
import {ReferentialAdminStore} from './state/referential-admin.store';
import {displayValue} from './referential-admin.util';
import {ReferentialLabels} from './referential-labels.service';
import {ReferentialEntryDialogComponent} from './referential-entry-dialog.component';
import {ReferentialImportDialogComponent} from './referential-import-dialog.component';

const SEARCH_DEBOUNCE_MS = 300;

/**
 * Paramétrage des référentiels du centre : forfaits, créneaux, salles, médecins traitants, générateurs,
 * caisses, agences, centres payeurs et transporteurs — saisie unitaire ou import CSV / Excel vérifié.
 */
@Component({
  selector: 'app-referentiels-admin',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButtonModule, MatIconModule, MatChipsModule, MatFormFieldModule, MatInputModule, MatTableModule,
    MatPaginatorModule, MatProgressBarModule, MatTooltipModule, TranslateModule],
  template: `
    <section class="app-page">
      <div class="app-hero-card">
        <span class="app-eyebrow">{{ 'ADMIN.REFERENTIALS.EYEBROW' | translate }}</span>
        <h1 class="app-section-title">{{ 'ADMIN.REFERENTIALS.TITLE' | translate }}</h1>
        <p class="app-section-copy">{{ 'ADMIN.REFERENTIALS.DESC' | translate }}</p>
      </div>

      <mat-chip-listbox class="kinds" [attr.aria-label]="'ADMIN.REFERENTIALS.KINDS_ARIA' | translate"
                        data-testid="referential-kinds">
        @for (kind of store.kinds(); track kind.slug) {
          <mat-chip-option [selected]="kind.slug === store.activeSlug()" (click)="select(kind)"
                           [attr.data-testid]="'kind-' + kind.slug">
            {{ labels.kind(kind) }}
          </mat-chip-option>
        }
      </mat-chip-listbox>

      @if (store.activeKind(); as kind) {
        <div class="toolbar">
          <mat-form-field appearance="outline" class="search" subscriptSizing="dynamic">
            <mat-icon matPrefix>search</mat-icon>
            <mat-label>{{ 'COMMON.SEARCH' | translate }}</mat-label>
            <input matInput [value]="searchDraft()" (input)="onSearch($any($event.target).value)"
                   data-testid="referential-search"/>
          </mat-form-field>
          <div class="toolbar-actions">
            <button mat-stroked-button type="button" (click)="openImport(kind)" data-testid="referential-import">
              <mat-icon>upload_file</mat-icon>
              {{ 'ADMIN.REFERENTIALS.IMPORT.BUTTON' | translate }}
            </button>
            <button mat-flat-button color="primary" type="button" (click)="openEditor(kind, null)"
                    data-testid="referential-add">
              <mat-icon>add</mat-icon>
              {{ 'ADMIN.REFERENTIALS.ADD' | translate }}
            </button>
          </div>
        </div>

        @if (store.error() && !dialogOpen()) {
          <p class="feedback error" role="alert">{{ store.error()! | translate }}</p>
        }
        @if (store.loading()) {
          <mat-progress-bar mode="indeterminate"/>
        }

        @if (!store.loading() && store.isEmpty()) {
          <div class="empty-state">
            <mat-icon>inventory_2</mat-icon>
            <p>{{ (store.search() ? 'ADMIN.REFERENTIALS.NO_MATCH' : 'ADMIN.REFERENTIALS.EMPTY') | translate }}</p>
          </div>
        } @else {
          <div class="table-wrapper">
            <table mat-table [dataSource]="store.rows()" class="ref-table" data-testid="referential-table">
              @for (field of kind.fields; track field.key) {
                <ng-container [matColumnDef]="field.key">
                  <th mat-header-cell *matHeaderCellDef>{{ labels.field(field) }}</th>
                  <td mat-cell *matCellDef="let row" [attr.data-label]="labels.field(field)">
                    @switch (field.type) {
                      @case ('ENUM') {
                        <span class="chip">{{ labels.enumValue(cell(row, field)) }}</span>
                      }
                      @case ('DECIMAL') {
                        {{ cell(row, field) || '-' }}
                      }
                      @default {
                        <span class="text" [matTooltip]="cell(row, field)">{{ cell(row, field) || '-' }}</span>
                      }
                    }
                  </td>
                </ng-container>
              }
              <ng-container matColumnDef="actions">
                <th mat-header-cell *matHeaderCellDef></th>
                <td mat-cell *matCellDef="let row" class="actions-cell">
                  <button mat-icon-button type="button" [matTooltip]="'COMMON.EDIT' | translate"
                          [attr.aria-label]="'COMMON.EDIT' | translate" (click)="openEditor(kind, row)">
                    <mat-icon>edit</mat-icon>
                  </button>
                  <button mat-icon-button type="button" color="warn" [matTooltip]="'COMMON.DELETE' | translate"
                          [attr.aria-label]="'COMMON.DELETE' | translate" (click)="confirmDelete(kind, row)">
                    <mat-icon>delete</mat-icon>
                  </button>
                </td>
              </ng-container>

              <tr mat-header-row *matHeaderRowDef="columns()"></tr>
              <tr mat-row *matRowDef="let row; columns: columns()"></tr>
            </table>
          </div>
        }
        <mat-paginator [length]="store.total()" [pageIndex]="store.pageIndex()" [pageSize]="store.pageSize()"
                       [pageSizeOptions]="[10, 20, 50, 100]" (page)="onPage($event)"/>
      }
    </section>
  `,
  styles: [`
    .kinds {
      display: block;
      margin: 16px 0;
    }

    .toolbar {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      margin-bottom: 12px;
    }

    .search {
      flex: 1 1 260px;
      max-width: 420px;
    }

    .toolbar-actions {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }

    .table-wrapper {
      overflow-x: auto;
      border: 1px solid var(--app-border);
      border-radius: 12px;
      background: var(--app-surface);
    }

    .ref-table {
      width: 100%;
    }

    .ref-table .mat-mdc-header-cell {
      color: var(--app-primary);
      font-weight: 700;
      white-space: nowrap;
    }

    .text {
      display: inline-block;
      max-width: 320px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      vertical-align: middle;
    }

    .chip {
      padding: 3px 10px;
      border-radius: 10px;
      font-size: 12px;
      font-weight: 600;
      background: color-mix(in srgb, var(--app-primary) 12%, white);
      color: var(--app-primary);
    }

    .actions-cell {
      white-space: nowrap;
      text-align: right;
    }

    .feedback.error {
      padding: 10px 16px;
      border-radius: 8px;
      background: #fce4ec;
      color: #c62828;
    }

    .empty-state {
      text-align: center;
      padding: 48px 0;
      color: var(--app-muted);
    }

    .empty-state mat-icon {
      font-size: 48px;
      width: 48px;
      height: 48px;
    }

    /* Mobile : chaque ligne devient une carte, chaque cellule affiche son libellé de colonne. */
    @media (max-width: 767px) {
      .search {
        max-width: none;
      }

      .toolbar-actions, .toolbar-actions button {
        width: 100%;
      }

      .ref-table thead {
        display: none;
      }

      .ref-table tr.mat-mdc-row {
        display: block;
        height: auto;
        padding: 8px 0;
        border-bottom: 1px solid var(--app-border);
      }

      .ref-table td.mat-mdc-cell {
        display: flex;
        justify-content: space-between;
        gap: 12px;
        min-height: 32px;
        border: 0;
        padding: 2px 12px;
      }

      .ref-table td.mat-mdc-cell[data-label]::before {
        content: attr(data-label);
        font-weight: 600;
        color: var(--app-muted);
      }

      .text {
        max-width: 60vw;
      }
    }
  `],
})
export class ReferentielsAdminComponent {
  protected readonly store = inject(ReferentialAdminStore);
  protected readonly labels = inject(ReferentialLabels);
  protected readonly searchDraft = signal('');
  protected readonly dialogOpen = signal(false);
  protected readonly columns = computed(() => [...(this.store.activeKind()?.fields.map((f) => f.key) ?? []), 'actions']);
  private readonly appShell = inject(AppShellStore);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly translate = inject(TranslateService);
  private searchTimer: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    // Centre actif (et changement de centre) : on repart des données du centre courant uniquement.
    effect(() => {
      const centerId = this.appShell.currentCenterId();
      if (!centerId) return;
      untracked(() => void this.init());
    });
  }

  protected select(kind: ReferentialKindDef): void {
    if (kind.slug === this.store.activeSlug()) return;
    this.searchDraft.set('');
    void this.store.selectKind(kind.slug);
  }

  protected cell(row: ReferentialEntry, field: ReferentialFieldDef): string {
    return displayValue(row, field);
  }

  protected onSearch(value: string): void {
    this.searchDraft.set(value);
    if (this.searchTimer) clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => void this.store.setSearch(value), SEARCH_DEBOUNCE_MS);
  }

  protected onPage(event: PageEvent): void {
    void this.store.setPagination(event.pageIndex, event.pageSize);
  }

  protected async openEditor(kind: ReferentialKindDef, entry: ReferentialEntry | null): Promise<void> {
    this.dialogOpen.set(true);
    const saved = await firstValueFrom(this.dialog.open(ReferentialEntryDialogComponent, {
      data: {kind, entry}, width: '680px', maxWidth: '95vw', autoFocus: 'first-tabbable',
    }).afterClosed());
    this.dialogOpen.set(false);
    this.store.clearFormIssues();
    if (saved) this.notify('ADMIN.REFERENTIALS.SAVED');
  }

  protected async openImport(kind: ReferentialKindDef): Promise<void> {
    this.dialogOpen.set(true);
    const imported = await firstValueFrom(this.dialog.open(ReferentialImportDialogComponent, {
      data: {kind}, width: '860px', maxWidth: '95vw',
    }).afterClosed());
    this.dialogOpen.set(false);
    this.store.clearImport();
    if (imported) void this.store.loadReferenceOptions();
  }

  protected async confirmDelete(kind: ReferentialKindDef, entry: ReferentialEntry): Promise<void> {
    const label = kind.fields.slice(0, 2).map((f) => displayValue(entry, f)).filter(Boolean).join(' · ');
    const confirmed = await firstValueFrom(this.dialog.open(ConfirmDialogComponent, {
      data: {
        title: this.translate.instant('ADMIN.REFERENTIALS.DELETE_TITLE'),
        message: this.translate.instant('ADMIN.REFERENTIALS.DELETE_MESSAGE', {label}),
        confirmLabel: this.translate.instant('COMMON.DELETE'),
        cancelLabel: this.translate.instant('COMMON.CANCEL'),
        color: 'warn',
        icon: 'delete',
      },
      maxWidth: '95vw',
    }).afterClosed());
    if (confirmed && await this.store.remove(entry.id)) this.notify('ADMIN.REFERENTIALS.DELETED');
  }

  private async init(): Promise<void> {
    this.store.resetForCenterChange();
    if (this.store.kinds().length === 0) await this.store.loadKinds();
    const slug = this.store.activeSlug();
    if (slug) await this.store.selectKind(slug);
  }

  private notify(key: string): void {
    this.snackBar.open(this.translate.instant(key), '', {duration: 2500});
  }
}

