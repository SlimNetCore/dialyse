import {Component, Injector, runInInjectionContext} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {NgTableRemoteQuery} from '@sbourahla/ng-table';
import {Subject} from 'rxjs';
import {vi} from 'vitest';
import {environment} from '../../../environments/environment';
import {ListeDistante, memesParametres, StockPagesApiService} from './stock-pages-api.service';
import {PageServeur} from './page-serveur';

const BASE = `${environment.apiBaseUrl}/stock`;

describe('StockPagesApiService', () => {
  let api: StockPagesApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({providers: [provideHttpClient(), provideHttpClientTesting()]});
    api = TestBed.inject(StockPagesApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('E1 : GET /dashboard/stock-valorise/page avec centerId, page, taille, tri et filtres', () => {
    api.etatDuStock('c-1', {page: '1', size: '20', sort: 'valeur,desc', quantite: '<=0'}).subscribe();
    const req = http.expectOne((r) => r.url === `${BASE}/dashboard/stock-valorise/page`);
    expect(req.request.params.get('centerId')).toBe('c-1');
    expect(req.request.params.get('sort')).toBe('valeur,desc');
    expect(req.request.params.get('quantite')).toBe('<=0');
    req.flush({items: [], total: 0, page: 1, size: 20});
  });

  it('E2 et E3 : pièces puis lignes de la pièce', () => {
    api.piecesPourSeances('c-1', {page: '0', size: '20', statut: 'RAPPROCHEE'}).subscribe();
    const pieces = http.expectOne((r) => r.url === `${BASE}/sorties-pour-seances/page`);
    expect(pieces.request.params.get('statut')).toBe('RAPPROCHEE');
    pieces.flush({items: [], total: 0, page: 0, size: 20});

    api.lignesDeLaPiece('c-1', 'p-1', {page: '0', size: '20', article: 'dia'}).subscribe();
    const lignes = http.expectOne((r) => r.url === `${BASE}/sorties-pour-seances/p-1/lignes/page`);
    expect(lignes.request.params.get('article')).toBe('dia');
    expect(lignes.request.params.get('centerId')).toBe('c-1');
    lignes.flush({items: [], total: 0, page: 0, size: 20});
  });

  it('E5 : GET /dashboard/pmp-explain/{articleId}/page sans tri', () => {
    api.explicationDuPmp('c-1', 'a-1', 2, 50).subscribe();
    const req = http.expectOne((r) => r.url === `${BASE}/dashboard/pmp-explain/a-1/page`);
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('50');
    expect(req.request.params.has('sort')).toBe(false);
    req.flush({items: [], total: 0, page: 2, size: 50});
  });
});

@Component({template: ''})
class Hote {
}

describe('ListeDistante', () => {
  const requete = (index: number, size: number, extra: Partial<NgTableRemoteQuery> = {}): NgTableRemoteQuery =>
    ({sort: {columnId: '', direction: ''}, filters: {}, page: {index, size}, ...extra} as NgTableRemoteQuery);

  function creer() {
    const reponses: Subject<PageServeur<string>>[] = [];
    const charger = vi.fn((_p: Record<string, string>) => {
      const s = new Subject<PageServeur<string>>();
      reponses.push(s);
      return s.asObservable();
    });
    const fixture = TestBed.createComponent(Hote);
    const liste = runInInjectionContext(fixture.componentRef.injector as Injector, () =>
      new ListeDistante<string>({tris: {nom: 'nom'}, filtres: {nom: 'nom'}}, charger));
    return {liste, charger, reponses};
  }

  it('compare les paramètres sans tenir compte de l ordre des clés', () => {
    expect(memesParametres({a: '1', b: '2'}, {b: '2', a: '1'})).toBe(true);
    expect(memesParametres({a: '1'}, {a: '2'})).toBe(false);
  });

  it('charge la page demandée, annule la requête précédente et expose items et total', () => {
    const {liste, charger, reponses} = creer();
    liste.recharger();
    liste.surRequete(requete(2, 20));
    expect(charger).toHaveBeenLastCalledWith({page: '2', size: '20'});
    reponses[0].next({items: ['vieux'], total: 1, page: 0, size: 20});
    expect(liste.items()).toEqual([]);
    reponses[1].next({items: ['a', 'b'], total: 42, page: 2, size: 20});
    expect(liste.items()).toEqual(['a', 'b']);
    expect(liste.total()).toBe(42);
  });

  it('un nouveau filtre ou tri ramène en page 0 ; une requête identique ne recharge pas', () => {
    const {liste, charger} = creer();
    liste.surRequete(requete(3, 20));
    liste.surRequete(requete(3, 20, {filters: {nom: 'x'}, sort: {columnId: 'nom', direction: 'desc'}}));
    expect(charger).toHaveBeenLastCalledWith({page: '0', size: '20', nom: 'x', sort: 'nom,desc'});
    const appels = charger.mock.calls.length;
    liste.surRequete(requete(0, 20, {filters: {nom: 'x'}, sort: {columnId: 'nom', direction: 'desc'}}));
    expect(charger.mock.calls.length).toBe(appels);
  });

  it('recharger relit la page courante avec les mêmes filtres', () => {
    const {liste, charger} = creer();
    liste.surRequete(requete(1, 50, {filters: {nom: 'x'}}));
    liste.surRequete(requete(4, 50, {filters: {nom: 'x'}}));
    liste.recharger();
    expect(charger).toHaveBeenLastCalledWith({page: '4', size: '50', nom: 'x'});
  });
});
