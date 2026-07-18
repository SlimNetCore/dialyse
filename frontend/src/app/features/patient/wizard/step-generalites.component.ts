import {
  AfterViewInit,
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  computed,
  EventEmitter,
  inject,
  Input,
  OnChanges,
  OnInit,
  Output,
  signal,
  SimpleChanges,
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
import {DropdownItem, SearchableSelectComponent,} from '../../../shared/searchable-select.component';

const PATIENT_STATES_WITH_EVENT_DATE = new Set([
  'OCCASIONNEL',
  'VACANCIER_LOCAL',
  'VACANCIER_ETRANGER',
  'TRANSFERE',
  'DECEDE',
  'GREFFE',
  'GUERRI',
]);

@Component({
  selector: 'app-step-generalites',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatCheckboxModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatIconModule,
    MatRadioModule,
    MatButtonModule,
    TranslateModule,
    SearchableSelectComponent,
  ],
  templateUrl: './step-generalites.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './step-generalites.component.css',
})
export class StepGeneralitesComponent implements OnInit, OnChanges, AfterViewInit {
  @Input() stepData: Record<string, any> | null = null;
  @Input() readonly = false;
  @Output() dataChange = new EventEmitter<Record<string, any>>();
  @Output() validChange = new EventEmitter<boolean>();

  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthStore);
  private readonly cdr = inject(ChangeDetectorRef);
  readonly civiliteOptions: DropdownItem[] = [
    {id: 'M.', label: 'PATIENT_FORM.MR'},
    {id: 'Mme', label: 'PATIENT_FORM.MRS'},
    {id: 'Mlle', label: 'PATIENT_FORM.MS'},
  ];
  readonly sexeOptions: DropdownItem[] = [
    {id: 'M', label: 'PATIENT_FORM.MASCULIN'},
    {id: 'F', label: 'PATIENT_FORM.FEMININ'},
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
    {id: 'O-', label: 'O-'},
  ];
  readonly etatPatientOptions: DropdownItem[] = [
    {id: 'PERMANENT', label: 'PATIENT_FORM.PERMANENT'},
    {id: 'OCCASIONNEL', label: 'PATIENT_FORM.OCCASIONNEL'},
    {id: 'TRANSFERE', label: 'PATIENT_FORM.TRANSFERE'},
    {id: 'DECEDE', label: 'PATIENT_FORM.DECEDE'},
    {id: 'GREFFE', label: 'PATIENT_FORM.GREFFE'},
    {id: 'GUERRI', label: 'PATIENT_FORM.GUERRI'},
    {id: 'VACANCIER_LOCAL', label: 'PATIENT_FORM.VACANCIER_LOCAL'},
    {id: 'VACANCIER_ETRANGER', label: 'PATIENT_FORM.VACANCIER_ETRANGER'},
  ];
  readonly situationFamilialeOptions: DropdownItem[] = [
    {id: '', label: '—'},
    {id: 'CELIBATAIRE', label: 'PATIENT_FORM.CELIBATAIRE'},
    {id: 'MARIE', label: 'PATIENT_FORM.MARIE'},
    {id: 'DIVORCE', label: 'PATIENT_FORM.DIVORCE'},
    {id: 'VEUF', label: 'PATIENT_FORM.VEUF'},
  ];
  photoPreview = signal<string | null>(null);
  private dateNaissanceSignal = signal<Date | null>(null);
  // Signals pour les SearchableSelectComponent (nécessaire en mode zoneless : form.get()?.value n'est pas réactif)
  readonly civiliteSignal = signal<string>('');
  readonly sexeSignal = signal<string>('');
  readonly groupeSanguinSignal = signal<string>('');
  readonly etatPatientSignal = signal<string>('PERMANENT');
  readonly situationFamilialeSignal = signal<string>('');

  readonly isMedecin = computed(() => this.auth.hasRole('ROLE_MEDECIN'));
  readonly showDateEvenement = computed(() => {
    return PATIENT_STATES_WITH_EVENT_DATE.has(this.etatPatientSignal());
  });
  readonly dateEvenementLabelKey = computed(() => {
    switch (this.etatPatientSignal()) {
      case 'OCCASIONNEL':
      case 'VACANCIER_LOCAL':
      case 'VACANCIER_ETRANGER':
        return 'PATIENT_FORM.DATE_SORTIE';
      case 'GREFFE':
        return 'PATIENT_FORM.DATE_GREFFE';
      case 'GUERRI':
        return 'PATIENT_FORM.DATE_GUERISON';
      case 'DECEDE':
        return 'PATIENT_FORM.DATE_DECES';
      case 'TRANSFERE':
        return 'PATIENT_FORM.DATE_TRANSFERT';
      default:
        return 'PATIENT_FORM.DATE_EVENEMENT_ETAT';
    }
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
  private pendingPatchData: Record<string, any> | null = null;
  private viewInitialized = false;

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
      observation: [''],
    });

    this.form.valueChanges.subscribe((val) => {
      // clear event date for states that do not require it
      if (!PATIENT_STATES_WITH_EVENT_DATE.has(val.etatPatient ?? 'PERMANENT') && val.dateEvenementEtat) {
        this.form.patchValue({ dateEvenementEtat: null }, { emitEvent: false });
        val.dateEvenementEtat = null;
      }
      // Mettre à jour tous les signals pour forcer la réévaluation des SearchableSelectComponent
      this.civiliteSignal.set(val.civilite ?? '');
      this.sexeSignal.set(val.sexe ?? '');
      this.groupeSanguinSignal.set(val.groupeSanguin ?? '');
      this.etatPatientSignal.set(val.etatPatient || 'PERMANENT');
      this.situationFamilialeSignal.set(val.situationFamiliale ?? '');
      this.dataChange.emit(val);
      this.validChange.emit(this.form.valid);
    });

    // Watch dateNaissance for age calculation
    this.form.get('dateNaissance')!.valueChanges.subscribe((val) => {
      this.dateNaissanceSignal.set(val instanceof Date ? val : val ? new Date(val) : null);
    });

    this.applyReadonly();

    // In consultation mode, wizard can call patchData before form init.
    // Replay buffered data now that controls exist.
    if (this.pendingPatchData) {
      const buffered = this.pendingPatchData;
      this.pendingPatchData = null;
      this.patchData(buffered);
    }
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['readonly']) this.applyReadonly();
    const stepDataChange = changes['stepData'];
    if (stepDataChange?.currentValue && typeof stepDataChange.currentValue === 'object') {
      const incoming = stepDataChange.currentValue as Record<string, any>;
      if (Object.keys(incoming).length > 0) this.patchData(incoming);
    }
  }

  ngAfterViewInit(): void {
    this.viewInitialized = true;
    if (this.pendingPatchData) {
      const buffered = this.pendingPatchData;
      this.pendingPatchData = null;
      this.patchData(buffered);
    }
  }

  markTouched(): void {
    this.form.markAllAsTouched();
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

  isValid(): boolean {
    return this.form.valid;
  }

  patchData(data: Record<string, any>): void {
    if (!this.form) {
      this.pendingPatchData = data;
      return;
    }
    if (!this.viewInitialized) {
      this.pendingPatchData = data;
    }
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
      observation: data['observation'] ?? '',
    };
    // Activer temporairement pour patcher les valeurs et calculer la validité correctement
    const wasDisabled = this.form.disabled;
    if (wasDisabled) this.form.enable({emitEvent: false});
    this.form.patchValue(patch, { emitEvent: false });
    // Mettre à jour tous les signals SearchableSelectComponent — nécessaire en mode zoneless
    this.civiliteSignal.set(patch.civilite);
    this.sexeSignal.set(patch.sexe);
    this.groupeSanguinSignal.set(patch.groupeSanguin);
    this.etatPatientSignal.set(patch.etatPatient || 'PERMANENT');
    this.situationFamilialeSignal.set(patch.situationFamiliale);
    this.dateNaissanceSignal.set(
      patch.dateNaissance instanceof Date
        ? patch.dateNaissance
        : patch.dateNaissance
          ? new Date(patch.dateNaissance)
          : null,
    );
    if (data['photoBase64']) this.photoPreview.set(data['photoBase64']);
    // Émettre la validité AVANT de désactiver le form (sinon form.valid = false pour form disabled)
    this.validChange.emit(this.form.valid);
    // Restaurer l'état disabled / enabled selon readonly
    this.applyReadonly();
    // In zoneless mode, patchValue with emitEvent:false may not trigger an immediate check.
    // Force a local view refresh so consultation mode shows data on first paint.
    this.cdr.detectChanges();
  }

  private applyReadonly(): void {
    if (!this.form) return;
    this.readonly
      ? this.form.disable({emitEvent: false})
      : this.form.enable({emitEvent: false});
  }
}
