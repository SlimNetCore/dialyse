import {provideZonelessChangeDetection} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {HttpErrorResponse} from '@angular/common/http';
import {of, throwError} from 'rxjs';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {
  IndicateursOptimisation,
  PlanningOptimisationApiService,
  ResultatOptimisation,
  RunOptimisation,
} from '../../../core/api/planning-optimisation-api.service';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {OptimisationStore, optimisationErrorKey} from './optimisation.store';
import {INTERVALLE_SUIVI_MS} from './optimisation.util';

const CENTRE = '11111111-1111-1111-1111-111111111111';
const AUTRE_CENTRE = '22222222-2222-2222-2222-222222222222';

const INDICATEURS: IndicateursOptimisation = {
  generateursUtilises: 4, sallesOuvertes: 4, vacationsRequises: 4, placesInfirmierInutilisees: 0, patientsNonPlaces: 0,
  vacationsNonPourvues: 0, infirmiersMobilises: 2, ecartCharge: 0, depassementsHebdo: 0,
};

function resultat(): ResultatOptimisation {
  return {
    salles: [], creneaux: [], deplacements: [], nonPlaces: [], vacations: [], manques: [], avant: INDICATEURS,
    apres: INDICATEURS,
  };
}

function run(overrides: Partial<RunOptimisation> = {}): RunOptimisation {
  return {
    id: 'r1', centerId: CENTRE, statut: 'EN_COURS',
    parametres: {
      perimetre: 'PATIENTS', debutSemaine: '2026-09-27', nbSemaines: 1, dureeMaxSecondes: 20, stabilite: 5,
      objectif: 'EQUITE', maxVacationsParJour: 2, maxVacationsParSemaine: 6,
    },
    creeLe: '2026-10-01T08:00:00Z', termineLe: null, lancePar: 'u', phase: null, score: null, resume: null,
    resultat: null, erreur: null, appliqueLe: null, ...overrides,
  };
}

function httpError(status: number, code?: string): HttpErrorResponse {
  return new HttpErrorResponse({status, error: code ? {code} : null});
}

