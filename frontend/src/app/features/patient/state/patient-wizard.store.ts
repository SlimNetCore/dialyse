import {computed, inject} from '@angular/core';
import {patchState, signalStore, withComputed, withMethods, withState} from '@ngrx/signals';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {firstValueFrom} from 'rxjs';
import {BackendApiService, CreatePatientPayload} from '../../../core/api/backend-api.service';

type PatientWizardState = {
  wizardData: Record<string, any>;
  version: number;
  editingPatientId: string | null;
  consultationMode: boolean;
  currentStep: number;
  saving: boolean;
  submitStatus: 'idle' | 'success' | 'error';
  step1Valid: boolean;
  step2Valid: boolean;
  step4Valid: boolean;
  step5Valid: boolean;
  attestationLoaded: boolean;
  pecLoaded: boolean;
  loadingPatient: boolean;
  assureCatalog: any[];
  assureAssignments: any[];
  loadingAssures: boolean;
  savingAssureEdit: boolean;
  error: string | null;
  infoMessage: string | null;
};

const initialState: PatientWizardState = {
  wizardData: {},
  version: 0,
  editingPatientId: null,
  consultationMode: false,
  currentStep: 0,
  saving: false,
  submitStatus: 'idle',
  step1Valid: false,
  step2Valid: false,
  step4Valid: false,
  step5Valid: false,
  attestationLoaded: false,
  pecLoaded: false,
  loadingPatient: false,
  assureCatalog: [],
  assureAssignments: [],
  loadingAssures: false,
  savingAssureEdit: false,
  error: null,
  infoMessage: null
};

