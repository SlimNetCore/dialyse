import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  EventEmitter,
  inject,
  Injector,
  Input,
  OnInit,
  Output,
  signal,
} from '@angular/core';
import {compatForm} from '@angular/forms/signals/compat';
import {disabled, FormField, FormRoot, required} from '@angular/forms/signals';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatDatepickerModule} from '@angular/material/datepicker';
import {MatNativeDateModule} from '@angular/material/core';
import {MatIconModule} from '@angular/material/icon';
import {MatButtonModule} from '@angular/material/button';
import {MatSnackBar} from '@angular/material/snack-bar';
import {MatDialog} from '@angular/material/dialog';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {ConfirmDialogComponent} from '../../../shared/confirm-dialog.component';
import {PatientFicheStore} from '../state/patient-fiche.store';
import {consumeWizardActionStatus} from './wizard-action-status.util';

@Component({
  selector: 'app-step-attestation',
  standalone: true,
  imports: [
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatIconModule,
    MatButtonModule,
    TranslateModule,
    FormRoot,
    FormField,
  ],
  template: `
    <div class="step-content">
      <div class="att-grid">
        <div class="history-pane">
          <div class="history-head">
            <h4>{{ 'WIZARD.ATTESTATION_HISTORY' | translate }}</h4>
            <button
              type="button"
              mat-flat-button
              class="add-btn"
              (click)="prepareNew()"
              [disabled]="readonly"
            >
              <mat-icon>add</mat-icon> {{ 'COMMON.NEW' | translate }}
            </button>
          </div>

          @if (history().length === 0) {
            <p class="empty">{{ 'WIZARD.ATTESTATION_HISTORY_EMPTY' | translate }}</p>
          } @else {
            <div class="history-list">
              @for (h of history(); track $index) {
                <button
                  type="button"
                  class="history-item"
                  [class.active]="isSelected(h)"
                  (click)="select(h)"
                >
                  <div>
                    <strong>{{ h.dateDebut || h.DATE_DEBUT }}</strong>
                    <span> → {{ h.dateFin || h.DATE_FIN }}</span>
                  </div>
                  <mat-icon>chevron_right</mat-icon>
                </button>
              }
            </div>
          }
        </div>

        <div>
          <h3 class="section-title">{{ 'WIZARD.ATTESTATION_TITLE' | translate }}</h3>
          <p class="info-text">{{ 'WIZARD.ATTESTATION_DESC' | translate }}</p>

          @if (selectedAttestation()) {
            <div class="detail-card">
              <div class="detail-head">
                <strong>{{ 'WIZARD.ATTESTATION_SELECTED' | translate }}</strong>
                <div class="detail-actions">
                  <button
                    type="button"
                    mat-stroked-button
                    color="warn"
                    (click)="deleteSelected()"
                    [disabled]="readonly || !selectedAttestationId()"
                  >
                    <mat-icon>delete</mat-icon>
                    {{ 'COMMON.DELETE' | translate }}
                  </button>
                  <button
                    type="button"
                    mat-stroked-button
                    (click)="printSelected()"
                    [disabled]="!patientId"
                  >
                    <mat-icon>print</mat-icon>
                    {{ 'COMMON.PRINT' | translate }}
                  </button>
                </div>
              </div>
              <div class="detail-grid">
                <span>{{ 'WIZARD.FIELD_START' | translate }}</span
                ><strong>{{
                  selectedAttestation()?.dateDebut || selectedAttestation()?.DATE_DEBUT || '—'
                }}</strong>
                <span>{{ 'WIZARD.FIELD_END' | translate }}</span
                ><strong>{{
                  selectedAttestation()?.dateFin || selectedAttestation()?.DATE_FIN || '—'
                }}</strong>
                <span>{{ 'WIZARD.FIELD_STATUS' | translate }}</span
                ><strong>{{
                  selectedAttestation()?.statut || selectedAttestation()?.STATUT || '—'
                }}</strong>
              </div>
            </div>
          }

          <form [formRoot]="form">
            <div class="form-row">
              <mat-form-field appearance="outline" class="flex1">
                <mat-label>{{ 'PATIENT_FORM.ATTESTATION_DEBUT' | translate }} *</mat-label>
                <mat-icon matPrefix>event</mat-icon>
                <input
                  matInput
                  [matDatepicker]="dpDebut"
                  [formField]="form.attestationDebut"
                  data-autofocus-first
                />
                <mat-datepicker-toggle matSuffix [for]="dpDebut" /><mat-datepicker #dpDebut />
              </mat-form-field>
              <mat-form-field appearance="outline" class="flex1">
                <mat-label>{{ 'PATIENT_FORM.ATTESTATION_FIN' | translate }} *</mat-label>
                <mat-icon matPrefix>event_available</mat-icon>
                <input matInput [matDatepicker]="dpFin" [formField]="form.attestationFin"/>
                <mat-datepicker-toggle matSuffix [for]="dpFin" /><mat-datepicker #dpFin />
              </mat-form-field>
            </div>
          </form>
        </div>
      </div>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.Eager,
  styles: [
    `
      .step-content {
        padding: 14px 18px 18px;
      }

      .att-grid {
        display: grid;
        grid-template-columns: 320px 1fr;
        gap: 18px;
        align-items: start;
      }

      .history-pane {
        border: 1px solid var(--app-border);
        border-radius: 12px;
        padding: 12px;
        background: var(--app-surface);
        box-shadow: var(--app-shadow);
        min-height: 160px;
      }

      .history-head {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 8px;
        margin-bottom: 8px;
      }

      .empty {
        color: var(--app-muted);
        font-style: italic;
      }

      .history-list {
        display: flex;
        flex-direction: column;
        gap: 6px;
      }

      .history-item {
        font-size: 12px;
        color: var(--app-text);
        padding: 8px 10px;
        display: flex;
        justify-content: space-between;
        align-items: center;
        border: 1px solid var(--app-border);
        border-radius: 10px;
        background: var(--app-surface);
        cursor: pointer;
        text-align: left;
      }

      .history-item.active {
        background: var(--app-primary-soft);
        border-color: var(--app-primary-outline);
      }

      .add-btn {
        --mdc-filled-button-container-color: var(--app-primary);
        --mdc-filled-button-label-text-color: #fff;
      }

      .section-title {
        color: var(--app-text);
        font-size: 1.02rem;
        font-weight: 700;
        margin: 0 0 8px;
      }

      .info-text {
        color: #6b7280;
        margin-bottom: 14px;
        font-size: 13px;
      }

      .detail-card {
        background: var(--app-surface);
        border: 1px solid var(--app-border);
        border-radius: 12px;
        padding: 10px 12px;
        margin-bottom: 12px;
      }

      .detail-head {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 8px;
      }

      .detail-actions {
        display: flex;
        gap: 8px;
        align-items: center;
      }

      .detail-grid {
        display: grid;
        grid-template-columns: 100px 1fr;
        row-gap: 6px;
        font-size: 13px;
      }

      .form-row {
        display: flex;
        gap: 12px;
        margin-bottom: 8px;
      }

      .flex1 {
        flex: 1;
      }

      .form-row {
        align-items: flex-start;
      }

      :host ::ng-deep .mat-mdc-form-field {
        font-size: 13px;
      }

      :host ::ng-deep .mat-mdc-form-field-subscript-wrapper {
        display: none;
      }

      :host ::ng-deep input.mat-mdc-input-element {
        text-align: center;
      }

      @media (max-width: 980px) {
        .step-content {
          padding: 12px;
        }
        .att-grid {
          grid-template-columns: 1fr;
        }
        .history-pane {
          min-height: auto;
        }
        .history-head {
          flex-direction: column;
          align-items: flex-start;
        }
        .add-btn {
          width: 100%;
        }
        .detail-head {
          flex-direction: column;
          align-items: flex-start;
          gap: 8px;
        }
        .detail-actions {
          width: 100%;
          flex-wrap: wrap;
        }
        .detail-grid {
          grid-template-columns: 90px 1fr;
        }
        .form-row {
          flex-wrap: wrap;
          gap: 10px;
        }
        .flex1 {
          flex: 1 1 100%;
        }
      }

      @media (max-width: 600px) {
        .history-pane {
          padding: 10px;
        }
        .history-item {
          padding: 10px;
        }
        .detail-card {
          padding: 10px;
        }
        .detail-grid {
          grid-template-columns: 1fr;
          row-gap: 4px;
        }
        .detail-actions > button {
          flex: 1 1 100%;
        }
      }
    `,
  ],
})
export class StepAttestationComponent implements OnInit {
  @Input() readonly = false;
  @Output() dataChange = new EventEmitter<Record<string, any>>();
  @Output() validChange = new EventEmitter<boolean>();
  @Output() deleteRequest = new EventEmitter<{ type: 'ATTESTATION'; id: string }>();

  private readonly appShell = inject(AppShellStore);
  private readonly ficheStore = inject(PatientFicheStore);
  private readonly snackBar = inject(MatSnackBar);
  private readonly dialog = inject(MatDialog);
  private readonly translate = inject(TranslateService);
  private readonly injector = inject(Injector);
  private pendingPrint = false;
  private pendingDeleteId: string | null = null;

  history = signal<any[]>([]);
  selectedAttestationId = signal<string | null>(null);
  selectedAttestation = signal<any | null>(null);
  patientId: string | null = null;
  readonly formModel = signal({
    attestationDebut: null as string | null,
    attestationFin: null as string | null,
  });
  readonly form = compatForm(this.formModel, (form) => {
    required(form.attestationDebut);
    required(form.attestationFin);
    disabled(form.attestationDebut, {when: () => this.readonly});
    disabled(form.attestationFin, {when: () => this.readonly});
  });
  readonly formValid = computed(() => {
    const value = this.formModel();
    return !!value.attestationDebut && !!value.attestationFin;
  });

  constructor() {
    consumeWizardActionStatus(this.ficheStore, ({action, success, error, meta}) => {
      const effectiveError = error || this.ficheStore.error() || '';
      const actionMeta = meta ?? {};

      if (
        action === 'PRINT_DOCUMENT' &&
        this.pendingPrint &&
        actionMeta['typeDocument'] === 'ATTESTATION'
      ) {
        this.pendingPrint = false;
        if (!success && effectiveError) {
          this.snackBar.open(
            this.translate.instant('WIZARD.PRINT_ERROR', {detail: effectiveError}),
            'OK',
            {duration: 4000},
          );
        }
      }

      if (
        action === 'DELETE_ATTESTATION' &&
        this.pendingDeleteId &&
        actionMeta['attestationId'] === this.pendingDeleteId
      ) {
        const deletedId = this.pendingDeleteId;
        this.pendingDeleteId = null;
        if (!success) {
          this.snackBar.open(
            this.translate.instant('WIZARD.DELETE_ERROR', {detail: effectiveError}),
            'OK',
            {duration: 4000},
          );
          return;
        }
        this.history.set(
          this.history().filter((h) => (h.id ?? h.ID ?? '').toString() !== deletedId),
        );
        this.prepareNew();
        this.snackBar.open(this.translate.instant('WIZARD.ATTESTATION_DELETED_OK'), 'OK', {
          duration: 3000,
        });
        this.deleteRequest.emit({type: 'ATTESTATION', id: deletedId});
      }
    });
  }

  ngOnInit(): void {
    effect(() => {
      const value = this.formModel();
      this.dataChange.emit({
        ...value,
        attestationId: this.selectedAttestationId(),
      });
      this.validChange.emit(this.formValid());
    }, {injector: this.injector});
  }

  markTouched(): void {
    this.form().markAsTouched();
  }

  isValid(): boolean {
    return this.formValid();
  }

  patchData(data: Record<string, any>): void {
    const pickLatest = (rows: any[]): any | null => {
      if (!Array.isArray(rows) || rows.length === 0) return null;
      const toMs = (raw: any): number => {
        if (!raw) return Number.NEGATIVE_INFINITY;
        const d = raw instanceof Date ? raw : new Date(raw);
        const ms = d.getTime();
        return Number.isNaN(ms) ? Number.NEGATIVE_INFINITY : ms;
      };
      return [...rows].sort((a, b) => {
        const aRef = toMs(a?.dateFin ?? a?.DATE_FIN ?? a?.dateDebut ?? a?.DATE_DEBUT);
        const bRef = toMs(b?.dateFin ?? b?.DATE_FIN ?? b?.dateDebut ?? b?.DATE_DEBUT);
        return bRef - aRef;
      })[0] ?? null;
    };

    this.formModel.set({
      attestationDebut: data['attestationDebut'] ?? null,
      attestationFin: data['attestationFin'] ?? null,
    });

    const providedId = data['attestationId'] ?? null;
    this.patientId =
      (data['patientId'] ?? data['id'] ?? this.patientId ?? null)?.toString?.() ?? null;
    if (Array.isArray(data['attestationHistory'])) this.history.set(data['attestationHistory']);

    const latest = pickLatest(this.history());
    const selectedId =
      providedId ?? (latest ? (latest.id ?? latest.ID ?? null)?.toString?.() ?? null : null);
    this.selectedAttestationId.set(selectedId);

    if (!providedId && latest) {
      this.formModel.update((model) => ({
        ...model,
        attestationDebut: latest.dateDebut ?? latest.DATE_DEBUT ?? null,
        attestationFin: latest.dateFin ?? latest.DATE_FIN ?? null,
      }));
    }

    const selected =
      this.history().find(
        (h) => (h?.id ?? h?.ID ?? '').toString() === (this.selectedAttestationId() ?? ''),
      ) ?? null;
    this.selectedAttestation.set(selected);

    this.dataChange.emit({
      ...this.formModel(),
      attestationId: this.selectedAttestationId(),
    });
    this.validChange.emit(this.formValid());
  }

  select(h: any): void {
    this.selectedAttestationId.set((h.id ?? h.ID ?? '').toString());
    this.patientId = (h.patientId ?? h.PATIENT_ID ?? this.patientId ?? null)?.toString?.() ?? null;
    this.selectedAttestation.set(h);
    this.formModel.update((model) => ({
      ...model,
      attestationDebut: h.dateDebut ?? h.DATE_DEBUT ?? null,
      attestationFin: h.dateFin ?? h.DATE_FIN ?? null,
    }));
  }

  printSelected(): void {
    const centerId = this.appShell.currentCenterId();
    if (!centerId || !this.patientId) return;

    this.ficheStore.printDocument({
      centerId,
      typeDocument: 'ATTESTATION',
      params: {
        patientId: this.patientId,
        attestationId: this.selectedAttestationId() ?? '',
      },
    });
    this.pendingPrint = true;
  }

  isSelected(h: any): boolean {
    const id = (h?.id ?? h?.ID ?? '').toString();
    return id !== '' && this.selectedAttestationId() === id;
  }

  prepareNew(): void {
    this.selectedAttestationId.set(null);
    this.selectedAttestation.set(null);
    this.formModel.update((model) => ({...model, attestationDebut: null, attestationFin: null}));
  }

  deleteSelected(): void {
    const id = this.selectedAttestationId();
    if (!id) return;
    const centerId = this.appShell.currentCenterId();
    if (!centerId) return;

    const ref = this.dialog.open(ConfirmDialogComponent, {
      width: 'min(96vw, 440px)',
      data: {
        title: this.translate.instant('WIZARD.DELETE_ATTESTATION_TITLE'),
        message: this.translate.instant('WIZARD.DELETE_ATTESTATION_CONFIRM'),
        confirmLabel: this.translate.instant('COMMON.DELETE'),
        cancelLabel: this.translate.instant('PEC_ADMIN.CANCEL'),
        color: 'warn',
        icon: 'delete',
      },
    });

    ref.afterClosed().subscribe((confirmed) => {
      if (!confirmed) return;
      this.pendingDeleteId = id;
      this.ficheStore.deleteAttestation({
        attestationId: id,
        centerId,
        patientId: this.patientId ?? undefined,
      });
    });
  }

}
