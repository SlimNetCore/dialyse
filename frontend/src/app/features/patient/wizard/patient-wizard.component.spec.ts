import {TestBed} from '@angular/core/testing';
import {PatientWizardComponent} from './patient-wizard.component';
import {PatientFicheStore} from '../state/patient-fiche.store';
import {PatientListStore} from '../state/patient-list.store';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {AuthStore} from '../../../core/state/auth.store';
import {WebSocketService} from '../../../core/ws/websocket.service';
import {ActivatedRoute, Router} from '@angular/router';
import {MatSnackBar} from '@angular/material/snack-bar';
import {TranslateService} from '@ngx-translate/core';
import {signal} from '@angular/core';
import {beforeEach, describe, expect, it, vi} from 'vitest';

/**
 * Tests unitaires de PatientWizardComponent via runInInjectionContext
 * (évite la compilation du template externe non supportée dans Vitest JSDOM)
 */
describe('PatientWizardComponent', () => {
  let mockFicheStore: any;

  beforeEach(() => {
    mockFicheStore = {
      currentStep: signal(0),
      saving: signal(false),
      step1Valid: signal(false),
      step2Valid: signal(false),
      step4Valid: signal(false),
      step5Valid: signal(false),
      editingPatientId: signal<string | null>(null),
      consultationMode: signal(false),
      version: signal(0),
      editMode: signal(false),
      wizardData: signal<Record<string, any>>({}),
      attestationLoaded: signal(false),
      pecLoaded: signal(false),
      submitStatus: signal('idle'),
      error: signal(null),
      loadingPatient: signal(false),
      lastAction: signal(null),
      lastSuccess: signal(null),
      lastError: signal(null),
      loadPatient: vi.fn(),
      loadAttestations: vi.fn(),
      loadPecs: vi.fn(),
      reset: vi.fn(),
      setEditingPatientId: vi.fn((id: string | null) => mockFicheStore.editingPatientId.set(id)),
      setConsultationMode: vi.fn((mode: boolean) => mockFicheStore.consultationMode.set(mode)),
      setStep1Valid: vi.fn((v: boolean) => mockFicheStore.step1Valid.set(v)),
      setStep2Valid: vi.fn((v: boolean) => mockFicheStore.step2Valid.set(v)),
      setCurrentStep: vi.fn((v: number) => mockFicheStore.currentStep.set(v)),
      setWizardData: vi.fn((data: any) => mockFicheStore.wizardData.set(data)),
    };

    const mockPatientListStore = {
      load: vi.fn(),
      items: signal([]),
      loading: signal(false),
    };

    TestBed.configureTestingModule({
      providers: [
        {provide: PatientFicheStore, useValue: mockFicheStore},
        {provide: PatientListStore, useValue: mockPatientListStore},
        {provide: AppShellStore, useValue: {currentCenterId: () => 'center-1', switchCenter: vi.fn()}},
        {provide: AuthStore, useValue: {username: () => 'test-user', hasRole: () => false}},
        {provide: WebSocketService, useValue: {lastEvent: () => null}},
        {provide: ActivatedRoute, useValue: {snapshot: {paramMap: {get: () => null}}}},
        {provide: Router, useValue: {navigate: vi.fn(() => Promise.resolve(true))}},
        {provide: MatSnackBar, useValue: {open: vi.fn()}},
        {provide: TranslateService, useValue: {instant: (key: string) => key}},
      ]
    });
  });

  it('should create', () => {
    const component = TestBed.runInInjectionContext(() => new PatientWizardComponent());
    expect(component).toBeTruthy();
  });

  it('shouldRenderStep always returns true (all steps permanently rendered)', () => {
    const component = TestBed.runInInjectionContext(() => new PatientWizardComponent());
    // shouldRenderStep toujours true — préserve les valeurs de formulaire lors du changement d'étape
    expect(component.shouldRenderStep(0)).toBe(true);
    expect(component.shouldRenderStep(1)).toBe(true);
    expect(component.shouldRenderStep(2)).toBe(true);
    expect(component.shouldRenderStep(3)).toBe(true);
  });

  it('should patch step with wizard data when patchStepsFromWizardData is called', () => {
    const component = TestBed.runInInjectionContext(() => new PatientWizardComponent());
    const mockPatientData = {nom: 'Dupont', prenom: 'Jean', sexe: 'M'};
    mockFicheStore.editingPatientId.set('test-id');
    mockFicheStore.wizardData.set(mockPatientData);

    const mockStepGen = {patchData: vi.fn()} as any;
    component['stepGen'] = mockStepGen;
    component['patchStepsFromWizardData']();

    expect(mockStepGen.patchData).toHaveBeenCalledWith(mockPatientData);
  });

  it('should initialize with currentStep 0', () => {
    TestBed.runInInjectionContext(() => new PatientWizardComponent());
    expect(mockFicheStore.currentStep()).toBe(0);
  });
});
