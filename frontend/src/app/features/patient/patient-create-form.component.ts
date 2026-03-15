import { Component, EventEmitter, inject, Input, Output, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatDividerModule } from '@angular/material/divider';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatRadioModule } from '@angular/material/radio';
import { MatTabsModule } from '@angular/material/tabs';
import { TranslateModule } from '@ngx-translate/core';
import { SearchableSelectComponent, DropdownItem } from '../../shared/searchable-select.component';
import { ReferentialApiService } from '../../core/api/referential-api.service';
import { AppShellStore } from '../../core/state/app-shell.store';

export interface PatientFormData {
  photoBase64: string;
  codePatient: string;
  civilite: string;
  nom: string;
  prenom: string;
  sexe: string;
  groupeSanguin: string;
  nombreEnfants: number;
  dateAdmission: string;
  enSommeil: boolean;
  dateNaissance: string;
  age: number;
  jugement: boolean;
  jugementAnnee: string;
  lieuNaissance: string;
  situationFamiliale: string;
  profession1: string;
  profession2: string;
  adresse: string;
  telPersonnel: string;
  telMobile: string;
  telBureau: string;
  email: string;
  // Assurance
  numeroAssurance: string;
  centrePaye: string;
  codeCentrePayeur: string;
  codeAgence: string;
  agence: string;
  caisse: string;
  adresseCentrePayeur: string;
  // Qualité
  qualiteAssure: string;
  // Assuré
  assureCode: string;
  assureSexe: string;
  assureNom: string;
  assurePrenom: string;
  assureDateNaissance: string;
  assureJugement: boolean;
  assureJugementDate: string;
  assureTelPersonnel: string;
  assureAdresse: string;
  assureTelMobile: string;
  assureTelBureau: string;
  assureGroupeSanguin: string;
  // Affectation
  medecinTraitant: string;
  salle: string;
  etatPatient: string;
  position: string;
  categorieTransport: string;
  transporteurAller: string;
  transporteurRetour: string;
  // Jours de dialyse
  jourDimanche: boolean;
  jourLundi: boolean;
  jourMardi: boolean;
  jourMercredi: boolean;
  jourJeudi: boolean;
  jourVendredi: boolean;
  jourSamedi: boolean;
  sousKt: boolean;
  // EPO / FER
  epoEnabled: boolean;
  epoDate: string;
  ferEnabled: boolean;
  ferDate: string;
  // Notes
  observation: string;
  // Attestation
  attestationDebut: string;
  attestationFin: string;
  typePatient: string;
  // PJ
  piecesJointes: { name: string; dataUrl: string }[];
}

