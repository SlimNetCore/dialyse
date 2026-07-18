import {
  afterNextRender,
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  HostListener,
  inject,
  Injector,
  OnInit,
  signal,
  ViewChild,
} from '@angular/core';
import {ActivatedRoute, Router} from '@angular/router';
import {MatStepper, MatStepperModule} from '@angular/material/stepper';
import {STEPPER_GLOBAL_OPTIONS, StepperSelectionEvent} from '@angular/cdk/stepper';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatSnackBar, MatSnackBarModule} from '@angular/material/snack-bar';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {StepGeneralitesComponent} from './step-generalites.component';
import {StepAssuranceComponent} from './step-assurance.component';
import {StepAffectationComponent} from './step-affectation.component';
import {StepAttestationComponent} from './step-attestation.component';
import {StepPecComponent} from './step-pec.component';
import {StepPiecesJointesComponent} from './step-pieces-jointes.component';
import {AuthStore} from '../../../core/state/auth.store';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {WebSocketService, WsEvent} from '../../../core/ws/websocket.service';
import {PatientFicheStore} from '../state/patient-fiche.store';
import {PatientListStore} from '../state/patient-list.store';
import {consumeWizardActionStatus} from './wizard-action-status.util';

@Component({
  selector: 'app-patient-wizard',
  standalone: true,
  imports: [
    MatStepperModule,
    MatButtonModule,
    MatIconModule,
    MatSnackBarModule,
    TranslateModule,
    StepGeneralitesComponent,
    StepAssuranceComponent,
    StepAffectationComponent,
    StepAttestationComponent,
    StepPecComponent,
    StepPiecesJointesComponent,
  ],
  providers: [{ provide: STEPPER_GLOBAL_OPTIONS, useValue: { showError: true } }],
  templateUrl: './patient-wizard.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './patient-wizard.component.css',
})
export class PatientWizardComponent implements OnInit, AfterViewInit {
  @ViewChild('stepper') stepper!: MatStepper;
  @ViewChild('stepGen') stepGen?: StepGeneralitesComponent;
  @ViewChild('stepAss') stepAss?: StepAssuranceComponent;
  @ViewChild('stepAff') stepAff?: StepAffectationComponent;
  @ViewChild('stepAtt') stepAtt?: StepAttestationComponent;
  @ViewChild('stepPec') stepPec?: StepPecComponent;
  @ViewChild('stepPj') stepPj?: StepPiecesJointesComponent;

