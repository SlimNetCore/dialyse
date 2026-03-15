import { Component, inject, OnInit, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatCardModule } from '@angular/material/card';
import { SlicePipe, DatePipe } from '@angular/common';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { BackendApiService } from '../../../core/api/backend-api.service';
import { AppShellStore } from '../../../core/state/app-shell.store';
import { AuthSessionService } from '../../../core/auth/auth-session.service';
import { SearchableSelectComponent, DropdownItem } from '../../../shared/searchable-select.component';
import { ReferentialApiService } from '../../../core/api/referential-api.service';

@Component({
  selector: 'app-pec-admin',
  standalone: true,
  imports: [
    ReactiveFormsModule, MatTableModule, MatButtonModule, MatIconModule, MatChipsModule,
    MatFormFieldModule, MatInputModule, MatDatepickerModule, MatNativeDateModule,
    MatSnackBarModule, MatCardModule, SlicePipe, DatePipe, TranslateModule, SearchableSelectComponent
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
        <p style="color:#888; text-align: center; padding: 32px;">{{ 'PEC_ADMIN.LOADING' | translate }}</p>
      }

      @if (!loading() && pecs().length === 0) {
        <div class="empty-state">
          <mat-icon>assignment_turned_in</mat-icon>
          <p>{{ 'PEC_ADMIN.EMPTY' | translate }}</p>
        </div>
      }

      @if (pecs().length > 0) {
        <table mat-table [dataSource]="pecs()" class="pec-table">
          <ng-container matColumnDef="patientId">
            <th mat-header-cell *matHeaderCellDef>{{ 'PEC_ADMIN.COL_PATIENT' | translate }}</th>
            <td mat-cell *matCellDef="let r">{{ r.patientId | slice:0:8 }}…</td>
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
              <span class="status-badge" [class]="'badge-' + r.status?.toLowerCase()">{{ r.status }}</span>
            </td>
          </ng-container>
          <ng-container matColumnDef="actions">
            <th mat-header-cell *matHeaderCellDef>{{ 'PEC_ADMIN.COL_ACTIONS' | translate }}</th>
            <td mat-cell *matCellDef="let r">
              @if (r.status === 'CREE') {
                <button mat-flat-button class="validate-btn" (click)="openValidate(r)">
                  <mat-icon>check_circle</mat-icon> {{ 'PEC_ADMIN.VALIDATE' | translate }}
                </button>
              }
              @if (r.status === 'VALIDEE') {
                <button mat-stroked-button color="warn" (click)="closePec(r)">
                  <mat-icon>cancel</mat-icon> {{ 'PEC_ADMIN.CLOSE' | translate }}
                </button>
              }
            </td>
          </ng-container>

          <tr mat-header-row *matHeaderRowDef="columns"></tr>
          <tr mat-row *matRowDef="let row; columns: columns"></tr>
        </table>
      }

      <!-- Inline validation form -->
      @if (validatingPec()) {
        <mat-card class="validate-card">
          <mat-card-header><mat-card-title>{{ 'PEC_ADMIN.VALIDATE_TITLE' | translate }}</mat-card-title></mat-card-header>
          <mat-card-content>
            <form [formGroup]="validateForm">
              <div class="form-row">
                <mat-form-field appearance="outline" class="flex1">
                  <mat-label>{{ 'PEC_ADMIN.DATE_DEBUT_EFFECTIF' | translate }}</mat-label>
                  <input matInput [matDatepicker]="dpDeb" formControlName="dateDebutEffectif" />
                  <mat-datepicker-toggle matSuffix [for]="dpDeb" /><mat-datepicker #dpDeb />
                </mat-form-field>
                <mat-form-field appearance="outline" class="flex1">
                  <mat-label>{{ 'PEC_ADMIN.DATE_FIN_EFFECTIF' | translate }}</mat-label>
                  <input matInput [matDatepicker]="dpFin" formControlName="dateFinEffectif" />
                  <mat-datepicker-toggle matSuffix [for]="dpFin" /><mat-datepicker #dpFin />
                </mat-form-field>
                <app-searchable-select [items]="forfaits()" [label]="'PEC_ADMIN.FORFAIT_EFFECTIF' | translate"
                  [selectedId]="validateForm.get('forfaitEffectifId')?.value"
                  (selectionChanged)="validateForm.patchValue({forfaitEffectifId: $event?.id})" cssClass="flex1" />
              </div>
              <div class="form-row" style="justify-content: flex-end; gap: 8px;">
                <button mat-stroked-button (click)="validatingPec.set(null)">{{ 'PEC_ADMIN.CANCEL' | translate }}</button>
                <button mat-flat-button class="validate-btn" (click)="confirmValidate()" [disabled]="validateForm.invalid">
                  <mat-icon>check</mat-icon> {{ 'PEC_ADMIN.CONFIRM' | translate }}
                </button>
              </div>
            </form>
          </mat-card-content>
        </mat-card>
      }
    </div>
  `,
  styles: [`
    .pec-admin { max-width: 1100px; margin: 0 auto; }
    .page-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 24px; gap: 16px; }
    .header-left { display: flex; gap: 12px; align-items: flex-start; }
    .header-icon { font-size: 32px; width: 32px; height: 32px; color: #1b5e20; margin-top: 4px; }
    h2 { color: #1b5e20; margin: 0 0 4px; font-size: 1.3rem; }
    .desc { color: #666; margin: 0; font-size: 14px; }
    .center-card { padding: 12px 20px !important; background: linear-gradient(135deg, #e8f5e9, #f1f8e9) !important; }
    .center-info { display: flex; align-items: center; gap: 10px; }
    .center-info mat-icon { color: #1b5e20; font-size: 28px; width: 28px; height: 28px; }
    .center-label { display: block; font-size: 11px; color: #666; text-transform: uppercase; letter-spacing: 0.5px; }
    .center-name { display: block; font-weight: 600; color: #1b5e20; font-size: 15px; }
    .empty-state { text-align: center; padding: 48px 0; color: #aaa; }
    .empty-state mat-icon { font-size: 48px; width: 48px; height: 48px; margin-bottom: 8px; }
    .pec-table { width: 100%; }
    .status-badge {
      display: inline-block; padding: 4px 12px; border-radius: 12px; font-size: 12px; font-weight: 600;
    }
    .badge-cree { background: #fff3e0; color: #e65100; }
    .badge-validee { background: #e8f5e9; color: #1b5e20; }
    .badge-cloturee { background: #fce4ec; color: #c62828; }
    .form-row { display: flex; gap: 12px; margin-bottom: 8px; }
    .flex1 { flex: 1; }
    .validate-btn {
      --mdc-filled-button-container-color: #1b5e20 !important;
      --mdc-filled-button-label-text-color: #fff !important;
    }
    .validate-card { margin-top: 20px; padding: 16px; }
  `]
})
export class PecAdminComponent implements OnInit {
  private readonly api = inject(BackendApiService);
  readonly store = inject(AppShellStore);
  readonly auth = inject(AuthSessionService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly translate = inject(TranslateService);
  private readonly fb = inject(FormBuilder);
  private readonly refApi = inject(ReferentialApiService);

  pecs = signal<any[]>([]);
  loading = signal(false);
  validatingPec = signal<any>(null);
  forfaits = signal<DropdownItem[]>([]);
  columns = ['patientId', 'dateDebutDemande', 'dateFinDemande', 'status', 'actions'];

  validateForm!: FormGroup;

  ngOnInit(): void {
    this.validateForm = this.fb.group({
      dateDebutEffectif: [null, Validators.required],
      dateFinEffectif: [null, Validators.required],
      forfaitEffectifId: [null]
    });
    this.loadPecs();

    const cid = this.store.currentCenterId();
    if (cid) {
      this.refApi.getForfaits(cid).subscribe(list =>
        this.forfaits.set(list.map((i: any) => ({ ...i, id: i.id, label: `${i.code ?? ''} - ${i.nom}` })))
      );
    }
  }

  loadPecs(): void {
    const cid = this.store.currentCenterId();
    if (!cid) return;
    this.loading.set(true);
    this.api.listPecs(cid).subscribe({
      next: list => { this.pecs.set(list); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }

  openValidate(pec: any): void {
    this.validatingPec.set(pec);
    this.validateForm.reset();
  }

  confirmValidate(): void {
    const pec = this.validatingPec();
    if (!pec) return;
    const cid = this.store.currentCenterId()!;
    const v = this.validateForm.value;
    const toDate = (d: any) => d instanceof Date ? d.toISOString().slice(0, 10) : d;

    this.api.validatePecAdmin(pec.id, {
      centerId: cid,
      userId: this.auth.username() ?? 'admin',
      dateDebutEffectif: toDate(v.dateDebutEffectif),
      dateFinEffectif: toDate(v.dateFinEffectif),
      forfaitEffectifId: v.forfaitEffectifId
    }).subscribe({
      next: () => {
        this.snackBar.open(this.translate.instant('PEC_ADMIN.VALIDATED_OK') || 'PEC validée', 'OK', { duration: 3000 });
        this.validatingPec.set(null);
        this.loadPecs();
      },
      error: err => this.snackBar.open(err?.error?.detail || 'Erreur', 'OK', { duration: 5000 })
    });
  }

  closePec(pec: any): void {
    const cid = this.store.currentCenterId()!;
    this.api.closePec(pec.id, cid, this.auth.username() ?? 'admin').subscribe({
      next: () => {
        this.snackBar.open(this.translate.instant('PEC_ADMIN.CLOSED_OK') || 'PEC clôturée', 'OK', { duration: 3000 });
        this.loadPecs();
      },
      error: err => this.snackBar.open(err?.error?.detail || 'Erreur', 'OK', { duration: 5000 })
    });
  }
}

