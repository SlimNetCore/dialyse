import {provideZonelessChangeDetection} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {HttpErrorResponse} from '@angular/common/http';
import {of, throwError} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {AbsencePatientApiService} from '../../core/api/absence-patient-api.service';
import {
  AbsenceInfirmier,
  CompteInfirmier,
  Infirmier,
  InfirmierApiService,
  MonPlanning,
  SemainePresence,
} from '../../core/api/infirmier-api.service';
import {AppShellStore} from '../../core/state/app-shell.store';
import {absenceErrorKey, AbsencesStore} from './absences.store';
import {infirmierErrorKey, InfirmiersStore} from './infirmiers.store';
import {MonPlanningStore, monPlanningErrorKey} from './mon-planning.store';
import {presenceErrorKey, PresenceStore} from './presence.store';

const CENTRE = '11111111-1111-1111-1111-111111111111';

function infirmier(overrides: Partial<Infirmier> = {}): Infirmier {
  return {
    id: 'i1', matricule: 'M1', nom: 'Amrani', prenom: 'Sara', telephone: null, qualification: 'INFIRMIER',
    habiliteIsolement: false, actif: true, affectations: [], compte: null, ...overrides,
  };
}

function absence(): AbsenceInfirmier {
  return {id: 'a1', infirmierId: 'i1', debut: '2026-10-05', fin: '2026-10-06', type: 'CONGE', motif: null};
}

function semaine(debut: string): SemainePresence {
  return {
    debut, fin: debut, jours: [], salles: [], creneaux: [], cases: [], conflits: [], patientsParInfirmier: 4,
    casesSousEffectif: 0,
  };
}

function compte(): CompteInfirmier {
  return {id: 'u1', username: 'sara', nomComplet: 'Sara Amrani', actif: true};
}

function planning(debut: string): MonPlanning {
  return {
    infirmier: infirmier(),
    debut,
    fin: debut,
    salles: [],
    creneaux: [],
    mesCreneaux: [],
    jours: [],
    mesCases: []
  };
}

const erreurServeur = (code: string) => new HttpErrorResponse({status: 422, error: {code}});

