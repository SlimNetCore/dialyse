import {
  Component,
  computed,
  EventEmitter,
  inject,
  Input,
  OnChanges,
  OnInit,
  Output,
  signal,
  SimpleChanges
} from '@angular/core';
import {FormBuilder, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatCheckboxModule} from '@angular/material/checkbox';
import {MatDatepickerModule} from '@angular/material/datepicker';
import {MatNativeDateModule} from '@angular/material/core';
import {MatIconModule} from '@angular/material/icon';
import {MatRadioModule} from '@angular/material/radio';
import {MatButtonModule} from '@angular/material/button';
import {TranslateModule} from '@ngx-translate/core';
import {AuthStore} from '../../../core/state/auth.store';
import {DropdownItem, SearchableSelectComponent} from '../../../shared/searchable-select.component';

@Component({
  selector: 'app-step-generalites',
  standalone: true,
  imports: [
    ReactiveFormsModule, MatFormFieldModule, MatInputModule,
    MatCheckboxModule, MatDatepickerModule, MatNativeDateModule, MatIconModule,
    MatRadioModule, MatButtonModule, TranslateModule, SearchableSelectComponent
  ],
  template: `
    <div class="step-content">
      <form [formGroup]="form">
        <!-- Photo + Identity row -->
        <div class="row-photo">
          <div class="photo-column">
            <button mat-flat-button class="medical-btn" [disabled]="readonly || !isMedecin()" (click)="openMedicalRecord()">
              <mat-icon>folder_shared</mat-icon>
              {{ 'PATIENT_FORM.DOSSIER_MEDICAL' | translate }}
            </button>
            <div class="photo-zone" [class.readonly-zone]="readonly" (click)="!readonly && photoInput.click()">
              @if (photoPreview()) {
                <img [src]="photoPreview()" alt="Photo" class="photo-img" />
              } @else {
                <mat-icon class="photo-placeholder">person</mat-icon>
                <span class="photo-label">{{ 'PATIENT_FORM.PHOTO' | translate }}</span>
              }
              <input #photoInput type="file" accept="image/*" hidden (change)="onPhoto($event)" />
            </div>
          </div>

          <div class="identity-grid">
            <!-- Row 1: Civilité, Nom, Prénom -->
            <app-searchable-select
              [items]="civiliteOptions"
              [label]="'PATIENT_FORM.CIVILITE' | translate"
              [prefixIcon]="'badge'"
              [selectedId]="form.get('civilite')?.value ?? ''"
              (selectionChanged)="form.patchValue({ civilite: $event?.id ?? '' })"
              [disabled]="readonly"
              [translateLabels]="true"
              cssClass="generalites-select"/>

            <mat-form-field appearance="outline">
              <mat-label>{{ 'PATIENT_FORM.NOM' | translate }} *</mat-label>
              <mat-icon matPrefix>person</mat-icon>
              <input matInput formControlName="nom" />
              @if (form.get('nom')?.hasError('required') && form.get('nom')?.touched) {
                <mat-error>{{ 'PATIENT_FORM.REQUIRED' | translate }}</mat-error>
              }
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>{{ 'PATIENT_FORM.PRENOM' | translate }} *</mat-label>
              <mat-icon matPrefix>person_outline</mat-icon>
              <input matInput formControlName="prenom" />
              @if (form.get('prenom')?.hasError('required') && form.get('prenom')?.touched) {
                <mat-error>{{ 'PATIENT_FORM.REQUIRED' | translate }}</mat-error>
              }
            </mat-form-field>

            <!-- Row 2: Sexe, Date d'admission, Nombre d'enfants -->
            <div>
              <app-searchable-select
                [items]="sexeOptions"
                [label]="'PATIENT_FORM.SEXE' | translate"
                [prefixIcon]="'wc'"
                [selectedId]="form.get('sexe')?.value ?? ''"
                (selectionChanged)="form.patchValue({ sexe: $event?.id ?? '' })"
                [disabled]="readonly"
                [translateLabels]="true"
                cssClass="generalites-select"/>
              @if (form.get('sexe')?.hasError('required') && form.get('sexe')?.touched) {
                <div class="field-error">{{ 'PATIENT_FORM.REQUIRED' | translate }}</div>
              }
            </div>

            <mat-form-field appearance="outline" class="h-sync">
              <mat-label>{{ 'PATIENT_FORM.DATE_ADMISSION' | translate }} *</mat-label>
              <mat-icon matPrefix>event</mat-icon>
              <input matInput [matDatepicker]="dpAdm" formControlName="dateAdmission" />
              <mat-datepicker-toggle matSuffix [for]="dpAdm" /><mat-datepicker #dpAdm />
              @if (form.get('dateAdmission')?.hasError('required') && form.get('dateAdmission')?.touched) {
                <mat-error>{{ 'PATIENT_FORM.REQUIRED' | translate }}</mat-error>
              }
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>{{ 'PATIENT_FORM.NOMBRE_ENFANTS' | translate }}</mat-label>
              <mat-icon matPrefix>child_care</mat-icon>
              <input matInput type="number" formControlName="nombreEnfants" [readonly]="readonly" />
            </mat-form-field>

            <!-- Row 3: Groupe sanguin, Date de naissance, Age -->
            <app-searchable-select
              [items]="groupeSanguinOptions"
              [label]="'PATIENT_FORM.GROUPE_SANGUIN' | translate"
              [prefixIcon]="'bloodtype'"
              [selectedId]="form.get('groupeSanguin')?.value ?? ''"
              (selectionChanged)="form.patchValue({ groupeSanguin: $event?.id ?? '' })"
              [disabled]="readonly"
              cssClass="h-sync generalites-select"/>

            <mat-form-field appearance="outline" class="h-sync">
              <mat-label>{{ 'PATIENT_FORM.DATE_NAISSANCE' | translate }} *</mat-label>
              <mat-icon matPrefix>cake</mat-icon>
              <input matInput [matDatepicker]="dpNais" formControlName="dateNaissance" />
              <mat-datepicker-toggle matSuffix [for]="dpNais" /><mat-datepicker #dpNais />
              @if (form.get('dateNaissance')?.hasError('required') && form.get('dateNaissance')?.touched) {
                <mat-error>{{ 'PATIENT_FORM.REQUIRED' | translate }}</mat-error>
              }
            </mat-form-field>

            <div class="age-box h-sync">
              <span class="age-label">{{ 'PATIENT_FORM.AGE' | translate }}</span>
              <span class="age-value">{{ calculatedAge() !== null ? calculatedAge() : '—' }}</span>
              @if (calculatedAge() !== null) {
                <span class="age-unit">{{ 'PATIENT_FORM.ANS' | translate }}</span>
              }
            </div>

            <mat-form-field appearance="outline" class="span-3">
              <mat-label>{{ 'PATIENT_FORM.LIEU_NAISSANCE' | translate }}</mat-label>
              <mat-icon matPrefix>place</mat-icon>
              <input matInput formControlName="lieuNaissance" />
            </mat-form-field>

            <div class="span-3">
              <app-searchable-select
                [items]="etatPatientOptions"
                [label]="'PATIENT_FORM.ETAT_PATIENT' | translate"
                [prefixIcon]="'monitor_heart'"
                [selectedId]="form.get('etatPatient')?.value ?? 'PERMANENT'"
                (selectionChanged)="form.patchValue({ etatPatient: $event?.id ?? 'PERMANENT' })"
                [disabled]="readonly"
                [translateLabels]="true"
                cssClass="generalites-select"/>
            </div>

            @if (showDateEvenement()) {
              <mat-form-field appearance="outline" class="span-3">
                <mat-label>{{ 'PATIENT_FORM.DATE_EVENEMENT_ETAT' | translate }}</mat-label>
                <mat-icon matPrefix>event_available</mat-icon>
                <input matInput [matDatepicker]="dpEvt" formControlName="dateEvenementEtat" />
                <mat-datepicker-toggle matSuffix [for]="dpEvt" /><mat-datepicker #dpEvt />
              </mat-form-field>
            }
          </div>
        </div>

        <!-- Contact -->
        <div class="form-row">
          <app-searchable-select
            [items]="situationFamilialeOptions"
            [label]="'PATIENT_FORM.SITUATION_FAMILIALE' | translate"
            [prefixIcon]="'diversity_3'"
            [selectedId]="form.get('situationFamiliale')?.value ?? ''"
            (selectionChanged)="form.patchValue({ situationFamiliale: $event?.id ?? '' })"
            [disabled]="readonly"
            [translateLabels]="true"
            cssClass="flex1 generalites-select"/>
          <mat-form-field appearance="outline" class="flex1">
            <mat-label>{{ 'PATIENT_FORM.PROFESSION1' | translate }}</mat-label>
            <mat-icon matPrefix>work</mat-icon>
            <input matInput formControlName="profession" />
          </mat-form-field>
          <mat-form-field appearance="outline" class="flex1">
            <mat-label>{{ 'PATIENT_FORM.TEL_MOBILE' | translate }}</mat-label>
            <mat-icon matPrefix>phone_iphone</mat-icon>
            <input matInput formControlName="telMobile" />
          </mat-form-field>
        </div>

        <div class="form-row">
          <mat-form-field appearance="outline" class="flex1">
            <mat-label>{{ 'PATIENT_FORM.TEL_PERSONNEL' | translate }}</mat-label>
            <mat-icon matPrefix>phone</mat-icon>
            <input matInput formControlName="telPersonnel" />
          </mat-form-field>
          <mat-form-field appearance="outline" class="flex1">
            <mat-label>{{ 'PATIENT_FORM.TEL_BUREAU' | translate }}</mat-label>
            <mat-icon matPrefix>phone_in_talk</mat-icon>
            <input matInput formControlName="telBureau" />
          </mat-form-field>
          <mat-form-field appearance="outline" class="flex1">
            <mat-label>{{ 'PATIENT_FORM.EMAIL' | translate }}</mat-label>
            <mat-icon matPrefix>mail</mat-icon>
            <input matInput type="email" formControlName="email" />
          </mat-form-field>
        </div>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>{{ 'PATIENT_FORM.ADRESSE' | translate }}</mat-label>
          <mat-icon matPrefix>home</mat-icon>
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
        <div class="form-row quality-row">
          <span class="quality-label">{{ 'PATIENT_FORM.QUALITE_ASSURE' | translate }}:</span>
          <mat-radio-group formControlName="qualiteAssure" class="quality-radio-group" [disabled]="readonly">
            <mat-radio-button value="ASSURE_LUI_MEME">{{ 'PATIENT_FORM.ASSURE_LUI_MEME' | translate }}</mat-radio-button>
            <mat-radio-button value="ENFANT">{{ 'PATIENT_FORM.ENFANT_ASSURE' | translate }}</mat-radio-button>
            <mat-radio-button value="CONJOINT">{{ 'PATIENT_FORM.CONJOINT_ASSURE' | translate }}</mat-radio-button>
            <mat-radio-button value="ASCENDANT">{{ 'PATIENT_FORM.ASCENDANT_ASSURE' | translate }}</mat-radio-button>
            <mat-radio-button value="AUTRE">{{ 'PATIENT_FORM.AUTRE' | translate }}</mat-radio-button>
          </mat-radio-group>
        </div>

        <!-- Observation with extra spacing below -->
        <mat-form-field appearance="outline" class="full-width observation-field">
          <mat-label>{{ 'PATIENT_FORM.OBSERVATION' | translate }}</mat-label>
          <mat-icon matPrefix>note_alt</mat-icon>
          <textarea matInput rows="3" formControlName="observation"></textarea>
        </mat-form-field>
      </form>
    </div>
  `,
  styles: [`
    :host {
      --sync-field-height: 40px;
    }
    .step-content { padding: 14px 18px 18px; }
    .row-photo { display: flex; gap: 20px; margin-bottom: 12px; align-items: stretch; }
    .photo-column { display: flex; flex-direction: column; align-items: stretch; width: 180px; }
    .photo-zone {
      width: 100%; min-height: 220px; border: 2px dashed var(--app-border); border-radius: 12px;
      display: flex; flex-direction: column; align-items: center; justify-content: center;
      cursor: pointer; background: var(--app-surface); transition: all 0.2s; flex-shrink: 0;
    }
    .medical-btn {
      margin-bottom: 10px;
      width: 100%;
      --mdc-filled-button-container-color: var(--app-primary) !important;
      --mdc-filled-button-label-text-color: #ffffff !important;
    }
    .photo-zone:hover { border-color: var(--app-primary); background: var(--app-primary-soft); box-shadow: 0 4px 16px rgba(2, 6, 23, 0.08); }
    .photo-zone.readonly-zone { cursor: default; opacity: .88; }
    .photo-img { width: 100%; height: 100%; object-fit: cover; border-radius: 10px; }
    .photo-placeholder { font-size: 56px; width: 56px; height: 56px; color: var(--app-primary-outline); }
    .photo-label { font-size: 12px; color: var(--app-primary); margin-top: 6px; }
    .identity-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; flex: 1; align-content: start; }
    .span-3 { grid-column: 1 / -1; }
    .form-row { display: flex; gap: 10px; margin-bottom: 8px; align-items: flex-start; }

    .identity-grid app-searchable-select,
    .form-row app-searchable-select {
      width: 100%;
      min-width: 0;
    }

    .form-row app-searchable-select {
      flex: 1;
    }
    .flex1 { flex: 1; }
    .full-width { width: 100%; }
    .checks-row { align-items: center; flex-wrap: wrap; gap: 12px; }
    .date-inline { width: 160px; }
    .observation-field { margin-bottom: 24px !important; }
    /* Age display box */
    .age-box {
      display: flex; flex-direction: column; align-items: center; justify-content: center;
      min-width: 70px; padding: 8px 12px;
      background: var(--app-primary-soft); border-radius: 10px; border: 1px solid var(--app-primary-outline);
    }

    .h-sync.age-box {
      width: 100%;
      min-width: 0;
      min-height: var(--sync-field-height);
      height: var(--sync-field-height);
      padding: 0 10px;
      box-sizing: border-box;
      flex-direction: row;
      gap: 6px;
    }
    .age-label { font-size: 11px; color: #666; text-transform: uppercase; letter-spacing: 0.3px; }

    .age-value {
      font-size: 16px;
      font-weight: 700;
      color: var(--app-primary);
      line-height: 1;
    }
    .age-unit { font-size: 11px; color: #666; }
    :host ::ng-deep .mat-mdc-form-field { font-size: 13px; }

    :host ::ng-deep .mat-mdc-form-field.h-sync {
      --mat-form-field-container-height: var(--sync-field-height);
      --mat-form-field-container-vertical-padding: 8px;
    }
    :host ::ng-deep .mat-mdc-form-field-subscript-wrapper { display: none; }
    :host ::ng-deep input.mat-mdc-input-element { text-align: center; }
    :host ::ng-deep .mat-mdc-select-value { text-align: center; }
    :host ::ng-deep textarea.mat-mdc-input-element { text-align: left; }

    .field-error {
      color: #b91c1c;
      font-size: 12px;
      margin: -2px 0 4px 2px;
    }

    .quality-row {
      align-items: center;
    }

    .quality-label {
      font-weight: 500;
      margin-right: 12px;
    }

    .quality-radio-group {
      display: flex;
      gap: 16px;
      flex-wrap: wrap;
    }

    @media (max-width: 900px) {
      .step-content {
        padding: 12px;
      }

      .row-photo {
        flex-direction: column;
        gap: 12px;
      }

      .photo-column {
        width: 100%;
      }

      .photo-zone {
        min-height: 170px;
      }

      .identity-grid {
        grid-template-columns: 1fr;
        gap: 10px;
      }

      .form-row {
        flex-wrap: wrap;
        gap: 10px;
      }

      .flex1,
      .date-inline {
        flex: 1 1 100%;
        width: 100%;
      }

      .h-sync.age-box {
        min-height: 48px;
        height: auto;
      }

      .quality-row {
        align-items: flex-start;
        flex-direction: column;
      }

      .quality-label {
        margin-right: 0;
      }

      .quality-radio-group {
        gap: 10px;
      }
    }
  `]
})
export class StepGeneralitesComponent implements OnInit, OnChanges {
  @Input() readonly = false;
  @Output() dataChange = new EventEmitter<Record<string, any>>();
  @Output() validChange = new EventEmitter<boolean>();

  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthStore);
  readonly civiliteOptions: DropdownItem[] = [
    {id: 'M.', label: 'PATIENT_FORM.MR'},
    {id: 'Mme', label: 'PATIENT_FORM.MRS'},
    {id: 'Mlle', label: 'PATIENT_FORM.MS'}
  ];
  readonly sexeOptions: DropdownItem[] = [
    {id: 'M', label: 'PATIENT_FORM.MASCULIN'},
    {id: 'F', label: 'PATIENT_FORM.FEMININ'}
  ];
  readonly groupeSanguinOptions: DropdownItem[] = [
    {id: '', label: '—'},
    {id: 'A+', label: 'A+'},
    {id: 'A-', label: 'A-'},
    {id: 'B+', label: 'B+'},
    {id: 'B-', label: 'B-'},
    {id: 'AB+', label: 'AB+'},
    {id: 'AB-', label: 'AB-'},
    {id: 'O+', label: 'O+'},
    {id: 'O-', label: 'O-'}
  ];
  readonly etatPatientOptions: DropdownItem[] = [
    {id: 'PERMANENT', label: 'PATIENT_FORM.PERMANENT'},
    {id: 'OCCASIONNEL', label: 'PATIENT_FORM.OCCASIONNEL'},
    {id: 'TRANSFERE', label: 'PATIENT_FORM.TRANSFERE'},
    {id: 'DECEDE', label: 'PATIENT_FORM.DECEDE'},
    {id: 'GREFFE', label: 'PATIENT_FORM.GREFFE'},
    {id: 'GUERRI', label: 'PATIENT_FORM.GUERRI'},
    {id: 'VACANCIER_LOCAL', label: 'PATIENT_FORM.VACANCIER_LOCAL'},
    {id: 'VACANCIER_ETRANGER', label: 'PATIENT_FORM.VACANCIER_ETRANGER'}
  ];
  readonly situationFamilialeOptions: DropdownItem[] = [
    {id: '', label: '—'},
    {id: 'CELIBATAIRE', label: 'PATIENT_FORM.CELIBATAIRE'},
    {id: 'MARIE', label: 'PATIENT_FORM.MARIE'},
    {id: 'DIVORCE', label: 'PATIENT_FORM.DIVORCE'},
    {id: 'VEUF', label: 'PATIENT_FORM.VEUF'}
  ];
  photoPreview = signal<string | null>(null);
  private dateNaissanceSignal = signal<Date | null>(null);
  private etatPatientSignal = signal<string>('PERMANENT');

  readonly isMedecin = computed(() => this.auth.hasRole('ROLE_MEDECIN'));
  readonly showDateEvenement = computed(() => {
    const etat = this.etatPatientSignal();
    return etat === 'DECEDE' || etat === 'GREFFE' || etat === 'TRANSFERE';
  });

  calculatedAge = computed(() => {
    const dob = this.dateNaissanceSignal();
    if (!dob) return null;
    const today = new Date();
    let age = today.getFullYear() - dob.getFullYear();
    const m = today.getMonth() - dob.getMonth();
    if (m < 0 || (m === 0 && today.getDate() < dob.getDate())) age--;
    return age >= 0 ? age : null;
  });

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
      etatPatient: ['PERMANENT'],
      dateEvenementEtat: [null],
      qualiteAssure: ['ASSURE_LUI_MEME'],
      observation: ['']
    });

    this.form.valueChanges.subscribe(val => {
      // clear event date for states that do not require it
      if (!(val.etatPatient === 'DECEDE' || val.etatPatient === 'GREFFE' || val.etatPatient === 'TRANSFERE') && val.dateEvenementEtat) {
        this.form.patchValue({ dateEvenementEtat: null }, { emitEvent: false });
        val.dateEvenementEtat = null;
      }
      this.etatPatientSignal.set(val.etatPatient || 'PERMANENT');
      this.dataChange.emit(val);
      this.validChange.emit(this.form.valid);
    });

    // Watch dateNaissance for age calculation
    this.form.get('dateNaissance')!.valueChanges.subscribe(val => {
      this.dateNaissanceSignal.set(val instanceof Date ? val : (val ? new Date(val) : null));
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

  onPhoto(event: Event): void {
    if (this.readonly) return;
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      this.photoPreview.set(reader.result as string);
      this.dataChange.emit({ ...this.form.value, photoBase64: reader.result });
    };
    reader.readAsDataURL(file);
  }

  openMedicalRecord(): void {
    if (!this.isMedecin()) return;
    // Placeholder: this will open dedicated medical record module when available
    console.info('Dossier medical opened for MEDECIN profile');
  }

  markTouched(): void { this.form.markAllAsTouched(); }
  isValid(): boolean { return this.form.valid; }

  patchData(data: Record<string, any>): void {
    if (!this.form) return;
    const patch = {
      civilite: data['civilite'] ?? '',
      nom: data['nom'] ?? '',
      prenom: data['prenom'] ?? '',
      sexe: data['sexe'] ?? '',
      groupeSanguin: data['groupeSanguin'] ?? '',
      nombreEnfants: data['nombreEnfants'] ?? 0,
      dateAdmission: data['dateAdmission'] ?? null,
      dateNaissance: data['dateNaissance'] ?? null,
      lieuNaissance: data['lieuNaissance'] ?? '',
      situationFamiliale: data['situationFamiliale'] ?? '',
      profession: data['profession'] ?? '',
      telMobile: data['telMobile'] ?? '',
      telPersonnel: data['telPersonnel'] ?? '',
      telBureau: data['telBureau'] ?? '',
      email: data['email'] ?? '',
      adresse: data['adresse'] ?? '',
      epoEnabled: data['epoEnabled'] ?? false,
      epoDate: data['epoDate'] ?? null,
      ferEnabled: data['ferEnabled'] ?? false,
      ferDate: data['ferDate'] ?? null,
      sousKt: data['sousKt'] ?? false,
      enSommeil: data['enSommeil'] ?? false,
      etatPatient: data['etatPatient'] ?? 'PERMANENT',
      dateEvenementEtat: data['dateEvenementEtat'] ?? data['dateEvenement'] ?? null,
      qualiteAssure: data['qualiteAssure'] ?? 'ASSURE_LUI_MEME',
      observation: data['observation'] ?? ''
    };
    this.form.patchValue(patch, { emitEvent: false });
    this.etatPatientSignal.set(patch.etatPatient || 'PERMANENT');
    if (data['photoBase64']) this.photoPreview.set(data['photoBase64']);
    this.dataChange.emit({ ...this.form.getRawValue(), photoBase64: data['photoBase64'] });
    this.validChange.emit(this.form.valid);
    this.applyReadonly();
  }
}
