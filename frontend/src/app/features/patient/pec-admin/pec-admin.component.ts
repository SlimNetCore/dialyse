import {ChangeDetectionStrategy, Component, computed, inject, OnInit, signal} from '@angular/core';
import {MatTableModule} from '@angular/material/table';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatChipsModule} from '@angular/material/chips';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatDatepickerModule} from '@angular/material/datepicker';
import {MatNativeDateModule} from '@angular/material/core';
import {MatSnackBar, MatSnackBarModule} from '@angular/material/snack-bar';
import {MatCardModule} from '@angular/material/card';
import {SlicePipe} from '@angular/common';
import {compatForm} from '@angular/forms/signals/compat';
import {FormField, FormRoot, required} from '@angular/forms/signals';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {AuthStore} from '../../../core/state/auth.store';
import {SearchableSelectComponent} from '../../../shared/searchable-select.component';
import {PecAdminStore} from './state/pec-admin.store';

@Component({
  selector: 'app-pec-admin',
  standalone: true,
  imports: [
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatSnackBarModule,
    MatCardModule,
    SlicePipe,
    TranslateModule,
    SearchableSelectComponent,
    FormRoot,
    FormField,
  ],
  template: `
    <div class="pec-admin">
      <!-- Header with center info -->
      <div class="page-header">
        <div class="header-left">
          <mat-icon class="header-icon">verified</mat-icon>
          <div>
            <h2>{{ 'PEC_ADMIN.TITLE' | translate }}</h2>
            <p class="desc">{{ 'PEC_ADMIN.DESC' | translate }}</p>
          </div>
        </div>
        <mat-card class="center-card">
          <div class="center-info">
            <mat-icon>business</mat-icon>
            <div>
              <span class="center-label">{{ 'PEC_ADMIN.CENTER' | translate }}</span>
              <span class="center-name">{{ auth.centerName() || '—' }}</span>
            </div>
          </div>
        </mat-card>
      </div>

      @if (loading()) {
        <p style="color:#888; text-align: center; padding: 32px;">
          {{ 'PEC_ADMIN.LOADING' | translate }}
        </p>
      }

      @if (!loading() && pecs().length === 0) {
        <div class="empty-state">
          <mat-icon>assignment_turned_in</mat-icon>
          <p>{{ 'PEC_ADMIN.EMPTY' | translate }}</p>
        </div>
      }

      @if (pecs().length > 0) {
        <div class="table-wrap">
          <table mat-table [dataSource]="pecs()" class="pec-table">
            <ng-container matColumnDef="patientId">
              <th mat-header-cell *matHeaderCellDef>{{ 'PEC_ADMIN.COL_PATIENT' | translate }}</th>
              <td mat-cell *matCellDef="let r">{{ r.patientId | slice: 0 : 8 }}…</td>
            </ng-container>
            <ng-container matColumnDef="dateDebutDemande">
              <th mat-header-cell *matHeaderCellDef>{{ 'PEC_ADMIN.COL_DEBUT' | translate }}</th>
              <td mat-cell *matCellDef="let r">{{ r.dateDebutDemande }}</td>
            </ng-container>
            <ng-container matColumnDef="dateFinDemande">
              <th mat-header-cell *matHeaderCellDef>{{ 'PEC_ADMIN.COL_FIN' | translate }}</th>
              <td mat-cell *matCellDef="let r">{{ r.dateFinDemande }}</td>
            </ng-container>
            <ng-container matColumnDef="status">
              <th mat-header-cell *matHeaderCellDef>{{ 'PEC_ADMIN.COL_STATUS' | translate }}</th>
              <td mat-cell *matCellDef="let r">
                <span class="status-badge" [class]="'badge-' + r.status?.toLowerCase()">{{
                    r.status
                  }}</span>
              </td>
            </ng-container>
            <ng-container matColumnDef="actions">
              <th mat-header-cell *matHeaderCellDef>{{ 'PEC_ADMIN.COL_ACTIONS' | translate }}</th>
              <td mat-cell *matCellDef="let r">
                @if (r.status === 'CREE') {
                  <button mat-flat-button class="validate-btn" (click)="openValidate(r)">
                    <mat-icon>check_circle</mat-icon>
                    {{ 'PEC_ADMIN.VALIDATE' | translate }}
                  </button>
                }
                @if (r.status === 'VALIDEE') {
                  <button mat-stroked-button color="warn" (click)="closePec(r)">
                    <mat-icon>cancel</mat-icon>
                    {{ 'PEC_ADMIN.CLOSE' | translate }}
                  </button>
                }
              </td>
            </ng-container>

            <tr mat-header-row *matHeaderRowDef="columns"></tr>
            <tr mat-row *matRowDef="let row; columns: columns" [attr.data-row-id]="row?.id"></tr>
          </table>
        </div>
      }

      <!-- Inline validation form -->
      @if (validatingPec()) {
        <mat-card class="validate-card">
          <mat-card-header
          >
            <mat-card-title>{{
                'PEC_ADMIN.VALIDATE_TITLE' | translate
              }}
            </mat-card-title>
          </mat-card-header
          >
          <mat-card-content>
            <form [formRoot]="validateForm">
              <div class="form-row">
                <mat-form-field appearance="outline" class="flex1">
                  <mat-label>{{ 'PEC_ADMIN.DATE_DEBUT_EFFECTIF' | translate }}</mat-label>
                  <input matInput [matDatepicker]="dpDeb" [formField]="validateForm.dateDebutEffectif" />
                  <mat-datepicker-toggle matSuffix [for]="dpDeb" /><mat-datepicker #dpDeb />
                </mat-form-field>
                <mat-form-field appearance="outline" class="flex1">
                  <mat-label>{{ 'PEC_ADMIN.DATE_FIN_EFFECTIF' | translate }}</mat-label>
                  <input matInput [matDatepicker]="dpFin" [formField]="validateForm.dateFinEffectif" />
                  <mat-datepicker-toggle matSuffix [for]="dpFin" /><mat-datepicker #dpFin />
                </mat-form-field>
                <app-searchable-select
                  [items]="forfaits()"
                  [label]="'PEC_ADMIN.FORFAIT_EFFECTIF' | translate"
                  [selectedId]="validateModel().forfaitEffectifId"
                  (selectionChanged)="setForfait($event?.id ?? null)"
                  cssClass="flex1"
                />
              </div>
              <div class="form-row" style="justify-content: flex-end; gap: 8px;">
                <button mat-stroked-button (click)="cancelValidate()">
                  {{ 'PEC_ADMIN.CANCEL' | translate }}
                </button>
                <button
                  mat-flat-button
                  class="validate-btn"
                  type="button"
                  (click)="confirmValidate()"
                  [disabled]="validateDisabled()"
                >
                  <mat-icon>check</mat-icon> {{ 'PEC_ADMIN.CONFIRM' | translate }}
                </button>
              </div>
            </form>
          </mat-card-content>
        </mat-card>
      }
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.Eager,
  styles: [
    `
      .pec-admin {
        max-width: 1160px;
        margin: 0 auto;
      }

      .page-header {
        display: flex;
        justify-content: space-between;
        align-items: flex-start;
        margin-bottom: 24px;
        gap: 16px;
      }

      .header-left {
        display: flex;
        gap: 12px;
        align-items: flex-start;
      }

      .header-icon {
        font-size: 32px;
        width: 32px;
        height: 32px;
        color: var(--app-primary);
        margin-top: 4px;
      }

      h2 {
        color: var(--app-text);
        margin: 0 0 4px;
        font-size: 1.25rem;
      }

      .desc {
        color: var(--app-muted);
        margin: 0;
        font-size: 14px;
      }

      .center-card {
        padding: 12px 20px !important;
        background: var(--app-surface) !important;
        border: 1px solid var(--app-border);
        box-shadow: var(--app-shadow);
      }

      .center-info {
        display: flex;
        align-items: center;
        gap: 10px;
      }

      .center-info mat-icon {
        color: var(--app-primary);
        font-size: 28px;
        width: 28px;
        height: 28px;
      }

      .center-label {
        display: block;
        font-size: 11px;
        color: var(--app-muted);
        text-transform: uppercase;
        letter-spacing: 0.5px;
      }

      .center-name {
        display: block;
        font-weight: 600;
        color: var(--app-primary);
        font-size: 15px;
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
        margin-bottom: 8px;
      }

      .table-wrap {
        overflow: auto;
        border: 1px solid var(--app-border);
        border-radius: 12px;
        background: var(--app-surface);
      }

      .pec-table {
        width: 100%;
      }

      .pec-table .mat-mdc-header-cell {
        color: var(--app-primary);
        font-weight: 700;
      }

      .pec-table .mat-mdc-row:hover {
        background: color-mix(in srgb, var(--app-primary-soft) 70%, white);
      }

      .status-badge {
        display: inline-block;
        padding: 4px 12px;
        border-radius: 12px;
        font-size: 12px;
        font-weight: 600;
      }

      .badge-cree {
        background: #fff3e0;
        color: #e65100;
      }

      .badge-validee {
        background: #e8f5e9;
        color: #1b5e20;
      }

      .badge-cloturee {
        background: #fce4ec;
        color: #c62828;
      }

      .form-row {
        display: flex;
        gap: 12px;
        margin-bottom: 8px;
      }

      .flex1 {
        flex: 1;
      }

      .validate-btn {
        --mdc-filled-button-container-color: var(--app-primary) !important;
        --mdc-filled-button-label-text-color: #fff !important;
      }

      .validate-card {
        margin-top: 20px;
        padding: 16px;
        border: 1px solid var(--app-border);
        box-shadow: var(--app-shadow);
        background: var(--app-surface);
      }
    `,
  ],
})
export class PecAdminComponent implements OnInit {
  private readonly pecAdminStore = inject(PecAdminStore);
  readonly store = inject(AppShellStore);
  readonly auth = inject(AuthStore);
  private readonly snackBar = inject(MatSnackBar);
  private readonly translate = inject(TranslateService);

  readonly pecs = this.pecAdminStore.pecs;
  readonly loading = this.pecAdminStore.loading;
  readonly validatingPec = this.pecAdminStore.validatingPec;
  readonly forfaits = this.pecAdminStore.forfaits;
  readonly validateModel = signal({
    dateDebutEffectif: null as Date | null,
    dateFinEffectif: null as Date | null,
    forfaitEffectifId: null as string | null,
  });
  readonly validateForm = compatForm(this.validateModel, (form) => {
    required(form.dateDebutEffectif);
    required(form.dateFinEffectif);
  });
  readonly validateDisabled = computed(() => {
    const model = this.validateModel();
    return !model.dateDebutEffectif || !model.dateFinEffectif;
  });
  columns = ['patientId', 'dateDebutDemande', 'dateFinDemande', 'status', 'actions'];

  ngOnInit(): void {
    const cid = this.store.currentCenterId();
    if (cid) {
      // ✅ Utiliser le store pour charger
      this.pecAdminStore.loadPecs({centerId: cid});
      this.pecAdminStore.loadForfaits({centerId: cid});
    }
  }

  openValidate(pec: any): void {
    this.pecAdminStore.setValidatingPec(pec);
    this.validateModel.set({
      dateDebutEffectif: null,
      dateFinEffectif: null,
      forfaitEffectifId: null,
    });
  }

  setForfait(forfaitId: string | null): void {
    this.validateModel.update((model) => ({...model, forfaitEffectifId: forfaitId}));
  }

  cancelValidate(): void {
    this.pecAdminStore.setValidatingPec(null);
  }

  confirmValidate(): void {
    const pec = this.validatingPec();
    if (!pec || this.validateDisabled()) return;
    const cid = this.store.currentCenterId()!;
    const v = this.validateModel();
    const toDate = (d: any) => (d instanceof Date ? d.toISOString().slice(0, 10) : d);

    this.pecAdminStore.validatePec({
      pecId: pec.id,
      centerId: cid,
      userId: this.auth.username() ?? 'admin',
      dateDebutEffectif: toDate(v.dateDebutEffectif),
      dateFinEffectif: toDate(v.dateFinEffectif),
      forfaitEffectifId: v.forfaitEffectifId ?? undefined,
    });
    this.snackBar.open(this.translate.instant('PEC_ADMIN.VALIDATED_OK') || 'PEC validée', 'OK', {
      duration: 3000,
    });
  }

  closePec(pec: any): void {
    const cid = this.store.currentCenterId()!;
    this.pecAdminStore.closePec({
      pecId: pec.id,
      centerId: cid,
      userId: this.auth.username() ?? 'admin',
    });
    this.snackBar.open(this.translate.instant('PEC_ADMIN.CLOSED_OK') || 'PEC clôturée', 'OK', {
      duration: 3000,
    });
  }
}
