import {TestBed} from '@angular/core/testing';
import {throwError} from 'rxjs';
import {describe, expect, it, vi} from 'vitest';
import {BackendApiService} from '../../../core/api/backend-api.service';
import {cleDeTraductionDeLErreurDeFiche, PatientWizardStore} from './patient-wizard.store';

/** Refus métier de la fiche (revue PC2/PC3) : clés de traduction et reprise en édition d'une fiche déjà enregistrée. */
describe('PatientWizardStore (refus métier de la fiche)', () => {
  function monter(erreur: unknown) {
    const createPatient = vi.fn(() => throwError(() => erreur));
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({providers: [{provide: BackendApiService, useValue: {createPatient}}]});
    return {store: TestBed.inject(PatientWizardStore), createPatient};
  }

  it('traduit les quatre codes métier en clés de traduction', () => {
    expect(cleDeTraductionDeLErreurDeFiche('PEC_IDENTIFIANT_DEJA_UTILISE')).toBe('PATIENT.ERREUR_API.PEC_IDENTIFIANT_DEJA_UTILISE');
    expect(cleDeTraductionDeLErreurDeFiche('PATIENT_ECRITURE_CONCURRENTE')).toBe('PATIENT.ERREUR_API.PATIENT_ECRITURE_CONCURRENTE');
    expect(cleDeTraductionDeLErreurDeFiche('ASSURE_D_UN_AUTRE_CENTRE')).toBe('PATIENT.ERREUR_API.ASSURE_D_UN_AUTRE_CENTRE');
    expect(cleDeTraductionDeLErreurDeFiche('PEC_INTROUVABLE')).toBe('PATIENT.ERREUR_API.PEC_INTROUVABLE');
    expect(cleDeTraductionDeLErreurDeFiche('AUTRE')).toBeNull();
    expect(cleDeTraductionDeLErreurDeFiche(undefined)).toBeNull();
  });

  it('bascule en édition de la fiche enregistrée quand seule la PEC est refusée (409), pour ne pas la recréer', () => {
    const {store} = monter({status: 409, error: {code: 'PEC_IDENTIFIANT_DEJA_UTILISE', id: 'fiche-1', detail: 'x'}});

    store.submitPatient({editingPatientId: null, payload: {} as never});

    expect(store.submitStatus()).toBe('error');
    expect(store.error()).toBe('PATIENT.ERREUR_API.PEC_IDENTIFIANT_DEJA_UTILISE');
    expect(store.editingPatientId()).toBe('fiche-1');
    expect(store.lastSavedPatientId()).toBe('fiche-1');
  });

  it('garde le mode création et la clé de traduction pour un assuré d\'un autre centre (422)', () => {
    const {store} = monter({status: 422, error: {code: 'ASSURE_D_UN_AUTRE_CENTRE', detail: 'x'}});

    store.submitPatient({editingPatientId: null, payload: {} as never});

    expect(store.error()).toBe('PATIENT.ERREUR_API.ASSURE_D_UN_AUTRE_CENTRE');
    expect(store.editingPatientId()).toBeNull();
  });
});
