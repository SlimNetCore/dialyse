import {computed, inject} from '@angular/core';
import {patchState, signalStore, withComputed, withMethods, withState} from '@ngrx/signals';
import {rxMethod} from '@ngrx/signals/rxjs-interop';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {catchError, of, pipe, switchMap, tap} from 'rxjs';
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
  lastSavedPatientId: string | null;
  lastAction: string | null;
  lastSuccess: boolean | null;
  lastError: string | null;
  lastMessage: string | null;
  lastActionMeta: Record<string, any> | null;
  lastActionId: number;
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
  lastSavedPatientId: null,
  lastAction: null,
  lastSuccess: null,
  lastError: null,
  lastMessage: null,
  lastActionMeta: null,
  lastActionId: 0,
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

    clearLastSavedPatientId(): void {
      patchState(store, {lastSavedPatientId: null});
    },

    clearLastAction(): void {
      patchState(store, {
        lastAction: null,
        lastSuccess: null,
        lastError: null,
        lastMessage: null,
        lastActionMeta: null
      });
    },

    loadPatient: rxMethod<{ id: string; centerId: string; userId: string }>(
      pipe(
        tap(() => patchState(store, {loadingPatient: true, error: null, infoMessage: null})),
        switchMap((params) => api.getPatient(params.id, params.centerId, params.userId).pipe(
          tap((patient: any) => {
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
            publishActionSuccess(store, 'LOAD_PATIENT');
          }),
          catchError((err: any) => {
            const message = err?.error?.detail || err?.error?.message || 'Erreur chargement patient';
            patchState(store, {loadingPatient: false, error: message});
            publishActionError(store, 'LOAD_PATIENT', message);
            return of(null);
          })
        ))
      )
    ),

    loadAttestations: rxMethod<{ centerId: string; patientId: string }>(
      pipe(
        switchMap((params) => api.listAttestationsByPatient(params.centerId, params.patientId).pipe(
          tap((attestations: any[]) => {
            const history = attestations ?? [];
            const latest = pickLatestAttestation(history);
            patchState(store, {
              wizardData: {
                ...store.wizardData(),
                attestationHistory: history,
                ...(latest ? {
                  attestationId: latest.id ?? latest.ID ?? null,
                  attestationDebut: latest.dateDebut ?? latest.DATE_DEBUT ?? null,
                  attestationFin: latest.dateFin ?? latest.DATE_FIN ?? null
                } : {})
              },
              attestationLoaded: true,
              version: store.version() + 1,
              error: null
            });
            publishActionSuccess(store, 'LOAD_ATTESTATIONS');
          }),
          catchError(() => {
            const message = 'Erreur chargement attestations';
            patchState(store, {attestationLoaded: false, error: message});
            publishActionError(store, 'LOAD_ATTESTATIONS', message);
            return of(null);
          })
        ))
      )
    ),

    loadPecs: rxMethod<{ centerId: string; patientId: string }>(
      pipe(
        switchMap((params) => api.listPecsByPatient(params.centerId, params.patientId).pipe(
          tap((pecs: any[]) => {
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
              version: store.version() + 1,
              error: null
            });
            publishActionSuccess(store, 'LOAD_PECS');
          }),
          catchError(() => {
            const message = 'Erreur chargement PEC';
            patchState(store, {pecLoaded: false, error: message});
            publishActionError(store, 'LOAD_PECS', message);
            return of(null);
          })
        ))
      )
    ),

    loadAssureHistory: rxMethod<{ centerId: string; patientId: string }>(
      pipe(
        switchMap((params) => api.listPatientAssureHistory(params.centerId, params.patientId).pipe(
          tap((rows: any[]) => {
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
            publishActionSuccess(store, 'LOAD_ASSURE_HISTORY');
          }),
          catchError((err: any) => {
            const message = err?.error?.detail || err?.error?.message || 'Erreur chargement historique assuré';
            patchState(store, {error: message});
            publishActionError(store, 'LOAD_ASSURE_HISTORY', message);
            return of(null);
          })
        ))
      )
    ),

    searchAssures: rxMethod<{ centerId: string; q: string }>(
      pipe(
        tap(() => patchState(store, {loadingAssures: true, error: null, infoMessage: null})),
        switchMap((params) => api.searchAssures(params.centerId, params.q).pipe(
          tap((rows: any[]) => {
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
            publishActionSuccess(store, 'SEARCH_ASSURES');
          }),
          catchError((err: any) => {
            const message = err?.error?.detail || err?.error?.message || 'Erreur chargement des assurés';
            patchState(store, {loadingAssures: false, error: message});
            publishActionError(store, 'SEARCH_ASSURES', message);
            return of(null);
          })
        ))
      )
    ),

    assignAssure: rxMethod<{ centerId: string; patientId: string | null; a: any }>(
      pipe(
        tap((params) => {
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
        }),
        switchMap((params) => {
          if (!params.patientId) {
            patchState(store, {infoMessage: 'Assuré sélectionné pour ce patient'});
            return of(null);
          }

          const assurePatch = createAssureWizardPatch(params.a);
          return api.assignAssureToPatient(params.centerId, params.patientId, params.a.numeroAssurance).pipe(
            switchMap(() => api.listPatientAssureHistory(params.centerId, params.patientId!)),
            tap((rows: any[]) => {
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
              publishActionSuccess(store, 'ASSIGN_ASSURE', {
                message: 'Assuré affecté au patient avec succès',
                meta: {numeroAssurance: params.a.numeroAssurance}
              });
            }),
            catchError((err: any) => {
              const activeNums = new Set(
                store.assureAssignments().filter((h: any) => h.actif).map((h: any) => h.numeroAssurance)
              );
              const message = err?.error?.detail || err?.error?.message || 'Erreur affectation assuré';
              patchState(store, {
                assureCatalog: store.assureCatalog().map((row: any) => ({
                  ...row,
                  isPrimary: activeNums.has(row.numeroAssurance)
                })),
                error: message
              });
              publishActionError(store, 'ASSIGN_ASSURE', message, {numeroAssurance: params.a.numeroAssurance});
              return of(null);
            })
          );
        })
      )
    ),

    updateAssure: rxMethod<{ centerId: string; numeroAssurance: string; payload: any }>(
      pipe(
        switchMap((params) => api.updateAssure(params.centerId, params.numeroAssurance, params.payload).pipe(
          tap((updated: any) => {
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
            publishActionSuccess(store, 'UPDATE_ASSURE', {
              message: 'Assuré mis à jour',
              meta: {numeroAssurance: params.numeroAssurance}
            });
          }),
          catchError((err: any) => {
            const message = err?.error?.detail || err?.error?.message || 'Erreur mise à jour assuré';
            patchState(store, {error: message});
            publishActionError(store, 'UPDATE_ASSURE', message, {numeroAssurance: params.numeroAssurance});
            return of(null);
          })
        ))
      )
    ),

    updateAssureAssignment: rxMethod<{
      centerId: string;
      patientId: string;
      assignmentId: string;
      debut: string | null;
      fin: string | null;
    }>(
      pipe(
        tap(() => patchState(store, {savingAssureEdit: true, error: null, infoMessage: null})),
        switchMap((params) => api.updateAssureAssignment(params.centerId, params.patientId, params.assignmentId, {
          dateDebutAffectation: params.debut,
          dateFinAffectation: params.fin
        }).pipe(
          switchMap(() => api.listPatientAssureHistory(params.centerId, params.patientId)),
          tap((rows: any[]) => {
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
            publishActionSuccess(store, 'UPDATE_ASSURE_ASSIGNMENT', {message: 'Affectation mise à jour'});
          }),
          catchError((err: any) => {
            const message = err?.error?.detail || err?.error?.message || 'Erreur mise à jour affectation';
            patchState(store, {savingAssureEdit: false, error: message});
            publishActionError(store, 'UPDATE_ASSURE_ASSIGNMENT', message);
            return of(null);
          })
        ))
      )
    ),

    deletePec: rxMethod<{ pecId: string; centerId: string; patientId?: string }>(
      pipe(
        switchMap((params) => api.deletePec(params.pecId, params.centerId, params.patientId).pipe(
          tap(() => {
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
            publishActionSuccess(store, 'DELETE_PEC', {
              message: 'PEC supprimée',
              meta: {pecId: params.pecId}
            });
          }),
          catchError((err: any) => {
            const message = err?.error?.detail || err?.message || 'Erreur suppression PEC';
            patchState(store, {error: message});
            publishActionError(store, 'DELETE_PEC', message, {pecId: params.pecId});
            return of(null);
          })
        ))
      )
    ),

    deleteAttestation: rxMethod<{ attestationId: string; centerId: string; patientId?: string }>(
      pipe(
        switchMap((params) => api.deleteAttestation(params.attestationId, params.centerId, params.patientId).pipe(
          tap(() => {
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
            publishActionSuccess(store, 'DELETE_ATTESTATION', {
              message: 'Attestation supprimée',
              meta: {attestationId: params.attestationId}
            });
          }),
          catchError((err: any) => {
            const message = err?.error?.detail || err?.message || 'Erreur suppression attestation';
            patchState(store, {error: message});
            publishActionError(store, 'DELETE_ATTESTATION', message, {attestationId: params.attestationId});
            return of(null);
          })
        ))
      )
    ),

    printDocument: rxMethod<{
      centerId: string;
      typeDocument: string;
      params: Record<string, string>;
      formatOverride?: string;
    }>(
      pipe(
        tap(() => patchState(store, {error: null})),
        switchMap((params) => api.printDocument(
          params.centerId,
          params.typeDocument,
          params.params,
          params.formatOverride
        ).pipe(
          tap((blob: Blob) => {
            const url = URL.createObjectURL(blob);
            window.open(url, '_blank');
            publishActionSuccess(store, 'PRINT_DOCUMENT', {
              meta: {typeDocument: params.typeDocument, params: params.params}
            });
          }),
          catchError((err: any) => {
            const message = err?.error?.text || err?.error?.detail || err?.error?.message || err?.message || 'Erreur impression';
            patchState(store, {error: message});
            publishActionError(store, 'PRINT_DOCUMENT', message, {
              typeDocument: params.typeDocument,
              params: params.params
            });
            return of(null);
          })
        ))
      )
    ),

    submitPatient: rxMethod<{
      editingPatientId: string | null;
      payload: CreatePatientPayload;
    }>(
      pipe(
        tap(() => patchState(store, {saving: true, submitStatus: 'idle', error: null, infoMessage: null})),
        switchMap((params) => {
          const request$ = params.editingPatientId
            ? api.updatePatient(params.editingPatientId, params.payload)
            : api.createPatient(params.payload);
          return request$.pipe(
            tap((result: any) => patchState(store, {
              saving: false,
              submitStatus: 'success',
              lastSavedPatientId: result?.id ?? params.editingPatientId ?? null
            })),
            tap(() => publishActionSuccess(store, 'SUBMIT_PATIENT')),
            catchError((err: any) => {
              const message = err?.error?.detail || err?.error?.message || 'Erreur lors de la sauvegarde';
              patchState(store, {
                saving: false,
                submitStatus: 'error',
                error: message
              });
              publishActionError(store, 'SUBMIT_PATIENT', message);
              return of(null);
            })
          );
        })
      )
    )
  }))
);

