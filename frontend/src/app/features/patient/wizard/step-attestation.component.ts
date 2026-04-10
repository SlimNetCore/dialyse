import { Component, OnInit, Output, EventEmitter, inject, signal, Input, OnChanges, SimpleChanges } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { TranslateModule } from '@ngx-translate/core';

@Component({
  selector: 'app-step-attestation',
  standalone: true,
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatDatepickerModule, MatNativeDateModule, MatIconModule, MatButtonModule, TranslateModule],
  template: `
    <div class="step-content">
      <div class="att-grid">
        <div class="history-pane">
          <div style="display:flex;align-items:center;justify-content:space-between;gap:8px;">
            <h4>{{ 'WIZARD.ATTESTATION_HISTORY' | translate }}</h4>
            <button type="button" mat-stroked-button (click)="prepareNew()" [disabled]="readonly">
              <mat-icon>add</mat-icon> {{ 'COMMON.NEW' | translate }}
            </button>
          </div>
          @if (history().length === 0) {
            <p style="color: #888; font-style: italic;">{{ 'WIZARD.ATTESTATION_HISTORY_EMPTY' | translate }}</p>
          } @else {
            @for (h of history(); track $index) {
              <div class="history-item">
                <span>{{ h.dateDebut || h.DATE_DEBUT }} → {{ h.dateFin || h.DATE_FIN }}</span>
                <button type="button" mat-button (click)="edit(h)" [disabled]="readonly">{{ 'COMMON.UPDATE' | translate }}</button>
              </div>
            }
          }
        </div>
        <div>
          <h3 class="section-title">{{ 'WIZARD.ATTESTATION_TITLE' | translate }}</h3>
          <p class="info-text">{{ 'WIZARD.ATTESTATION_DESC' | translate }}</p>
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
    .att-grid { display:grid; grid-template-columns: 300px 1fr; gap: 18px; align-items:start; }
    .history-pane {
      border:1px solid #d9e7dd; border-radius:12px; padding:12px;
      background: linear-gradient(180deg, #f7fcf8 0%, #f1f8f3 100%);
      box-shadow: 0 6px 18px rgba(27,94,32,.06);
      min-height:160px;
    }
    .history-item {
      font-size:12px; color:#375a3f; padding:6px 0;
      display:flex; justify-content:space-between; align-items:center;
      border-bottom:1px dashed #dbe8de;
    }
    .history-item:last-child { border-bottom: none; }
    .section-title { color: #1b5e20; font-size: 1.02rem; font-weight: 700; margin: 0 0 8px; }
    .info-text { color: #6b7280; margin-bottom: 14px; font-size: 13px; }
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

  private readonly fb = inject(FormBuilder);
  history = signal<any[]>([]);
  selectedAttestationId = signal<string | null>(null);
  form!: FormGroup;

  ngOnInit(): void {
    this.form = this.fb.group({
      attestationDebut: [null, Validators.required],
      attestationFin: [null, Validators.required]
    });
    this.form.valueChanges.subscribe(val => {
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
    if (Array.isArray(data['attestationHistory'])) this.history.set(data['attestationHistory']);
    this.dataChange.emit({ ...this.form.getRawValue(), attestationId: this.selectedAttestationId() });
    this.validChange.emit(this.form.valid);
    this.applyReadonly();
  }

  edit(h: any): void {
    this.selectedAttestationId.set((h.id ?? h.ID ?? '').toString());
    this.form.patchValue({
      attestationDebut: h.dateDebut ?? h.DATE_DEBUT ?? null,
      attestationFin: h.dateFin ?? h.DATE_FIN ?? null
    });
  }

  prepareNew(): void {
    this.selectedAttestationId.set(null);
    this.form.patchValue({ attestationDebut: null, attestationFin: null });
  }
}
