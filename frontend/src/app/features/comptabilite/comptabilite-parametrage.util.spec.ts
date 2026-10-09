import {describe, expect, it} from 'vitest';
import {MappingComptableItem} from '../../core/api/comptabilite-api.service';
import {
  comptesDe,
  comptesInvalides,
  journalValide,
  normaliserCodeJournal,
  operationsDuJournal,
  operationsSansJournal,
  periodeParDefaut,
  periodeValide,
} from './comptabilite-parametrage.util';

const mapping: MappingComptableItem = {
  centerId: 'c1', compteVentes: '706', compteClientPatient: '411100', compteClientDefaut: '411500',
  compteBanque: '512', compteCaisse: '530', compteTVACollectee: '', compteStock: '322', compteConsommation: '602',
  compteFacturesNonParvenues: '408', compteBoniInventaire: '757', compteMaliInventaire: '657',
  journaux: {
    VENTE: 'VE', REGLEMENT_BANQUE: 'BQ', REGLEMENT_CAISSE: 'CA', STOCK_RECEPTION: 'AC', STOCK_SORTIE: 'ST',
    STOCK_INVENTAIRE: 'ST',
  },
};

describe('paramétrage comptable (règles de saisie)', () => {
  /** Plan comptable du centre : tous les comptes du paramétrage, plus un compte désactivé (absent des actifs). */
  const plan = ['706', '411100', '411500', '512', '530', '44571', '322', '602', '408', '757', '657']
    .map((numero) => ({numero, libelle: `Compte ${numero}`, actif: true}));

  it('accepte le paramétrage du serveur : seule la TVA collectée peut rester vide', () => {
    expect(comptesInvalides(comptesDe(mapping), plan)).toEqual([]);
    expect(Object.keys(comptesDe(mapping))).toHaveLength(11);
  });

  it('signale un compte obligatoire vide et un compte absent du plan comptable', () => {
    const comptes = {...comptesDe(mapping), compteStock: ' ', compteBanque: '5129', compteTVACollectee: '4457'};

    expect(comptesInvalides(comptes, plan).sort()).toEqual(['compteBanque', 'compteStock', 'compteTVACollectee']);
  });

  it('normalise le code d\'un journal et contrôle code et libellé', () => {
    expect(normaliserCodeJournal(' od ')).toBe('OD');
    expect(journalValide(' od ', 'Opérations diverses')).toBe(true);
    expect(journalValide('O-D', 'Opérations diverses')).toBe(false);
    expect(journalValide('ABCDEFGHIJK', 'Trop long')).toBe(false);
    expect(journalValide('OD', '   ')).toBe(false);
    expect(journalValide('OD', 'x'.repeat(101))).toBe(false);
  });

  it('repère les opérations dont le journal n\'est plus actif', () => {
    const actifs = [{code: 'VE', libelle: 'Ventes', actif: true}, {code: 'BQ', libelle: 'Banque', actif: true}];

    expect(operationsSansJournal(mapping.journaux, actifs))
      .toEqual(['REGLEMENT_CAISSE', 'STOCK_RECEPTION', 'STOCK_SORTIE', 'STOCK_INVENTAIRE']);
  });

  it('liste les opérations qui utilisent un journal', () => {
    expect(operationsDuJournal(mapping.journaux, 'ST')).toEqual(['STOCK_SORTIE', 'STOCK_INVENTAIRE']);
    expect(operationsDuJournal(mapping.journaux, 'OD')).toEqual([]);
    expect(operationsDuJournal(null, 'ST')).toEqual([]);
  });

  it('propose le mois en cours et refuse une période inversée ou de plus d\'un an', () => {
    expect(periodeParDefaut(new Date(2026, 9, 9))).toEqual({from: '2026-10-01', to: '2026-10-09'});
    expect(periodeValide('2026-10-01', '2026-10-09')).toBe(true);
    expect(periodeValide('2026-10-09', '2026-10-01')).toBe(false);
    expect(periodeValide('2025-01-01', '2026-10-09')).toBe(false);
    expect(periodeValide('', '2026-10-09')).toBe(false);
  });
});