export const PatientWizardStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withDevtools('PatientWizardStore'),
  withComputed((store) => ({
    editMode: computed(() => !!store.editingPatientId())
  })),
  withMethods((store, api = inject(BackendApiService)) => ({

    // ── Cycle de vie ──────────────────────────────────
    reset(): void {
      patchState(store, initialState);
    },

    clearMessages(): void {
      patchState(store, {error: null, infoMessage: null});
    },

    // ── Setters UI ────────────────────────────────────
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
    resetSubmitStatus(): void {
      patchState(store, {submitStatus: 'idle'});
    },

    async loadPatient(params: { id: string; centerId: string; userId: string }): Promise<void> {
      patchState(store, {loadingPatient: true, error: null, infoMessage: null});
      try {
        const patient = await firstValueFrom(api.getPatient(params.id, params.centerId, params.userId));
        const wizardData = mapPatientToWizardData(patient);
        patchState(store, {
          wizardData,
          version: store.version() + 1,
          loadingPatient: false,
          attestationLoaded: false,
          pecLoaded: false,
          assureAssignments: wizardData['assureHistory'] ?? [],
          step1Valid: !!(wizardData['nom'] && wizardData['prenom'] && wizardData['sexe'] && wizardData['dateAdmission']),
          step2Valid: !!wizardData['numeroAssurance'],
          step4Valid: !!(wizardData['attestationDebut'] && wizardData['attestationFin']),
          step5Valid: !!(wizardData['pecDateDebutDemande'] && wizardData['pecDateFinDemande'])
        });
      } catch (err: any) {
        patchState(store, {
          loadingPatient: false,
          error: err?.error?.detail || err?.error?.message || 'Erreur chargement patient'
        });
        throw err;
      }
    },

    async loadAttestations(params: { centerId: string; patientId: string }): Promise<void> {
      try {
        const attestations = await firstValueFrom(api.listAttestationsByPatient(params.centerId, params.patientId));
        const history = attestations ?? [];
        const first = history[0];
        patchState(store, {
          wizardData: {
            ...store.wizardData(),
            attestationHistory: history,
            ...(first ? {
              attestationId: first.id ?? first.ID ?? null,
              attestationDebut: first.dateDebut ?? first.DATE_DEBUT ?? null,
              attestationFin: first.dateFin ?? first.DATE_FIN ?? null
            } : {})
          },
          attestationLoaded: true,
          version: store.version() + 1
        });
      } catch (err) {
        patchState(store, {
          attestationLoaded: false,
          error: 'Erreur chargement attestations'
        });
        throw err;
      }
    },

    async loadPecs(params: { centerId: string; patientId: string }): Promise<void> {
      try {
        const pecs = await firstValueFrom(api.listPecsByPatient(params.centerId, params.patientId));
        const history = pecs ?? [];
        const first = history[0];
        patchState(store, {
          wizardData: {
            ...store.wizardData(),
            pecHistory: history,
            ...(first ? {
              pecId: first.id ?? first.ID ?? null,
              pecDateDebutDemande: first.dateDebutDemande ?? first.DATE_DEBUT_DEMANDE ?? null,
              pecDateFinDemande: first.dateFinDemande ?? first.DATE_FIN_DEMANDE ?? null,
              pecForfaitDemandeId: first.forfaitDemandeId ?? first.FORFAIT_DEMANDE_ID ?? null
            } : {})
          },
          pecLoaded: true,
          version: store.version() + 1
        });
      } catch (err) {
        patchState(store, {
          pecLoaded: false,
          error: 'Erreur chargement PEC'
        });
        throw err;
      }
    },

    async loadAssureHistory(params: { centerId: string; patientId: string }): Promise<void> {
      try {
        const rows = await firstValueFrom(api.listPatientAssureHistory(params.centerId, params.patientId));
        const assignments = rows ?? [];
        const activeNums = new Set(assignments.filter((h: any) => h.actif).map((h: any) => h.numeroAssurance));
        patchState(store, {
          assureAssignments: assignments,
          assureCatalog: store.assureCatalog().map((a: any) => ({
            ...a,
            isPrimary: activeNums.has(a.numeroAssurance)
          })),
          wizardData: {
            ...store.wizardData(),
            assureHistory: assignments
          },
          version: store.version() + 1,
          error: null
        });
      } catch (err: any) {
        patchState(store, {
          error: err?.error?.detail || err?.error?.message || 'Erreur chargement historique assuré'
        });
        throw err;
      }
    },

    async searchAssures(params: { centerId: string; q: string }): Promise<void> {
      patchState(store, {loadingAssures: true, error: null, infoMessage: null});
      try {
        const rows = await firstValueFrom(api.searchAssures(params.centerId, params.q));
        const activeNums = new Set(
          store.assureAssignments().filter((h: any) => h.actif).map((h: any) => h.numeroAssurance)
        );
        patchState(store, {
          loadingAssures: false,
          assureCatalog: (rows ?? []).map((a: any) => ({
            ...a,
            isPrimary: activeNums.has(a.numeroAssurance)
          }))
        });
      } catch (err: any) {
        patchState(store, {
          loadingAssures: false,
          error: err?.error?.detail || err?.error?.message || 'Erreur chargement des assurés'
        });
        throw err;
      }
    },

    async assignAssure(params: { centerId: string; patientId: string | null; a: any }): Promise<void> {
      const assurePatch = createAssureWizardPatch(params.a);
      patchState(store, {
        error: null,
        infoMessage: null,
        assureCatalog: store.assureCatalog().map((row: any) => ({
          ...row,
          isPrimary: row.numeroAssurance === params.a.numeroAssurance
        })),
        wizardData: {
          ...store.wizardData(),
          ...assurePatch
        },
        version: store.version() + 1
      });

      if (!params.patientId) {
        patchState(store, {infoMessage: 'Assuré sélectionné pour ce patient'});
        return;
      }

      try {
        await firstValueFrom(api.assignAssureToPatient(params.centerId, params.patientId, params.a.numeroAssurance));
        const rows = await firstValueFrom(api.listPatientAssureHistory(params.centerId, params.patientId));
        const assignments = rows ?? [];
        const activeNums = new Set(assignments.filter((h: any) => h.actif).map((h: any) => h.numeroAssurance));
        patchState(store, {
          assureAssignments: assignments,
          assureCatalog: store.assureCatalog().map((row: any) => ({
            ...row,
            isPrimary: activeNums.has(row.numeroAssurance)
          })),
          wizardData: {
            ...store.wizardData(),
            ...assurePatch,
            assureHistory: assignments
          },
          version: store.version() + 1,
          infoMessage: 'Assuré affecté au patient avec succès'
        });
      } catch (err: any) {
        const activeNums = new Set(
          store.assureAssignments().filter((h: any) => h.actif).map((h: any) => h.numeroAssurance)
        );
        patchState(store, {
          assureCatalog: store.assureCatalog().map((row: any) => ({
            ...row,
            isPrimary: activeNums.has(row.numeroAssurance)
          })),
          error: err?.error?.detail || err?.error?.message || 'Erreur affectation assuré'
        });
        throw err;
      }
    },

    async updateAssure(params: { centerId: string; numeroAssurance: string; payload: any }): Promise<any> {
      try {
        const updated = await firstValueFrom(api.updateAssure(params.centerId, params.numeroAssurance, params.payload));
        const wizardData = store.wizardData();
        const nextState: Partial<PatientWizardState> = {
          assureCatalog: store.assureCatalog().map((row: any) =>
            row.numeroAssurance === params.numeroAssurance ? {...row, ...updated} : row
          ),
          infoMessage: 'Assuré mis à jour',
          error: null
        };

        if (wizardData['assureNumeroAssurance'] === params.numeroAssurance) {
          nextState.wizardData = {
            ...wizardData,
            assureNom: updated.nom ?? params.payload.nom,
            assurePrenom: updated.prenom ?? params.payload.prenom,
            assureSexe: updated.sexe ?? params.payload.sexe,
            assureDateNaissance: updated.dateNaissance ?? params.payload.dateNaissance ?? null,
            assureTelPersonnel: updated.telPersonnel ?? params.payload.telPersonnel,
            assureTelMobile: updated.telMobile ?? params.payload.telMobile,
            assureTelBureau: updated.telBureau ?? params.payload.telBureau,
            assureGroupeSanguin: updated.groupeSanguin ?? params.payload.groupeSanguin,
            assureAdresse: updated.adresse ?? params.payload.adresse
          };
          nextState.version = store.version() + 1;
        }

        patchState(store, nextState);
        return updated;
      } catch (err: any) {
        patchState(store, {
          error: err?.error?.detail || err?.error?.message || 'Erreur mise à jour assuré'
        });
        throw err;
      }
    },

    async updateAssureAssignment(params: {
      centerId: string;
      patientId: string;
      assignmentId: string;
      debut: string | null;
      fin: string | null;
    }): Promise<void> {
      patchState(store, {savingAssureEdit: true, error: null, infoMessage: null});
      try {
        await firstValueFrom(api.updateAssureAssignment(params.centerId, params.patientId, params.assignmentId, {
          dateDebutAffectation: params.debut,
          dateFinAffectation: params.fin
        }));
        const rows = await firstValueFrom(api.listPatientAssureHistory(params.centerId, params.patientId));
        patchState(store, {
          assureAssignments: rows ?? [],
          savingAssureEdit: false,
          wizardData: {
            ...store.wizardData(),
            assureHistory: rows ?? []
          },
          version: store.version() + 1,
          infoMessage: 'Affectation mise à jour'
        });
      } catch (err: any) {
        patchState(store, {
          savingAssureEdit: false,
          error: err?.error?.detail || err?.error?.message || 'Erreur mise à jour affectation'
        });
        throw err;
      }
    },

    async deletePec(params: { pecId: string; centerId: string; patientId?: string }): Promise<void> {
      try {
        await firstValueFrom(api.deletePec(params.pecId, params.centerId, params.patientId));
        const history = (store.wizardData()['pecHistory'] ?? []).filter(
          (h: any) => (h.id ?? h.ID ?? '').toString() !== params.pecId
        );
        patchState(store, {
          wizardData: {
            ...store.wizardData(),
            pecHistory: history,
            pecId: null,
            pecDateDebutDemande: null,
            pecDateFinDemande: null,
            pecForfaitDemandeId: null
          },
          version: store.version() + 1,
          infoMessage: 'PEC supprimée',
          error: null
        });
      } catch (err: any) {
        patchState(store, {
          error: err?.error?.detail || err?.message || 'Erreur suppression PEC'
        });
        throw err;
      }
    },

    async deleteAttestation(params: { attestationId: string; centerId: string; patientId?: string }): Promise<void> {
      try {
        await firstValueFrom(api.deleteAttestation(params.attestationId, params.centerId, params.patientId));
        const history = (store.wizardData()['attestationHistory'] ?? []).filter(
          (h: any) => (h.id ?? h.ID ?? '').toString() !== params.attestationId
        );
        patchState(store, {
          wizardData: {
            ...store.wizardData(),
            attestationHistory: history,
            attestationId: null,
            attestationDebut: null,
            attestationFin: null
          },
          version: store.version() + 1,
          infoMessage: 'Attestation supprimée',
          error: null
        });
      } catch (err: any) {
        patchState(store, {
          error: err?.error?.detail || err?.message || 'Erreur suppression attestation'
        });
        throw err;
      }
    },

    async printDocument(centerId: string, typeDocument: string, params: Record<string, string>, formatOverride?: string): Promise<Blob> {
      patchState(store, {error: null});
      try {
        return await firstValueFrom(api.printDocument(centerId, typeDocument, params, formatOverride));
      } catch (err: any) {
        patchState(store, {
          error: err?.error?.text || err?.error?.detail || err?.error?.message || err?.message || 'Erreur impression'
        });
        throw err;
      }
    },

    async submitPatient(params: {
      editingPatientId: string | null;
      payload: CreatePatientPayload;
    }): Promise<void> {
      patchState(store, {saving: true, submitStatus: 'idle', error: null, infoMessage: null});
      try {
        const request$ = params.editingPatientId
          ? api.updatePatient(params.editingPatientId, params.payload)
          : api.createPatient(params.payload);
        await firstValueFrom(request$);
        patchState(store, {saving: false, submitStatus: 'success'});
      } catch (err: any) {
        patchState(store, {
          saving: false,
          submitStatus: 'error',
          error: err?.error?.detail || err?.error?.message || 'Erreur lors de la sauvegarde'
        });
        throw err;
      }
    }
  }))
);

