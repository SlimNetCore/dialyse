import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {HttpErrorResponse} from '@angular/common/http';
import {provideZonelessChangeDetection} from '@angular/core';
import {of, Subject, throwError} from 'rxjs';
import {AccueilStore} from './accueil.store';
import {BackendApiService} from '../../../core/api/backend-api.service';
import {InfirmierApiService} from '../../../core/api/infirmier-api.service';
import {StockApiService} from '../../../core/api/stock-api.service';
import {GmaoApiService} from '../../../core/api/gmao-api.service';

describe('AccueilStore', () => {
  let api: { getDashboardStats: ReturnType<typeof vi.fn> };
  let stock: { alertes: ReturnType<typeof vi.fn> };
  let gmao: { getStats: ReturnType<typeof vi.fn> };
  let store: InstanceType<typeof AccueilStore>;

  beforeEach(() => {
    api = {getDashboardStats: vi.fn()};
    stock = {alertes: vi.fn()};
    gmao = {getStats: vi.fn()};
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        AccueilStore,
        {provide: BackendApiService, useValue: api},
        {provide: InfirmierApiService, useValue: {}},
        {provide: StockApiService, useValue: stock},
        {provide: GmaoApiService, useValue: gmao},
      ],
    });
    store = TestBed.inject(AccueilStore);
  });

  it('ignore la réponse tardive du centre précédent : les chiffres du centre courant ne sont pas écrasés', () => {
    const reponses: Record<string, Subject<{ patientCount: number; pecExpiring: number }>> = {
      c1: new Subject(), c2: new Subject(),
    };
    api.getDashboardStats.mockImplementation((centerId: string) => reponses[centerId]);

    store.charger('c1', ['patients']);
    store.charger('c2', ['patients']);
    expect(reponses['c1'].observed).toBe(false);

    reponses['c2'].next({patientCount: 20, pecExpiring: 2});
    reponses['c1'].next({patientCount: 10, pecExpiring: 1});

    expect(store.valeurs().patients).toEqual([20, 2]);
    expect(store.chargement().patients).toBe(false);
  });

  it('garde l\'indicateur de chargement tant que la lecture du centre courant n\'a pas répondu', () => {
    const lente = new Subject<{ patientCount: number; pecExpiring: number }>();
    api.getDashboardStats.mockReturnValueOnce(of({patientCount: 10, pecExpiring: 1})).mockReturnValueOnce(lente);

    store.charger('c1', ['patients']);
    store.charger('c2', ['patients']);

    expect(store.chargement().patients).toBe(true);
    lente.next({patientCount: 20, pecExpiring: 2});
    expect(store.chargement().patients).toBe(false);
    expect(store.valeurs().patients).toEqual([20, 2]);
  });

  it('marque non accessible un module refusé par le serveur (403), pas un module en erreur', () => {
    stock.alertes.mockReturnValue(throwError(() => new HttpErrorResponse({status: 403})));
    gmao.getStats.mockReturnValue(throwError(() => new HttpErrorResponse({status: 500})));

    store.charger('c1', ['stock', 'gmao']);

    expect(store.nonAccessibles().stock).toBe(true);
    expect(store.nonAccessibles().gmao).toBe(false);
    expect(store.valeurs().gmao).toEqual([null, null]);
    expect(store.chargement().stock).toBe(false);
  });

  it('un rafraîchissement silencieux ne remontre pas l\'indicateur de chargement', () => {
    const lente = new Subject<{ patientCount: number; pecExpiring: number }>();
    api.getDashboardStats.mockReturnValueOnce(of({patientCount: 10, pecExpiring: 1})).mockReturnValueOnce(lente);

    store.charger('c1', ['patients']);
    store.charger('c1', ['patients'], new Date(), {silencieux: true});

    expect(store.chargement().patients).toBe(false);
    expect(store.valeurs().patients).toEqual([10, 1]);
    lente.next({patientCount: 11, pecExpiring: 1});
    expect(store.valeurs().patients).toEqual([11, 1]);
  });
});