describe('OptimisationStore', () => {
  let api: Record<'lancer' | 'historique' | 'consulter' | 'arreter' | 'appliquer', ReturnType<typeof vi.fn>>;

  beforeEach(() => {
    vi.useFakeTimers();
    api = {
      lancer: vi.fn().mockReturnValue(of(run())),
      historique: vi.fn().mockReturnValue(of({items: [run()], total: 1, page: 0, size: 10})),
      consulter: vi.fn().mockReturnValue(of(run({statut: 'TERMINEE', resultat: resultat()}))),
      arreter: vi.fn().mockReturnValue(of(run())),
      appliquer: vi.fn().mockReturnValue(of(run({statut: 'TERMINEE', appliqueLe: '2026-10-01T09:00:00Z'}))),
    };
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), {provide: PlanningOptimisationApiService, useValue: api}],
    });
    TestBed.inject(AppShellStore).switchCenter(CENTRE);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('lance un calcul pour le centre actif puis suit son avancement jusqu\'à la fin', async () => {
    api.consulter
      .mockReturnValueOnce(of(run({phase: 'PATIENTS', score: '0hard/0medium/-100soft'})))
      .mockReturnValueOnce(of(run({statut: 'TERMINEE', resultat: resultat()})));
    const store = TestBed.inject(OptimisationStore);

    const accepte = await store.lancer({perimetre: 'PATIENTS'});

    expect(accepte).toBe(true);
    expect(api.lancer).toHaveBeenCalledWith(CENTRE, {perimetre: 'PATIENTS'});
    expect(store.enCours()).toBe(true);

    await vi.advanceTimersByTimeAsync(0);
    expect(store.courant()?.phase).toBe('PATIENTS');
    expect(store.enCours()).toBe(true);

    await vi.advanceTimersByTimeAsync(INTERVALLE_SUIVI_MS);
    expect(store.courant()?.statut).toBe('TERMINEE');
    expect(store.enCours()).toBe(false);
    expect(store.appliquable()).toBe(true);
    expect(api.consulter).toHaveBeenCalledWith(CENTRE, 'r1');

    // la lecture périodique s'arrête avec le calcul
    const appels = api.consulter.mock.calls.length;
    await vi.advanceTimersByTimeAsync(INTERVALLE_SUIVI_MS * 4);
    expect(api.consulter.mock.calls.length).toBe(appels);
  });

  it('recharge l\'historique à la fin du calcul', async () => {
    const store = TestBed.inject(OptimisationStore);
    await store.lancer({perimetre: 'ROULEMENT'});
    api.historique.mockClear();

    await vi.advanceTimersByTimeAsync(0);

    expect(api.historique).toHaveBeenCalledWith(CENTRE, 0, 10);
    expect(store.historique()).toHaveLength(1);
  });

  it('signale un lancement refusé par le serveur avec la clé de traduction du code métier', async () => {
    api.lancer.mockReturnValue(throwError(() => httpError(422, 'OPTIMISATION_DEJA_EN_COURS')));
    const store = TestBed.inject(OptimisationStore);

    expect(await store.lancer({perimetre: 'PATIENTS'})).toBe(false);

    expect(store.error()).toBe('PLANNING.OPTIM.ERR.OPTIMISATION_DEJA_EN_COURS');
    expect(store.launching()).toBe(false);
    expect(store.courant()).toBeNull();
  });

  it('arrête le calcul en cours sur le centre actif', async () => {
    const store = TestBed.inject(OptimisationStore);
    await store.lancer({perimetre: 'PATIENTS'});

    await store.arreter();

    expect(api.arreter).toHaveBeenCalledWith(CENTRE, 'r1');
  });

  it('n\'arrête rien quand aucun calcul n\'est en cours', async () => {
    const store = TestBed.inject(OptimisationStore);

    await store.arreter();

    expect(api.arreter).not.toHaveBeenCalled();
  });

  it('applique la proposition terminée puis recharge l\'historique', async () => {
    const store = TestBed.inject(OptimisationStore);
    await store.ouvrir('r1');
    api.historique.mockClear();

    const ok = await store.appliquer();

    expect(ok).toBe(true);
    expect(api.appliquer).toHaveBeenCalledWith(CENTRE, 'r1');
    expect(store.courant()?.appliqueLe).toBe('2026-10-01T09:00:00Z');
    expect(store.appliquable()).toBe(false);
    expect(store.successMessage()).toBe('PLANNING.OPTIM.OK.APPLIQUEE');
    expect(api.historique).toHaveBeenCalled();
  });

  it('refuse d\'appliquer une proposition déjà appliquée ou sans résultat', async () => {
    api.consulter.mockReturnValue(of(run({statut: 'TERMINEE', resultat: resultat(), appliqueLe: '2026-10-01T09:00:00Z'})));
    const store = TestBed.inject(OptimisationStore);
    await store.ouvrir('r1');

    expect(store.appliquable()).toBe(false);
    expect(await store.appliquer()).toBe(false);
    expect(api.appliquer).not.toHaveBeenCalled();
  });

  it('affiche l\'erreur d\'une proposition périmée sans la marquer comme appliquée', async () => {
    api.appliquer.mockReturnValue(throwError(() => httpError(422, 'OPTIMISATION_PERIMEE')));
    const store = TestBed.inject(OptimisationStore);
    await store.ouvrir('r1');

    expect(await store.appliquer()).toBe(false);

    expect(store.error()).toBe('PLANNING.OPTIM.ERR.OPTIMISATION_PERIMEE');
    expect(store.courant()?.appliqueLe).toBeNull();
    expect(store.applying()).toBe(false);
  });

  it('reprend le suivi d\'une exécution encore en cours ouverte depuis l\'historique', async () => {
    api.consulter
      .mockReturnValueOnce(of(run()))
      .mockReturnValueOnce(of(run()))
      .mockReturnValue(of(run({statut: 'TERMINEE', resultat: resultat()})));
    const store = TestBed.inject(OptimisationStore);

    await store.ouvrir('r1');
    expect(store.enCours()).toBe(true);
    await vi.advanceTimersByTimeAsync(INTERVALLE_SUIVI_MS);

    expect(store.courant()?.statut).toBe('TERMINEE');
  });

  it('pagine l\'historique avec le centre courant', () => {
    api.historique.mockReturnValue(of({items: [run()], total: 45, page: 2, size: 20}));
    const store = TestBed.inject(OptimisationStore);

    store.setPagination(2, 20);

    expect(api.historique).toHaveBeenCalledWith(CENTRE, 2, 20);
    expect(store.pageIndex()).toBe(2);
    expect(store.pageSize()).toBe(20);
    expect(store.total()).toBe(45);
  });

  it('ne mélange pas les centres : le changement de centre abandonne le calcul affiché et son suivi', async () => {
    const store = TestBed.inject(OptimisationStore);
    await store.lancer({perimetre: 'PATIENTS'});
    TestBed.inject(AppShellStore).switchCenter(AUTRE_CENTRE);

    store.reinitialiser();
    api.consulter.mockClear();
    await vi.advanceTimersByTimeAsync(INTERVALLE_SUIVI_MS * 3);

    expect(store.courant()).toBeNull();
    expect(store.historique()).toEqual([]);
    expect(api.consulter).not.toHaveBeenCalled();

    store.setPagination(0, 10);
    expect(api.historique).toHaveBeenLastCalledWith(AUTRE_CENTRE, 0, 10);
  });

  it('signale l\'échec du suivi sans planter', async () => {
    api.consulter.mockReturnValue(throwError(() => httpError(500)));
    const store = TestBed.inject(OptimisationStore);
    await store.lancer({perimetre: 'PATIENTS'});

    await vi.advanceTimersByTimeAsync(0);

    expect(store.error()).toBe('PLANNING.OPTIM.ERR.SUIVI');
  });
});

describe('optimisationErrorKey', () => {
  it('traduit les codes métier connus', () => {
    expect(optimisationErrorKey(httpError(422, 'OPTIMISATION_PERIMEE'))).toBe('PLANNING.OPTIM.ERR.OPTIMISATION_PERIMEE');
    expect(optimisationErrorKey(httpError(422, 'OPTIMISATION_DEJA_APPLIQUEE')))
      .toBe('PLANNING.OPTIM.ERR.OPTIMISATION_DEJA_APPLIQUEE');
  });

  it('regroupe les refus de placement par règle sous un seul message', () => {
    expect(optimisationErrorKey(httpError(422, 'OPTIMISATION_PLACEMENT_GENERATEUR_OCCUPE')))
      .toBe('PLANNING.OPTIM.ERR.PLACEMENT');
  });

  it('distingue des paramètres refusés (400) d\'une erreur inconnue', () => {
    expect(optimisationErrorKey(httpError(400))).toBe('PLANNING.OPTIM.ERR.PARAMETRES');
    expect(optimisationErrorKey(httpError(500, 'INCONNU'))).toBe('PLANNING.OPTIM.ERR.GENERIC');
    expect(optimisationErrorKey(new Error('x'))).toBe('PLANNING.OPTIM.ERR.GENERIC');
  });
});
