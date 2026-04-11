import { Component, OnInit, Output, EventEmitter, inject, signal, Input, OnChanges, SimpleChanges } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslateModule } from '@ngx-translate/core';
import { BackendApiService } from '../../../core/api/backend-api.service';
import { AppShellStore } from '../../../core/state/app-shell.store';

@Component({
  selector: 'app-step-attestation',
  standalone: true,
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatDatepickerModule, MatNativeDateModule, MatIconModule, MatButtonModule, TranslateModule],
  template: `
    <div class="step-content">
      <div class="att-grid">
        <div class="history-pane">
          <div class="history-head">
            <h4>{{ 'WIZARD.ATTESTATION_HISTORY' | translate }}</h4>
            <button type="button" mat-flat-button class="add-btn" (click)="prepareNew()" [disabled]="readonly">
              <mat-icon>add</mat-icon> {{ 'COMMON.NEW' | translate }}
            </button>
          </div>

          @if (history().length === 0) {
            <p class="empty">{{ 'WIZARD.ATTESTATION_HISTORY_EMPTY' | translate }}</p>
          } @else {
            <div class="history-list">
              @for (h of history(); track $index) {
                <button type="button" class="history-item" [class.active]="isSelected(h)" (click)="select(h)">
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
                <strong>Attestation selectionnee</strong>
                <div class="detail-actions">
                  <button type="button" mat-stroked-button color="warn" (click)="deleteSelected()" [disabled]="readonly || !selectedAttestationId()">
                    <mat-icon>delete</mat-icon>
                    Supprimer
                  </button>
                  <button type="button" mat-stroked-button (click)="printSelected()" [disabled]="!patientId">
                    <mat-icon>print</mat-icon>
                    {{ 'COMMON.PRINT' | translate }}
                  </button>
                </div>
              </div>
              <div class="detail-grid">
                <span>Debut</span><strong>{{ selectedAttestation()?.dateDebut || selectedAttestation()?.DATE_DEBUT || '—' }}</strong>
                <span>Fin</span><strong>{{ selectedAttestation()?.dateFin || selectedAttestation()?.DATE_FIN || '—' }}</strong>
                <span>Statut</span><strong>{{ selectedAttestation()?.statut || selectedAttestation()?.STATUT || '—' }}</strong>
              </div>
            </div>
          }

          <form [formGroup]="form">
            <div class="form-row">
              <mat-form-field appearance="outline" class="flex1">
                <mat-label>{{ 'PATIENT_FORM.ATTESTATION_DEBUT' | translate }} *</mat-label>
                <mat-icon matPrefix>event</mat-icon>
                <input matInput [matDatepicker]="dpDebut" formControlName="attestationDebut" />
                <mat-datepicker-toggle matSuffix [for]="dpDebut" /><mat-datepicker #dpDebut />
                @if (form.get('attestationDebut')?.hasError('required') && form.get('attestationDebut')?.touched) {
                  <mat-error>{{ 'PATIENT_FORM.REQUIRED' | translate }}</mat-error>
                }
              </mat-form-field>
              <mat-form-field appearance="outline" class="flex1">
                <mat-label>{{ 'PATIENT_FORM.ATTESTATION_FIN' | translate }} *</mat-label>
                <mat-icon matPrefix>event_available</mat-icon>
                <input matInput [matDatepicker]="dpFin" formControlName="attestationFin" />
                <mat-datepicker-toggle matSuffix [for]="dpFin" /><mat-datepicker #dpFin />
                @if (form.get('attestationFin')?.hasError('required') && form.get('attestationFin')?.touched) {
                  <mat-error>{{ 'PATIENT_FORM.REQUIRED' | translate }}</mat-error>
                }
              </mat-form-field>
            </div>
          </form>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .step-content { padding: 14px 20px 20px; }
    .att-grid { display:grid; grid-template-columns: 320px 1fr; gap: 18px; align-items:start; }
    .history-pane {
      border:1px solid #d9e7dd; border-radius:12px; padding:12px;
      background: linear-gradient(180deg, #f7fcf8 0%, #f1f8f3 100%);
      box-shadow: 0 6px 18px rgba(27,94,32,.06);
      min-height:160px;
    }
    .history-head { display:flex; align-items:center; justify-content:space-between; gap:8px; margin-bottom:8px; }
    .empty { color:#888; font-style:italic; }
    .history-list { display:flex; flex-direction:column; gap:6px; }
    .history-item {
      font-size:12px; color:#375a3f; padding:8px 10px;
      display:flex; justify-content:space-between; align-items:center;
      border:1px solid #dbe8de;
      border-radius:10px;
      background:#fff;
      cursor:pointer;
      text-align:left;
    }
    .history-item.active { background:#e9f6ed; border-color:#b9ddc2; }
    .add-btn { --mdc-filled-button-container-color:#1b5e20; --mdc-filled-button-label-text-color:#fff; }
    .section-title { color: #1b5e20; font-size: 1.02rem; font-weight: 700; margin: 0 0 8px; }
    .info-text { color: #6b7280; margin-bottom: 14px; font-size: 13px; }
    .detail-card {
      background:#fff;
      border:1px solid #deebdf;
      border-radius:12px;
      padding:10px 12px;
      margin-bottom:12px;
    }
    .detail-head { display:flex; justify-content:space-between; align-items:center; margin-bottom:8px; }
    .detail-actions { display:flex; gap:8px; align-items:center; }
    .detail-grid { display:grid; grid-template-columns:100px 1fr; row-gap:6px; font-size:13px; }
    .form-row { display: flex; gap: 12px; margin-bottom: 8px; }
    .flex1 { flex: 1; }
    :host ::ng-deep .mat-mdc-form-field { font-size: 13px; }
    :host ::ng-deep .mat-mdc-form-field-subscript-wrapper { display: none; }
    :host ::ng-deep input.mat-mdc-input-element { text-align: center; }
  `]
})
export class StepAttestationComponent implements OnInit, OnChanges {
  @Input() readonly = false;
  @Output() dataChange = new EventEmitter<Record<string, any>>();
  @Output() validChange = new EventEmitter<boolean>();
  @Output() deleteRequest = new EventEmitter<{ type: 'ATTESTATION'; id: string }>();

