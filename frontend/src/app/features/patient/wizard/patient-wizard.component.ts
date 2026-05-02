import {AfterViewInit, Component, computed, inject, OnInit, signal, ViewChild} from '@angular/core';
import {ActivatedRoute, Router} from '@angular/router';
import {MatStepper, MatStepperModule} from '@angular/material/stepper';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatSnackBar, MatSnackBarModule} from '@angular/material/snack-bar';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {STEPPER_GLOBAL_OPTIONS} from '@angular/cdk/stepper';
import {StepGeneralitesComponent} from './step-generalites.component';
import {StepAssuranceComponent} from './step-assurance.component';
import {StepAffectationComponent} from './step-affectation.component';
import {StepAttestationComponent} from './step-attestation.component';
import {StepPecComponent} from './step-pec.component';
import {StepPiecesJointesComponent} from './step-pieces-jointes.component';
import {BackendApiService} from '../../../core/api/backend-api.service';
import {AuthSessionService} from '../../../core/auth/auth-session.service';
import {AppShellStore} from '../../../core/state/app-shell.store';

@Component({
  selector: 'app-patient-wizard',
  standalone: true,
  imports: [
    MatStepperModule, MatButtonModule, MatIconModule, MatSnackBarModule, TranslateModule,
    StepGeneralitesComponent, StepAssuranceComponent, StepAffectationComponent,
    StepAttestationComponent, StepPecComponent, StepPiecesJointesComponent
  ],
  providers: [{ provide: STEPPER_GLOBAL_OPTIONS, useValue: { showError: true } }],
  template: `
    <div class="wizard-container patient-form-compact">
      <div class="wizard-header">
        <button mat-icon-button (click)="goBack()"><mat-icon>arrow_back</mat-icon></button>
        <h2>{{ editMode() ? ('PATIENT_FORM.TITLE_EDIT' | translate) : ('WIZARD.TITLE' | translate) }}</h2>
        @if (editMode()) {
          <div class="mode-badge" [class.editing]="!consultationMode()">
            {{ consultationMode() ? ('WIZARD.READONLY_MODE' | translate) : ('WIZARD.EDITING_MODE' | translate) }}
          </div>
          @if (consultationMode()) {
            <button mat-stroked-button class="edit-toggle-btn" (click)="enableEditing()">
              <mat-icon>edit</mat-icon>
              {{ 'WIZARD.ENABLE_EDIT' | translate }}
            </button>
          }
        }
        <div class="wizard-progress">
          {{ 'WIZARD.STEP' | translate }} {{ currentStep() + 1 }} / {{ totalSteps() }}
        </div>
      </div>

      <mat-stepper #stepper [linear]="false" [animationDuration]="'0'" (selectionChange)="onStepChange($event)" class="wizard-stepper">
        <!-- Step 1: Généralités -->
        <mat-step [label]="'WIZARD.STEP_GENERALITES' | translate" [completed]="step1Valid()" [editable]="true">
          @if (shouldRenderStep(0)) {
            <app-step-generalites #stepGen [readonly]="consultationMode()" (dataChange)="updateData($event)" (validChange)="step1Valid.set($event)" />
          }
        </mat-step>

        <!-- Step 2: Assurance -->
        <mat-step [label]="'WIZARD.STEP_ASSURANCE' | translate" [completed]="step2Valid()" [editable]="true">
          @if (shouldRenderStep(1)) {
            <app-step-assurance #stepAss [readonly]="consultationMode()" [patientId]="editingPatientId() || undefined" (dataChange)="updateData($event)" (validChange)="step2Valid.set($event)" />
          }
        </mat-step>

        <!-- Step 3: Affectation -->
        <mat-step [label]="'WIZARD.STEP_AFFECTATION' | translate" [editable]="true">
          @if (shouldRenderStep(2)) {
            <app-step-affectation #stepAff [readonly]="consultationMode()" (dataChange)="updateData($event)" />
          }
        </mat-step>

        <!-- Step 4: Attestation (hidden for vacancier) -->
        @if (!isVacancier()) {
          <mat-step [label]="'WIZARD.STEP_ATTESTATION' | translate" [completed]="step4Valid()" [editable]="true">
            @if (shouldRenderStep(3)) {
              <app-step-attestation #stepAtt [readonly]="consultationMode()" (dataChange)="updateData($event)" (validChange)="step4Valid.set($event)" />
            }
          </mat-step>
        }

        <!-- Step 5: PEC -->
        <mat-step [label]="'WIZARD.STEP_PEC' | translate" [completed]="step5Valid()" [editable]="true">
          @if (shouldRenderStep(isVacancier() ? 3 : 4)) {
            <app-step-pec #stepPec [readonly]="consultationMode()" [patientId]="editingPatientId() || undefined" (dataChange)="updateData($event)" (validChange)="step5Valid.set($event)" />
          }
        </mat-step>

        <!-- Step 6: Pièces jointes -->
        <mat-step [label]="'WIZARD.STEP_PJ' | translate" [editable]="true">
          @if (shouldRenderStep(isVacancier() ? 4 : 5)) {
            <app-step-pieces-jointes #stepPj [readonly]="consultationMode()" (dataChange)="updateData($event)" />
          }
        </mat-step>
      </mat-stepper>

      <!-- Floating save button -->
      @if (!consultationMode()) {
        @if (pecCoverageError()) {
          <div class="floating-error">
            <mat-icon>error_outline</mat-icon>
            {{ pecCoverageError() }}
          </div>
        }
        <button mat-fab extended class="floating-save" (click)="submit()" [disabled]="saving() || !canSave()">
          <mat-icon>save</mat-icon>
          {{ editMode() ? ('PATIENT_FORM.TITLE_EDIT' | translate) : ('WIZARD.SAVE' | translate) }}
        </button>
      }
    </div>
  `,
  styles: [`
    .wizard-container { max-width: 1100px; margin: 0 auto; }
    .wizard-header {
      display: flex; align-items: center; gap: 12px; margin-bottom: 16px;
    }

    .wizard-header h2 {
      flex: 1;
      margin: 0;
      font-size: 1.3rem;
      color: var(--app-primary);
    }
    .wizard-progress {
      background: var(--app-primary-soft);
      color: var(--app-primary);
      padding: 6px 16px;
      border-radius: 20px;
      font-size: 13px; font-weight: 600;
    }
    .mode-badge { background:#eef2ff; color:#4338ca; padding:6px 12px; border-radius:999px; font-size:12px; font-weight:600; }

    .mode-badge.editing {
      background: var(--app-primary-soft);
      color: var(--app-primary-hover);
    }

    .edit-toggle-btn {
      border-color: var(--app-primary-outline) !important;
      color: var(--app-primary) !important;
    }
    :host ::ng-deep .wizard-stepper { background: transparent; }

    /* Remove Material horizontal content container spacing under step headers. */
    :host ::ng-deep .wizard-stepper .mat-stepper-horizontal-content-container,
    :host ::ng-deep .wizard-stepper .mat-horizontal-content-container {
      padding: 0 !important;
      margin: 0 !important;
    }

    :host ::ng-deep .wizard-stepper .mat-horizontal-stepper-content {
      background: var(--app-surface-soft);
      border-radius: 12px;
      margin-top: 0;
      border: 1px solid var(--app-border);
      min-height: 0;
      box-sizing: border-box;
    }

    /* Collapse only explicit inactive panels, keep active panel always visible. */
    :host ::ng-deep .wizard-stepper .mat-horizontal-stepper-content[aria-expanded="false"] {
      display: none !important;
      height: 0 !important;
      min-height: 0 !important;
      padding: 0 !important;
      margin: 0 !important;
      border: 0 !important;
      overflow: hidden !important;
    }

    :host ::ng-deep .wizard-stepper .mat-horizontal-stepper-content[aria-expanded="true"] {
      display: block !important;
      height: auto !important;
      visibility: visible !important;
      overflow: visible !important;
    }

    :host ::ng-deep .wizard-stepper .mat-step-header .mat-step-icon-selected {
      background-color: var(--app-primary) !important;
    }
    :host ::ng-deep .wizard-stepper .mat-step-header .mat-step-icon-state-done {
      background-color: var(--app-primary-hover) !important;
    }
    :host ::ng-deep .wizard-stepper .mat-step-header .mat-step-icon-state-error {
      background-color: #d32f2f !important;
    }
    .floating-save {
      position: fixed;
      bottom: 28px;
      right: 16px;
      z-index: 1000;
      background-color: var(--app-primary) !important;
      color: #fff !important;
      --mdc-extended-fab-container-color: var(--app-primary) !important;
      --mdc-fab-container-color: var(--app-primary) !important;
      --mdc-extended-fab-label-text-color: #fff !important;
      --mat-fab-foreground-color: #fff !important;
      --mdc-fab-icon-color: #fff !important;
      box-shadow: 0 6px 24px rgba(2, 6, 23, .2) !important;
      border-radius: 16px !important;
    }
    .floating-save:disabled {
      background-color: #bdbdbd !important;
      color: rgba(255,255,255,.7) !important;
      --mdc-extended-fab-container-color: #bdbdbd !important;
      --mdc-fab-container-color: #bdbdbd !important;
      box-shadow: 0 4px 12px rgba(0,0,0,.12) !important;
    }
    .floating-error {
      position: fixed;
      bottom: 80px;
      right: 16px;
      z-index: 1000;
      display: flex;
      align-items: center;
      gap: 8px;
      background: #fff3e0;
      color: #e65100;
      border: 1px solid #ffcc02;
      border-radius: 12px;
      padding: 10px 16px;
      font-size: 12px;
      font-weight: 500;
      max-width: 360px;
      box-shadow: 0 4px 16px rgba(0,0,0,.12);
    }
    .floating-error mat-icon { color: #e65100; font-size: 20px; width: 20px; height: 20px; flex-shrink: 0; }
  `]
})
export class PatientWizardComponent implements OnInit, AfterViewInit {
  @ViewChild('stepper') stepper!: MatStepper;
  @ViewChild('stepGen') stepGen?: StepGeneralitesComponent;
  @ViewChild('stepAss') stepAss?: StepAssuranceComponent;
  @ViewChild('stepAff') stepAff?: StepAffectationComponent;
  @ViewChild('stepAtt') stepAtt?: StepAttestationComponent;
  @ViewChild('stepPec') stepPec?: StepPecComponent;
  @ViewChild('stepPj') stepPj?: StepPiecesJointesComponent;

