import {NgTableRemoteQuery} from '@sbourahla/ng-table';
import {filtreNumerique, FiltreDePageInvalide, parametresDePage} from './page-serveur';

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

  it('filtre numérique : plages et différence envoyées telles quelles au serveur, l illisible refusé', () => {
    const corr = {filtres: {quantite: 'quantite'}, numeriques: ['quantite']};
    const avec = (v: string) => parametresDePage(requete({filters: {quantite: v}}), corr)['quantite'];
    expect(avec('<= 0')).toBe('<=0');
    expect(avec('12,5')).toBe('12,5');
    expect(avec('10..')).toBe('10..');
    expect(avec('..50')).toBe('..50');
    expect(avec('5..5')).toBe('5..5');
    expect(avec('10 .. 50')).toBe('10..50');
    expect(avec('!= 0')).toBe('!=0');
    expect(() => avec('50..10')).toThrow(FiltreDePageInvalide);
    expect(() => avec('abc')).toThrow(FiltreDePageInvalide);
  });

  it('filtreNumerique : null seulement pour ce que le serveur refuserait', () => {
    expect(filtreNumerique('>=100')).toBe('>=100');
    expect(filtreNumerique('-3.5')).toBe('-3.5');
    expect(filtreNumerique('!=0')).toBe('!=0');
    expect(filtreNumerique('1,5..2,5')).toBe('1,5..2,5');
    expect(filtreNumerique('-5..-1')).toBe('-5..-1');
    for (const illisible of ['..', '2..1', '1...5', '1..2..3', '!5', '>>5', '10-50', 'abc', '!=']) {
      expect(filtreNumerique(illisible), illisible).toBeNull();
    }
  });
});