function mapPatientToWizardData(patient: any): Record<string, any> {
  const assureInfo = patient?.assureInfo ?? {};
  const wizardData = {
    ...patient,
    dateEvenementEtat: patient?.dateEvenementEtat ?? patient?.dateEvenement ?? null,
    qualiteAssure: patient?.qualiteAssure ?? patient?.qualite_assure ?? null,
    numeroAssurance: patient?.numeroAssurance?.value ?? patient?.numeroAssurance,
    assureNom: patient?.assureNom ?? assureInfo?.nom ?? assureInfo?.assureNom ?? null,
    assurePrenom: patient?.assurePrenom ?? assureInfo?.prenom ?? assureInfo?.assurePrenom ?? null,
    assureSexe: patient?.assureSexe ?? assureInfo?.sexe ?? assureInfo?.assureSexe ?? null,
    assureDateNaissance: patient?.assureDateNaissance ?? assureInfo?.dateNaissance ?? assureInfo?.assureDateNaissance ?? null,
    assureTelPersonnel: patient?.assureTelPersonnel ?? assureInfo?.telPersonnel ?? assureInfo?.assureTelPersonnel ?? null,
    assureTelMobile: patient?.assureTelMobile ?? assureInfo?.telMobile ?? assureInfo?.assureTelMobile ?? null,
    assureTelBureau: patient?.assureTelBureau ?? assureInfo?.telBureau ?? assureInfo?.assureTelBureau ?? null,
    assureAdresse: patient?.assureAdresse ?? assureInfo?.adresse ?? assureInfo?.assureAdresse ?? null,
    assureGroupeSanguin: patient?.assureGroupeSanguin ?? assureInfo?.groupeSanguin ?? assureInfo?.assureGroupeSanguin ?? null,
    assureHistory: parseJson(patient?.assureHistoryJson),
    piecesJointes: parseJson(patient?.piecesJointesJson),
    attestationId: null,
    attestationDebut: null,
    attestationFin: null,
    pecId: null,
    pecDateDebutDemande: null,
    pecDateFinDemande: null
  };

  return (wizardData['qualiteAssure'] ?? 'ASSURE_LUI_MEME') === 'ASSURE_LUI_MEME'
    ? {
      ...wizardData,
      assureNom: wizardData['nom'] ?? wizardData['assureNom'] ?? null,
      assurePrenom: wizardData['prenom'] ?? wizardData['assurePrenom'] ?? null,
      assureSexe: wizardData['sexe'] ?? wizardData['assureSexe'] ?? null,
      assureDateNaissance: wizardData['dateNaissance'] ?? wizardData['assureDateNaissance'] ?? null,
      assureTelPersonnel: wizardData['telPersonnel'] ?? wizardData['assureTelPersonnel'] ?? null,
      assureTelMobile: wizardData['telMobile'] ?? wizardData['assureTelMobile'] ?? null,
      assureTelBureau: wizardData['telBureau'] ?? wizardData['assureTelBureau'] ?? null,
      assureAdresse: wizardData['adresse'] ?? wizardData['assureAdresse'] ?? null,
      assureGroupeSanguin: wizardData['groupeSanguin'] ?? wizardData['assureGroupeSanguin'] ?? null
    }
    : wizardData;
}

function createAssureWizardPatch(assure: any): Record<string, any> {
  return {
    assureNumeroAssurance: assure?.numeroAssurance ?? null,
    assureNom: assure?.nom ?? '',
    assurePrenom: assure?.prenom ?? '',
    assureSexe: assure?.sexe ?? '',
    assureDateNaissance: assure?.dateNaissance ?? null,
    assureTelPersonnel: assure?.telPersonnel ?? '',
    assureTelMobile: assure?.telMobile ?? '',
    assureTelBureau: assure?.telBureau ?? '',
    assureAdresse: assure?.adresse ?? '',
    assureGroupeSanguin: assure?.groupeSanguin ?? ''
  };
}

function parseJson(json: any): any[] {
  if (!json) return [];
  if (Array.isArray(json)) return json;
  try {
    return JSON.parse(json);
  } catch {
    return [];
  }
}
