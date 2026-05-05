import {computed} from '@angular/core';
import {patchState, signalStore, withComputed, withMethods, withState} from '@ngrx/signals';
import {withDevtools} from '@angular-architects/ngrx-toolkit';

type PatientFicheState = {
  wizardData: Record<string, any>;
  version: number;
  editingPatientId: string | null;
  consultationMode: boolean;
  currentStep: number;
  saving: boolean;
  step1Valid: boolean;
  step2Valid: boolean;
  step4Valid: boolean;
  step5Valid: boolean;
  attestationLoaded: boolean;
  pecLoaded: boolean;
};

const initialState: PatientFicheState = {
  wizardData: {},
  version: 0,
  editingPatientId: null,
  consultationMode: false,
  currentStep: 0,
  saving: false,
  step1Valid: false,
  step2Valid: false,
  step4Valid: false,
  step5Valid: false,
  attestationLoaded: false,
  pecLoaded: false
};

export const PatientFicheStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withDevtools('PatientFicheStore'),
  withComputed((store) => ({
    editMode: computed(() => !!store.editingPatientId())
  })),
  withMethods((store) => ({
    setEditingPatientId(editingPatientId: string | null): void {
      patchState(store, {editingPatientId});
    },

    setConsultationMode(consultationMode: boolean): void {
      patchState(store, {consultationMode});
    },

    setCurrentStep(currentStep: number): void {
      patchState(store, {currentStep});
    },

    setSaving(saving: boolean): void {
      patchState(store, {saving});
    },

    setStep1Valid(step1Valid: boolean): void {
      patchState(store, {step1Valid});
    },

    setStep2Valid(step2Valid: boolean): void {
      patchState(store, {step2Valid});
    },

    setStep4Valid(step4Valid: boolean): void {
      patchState(store, {step4Valid});
    },

    setStep5Valid(step5Valid: boolean): void {
      patchState(store, {step5Valid});
    },

    setAttestationLoaded(attestationLoaded: boolean): void {
      patchState(store, {attestationLoaded});
    },

    setPecLoaded(pecLoaded: boolean): void {
      patchState(store, {pecLoaded});
    },

    setWizardData(wizardData: Record<string, any>): void {
      patchState(store, {
        wizardData,
        version: store.version() + 1
      });
    },

    mergeWizardData(partial: Record<string, any>): void {
      patchState(store, {
        wizardData: {...store.wizardData(), ...partial},
        version: store.version() + 1
      });
    },

    bumpVersion(): void {
      patchState(store, {version: store.version() + 1});
    },

    reset(): void {
      patchState(store, initialState);
    }
  }))
);


