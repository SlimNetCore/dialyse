import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  EventEmitter,
  inject,
  Input,
  OnChanges,
  OnInit,
  Output,
  signal,
  SimpleChanges,
  untracked,
} from '@angular/core';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatDatepickerModule} from '@angular/material/datepicker';
import {MatNativeDateModule} from '@angular/material/core';
import {MatIconModule} from '@angular/material/icon';
import {MatDividerModule} from '@angular/material/divider';
import {MatButtonModule} from '@angular/material/button';
import {MatSnackBar} from '@angular/material/snack-bar';
import {MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef,} from '@angular/material/dialog';
import {TranslateModule} from '@ngx-translate/core';
import {DropdownItem, SearchableSelectComponent,} from '../../../shared/searchable-select.component';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {CentresPayeursDetailsStore, CentresPayeursStore,} from '../../../core/state/referentials.store';
import {PatientFicheStore} from '../state/patient-fiche.store';
import {consumeWizardActionStatus} from './wizard-action-status.util';
import {requiredValidator, SignalForm} from '../../../shared/forms/signal-form';

interface AssignmentEdit {
  id: string;
  dateDebutAffectation: Date | null;
  dateFinAffectation: Date | null;
}

interface AssureEditModel {
  nom: string;
  prenom: string;
  sexe: string;
  dateNaissance: Date | string | null;
  groupeSanguin: string;
  telPersonnel: string;
  telMobile: string;
  telBureau: string;
  adresse: string;
}

interface AssuranceModel {
  numeroAssurance: string;
  centrePayeurId: string | null;
  assureNumeroAssurance: string;
  assureNom: string;
  assurePrenom: string;
  assureSexe: string;
  assureDateNaissance: Date | string | null;
  assureTelPersonnel: string;
  assureTelMobile: string;
  assureTelBureau: string;
  assureGroupeSanguin: string;
  assureAdresse: string;
}

const SEXE_OPTIONS: DropdownItem[] = [
  {id: '', label: '—'},
  {id: 'M', label: 'Masculin'},
  {id: 'F', label: 'Feminin'},
];

const GROUPE_SANGUIN_OPTIONS: DropdownItem[] = [
  {id: '', label: '—'},
  {id: 'A+', label: 'A+'},
  {id: 'A-', label: 'A-'},
  {id: 'B+', label: 'B+'},
  {id: 'B-', label: 'B-'},
  {id: 'O+', label: 'O+'},
  {id: 'O-', label: 'O-'},
  {id: 'AB+', label: 'AB+'},
  {id: 'AB-', label: 'AB-'},
];

// ─────────────────────────────────────────────────────────────────────────────
// Dialog : modification des informations d'un assuré
// ─────────────────────────────────────────────────────────────────────────────
@Component({
  selector: 'app-assure-edit-dialog',
  standalone: true,
  imports: [
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatIconModule,
    MatButtonModule,
    MatDialogModule,
    SearchableSelectComponent,
  ],
  templateUrl: './assure-edit-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './assure-edit-dialog.component.css',
})
export class AssureEditDialogComponent {
  readonly dialogRef = inject(MatDialogRef<AssureEditDialogComponent>);
  readonly data = inject<any>(MAT_DIALOG_DATA);
  readonly sexeOptions = SEXE_OPTIONS;
  readonly groupeSanguinOptions = GROUPE_SANGUIN_OPTIONS;

  readonly form = new SignalForm<AssureEditModel>(
    {
      nom: this.data.nom ?? '',
      prenom: this.data.prenom ?? '',
      sexe: this.data.sexe ?? '',
      dateNaissance: this.data.dateNaissance ? new Date(this.data.dateNaissance) : null,
      groupeSanguin: this.data.groupeSanguin ?? '',
      telPersonnel: this.data.telPersonnel ?? '',
      telMobile: this.data.telMobile ?? '',
      telBureau: this.data.telBureau ?? '',
      adresse: this.data.adresse ?? '',
    },
    {
      nom: [requiredValidator()],
      prenom: [requiredValidator()],
    },
  );

