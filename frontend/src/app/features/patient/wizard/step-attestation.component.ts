import { Component, OnInit, Output, EventEmitter, inject, signal, Input, OnChanges, SimpleChanges } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';

@Component({
  selector: 'app-step-attestation',
  standalone: true,
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatDatepickerModule, MatNativeDateModule, MatIconModule, TranslateModule],
  template: `
    <div class="step-content">
      <div class="att-grid">
        <div class="history-pane">
          <h4>{{ 'WIZARD.ATTESTATION_HISTORY' | translate }}</h4>
          @if (history().length === 0) {
            <p style="color: #888; font-style: italic;">{{ 'WIZARD.ATTESTATION_HISTORY_EMPTY' | translate }}</p>
          } @else {
            @for (h of history(); track $index) {
              <div class="history-item">{{ h.dateDebut || h.DATE_DEBUT }} → {{ h.dateFin || h.DATE_FIN }}</div>
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
    .step-content {   padding: 12px 20px 20px; }
    .att-grid { display:grid; grid-template-columns: 280px 1fr; gap: 16px; align-items:start; }
    .history-pane { border:1px solid #e2e8f0; border-radius:10px; padding:10px; background:#fafafa; min-height:120px; }
    .history-item { font-size:12px; color:#4b5563; padding:4px 0; }
    .section-title { color: #1b5e20; font-size: 1rem; font-weight: 600; margin: 0 0 8px; }
    .info-text { color: #666; margin-bottom: 16px; font-size: 14px; }
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
  form!: FormGroup;

  ngOnInit(): void {
    this.form = this.fb.group({
      attestationDebut: [null, Validators.required],
      attestationFin: [null, Validators.required]
    });
    this.form.valueChanges.subscribe(val => {
      this.dataChange.emit(val);
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
    if (Array.isArray(data['attestationHistory'])) this.history.set(data['attestationHistory']);
    this.dataChange.emit(this.form.value);
    this.validChange.emit(this.form.valid);
    this.applyReadonly();
  }
}
