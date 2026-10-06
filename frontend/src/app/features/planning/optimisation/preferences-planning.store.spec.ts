import {provideZonelessChangeDetection} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {HttpErrorResponse} from '@angular/common/http';
import {of, throwError} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {
  LignePreferencePatient,
  LigneProfilInfirmier,
  PlanningPreferencesApiService,
  ReglagesOptimisation,
} from '../../../core/api/planning-preferences-api.service';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {PreferencesPlanningStore, preferencesErrorKey} from './preferences-planning.store';

const CENTRE = '11111111-1111-1111-1111-111111111111';
const AUTRE_CENTRE = '22222222-2222-2222-2222-222222222222';
const REGLAGES: ReglagesOptimisation = {
  replanificationAuto: false, heuresParVacation: 5, heuresHebdoTempsPlein: 40, reposHebdoMin: 1,
};

const patient = (id: string): LignePreferencePatient => ({
  patientId: id, nom: 'Patient ' + id, jours: ['LUNDI'], creneauActuelId: 'c1',
  preference: {patientId: id, creneauPrefereId: null, seancesParSemaine: null, joursAChoisir: false},
});
const infirmier = (id: string): LigneProfilInfirmier => ({
  infirmierId: id, nom: 'Inf ' + id, qualification: 'INFIRMIER',
  profil: {infirmierId: id, tauxActivite: 100, competences: []},
});

function httpError(status: number, code?: string): HttpErrorResponse {
  return new HttpErrorResponse({status, error: code ? {code} : null});
}

describe('PreferencesPlanningStore', () => {
  let api: Record<'patients' | 'infirmiers' | 'reglages' | 'enregistrerPreference' | 'enregistrerProfil'
    | 'enregistrerReglages', ReturnType<typeof vi.fn>>;

  beforeEach(() => {
    api = {
      patients: vi.fn().mockReturnValue(of({items: [patient('p1')], total: 31, page: 0, size: 10})),
      infirmiers: vi.fn().mockReturnValue(of({items: [infirmier('i1')], total: 1, page: 0, size: 10})),
      reglages: vi.fn().mockReturnValue(of(REGLAGES)),
      enregistrerPreference: vi.fn().mockReturnValue(of({})),
      enregistrerProfil: vi.fn().mockReturnValue(of({})),
      enregistrerReglages: vi.fn().mockImplementation((_c: string, r: ReglagesOptimisation) => of(r)),
    };
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), {provide: PlanningPreferencesApiService, useValue: api}],
    });
    TestBed.inject(AppShellStore).switchCenter(CENTRE);
  });

  async function charge() {
    const store = TestBed.inject(PreferencesPlanningStore);
    store.charger();
    await vi.waitFor(() => expect(store.reglages()).not.toBeNull());
    return store;
  }

  it('charge les deux listes paginées et les réglages du centre actif', async () => {
    const store = await charge();

    expect(api.patients).toHaveBeenCalledWith(CENTRE, 0, 10);
    expect(api.infirmiers).toHaveBeenCalledWith(CENTRE, 0, 10);
    expect(api.reglages).toHaveBeenCalledWith(CENTRE);
    expect(store.patients().rows).toHaveLength(1);
    expect(store.patients().total).toBe(31);
    expect(store.infirmiers().rows[0].nom).toBe('Inf i1');
  });

  it('pagine côté serveur', async () => {
    const store = await charge();

    await store.setPatientsPagination(2, 20);
    await store.setInfirmiersPagination(1, 50);

    expect(api.patients).toHaveBeenLastCalledWith(CENTRE, 2, 20);
    expect(api.infirmiers).toHaveBeenLastCalledWith(CENTRE, 1, 50);
    expect(store.patients().pageIndex).toBe(2);
    expect(store.patients().pageSize).toBe(20);
  });

  it('enregistre une préférence puis recharge la page courante', async () => {
    const store = await charge();

    const ok = await store.enregistrerPreference('p1', {creneauPrefereId: 'c2', joursAChoisir: true, seancesParSemaine: 3});

    expect(ok).toBe(true);
    expect(api.enregistrerPreference).toHaveBeenCalledWith(CENTRE, 'p1',
      {creneauPrefereId: 'c2', joursAChoisir: true, seancesParSemaine: 3});
    expect(api.patients).toHaveBeenCalledTimes(2);
    expect(store.successMessage()).toBe('PLANNING.OPTIM.PREFS.OK');
  });

  it('traduit le refus du serveur et ne recharge pas', async () => {
    const store = await charge();
    api.enregistrerProfil.mockReturnValue(throwError(() => httpError(422, 'PROFIL_INFIRMIER_INTROUVABLE')));

    const ok = await store.enregistrerProfil('ix', {tauxActivite: 50, competences: ['PEDIATRIE']});

    expect(ok).toBe(false);
    expect(store.error()).toBe('PLANNING.OPTIM.PREFS.ERR.PROFIL_INFIRMIER_INTROUVABLE');
    expect(api.infirmiers).toHaveBeenCalledTimes(1);
  });

  it('enregistre les réglages et garde ceux renvoyés par le serveur', async () => {
    const store = await charge();

    await store.enregistrerReglages({...REGLAGES, replanificationAuto: true});

    expect(store.reglages()?.replanificationAuto).toBe(true);
  });

  it('interroge le nouveau centre après un changement de centre', async () => {
    const store = await charge();
    TestBed.inject(AppShellStore).switchCenter(AUTRE_CENTRE);
    store.reinitialiser();

    expect(store.patients().rows).toEqual([]);
    store.charger();
    expect(api.patients).toHaveBeenLastCalledWith(AUTRE_CENTRE, 0, 10);
  });

  it('associe chaque refus à un message', () => {
    expect(preferencesErrorKey(httpError(422, 'PREFERENCE_CRENEAU_INCONNU')))
      .toBe('PLANNING.OPTIM.PREFS.ERR.PREFERENCE_CRENEAU_INCONNU');
    expect(preferencesErrorKey(httpError(400))).toBe('PLANNING.OPTIM.PREFS.ERR.VALEURS');
    expect(preferencesErrorKey(httpError(403))).toBe('PLANNING.OPTIM.PREFS.ERR.DROITS');
    expect(preferencesErrorKey(new Error('x'))).toBe('PLANNING.OPTIM.PREFS.ERR.GENERIC');
  });
});
