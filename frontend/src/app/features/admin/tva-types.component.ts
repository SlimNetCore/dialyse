import {ChangeDetectionStrategy, Component, effect, inject, signal} from '@angular/core';
import {FormsModule, NgForm} from '@angular/forms';
import {MatTableModule} from '@angular/material/table';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatDatepickerModule} from '@angular/material/datepicker';
import {MatNativeDateModule} from '@angular/material/core';
import {MatSlideToggleModule} from '@angular/material/slide-toggle';
import {MatPaginatorModule, PageEvent} from '@angular/material/paginator';
import {MatCardModule} from '@angular/material/card';
import {MatChipsModule} from '@angular/material/chips';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatTooltipModule} from '@angular/material/tooltip';
import {MatSelectModule} from '@angular/material/select';
import {RouterLink} from '@angular/router';
import {TranslateModule} from '@ngx-translate/core';
import {AppShellStore} from '../../core/state/app-shell.store';
import {AuthStore} from '../../core/state/auth.store';
import {TvaTypesStore} from './state/tva-types.store';
import {TvaType} from '../../core/api/backend-api.service';

const TYPES_PRESTATION = ['HEMODIALYSE', 'CONSOMMABLE', 'MEDICAMENT', 'FORFAIT_GLOBAL', 'AUTRE'];