function mapPatientToWizardData(patient: any): Record<string, any> {
  const assureInfo = patient?.assureInfo ?? patient?.assure_info ?? {};
  const joursDialyse = patient?.joursDialyse ?? patient?.jours_dialyse ?? {};
  const centrePayeurId = normalizeId(patient?.centrePayeurId ?? patient?.centre_payeur_id);
  const medecinTraitantId = normalizeId(
    patient?.medecinTraitantId ?? patient?.medecin_traitant_id,
  );
  const salleId = normalizeId(patient?.salleId ?? patient?.salle_id);
  const positionId = normalizeId(patient?.positionId ?? patient?.position_id);
  const transporteurAllerId = normalizeId(
    patient?.transporteurAllerId ?? patient?.transporteur_aller_id,
  );
  const transporteurRetourId = normalizeId(
    patient?.transporteurRetourId ?? patient?.transporteur_retour_id,
  );
  const categorieTransportId = normalizeId(
    patient?.categorieTransportId ?? patient?.categorie_transport_id,
  );
  const generateurId = normalizeId(
    patient?.generateurId ?? patient?.generateur_id,
  );
  const generateurNom = patient?.generateurNom ?? patient?.generateur_nom ?? null;
  const generateurMarque = patient?.generateurMarque ?? patient?.generateur_marque ?? null;
  const generateurEtat = patient?.generateurEtat ?? patient?.generateur_etat ?? null;
  const wizardData = {
    ...patient,
    centrePayeurId,
    medecinTraitantId,
    salleId,
    positionId,
    transporteurAllerId,
    transporteurRetourId,
    categorieTransportId,
    generateurId,
    generateurNom,
    generateurMarque,
    generateurEtat,
    dateEvenementEtat: patient?.dateEvenementEtat ?? patient?.dateEvenement ?? null,
    qualiteAssure: patient?.qualiteAssure ?? patient?.qualite_assure ?? null,
    numeroAssurance: patient?.numeroAssurance?.value ?? patient?.numeroAssurance,
    assureNumeroAssurance:
      patient?.assureNumeroAssurance ?? patient?.assure_numero_assurance ?? null,
    assureNom:
      patient?.assureNom ?? patient?.assure_nom ?? assureInfo?.nom ?? assureInfo?.assureNom ?? null,
    assurePrenom:
      patient?.assurePrenom ?? patient?.assure_prenom ?? assureInfo?.prenom ?? assureInfo?.assurePrenom ?? null,
    assureSexe:
      patient?.assureSexe ?? patient?.assure_sexe ?? assureInfo?.sexe ?? assureInfo?.assureSexe ?? null,
    assureDateNaissance:
      patient?.assureDateNaissance ??
      patient?.assure_date_naissance ??
      assureInfo?.dateNaissance ??
      assureInfo?.assureDateNaissance ??
      null,
    assureTelPersonnel:
      patient?.assureTelPersonnel ??
      patient?.assure_tel_personnel ??
      assureInfo?.telPersonnel ??
      assureInfo?.assureTelPersonnel ??
      null,
    assureTelMobile:
      patient?.assureTelMobile ??
      patient?.assure_tel_mobile ??
      assureInfo?.telMobile ??
      assureInfo?.assureTelMobile ??
      null,
    assureTelBureau:
      patient?.assureTelBureau ??
      patient?.assure_tel_bureau ??
      assureInfo?.telBureau ??
      assureInfo?.assureTelBureau ??
      null,
    assureAdresse:
      patient?.assureAdresse ?? patient?.assure_adresse ?? assureInfo?.adresse ?? assureInfo?.assureAdresse ?? null,
    assureGroupeSanguin:
      patient?.assureGroupeSanguin ??
      patient?.assure_groupe_sanguin ??
      assureInfo?.groupeSanguin ??
      assureInfo?.assureGroupeSanguin ??
      null,
    jourDimanche: coerceBoolean(
      patient?.jourDimanche ??
      patient?.jour_dimanche ??
      joursDialyse?.jourDimanche ??
      joursDialyse?.jour_dimanche ??
      joursDialyse?.dimanche,
    ),
    jourLundi: coerceBoolean(
      patient?.jourLundi ??
      patient?.jour_lundi ??
      joursDialyse?.jourLundi ??
      joursDialyse?.jour_lundi ??
      joursDialyse?.lundi,
    ),
    jourMardi: coerceBoolean(
      patient?.jourMardi ??
      patient?.jour_mardi ??
      joursDialyse?.jourMardi ??
      joursDialyse?.jour_mardi ??
      joursDialyse?.mardi,
    ),
    jourMercredi: coerceBoolean(
      patient?.jourMercredi ??
      patient?.jour_mercredi ??
      joursDialyse?.jourMercredi ??
      joursDialyse?.jour_mercredi ??
      joursDialyse?.mercredi,
    ),
    jourJeudi: coerceBoolean(
      patient?.jourJeudi ??
      patient?.jour_jeudi ??
      joursDialyse?.jourJeudi ??
      joursDialyse?.jour_jeudi ??
      joursDialyse?.jeudi,
    ),
    jourVendredi: coerceBoolean(
      patient?.jourVendredi ??
      patient?.jour_vendredi ??
      joursDialyse?.jourVendredi ??
      joursDialyse?.jour_vendredi ??
      joursDialyse?.vendredi,
    ),
    jourSamedi: coerceBoolean(
      patient?.jourSamedi ??
      patient?.jour_samedi ??
      joursDialyse?.jourSamedi ??
      joursDialyse?.jour_samedi ??
      joursDialyse?.samedi,
    ),
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

function publishActionSuccess(
  store: any,
  action: string,
  options: { message?: string; meta?: Record<string, any> } = {}
): void {
  patchState(store, {
    lastAction: action,
    lastSuccess: true,
    lastError: null,
    lastMessage: options.message ?? null,
    lastActionMeta: options.meta ?? null,
    lastActionId: store.lastActionId() + 1
  });
}

function publishActionError(
  store: any,
  action: string,
  message: string,
  meta: Record<string, any> | null = null
): void {
  patchState(store, {
    lastAction: action,
    lastSuccess: false,
    lastError: message,
    lastMessage: null,
    lastActionMeta: meta,
    lastActionId: store.lastActionId() + 1
  });
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

function normalizeId(value: any): string | null {
  if (value === null || value === undefined || value === '') return null;
  if (typeof value === 'string' || typeof value === 'number') return String(value);
  if (typeof value === 'object') {
    const nested = value['value'] ?? value['id'] ?? value['ID'];
    if (nested !== undefined && nested !== null && nested !== '') return String(nested);
  }
  return String(value);
}

function coerceBoolean(value: any): boolean {
  if (value === true || value === false) return value;
  if (typeof value === 'string') {
    const normalized = value.trim().toLowerCase();
    if (['true', '1', 'yes', 'oui'].includes(normalized)) return true;
    if (['false', '0', 'no', 'non', ''].includes(normalized)) return false;
  }
  return !!value;
}

function pickLatestAttestation(history: any[]): any | null {
  if (!Array.isArray(history) || history.length === 0) return null;

  const toMs = (raw: any): number => {
    if (!raw) return Number.NEGATIVE_INFINITY;
    const d = raw instanceof Date ? raw : new Date(raw);
    const ms = d.getTime();
    return Number.isNaN(ms) ? Number.NEGATIVE_INFINITY : ms;
  };

  return [...history].sort((a, b) => {
    const aRef = toMs(a?.dateFin ?? a?.DATE_FIN ?? a?.dateDebut ?? a?.DATE_DEBUT);
    const bRef = toMs(b?.dateFin ?? b?.DATE_FIN ?? b?.dateDebut ?? b?.DATE_DEBUT);
    return bRef - aRef;
  })[0] ?? null;
}

