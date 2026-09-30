import {LigneInventaire} from '../../../core/api/inventaire-api.service';
import {ecart, etatPeremption, filtrerLignes, progression} from './inventaire.util';

function ligne(partial: Partial<LigneInventaire>): LigneInventaire {
  return {
    id: 'l', articleId: 'a', articleCode: 'DIA', articleLibelle: 'Dialyseur', unite: 'U', lotId: 'lot',
    numeroLot: 'L1', datePeremption: null, quantiteTheorique: 10, quantiteComptee: null, ecart: null, pmp: 2,
    valeurEcart: null, motifEcart: null, comptePar: null, compteLe: null, ajoutee: false, ...partial,
  };
}

describe('inventaire.util', () => {
  const lignes = [
    ligne({id: '1'}),
    ligne({id: '2', quantiteComptee: 10, ecart: 0}),
    ligne({id: '3', quantiteComptee: 8, ecart: -2, articleCode: 'AIG', articleLibelle: 'Aiguille', numeroLot: 'X9'}),
  ];

  it('filtre les lignes selon leur état de comptage', () => {
    expect(filtrerLignes(lignes, 'TOUTES', '').map((l) => l.id)).toEqual(['1', '2', '3']);
    expect(filtrerLignes(lignes, 'A_COMPTER', '').map((l) => l.id)).toEqual(['1']);
    expect(filtrerLignes(lignes, 'COMPTEES', '').map((l) => l.id)).toEqual(['2', '3']);
    expect(filtrerLignes(lignes, 'ECARTS', '').map((l) => l.id)).toEqual(['3']);
  });

  it('recherche par code, libellé ou numéro de lot sans tenir compte de la casse', () => {
    expect(filtrerLignes(lignes, 'TOUTES', 'aiguille').map((l) => l.id)).toEqual(['3']);
    expect(filtrerLignes(lignes, 'TOUTES', ' x9 ').map((l) => l.id)).toEqual(['3']);
    expect(filtrerLignes(lignes, 'A_COMPTER', 'aig')).toEqual([]);
  });

  it('calcule la progression en pourcentage entier', () => {
    expect(progression(null)).toBe(0);
    expect(progression({lignes: 0, comptees: 0})).toBe(0);
    expect(progression({lignes: 3, comptees: 2})).toBe(67);
  });

  it('signale les lots périmés ou proches de la péremption', () => {
    const today = new Date(2026, 8, 30);
    expect(etatPeremption(null, today)).toBeNull();
    expect(etatPeremption('2026-09-29', today)).toBe('EXPIRE');
    expect(etatPeremption('2026-12-01', today)).toBe('PROCHE');
    expect(etatPeremption('2027-06-01', today)).toBeNull();
  });

  it('calcule l\'écart de saisie arrondi à 3 décimales', () => {
    expect(ecart(ligne({quantiteTheorique: 10}), null)).toBeNull();
    expect(ecart(ligne({quantiteTheorique: 10}), 7.5)).toBe(-2.5);
    expect(ecart(ligne({quantiteTheorique: 0.1}), 0.3)).toBe(0.2);
  });
});

