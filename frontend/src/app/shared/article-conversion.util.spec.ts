import {describe, expect, it} from 'vitest';
import {quantiteStockPourDose} from './article-conversion.util';

const epo = {unite: 'seringue', dosageParUnite: 4000, uniteDosage: 'UI'};

describe('quantiteStockPourDose', () => {
  it('convertit une dose en quantité de stock selon le dosage par unité', () => {
    expect(quantiteStockPourDose(epo, 8000, 'UI')).toEqual({quantite: 2, erreur: null});
    expect(quantiteStockPourDose(epo, 3000, 'ui')).toEqual({quantite: 0.75, erreur: null});
  });

  it('refuse une dose exprimée dans une autre unité que celle de l\'article', () => {
    expect(quantiteStockPourDose(epo, 100, 'mg')).toEqual({quantite: null, erreur: 'UNITE_INCOMPATIBLE'});
  });

  it('sans dosage, accepte seulement l\'unité de stock (1:1) et signale le reste', () => {
    const sansDosage = {unite: 'ampoule'};
    expect(quantiteStockPourDose(sansDosage, 2, 'ampoule')).toEqual({quantite: 2, erreur: null});
    expect(quantiteStockPourDose(sansDosage, 100, 'mg')).toEqual({quantite: null, erreur: 'DOSAGE_NON_DEFINI'});
  });

  it('rejette une dose absente ou non positive et un article inconnu', () => {
    expect(quantiteStockPourDose(epo, null, 'UI').erreur).toBe('DOSE_INVALIDE');
    expect(quantiteStockPourDose(epo, 0, 'UI').erreur).toBe('DOSE_INVALIDE');
    expect(quantiteStockPourDose(null, 10, 'UI').erreur).toBe('DOSE_INVALIDE');
  });
});