@Component({
  selector: 'app-tva-types',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule, MatTableModule, MatButtonModule, MatIconModule,
    MatFormFieldModule, MatInputModule, MatDatepickerModule, MatNativeDateModule,
    MatSlideToggleModule, MatPaginatorModule, MatCardModule, MatChipsModule,
    MatProgressBarModule, MatTooltipModule, MatSelectModule,
    RouterLink, TranslateModule
  ],
  template: `
    <section class="app-page">
      <!-- Hero -->
      <div class="app-hero-card">
        <span class="app-eyebrow">{{ 'ADMIN.BILLING_SETTINGS.EYEBROW' | translate }}</span>
        <h1 class="app-section-title">{{ 'TVA_TYPES.TITLE' | translate }}</h1>
        <p class="app-section-copy">{{ 'TVA_TYPES.DESC' | translate }}</p>
        <div class="app-button-cluster">
          <a mat-stroked-button routerLink="/admin/parametrage/facturation">
            <mat-icon>arrow_back</mat-icon>
            {{ 'TVA_TYPES.BACK' | translate }}
          </a>
          <button mat-flat-button color="primary" (click)="openCreate()">
            <mat-icon>add</mat-icon>
            {{ 'TVA_TYPES.ADD' | translate }}
          </button>
        </div>
      </div>

      <!-- Messages -->
      @if (store.successMessage()) {
        <p class="feedback success">{{ store.successMessage()! | translate }}</p>
      }
      @if (store.error()) {
        <p class="feedback error">{{ store.error()! | translate }}</p>
      }

      <!-- Formulaire création / édition -->
      @if (showForm()) {
        <mat-card class="form-card">
          <mat-card-header>
            <mat-card-title>
              {{ (editingId() ? 'TVA_TYPES.FORM.EDIT_TITLE' : 'TVA_TYPES.FORM.CREATE_TITLE') | translate }}
            </mat-card-title>
          </mat-card-header>
          <mat-card-content>
            <form #f="ngForm" (ngSubmit)="onSubmit(f)" class="tva-form">
              <div class="form-grid">
                <mat-form-field appearance="outline">
                  <mat-label>{{ 'TVA_TYPES.FORM.LIBELLE' | translate }}</mat-label>
                  <input matInput name="libelle" [(ngModel)]="formData.libelle" required
                         [placeholder]="'TVA_TYPES.FORM.LIBELLE_PLACEHOLDER' | translate"/>
                </mat-form-field>

                <mat-form-field appearance="outline">
                  <mat-label>{{ 'TVA_TYPES.FORM.TAUX' | translate }}</mat-label>
                  <input matInput type="number" name="taux" [(ngModel)]="formData.taux"
                         min="0" max="100" step="0.01" required/>
                  <span matSuffix>%</span>
                </mat-form-field>

                <mat-form-field appearance="outline">
                  <mat-label>{{ 'TVA_TYPES.FORM.TYPE_PRESTATION' | translate }}</mat-label>
                  <mat-select name="typePrestation" [(ngModel)]="formData.typePrestation" required>
                    @for (tp of typesPrestation; track tp) {
                      <mat-option [value]="tp">{{ tp }}</mat-option>
                    }
                  </mat-select>
                </mat-form-field>

                <mat-form-field appearance="outline">
                  <mat-label>{{ 'TVA_TYPES.FORM.DATE_DEBUT' | translate }}</mat-label>
                  <input matInput [matDatepicker]="dpDebut" name="dateDebutValidite"
                         [(ngModel)]="formData.dateDebutValidite" required/>
                  <mat-datepicker-toggle matSuffix [for]="dpDebut"/>
                  <mat-datepicker #dpDebut/>
                </mat-form-field>

                <mat-form-field appearance="outline">
                  <mat-label>{{ 'TVA_TYPES.FORM.DATE_FIN' | translate }}</mat-label>
                  <input matInput [matDatepicker]="dpFin" name="dateFinValidite"
                         [(ngModel)]="formData.dateFinValidite"/>
                  <mat-datepicker-toggle matSuffix [for]="dpFin"/>
                  <mat-datepicker #dpFin/>
                  <mat-hint>{{ 'TVA_TYPES.FORM.DATE_FIN_HINT' | translate }}</mat-hint>
                </mat-form-field>

                <mat-form-field appearance="outline" class="full-width">
                  <mat-label>{{ 'TVA_TYPES.FORM.TEXTE_REFERENCE' | translate }}</mat-label>
                  <input matInput name="texteReference" [(ngModel)]="formData.texteReference"
                         [placeholder]="'TVA_TYPES.FORM.TEXTE_REFERENCE_PLACEHOLDER' | translate"/>
                </mat-form-field>
              </div>

              <div class="toggle-row">
                <mat-slide-toggle name="exonere" [(ngModel)]="formData.exonere">
                  {{ 'TVA_TYPES.FORM.EXONERE' | translate }}
                </mat-slide-toggle>
              </div>

              <div class="form-actions">
                <button mat-stroked-button type="button" (click)="cancelForm()">
                  {{ 'COMMON.CANCEL' | translate }}
                </button>
                <button mat-flat-button color="primary" type="submit"
                        [disabled]="f.invalid || store.saving()">
                  <mat-icon>save</mat-icon>
                  {{ 'COMMON.SAVE' | translate }}
                </button>
              </div>

              @if (store.saving()) {
                <mat-progress-bar mode="indeterminate"/>
              }
            </form>
          </mat-card-content>
        </mat-card>
      }

      <!-- Table -->
      @if (store.loading() && store.isEmpty()) {
        <mat-progress-bar mode="indeterminate"/>
      }

      @if (!store.loading() && store.isEmpty() && !showForm()) {
        <div class="empty-state">
          <mat-icon>percent</mat-icon>
          <p>{{ 'TVA_TYPES.EMPTY' | translate }}</p>
          <button mat-flat-button color="primary" (click)="openCreate()">
            <mat-icon>add</mat-icon>
            {{ 'TVA_TYPES.ADD' | translate }}
          </button>
        </div>
      }

      @if (!store.isEmpty()) {
        <div class="table-wrapper">
          <table mat-table [dataSource]="store.rows()" class="tva-table">

            <ng-container matColumnDef="libelle">
              <th mat-header-cell *matHeaderCellDef>{{ 'TVA_TYPES.COL.LIBELLE' | translate }}</th>
              <td mat-cell *matCellDef="let row">{{ row.libelle }}</td>
            </ng-container>

            <ng-container matColumnDef="taux">
              <th mat-header-cell *matHeaderCellDef>{{ 'TVA_TYPES.COL.TAUX' | translate }}</th>
              <td mat-cell *matCellDef="let row">
                <span class="taux-badge">{{ row.taux }}%</span>
              </td>
            </ng-container>

            <ng-container matColumnDef="typePrestation">
              <th mat-header-cell *matHeaderCellDef>{{ 'TVA_TYPES.COL.TYPE_PRESTATION' | translate }}</th>
              <td mat-cell *matCellDef="let row">
                <mat-chip>{{ row.typePrestation }}</mat-chip>
              </td>
            </ng-container>

            <ng-container matColumnDef="periode">
              <th mat-header-cell *matHeaderCellDef>{{ 'TVA_TYPES.COL.PERIODE' | translate }}</th>
              <td mat-cell *matCellDef="let row">
                <span>{{ row.dateDebutValidite }}</span>
                @if (row.dateFinValidite) {
                  <span class="date-sep"> → {{ row.dateFinValidite }}</span>
                } @else {
                  <span class="date-sep date-open"> → {{ 'TVA_TYPES.OPEN_ENDED' | translate }}</span>
                }
              </td>
            </ng-container>

            <ng-container matColumnDef="exonere">
              <th mat-header-cell *matHeaderCellDef>{{ 'TVA_TYPES.COL.EXONERE' | translate }}</th>
              <td mat-cell *matCellDef="let row">
                @if (row.exonere) {
                  <mat-chip class="chip-warn">{{ 'COMMON.YES' | translate }}</mat-chip>
                } @else {
                  <span class="muted">{{ 'COMMON.NO' | translate }}</span>
                }
              </td>
            </ng-container>

            <ng-container matColumnDef="actif">
              <th mat-header-cell *matHeaderCellDef>{{ 'TVA_TYPES.COL.STATUT' | translate }}</th>
              <td mat-cell *matCellDef="let row">
                @if (row.actif) {
                  <mat-chip class="chip-active">{{ 'TVA_TYPES.ACTIF' | translate }}</mat-chip>
                } @else {
                  <mat-chip class="chip-inactive">{{ 'TVA_TYPES.INACTIF' | translate }}</mat-chip>
                }
              </td>
            </ng-container>

            <ng-container matColumnDef="actions">
              <th mat-header-cell *matHeaderCellDef></th>
              <td mat-cell *matCellDef="let row">
                <div class="row-actions">
                  <button mat-icon-button [matTooltip]="'COMMON.EDIT' | translate" (click)="openEdit(row)">
                    <mat-icon>edit</mat-icon>
                  </button>
                  @if (row.actif) {
                    <button mat-icon-button color="warn"
                            [matTooltip]="'TVA_TYPES.DEACTIVATE' | translate"
                            [disabled]="store.deleting()"
                            (click)="deactivate(row)">
                      <mat-icon>block</mat-icon>
                    </button>
                  }
                </div>
              </td>
            </ng-container>

            <tr mat-header-row *matHeaderRowDef="columns"></tr>
            <tr mat-row *matRowDef="let row; columns: columns" [class.row-inactive]="!row.actif"></tr>
          </table>

          <mat-paginator
            [length]="store.total()"
            [pageIndex]="store.pageIndex()"
            [pageSize]="store.pageSize()"
            [pageSizeOptions]="[10, 20, 50, 100]"
            (page)="onPage($event)"/>
        </div>
      }
    </section>
  `,
  styles: [`
    .tva-form {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

    .form-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
      gap: 12px;
    }

    .full-width {
      grid-column: 1 / -1;
    }

    .toggle-row {
      margin: 8px 0;
    }

    .form-actions {
      display: flex;
      gap: 8px;
      justify-content: flex-end;
      margin-top: 8px;
    }

    .form-card {
      margin-bottom: 24px;
    }

    .table-wrapper {
      overflow-x: auto;
      border: 1px solid var(--app-border);
      border-radius: 12px;
      background: var(--app-surface);
    }

    .tva-table {
      width: 100%;
    }

    .tva-table .mat-mdc-header-cell {
      color: var(--app-primary);
      font-weight: 700;
    }

    .taux-badge {
      background: color-mix(in srgb, var(--app-primary) 12%, white);
      color: var(--app-primary);
      padding: 3px 10px;
      border-radius: 10px;
      font-weight: 700;
    }

    .date-sep {
      color: var(--app-muted);
      margin-left: 4px;
    }

    .date-open {
      color: var(--app-primary);
    }

    .chip-active {
      background: #e8f5e9 !important;
      color: #1b5e20 !important;
    }

    .chip-inactive {
      background: #fce4ec !important;
      color: #c62828 !important;
    }

    .chip-warn {
      background: #fff3e0 !important;
      color: #e65100 !important;
    }

    .row-inactive {
      opacity: 0.55;
    }

    .row-actions {
      display: flex;
      gap: 4px;
    }

    .muted {
      color: var(--app-muted);
      font-size: 13px;
    }

    .feedback {
      padding: 10px 16px;
      border-radius: 8px;
      margin-bottom: 12px;
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

    .empty-state {
      text-align: center;
      padding: 64px 0;
      color: var(--app-muted);
    }

    .empty-state mat-icon {
      font-size: 56px;
      width: 56px;
      height: 56px;
      margin-bottom: 12px;
    }

    @media (max-width: 767px) {
      .form-grid {
        grid-template-columns: 1fr;
      }
      .full-width {
        grid-column: 1;
      }
    }
  `]
})
export class TvaTypesComponent {
  readonly columns = ['libelle', 'taux', 'typePrestation', 'periode', 'exonere', 'actif', 'actions'];
  readonly typesPrestation = TYPES_PRESTATION;
  readonly showForm = signal(false);
  readonly editingId = signal<string | null>(null);
  formData: {
    libelle: string;
    taux: number;
    typePrestation: string;
    exonere: boolean;
    dateDebutValidite: Date | null;
    dateFinValidite: Date | null;
    texteReference: string;
  } = this.defaultFormData();
  protected readonly store = inject(TvaTypesStore);
  private readonly appShell = inject(AppShellStore);
  private readonly auth = inject(AuthStore);

