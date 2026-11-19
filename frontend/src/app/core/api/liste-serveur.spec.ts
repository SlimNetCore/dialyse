import {Component, Injector, runInInjectionContext} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {HttpErrorResponse} from '@angular/common/http';
import {NgTableRemoteQuery} from '@sbourahla/ng-table';
import {Subject} from 'rxjs';
import {vi} from 'vitest';
import {
  CODES_DE_PAGINATION,
  ERREUR_DE_LISTE,
  ERREUR_RESEAU_DE_LISTE,
  ListeServeur,
  memesCriteres,
  messageDErreurDePagination,
  pageAtteignable,
} from './liste-serveur';
import {FILTRE_NUMERIQUE_INVALIDE, PageServeur} from './page-serveur';

@Component({template: ''})
class Hote {
}

const requete = (index: number, size: number, extra: Partial<NgTableRemoteQuery> = {}): NgTableRemoteQuery =>
  ({sort: {columnId: '', direction: ''}, filters: {}, page: {index, size}, ...extra} as NgTableRemoteQuery);

describe('ListeServeur (brique commune des listes paginées côté serveur)', () => {
  function creer(nul = false) {
    const reponses: Subject<PageServeur<string>>[] = [];
    const charger = vi.fn((_p: Record<string, string>) => {
      if (nul) return null;
      const s = new Subject<PageServeur<string>>();
      reponses.push(s);
      return s.asObservable();
    });
    const fixture = TestBed.createComponent(Hote);
    const liste = runInInjectionContext(fixture.componentRef.injector as Injector, () =>
      new ListeServeur<string>(charger, {tris: {nom: 'nom'}, filtres: {nom: 'nom', qte: 'qte'}, numeriques: ['qte']}));
    return {liste, charger, reponses};
  }

  it('compare les critères sans tenir compte de l ordre des clés ni de la page', () => {
    expect(memesCriteres({a: '1', b: '2', page: '1'}, {b: '2', a: '1', page: '3'})).toBe(true);
    expect(memesCriteres({a: '1'}, {a: '2'})).toBe(false);
  });

  it('charge la page demandée, annule la requête précédente et expose lignes et total', () => {
    const {liste, charger, reponses} = creer();
    liste.recharger();
    liste.onRemoteQueryChange(requete(2, 20));
    expect(charger).toHaveBeenLastCalledWith({page: '2', size: '20'});
    reponses[0].next({items: ['vieux'], total: 1, page: 0, size: 20});
    expect(liste.lignes()).toEqual([]);
    reponses[1].next({items: ['a', 'b'], total: 42, page: 2, size: 20});
    expect(liste.lignes()).toEqual(['a', 'b']);
    expect(liste.total()).toBe(42);
    expect(liste.chargement()).toBe(false);
  });

  it('un nouveau filtre ou tri ramène en page 0 ; une requête identique ne recharge pas', () => {
    const {liste, charger} = creer();
    liste.onRemoteQueryChange(requete(3, 20));
    liste.onRemoteQueryChange(requete(3, 20, {filters: {nom: 'x'}, sort: {columnId: 'nom', direction: 'desc'}}));
    expect(charger).toHaveBeenLastCalledWith({page: '0', size: '20', nom: 'x', sort: 'nom,desc'});
    const appels = charger.mock.calls.length;
    liste.onRemoteQueryChange(requete(0, 20, {filters: {nom: 'x'}, sort: {columnId: 'nom', direction: 'desc'}}));
    expect(charger.mock.calls.length).toBe(appels);
  });

  it('recharger relit la page courante avec les mêmes filtres', () => {
    const {liste, charger} = creer();
    liste.onRemoteQueryChange(requete(1, 50, {filters: {nom: 'x'}}));
    liste.onRemoteQueryChange(requete(4, 50, {filters: {nom: 'x'}}));
    liste.recharger();
    expect(charger).toHaveBeenLastCalledWith({page: '4', size: '50', nom: 'x'});
  });

  it('une erreur 400 vide la page (pas de page périmée) et expose le detail du ProblemDetail', () => {
    const {liste, reponses} = creer();
    liste.recharger();
    reponses[0].next({items: ['a'], total: 1, page: 0, size: 20});
    liste.onRemoteQueryChange(requete(0, 20, {filters: {nom: 'y'}}));
    reponses[1].error(new HttpErrorResponse({
      status: 400, error: {code: 'FILTRE_INVALIDE', detail: 'Filtre numerique illisible « qte »'},
    }));
    expect(liste.lignes()).toEqual([]);
    expect(liste.total()).toBe(0);
    expect(liste.erreur()).toBe('COMMON.LISTE_SERVEUR.FILTRE_INVALIDE');
    expect(liste.enErreur()).toBe(true);
    liste.recharger();
    reponses[2].next({items: ['b'], total: 1, page: 0, size: 20});
    expect(liste.erreur()).toBeNull();
  });

  it('serveur injoignable ⇒ clé réseau ; autre erreur ⇒ clé générique', () => {
    const {liste, reponses} = creer();
    liste.recharger();
    reponses[0].error(new HttpErrorResponse({status: 0}));
    expect(liste.erreur()).toBe(ERREUR_RESEAU_DE_LISTE);
    liste.recharger();
    reponses[1].error(new HttpErrorResponse({status: 500, error: {detail: 'boom'}}));
    expect(liste.erreur()).toBe(ERREUR_DE_LISTE);
    for (const code of CODES_DE_PAGINATION) {
      expect(messageDErreurDePagination(new HttpErrorResponse({status: 400, error: {code}})))
        .toBe(`COMMON.LISTE_SERVEUR.${code}`);
    }
  });

  it('un filtre numérique illisible n est pas envoyé et s affiche en erreur ; plage et différence vont au serveur', () => {
    const {liste, charger} = creer();
    liste.onRemoteQueryChange(requete(0, 20, {filters: {qte: 'abc'}}));
    liste.onRemoteQueryChange(requete(0, 20, {filters: {qte: '50..10'}}));
    expect(charger).not.toHaveBeenCalled();
    expect(liste.erreur()).toBe(FILTRE_NUMERIQUE_INVALIDE);
    liste.onRemoteQueryChange(requete(0, 20, {filters: {qte: '10..50'}}));
    expect(charger).toHaveBeenLastCalledWith({page: '0', size: '20', qte: '10..50'});
    liste.onRemoteQueryChange(requete(0, 20, {filters: {qte: '!=0'}}));
    expect(charger).toHaveBeenLastCalledWith({page: '0', size: '20', qte: '!=0'});
  });

  it('après une écriture qui réduit la liste, une page devenue vide ramène à la dernière page', () => {
    const {liste, charger, reponses} = creer();
    liste.onRemoteQueryChange(requete(2, 10, {filters: {nom: 'x'}}));
    reponses[0].next({items: ['a'], total: 21, page: 2, size: 10});
    liste.recharger();
    reponses[1].next({items: [], total: 20, page: 2, size: 10});
    expect(charger).toHaveBeenLastCalledWith({page: '1', size: '10', nom: 'x'});
    expect(liste.pageIndex()).toBe(1);
  });

  it('reinitialiser oublie tri, filtres et page reçue ; un chargeur sans contexte vide la liste', () => {
    const {liste, charger, reponses} = creer();
    liste.onRemoteQueryChange(requete(1, 20, {filters: {nom: 'x'}}));
    reponses[0].next({items: ['a'], total: 30, page: 1, size: 20});
    liste.reinitialiser();
    expect(liste.lignes()).toEqual([]);
    expect(charger).toHaveBeenLastCalledWith({page: '0', size: '20'});

    const vide = creer(true);
    vide.liste.recharger();
    expect(vide.liste.lignes()).toEqual([]);
    expect(vide.liste.chargement()).toBe(false);
  });

  it('pageAtteignable : seulement pour une page vide au-delà de la fin', () => {
    expect(pageAtteignable(3, 10, 25, 0)).toBe(2);
    expect(pageAtteignable(0, 10, 25, 0)).toBeNull();
    expect(pageAtteignable(3, 10, 0, 0)).toBe(0);
    expect(pageAtteignable(1, 10, 25, 5)).toBeNull();
  });
});
