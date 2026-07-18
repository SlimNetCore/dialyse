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
  templateUrl: './cahier-dialyse.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './cahier-dialyse.component.css',
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
