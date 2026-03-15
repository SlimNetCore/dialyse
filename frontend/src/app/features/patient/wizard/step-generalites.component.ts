import { Component, inject, OnInit, Output, EventEmitter, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatIconModule } from '@angular/material/icon';
import { MatRadioModule } from '@angular/material/radio';
import { TranslateModule } from '@ngx-translate/core';

@Component({
  selector: 'app-step-generalites',
  standalone: true,
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatCheckboxModule, MatDatepickerModule, MatNativeDateModule, MatIconModule, MatRadioModule, TranslateModule],
  template: `
    <div class="step-content">
      <form [formGroup]="form">
        <!-- Photo + Identity row -->
        <div class="row-photo">
          <div class="photo-zone" (click)="photoInput.click()">
            @if (photoPreview()) {
              <img [src]="photoPreview()" alt="Photo" class="photo-img" />
            } @else {
              <mat-icon class="photo-placeholder">person</mat-icon>
              <span class="photo-label">{{ 'PATIENT_FORM.PHOTO' | translate }}</span>
            }
            <input #photoInput type="file" accept="image/*" hidden (change)="onPhoto($event)" />
          </div>

          <div class="identity-grid">
            <mat-form-field appearance="outline">
              <mat-label>{{ 'PATIENT_FORM.CIVILITE' | translate }}</mat-label>
              <mat-select formControlName="civilite">
                <mat-option value="M.">{{ 'PATIENT_FORM.MR' | translate }}</mat-option>
                <mat-option value="Mme">{{ 'PATIENT_FORM.MRS' | translate }}</mat-option>
                <mat-option value="Mlle">{{ 'PATIENT_FORM.MS' | translate }}</mat-option>
              </mat-select>
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>{{ 'PATIENT_FORM.NOM' | translate }} *</mat-label>
              <input matInput formControlName="nom" />
              @if (form.get('nom')?.hasError('required') && form.get('nom')?.touched) {
                <mat-error>{{ 'PATIENT_FORM.REQUIRED' | translate }}</mat-error>
              }
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>{{ 'PATIENT_FORM.PRENOM' | translate }} *</mat-label>
              <input matInput formControlName="prenom" />
              @if (form.get('prenom')?.hasError('required') && form.get('prenom')?.touched) {
                <mat-error>{{ 'PATIENT_FORM.REQUIRED' | translate }}</mat-error>
              }
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>{{ 'PATIENT_FORM.SEXE' | translate }} *</mat-label>
              <mat-select formControlName="sexe">
                <mat-option value="M">{{ 'PATIENT_FORM.MASCULIN' | translate }}</mat-option>
                <mat-option value="F">{{ 'PATIENT_FORM.FEMININ' | translate }}</mat-option>
              </mat-select>
              @if (form.get('sexe')?.hasError('required') && form.get('sexe')?.touched) {
                <mat-error>{{ 'PATIENT_FORM.REQUIRED' | translate }}</mat-error>
              }
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>{{ 'PATIENT_FORM.GROUPE_SANGUIN' | translate }}</mat-label>
              <mat-select formControlName="groupeSanguin">
                <mat-option value="">—</mat-option>
                <mat-option value="A+">A+</mat-option><mat-option value="A-">A-</mat-option>
                <mat-option value="B+">B+</mat-option><mat-option value="B-">B-</mat-option>
                <mat-option value="AB+">AB+</mat-option><mat-option value="AB-">AB-</mat-option>
                <mat-option value="O+">O+</mat-option><mat-option value="O-">O-</mat-option>
              </mat-select>
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>{{ 'PATIENT_FORM.NOMBRE_ENFANTS' | translate }}</mat-label>
              <input matInput type="number" formControlName="nombreEnfants" />
            </mat-form-field>
          </div>
        </div>

        <!-- Dates -->
        <div class="form-row">
          <mat-form-field appearance="outline" class="flex1">
            <mat-label>{{ 'PATIENT_FORM.DATE_ADMISSION' | translate }} *</mat-label>
            <input matInput [matDatepicker]="dpAdm" formControlName="dateAdmission" />
            <mat-datepicker-toggle matSuffix [for]="dpAdm" /><mat-datepicker #dpAdm />
            @if (form.get('dateAdmission')?.hasError('required') && form.get('dateAdmission')?.touched) {
              <mat-error>{{ 'PATIENT_FORM.REQUIRED' | translate }}</mat-error>
            }
          </mat-form-field>
          <mat-form-field appearance="outline" class="flex1">
            <mat-label>{{ 'PATIENT_FORM.DATE_NAISSANCE' | translate }} *</mat-label>
            <input matInput [matDatepicker]="dpNais" formControlName="dateNaissance" />
            <mat-datepicker-toggle matSuffix [for]="dpNais" /><mat-datepicker #dpNais />
            @if (form.get('dateNaissance')?.hasError('required') && form.get('dateNaissance')?.touched) {
              <mat-error>{{ 'PATIENT_FORM.REQUIRED' | translate }}</mat-error>
            }
          </mat-form-field>
          <mat-form-field appearance="outline" class="flex1">
            <mat-label>{{ 'PATIENT_FORM.LIEU_NAISSANCE' | translate }}</mat-label>
            <input matInput formControlName="lieuNaissance" />
          </mat-form-field>
        </div>

        <!-- Contact -->
        <div class="form-row">
          <mat-form-field appearance="outline" class="flex1">
            <mat-label>{{ 'PATIENT_FORM.SITUATION_FAMILIALE' | translate }}</mat-label>
            <mat-select formControlName="situationFamiliale">
              <mat-option value="">—</mat-option>
              <mat-option value="CELIBATAIRE">{{ 'PATIENT_FORM.CELIBATAIRE' | translate }}</mat-option>
              <mat-option value="MARIE">{{ 'PATIENT_FORM.MARIE' | translate }}</mat-option>
              <mat-option value="DIVORCE">{{ 'PATIENT_FORM.DIVORCE' | translate }}</mat-option>
              <mat-option value="VEUF">{{ 'PATIENT_FORM.VEUF' | translate }}</mat-option>
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline" class="flex1">
            <mat-label>{{ 'PATIENT_FORM.PROFESSION1' | translate }}</mat-label>
            <input matInput formControlName="profession" />
          </mat-form-field>
          <mat-form-field appearance="outline" class="flex1">
            <mat-label>{{ 'PATIENT_FORM.TEL_MOBILE' | translate }}</mat-label>
            <input matInput formControlName="telMobile" />
          </mat-form-field>
        </div>

        <div class="form-row">
          <mat-form-field appearance="outline" class="flex1">
            <mat-label>{{ 'PATIENT_FORM.TEL_PERSONNEL' | translate }}</mat-label>
            <input matInput formControlName="telPersonnel" />
          </mat-form-field>
          <mat-form-field appearance="outline" class="flex1">
            <mat-label>{{ 'PATIENT_FORM.TEL_BUREAU' | translate }}</mat-label>
            <input matInput formControlName="telBureau" />
          </mat-form-field>
          <mat-form-field appearance="outline" class="flex1">
            <mat-label>{{ 'PATIENT_FORM.EMAIL' | translate }}</mat-label>
            <input matInput type="email" formControlName="email" />
          </mat-form-field>
        </div>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>{{ 'PATIENT_FORM.ADRESSE' | translate }}</mat-label>
          <input matInput formControlName="adresse" />
        </mat-form-field>

        <!-- Medical checks -->
        <div class="form-row checks-row">
          <mat-checkbox formControlName="epoEnabled">{{ 'PATIENT_FORM.EPO_LABEL' | translate }}</mat-checkbox>
          @if (form.get('epoEnabled')?.value) {
            <mat-form-field appearance="outline" class="date-inline">
              <input matInput [matDatepicker]="dpEpo" formControlName="epoDate" />
              <mat-datepicker-toggle matSuffix [for]="dpEpo" /><mat-datepicker #dpEpo />
            </mat-form-field>
          }
          <mat-checkbox formControlName="ferEnabled">{{ 'PATIENT_FORM.FER_LABEL' | translate }}</mat-checkbox>
          @if (form.get('ferEnabled')?.value) {
            <mat-form-field appearance="outline" class="date-inline">
              <input matInput [matDatepicker]="dpFer" formControlName="ferDate" />
              <mat-datepicker-toggle matSuffix [for]="dpFer" /><mat-datepicker #dpFer />
            </mat-form-field>
          }
          <mat-checkbox formControlName="sousKt">{{ 'PATIENT_FORM.SOUS_KT' | translate }}</mat-checkbox>
          <mat-checkbox formControlName="enSommeil">{{ 'PATIENT_FORM.EN_SOMMEIL' | translate }}</mat-checkbox>
        </div>

        <!-- Qualité assuré -->
        <div class="form-row" style="align-items: center;">
          <span style="font-weight: 500; margin-right: 12px;">{{ 'PATIENT_FORM.QUALITE_ASSURE' | translate }}:</span>
          <mat-radio-group formControlName="qualiteAssure" style="display:flex;gap:16px;">
            <mat-radio-button value="ASSURE_LUI_MEME">{{ 'PATIENT_FORM.ASSURE_LUI_MEME' | translate }}</mat-radio-button>
            <mat-radio-button value="ENFANT">{{ 'PATIENT_FORM.ENFANT_ASSURE' | translate }}</mat-radio-button>
            <mat-radio-button value="CONJOINT">{{ 'PATIENT_FORM.CONJOINT_ASSURE' | translate }}</mat-radio-button>
            <mat-radio-button value="ASCENDANT">{{ 'PATIENT_FORM.ASCENDANT_ASSURE' | translate }}</mat-radio-button>
            <mat-radio-button value="AUTRE">{{ 'PATIENT_FORM.AUTRE' | translate }}</mat-radio-button>
          </mat-radio-group>
        </div>

        <!-- Observation -->
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>{{ 'PATIENT_FORM.OBSERVATION' | translate }}</mat-label>
          <textarea matInput rows="3" formControlName="observation"></textarea>
        </mat-form-field>
      </form>
    </div>
  `,
  styles: [`
    .step-content { padding: 16px 0; }
    .row-photo { display: flex; gap: 24px; margin-bottom: 16px; }
    .photo-zone {
      width: 200px; height: 240px; border: 2px dashed #bbb; border-radius: 10px;
      display: flex; flex-direction: column; align-items: center; justify-content: center;
      cursor: pointer; background: #fafafa; transition: border-color 0.2s; flex-shrink: 0;
    }
    .photo-zone:hover { border-color: #1b5e20; background: #f0fdf4; }
    .photo-img { width: 100%; height: 100%; object-fit: cover; border-radius: 8px; }
    .photo-placeholder { font-size: 64px; width: 64px; height: 64px; color: #bbb; }
    .photo-label { font-size: 13px; color: #999; margin-top: 8px; }
    .identity-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; flex: 1; }
    .form-row { display: flex; gap: 12px; margin-bottom: 4px; }
    .flex1 { flex: 1; }
    .full-width { width: 100%; }
    .checks-row { align-items: center; flex-wrap: wrap; gap: 8px; }
    .date-inline { width: 160px; }
    /* Centered placeholders */
    :host ::ng-deep .mat-mdc-form-field { font-size: 13px; }
    :host ::ng-deep .mat-mdc-form-field-subscript-wrapper { display: none; }
    :host ::ng-deep .mat-mdc-form-field-infix { min-height: 40px !important; padding-top: 10px !important; padding-bottom: 6px !important; }
    :host ::ng-deep input.mat-mdc-input-element { text-align: center; }
    :host ::ng-deep .mat-mdc-select-value { text-align: center; }
    :host ::ng-deep textarea.mat-mdc-input-element { text-align: left; }
    /* Error highlight */
    :host ::ng-deep .mat-mdc-form-field.mat-form-field-invalid .mdc-notched-outline__leading,
    :host ::ng-deep .mat-mdc-form-field.mat-form-field-invalid .mdc-notched-outline__notch,
    :host ::ng-deep .mat-mdc-form-field.mat-form-field-invalid .mdc-notched-outline__trailing {
      border-color: #d32f2f !important;
    }
  `]
})
export class StepGeneralitesComponent implements OnInit {
  @Output() dataChange = new EventEmitter<Record<string, any>>();
  @Output() validChange = new EventEmitter<boolean>();