  onText(key: keyof AssureEditModel, value: string): void {
    this.form.set(key, value ?? '');
  }

  onSelect(key: 'sexe' | 'groupeSanguin', item: DropdownItem | null): void {
    this.form.set(key, item?.id ?? '');
  }

  onDate(value: Date | null): void {
    this.form.set('dateNaissance', value);
  }

  cancel(): void {
    this.dialogRef.close(null);
  }

  save(): void {
    if (this.form.invalid()) {
      this.form.markAllTouched();
      return;
    }
    const val = this.form.value();
    const dn = val.dateNaissance;
    this.dialogRef.close({
      ...val,
      dateNaissance: dn instanceof Date ? dn.toISOString().slice(0, 10) : (dn ?? null),
    });
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// Composant principal : étape assurance
// ─────────────────────────────────────────────────────────────────────────────
@Component({
  selector: 'app-step-assurance',
  standalone: true,
  imports: [
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatIconModule,
    MatDividerModule,
    MatButtonModule,
    MatDialogModule,
    TranslateModule,
    SearchableSelectComponent,
  ],
  templateUrl: './step-assurance.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './step-assurance.component.css',
})
export class StepAssuranceComponent implements OnInit, OnChanges {
  @Input() patientId?: string;
  @Input() readonly = false;
  @Output() dataChange = new EventEmitter<Record<string, any>>();
  @Output() validChange = new EventEmitter<boolean>();

  readonly form = new SignalForm<AssuranceModel>(
    {
      numeroAssurance: '',
      centrePayeurId: null,
      assureNumeroAssurance: '',
      assureNom: '',
      assurePrenom: '',
      assureSexe: '',
      assureDateNaissance: null,
      assureTelPersonnel: '',
      assureTelMobile: '',
      assureTelBureau: '',
      assureGroupeSanguin: '',
      assureAdresse: '',
    },
    {
      numeroAssurance: [requiredValidator()],
    },
  );

  readonly assureSexeOptions: DropdownItem[] = [
    {id: 'M', label: 'PATIENT_FORM.MASCULIN'},
    {id: 'F', label: 'PATIENT_FORM.FEMININ'},
  ];
  readonly assureGroupeSanguinOptions = GROUPE_SANGUIN_OPTIONS;

  private readonly appShell = inject(AppShellStore);
  private readonly centresPayeursStore = inject(CentresPayeursStore);
  readonly centresPayeurs = this.centresPayeursStore.items as unknown as () => DropdownItem[];
  private readonly centresPayeursDetailsStore = inject(CentresPayeursDetailsStore);
  private readonly snackBar = inject(MatSnackBar);
  editingAssignment = signal<AssignmentEdit | null>(null);
  private readonly ficheStore = inject(PatientFicheStore);
  readonly assureCatalog = this.ficheStore.assureCatalog;
  readonly assureAssignments = this.ficheStore.assureAssignments;
  readonly loadingAssures = this.ficheStore.loadingAssures;
  readonly savingEdit = this.ficheStore.savingAssureEdit;
  readonly selectedCentrePayeurId = signal<string | null>(null);
  readonly codeCentrePayeur = computed(
    () =>
      this.centresPayeurs().find(
        (x: any) => String(x?.id ?? '') === this.selectedCentrePayeurId(),
      )?.['code'] ?? '',
  );
  showAssure = signal(true);
  private qualiteAssure = signal<string>('ASSURE_LUI_MEME');
  requiresAssureNumero = signal(false);
  assureSearch = signal('');
  private readonly centresPayeursDetails = this.centresPayeursDetailsStore.items;
  private readonly selectedCentrePayeurDetail = computed<any | null>(() => {
    const id = this.selectedCentrePayeurId();
    if (!id) return null;
    return this.centresPayeursDetails().find((d: any) => String(d?.id ?? '') === id) ?? null;
  });
  readonly codeAgence = computed(() => this.selectedCentrePayeurDetail()?.codeAgence ?? '');
  showCatalog = signal(false);
  showHistory = signal(false);
  readonly libelleAgence = computed(() => this.selectedCentrePayeurDetail()?.libelleAgence ?? '');
  readonly libelleCaisse = computed(() => this.selectedCentrePayeurDetail()?.libelleCaisse ?? '');
  private readonly dialog = inject(MatDialog);
  private pendingAssignAssure = false;
  private pendingUpdateAssure = false;
  private pendingUpdateAssignment = false;
  private pendingLoadAssureHistory = false;
  private historyInFlightKey: string | null = null;
  private lastLoadedHistoryKey: string | null = null;

  constructor() {
    effect(() => {
      if (!this.selectedCentrePayeurId()) return;
      // Suivi des codes/libellés (rechargés de façon asynchrone).
      this.codeCentrePayeur();
      this.codeAgence();
      this.libelleAgence();
      this.libelleCaisse();
      untracked(() => this.emitAssuranceData());
    });

    consumeWizardActionStatus(this.ficheStore, ({action, success, error, message}) => {
      const effectiveError = error || this.ficheStore.error() || '';
      if (action === 'ASSIGN_ASSURE' && this.pendingAssignAssure) {
        this.pendingAssignAssure = false;
        if (success) {
          this.snackBar.open(
            message || this.ficheStore.infoMessage() || 'Assuré affecté au patient avec succès',
            'OK',
            {duration: 2500},
          );
        } else if (
          typeof effectiveError === 'string' &&
          effectiveError.toLowerCase().includes('assuré lui-même')
        ) {
          this.snackBar.open(
            'Assuré sélectionné localement. Enregistrez le patient puis réessayez.',
            'OK',
            {duration: 4500},
          );
        } else if (effectiveError) {
          this.snackBar.open(effectiveError, 'OK', {duration: 3500});
        }
      }

      if (action === 'UPDATE_ASSURE' && this.pendingUpdateAssure) {
        this.pendingUpdateAssure = false;
        if (success) {
          this.snackBar.open('Assuré mis à jour', 'OK', {duration: 2500});
        } else if (effectiveError) {
          this.snackBar.open(effectiveError, 'OK', {duration: 3500});
        }
      }

      if (action === 'UPDATE_ASSURE_ASSIGNMENT' && this.pendingUpdateAssignment) {
        this.pendingUpdateAssignment = false;
        if (success) {
          this.editingAssignment.set(null);
          this.snackBar.open('Affectation mise à jour', 'OK', {duration: 2500});
        } else if (effectiveError) {
          this.snackBar.open(effectiveError, 'OK', {duration: 3500});
        }
      }

      if (action === 'LOAD_ASSURE_HISTORY' && this.pendingLoadAssureHistory) {
        this.pendingLoadAssureHistory = false;
        if (success && this.historyInFlightKey) {
          this.lastLoadedHistoryKey = this.historyInFlightKey;
        }
        this.historyInFlightKey = null;
        if (!success && effectiveError) {
          this.snackBar.open(effectiveError, 'OK', {duration: 3000});
        }
      }
    });
  }

  ngOnInit(): void {
    const cid = this.appShell.currentCenterId();
    if (cid) {
      void this.centresPayeursStore.ensureLoaded(cid);
      void this.centresPayeursDetailsStore.ensureLoaded(cid);
    }
    this.applyReadonly();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['readonly']) this.applyReadonly();
    if (changes['patientId'] && this.patientId) void this.loadAssureHistory();
  }

  onNumeroAssurance(value: string): void {
    if (this.readonly) return;
    this.form.set('numeroAssurance', value ?? '');
    this.emit();
  }

  onAssureText(key: keyof AssuranceModel, value: string): void {
    if (this.readonly) return;
    this.form.set(key, value ?? '');
    this.emit();
  }

  onAssureDate(value: Date | null): void {
    if (this.readonly) return;
    this.form.set('assureDateNaissance', value);
    this.emit();
  }

  onAssureSelect(key: 'assureSexe' | 'assureGroupeSanguin', item: DropdownItem | null): void {
    if (this.readonly) return;
    this.form.set(key, item?.id ?? '');
    this.emit();
  }

  prepareNewAssure(): void {
    if (!this.canAssignAssure()) {
      this.snackBar.open(
        'Pour ajouter/affecter un assuré, choisissez ENFANT/CONJOINT/ASCENDANT/AUTRE.',
        'OK',
        {duration: 3500},
      );
      return;
    }
    this.form.patch({
      assureNumeroAssurance: '',
      assureNom: '',
      assurePrenom: '',
      assureSexe: '',
      assureDateNaissance: null,
      assureTelPersonnel: '',
      assureTelMobile: '',
      assureTelBureau: '',
      assureAdresse: '',
      assureGroupeSanguin: '',
    });
    this.emit();
  }

  setQualiteAssure(qa: string): void {
    this.qualiteAssure.set(qa || 'ASSURE_LUI_MEME');
    const needsAssure = !!(qa && qa !== 'ASSURE_LUI_MEME');
    this.showAssure.set(true);
    const needsAssureNumero = ['ENFANT', 'CONJOINT', 'ASCENDANT', 'AUTRE'].includes(qa || '');
    this.requiresAssureNumero.set(needsAssureNumero);
    if (needsAssure) {
      this.form.setValidators('assureNom', [requiredValidator()]);
      this.form.setValidators('assurePrenom', [requiredValidator()]);
    } else {
      this.form.clearValidators('assureNom');
      this.form.clearValidators('assurePrenom');
    }
    if (needsAssureNumero) {
      this.form.setValidators('assureNumeroAssurance', [requiredValidator()]);
    } else {
      this.form.clearValidators('assureNumeroAssurance');
    }
  }

  onCentrePayeur(item: DropdownItem | null): void {
    if (this.readonly) return;
    const selectedId = item ? String((item as any).id ?? '') : null;
    this.form.set('centrePayeurId', selectedId);
    this.selectedCentrePayeurId.set(selectedId);
    this.emitAssuranceData();
  }

  openEditAssureDialog(a: any): void {
    const centerId = this.appShell.currentCenterId();
    if (!centerId) return;
    const ref = this.dialog.open(AssureEditDialogComponent, {
      data: {...a},
      width: 'min(96vw, 600px)',
      disableClose: false,
    });
    ref.afterClosed().subscribe((result) => {
      if (!result) return;
      const isCurrentAssure = this.form.get('assureNumeroAssurance') === a.numeroAssurance;
      this.pendingUpdateAssure = true;
      this.ficheStore.updateAssure({
        centerId,
        numeroAssurance: a.numeroAssurance,
        payload: result,
      });

      if (isCurrentAssure) {
        this.form.patch({
          assureNom: result.nom,
          assurePrenom: result.prenom,
          assureSexe: result.sexe,
          assureDateNaissance: result.dateNaissance ?? null,
          assureTelPersonnel: result.telPersonnel,
          assureTelMobile: result.telMobile,
          assureTelBureau: result.telBureau,
          assureGroupeSanguin: result.groupeSanguin,
          assureAdresse: result.adresse,
        });
      }
    });
  }

  canAssignAssure(): boolean {
    return this.showAssure() && this.qualiteAssure() !== 'ASSURE_LUI_MEME';
  }

  toggleAssureCatalog(): void {
    this.showCatalog.set(!this.showCatalog());
    if (this.showCatalog() && this.assureCatalog().length === 0) this.searchAssures();
  }

  toggleAssureHistory(): void {
    this.showHistory.set(!this.showHistory());
    if (this.showHistory()) this.loadAssureHistory(true);
  }

  searchAssures(): void {
    const centerId = this.appShell.currentCenterId();
    if (!centerId) return;
    this.ficheStore.searchAssures({centerId, q: this.assureSearch()});
  }

  affectAssure(a: any): void {
    if (!this.canAssignAssure()) return;
    if (a?.isPrimary) return;
    const centerId = this.appShell.currentCenterId();
    if (!centerId) return;

    this.form.patch({
      assureNumeroAssurance: a.numeroAssurance,
      assureNom: a.nom ?? '',
      assurePrenom: a.prenom ?? '',
      assureSexe: a.sexe ?? '',
      assureDateNaissance: a.dateNaissance ?? null,
      assureTelPersonnel: a.telPersonnel ?? '',
      assureTelMobile: a.telMobile ?? '',
      assureTelBureau: a.telBureau ?? '',
      assureAdresse: a.adresse ?? '',
      assureGroupeSanguin: a.groupeSanguin ?? '',
    });

    this.ficheStore.assignAssure({
      centerId,
      patientId: this.patientId ?? null,
      a,
    });
    this.pendingAssignAssure = true;
  }

  openEditAssignment(h: any): void {
    this.editingAssignment.set({
      id: h.id,
      dateDebutAffectation: h.dateDebutAffectation ? new Date(h.dateDebutAffectation) : null,
      dateFinAffectation: h.dateFinAffectation ? new Date(h.dateFinAffectation) : null,
    });
  }

  saveEditAssignment(): void {
    const edit = this.editingAssignment();
    const centerId = this.appShell.currentCenterId();
    if (!edit || !this.patientId || !centerId) return;
    const toStr = (d: Date | null) => (d ? d.toISOString().slice(0, 10) : null);
    const debut = toStr(edit.dateDebutAffectation);
    const fin = toStr(edit.dateFinAffectation);
    if (debut && fin && fin < debut) {
      this.snackBar.open('La date de fin doit être >= à la date de début', 'OK', {
        duration: 3000,
      });
      return;
    }
    this.ficheStore.updateAssureAssignment({
      centerId,
      patientId: this.patientId,
      assignmentId: edit.id,
      debut,
      fin,
    });
    this.pendingUpdateAssignment = true;
  }

  setEditDateDebut(value: Date | null): void {
    const cur = this.editingAssignment();
    if (cur) this.editingAssignment.set({...cur, dateDebutAffectation: value});
  }

  setEditDateFin(value: Date | null): void {
    const cur = this.editingAssignment();
    if (cur) this.editingAssignment.set({...cur, dateFinAffectation: value});
  }

  cancelEdit(): void {
    this.editingAssignment.set(null);
  }

  patchData(data: Record<string, any>): void {
    const assureInfo = data['assureInfo'] ?? data['assure_info'] ?? {};

    const normalizeId = (value: any): string | null => {
      if (value === null || value === undefined || value === '') return null;
      if (typeof value === 'string' || typeof value === 'number') return String(value);
      if (typeof value === 'object') {
        const nested = value['value'] ?? value['id'] ?? value['ID'];
        if (nested !== undefined && nested !== null && nested !== '') return String(nested);
      }
      return String(value);
    };

    const pick = (camel: string, snake: string, fallback: any = '') =>
      data[camel] ?? data[snake] ?? fallback;

    const pickAssure = (camel: string, snake: string) =>
      data[camel] ?? data[snake] ?? assureInfo[camel] ?? assureInfo[snake] ?? '';

    const resolveCentrePayeurId = (): string | null => {
      const direct =
        data['centrePayeurId'] ??
        data['centerPayeurId'] ??
        data['centre_payeur_id'] ??
        data['center_payeur_id'];
      const normalizedDirect = normalizeId(direct);
      if (normalizedDirect) return normalizedDirect;
      const cp = data['centrePayeur'] ?? data['centerPayeur'];
      const normalizedNested = normalizeId(cp);
      if (normalizedNested) return normalizedNested;
      return null;
    };

    const qualiteFromData =
      data['qualiteAssure'] ?? data['qualite_assure'] ?? 'ASSURE_LUI_MEME';
    const isSelf = qualiteFromData === 'ASSURE_LUI_MEME';
    const patch = {
      numeroAssurance: pick('numeroAssurance', 'numero_assurance', ''),
      centrePayeurId: resolveCentrePayeurId(),
      assureNumeroAssurance:
        pick('assureNumeroAssurance', 'assure_numero_assurance', '') ||
        (isSelf ? pick('numeroAssurance', 'numero_assurance', '') : ''),
      assureNom: pickAssure('assureNom', 'assure_nom') || (isSelf ? pick('nom', 'nom', '') : ''),
      assurePrenom:
        pickAssure('assurePrenom', 'assure_prenom') || (isSelf ? pick('prenom', 'prenom', '') : ''),
      assureSexe: pickAssure('assureSexe', 'assure_sexe') || (isSelf ? pick('sexe', 'sexe', '') : ''),
      assureDateNaissance:
        pickAssure('assureDateNaissance', 'assure_date_naissance') ||
        (isSelf ? pick('dateNaissance', 'date_naissance', null) : null),
      assureTelPersonnel:
        pickAssure('assureTelPersonnel', 'assure_tel_personnel') ||
        (isSelf ? pick('telPersonnel', 'tel_personnel', '') : ''),
      assureTelMobile:
        pickAssure('assureTelMobile', 'assure_tel_mobile') ||
        (isSelf ? pick('telMobile', 'tel_mobile', '') : ''),
      assureTelBureau:
        pickAssure('assureTelBureau', 'assure_tel_bureau') ||
        (isSelf ? pick('telBureau', 'tel_bureau', '') : ''),
      assureGroupeSanguin:
        pickAssure('assureGroupeSanguin', 'assure_groupe_sanguin') ||
        (isSelf ? pick('groupeSanguin', 'groupe_sanguin', '') : ''),
      assureAdresse:
        pickAssure('assureAdresse', 'assure_adresse') || (isSelf ? pick('adresse', 'adresse', '') : ''),
    };
    this.form.patch(patch);

    const hasAssureData = [
      patch.assureNom,
      patch.assurePrenom,
      patch.assureSexe,
      patch.assureDateNaissance,
      patch.assureTelPersonnel,
      patch.assureTelMobile,
      patch.assureTelBureau,
      patch.assureGroupeSanguin,
      patch.assureAdresse,
    ].some((v) => v !== null && v !== undefined && String(v).trim() !== '');

    const qualite = data['qualiteAssure'] ?? (hasAssureData ? 'AUTRE' : 'ASSURE_LUI_MEME');
    this.setQualiteAssure(qualite);

    if (this.patientId) this.loadAssureHistory();

    this.selectedCentrePayeurId.set(patch.centrePayeurId);
    // NE PAS appeler emitAssuranceData() ici — wizardData contient déjà les données du patient
    // depuis loadPatient ; la synchronisation bidirectionnelle est gérée par les handlers.
    this.validChange.emit(!!patch.numeroAssurance);
    this.applyReadonly();
  }

  markTouched(): void {
    this.form.markAllTouched();
  }

  isValid(): boolean {
    return this.form.valid();
  }

  private loadAssureHistory(force = false): void {
    const centerId = this.appShell.currentCenterId();
    if (!centerId || !this.patientId) return;

    const key = `${centerId}|${this.patientId}`;
    if (this.historyInFlightKey === key) return;
    if (!force && this.lastLoadedHistoryKey === key) return;

    this.pendingLoadAssureHistory = true;
    this.historyInFlightKey = key;
    this.ficheStore.loadAssureHistory({centerId, patientId: this.patientId});
  }

  private emit(): void {
    this.emitAssuranceData();
    this.validChange.emit(this.form.valid());
  }

  private applyReadonly(): void {
    this.form.setDisabled(this.readonly);
  }

  private emitAssuranceData(): void {
    this.dataChange.emit({
      ...this.form.value(),
      codeCentrePayeur: this.codeCentrePayeur(),
      codeAgence: this.codeAgence(),
      libelleAgence: this.libelleAgence(),
      libelleCaisse: this.libelleCaisse(),
    });
  }
}