@Component({
  selector: 'app-patient-create-form',
  standalone: true,
  imports: [
    ReactiveFormsModule, MatCardModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatButtonModule, MatIconModule, MatCheckboxModule,
    MatDatepickerModule, MatNativeDateModule, MatDividerModule, MatTooltipModule,
    MatRadioModule, MatTabsModule, TranslateModule, SearchableSelectComponent
  ],
  templateUrl: './patient-create-form.component.html',
  styleUrl: './patient-create-form.component.scss'
})
export class PatientCreateFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly refApi = inject(ReferentialApiService);
  private readonly store = inject(AppShellStore);

  @Input() visible = false;
  @Output() saved = new EventEmitter<PatientFormData>();
  @Output() cancelled = new EventEmitter<void>();

  photoPreview = signal<string | null>(null);
  piecesJointes = signal<{ name: string; dataUrl: string }[]>([]);
  generatedCode = signal('PAT-' + Math.random().toString(36).substring(2, 8).toUpperCase());

  // Dropdown items from backend
  centresPayeurs = signal<DropdownItem[]>([]);
  medecins = signal<DropdownItem[]>([]);
  salles = signal<DropdownItem[]>([]);
  positions = signal<DropdownItem[]>([]);
  transporteurs = signal<DropdownItem[]>([]);

  // Selected IDs for searchable selects
  selectedCentrePayeurId = signal<string | null>(null);
  selectedMedecinId = signal<string | null>(null);
  selectedSalleId = signal<string | null>(null);
  selectedPositionId = signal<string | null>(null);
  selectedTransporteurAllerId = signal<string | null>(null);
  selectedTransporteurRetourId = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    photoBase64: [''],
    civilite: [''],
    nom: ['', Validators.required],
    prenom: ['', Validators.required],
    sexe: ['', Validators.required],
    groupeSanguin: [''],
    nombreEnfants: [0],
    dateAdmission: ['', Validators.required],
    enSommeil: [false],
    dateNaissance: ['', Validators.required],
    age: [{ value: 0, disabled: true }],
    jugement: [false],
    jugementAnnee: [''],
    lieuNaissance: [''],
    situationFamiliale: [''],
    profession1: [''],
    profession2: [''],
    adresse: [''],
    telPersonnel: [''],
    telMobile: [''],
    telBureau: [''],
    email: ['', Validators.email],
    // Assurance
    numeroAssurance: ['', Validators.required],
    centrePaye: [''],
    codeCentrePayeur: [{ value: '', disabled: true }],
    codeAgence: [{ value: '', disabled: true }],
    agence: [{ value: '', disabled: true }],
    caisse: [{ value: '', disabled: true }],
    adresseCentrePayeur: [''],
    qualiteAssure: ['ASSURE_LUI_MEME'],
    // Assuré
    assureCode: [{ value: '', disabled: true }],
    assureSexe: [''],
    assureNom: [''],
    assurePrenom: [''],
    assureDateNaissance: [''],
    assureJugement: [false],
    assureJugementDate: [''],
    assureTelPersonnel: [''],
    assureAdresse: [''],
    assureTelMobile: [''],
    assureTelBureau: [''],
    assureGroupeSanguin: [''],
    // Affectation
    medecinTraitant: [''],
    salle: [''],
    etatPatient: ['PERMANENT'],
    position: [''],
    categorieTransport: [''],
    transporteurAller: [''],
    transporteurRetour: [''],
    // Jours
    jourDimanche: [false],
    jourLundi: [false],
    jourMardi: [false],
    jourMercredi: [false],
    jourJeudi: [false],
    jourVendredi: [false],
    jourSamedi: [false],
    sousKt: [false],
    epoEnabled: [false],
    epoDate: [''],
    ferEnabled: [false],
    ferDate: [''],
    observation: [''],
    attestationDebut: [''],
    attestationFin: [''],
    typePatient: ['NON_VACANCIER', Validators.required]
  });

  get showAssureSection(): boolean {
    return this.form.get('qualiteAssure')!.value !== 'ASSURE_LUI_MEME';
  }

  get joursDialyseLabel(): string {
    const days: string[] = [];
    if (this.form.get('jourDimanche')!.value) days.push('dimanche');
    if (this.form.get('jourLundi')!.value) days.push('lundi');
    if (this.form.get('jourMardi')!.value) days.push('mardi');
    if (this.form.get('jourMercredi')!.value) days.push('mercredi');
    if (this.form.get('jourJeudi')!.value) days.push('jeudi');
    if (this.form.get('jourVendredi')!.value) days.push('vendredi');
    if (this.form.get('jourSamedi')!.value) days.push('samedi');
    return days.join('/') || '—';
  }

  ngOnInit(): void {
    // Load referential data
    const centerId = this.store.currentCenterId();
    if (centerId) {
      this.refApi.getCentresPayeurs(centerId).subscribe(list =>
        this.centresPayeurs.set(list.map(i => ({ ...i, id: i.id, label: `${i.nom} (${i.code ?? ''})` })))
      );
      this.refApi.getMedecins(centerId).subscribe(list =>
        this.medecins.set(list.map(i => ({ ...i, id: i.id, label: `${i.nom} ${i.prenom ?? ''}`.trim() })))
      );
      this.refApi.getSalles(centerId).subscribe(list =>
        this.salles.set(list.map(i => ({ ...i, id: i.id, label: `${i.code ?? ''} - ${i.nom}` })))
      );
      this.refApi.getPositions(centerId).subscribe(list =>
        this.positions.set(list.map(i => ({ ...i, id: i.id, label: `${i.code ?? ''} - ${i.libelle ?? i.nom}` })))
      );
      this.refApi.getTransporteurs(centerId).subscribe(list =>
        this.transporteurs.set(list.map(i => ({ ...i, id: i.id, label: i.nom })))
      );
    }

    // Calculate age from dateNaissance
    this.form.get('dateNaissance')!.valueChanges.subscribe(val => {
      if (val) {
        const birth = new Date(val);
        const today = new Date();
        let age = today.getFullYear() - birth.getFullYear();
        const m = today.getMonth() - birth.getMonth();
        if (m < 0 || (m === 0 && today.getDate() < birth.getDate())) age--;
        this.form.get('age')!.setValue(age);
      }
    });

    // Centre payeur selection fills readonly fields
    this.form.get('centrePaye')!.valueChanges.subscribe(val => {
      // Simulate: when center payeur is selected, fill the readonly codes
      if (val) {
        this.form.patchValue({
          codeCentrePayeur: val.substring(0, 5),
          codeAgence: '23',
          agence: 'Annaba',
          caisse: 'CNAS'
        });
      }
    });

    // Attestation conditionally required
    this.form.get('typePatient')!.valueChanges.subscribe(val => {
      const deb = this.form.get('attestationDebut')!;
      const fin = this.form.get('attestationFin')!;
      if (val !== 'VACANCIER') {
        deb.setValidators(Validators.required);
        fin.setValidators(Validators.required);
      } else {
        deb.clearValidators();
        fin.clearValidators();
      }
      deb.updateValueAndValidity();
      fin.updateValueAndValidity();
    });

    // Assure required fields
    this.form.get('qualiteAssure')!.valueChanges.subscribe(val => {
      const n = this.form.get('assureNom')!;
      const p = this.form.get('assurePrenom')!;
      if (val !== 'ASSURE_LUI_MEME') {
        n.setValidators(Validators.required);
        p.setValidators(Validators.required);
      } else {
        n.clearValidators();
        p.clearValidators();
      }
      n.updateValueAndValidity();
      p.updateValueAndValidity();
    });
  }

  onCentrePayeurSelected(item: DropdownItem | null): void {
    this.selectedCentrePayeurId.set(item?.id ?? null);
    if (item) {
      this.form.patchValue({
        codeCentrePayeur: item['code'] ?? '',
        adresseCentrePayeur: item['adresse'] ?? ''
      });
      // Load agence/caisse info via the item's agenceId
    }
  }

  onMedecinSelected(item: DropdownItem | null): void { this.selectedMedecinId.set(item?.id ?? null); }
  onSalleSelected(item: DropdownItem | null): void { this.selectedSalleId.set(item?.id ?? null); }
  onPositionSelected(item: DropdownItem | null): void { this.selectedPositionId.set(item?.id ?? null); }
  onTransporteurAllerSelected(item: DropdownItem | null): void { this.selectedTransporteurAllerId.set(item?.id ?? null); }
  onTransporteurRetourSelected(item: DropdownItem | null): void { this.selectedTransporteurRetourId.set(item?.id ?? null); }

  onPhotoSelected(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      const base64 = reader.result as string;
      this.photoPreview.set(base64);
      this.form.patchValue({ photoBase64: base64 });
    };
    reader.readAsDataURL(file);
  }

  removePhoto(): void {
    this.photoPreview.set(null);
    this.form.patchValue({ photoBase64: '' });
  }

  onPieceJointeSelected(event: Event): void {
    const files = (event.target as HTMLInputElement).files;
    if (!files) return;
    Array.from(files).forEach(file => {
      const reader = new FileReader();
      reader.onload = () => {
        this.piecesJointes.update(pj => [...pj, { name: file.name, dataUrl: reader.result as string }]);
      };
      reader.readAsDataURL(file);
    });
  }

  removePieceJointe(index: number): void {
    this.piecesJointes.update(pj => pj.filter((_, i) => i !== index));
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    this.saved.emit({
      ...raw,
      codePatient: this.generatedCode(),
      centrePayeurId: this.selectedCentrePayeurId(),
      medecinTraitantId: this.selectedMedecinId(),
      salleId: this.selectedSalleId(),
      positionId: this.selectedPositionId(),
      transporteurAllerId: this.selectedTransporteurAllerId(),
      transporteurRetourId: this.selectedTransporteurRetourId(),
      piecesJointes: this.piecesJointes()
    } as any);
  }

  cancel(): void {
    this.form.reset({
      qualiteAssure: 'ASSURE_LUI_MEME', typePatient: 'NON_VACANCIER',
      etatPatient: 'PERMANENT', nombreEnfants: 0, sousKt: false,
      epoEnabled: false, ferEnabled: false, enSommeil: false, jugement: false
    });
    this.photoPreview.set(null);
    this.piecesJointes.set([]);
    this.cancelled.emit();
  }
}