  private readonly fb = inject(FormBuilder);
  photoPreview = signal<string | null>(null);

  form!: FormGroup;

  ngOnInit(): void {
    this.form = this.fb.group({
      civilite: [''],
      nom: ['', Validators.required],
      prenom: ['', Validators.required],
      sexe: ['', Validators.required],
      groupeSanguin: [''],
      nombreEnfants: [0],
      dateAdmission: [null, Validators.required],
      dateNaissance: [null, Validators.required],
      lieuNaissance: [''],
      situationFamiliale: [''],
      profession: [''],
      telMobile: [''],
      telPersonnel: [''],
      telBureau: [''],
      email: [''],
      adresse: [''],
      epoEnabled: [false],
      epoDate: [null],
      ferEnabled: [false],
      ferDate: [null],
      sousKt: [false],
      enSommeil: [false],
      qualiteAssure: ['ASSURE_LUI_MEME'],
      observation: ['']
    });

    this.form.valueChanges.subscribe(val => {
      this.dataChange.emit(val);
      this.validChange.emit(this.form.valid);
    });
  }

  onPhoto(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      this.photoPreview.set(reader.result as string);
      this.dataChange.emit({ ...this.form.value, photoBase64: reader.result });
    };
    reader.readAsDataURL(file);
  }

  /** Called by wizard before stepping — marks all fields touched to show errors */
  markTouched(): void { this.form.markAllAsTouched(); }
  isValid(): boolean { return this.form.valid; }
}
