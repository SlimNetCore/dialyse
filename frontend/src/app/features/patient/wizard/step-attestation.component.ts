import { Component, OnInit, Output, EventEmitter, inject } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, FormGroup } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { TranslateModule } from '@ngx-translate/core';

@Component({
  selector: 'app-step-attestation',
  standalone: true,
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatDatepickerModule, MatNativeDateModule, TranslateModule],
  template: `
    <div class="step-content">
      <h3 class="section-title">{{ 'WIZARD.ATTESTATION_TITLE' | translate }}</h3>
      <p class="info-text">{{ 'WIZARD.ATTESTATION_DESC' | translate }}</p>
      <form [formGroup]="form">
        <div class="form-row">
          <mat-form-field appearance="outline" class="flex1">
            <mat-label>{{ 'PATIENT_FORM.ATTESTATION_DEBUT' | translate }}</mat-label>
            <input matInput [matDatepicker]="dpDebut" formControlName="attestationDebut" />
            <mat-datepicker-toggle matSuffix [for]="dpDebut" /><mat-datepicker #dpDebut />
          </mat-form-field>
          <mat-form-field appearance="outline" class="flex1">
            <mat-label>{{ 'PATIENT_FORM.ATTESTATION_FIN' | translate }}</mat-label>
            <input matInput [matDatepicker]="dpFin" formControlName="attestationFin" />
            <mat-datepicker-toggle matSuffix [for]="dpFin" /><mat-datepicker #dpFin />
          </mat-form-field>
        </div>
      </form>
      <h4 style="margin-top: 24px; color: #37474f;">{{ 'WIZARD.ATTESTATION_HISTORY' | translate }}</h4>
      <p style="color: #888; font-style: italic;">{{ 'WIZARD.ATTESTATION_HISTORY_EMPTY' | translate }}</p>
    </div>
  `,
  styles: [`
    .step-content { padding: 16px 0; }
    .section-title { color: #1b5e20; font-size: 1rem; font-weight: 600; margin-bottom: 8px; }
    .info-text { color: #666; margin-bottom: 16px; font-size: 14px; }
    .form-row { display: flex; gap: 12px; margin-bottom: 8px; }
    .flex1 { flex: 1; }
    :host ::ng-deep .mat-mdc-form-field { font-size: 13px; }
    :host ::ng-deep .mat-mdc-form-field-subscript-wrapper { display: none; }
    :host ::ng-deep input.mat-mdc-input-element { text-align: center; }
  `]
})
export class StepAttestationComponent implements OnInit {
  @Output() dataChange = new EventEmitter<Record<string, any>>();
  @Output() validChange = new EventEmitter<boolean>();

  private readonly fb = inject(FormBuilder);
  form!: FormGroup;

  ngOnInit(): void {
    this.form = this.fb.group({ attestationDebut: [null], attestationFin: [null] });
    this.form.valueChanges.subscribe(val => {
      this.dataChange.emit(val);
      this.validChange.emit(true);
    });
  }

  markTouched(): void { this.form.markAllAsTouched(); }
  isValid(): boolean { return true; }
}
