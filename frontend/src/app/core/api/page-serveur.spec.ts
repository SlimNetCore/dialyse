import {NgTableRemoteQuery} from '@sbourahla/ng-table';
import {parametresDePage} from './page-serveur';

describe('parametresDePage', () => {
  const requete = (partielle: Partial<NgTableRemoteQuery>): NgTableRemoteQuery => ({
    sort: {columnId: '', direction: ''}, filters: {}, page: {index: 0, size: 20}, ...partielle,
  });

  it('envoie page, taille, tri et filtres correspondants seulement', () => {
    expect(parametresDePage(requete({
      page: {index: 2, size: 50},
      sort: {columnId: 'date', direction: 'desc'},
      filters: {reference: ' BR-1 ', statut: 'VALIDE', inconnu: 'x', vide: ' '},
    }), {tris: {date: 'dateReception'}, filtres: {reference: 'reference', statut: 'statut', vide: 'vide'}}))
      .toEqual({page: '2', size: '50', sort: 'dateReception,desc', reference: 'BR-1', statut: 'VALIDE'});
  });

  it('borne la taille à 100 et ignore un tri sans direction ou non triable', () => {
    expect(parametresDePage(requete({page: {index: -1, size: 500}, sort: {columnId: 'lignes', direction: 'asc'}}), {}))
      .toEqual({page: '0', size: '100'});
  });

  it('envoie la recherche globale sous le nom demandé', () => {
    expect(parametresDePage(requete({search: ' acme '}), {recherche: 'q'})).toEqual({page: '0', size: '20', q: 'acme'});
  });
});
