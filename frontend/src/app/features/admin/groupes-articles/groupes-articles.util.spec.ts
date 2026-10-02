import {describe, expect, it} from 'vitest';
import {ArticleStock} from '../../../core/api/stock-api.service';
import {filterArticles} from './groupes-articles.util';

const article = (code: string, libelle: string): ArticleStock => ({
  id: code, centerId: 'c', code, libelle, unite: 'U', stockQuantity: 0, seuilAlerte: 0, pmpCourant: 0,
  gereParLot: false, active: true, typeTraitementAnemie: null,
});

const articles = [article('DIA-02', 'Dialyseur haute perméabilité'), article('AIG-01', 'Aiguille fistule'),
  article('EPO-01', 'Érythropoïétine')];

describe('filterArticles', () => {
  it('trie par libellé quand la saisie est vide', () => {
    expect(filterArticles(articles, '  ').map((a) => a.code)).toEqual(['AIG-01', 'DIA-02', 'EPO-01']);
  });

  it('cherche dans le code et le libellé, sans casse ni accents', () => {
    expect(filterArticles(articles, 'permeabilite').map((a) => a.code)).toEqual(['DIA-02']);
    expect(filterArticles(articles, 'ERYTHRO').map((a) => a.code)).toEqual(['EPO-01']);
    expect(filterArticles(articles, 'aig-').map((a) => a.code)).toEqual(['AIG-01']);
    expect(filterArticles(articles, 'zzz')).toEqual([]);
  });
});
