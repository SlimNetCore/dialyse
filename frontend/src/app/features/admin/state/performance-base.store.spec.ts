import {TestBed} from '@angular/core/testing';
import {of, throwError} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {
  RequeteBase, StatutSupervision, SupervisionApiService,
} from '../../../core/api/supervision-api.service';
import {PerformanceBaseStore} from './performance-base.store';

describe('PerformanceBaseStore', () => {
  let api: Record<'statut' | 'requetes' | 'reinitialiser' | 'analyse' | 'sante', ReturnType<typeof vi.fn>>;
  let store: InstanceType<typeof PerformanceBaseStore>;

  const disponible: StatutSupervision = {
    disponible: true, raison: null, reinitialiseLe: '2026-10-01T00:00:00Z', tempsTotalMs: 1000,
  };
  const requete: RequeteBase = {
    id: '1', requete: 'SELECT * FROM seances WHERE center_id = $1', appels: 42, tempsTotalMs: 600, tempsMoyenMs: 14.3,
    tempsMaxMs: 80, lignes: 900, partTempsTotalPct: 60, niveau: 'ATTENTION', conseils: [],
  };

  beforeEach(() => {
    api = {statut: vi.fn(), requetes: vi.fn(), reinitialiser: vi.fn(), analyse: vi.fn(), sante: vi.fn()};
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({providers: [{provide: SupervisionApiService, useValue: api}]});
    store = TestBed.inject(PerformanceBaseStore);
  });

  it('charge le statut puis le classement demandé', async () => {
    api.statut.mockReturnValue(of(disponible));
    api.requetes.mockReturnValue(of({items: [requete], total: 1, page: 0, size: 20}));

    await store.load();

    expect(api.requetes).toHaveBeenCalledWith('TEMPS_TOTAL', 0, 20);
    expect(store.rows()).toEqual([requete]);
    expect(store.total()).toBe(1);
    expect(store.statut()?.disponible).toBe(true);
    expect(store.loading()).toBe(false);
  });

  it('ne demande pas le classement quand la mesure est indisponible, et garde la raison', async () => {
    api.statut.mockReturnValue(of({
      disponible: false,
      raison: 'BASE_NON_POSTGRESQL',
      reinitialiseLe: null,
      tempsTotalMs: 0
    }));

    await store.load();

    expect(api.requetes).not.toHaveBeenCalled();
    expect(store.rows()).toEqual([]);
    expect(store.statut()?.raison).toBe('BASE_NON_POSTGRESQL');
  });

  it('change de classement en revenant à la première page', async () => {
    api.statut.mockReturnValue(of(disponible));
    api.requetes.mockReturnValue(of({items: [], total: 0, page: 0, size: 20}));
    store.setPagination(3, 50);

    store.setTri('TEMPS_MOYEN');
    await store.load();

    expect(store.pageIndex()).toBe(0);
    expect(api.requetes).toHaveBeenCalledWith('TEMPS_MOYEN', 0, 50);
  });

  it('signale une erreur de lecture et vide la liste', async () => {
    api.statut.mockReturnValue(throwError(() => new Error('500')));

    await store.load();

    expect(store.error()).toBe(true);
    expect(store.rows()).toEqual([]);
  });

  it('remet les compteurs à zéro puis recharge', async () => {
    api.statut.mockReturnValue(of(disponible));
    api.requetes.mockReturnValue(of({items: [], total: 0, page: 0, size: 20}));
    api.reinitialiser.mockReturnValue(of(undefined));

    const ok = await store.reinitialiser();

    expect(ok).toBe(true);
    expect(api.reinitialiser).toHaveBeenCalled();
    expect(api.requetes).toHaveBeenCalled();
    expect(store.resetError()).toBe(false);
  });

  it('signale le refus de PostgreSQL lors de la remise à zéro', async () => {
    api.reinitialiser.mockReturnValue(throwError(() => new Error('422')));

    const ok = await store.reinitialiser();

    expect(ok).toBe(false);
    expect(store.resetError()).toBe(true);
    expect(api.statut).not.toHaveBeenCalled();
  });

  it('établit le plan d\'une requête par son identifiant puis le libère', async () => {
    const plan = {
      disponible: true, raison: null, requete: 'SELECT 1', planTexte: 'Seq Scan', coutTotal: 5, indexUtilises: 0,
      balayagesComplets: [],
    };
    api.analyse.mockReturnValue(of(plan));

    await store.analyser('42');

    expect(api.analyse).toHaveBeenCalledWith('42');
    expect(store.analyse()).toEqual(plan);
    expect(store.analyseLoading()).toBe(false);

    store.fermerAnalyse();
    expect(store.analyse()).toBeNull();
  });

  it('signale l\'échec de l\'analyse sans garder de plan', async () => {
    api.analyse.mockReturnValue(throwError(() => new Error('422')));

    await store.analyser('42');

    expect(store.analyseError()).toBe(true);
    expect(store.analyse()).toBeNull();
    expect(store.analyseLoading()).toBe(false);
  });

  it('charge la santé de la base, ou signale l\'erreur', async () => {
    const sante = {
      disponible: true,
      raison: null,
      tailleOctets: 1,
      cachePct: 99,
      connexions: 1,
      connexionsMax: 100,
      statsReset: null,
      tables: [],
      indexInutilises: [],
      alertes: [],
    };
    api.sante.mockReturnValue(of(sante));
    await store.chargerSante();
    expect(store.sante()).toEqual(sante);
    expect(store.santeError()).toBe(false);

    api.sante.mockReturnValue(throwError(() => new Error('500')));
    await store.chargerSante();
    expect(store.sante()).toBeNull();
    expect(store.santeError()).toBe(true);
  });
});
