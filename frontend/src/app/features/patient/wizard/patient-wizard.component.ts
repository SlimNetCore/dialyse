import { Component, inject, signal, computed, ViewChild } from '@angular/core';
import { Router } from '@angular/router';
import { MatStepperModule, MatStepper } from '@angular/material/stepper';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { STEPPER_GLOBAL_OPTIONS } from '@angular/cdk/stepper';
import { StepGeneralitesComponent } from './step-generalites.component';
import { StepAssuranceComponent } from './step-assurance.component';
import { StepAffectationComponent } from './step-affectation.component';
import { StepAttestationComponent } from './step-attestation.component';
import { StepPecComponent } from './step-pec.component';
import { StepPiecesJointesComponent } from './step-pieces-jointes.component';
import { BackendApiService } from '../../../core/api/backend-api.service';
import { AuthSessionService } from '../../../core/auth/auth-session.service';
import { AppShellStore } from '../../../core/state/app-shell.store';

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
    <div class="wizard-container">
      <div class="wizard-header">
        <button mat-icon-button (click)="goBack()"><mat-icon>arrow_back</mat-icon></button>
        <h2>{{ 'WIZARD.TITLE' | translate }}</h2>
        <div class="wizard-progress">
          {{ 'WIZARD.STEP' | translate }} {{ currentStep() + 1 }} / {{ totalSteps() }}
        </div>
      </div>

      <mat-stepper #stepper [linear]="false" [animationDuration]="'0'" (selectionChange)="onStepChange($event)" class="wizard-stepper">
        <!-- Step 1: Généralités -->
        <mat-step [label]="'WIZARD.STEP_GENERALITES' | translate" [completed]="step1Valid()" [editable]="true">
          <app-step-generalites #stepGen (dataChange)="updateData($event)" (validChange)="step1Valid.set($event)" />
          <div class="step-actions">
            <span></span>
            <button mat-flat-button class="next-btn" (click)="tryNext(0)">
              {{ 'WIZARD.NEXT' | translate }} <mat-icon>chevron_right</mat-icon>
            </button>
          </div>
        </mat-step>

        <!-- Step 2: Assurance -->
        <mat-step [label]="'WIZARD.STEP_ASSURANCE' | translate" [completed]="step2Valid()" [editable]="true">
          <app-step-assurance #stepAss (dataChange)="updateData($event)" (validChange)="step2Valid.set($event)" />
          <div class="step-actions">
            <button mat-stroked-button matStepperPrevious><mat-icon>chevron_left</mat-icon> {{ 'WIZARD.PREV' | translate }}</button>
            <button mat-flat-button class="next-btn" (click)="tryNext(1)">{{ 'WIZARD.NEXT' | translate }} <mat-icon>chevron_right</mat-icon></button>
          </div>
        </mat-step>

        <!-- Step 3: Affectation -->
        <mat-step [label]="'WIZARD.STEP_AFFECTATION' | translate" [editable]="true">
          <app-step-affectation #stepAff (dataChange)="updateData($event)" />
          <div class="step-actions">
            <button mat-stroked-button matStepperPrevious><mat-icon>chevron_left</mat-icon> {{ 'WIZARD.PREV' | translate }}</button>
            <button mat-flat-button class="next-btn" matStepperNext>{{ 'WIZARD.NEXT' | translate }} <mat-icon>chevron_right</mat-icon></button>
          </div>
        </mat-step>

        <!-- Step 4: Attestation (hidden for vacancier) -->
        @if (!isVacancier()) {
          <mat-step [label]="'WIZARD.STEP_ATTESTATION' | translate" [completed]="step4Valid()" [editable]="true">
            <app-step-attestation #stepAtt (dataChange)="updateData($event)" (validChange)="step4Valid.set($event)" />
            <div class="step-actions">
              <button mat-stroked-button matStepperPrevious><mat-icon>chevron_left</mat-icon> {{ 'WIZARD.PREV' | translate }}</button>
              <button mat-flat-button class="next-btn" (click)="tryNext(3)">{{ 'WIZARD.NEXT' | translate }} <mat-icon>chevron_right</mat-icon></button>
            </div>
          </mat-step>
        }

        <!-- Step 5: PEC -->
        <mat-step [label]="'WIZARD.STEP_PEC' | translate" [completed]="step5Valid()" [editable]="true">
          <app-step-pec #stepPec (dataChange)="updateData($event)" (validChange)="step5Valid.set($event)" />
          <div class="step-actions">
            <button mat-stroked-button matStepperPrevious><mat-icon>chevron_left</mat-icon> {{ 'WIZARD.PREV' | translate }}</button>
            <button mat-flat-button class="next-btn" (click)="tryNext(4)">{{ 'WIZARD.NEXT' | translate }} <mat-icon>chevron_right</mat-icon></button>
          </div>
        </mat-step>

        <!-- Step 6: Pièces jointes -->
        <mat-step [label]="'WIZARD.STEP_PJ' | translate" [editable]="true">
          <app-step-pieces-jointes (dataChange)="updateData($event)" />
          <div class="step-actions">
            <button mat-stroked-button matStepperPrevious><mat-icon>chevron_left</mat-icon> {{ 'WIZARD.PREV' | translate }}</button>
            <button mat-flat-button class="save-btn" (click)="submit()" [disabled]="saving() || !canSave()">
              <mat-icon>save</mat-icon> {{ 'WIZARD.SAVE' | translate }}
            </button>
          </div>
        </mat-step>
      </mat-stepper>
    </div>
  `,
  styles: [`
    .wizard-container { max-width: 1100px; margin: 0 auto; }
    .wizard-header {
      display: flex; align-items: center; gap: 12px; margin-bottom: 16px;
    }
    .wizard-header h2 { flex: 1; margin: 0; font-size: 1.3rem; color: #1b5e20; }
    .wizard-progress {
      background: #e8f5e9; color: #1b5e20; padding: 6px 16px; border-radius: 20px;
      font-size: 13px; font-weight: 600;
    }
    .step-actions {
      display: flex; justify-content: space-between; margin-top: 16px; padding-top: 14px;
      border-top: 1px solid #e0e0e0;
    }
    .next-btn, .save-btn {
      --mdc-filled-button-container-color: #1b5e20 !important;
      --mdc-filled-button-label-text-color: #fff !important;
    }
    .save-btn:disabled {
      --mdc-filled-button-container-color: #bdbdbd !important;
    }
    :host ::ng-deep .wizard-stepper { background: transparent; }
    :host ::ng-deep .wizard-stepper .mat-horizontal-stepper-content {
      background: #f4faf5; border-radius: 12px; padding: 12px 20px 20px; margin-top: 0;
      border: 1px solid #e0ede2; min-height: 350px;
    }
    :host ::ng-deep .wizard-stepper .mat-horizontal-stepper-content[aria-expanded="false"] {
      min-height: 0 !important; padding: 0 !important; overflow: hidden;
    }
    :host ::ng-deep .wizard-stepper .mat-step-header .mat-step-icon-selected {
      background-color: #1b5e20 !important;
    }
    :host ::ng-deep .wizard-stepper .mat-step-header .mat-step-icon-state-done {
      background-color: #2e7d32 !important;
    }
    :host ::ng-deep .wizard-stepper .mat-step-header .mat-step-icon-state-error {
      background-color: #d32f2f !important;
    }
  `]
})
export class PatientWizardComponent {
  @ViewChild('stepper') stepper!: MatStepper;
  @ViewChild('stepGen') stepGen!: StepGeneralitesComponent;
  @ViewChild('stepAss') stepAss!: StepAssuranceComponent;
  @ViewChild('stepAff') stepAff!: StepAffectationComponent;
  @ViewChild('stepAtt') stepAtt!: StepAttestationComponent;
  @ViewChild('stepPec') stepPec!: StepPecComponent;

  private readonly api = inject(BackendApiService);
  private readonly auth = inject(AuthSessionService);
  private readonly store = inject(AppShellStore);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);
  private readonly translate = inject(TranslateService);

  readonly currentStep = signal(0);
  readonly saving = signal(false);
  readonly step1Valid = signal(false);
  readonly step2Valid = signal(false);
  readonly step4Valid = signal(false);
  readonly step5Valid = signal(false);
  readonly isVacancier = signal(false);
  wizardData: Record<string, any> = {};

  readonly totalSteps = computed(() => this.isVacancier() ? 5 : 6);

  readonly canSave = computed(() => {
    const base = this.step1Valid() && this.step2Valid() && this.step5Valid();
    if (this.isVacancier()) return base;
    return base && this.step4Valid();
  });

  updateData(partial: Record<string, any>): void {
    this.wizardData = { ...this.wizardData, ...partial };
    // Propagate qualiteAssure to step 2 if it changed
    if (partial['qualiteAssure'] !== undefined && this.stepAss) {
      this.stepAss.setQualiteAssure(partial['qualiteAssure']);
    }
    if (partial['typePatient'] !== undefined) {
      this.isVacancier.set(partial['typePatient'] === 'VACANCIER');
    }
  }

  onStepChange(event: any): void {
    this.currentStep.set(event.selectedIndex);
  }

  /** Validate current step before moving to next */
  tryNext(stepIndex: number): void {
    const stepComponents = [this.stepGen, this.stepAss, this.stepAff, this.stepAtt, this.stepPec];
    const step = stepComponents[stepIndex];
    if (step) {
      step.markTouched();
      if (!step.isValid()) {
        this.snackBar.open(
          this.translate.instant('WIZARD.VALIDATION_ERROR') || 'Veuillez remplir les champs obligatoires',
          'OK', { duration: 3000, panelClass: 'snack-error' }
        );
        return;
      }
    }
    this.stepper.next();
  }

  goBack(): void { this.router.navigate(['/patients']); }

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

    this.api.createPatient({
      centerId,
      userId: this.auth.username() ?? 'demo',
      nom: d['nom'], prenom: d['prenom'], sexe: d['sexe'],
      dateAdmission: toDate(d['dateAdmission']) || new Date().toISOString().slice(0, 10),
      dateNaissance: toDate(d['dateNaissance']),
      numeroAssurance: d['numeroAssurance'] || 'TEMP-' + Date.now(),
      typePatient: d['typePatient'] || 'NON_VACANCIER',
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
      centrePayeurId: d['centrePayeurId'], medecinTraitantId: d['medecinTraitantId'],
      salleId: d['salleId'], positionId: d['positionId'],
      transporteurAllerId: d['transporteurAllerId'], transporteurRetourId: d['transporteurRetourId'],
      categorieTransportId: d['categorieTransportId'],
      jourDimanche: d['jourDimanche'] || false, jourLundi: d['jourLundi'] || false,
      jourMardi: d['jourMardi'] || false, jourMercredi: d['jourMercredi'] || false,
      jourJeudi: d['jourJeudi'] || false, jourVendredi: d['jourVendredi'] || false,
      jourSamedi: d['jourSamedi'] || false,
      attestationDebut: toDate(d['attestationDebut']), attestationFin: toDate(d['attestationFin']),
      assureNom: d['assureNom'], assurePrenom: d['assurePrenom'], assureSexe: d['assureSexe'],
      assureDateNaissance: toDate(d['assureDateNaissance']),
      assureTelPersonnel: d['assureTelPersonnel'], assureAdresse: d['assureAdresse'],
      assureGroupeSanguin: d['assureGroupeSanguin'],
      pecDateDebutDemande: toDate(d['pecDateDebutDemande']),
      pecDateFinDemande: toDate(d['pecDateFinDemande']),
      pecForfaitDemandeId: d['pecForfaitDemandeId']
    } as any).subscribe({
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
}
