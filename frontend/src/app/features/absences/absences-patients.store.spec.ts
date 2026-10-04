import {provideZonelessChangeDetection} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {HttpErrorResponse} from '@angular/common/http';
import {of, throwError} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {AbsencePatient, AbsencePatientApiService} from '../../core/api/absence-patient-api.service';
import {AppShellStore} from '../../core/state/app-shell.store';
import {AbsencesPatientsStore, absencePatientErrorKey, EMPTY_FILTERS} from './absences-patients.store';

const CENTRE = '11111111-1111-1111-1111-111111111111';
const AUTRE_CENTRE = '22222222-2222-2222-2222-222222222222';

function absence(overrides: Partial<AbsencePatient> = {}): AbsencePatient {
  return {
    id: 'a1', patientId: 'p1', patientNom: 'Ali Amrani', dateSeance: '2026-09-28', source: 'AUTOMATIQUE',
    statut: 'A_QUALIFIER', motif: null, commentaire: null, forfaitLibelle: 'Forfait', prixTtc: 11900, tauxTva: 19,
    montantHt: 10000, declareeLe: null, qualifieeLe: null, dateRattrapage: null, enRetard: true, ...overrides,
  };
}

const erreurServeur = (code: string) => new HttpErrorResponse({status: 422, error: {code}});

describe('AbsencesPatientsStore', () => {
  let api: Record<string, ReturnType<typeof vi.fn>>;

  beforeEach(() => {
    api = {
      list: vi.fn().mockReturnValue(of({items: [absence()], total: 31, page: 1, size: 10})),
      synthese: vi.fn().mockReturnValue(of({aQualifier: 4, enRetard: 1})),
      declarer: vi.fn().mockReturnValue(of(absence())),
      qualifier: vi.fn().mockReturnValue(of(absence({statut: 'JUSTIFIEE'}))),
      rattraper: vi.fn().mockReturnValue(of(absence({statut: 'RATTRAPEE'}))),
      annuler: vi.fn().mockReturnValue(of(absence({statut: 'ANNULEE'}))),
    };
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), {provide: AbsencePatientApiService, useValue: api}],
    });
    TestBed.inject(AppShellStore).switchCenter(CENTRE);
  });

  it('charge une page paginée et la synthèse du centre actif', () => {
    const store = TestBed.inject(AbsencesPatientsStore);

    store.loadPage({page: 1, size: 10});

    expect(api['list']).toHaveBeenCalledWith(CENTRE, EMPTY_FILTERS, 1, 10);
    expect(api['synthese']).toHaveBeenCalledWith(CENTRE);
    expect(store.rows()).toHaveLength(1);
    expect(store.total()).toBe(31);
    expect(store.pageIndex()).toBe(1);
    expect(store.synthese()).toEqual({aQualifier: 4, enRetard: 1});
    expect(store.isEmpty()).toBe(false);
  });

  it('applique les filtres en revenant à la première page et ne transmet que le centre actif', () => {
    const store = TestBed.inject(AbsencesPatientsStore);
    const filtres = {statut: 'A_QUALIFIER' as const, motif: '' as const, from: '2026-09-01', to: ''};

    store.applyFilters(filtres);

    expect(api['list']).toHaveBeenCalledWith(CENTRE, filtres, 0, store.pageSize());
    TestBed.inject(AppShellStore).switchCenter(AUTRE_CENTRE);
    store.loadPage({page: 0, size: 10});
    expect(api['list']).toHaveBeenLastCalledWith(AUTRE_CENTRE, filtres, 0, 10);
  });

  it('recharge la page courante après chaque écriture', () => {
    const store = TestBed.inject(AbsencesPatientsStore);
    store.loadPage({page: 0, size: 10});

    store.declare({patientId: 'p1', dateSeance: '2026-09-28', motif: 'MALADIE', commentaire: null});
    expect(api['declarer']).toHaveBeenCalledWith(CENTRE, {
      patientId: 'p1', dateSeance: '2026-09-28', motif: 'MALADIE', commentaire: null,
    });
    expect(store.successMessage()).toBe('ABSENCES.SAVED_OK');

    store.qualify({id: 'a1', motif: 'VOYAGE', commentaire: 'x'});
    expect(api['qualifier']).toHaveBeenCalledWith(CENTRE, 'a1', 'VOYAGE', 'x');
    store.makeUp({id: 'a1', dateRattrapage: '2026-09-30'});
    expect(api['rattraper']).toHaveBeenCalledWith(CENTRE, 'a1', '2026-09-30');
    store.cancel({id: 'a1', commentaire: 'Erreur'});
    expect(api['annuler']).toHaveBeenCalledWith(CENTRE, 'a1', 'Erreur');
    expect(api['list'].mock.calls.length).toBe(5);
  });

  it('traduit les erreurs métier du serveur et libère le bouton', () => {
    api['declarer'].mockReturnValue(throwError(() => erreurServeur('ABSENCE_EXISTANTE')));
    const store = TestBed.inject(AbsencesPatientsStore);

    store.declare({patientId: 'p1', dateSeance: '2026-09-28', motif: null, commentaire: null});

    expect(store.error()).toBe('ABSENCES.ERR.ABSENCE_EXISTANTE');
    expect(store.saving()).toBe(false);
    expect(absencePatientErrorKey(erreurServeur('ABSENCE_CORRECTION_INTERDITE')))
      .toBe('ABSENCES.ERR.ABSENCE_CORRECTION_INTERDITE');
    expect(absencePatientErrorKey(erreurServeur('INCONNU'))).toBe('ABSENCES.ERR.SAVE');
    expect(absencePatientErrorKey(new Error('boom'))).toBe('ABSENCES.ERR.SAVE');
  });

  it('rattrape la détection sur une période du centre actif puis recharge la liste', () => {
    api['rattraperDetection'] = vi.fn().mockReturnValue(of({creees: 12, annulees: 2}));
    const store = TestBed.inject(AbsencesPatientsStore);

    store.catchUp({from: '2026-08-01', to: '2026-08-31'});

    expect(api['rattraperDetection']).toHaveBeenCalledWith(CENTRE, '2026-08-01', '2026-08-31');
    expect(store.rattrapage()).toEqual({creees: 12, annulees: 2});
    expect(store.saving()).toBe(false);
    expect(api['list']).toHaveBeenCalledWith(CENTRE, EMPTY_FILTERS, 0, 10);
  });

  it('traduit les erreurs de période du rattrapage', () => {
    api['rattraperDetection'] = vi.fn().mockReturnValue(throwError(() => erreurServeur('ABSENCE_PERIODE_TROP_LONGUE')));
    const store = TestBed.inject(AbsencesPatientsStore);

    store.catchUp({from: '2020-01-01', to: '2026-08-31'});

    expect(store.error()).toBe('ABSENCES.ERR.ABSENCE_PERIODE_TROP_LONGUE');
    expect(store.rattrapage()).toBeNull();
    expect(store.saving()).toBe(false);
  });

  it("signale l'échec du chargement et vide la liste", () => {
    api['list'].mockReturnValue(throwError(() => new Error('boom')));
    const store = TestBed.inject(AbsencesPatientsStore);

    store.loadPage({page: 0, size: 10});

    expect(store.error()).toBe('ABSENCES.ERR.LOAD');
    expect(store.rows()).toEqual([]);
    expect(store.loading()).toBe(false);
  });
});