describe('stores infirmiers', () => {
  let api: Record<string, ReturnType<typeof vi.fn>>;
  let absenceApi: { semaine: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    api = {
      list: vi.fn().mockReturnValue(of({items: [infirmier()], total: 1, page: 0, size: 10})),
      create: vi.fn().mockReturnValue(of(infirmier())),
      update: vi.fn().mockReturnValue(of(infirmier())),
      setActif: vi.fn().mockReturnValue(of(infirmier({actif: false}))),
      addAffectation: vi.fn().mockReturnValue(of({id: 'af1'})),
      deleteAffectation: vi.fn().mockReturnValue(of(undefined)),
      listAbsences: vi.fn().mockReturnValue(of({items: [absence()], total: 1, page: 0, size: 10})),
      createAbsence: vi.fn().mockReturnValue(of(absence())),
      deleteAbsence: vi.fn().mockReturnValue(of(undefined)),
      semaine: vi.fn().mockReturnValue(of(semaine('2026-09-27'))),
      alertes: vi.fn().mockReturnValue(of([])),
      remplacants: vi.fn().mockReturnValue(of([{infirmierId: 'i2', nom: 'Benali', score: 80}])),
      affecterRemplacement: vi.fn().mockReturnValue(of({})),
      annulerRemplacement: vi.fn().mockReturnValue(of(undefined)),
      charge: vi.fn().mockReturnValue(of({items: [], total: 0, page: 0, size: 20, moyenne: 3.5})),
      comptesLiables: vi.fn().mockReturnValue(of({items: [compte()], total: 1, page: 0, size: 100})),
      lierCompte: vi.fn().mockReturnValue(of(infirmier({compte: compte()}))),
      creerCompte: vi.fn().mockReturnValue(of({
        infirmier: infirmier({compte: compte()}),
        motDePasseTemporaire: 'Tmp-1'
      })),
      delierCompte: vi.fn().mockReturnValue(of(infirmier())),
      monPlanning: vi.fn().mockReturnValue(of(planning('2026-09-27'))),
      mesAbsences: vi.fn().mockReturnValue(of({items: [absence()], total: 1, page: 0, size: 10})),
      declarerMonAbsence: vi.fn().mockReturnValue(of(absence())),
      annulerMonAbsence: vi.fn().mockReturnValue(of(undefined)),
    };
    absenceApi = {
      semaine: vi.fn().mockReturnValue(of({
        absences: [{
          absenceId: 'ap1',
          patientId: 'p1',
          dateSeance: '2026-09-28',
          statut: 'JUSTIFIEE',
          motif: 'MALADIE'
        }],
        seancesRealisees: [{patientId: 'p2', dateSeance: '2026-09-28'}],
      })),
    };
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), {provide: InfirmierApiService, useValue: api},
        {provide: AbsencePatientApiService, useValue: absenceApi}],
    });
    TestBed.inject(AppShellStore).switchCenter(CENTRE);
  });

  it('InfirmiersStore charge une page du centre actif et recharge après chaque écriture', () => {
    const store = TestBed.inject(InfirmiersStore);

    store.loadPage({page: 0, size: 10});
    expect(api['list']).toHaveBeenCalledWith(CENTRE, 0, 10);
    expect(store.rows()).toHaveLength(1);
    expect(store.total()).toBe(1);

    const payload = {
      matricule: 'M1', nom: 'Amrani', prenom: null, telephone: null, qualification: 'INFIRMIER' as const,
      habiliteIsolement: false,
    };
    store.create(payload);
    expect(api['create']).toHaveBeenCalledWith(CENTRE, payload);
    expect(store.successMessage()).toBe('INFIRMIER.SAVED_OK');

    store.addAffectation({infirmierId: 'i1', payload: {salleId: 's', creneauId: 'c', jours: ['LUNDI']}});
    expect(api['addAffectation']).toHaveBeenCalledWith(CENTRE, 'i1', {salleId: 's', creneauId: 'c', jours: ['LUNDI']});
    store.deleteAffectation({infirmierId: 'i1', affectationId: 'af1'});
    expect(api['deleteAffectation']).toHaveBeenCalledWith(CENTRE, 'i1', 'af1');
    store.setActif({id: 'i1', actif: false});
    expect(api['setActif']).toHaveBeenCalledWith(CENTRE, 'i1', false);
    expect(api['list'].mock.calls.length).toBeGreaterThan(3);
  });

  it('InfirmiersStore retient les jours en sur-effectif d\'une affectation enregistrée, sans la refuser', () => {
    api['addAffectation'].mockReturnValue(of({id: 'af2', joursEnSureffectif: ['MERCREDI']}));
    const store = TestBed.inject(InfirmiersStore);

    store.addAffectation({infirmierId: 'i1', payload: {salleId: 's', creneauId: 'c', jours: ['LUNDI', 'MERCREDI']}});

    expect(store.successMessage()).toBe('INFIRMIER.SAVED_OK');
    expect(store.joursEnSureffectif()).toEqual(['MERCREDI']);

    store.deleteAffectation({infirmierId: 'i1', affectationId: 'af2'});
    expect(store.joursEnSureffectif()).toEqual([]);
  });

  it('InfirmiersStore ne signale aucun sur-effectif quand le serveur n\'en indique pas', () => {
    api['addAffectation'].mockReturnValue(of({id: 'af3'}));
    const store = TestBed.inject(InfirmiersStore);

    store.addAffectation({infirmierId: 'i1', payload: {salleId: 's', creneauId: 'c', jours: ['LUNDI']}});

    expect(store.joursEnSureffectif()).toEqual([]);
  });

  it('InfirmiersStore traduit les erreurs métier du serveur', () => {
    api['create'].mockReturnValue(throwError(() => erreurServeur('INFIRMIER_MATRICULE_EXISTANT')));
    const store = TestBed.inject(InfirmiersStore);

    store.create({
      matricule: 'M1', nom: 'A', prenom: null, telephone: null, qualification: 'MAJOR', habiliteIsolement: true,
    });

    expect(store.error()).toBe('INFIRMIER.ERR.INFIRMIER_MATRICULE_EXISTANT');
    expect(store.saving()).toBe(false);
    expect(infirmierErrorKey(erreurServeur('AFFECTATION_CHEVAUCHEMENT'))).toBe('INFIRMIER.ERR.AFFECTATION_CHEVAUCHEMENT');
    expect(infirmierErrorKey(new Error('boom'))).toBe('INFIRMIER.ERR.SAVE');
  });

  it('InfirmiersStore lie un compte, le délie et recharge les comptes disponibles', () => {
    const store = TestBed.inject(InfirmiersStore);

    store.loadComptesLiables();
    expect(api['comptesLiables']).toHaveBeenCalledWith(CENTRE, 0, 100);
    expect(store.comptesLiables()).toHaveLength(1);

    store.lierCompte({infirmierId: 'i1', userId: 'u1'});
    expect(api['lierCompte']).toHaveBeenCalledWith(CENTRE, 'i1', 'u1');
    expect(store.successMessage()).toBe('INFIRMIER.COMPTE.LIE_OK');
    expect(api['comptesLiables']).toHaveBeenCalledTimes(2);

    store.delierCompte('i1');
    expect(api['delierCompte']).toHaveBeenCalledWith(CENTRE, 'i1');
    expect(store.successMessage()).toBe('INFIRMIER.COMPTE.DELIE_OK');
  });

  it('InfirmiersStore garde le mot de passe temporaire d\'un compte créé jusqu\'à ce qu\'on l\'efface', () => {
    const store = TestBed.inject(InfirmiersStore);

    store.creerCompte({infirmierId: 'i1', payload: {identifiant: 'sara', email: null}});

    expect(api['creerCompte']).toHaveBeenCalledWith(CENTRE, 'i1', {identifiant: 'sara', email: null});
    expect(store.compteCree()).toEqual({infirmierId: 'i1', motDePasse: 'Tmp-1'});
    store.effacerMotDePasse();
    expect(store.compteCree()).toBeNull();
  });

  it('InfirmiersStore traduit les erreurs de compte du serveur', () => {
    api['creerCompte'].mockReturnValue(throwError(() => erreurServeur('COMPTE_IDENTIFIANT_EXISTANT')));
    const store = TestBed.inject(InfirmiersStore);

    store.creerCompte({infirmierId: 'i1', payload: {identifiant: 'sara', email: null}});

    expect(store.error()).toBe('INFIRMIER.ERR.COMPTE_IDENTIFIANT_EXISTANT');
    expect(store.compteCree()).toBeNull();
    expect(infirmierErrorKey(erreurServeur('COMPTE_DEJA_UTILISE'))).toBe('INFIRMIER.ERR.COMPTE_DEJA_UTILISE');
  });

  it('MonPlanningStore charge ma semaine, navigue et signale un compte non relié', () => {
    const store = TestBed.inject(MonPlanningStore);

    store.chargerPlanning(null);
    expect(api['monPlanning']).toHaveBeenCalledWith(CENTRE, undefined);
    expect(store.planning()?.debut).toBe('2026-09-27');
    store.changerSemaine(1);
    expect(api['monPlanning']).toHaveBeenLastCalledWith(CENTRE, '2026-10-04');

    api['monPlanning'].mockReturnValue(throwError(() => erreurServeur('INFIRMIER_NON_LIE')));
    store.chargerPlanning(null);
    expect(store.nonLie()).toBe(true);
    expect(store.planning()).toBeNull();
    expect(store.error()).toBeNull();
  });

  it('MonPlanningStore charge avec ma semaine les absences et séances validées des patients, du même centre', () => {
    const store = TestBed.inject(MonPlanningStore);

    store.chargerPlanning('2026-09-30');

    expect(absenceApi.semaine).toHaveBeenCalledWith(CENTRE, '2026-09-30');
    expect(store.absencesPatients().map((a) => a.patientId)).toEqual(['p1']);
    expect(store.seancesRealisees().map((s) => s.patientId)).toEqual(['p2']);
  });

  it('MonPlanningStore garde mon planning même si les absences des patients ne se chargent pas', () => {
    absenceApi.semaine.mockReturnValue(throwError(() => new Error('boom')));
    const store = TestBed.inject(MonPlanningStore);

    store.chargerPlanning(null);

    expect(store.planning()?.debut).toBe('2026-09-27');
    expect(store.absencesPatients()).toEqual([]);
    expect(store.error()).toBeNull();
  });

  it('MonPlanningStore signale une erreur technique de chargement distincte du compte non relié', () => {
    api['monPlanning'].mockReturnValue(throwError(() => new Error('boom')));
    const store = TestBed.inject(MonPlanningStore);

    store.chargerPlanning(null);

    expect(store.nonLie()).toBe(false);
    expect(store.error()).toBe('INFIRMIER.MOI.ERR.LOAD');
  });

  it('MonPlanningStore déclare et retire mes absences puis recharge planning et absences', () => {
    const store = TestBed.inject(MonPlanningStore);
    store.chargerPlanning(null);
    store.loadAbsences({page: 0, size: 10});
    const payload = {debut: '2026-10-05', fin: '2026-10-06', type: 'CONGE' as const, motif: null};

    store.declarer(payload);
    expect(api['declarerMonAbsence']).toHaveBeenCalledWith(CENTRE, payload);
    expect(store.successMessage()).toBe('INFIRMIER.MOI.ABSENCE_OK');
    expect(api['monPlanning'].mock.calls.length).toBe(2);
    expect(api['mesAbsences'].mock.calls.length).toBe(2);

    store.annuler('a1');
    expect(api['annulerMonAbsence']).toHaveBeenCalledWith(CENTRE, 'a1');
    expect(store.successMessage()).toBe('INFIRMIER.MOI.ANNULEE_OK');
  });

  it('MonPlanningStore traduit les refus du serveur sur mes absences', () => {
    api['annulerMonAbsence'].mockReturnValue(throwError(() => erreurServeur('ABSENCE_NON_ANNULABLE')));
    const store = TestBed.inject(MonPlanningStore);

    store.annuler('a1');

    expect(store.error()).toBe('INFIRMIER.ERR.ABSENCE_NON_ANNULABLE');
    expect(monPlanningErrorKey(erreurServeur('ABSENCE_PASSEE'))).toBe('INFIRMIER.ERR.ABSENCE_PASSEE');
    expect(monPlanningErrorKey(new Error('boom'))).toBe('INFIRMIER.ERR.SAVE');
  });

  it('AbsencesStore charge, crée et supprime pour le centre actif', () => {
    const store = TestBed.inject(AbsencesStore);

    store.loadPage({page: 0, size: 10});
    expect(api['listAbsences']).toHaveBeenCalledWith(CENTRE, 0, 10);
    expect(store.rows()).toHaveLength(1);

    const payload = {infirmierId: 'i1', debut: '2026-10-05', fin: '2026-10-06', type: 'CONGE' as const, motif: null};
    store.create(payload);
    expect(api['createAbsence']).toHaveBeenCalledWith(CENTRE, payload);
    expect(store.successMessage()).toBe('INFIRMIER.ABSENCES.SAVED_OK');
    store.remove('a1');
    expect(api['deleteAbsence']).toHaveBeenCalledWith(CENTRE, 'a1');
    expect(absenceErrorKey(erreurServeur('INFIRMIER_INTROUVABLE'))).toBe('INFIRMIER.ERR.INFIRMIER_INTROUVABLE');
    expect(absenceErrorKey(new Error('boom'))).toBe('INFIRMIER.ERR.SAVE');
  });

  it('PresenceStore charge la semaine, navigue et ouvre les remplaçants d\'une case', () => {
    const store = TestBed.inject(PresenceStore);

    store.chargerSemaine(null);
    expect(api['semaine']).toHaveBeenCalledWith(CENTRE, undefined);
    store.changerSemaine(1);
    expect(api['semaine']).toHaveBeenLastCalledWith(CENTRE, '2026-10-04');

    const sel = {date: '2026-09-28', jour: 'LUNDI' as const, salleId: 's', creneauId: 'c'};
    store.selectionnerCase(sel);
    expect(api['remplacants']).toHaveBeenCalledWith(CENTRE, '2026-09-28', 's', 'c');
    expect(store.candidats()).toHaveLength(1);

    store.selectionnerCase(null);
    expect(store.selection()).toBeNull();
    expect(store.candidats()).toHaveLength(0);
  });

  it('PresenceStore affecte un remplaçant sur la case ouverte puis rafraîchit semaine, alertes et candidats', () => {
    const store = TestBed.inject(PresenceStore);
    store.chargerSemaine(null);
    store.selectionnerCase({date: '2026-09-28', jour: 'LUNDI', salleId: 's', creneauId: 'c'});

    store.affecter({infirmierId: 'i2', remplaceId: 'i1'});

    expect(api['affecterRemplacement']).toHaveBeenCalledWith(CENTRE, {
      date: '2026-09-28', salleId: 's', creneauId: 'c', infirmierId: 'i2', remplaceId: 'i1',
    });
    expect(store.successMessage()).toBe('INFIRMIER.PRESENCE.SAVED_OK');
    expect(api['alertes']).toHaveBeenCalledWith(CENTRE, 14);
    expect(api['remplacants'].mock.calls.length).toBe(2);

    store.annuler('r1');
    expect(api['annulerRemplacement']).toHaveBeenCalledWith(CENTRE, 'r1');
  });

  it('PresenceStore n\'affecte rien sans case ouverte et traduit une erreur d\'éligibilité', () => {
    const store = TestBed.inject(PresenceStore);

    store.affecter({infirmierId: 'i2', remplaceId: null});
    expect(api['affecterRemplacement']).not.toHaveBeenCalled();
    expect(store.saving()).toBe(false);

    store.selectionnerCase({date: '2026-09-28', jour: 'LUNDI', salleId: 's', creneauId: 'c'});
    api['affecterRemplacement'].mockReturnValue(throwError(() => erreurServeur('REMPLACEMENT_INELIGIBLE')));
    store.affecter({infirmierId: 'i2', remplaceId: null});
    expect(store.error()).toBe('INFIRMIER.ERR.REMPLACEMENT_INELIGIBLE');
    expect(presenceErrorKey(new Error('boom'))).toBe('INFIRMIER.ERR.SAVE');
  });

  it('PresenceStore charge la charge mensuelle paginée du centre actif', () => {
    const store = TestBed.inject(PresenceStore);

    store.chargerCharge({mois: '2026-10', page: 1, size: 5});

    expect(api['charge']).toHaveBeenCalledWith(CENTRE, '2026-10', 1, 5);
    expect(store.chargeMoyenne()).toBe(3.5);
    expect(store.chargeMois()).toBe('2026-10');
  });
});