  constructor() {
    effect(() => {
      const centerId = this.appShell.currentCenterId();
      if (!centerId) return;
      this.load(centerId, this.store.pageIndex(), this.store.pageSize());
    });

    // Recharger après create/update/delete
    effect(() => {
      const msg = this.store.successMessage();
      if (!msg) return;
      const cid = this.appShell.currentCenterId();
      if (cid) this.load(cid, this.store.pageIndex(), this.store.pageSize());
    });
  }

  openCreate(): void {
    this.formData = this.defaultFormData();
    this.editingId.set(null);
    this.showForm.set(true);
    this.store.clearMessages();
  }

  openEdit(row: TvaType): void {
    this.formData = {
      libelle: row.libelle,
      taux: row.taux,
      typePrestation: row.typePrestation,
      exonere: row.exonere,
      dateDebutValidite: row.dateDebutValidite ? new Date(row.dateDebutValidite) : null,
      dateFinValidite: row.dateFinValidite ? new Date(row.dateFinValidite) : null,
      texteReference: row.texteReference ?? ''
    };
    this.editingId.set(row.id);
    this.showForm.set(true);
    this.store.clearMessages();
  }

  cancelForm(): void {
    this.showForm.set(false);
    this.editingId.set(null);
    this.store.clearMessages();
  }

