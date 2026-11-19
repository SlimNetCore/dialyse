import {TestBed} from '@angular/core/testing';
import {provideZonelessChangeDetection} from '@angular/core';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {of, throwError} from 'rxjs';
import {ReglementStore} from './reglement.store';
import {BackendApiService} from '../../../core/api/backend-api.service';
import {ReferentialApiService} from '../../../core/api/referential-api.service';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {AuthStore} from '../../../core/state/auth.store';

const CENTER_ID = '11111111-1111-1111-1111-111111111111';

describe('ReglementStore', () => {
  let apiMock: {
    listReglementsPage: ReturnType<typeof vi.fn>;
    getReglementDashboard: ReturnType<typeof vi.fn>;
    registerFacturePayment: ReturnType<typeof vi.fn>;
  };
  let refMock: {
    getCaisses: ReturnType<typeof vi.fn>;
    getAgences: ReturnType<typeof vi.fn>;
    getCentresPayeurs: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    apiMock = {
      listReglementsPage: vi.fn().mockReturnValue(of({
        items: [{
          factureId: 'fac-1',
          numeroFacture: 'FAC-2026-0001',
          numeroAssurance: 'ASS-1',
          patientNom: 'Dupont',
          patientPrenom: 'Jean',
          caisseId: 'c1',
          caisse: 'CNAS',
          agenceId: 'a1',
          agence: 'Agence A',
          centrePayeurId: 'cp1',
          centrePayeur: 'Centre Payeur A',
          dateFacturation: '2026-08-01',
          montantFacture: 1000,
          montantRegle: 0,
          reste: 1000,
          tropPercu: 0,
          etat: 'NON_REGLEE',
          soldeType: 'RESTE',
        }],
        total: 1,
        page: 0,
        size: 20,
      })),
      getReglementDashboard: vi.fn().mockReturnValue(of({
        centerId: CENTER_ID,
        year: 2026,
        month: 8,
        totalFactures: 1,
        nonReglees: 1,
        partiellementReglees: 0,
        reglees: 0,
        tropPercus: 0,
        totalFacture: 1000,
        totalRegle: 0,
        totalReste: 1000,
        totalTropPercu: 0,
        statusBreakdown: [],
      })),
      registerFacturePayment: vi.fn().mockReturnValue(of({
        factureId: 'fac-1',
        numeroFacture: 'FAC-2026-0001',
        numeroAssurance: 'ASS-1',
        patientNom: 'Dupont',
        patientPrenom: 'Jean',
        caisseId: 'c1',
        caisse: 'CNAS',
        agenceId: 'a1',
        agence: 'Agence A',
        centrePayeurId: 'cp1',
        centrePayeur: 'Centre Payeur A',
        dateFacturation: '2026-08-01',
        montantFacture: 1000,
        montantRegle: 1000,
        reste: 0,
        tropPercu: 0,
        etat: 'REGLEE',
        soldeType: 'REGLE',
      })),
    };
    refMock = {
      getCaisses: vi.fn().mockReturnValue(of([{id: 'c1', nom: 'CNAS'}])),
      getAgences: vi.fn().mockReturnValue(of([{id: 'a1', nom: 'Agence A'}])),
      getCentresPayeurs: vi.fn().mockReturnValue(of([{id: 'cp1', nom: 'Centre Payeur A'}])),
    };

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        ReglementStore,
        {provide: BackendApiService, useValue: apiMock},
        {provide: ReferentialApiService, useValue: refMock},
        {provide: AppShellStore, useValue: {currentCenterId: vi.fn(() => CENTER_ID)}},
        {provide: AuthStore, useValue: {username: vi.fn(() => 'secretaire')}}
      ]
    });
  });

  it('charge la liste paginée au démarrage avec le centerId courant', () => {
    const store = TestBed.inject(ReglementStore);
    store.loadPage({centerId: CENTER_ID, userId: 'secretaire', page: 0, size: 20});
    expect(apiMock.listReglementsPage).toHaveBeenCalled();
    expect(store.rows().length).toBe(1);
    expect(store.total()).toBe(1);
    expect(store.pageSize()).toBe(20);
  });

  it('envoie le tri et les filtres de colonne à GET /reglements/page', () => {
    const store = TestBed.inject(ReglementStore);
    store.setColonnes({sort: 'patientNom,desc', etat: 'REGLEE'});
    store.loadPage({centerId: CENTER_ID, userId: 'secretaire', page: 0, size: 20});
    const [centre, criteres, colonnes] = apiMock.listReglementsPage.mock.calls.at(-1)!;
    expect(centre).toBe(CENTER_ID);
    expect(criteres).toMatchObject({page: 0, size: 20});
    expect(colonnes).toEqual({sort: 'patientNom,desc', etat: 'REGLEE'});
  });

  it('des colonnes identiques ne réinitialisent pas la page ; des colonnes changées ramènent en page 0', () => {
    const store = TestBed.inject(ReglementStore);
    store.setColonnes({etat: 'REGLEE', sort: 'caisse,asc'});
    store.setPagination(3, 20);
    store.setColonnes({sort: 'caisse,asc', etat: 'REGLEE'});
    expect(store.pageIndex()).toBe(3);
    store.setColonnes({sort: 'caisse,desc', etat: 'REGLEE'});
    expect(store.pageIndex()).toBe(0);
    expect(store.colonnes()).toEqual({sort: 'caisse,desc', etat: 'REGLEE'});
  });

  it('recharge la page courante après un paiement enregistré, en gardant le message de succès', () => {
    apiMock.listReglementsPage.mockImplementation((_c: string, q: { page: number; size: number }) =>
      of({items: [], total: 60, page: q.page, size: q.size}));
    const store = TestBed.inject(ReglementStore);
    TestBed.tick();
    store.setPagination(2, 20);
    TestBed.tick();
    const appelsAvant = apiMock.listReglementsPage.mock.calls.length;
    store.savePayment({centerId: CENTER_ID, factureId: 'fac-1', montant: 1000});
    TestBed.tick();
    expect(apiMock.listReglementsPage.mock.calls.length).toBe(appelsAvant + 1);
    expect(apiMock.listReglementsPage.mock.calls.at(-1)![1]).toMatchObject({page: 2, size: 20});
    expect(store.successMessage()).toBe('REGLEMENT_MODULE.SUCCESS.PAYMENT_SAVED');
  });

  it('met à jour la pagination', () => {
    const store = TestBed.inject(ReglementStore);
    store.setPagination(2, 50);
    expect(store.pageIndex()).toBe(2);
    expect(store.pageSize()).toBe(50);
  });

  it('enregistre un paiement et met à jour la ligne', () => {
    const store = TestBed.inject(ReglementStore);
    store.loadPage({centerId: CENTER_ID, userId: 'secretaire', page: 0, size: 20});
    store.savePayment({centerId: CENTER_ID, factureId: 'fac-1', montant: 1000, userId: 'secretaire'});
    const [, payload] = apiMock.registerFacturePayment.mock.calls[0];
    expect(payload.centerId).toBe(CENTER_ID);
    expect(payload.montant).toBe(1000);
    expect(payload.userId).toBeUndefined();
    expect(payload.modeReglement).toBeUndefined();
    expect(payload.referencePaiement).toBeUndefined();
    expect(payload.cleIdempotence).toMatch(/^[0-9a-f-]{36}$/);
    expect(store.rows()[0].etat).toBe('REGLEE');
    expect(store.rows()[0].soldeType).toBe('REGLE');
    expect(store.idempotenceKeys()['fac-1']).toBeUndefined();
    expect(store.savingFactureIds()).toEqual([]);
    expect(store.successMessage()).toBe('REGLEMENT_MODULE.SUCCESS.PAYMENT_SAVED');
    // les indicateurs ne sont plus chargés par l'écran de saisie (tableau de bord dédié)
    expect(apiMock.getReglementDashboard).not.toHaveBeenCalled();
  });

  it('envoie le mode et la référence du lot, et les vide après succès', () => {
    const store = TestBed.inject(ReglementStore);
    store.loadPage({centerId: CENTER_ID, userId: 'secretaire', page: 0, size: 20});
    store.setModeReglement('CHEQUE');
    store.setReferencePaiement('  CHQ-123 ');
    store.setPaymentDraft('fac-1', '500');
    store.batchSavePayments({centerId: CENTER_ID});
    const [, payload] = apiMock.registerFacturePayment.mock.calls[0];
    expect(payload.modeReglement).toBe('CHEQUE');
    expect(payload.referencePaiement).toBe('CHQ-123');
    expect(store.modeReglement()).toBeNull();
    expect(store.referencePaiement()).toBe('');
    expect(store.hasDrafts()).toBe(false);
  });

  it('réutilise la clé d\'idempotence après un échec, la régénère après une nouvelle saisie', () => {
    const store = TestBed.inject(ReglementStore);
    store.loadPage({centerId: CENTER_ID, userId: 'secretaire', page: 0, size: 20});
    apiMock.registerFacturePayment.mockReturnValue(throwError(() => ({
      status: 422,
      error: {code: 'REMBOURSEMENT_SUPERIEUR_AU_TOTAL_REGLE', detail: 'x'}
    })));
    store.setPaymentDraft('fac-1', '-500');
    store.batchSavePayments({centerId: CENTER_ID});
    store.batchSavePayments({centerId: CENTER_ID});
    const first = apiMock.registerFacturePayment.mock.calls[0][1].cleIdempotence;
    expect(apiMock.registerFacturePayment.mock.calls[1][1].cleIdempotence).toBe(first);
    expect(store.error()).toBe('REGLEMENT_MODULE.ERROR.REMBOURSEMENT_SUPERIEUR_AU_TOTAL_REGLE');

    store.setPaymentDraft('fac-1', '-400');
    store.batchSavePayments({centerId: CENTER_ID});
    expect(apiMock.registerFacturePayment.mock.calls[2][1].cleIdempotence).not.toBe(first);
  });
});


