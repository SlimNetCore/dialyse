import {provideZonelessChangeDetection} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {throwError, of} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {PlanningApiService, SemainePlanning} from '../../core/api/planning-api.service';
import {AbsencePatientApiService} from '../../core/api/absence-patient-api.service';
import {HttpErrorResponse} from '@angular/common/http';
import {InfirmierApiService} from '../../core/api/infirmier-api.service';
import {AppShellStore} from '../../core/state/app-shell.store';
import {PlanningStore} from './planning.store';

const CENTRE = '11111111-1111-1111-1111-111111111111';

function semaine(debut: string): SemainePlanning {
  return {debut, fin: debut, jours: [], salles: [], creneaux: [], cellules: [], conflits: [], patientsAReplanifier: 0};
}

const PRESENCE = {
  debut: '2026-09-27', fin: '2026-10-03', jours: [], salles: [], creneaux: [], cases: [], conflits: [],
  patientsParInfirmier: 4, casesSousEffectif: 0,
};

describe('PlanningStore', () => {
  let api: {
    semaine: ReturnType<typeof vi.fn>;
    parametres: ReturnType<typeof vi.fn>;
    enregistrerParametres: ReturnType<typeof vi.fn>;
  };

  let absenceApi: { semaine: ReturnType<typeof vi.fn>; declarer: ReturnType<typeof vi.fn> };
  let infirmierApi: { semaine: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    absenceApi = {
      semaine: vi.fn().mockReturnValue(of({
        absences: [{
          absenceId: 'a1',
          patientId: 'p1',
          dateSeance: '2026-09-28',
          statut: 'JUSTIFIEE',
          motif: 'MALADIE'
        }],
        seancesRealisees: [{patientId: 'p2', dateSeance: '2026-09-29'}],
      })),
      declarer: vi.fn().mockReturnValue(of({})),
    };
    api = {
      semaine: vi.fn().mockReturnValue(of(semaine('2026-09-27'))),
      parametres: vi.fn().mockReturnValue(of({
        joursOuverts: ['LUNDI'],
        sallesIsolement: [],
        patientsParInfirmier: 4,
        patientsParPosteEtSerie: 3
      })),
      enregistrerParametres: vi.fn().mockImplementation((_c: string, p: unknown) => of(p)),
    };
    infirmierApi = {semaine: vi.fn().mockReturnValue(of(PRESENCE))};
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), {provide: PlanningApiService, useValue: api},
        {provide: AbsencePatientApiService, useValue: absenceApi},
        {provide: InfirmierApiService, useValue: infirmierApi}],
    });
    TestBed.inject(AppShellStore).switchCenter(CENTRE);
  });

  it('charge la semaine du centre actif puis navigue de semaine en semaine', () => {
    const store = TestBed.inject(PlanningStore);

    store.chargerSemaine(null);
    expect(api.semaine).toHaveBeenCalledWith(CENTRE, undefined);
    expect(store.semaine()?.debut).toBe('2026-09-27');

    store.changerSemaine(1);
    expect(api.semaine).toHaveBeenLastCalledWith(CENTRE, '2026-10-04');
    store.changerSemaine(-1);
    expect(api.semaine).toHaveBeenLastCalledWith(CENTRE, '2026-09-20');
  });

  it('charge les infirmiers de la même semaine et du même centre', () => {
    const store = TestBed.inject(PlanningStore);

    store.chargerSemaine('2026-09-30');

    expect(infirmierApi.semaine).toHaveBeenCalledWith(CENTRE, '2026-09-30');
    expect(store.presence()?.patientsParInfirmier).toBe(4);
  });

  it('affiche le planning des patients même si les infirmiers ne se chargent pas', () => {
    infirmierApi.semaine.mockReturnValue(throwError(() => new Error('boom')));
    const store = TestBed.inject(PlanningStore);

    store.chargerSemaine(null);

    expect(store.semaine()?.debut).toBe('2026-09-27');
    expect(store.presence()).toBeNull();
    expect(store.error()).toBeNull();
  });

  it('signale une erreur de chargement de la semaine', () => {
    api.semaine.mockReturnValue(throwError(() => new Error('boom')));
    const store = TestBed.inject(PlanningStore);

    store.chargerSemaine(null);

    expect(store.semaine()).toBeNull();
    expect(store.error()).toBe('PLANNING.SEMAINE.ERR.LOAD');
    expect(store.loading()).toBe(false);
  });

  it('charge puis enregistre les paramètres pour le centre actif', () => {
    const store = TestBed.inject(PlanningStore);

    store.chargerParametres();
    expect(api.parametres).toHaveBeenCalledWith(CENTRE);
    expect(store.parametres()?.joursOuverts).toEqual(['LUNDI']);

    store.enregistrerParametres({
      joursOuverts: ['LUNDI', 'MARDI'], sallesIsolement: ['s1'], patientsParInfirmier: 3, patientsParPosteEtSerie: 4,
    });
    expect(api.enregistrerParametres).toHaveBeenCalledWith(CENTRE, {
      joursOuverts: ['LUNDI', 'MARDI'],
      sallesIsolement: ['s1'],
      patientsParInfirmier: 3,
      patientsParPosteEtSerie: 4,
    });
    expect(store.parametres()?.sallesIsolement).toEqual(['s1']);
    expect(store.successMessage()).toBe('PLANNING.PARAMS.SAVED_OK');
  });

  it('signale un échec d\'enregistrement des paramètres', () => {
    api.enregistrerParametres.mockReturnValue(throwError(() => new Error('boom')));
    const store = TestBed.inject(PlanningStore);

    store.enregistrerParametres({
      joursOuverts: ['LUNDI'], sallesIsolement: [], patientsParInfirmier: 4, patientsParPosteEtSerie: 3,
    });

    expect(store.saving()).toBe(false);
    expect(store.error()).toBe('PLANNING.PARAMS.ERR.SAVE');
  });

  it('charge les absences de la semaine avec le planning, sans bloquer le planning si elles échouent', () => {
    const store = TestBed.inject(PlanningStore);

    store.chargerSemaine(null);
    expect(absenceApi.semaine).toHaveBeenCalledWith(CENTRE, undefined);
    expect(store.absences()).toHaveLength(1);
    expect(store.seancesRealisees()).toEqual([{patientId: 'p2', dateSeance: '2026-09-29'}]);

    absenceApi.semaine.mockReturnValue(throwError(() => new Error('boom')));
    store.chargerSemaine('2026-10-04');
    expect(store.semaine()?.debut).toBe('2026-09-27');
    expect(store.absences()).toEqual([]);
    expect(store.seancesRealisees()).toEqual([]);
    expect(store.error()).toBeNull();
  });

  it('déclare une absence pour le centre actif puis recharge la semaine', async () => {
    const store = TestBed.inject(PlanningStore);
    store.chargerSemaine('2026-09-28');

    const ok = await store.declarerAbsence({
      patientId: 'p1', dateSeance: '2026-09-28', motif: 'MALADIE', commentaire: null,
    });

    expect(ok).toBe(true);
    expect(absenceApi.declarer).toHaveBeenCalledWith(CENTRE, {
      patientId: 'p1', dateSeance: '2026-09-28', motif: 'MALADIE', commentaire: null,
    });
    expect(api.semaine).toHaveBeenCalledTimes(2);
    expect(store.absenceSaving()).toBe(false);
    expect(store.absenceError()).toBeNull();
  });

  it('renvoie la clé du message quand la déclaration est refusée', async () => {
    absenceApi.declarer.mockReturnValue(throwError(() =>
      new HttpErrorResponse({status: 422, error: {code: 'ABSENCE_EXISTANTE'}})));
    const store = TestBed.inject(PlanningStore);

    const ok = await store.declarerAbsence({
      patientId: 'p1', dateSeance: '2026-09-28', motif: 'VOYAGE', commentaire: null,
    });

    expect(ok).toBe(false);
    expect(store.absenceError()).toBe('ABSENCES.ERR.ABSENCE_EXISTANTE');
    expect(store.absenceSaving()).toBe(false);
    store.clearAbsenceError();
    expect(store.absenceError()).toBeNull();
  });
});