  private readonly api = inject(BackendApiService);
  private readonly auth = inject(AuthSessionService);
  private readonly store = inject(AppShellStore);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);
  private readonly translate = inject(TranslateService);
  private readonly route = inject(ActivatedRoute);

  readonly currentStep = signal(0);
  readonly saving = signal(false);
  readonly step1Valid = signal(false);
  readonly step2Valid = signal(false);
  readonly step4Valid = signal(false);
  readonly step5Valid = signal(false);
  readonly isVacancier = computed(() => {
    this.wizardDataVersion();  // trigger re-eval on data change
    const etat = (this.wizardData['etatPatient'] ?? '').toString();
    return etat === 'VACANCIER_LOCAL' || etat === 'VACANCIER_ETRANGER';
  });
  wizardData: Record<string, any> = {};
  private readonly wizardDataVersion = signal(0);

  readonly totalSteps = computed(() => this.isVacancier() ? 5 : 6);
  readonly editingPatientId = signal<string | null>(null);
  readonly editMode = computed(() => !!this.editingPatientId());
  readonly consultationMode = signal(false);
  private attestationLoaded = signal(false);
  private pecLoaded = signal(false);

  readonly canSave = computed(() => {
    this.wizardDataVersion();  // trigger re-eval on data change
    const uiBase = this.step1Valid() && this.step2Valid() && this.step5Valid();
    const dataBase = this.isDataBaseValid();
    const base = this.editMode() ? (uiBase || dataBase) : uiBase;
    if (this.consultationMode()) return false;
    const uiAtt = this.step4Valid();
    const dataAtt = this.isAttestationDataValid();
    const attOk = this.editMode() ? (uiAtt || dataAtt) : uiAtt;
    const stepsOk = this.isVacancier() ? base : (base && attOk);
    if (!stepsOk) return false;

    // Check PEC coverage by attestation (non-vacancier only)
    if (!this.isVacancier()) {
      const pecDeb = this.wizardData['pecDateDebutDemande'];
      const pecFin = this.wizardData['pecDateFinDemande'];
      const attDeb = this.wizardData['attestationDebut'];
      const attFin = this.wizardData['attestationFin'];
      if (pecDeb && pecFin && attDeb && attFin) {
        const toMs = (v: any) => (v instanceof Date ? v : new Date(v)).getTime();
        if (toMs(pecDeb) < toMs(attDeb) || toMs(pecFin) > toMs(attFin)) {
          return false;
        }
      }
    }
    return true;
  });

  /** Error message when PEC is outside attestation range */
  readonly pecCoverageError = computed(() => {
    this.wizardDataVersion();  // trigger re-eval on data change
    if (this.isVacancier()) return null;
    const pecDeb = this.wizardData['pecDateDebutDemande'];
    const pecFin = this.wizardData['pecDateFinDemande'];
    const attDeb = this.wizardData['attestationDebut'];
    const attFin = this.wizardData['attestationFin'];
    if (pecDeb && pecFin && attDeb && attFin) {
      const toMs = (v: any) => (v instanceof Date ? v : new Date(v)).getTime();
      if (toMs(pecDeb) < toMs(attDeb) || toMs(pecFin) > toMs(attFin)) {
        return 'L\'intervalle de la prise en charge dépasse la période de l\'ouverture de droit (attestation).';
      }
    }
    return null;
  });

  updateData(partial: Record<string, any>): void {
    this.wizardData = { ...this.wizardData, ...partial };
    this.wizardDataVersion.update(v => v + 1);
    this.syncAssureWhenSelf();
    if (partial['qualiteAssure'] !== undefined && this.stepAss) {
      this.stepAss.setQualiteAssure(partial['qualiteAssure']);
    }
  }

  private syncAssureWhenSelf(): void {
    if ((this.wizardData['qualiteAssure'] ?? 'ASSURE_LUI_MEME') !== 'ASSURE_LUI_MEME') return;
    this.wizardData = {
      ...this.wizardData,
      assureNom: this.wizardData['nom'] ?? this.wizardData['assureNom'] ?? null,
      assurePrenom: this.wizardData['prenom'] ?? this.wizardData['assurePrenom'] ?? null,
      assureSexe: this.wizardData['sexe'] ?? this.wizardData['assureSexe'] ?? null,
      assureDateNaissance: this.wizardData['dateNaissance'] ?? this.wizardData['assureDateNaissance'] ?? null,
      assureTelPersonnel: this.wizardData['telPersonnel'] ?? this.wizardData['assureTelPersonnel'] ?? null,
      assureTelMobile: this.wizardData['telMobile'] ?? this.wizardData['assureTelMobile'] ?? null,
      assureTelBureau: this.wizardData['telBureau'] ?? this.wizardData['assureTelBureau'] ?? null,
      assureAdresse: this.wizardData['adresse'] ?? this.wizardData['assureAdresse'] ?? null,
      assureGroupeSanguin: this.wizardData['groupeSanguin'] ?? this.wizardData['assureGroupeSanguin'] ?? null
    };
  }

  shouldRenderStep(stepIndex: number): boolean {
    if (!this.editMode()) return true;
    return this.currentStep() === stepIndex;
  }

  onStepChange(event: any): void {
    this.currentStep.set(event.selectedIndex);
    this.loadStepDataIfNeeded(event.selectedIndex);
    // Double setTimeout to ensure ViewChild is resolved after @if renders the component
    setTimeout(() => {
      this.patchActiveStep();
      // Retry once more after another tick for late ViewChild resolution
      setTimeout(() => this.patchActiveStep(), 50);
    });
  }

  private loadStepDataIfNeeded(stepIndex: number): void {
    if (!this.editMode() || !this.editingPatientId()) return;
    const centerId = this.store.currentCenterId();
    if (!centerId) return;

    const attestationIndex = this.isVacancier() ? -1 : 3;
    const pecIndex = this.isVacancier() ? 3 : 4;

    if (stepIndex === attestationIndex && !this.attestationLoaded()) {
      this.attestationLoaded.set(true);
      this.api.listAttestationsByPatient(centerId, this.editingPatientId()!).subscribe(a => {
        this.wizardData = { ...this.wizardData, attestationHistory: a ?? [] };
        const first = (a ?? [])[0];
        if (first) {
          this.wizardData = {
            ...this.wizardData,
            attestationId: (first.id ?? first.ID ?? null),
            attestationDebut: first.dateDebut ?? first.DATE_DEBUT,
            attestationFin: first.dateFin ?? first.DATE_FIN
          };
        }
        this.wizardDataVersion.update(v => v + 1);
        this.patchStepsFromWizardData();
      });
    }

    if (stepIndex === pecIndex && !this.pecLoaded()) {
      this.pecLoaded.set(true);
      this.api.listPecsByPatient(centerId, this.editingPatientId()!).subscribe(pecs => {
        this.wizardData = { ...this.wizardData, pecHistory: pecs ?? [] };
        const first = (pecs ?? [])[0];
        if (first) {
          this.wizardData = {
            ...this.wizardData,
            pecId: (first.id ?? first.ID ?? null),
            pecDateDebutDemande: first.dateDebutDemande ?? first.DATE_DEBUT_DEMANDE,
            pecDateFinDemande: first.dateFinDemande ?? first.DATE_FIN_DEMANDE,
            pecForfaitDemandeId: first.forfaitDemandeId ?? first.FORFAIT_DEMANDE_ID ?? null
          };
        }
        this.wizardDataVersion.update(v => v + 1);
        this.patchStepsFromWizardData();
      });
    }
  }

  goBack(): void { this.router.navigate(['/patients']); }

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) return;
    const centerId = this.store.currentCenterId();
    if (!centerId) return;

    this.editingPatientId.set(id);
    this.consultationMode.set(true);

    this.api.getPatient(id, centerId, this.auth.username() ?? 'demo').subscribe({
      next: (p: any) => {
        const ai = p?.assureInfo ?? {};
        this.wizardData = {
          ...this.wizardData,
          ...p,
          qualiteAssure: p?.qualiteAssure ?? p?.qualite_assure ?? null,
          numeroAssurance: p?.numeroAssurance?.value ?? p?.numeroAssurance,
          assureNom: p?.assureNom ?? ai?.nom ?? ai?.assureNom ?? null,
          assurePrenom: p?.assurePrenom ?? ai?.prenom ?? ai?.assurePrenom ?? null,
          assureSexe: p?.assureSexe ?? ai?.sexe ?? ai?.assureSexe ?? null,
          assureDateNaissance: p?.assureDateNaissance ?? ai?.dateNaissance ?? ai?.assureDateNaissance ?? null,
          assureTelPersonnel: p?.assureTelPersonnel ?? ai?.telPersonnel ?? ai?.assureTelPersonnel ?? null,
          assureTelMobile: p?.assureTelMobile ?? ai?.telMobile ?? ai?.assureTelMobile ?? null,
          assureTelBureau: p?.assureTelBureau ?? ai?.telBureau ?? ai?.assureTelBureau ?? null,
          assureAdresse: p?.assureAdresse ?? ai?.adresse ?? ai?.assureAdresse ?? null,
          assureGroupeSanguin: p?.assureGroupeSanguin ?? ai?.groupeSanguin ?? ai?.assureGroupeSanguin ?? null,
          assureHistory: this.parseAssureHistory(p?.assureHistoryJson),
          attestationId: null,
          attestationDebut: null,
          attestationFin: null,
          pecId: null,
          pecDateDebutDemande: null,
          pecDateFinDemande: null
        };
        this.syncAssureWhenSelf();
        this.wizardDataVersion.update(v => v + 1);
        this.patchStepsFromWizardData();

        // Recompute validity from loaded data so Save is enabled automatically when valid.
        this.recomputeStepValidityFromData();
      }
    });
  }

  ngAfterViewInit(): void {
    setTimeout(() => this.patchStepsFromWizardData());
  }

  private patchStepsFromWizardData(): void {
    if (!this.wizardData || Object.keys(this.wizardData).length === 0) return;

    if (this.editMode()) {
      this.patchActiveStep();
    } else {
      this.stepGen?.patchData?.(this.wizardData);
      this.stepAss?.patchData?.(this.wizardData);
      this.stepAff?.patchData?.(this.wizardData);
      this.stepAtt?.patchData?.(this.wizardData);
      this.stepPec?.patchData?.(this.wizardData);
      this.stepPj?.patchData?.(this.wizardData);
    }

    const etat = (this.wizardData['etatPatient'] ?? '').toString();
    if (etat) this.updateData({ etatPatient: etat });
  }

  private patchActiveStep(): void {
    if (!this.wizardData || Object.keys(this.wizardData).length === 0) return;
    const idx = this.currentStep();
    if (idx === 0) this.stepGen?.patchData?.(this.wizardData);
    if (idx === 1) this.stepAss?.patchData?.(this.wizardData);
    if (idx === 2) this.stepAff?.patchData?.(this.wizardData);

    const attestationIndex = this.isVacancier() ? -1 : 3;
    const pecIndex = this.isVacancier() ? 3 : 4;
    const pjIndex = this.isVacancier() ? 4 : 5;

    if (idx === attestationIndex) this.stepAtt?.patchData?.(this.wizardData);
    if (idx === pecIndex) this.stepPec?.patchData?.(this.wizardData);
    if (idx === pjIndex) this.stepPj?.patchData?.(this.wizardData);
  }

  /** Submit the final form */
  submit(): void {
    const centerId = this.store.currentCenterId();
    if (!centerId) return;
    this.saving.set(true);

    const d = this.wizardData;
    const toDate = (v: any) => {
      if (!v) return undefined;
      if (v instanceof Date) return v.toISOString().slice(0, 10);
      return String(v);
    };
    const derivedTypePatient = (d['etatPatient'] === 'VACANCIER_LOCAL' || d['etatPatient'] === 'VACANCIER_ETRANGER')
      ? 'VACANCIER'
      : 'NON_VACANCIER';

    const payload = {
      centerId,
      userId: this.auth.username() ?? 'demo',
      nom: d['nom'], prenom: d['prenom'], sexe: d['sexe'],
      dateAdmission: toDate(d['dateAdmission']) || new Date().toISOString().slice(0, 10),
      dateNaissance: toDate(d['dateNaissance']),
      numeroAssurance: d['numeroAssurance'] || 'TEMP-' + Date.now(),
      typePatient: derivedTypePatient,
      civilite: d['civilite'], groupeSanguin: d['groupeSanguin'],
      nombreEnfants: d['nombreEnfants'] || 0,
      lieuNaissance: d['lieuNaissance'], situationFamiliale: d['situationFamiliale'],
      profession: d['profession'], adresse: d['adresse'],
      telPersonnel: d['telPersonnel'], telMobile: d['telMobile'],
      telBureau: d['telBureau'], email: d['email'],
      sousKt: d['sousKt'] || false, epoEnabled: d['epoEnabled'] || false,
      epoDate: toDate(d['epoDate']), ferEnabled: d['ferEnabled'] || false,
      ferDate: toDate(d['ferDate']), observation: d['observation'],
      qualiteAssure: d['qualiteAssure'] || 'ASSURE_LUI_MEME',
      photoBase64: d['photoBase64'], enSommeil: d['enSommeil'] || false,
      etatPatient: d['etatPatient'] || 'PERMANENT',
      dateEvenementEtat: toDate(d['dateEvenementEtat']),
      centrePayeurId: d['centrePayeurId'], medecinTraitantId: d['medecinTraitantId'],
      salleId: d['salleId'], positionId: d['positionId'],
      transporteurAllerId: d['transporteurAllerId'], transporteurRetourId: d['transporteurRetourId'],
      categorieTransportId: d['categorieTransportId'],
      jourDimanche: d['jourDimanche'] || false, jourLundi: d['jourLundi'] || false,
      jourMardi: d['jourMardi'] || false, jourMercredi: d['jourMercredi'] || false,
      jourJeudi: d['jourJeudi'] || false, jourVendredi: d['jourVendredi'] || false,
      jourSamedi: d['jourSamedi'] || false,
      attestationId: d['attestationId'] ?? undefined,
      attestationDebut: toDate(d['attestationDebut']), attestationFin: toDate(d['attestationFin']),
      assureNumeroAssurance: d['assureNumeroAssurance'],
      assureNom: d['assureNom'], assurePrenom: d['assurePrenom'], assureSexe: d['assureSexe'],
      assureDateNaissance: toDate(d['assureDateNaissance']),
      assureTelPersonnel: d['assureTelPersonnel'], assureAdresse: d['assureAdresse'],
      assureGroupeSanguin: d['assureGroupeSanguin'],
      assureTelMobile: d['assureTelMobile'],
      assureTelBureau: d['assureTelBureau'],
      assureHistoryJson: d['assureHistory'] ? JSON.stringify(d['assureHistory']) : undefined,
      pecId: d['pecId'] ?? undefined,
      pecDateDebutDemande: toDate(d['pecDateDebutDemande']),
      pecDateFinDemande: toDate(d['pecDateFinDemande']),
      pecForfaitDemandeId: d['pecForfaitDemandeId']
    } as any;

    const req$ = this.editMode() && this.editingPatientId()
      ? this.api.updatePatient(this.editingPatientId()!, payload)
      : this.api.createPatient(payload);

    req$.subscribe({
      next: () => {
        this.saving.set(false);
        this.snackBar.open(
          this.translate.instant('PATIENT_FORM.SUCCESS') || 'Patient enregistré avec succès',
          'OK', { duration: 3000 }
        );
        this.router.navigate(['/patients']);
      },
      error: (err) => {
        this.saving.set(false);
        this.snackBar.open(err?.error?.detail || 'Erreur', 'OK', { duration: 5000 });
      }
    });
  }

  enableEditing(): void {
    this.consultationMode.set(false);
    // Eagerly load attestation + PEC data so coverage validation works immediately
    if (this.editingPatientId()) {
      const attestationIndex = this.isVacancier() ? -1 : 3;
      const pecIndex = this.isVacancier() ? 3 : 4;
      this.loadStepDataIfNeeded(attestationIndex);
      this.loadStepDataIfNeeded(pecIndex);
    }
    this.recomputeStepValidityFromData();
    setTimeout(() => this.patchStepsFromWizardData());
  }

  private isDataBaseValid(): boolean {
    const d = this.wizardData;
    const required = [d['nom'], d['prenom'], d['sexe'], d['dateAdmission'], d['numeroAssurance']];
    return required.every(v => !!String(v ?? '').trim()) && this.isPecDataValid();
  }

  private isPecDataValid(): boolean {
    const d = this.wizardData;
    if (this.isVacancier()) return true;
    return !!(d['pecDateDebutDemande'] && d['pecDateFinDemande']);
  }

  private isAttestationDataValid(): boolean {
    if (this.isVacancier()) return true;
    const d = this.wizardData;
    return !!(d['attestationDebut'] && d['attestationFin']);
  }

  private recomputeStepValidityFromData(): void {
    this.step1Valid.set(!!(this.wizardData['nom'] && this.wizardData['prenom'] && this.wizardData['sexe'] && this.wizardData['dateAdmission']));
    this.step2Valid.set(!!this.wizardData['numeroAssurance']);
    this.step4Valid.set(this.isAttestationDataValid());
    this.step5Valid.set(this.isPecDataValid());
  }

  private parseAssureHistory(json: any): any[] {
    if (!json) return [];
    if (Array.isArray(json)) return json;
    try { return JSON.parse(json); } catch { return []; }
  }
}
