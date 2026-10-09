import {provideZonelessChangeDetection} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {HttpErrorResponse} from '@angular/common/http';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {of, throwError} from 'rxjs';
import {ComptabiliteApiService, CompteItem, PayeurCompteItem} from '../../../core/api/comptabilite-api.service';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {PlanComptableStore} from './plan-comptable.store';

const CENTRE = '11111111-1111-1111-1111-111111111111';
const AUTRE_CENTRE = '22222222-2222-2222-2222-222222222222';

const comptes: CompteItem[] = [
  {numero: '411500', libelle: 'Clients — organismes payeurs', actif: true},
  {numero: '512', libelle: 'Banque', actif: true},
];
const payeurs: PayeurCompteItem[] = [
  {payeurId: 'p1', code: 'CNAS-16', nom: 'CNAS Alger', compte: null},
  {payeurId: 'p2', code: 'MUT-1', nom: 'Mutuelle X', compte: '411500'},
];

describe('PlanComptableStore', () => {
  let api: Record<string, ReturnType<typeof vi.fn>>;

  beforeEach(() => {
    api = {
      getComptes: vi.fn().mockImplementation((_c: string, page: number, size: number) =>
        of({items: comptes, total: comptes.length, page, size})),
      saveCompte: vi.fn().mockImplementation((_c: string, compte: CompteItem) => of(compte)),
      deleteCompte: vi.fn().mockReturnValue(of(undefined)),
      getPayeurs: vi.fn().mockImplementation((_c: string, page: number, size: number) =>
        of({items: payeurs, total: payeurs.length, page, size})),
      saveComptePayeur: vi.fn().mockReturnValue(of(payeurs[0])),
    };
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), {provide: ComptabiliteApiService, useValue: api}],
    });
    TestBed.inject(AppShellStore).switchCenter(CENTRE);
  });

  it('charge la page du plan, les comptes actifs et les payeurs du centre actif', () => {
    const store = TestBed.inject(PlanComptableStore);

    store.load();

    expect(api['getComptes']).toHaveBeenCalledWith(CENTRE, 0, 10, '');
    expect(api['getComptes']).toHaveBeenCalledWith(CENTRE, 0, 500, '', true);
    expect(api['getPayeurs']).toHaveBeenCalledWith(CENTRE, 0, 10, '');
    expect(store.rows()).toHaveLength(2);
    expect(store.actifs()).toHaveLength(2);
    expect(store.libelles()).toMatchObject({'512': 'Banque'});
    expect(store.payeurs()).toHaveLength(2);
    expect(store.payeursTotal()).toBe(2);
    expect(store.loading()).toBe(false);
  });

  it('ne lit que le plan du centre actif', () => {
    const store = TestBed.inject(PlanComptableStore);
    TestBed.inject(AppShellStore).switchCenter(AUTRE_CENTRE);

    store.load();
    store.saveCompte({numero: '411210', libelle: 'Clients — CNAS', actif: true});

    expect(api['getComptes']).not.toHaveBeenCalledWith(CENTRE, expect.anything(), expect.anything(), expect.anything());
    expect(api['saveCompte']).toHaveBeenCalledWith(AUTRE_CENTRE, expect.objectContaining({numero: '411210'}));
  });

  it('recherche et pagine le plan en repartant de la première page', () => {
    const store = TestBed.inject(PlanComptableStore);

    store.setPagination({pageIndex: 3, pageSize: 20});
    expect(api['getComptes']).toHaveBeenCalledWith(CENTRE, 3, 20, '');

    store.setRecherche('banq');
    expect(store.pageIndex()).toBe(0);
    expect(api['getComptes']).toHaveBeenCalledWith(CENTRE, 0, 20, 'banq');
  });

  it('enregistre puis supprime un compte, recharge le plan et signale le succès', () => {
    const store = TestBed.inject(PlanComptableStore);

    store.saveCompte({numero: '411210', libelle: 'Clients — CNAS', actif: true});
    expect(api['saveCompte']).toHaveBeenCalledWith(CENTRE, {numero: '411210', libelle: 'Clients — CNAS', actif: true});
    expect(store.successMessage()).toBe('COMPTABILITE.PLAN.ENREGISTRE');

    store.deleteCompte('411210');
    expect(api['deleteCompte']).toHaveBeenCalledWith(CENTRE, '411210');
    expect(store.successMessage()).toBe('COMPTABILITE.PLAN.SUPPRIME');
    expect(store.saving()).toBe(false);
  });

  it('traduit le refus du serveur pour un compte utilisé', () => {
    api['deleteCompte'].mockReturnValue(
      throwError(() => new HttpErrorResponse({status: 422, error: {code: 'COMPTE_UTILISE'}})));
    const store = TestBed.inject(PlanComptableStore);

    store.deleteCompte('706');

    expect(store.error()).toBe('COMPTABILITE.PARAMETRAGE.ERR.COMPTE_UTILISE');
    expect(store.successMessage()).toBeNull();
  });

  it('affecte un compte client à un payeur puis recharge la page de payeurs', () => {
    const store = TestBed.inject(PlanComptableStore);

    store.saveComptePayeur({payeurId: 'p1', compte: '411500'});

    expect(api['saveComptePayeur']).toHaveBeenCalledWith(CENTRE, 'p1', '411500');
    expect(api['getPayeurs']).toHaveBeenCalledWith(CENTRE, 0, 10, '');
    expect(store.successMessage()).toBe('COMPTABILITE.PAYEURS.ENREGISTRE');
  });

  it('recherche et pagine les payeurs', () => {
    const store = TestBed.inject(PlanComptableStore);

    store.setPayeursPagination({pageIndex: 2, pageSize: 50});
    expect(api['getPayeurs']).toHaveBeenCalledWith(CENTRE, 2, 50, '');

    store.setPayeursRecherche('cnas');
    expect(store.payeursPageIndex()).toBe(0);
    expect(api['getPayeurs']).toHaveBeenCalledWith(CENTRE, 0, 50, 'cnas');
  });
});
