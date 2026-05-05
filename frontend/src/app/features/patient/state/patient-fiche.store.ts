import {computed} from '@angular/core';
import {patchState, signalStore, withComputed, withMethods, withState} from '@ngrx/signals';
import {withDevtools} from '@angular-architects/ngrx-toolkit';

type PatientFicheState = {
  wizardData: Record<string, any>;
  version: number;
  editingPatientId: string | null;
  consultationMode: boolean;
};

const initialState: PatientFicheState = {
  wizardData: {},
  version: 0,
  editingPatientId: null,
  consultationMode: false
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

