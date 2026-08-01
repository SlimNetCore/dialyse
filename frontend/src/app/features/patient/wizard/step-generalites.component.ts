import {
  ChangeDetectionStrategy,
  Component,
  computed,
  EventEmitter,
  inject,
  Input,
  OnChanges,
  Output,
  signal,
  SimpleChanges,
} from '@angular/core';
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
import {requiredValidator, SignalForm} from '../../../shared/forms/signal-form';

const PATIENT_STATES_WITH_EVENT_DATE = new Set([
  'OCCASIONNEL',
  'VACANCIER_LOCAL',
  'VACANCIER_ETRANGER',
  'TRANSFERE',
  'DECEDE',
  'GREFFE',
  'GUERRI',
]);

interface GeneralitesModel {
  civilite: string;
  nom: string;
  prenom: string;
  sexe: string;
  groupeSanguin: string;
  nombreEnfants: number;
  dateAdmission: Date | string | null;
  dateNaissance: Date | string | null;
  lieuNaissance: string;
  situationFamiliale: string;
  profession: string;
  telMobile: string;
  telPersonnel: string;
  telBureau: string;
  email: string;
  adresse: string;
  epoEnabled: boolean;
  epoDate: Date | string | null;
  ferEnabled: boolean;
  ferDate: Date | string | null;
  sousKt: boolean;
  enSommeil: boolean;
  etatPatient: string;
  dateEvenementEtat: Date | string | null;
  qualiteAssure: string;
  observation: string;
}

type GeneralitesTextKey =
  | 'nom'
  | 'prenom'
  | 'lieuNaissance'
  | 'profession'
  | 'telMobile'
  | 'telPersonnel'
  | 'telBureau'
  | 'email'
  | 'adresse'
  | 'observation';

type GeneralitesSelectKey = 'civilite' | 'sexe' | 'groupeSanguin' | 'situationFamiliale';

type GeneralitesToggleKey = 'epoEnabled' | 'ferEnabled' | 'sousKt' | 'enSommeil';

type GeneralitesDateKey = 'dateAdmission' | 'dateNaissance' | 'epoDate' | 'ferDate';