  private readonly fb = inject(FormBuilder);
  private readonly api = inject(BackendApiService);
  private readonly store = inject(AppShellStore);
  private readonly snackBar = inject(MatSnackBar);

  history = signal<any[]>([]);
  selectedAttestationId = signal<string | null>(null);
  selectedAttestation = signal<any | null>(null);
  patientId: string | null = null;
  form!: FormGroup;

  ngOnInit(): void {
    this.form = this.fb.group({
      attestationDebut: [null, Validators.required],
      attestationFin: [null, Validators.required]
    });
    this.form.valueChanges.subscribe(() => {
      this.dataChange.emit({ ...this.form.getRawValue(), attestationId: this.selectedAttestationId() });
      this.validChange.emit(this.form.valid);
    });
    this.applyReadonly();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['readonly']) this.applyReadonly();
  }

  private applyReadonly(): void {
    if (!this.form) return;
    this.readonly ? this.form.disable({ emitEvent: false }) : this.form.enable({ emitEvent: false });
  }

  markTouched(): void { this.form.markAllAsTouched(); }
  isValid(): boolean { return this.form.valid; }

  patchData(data: Record<string, any>): void {
    if (!this.form) return;
    this.form.patchValue({
      attestationDebut: data['attestationDebut'] ?? null,
      attestationFin: data['attestationFin'] ?? null
    }, { emitEvent: false });

    this.selectedAttestationId.set(data['attestationId'] ?? null);
    this.patientId = (data['patientId'] ?? data['id'] ?? this.patientId ?? null)?.toString?.() ?? null;
    if (Array.isArray(data['attestationHistory'])) this.history.set(data['attestationHistory']);

    const selected = this.history().find(h => (h?.id ?? h?.ID ?? '').toString() === (this.selectedAttestationId() ?? '')) ?? null;
    this.selectedAttestation.set(selected);

    this.dataChange.emit({ ...this.form.getRawValue(), attestationId: this.selectedAttestationId() });
    this.validChange.emit(this.form.valid);
    this.applyReadonly();
  }

  select(h: any): void {
    this.selectedAttestationId.set((h.id ?? h.ID ?? '').toString());
    this.patientId = (h.patientId ?? h.PATIENT_ID ?? this.patientId ?? null)?.toString?.() ?? null;
    this.selectedAttestation.set(h);
    this.form.patchValue({
      attestationDebut: h.dateDebut ?? h.DATE_DEBUT ?? null,
      attestationFin: h.dateFin ?? h.DATE_FIN ?? null
    });
  }

  isSelected(h: any): boolean {
    const id = (h?.id ?? h?.ID ?? '').toString();
    return id !== '' && this.selectedAttestationId() === id;
  }

  prepareNew(): void {
    this.selectedAttestationId.set(null);
    this.selectedAttestation.set(null);
    this.form.patchValue({ attestationDebut: null, attestationFin: null });
  }

  printSelected(): void {
    const centerId = this.store.currentCenterId();
    if (!centerId || !this.patientId) return;

    this.api.printDocument(centerId, 'ATTESTATION', {
      patientId: this.patientId,
      attestationId: this.selectedAttestationId() ?? ''
    }).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        window.open(url, '_blank');
      },
      error: (err) => this.snackBar.open('Erreur impression: ' + (err?.error?.text || err.message), 'OK', { duration: 4000 })
    });
  }

  deleteSelected(): void {
    const id = this.selectedAttestationId();
    if (!id) return;
    const centerId = this.store.currentCenterId();
    if (!centerId) return;
    if (!confirm('Êtes-vous sûr de vouloir supprimer cette attestation ?')) return;

    this.api.deleteAttestation(id, centerId).subscribe({
      next: () => {
        this.history.set(this.history().filter(h => (h.id ?? h.ID ?? '').toString() !== id));
        this.prepareNew();
        this.snackBar.open('Attestation supprimée', 'OK', { duration: 3000 });
        this.deleteRequest.emit({ type: 'ATTESTATION', id });
      },
      error: (err) => this.snackBar.open('Erreur suppression: ' + (err?.error?.detail || err.message), 'OK', { duration: 4000 })
    });
  }
}
