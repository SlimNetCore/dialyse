import {ComponentFixture, TestBed} from '@angular/core/testing';
import {PatientWizardComponent} from './patient-wizard.component';
import {PatientFicheStore} from '../state/patient-fiche.store';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {AuthStore} from '../../../core/state/auth.store';
import {WebSocketService} from '../../../core/ws/websocket.service';
import {ActivatedRoute, Router} from '@angular/router';
import {MatSnackBar} from '@angular/material/snack-bar';
import {TranslateService} from '@ngx-translate/core';
import {signal} from '@angular/core';

describe('PatientWizardComponent', () => {
  let component: PatientWizardComponent;
  let fixture: ComponentFixture<PatientWizardComponent>;
  let mockFicheStore: any;
  let mockAppShellStore: any;
  let mockActivatedRoute: any;

  beforeEach(async () => {
    // Mock stores
    mockFicheStore = {
      currentStep: signal(0),
      saving: signal(false),
      step1Valid: signal(false),
      step2Valid: signal(false),
      step4Valid: signal(false),
      step5Valid: signal(false),
      editingPatientId: signal(null),
      consultationMode: signal(false),
      version: signal(0),
      editMode: signal(false),
      wizardData: signal({}),
      attestationLoaded: signal(false),
      pecLoaded: signal(false),
      submitStatus: signal('idle'),
      error: signal(null),
      loadingPatient: signal(false),
      lastAction: signal(null),
      lastSuccess: signal(null),
      lastError: signal(null),
      loadPatient: () => {
      },
      loadAttestations: () => {
      },
      loadPecs: () => {
      },
      reset: () => {
      },
      setEditingPatientId: (id: string | null) => {
        mockFicheStore.editingPatientId.set(id);
        mockFicheStore.editMode.set(!!id);
      },
      setConsultationMode: (mode: boolean) => mockFicheStore.consultationMode.set(mode),
      setStep1Valid: (v: boolean) => mockFicheStore.step1Valid.set(v),
      setStep2Valid: (v: boolean) => mockFicheStore.step2Valid.set(v),
      setCurrentStep: (v: number) => mockFicheStore.currentStep.set(v),
      setWizardData: (data: any) => mockFicheStore.wizardData.set(data),
    };

    mockAppShellStore = {
      currentCenterId: () => '11111111-1111-1111-1111-111111111111',
      switchCenter: () => {
      }
    };

    mockActivatedRoute = {
      snapshot: {
        paramMap: {
          get: (key: string) => key === 'id' ? 'test-patient-id' : null
        }
      }
    };

    await TestBed.configureTestingModule({
      imports: [PatientWizardComponent],
      providers: [
        {provide: PatientFicheStore, useValue: mockFicheStore},
        {provide: AppShellStore, useValue: mockAppShellStore},
        {provide: AuthStore, useValue: {username: () => 'test-user', hasRole: () => false}},
        {provide: WebSocketService, useValue: {lastEvent: () => null}},
        {provide: ActivatedRoute, useValue: mockActivatedRoute},
        {provide: Router, useValue: {navigate: () => Promise.resolve(true)}},
        {
          provide: MatSnackBar, useValue: {
            open: () => {
            }
          }
        },
        {provide: TranslateService, useValue: {instant: (key: string) => key}}
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(PatientWizardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should render all steps when loading patient data', () => {
    // Set up mock patient data
    const mockPatientData = {
      id: 'test-patient-id',
      nom: 'Dupont',
      prenom: 'Jean',
      sexe: 'M',
      dateAdmission: '2024-01-01',
      dateNaissance: '1980-01-01',
      numeroAssurance: 'AMA123456'
    };

    // Simulate patient loading
    component['loadingPatientData'].set(true);
    mockFicheStore.wizardData.set(mockPatientData);

    // Verify that shouldRenderStep returns true for all steps during loading
    expect(component.shouldRenderStep(0)).toBe(true, 'Step 0 should be rendered while loading');
    expect(component.shouldRenderStep(1)).toBe(true, 'Step 1 should be rendered while loading');
    expect(component.shouldRenderStep(2)).toBe(true, 'Step 2 should be rendered while loading');
    expect(component.shouldRenderStep(3)).toBe(true, 'Step 3 should be rendered while loading');
  });

  it('should stop rendering all steps after loading is complete', () => {
    mockFicheStore.editingPatientId.set('test-patient-id');
    mockFicheStore.wizardData.set({nom: 'Dupont'});
    component['loadingPatientData'].set(false);

    // After loading, only active and adjacent steps should be rendered in edit mode
    component['ficheStore'].currentStep.set(0);
    expect(component.shouldRenderStep(0)).toBe(true, 'Active step should be rendered');
    expect(component.shouldRenderStep(1)).toBe(true, 'Adjacent step should be rendered');
    expect(component.shouldRenderStep(2)).toBe(false, 'Non-adjacent step should not be rendered');
  });

  it('should patch all steps with wizard data in edit mode', () => {
    const mockPatientData = {
      nom: 'Dupont',
      prenom: 'Jean',
      sexe: 'M'
    };

    component['ficheStore'].editingPatientId.set('test-id');
    component['ficheStore'].wizardData.set(mockPatientData);

    // Mock step components
    const mockStepGen = {patchData: jasmine.createSpy('patchData')};
    component['stepGen'] = mockStepGen;

    // Call patchStepsFromWizardData
    component['patchStepsFromWizardData']();

    // Verify patchData was called with wizard data
    expect(mockStepGen.patchData).toHaveBeenCalledWith(mockPatientData);
  });
});