  private readonly auth = inject(AuthStore);
  private readonly appShell = inject(AppShellStore);
  private readonly ficheStore = inject(PatientFicheStore);
  private readonly patientListStore = inject(PatientListStore);
  readonly editingPatientId = this.ficheStore.editingPatientId;
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);
  private readonly translate = inject(TranslateService);
  private readonly route = inject(ActivatedRoute);
  private readonly injector = inject(Injector);

  readonly currentStep = this.ficheStore.currentStep;
  readonly saving = this.ficheStore.saving;
  readonly step1Valid = this.ficheStore.step1Valid;
  readonly step2Valid = this.ficheStore.step2Valid;
  readonly step4Valid = this.ficheStore.step4Valid;
  readonly step5Valid = this.ficheStore.step5Valid;
  readonly isVacancier = computed(() => {
    this.wizardDataVersion(); // trigger re-eval on data change
    const etat = (this.wizardData['etatPatient'] ?? '').toString();
    return etat === 'VACANCIER_LOCAL' || etat === 'VACANCIER_ETRANGER';
  });
  readonly editMode = this.ficheStore.editMode;
  readonly consultationMode = this.ficheStore.consultationMode;
  private readonly ws = inject(WebSocketService);
  // loadingPatientData removed – shouldRenderStep now always returns true

  readonly totalSteps = computed(() => (this.isVacancier() ? 5 : 6));
  readonly isMobileViewport = signal(
    typeof window !== 'undefined' ? window.innerWidth <= 900 : false,
  );
  private readonly wizardDataVersion = this.ficheStore.version;
  private patchActiveStepScheduled = false;
  private patchAllStepsScheduled = false;
  readonly canSave = computed(() => {
    this.wizardDataVersion(); // trigger re-eval on data change
    const uiBase = this.step1Valid() && this.step2Valid() && this.step5Valid();
    const dataBase = this.isDataBaseValid();
    const base = this.editMode() ? uiBase || dataBase : uiBase;
    if (this.consultationMode()) return false;
    const uiAtt = this.step4Valid();
    const dataAtt = this.isAttestationDataValid();
    const attOk = this.editMode() ? uiAtt || dataAtt : uiAtt;
    const stepsOk = this.isVacancier() ? base : base && attOk;
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
    this.wizardDataVersion(); // trigger re-eval on data change
    if (this.isVacancier()) return null;
    const pecDeb = this.wizardData['pecDateDebutDemande'];
    const pecFin = this.wizardData['pecDateFinDemande'];
    const attDeb = this.wizardData['attestationDebut'];
    const attFin = this.wizardData['attestationFin'];
    if (pecDeb && pecFin && attDeb && attFin) {
      const toMs = (v: any) => (v instanceof Date ? v : new Date(v)).getTime();
      if (toMs(pecDeb) < toMs(attDeb) || toMs(pecFin) > toMs(attFin)) {
        return "L'intervalle de la prise en charge dépasse la période de l'ouverture de droit (attestation).";
      }
    }
    return null;
  });
  private readonly attestationLoaded = this.ficheStore.attestationLoaded;
  private readonly pecLoaded = this.ficheStore.pecLoaded;

  constructor() {
    effect(() => {
      const event = this.ws.lastEvent();
      if (!this.editMode() || !event || !this.editingPatientId()) return;
      if (!this.shouldRefreshFromEvent(event)) return;
      const eventPatientId = this.extractPatientId(event);
      const currentPatientId = this.editingPatientId();
      if (eventPatientId && currentPatientId && eventPatientId !== currentPatientId) return;
      this.refreshCurrentPatientFromServer();
    });

    effect(() => {
      const submitStatus = this.ficheStore.submitStatus();
      if (submitStatus === 'idle') return;

      if (submitStatus === 'success') {
        const centerId = this.appShell.currentCenterId();
        const userId = this.auth.username() ?? 'demo';
        const savedPatientId =
          this.ficheStore.lastSavedPatientId() ?? this.editingPatientId() ?? null;
        this.patientListStore.setRecentPatient(savedPatientId);
        if (centerId) {
          this.patientListStore.loadPage({
            centerId,
            userId,
            page: this.patientListStore.pageIndex(),
            size: this.patientListStore.pageSize(),
          });
        }
        this.snackBar.open(
          this.translate.instant('PATIENT_FORM.SUCCESS') || 'Patient enregistré avec succès',
          'OK',
          {duration: 3000},
        );
        this.ficheStore.resetSubmitStatus();
        void this.router.navigate(['/patients']);
        return;
      }

      this.snackBar.open(this.ficheStore.error() || 'Erreur', 'OK', {duration: 5000});
      this.ficheStore.resetSubmitStatus();
    });

    consumeWizardActionStatus(this.ficheStore, ({action, success, error}) => {
      if (action === 'LOAD_PATIENT' || action === 'LOAD_ATTESTATIONS' || action === 'LOAD_PECS') {
        if (success) {
          this.recomputeStepValidityFromData();
          // All steps are always rendered (shouldRenderStep always returns true),
          // so @ViewChild instances are always available — patch immediately.
          this.patchStepsFromWizardData();
          // Second pass after render for Angular Material late content-projection.
          afterNextRender(() => this.patchStepsFromWizardData(), {injector: this.injector});
        } else if (error) {
          this.snackBar.open(error, 'OK', {duration: 4000});
        }
      }
    });
  }

  get wizardData(): Record<string, any> {
    return this.ficheStore.wizardData();
  }

  set wizardData(value: Record<string, any>) {
    this.ficheStore.setWizardData(value);
  }

  updateData(partial: Record<string, any>): void {
    this.wizardData = { ...this.wizardData, ...partial };
    this.syncAssureWhenSelf();
    if (partial['qualiteAssure'] !== undefined && this.stepAss) {
      this.stepAss.setQualiteAssure(partial['qualiteAssure']);
    }
  }

  goBack(): void {
    this.router.navigate(['/patients']);
  }

  shouldRenderStep(_stepIndex: number): boolean {
    // Always render all steps: avoids @ViewChild being null when patchData() is called,
    // and prevents form values from being lost when switching between steps.
    return true;
  }

  setStep1Valid(value: boolean): void {
    this.ficheStore.setStep1Valid(value);
  }

  setStep2Valid(value: boolean): void {
    this.ficheStore.setStep2Valid(value);
  }

  setStep4Valid(value: boolean): void {
    this.ficheStore.setStep4Valid(value);
  }

  setStep5Valid(value: boolean): void {
    this.ficheStore.setStep5Valid(value);
  }

  onStepChange(event: StepperSelectionEvent): void {
    this.ficheStore.setCurrentStep(event.selectedIndex);
    this.loadStepDataIfNeeded(event.selectedIndex);
    this.schedulePatchActiveStep();
    this.scrollToStepAndFocus();
  }

  ngOnInit(): void {
    this.ficheStore.reset();

    const id = this.route.snapshot.paramMap.get('id');
    if (!id) return;
    const centerId = this.appShell.currentCenterId();
    if (!centerId) return;

    this.ficheStore.setEditingPatientId(id);
    this.ficheStore.setConsultationMode(true);

    void this.loadPatientFromServer(id);
  }

  /** Submit the final form */
  submit(): void {
    const centerId = this.appShell.currentCenterId();
    if (!centerId) return;

    const d = this.wizardData;
    const toDate = (v: any) => {
      if (!v) return undefined;
      if (v instanceof Date) return v.toISOString().slice(0, 10);
      return String(v);
    };
    const derivedTypePatient =
      d['etatPatient'] === 'VACANCIER_LOCAL' || d['etatPatient'] === 'VACANCIER_ETRANGER'
        ? 'VACANCIER'
        : 'NON_VACANCIER';

    const payload = {
      centerId,
      userId: this.auth.username() ?? 'demo',
      nom: d['nom'],
      prenom: d['prenom'],
      sexe: d['sexe'],
      dateAdmission: toDate(d['dateAdmission']) || new Date().toISOString().slice(0, 10),
      dateNaissance: toDate(d['dateNaissance']),
      numeroAssurance: d['numeroAssurance'] || 'TEMP-' + Date.now(),
      typePatient: derivedTypePatient,
      civilite: d['civilite'],
      groupeSanguin: d['groupeSanguin'],
      nombreEnfants: d['nombreEnfants'] || 0,
      lieuNaissance: d['lieuNaissance'],
      situationFamiliale: d['situationFamiliale'],
      profession: d['profession'],
      adresse: d['adresse'],
      telPersonnel: d['telPersonnel'],
      telMobile: d['telMobile'],
      telBureau: d['telBureau'],
      email: d['email'],
      sousKt: d['sousKt'] || false,
      epoEnabled: d['epoEnabled'] || false,
      epoDate: toDate(d['epoDate']),
      ferEnabled: d['ferEnabled'] || false,
      ferDate: toDate(d['ferDate']),
      observation: d['observation'],
      qualiteAssure: d['qualiteAssure'] || 'ASSURE_LUI_MEME',
      photoBase64: d['photoBase64'],
      enSommeil: d['enSommeil'] || false,
      etatPatient: d['etatPatient'] || 'PERMANENT',
      dateEvenementEtat: toDate(d['dateEvenementEtat']),
      centrePayeurId: d['centrePayeurId'],
      medecinTraitantId: d['medecinTraitantId'],
      salleId: d['salleId'],
      positionId: d['positionId'],
      transporteurAllerId: d['transporteurAllerId'],
      transporteurRetourId: d['transporteurRetourId'],
      categorieTransportId: d['categorieTransportId'],
      jourDimanche: d['jourDimanche'] || false,
      jourLundi: d['jourLundi'] || false,
      jourMardi: d['jourMardi'] || false,
      jourMercredi: d['jourMercredi'] || false,
      jourJeudi: d['jourJeudi'] || false,
      jourVendredi: d['jourVendredi'] || false,
      jourSamedi: d['jourSamedi'] || false,
      attestationId: d['attestationId'] ?? undefined,
      attestationDebut: toDate(d['attestationDebut']),
      attestationFin: toDate(d['attestationFin']),
      assureNumeroAssurance: d['assureNumeroAssurance'],
      assureNom: d['assureNom'],
      assurePrenom: d['assurePrenom'],
      assureSexe: d['assureSexe'],
      assureDateNaissance: toDate(d['assureDateNaissance']),
      assureTelPersonnel: d['assureTelPersonnel'],
      assureAdresse: d['assureAdresse'],
      assureGroupeSanguin: d['assureGroupeSanguin'],
      assureTelMobile: d['assureTelMobile'],
      assureTelBureau: d['assureTelBureau'],
      assureHistoryJson: d['assureHistory'] ? JSON.stringify(d['assureHistory']) : undefined,
      piecesJointesJson: d['piecesJointes']?.length
        ? JSON.stringify(d['piecesJointes'])
        : undefined,
      pecId: d['pecId'] ?? undefined,
      pecDateDebutDemande: toDate(d['pecDateDebutDemande']),
      pecDateFinDemande: toDate(d['pecDateFinDemande']),
      pecForfaitDemandeId: d['pecForfaitDemandeId'],
    } as any;

    void this.ficheStore.submitPatient({
      editingPatientId: this.editMode() ? this.editingPatientId() : null,
      payload,
    });
  }

  enableEditing(): void {
    this.ficheStore.setConsultationMode(false);
    // Eagerly load attestation + PEC data so coverage validation works immediately
    if (this.editingPatientId()) {
      const attestationIndex = this.isVacancier() ? -1 : 3;
      const pecIndex = this.isVacancier() ? 3 : 4;
      this.loadStepDataIfNeeded(attestationIndex);
      this.loadStepDataIfNeeded(pecIndex);
    }
    this.recomputeStepValidityFromData();
    this.schedulePatchAllSteps();
  }

  ngAfterViewInit(): void {
    this.schedulePatchAllSteps();
  }

  @HostListener('window:resize')
  onWindowResize(): void {
    this.isMobileViewport.set(window.innerWidth <= 900);
  }

  private syncAssureWhenSelf(): void {
    if ((this.wizardData['qualiteAssure'] ?? 'ASSURE_LUI_MEME') !== 'ASSURE_LUI_MEME') return;
    this.wizardData = {
      ...this.wizardData,
      assureNom: this.wizardData['nom'] ?? this.wizardData['assureNom'] ?? null,
      assurePrenom: this.wizardData['prenom'] ?? this.wizardData['assurePrenom'] ?? null,
      assureSexe: this.wizardData['sexe'] ?? this.wizardData['assureSexe'] ?? null,
      assureDateNaissance:
        this.wizardData['dateNaissance'] ?? this.wizardData['assureDateNaissance'] ?? null,
      assureTelPersonnel:
        this.wizardData['telPersonnel'] ?? this.wizardData['assureTelPersonnel'] ?? null,
      assureTelMobile: this.wizardData['telMobile'] ?? this.wizardData['assureTelMobile'] ?? null,
      assureTelBureau: this.wizardData['telBureau'] ?? this.wizardData['assureTelBureau'] ?? null,
      assureAdresse: this.wizardData['adresse'] ?? this.wizardData['assureAdresse'] ?? null,
      assureGroupeSanguin:
        this.wizardData['groupeSanguin'] ?? this.wizardData['assureGroupeSanguin'] ?? null,
    };
  }

  private schedulePatchActiveStep(): void {
    if (this.patchActiveStepScheduled) return;
    this.patchActiveStepScheduled = true;
    afterNextRender(
      () => {
        this.patchActiveStepScheduled = false;
        this.patchActiveStep();
        // Second pass for late material step content projection
        afterNextRender(() => this.patchActiveStep(), {injector: this.injector});
      },
      {injector: this.injector},
    );
  }

  private patchStepsFromWizardData(): void {
    // Ensure we have at least some data before patching
    if (!this.wizardData || typeof this.wizardData !== 'object') {
      console.warn('[PatientWizard] wizardData is empty or invalid', this.wizardData);
      return;
    }

    // Log wizard data for debugging
    console.log('[PatientWizard] Patching steps with wizardData:', {
      keyCount: Object.keys(this.wizardData).length,
      hasCriticalFields: !!(this.wizardData['nom'] && this.wizardData['prenom']),
      sampleKeys: Object.keys(this.wizardData).slice(0, 10)
    });

    // Always patch all steps when in edit mode (loading existing patient)
    // This ensures data is available even if the step is not currently active
    if (this.editMode()) {
      // In edit/consultation mode, patch ALL steps (not just active) to ensure data is populated
      // The @if(shouldRenderStep) will control which ones are visible
      this.stepGen?.patchData?.(this.wizardData);
      this.stepAss?.patchData?.(this.wizardData);
      this.stepAff?.patchData?.(this.wizardData);
      this.stepAtt?.patchData?.(this.wizardData);
      this.stepPec?.patchData?.(this.wizardData);
      this.stepPj?.patchData?.(this.wizardData);
    } else {
      // In creation mode, patch all steps
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

  private schedulePatchAllSteps(): void {
    if (this.patchAllStepsScheduled) return;
    this.patchAllStepsScheduled = true;
    afterNextRender(
      () => {
        this.patchAllStepsScheduled = false;
        this.patchStepsFromWizardData();
      },
      {injector: this.injector},
    );
  }

  private scrollToStepAndFocus(): void {
    afterNextRender(
      () => {
        const smoothBehavior = this.prefersReducedMotion() ? 'auto' : 'smooth';
        const stepperElement = document.querySelector('.wizard-stepper');
        stepperElement?.scrollIntoView({behavior: smoothBehavior, block: 'start'});

        // Wait one more render pass to ensure lazy step content is mounted.
        afterNextRender(
          () => {
            if (this.consultationMode()) return;
            const activeStepContent = this.getActiveStepContent();
            if (!activeStepContent) return;

            const preferred = this.resolvePreferredAutofocus(activeStepContent);
            const fallback = activeStepContent.querySelector(
              this.focusableSelector(),
            ) as HTMLElement | null;
            const target = preferred ?? fallback;
            if (!target) return;

            target.scrollIntoView({behavior: smoothBehavior, block: 'nearest'});
            setTimeout(() => target.focus(), 0);
          },
          {injector: this.injector},
        );
      },
      {injector: this.injector},
    );
  }

  private getActiveStepContent(): HTMLElement | null {
    return document.querySelector(
      '.wizard-stepper .mat-horizontal-stepper-content[aria-expanded="true"], ' +
      '.wizard-stepper .mat-vertical-content[aria-expanded="true"], ' +
      '.wizard-stepper .mat-vertical-content-container[aria-expanded="true"]',
    ) as HTMLElement | null;
  }

  private resolvePreferredAutofocus(container: HTMLElement): HTMLElement | null {
    const preferred = container.querySelector('[data-autofocus-first]') as HTMLElement | null;
    if (!preferred) return null;
    if (preferred.matches(this.focusableSelector())) return preferred;
    return preferred.querySelector(this.focusableSelector()) as HTMLElement | null;
  }

  private focusableSelector(): string {
    return [
      'input:not([type="hidden"]):not([disabled]):not([readonly])',
      'textarea:not([disabled]):not([readonly])',
      'select:not([disabled])',
      'mat-select:not([disabled])',
      '[contenteditable="true"]',
      '[tabindex]:not([tabindex="-1"]):not([disabled])',
    ].join(', ');
  }

  private prefersReducedMotion(): boolean {
    return (
      typeof window !== 'undefined' &&
      typeof window.matchMedia === 'function' &&
      window.matchMedia('(prefers-reduced-motion: reduce)').matches
    );
  }

  private shouldRefreshFromEvent(event: WsEvent): boolean {
    return (
      event.type === 'PATIENT_UPDATED' ||
      event.type === 'PATIENT_CREATED' ||
      event.type === 'PEC_VALIDATED' ||
      event.type === 'PEC_CLOSED' ||
      event.type === 'PEC_DELETED' ||
      event.type === 'ATTESTATION_CREATED' ||
      event.type === 'ATTESTATION_DELETED'
    );
  }

  private extractPatientId(event: WsEvent): string | null {
    const payload = event.payload ?? {};
    return payload['patientId'] || payload['PATIENT_ID'] || payload['id'] || null;
  }

  private refreshCurrentPatientFromServer(): void {
    const id = this.editingPatientId();
    const centerId = this.appShell.currentCenterId();
    if (!id || !centerId) return;

    this.loadPatientFromServer(id, {
      reloadAttestations: this.attestationLoaded(),
      reloadPecs: this.pecLoaded(),
    });
  }

  private loadStepDataIfNeeded(stepIndex: number): void {
    if (!this.editMode() || !this.editingPatientId()) return;
    const centerId = this.appShell.currentCenterId();
    if (!centerId) return;

    const attestationIndex = this.isVacancier() ? -1 : 3;
    const pecIndex = this.isVacancier() ? 3 : 4;

    if (stepIndex === attestationIndex && !this.attestationLoaded()) {
      this.ficheStore.loadAttestations({
        centerId,
        patientId: this.editingPatientId()!,
      });
    }

    if (stepIndex === pecIndex && !this.pecLoaded()) {
      this.ficheStore.loadPecs({
        centerId,
        patientId: this.editingPatientId()!,
      });
    }
  }

  private isDataBaseValid(): boolean {
    const d = this.wizardData;
    const required = [d['nom'], d['prenom'], d['sexe'], d['dateAdmission'], d['numeroAssurance']];
    return required.every((v) => !!String(v ?? '').trim()) && this.isPecDataValid();
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
    this.ficheStore.setStep1Valid(
      !!(
        this.wizardData['nom'] &&
        this.wizardData['prenom'] &&
        this.wizardData['sexe'] &&
        this.wizardData['dateAdmission']
      ),
    );
    this.ficheStore.setStep2Valid(!!this.wizardData['numeroAssurance']);
    this.ficheStore.setStep4Valid(this.isAttestationDataValid());
    this.ficheStore.setStep5Valid(this.isPecDataValid());
  }

  private loadPatientFromServer(
    patientId: string,
    options: { reloadAttestations?: boolean; reloadPecs?: boolean } = {},
  ): void {
    const centerId = this.appShell.currentCenterId();
    if (!centerId) return;


    this.ficheStore.loadPatient({
      id: patientId,
      centerId,
      userId: this.auth.username() ?? 'demo',
    });

    if (options.reloadAttestations && !this.isVacancier()) {
      this.ficheStore.loadAttestations({centerId, patientId});
    }
    if (options.reloadPecs) {
      this.ficheStore.loadPecs({centerId, patientId});
    }
  }
}
