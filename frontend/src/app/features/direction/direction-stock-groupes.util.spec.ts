import {describe, expect, it} from 'vitest';
import {GroupeStock, StockGroupesOverview, ValeursStockGroupe} from '../../core/api/direction-api.service';
import {stockGroupeRows} from './direction-stock-groupes.util';

const v = (fin: number): ValeursStockGroupe => ({
  valeurDebut: 0,
  entrees: fin,
  sorties: 0,
  autresVariations: 0,
  valeurFin: fin
});

const groupe: GroupeStock = {
  nom: 'Kit CNAS', nbCentres: 2, nbArticles: 5, total: v(300),
  centres: [
    {centerId: 'c1', centreNom: 'Annaba', nbArticles: 3, valeurs: v(100)},
    {centerId: 'c2', centreNom: 'Rouiba', nbArticles: 2, valeurs: v(200)},
  ],
};
const overview: StockGroupesOverview = {
  societeId: 's', from: '2026-01-01', to: '2026-12-31', generatedAt: '',
  groupes: [groupe, {
    nom: 'Kit CASNOS', nbCentres: 1, nbArticles: 1, total: v(50),
    centres: [{centerId: 'c2', centreNom: 'Rouiba', nbArticles: 1, valeurs: v(50)}]
  }],
};

describe('stockGroupeRows', () => {
  it('liste le total consolidé de chaque groupe puis le détail par centre', () => {
    const rows = stockGroupeRows(overview, null);

    expect(rows.map((r) => `${r.nom}:${r.centre ?? 'TOTAL'}`)).toEqual([
      'Kit CNAS:TOTAL', 'Kit CNAS:Annaba', 'Kit CNAS:Rouiba', 'Kit CASNOS:TOTAL', 'Kit CASNOS:Rouiba',
    ]);
    expect(rows[0].total).toBe(true);
    expect(rows[0].valeurs.valeurFin).toBe(300);
  });

  it('ne garde que les lignes du centre isolé, sans total', () => {
    const rows = stockGroupeRows(overview, 'c1');

    expect(rows).toHaveLength(1);
    expect(rows[0].centre).toBe('Annaba');
    expect(rows[0].total).toBe(false);
  });

  it('ne renvoie rien sans données', () => {
    expect(stockGroupeRows(null, null)).toEqual([]);
  });
});