@Component({
  selector: 'app-step-generalites',
  standalone: true,
  imports: [
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
export class StepGeneralitesComponent implements OnChanges {
  @Input() stepData: Record<string, any> | null = null;
  @Input() readonly = false;
  @Output() dataChange = new EventEmitter<Record<string, any>>();
  @Output() validChange = new EventEmitter<boolean>();

  readonly form = new SignalForm<GeneralitesModel>(
    {
      civilite: '',
      nom: '',
      prenom: '',
      sexe: '',
      groupeSanguin: '',
      nombreEnfants: 0,
      dateAdmission: null,
      dateNaissance: null,
      lieuNaissance: '',
      situationFamiliale: '',
      profession: '',
      telMobile: '',
      telPersonnel: '',
      telBureau: '',
      email: '',
      adresse: '',
      epoEnabled: false,
      epoDate: null,
      ferEnabled: false,
      ferDate: null,
      sousKt: false,
      enSommeil: false,
      etatPatient: 'PERMANENT',
      dateEvenementEtat: null,
      qualiteAssure: 'ASSURE_LUI_MEME',
      observation: '',
    },
    {
      nom: [requiredValidator()],
      prenom: [requiredValidator()],
      sexe: [requiredValidator()],
      dateAdmission: [requiredValidator()],
      dateNaissance: [requiredValidator()],
    },
  );

  private readonly auth = inject(AuthStore);
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

  readonly isMedecin = computed(() => this.auth.hasRole('ROLE_MEDECIN'));
  readonly showDateEvenement = computed(() =>
    PATIENT_STATES_WITH_EVENT_DATE.has(this.form.value().etatPatient),
  );
  readonly dateEvenementLabelKey = computed(() => {
    switch (this.form.value().etatPatient) {
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

  readonly calculatedAge = computed(() => {
    const dob = this.toDate(this.form.value().dateNaissance);
    if (!dob) return null;
    const today = new Date();
    let age = today.getFullYear() - dob.getFullYear();
    const m = today.getMonth() - dob.getMonth();
    if (m < 0 || (m === 0 && today.getDate() < dob.getDate())) age--;
    return age >= 0 ? age : null;
  });

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['readonly']) this.applyReadonly();
    const stepDataChange = changes['stepData'];
    if (stepDataChange?.currentValue && typeof stepDataChange.currentValue === 'object') {
      const incoming = stepDataChange.currentValue as Record<string, any>;
      if (Object.keys(incoming).length > 0) this.patchData(incoming);
    }
  }

  onText(key: GeneralitesTextKey, value: string): void {
    if (this.readonly) return;
    this.form.set(key, value ?? '');
    this.emit();
  }

  onNombreEnfants(value: string): void {
    if (this.readonly) return;
    const parsed = Number(value);
    this.form.set('nombreEnfants', Number.isFinite(parsed) ? parsed : 0);
    this.emit();
  }

  onSelect(key: GeneralitesSelectKey, item: DropdownItem | null): void {
    if (this.readonly) return;
    this.form.set(key, item?.id ?? '');
    this.emit();
  }

  onEtatPatientChange(id: string | null): void {
    if (this.readonly) return;
    const etat = id ?? 'PERMANENT';
    this.form.set('etatPatient', etat);
    if (!PATIENT_STATES_WITH_EVENT_DATE.has(etat)) {
      this.form.set('dateEvenementEtat', null);
    }
    this.emit();
  }

  onToggle(key: GeneralitesToggleKey, checked: boolean): void {
    if (this.readonly) return;
    this.form.set(key, checked);
    this.emit();
  }

  onDate(key: GeneralitesDateKey, value: Date | null): void {
    if (this.readonly) return;
    this.form.set(key, value);
    this.form.markTouched(key);
    this.emit();
  }

  onDateEvenement(value: Date | null): void {
    if (this.readonly) return;
    this.form.set('dateEvenementEtat', value);
    this.emit();
  }

  onQualiteAssure(value: string): void {
    if (this.readonly) return;
    this.form.set('qualiteAssure', value);
    this.emit();
  }

  markTouched(): void {
    this.form.markAllTouched();
  }

  onPhoto(event: Event): void {
    if (this.readonly) return;
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      this.photoPreview.set(reader.result as string);
      this.dataChange.emit({...this.form.value(), photoBase64: reader.result});
    };
    reader.readAsDataURL(file);
  }

  openMedicalRecord(): void {
    if (!this.isMedecin()) return;
    console.info('Dossier medical opened for MEDECIN profile');
  }

  isValid(): boolean {
    return this.form.valid();
  }

  patchData(data: Record<string, any>): void {
    const etat = data['etatPatient'] ?? 'PERMANENT';
    this.form.patch({
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
      etatPatient: etat,
      dateEvenementEtat: data['dateEvenementEtat'] ?? data['dateEvenement'] ?? null,
      qualiteAssure: data['qualiteAssure'] ?? 'ASSURE_LUI_MEME',
      observation: data['observation'] ?? '',
    });
    if (data['photoBase64']) this.photoPreview.set(data['photoBase64']);
    this.validChange.emit(this.form.valid());
    this.applyReadonly();
  }

  private emit(): void {
    this.dataChange.emit({...this.form.value()});
    this.validChange.emit(this.form.valid());
  }

  private applyReadonly(): void {
    this.form.setDisabled(this.readonly);
  }

  private toDate(value: Date | string | null): Date | null {
    if (!value) return null;
    if (value instanceof Date) return value;
    const parsed = new Date(value);
    return Number.isNaN(parsed.getTime()) ? null : parsed;
  }
}
