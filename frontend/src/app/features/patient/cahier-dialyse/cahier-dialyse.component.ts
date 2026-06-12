import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
  ViewChild,
} from '@angular/core';
import {CommonModule} from '@angular/common';
import {ActivatedRoute, Router} from '@angular/router';
import {MatStepper, MatStepperModule} from '@angular/material/stepper';
import {STEPPER_GLOBAL_OPTIONS, StepperSelectionEvent} from '@angular/cdk/stepper';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatCardModule} from '@angular/material/card';
import {MatTooltipModule} from '@angular/material/tooltip';
import {MatSnackBarModule} from '@angular/material/snack-bar';
import {TranslateModule} from '@ngx-translate/core';
import {AuthStore} from '../../../core/state/auth.store';
import {CahierStepFicheComponent} from './cahier-step-fiche.component';
import {CahierStepParamedicalComponent} from './cahier-step-paramedical.component';
import {CahierStepMedicalComponent} from './cahier-step-medical.component';
import {CahierStepStatsComponent} from './cahier-step-stats.component';

type StepStatus = 'brouillon' | 'enregistré' | 'validé' | 'signé';

@Component({
  selector: 'app-cahier-dialyse',
  standalone: true,
  imports: [
    CommonModule,
    MatStepperModule,
    MatButtonModule,
    MatIconModule,
    MatCardModule,
    MatTooltipModule,
    MatSnackBarModule,
    TranslateModule,
    CahierStepFicheComponent,
    CahierStepParamedicalComponent,
    CahierStepMedicalComponent,
    CahierStepStatsComponent,
  ],
  providers: [{provide: STEPPER_GLOBAL_OPTIONS, useValue: {showError: true}}],
  template: `
    <div class="cahier-container cahier-theme">
      <div class="cahier-header">
        <button mat-icon-button (click)="goBack()"><mat-icon>arrow_back</mat-icon></button>
        <h2>{{ 'CAHIER.TITLE' | translate }}</h2>
        <p class="patient-ref">
          {{ 'CAHIER.PATIENT' | translate }}: <strong>{{ patientId }}</strong>
        </p>
      </div>

      <mat-stepper
        #stepper
        [linear]="false"
        [animationDuration]="'0'"
        [orientation]="isMobileViewport() ? 'vertical' : 'horizontal'"
        (selectionChange)="onStepChange($event)"
        class="cahier-stepper"
      >
        <!-- Step 1: Fiche Patient -->
        <mat-step
          [label]="'CAHIER.STEP_FICHE' | translate"
          [completed]="stepStates()[0].status === 'validé'"
          [editable]="stepStates()[0].canRead"
        >
          <ng-template matStepLabel>
            <span class="step-label">{{ 'CAHIER.STEP_FICHE' | translate }}</span>
            <mat-icon class="step-badge" [matTooltip]="stepLabelStatus(0)">
              {{ stepBadgeIcon(0) }}
            </mat-icon>
          </ng-template>
          @if (shouldRenderStep(0)) {
            <app-cahier-step-fiche
              #step1
              [patientId]="patientId"
              [readonly]="true"
              [recapMode]="true"
              [showProceed]="false"
              (dataChange)="onStep1DataChange($event)"
              (validChange)="onStep1ValidChange($event)"
            />
          }
        </mat-step>

        <!-- Step 2: Volet Paramédical -->
        <mat-step
          [label]="'CAHIER.STEP_PARAMEDICAL' | translate"
          [completed]="stepStates()[1].status === 'validé'"
          [editable]="stepStates()[1].canRead"
        >
          <ng-template matStepLabel>
            <span class="step-label">{{ 'CAHIER.STEP_PARAMEDICAL' | translate }}</span>
            <mat-icon class="step-badge" [matTooltip]="stepLabelStatus(1)">
              {{ stepBadgeIcon(1) }}
            </mat-icon>
          </ng-template>
          @if (shouldRenderStep(1)) {
            <app-cahier-step-paramedical
              #step2
              [patientId]="patientId"
              [readonly]="!stepStates()[1].canWrite"
              (dataChange)="onStep2DataChange($event)"
              (validChange)="onStep2ValidChange($event)"
            />
          }
        </mat-step>

        <!-- Step 3: Volet Médical -->
        <mat-step
          [label]="'CAHIER.STEP_MEDICAL' | translate"
          [completed]="stepStates()[2].status === 'validé'"
          [editable]="stepStates()[2].canRead"
        >
          <ng-template matStepLabel>
            <span class="step-label">{{ 'CAHIER.STEP_MEDICAL' | translate }}</span>
            <mat-icon class="step-badge" [matTooltip]="stepLabelStatus(2)">
              {{ stepBadgeIcon(2) }}
            </mat-icon>
          </ng-template>
          @if (shouldRenderStep(2)) {
            <app-cahier-step-medical
              #step3
              [patientId]="patientId"
              [readonly]="!stepStates()[2].canWrite"
              (dataChange)="onStep3DataChange($event)"
              (validChange)="onStep3ValidChange($event)"
            />
          }
        </mat-step>

        <!-- Step 4: Statistiques -->
        <mat-step [label]="'CAHIER.STEP_STATS' | translate" [editable]="stepStates()[3].canRead">
          <ng-template matStepLabel>
            <span class="step-label">{{ 'CAHIER.STEP_STATS' | translate }}</span>
          </ng-template>
          @if (shouldRenderStep(3)) {
            <app-cahier-step-stats #step4 [patientId]="patientId"/>
          }
        </mat-step>
      </mat-stepper>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.Eager,
  styles: [
    `
      .cahier-container {
        max-width: 1400px;
        margin: 0 auto;
        display: grid;
        gap: 18px;
      }

      .cahier-header {
        display: flex;
        align-items: center;
        gap: 12px;
        padding: 18px 20px;
        background: var(--app-surface);
        border-radius: 18px;
        border: 1px solid var(--app-border-strong);
        box-shadow: var(--app-shadow-soft);
      }

      .cahier-header h2 {
        margin: 0;
        color: var(--app-primary);
        flex: 1;
        font-size: clamp(1.3rem, 2vw, 1.7rem);
      }

      .patient-ref {
        margin: 0;
        color: var(--app-muted);
        font-size: 13px;
        background: var(--app-frost);
        border: 1px solid var(--app-border);
        border-radius: 999px;
        padding: 6px 12px;
      }

      :host ::ng-deep .cahier-stepper {
        background: transparent;
      }

      :host ::ng-deep .cahier-theme .mat-step-header {
        border-radius: 14px;
        margin: 4px;
        border: 1px solid transparent;
        transition: border-color 0.18s ease,
        background-color 0.18s ease;
      }

      :host ::ng-deep .cahier-theme .mat-step-header:hover {
        background: var(--app-hover-surface);
        border-color: var(--app-border);
      }

      :host ::ng-deep .cahier-theme .mat-step-header .mat-step-icon-selected {
        background: var(--app-primary);
        color: #041219;
      }

      :host ::ng-deep .cahier-theme .mat-stepper-horizontal-line {
        border-top-color: var(--app-border-strong);
      }

      :host ::ng-deep .cahier-theme .mat-step-content {
        padding-top: 12px;
      }

      .step-label {
        font-size: 13px;
        font-weight: 600;
      }

      .step-badge {
        margin-left: 8px;
        font-size: 14px;
      }

      @media (max-width: 900px) {
        .cahier-header {
          flex-wrap: wrap;
        }
        .cahier-header h2 {
          width: 100%;
        }
      }
    `,
  ],
})
export class CahierDialyseComponent implements AfterViewInit {
  @ViewChild('stepper') stepper?: MatStepper;
  readonly isMobileViewport = signal(
    typeof window !== 'undefined' ? window.innerWidth <= 900 : false,
  );
  // Step status tracking
  readonly step1Status = signal<StepStatus>('brouillon');
  readonly step2Status = signal<StepStatus>('brouillon');
  readonly step3Status = signal<StepStatus>('brouillon');
  readonly step4Status = signal<StepStatus>('brouillon');
  private readonly route = inject(ActivatedRoute);
  readonly patientId = this.route.snapshot.paramMap.get('id') ?? '';
  private readonly router = inject(Router);
  private readonly auth = inject(AuthStore);
  readonly stepStates = computed(() => [
    {
      label: 'Step 1 - Fiche Patient',
      status: this.step1Status(),
      canRead: true,
      canWrite: this.auth.hasRole('ADMIN') || this.auth.hasRole('SECRETAIRE'),
    },
    {
      label: 'Step 2 - Paramédical',
      status: this.step2Status(),
      canRead: true,
      canWrite: this.auth.hasRole('INFIRMIER') || this.auth.hasRole('ADMIN'),
    },
    {
      label: 'Step 3 - Médical',
      status: this.step3Status(),
      canRead: true,
      canWrite: this.auth.hasRole('MEDECIN') || this.auth.hasRole('ADMIN'),
    },
    {
      label: 'Step 4 - Statistiques',
      status: this.step4Status(),
      canRead: true,
      canWrite: false,
    },
  ]);

  private readonly currentStep = signal(0);

  constructor() {
    effect(() => {
      const width = typeof window !== 'undefined' ? window.innerWidth : 900;
      this.isMobileViewport.set(width <= 900);
    });
  }

  ngAfterViewInit(): void {
    if (typeof window !== 'undefined') {
      window.addEventListener('resize', () => {
        this.isMobileViewport.set(window.innerWidth <= 900);
      });
    }
  }

  shouldRenderStep(stepIndex: number): boolean {
    return Math.abs(this.currentStep() - stepIndex) <= 1;
  }

  stepBadgeIcon(stepIndex: number): string {
    const status = this.stepStates()[stepIndex].status;
    return status === 'brouillon'
      ? 'edit_note'
      : status === 'enregistré'
        ? 'check'
        : status === 'validé'
          ? 'verified'
          : 'done_all';
  }

  stepLabelStatus(stepIndex: number): string {
    return `Statut: ${this.stepStates()[stepIndex].status}`;
  }

  onStepChange(event: StepperSelectionEvent): void {
    this.currentStep.set(event.selectedIndex);
  }

  onStep1DataChange(_data: any): void {
    // Placeholder for step 1 data handling
  }

  onStep1ValidChange(isValid: boolean): void {
    this.step1Status.set(isValid ? 'validé' : 'enregistré');
  }

  onStep2DataChange(_data: any): void {
    // Placeholder for step 2 data handling
  }

  onStep2ValidChange(isValid: boolean): void {
    this.step2Status.set(isValid ? 'validé' : 'enregistré');
  }

  onStep3DataChange(_data: any): void {
    // Placeholder for step 3 data handling
  }

  onStep3ValidChange(isValid: boolean): void {
    this.step3Status.set(isValid ? 'validé' : 'enregistré');
  }

  goBack(): void {
    this.router.navigate(['/patients']);
  }
}