  onSubmit(f: NgForm): void {
    if (f.invalid) return;
    const centerId = this.appShell.currentCenterId()!;
    const userId = this.auth.username() ?? 'admin';
    const toDateStr = (d: Date | null): string | undefined =>
      d ? new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10) : undefined;

    const payload = {
      centerId,
      libelle: this.formData.libelle,
      taux: this.formData.taux,
      typePrestation: this.formData.typePrestation,
      exonere: this.formData.exonere,
      dateDebutValidite: toDateStr(this.formData.dateDebutValidite)!,
      dateFinValidite: toDateStr(this.formData.dateFinValidite) ?? null,
      texteReference: this.formData.texteReference || null,
      userId
    };

    const id = this.editingId();
    if (id) {
      this.store.updateTvaType({id, payload});
    } else {
      this.store.createTvaType(payload);
    }
    this.showForm.set(false);
  }

  deactivate(row: TvaType): void {
    const centerId = this.appShell.currentCenterId()!;
    this.store.deleteTvaType({id: row.id, centerId});
  }

  onPage(event: PageEvent): void {
    const centerId = this.appShell.currentCenterId();
    if (!centerId) return;
    this.store.setPagination(event.pageIndex, event.pageSize);
    this.load(centerId, event.pageIndex, event.pageSize);
  }

  private load(centerId: string, page: number, size: number): void {
    this.store.loadPage({centerId, page, size});
  }

  private defaultFormData() {
    return {
      libelle: '',
      taux: 0,
      typePrestation: 'HEMODIALYSE',
      exonere: false,
      dateDebutValidite: null as Date | null,
      dateFinValidite: null as Date | null,
      texteReference: ''
    };
  }
}


