import {describe, expect, it} from 'vitest';
import {CompteItem, ModelePieceItem} from '../../core/api/comptabilite-api.service';
import {
  compteDuPlan,
  compteValide,
  erreursModele,
  filtrerComptes,
  modeleDepuis,
  modeleVide,
  montantSaisi,
  totauxPiece,
} from './plan-comptable.util';

const plan: CompteItem[] = [
  {numero: '401', libelle: 'Fournisseurs', actif: true},
  {numero: '4456', libelle: 'TVA déductible', actif: true},
  {numero: '512', libelle: 'Banque', actif: true},
  {numero: '613', libelle: 'Locations', actif: true},
];

const loyer: ModelePieceItem = {
  id: 'm1', code: 'LOYER', libelle: 'Loyer du centre', journal: 'AC', actif: true,
  lignes: [
    {sens: 'DEBIT', compte: '613', libelle: 'Loyer'},
    {sens: 'DEBIT', compte: '4456', libelle: null},
    {sens: 'CREDIT', compte: '401'},
  ],
};

describe('plan comptable et pièces (règles de saisie)', () => {
  it('retrouve un compte du plan par son numéro exact', () => {
    expect(compteDuPlan(plan, ' 512 ')?.libelle).toBe('Banque');
    expect(compteDuPlan(plan, '51')).toBeUndefined();
    expect(compteDuPlan(plan, '')).toBeUndefined();
  });

  it('propose d\'abord les comptes dont le numéro commence par la frappe, puis ceux dont le libellé la contient', () => {
    expect(filtrerComptes(plan, '4').map((c) => c.numero)).toEqual(['401', '4456']);
    expect(filtrerComptes(plan, 'tva').map((c) => c.numero)).toEqual(['4456']);
    expect(filtrerComptes(plan, '').map((c) => c.numero)).toEqual(['401', '4456', '512', '613']);
    expect(filtrerComptes(plan, 'zzz')).toEqual([]);
  });

  it('contrôle le numéro et le libellé d\'un compte', () => {
    expect(compteValide(' 411210 ', 'Clients — CNAS')).toBe(true);
    expect(compteValide('41-1', 'x')).toBe(false);
    expect(compteValide('411', '  ')).toBe(false);
    expect(compteValide('1'.repeat(21), 'x')).toBe(false);
  });

  it('accepte un modèle complet et le recharge à l\'identique dans le formulaire', () => {
    const formulaire = modeleDepuis(loyer);

    expect(formulaire.lignes[1].libelle).toBe('');
    expect(erreursModele(formulaire, plan, ['AC', 'BQ'])).toEqual([]);
  });

  it('explique ce qui empêche d\'enregistrer un modèle', () => {
    expect(erreursModele(modeleVide(), plan, ['AC']).sort()).toEqual(['CODE', 'COMPTES', 'JOURNAL', 'LIBELLE']);
    const sansCredit = {...modeleDepuis(loyer), lignes: loyer.lignes.slice(0, 2).map((l) => ({...l}))};
    expect(erreursModele(sansCredit, plan, ['AC'])).toEqual(['SENS']);
    expect(erreursModele({...modeleDepuis(loyer), code: 'a b'}, plan, ['AC'])).toEqual(['CODE']);
    expect(erreursModele(modeleDepuis(loyer), plan, ['BQ'])).toEqual(['JOURNAL']);
    expect(erreursModele(modeleDepuis(loyer), plan.slice(1), ['AC'])).toEqual(['COMPTES']);
    expect(erreursModele({...modeleDepuis(loyer), lignes: [loyer.lignes[0]]}, plan, ['AC']).sort())
      .toEqual(['LIGNES', 'SENS']);
  });

  it('lit un montant saisi avec virgule ou point, et refuse ce qui n\'est pas un nombre positif', () => {
    expect(montantSaisi(' 1 190,50 ')).toBe(1190.5);
    expect(montantSaisi('100.005')).toBe(100.01);
    expect(montantSaisi('')).toBe(0);
    expect(montantSaisi('-5')).toBeNull();
    expect(montantSaisi('abc')).toBeNull();
  });

  it('n\'autorise la pièce qu\'équilibrée, avec un débit et un crédit', () => {
    expect(totauxPiece(loyer.lignes, ['100000', '19000', '119000']))
      .toEqual({debit: 119000, credit: 119000, equilibree: true, valide: true});
    expect(totauxPiece(loyer.lignes, ['100', '', '100']).valide).toBe(true);
    expect(totauxPiece(loyer.lignes, ['100', '', '90'])).toMatchObject({equilibree: false, valide: false});
    expect(totauxPiece(loyer.lignes, ['', '', ''])).toMatchObject({equilibree: true, valide: false});
    expect(totauxPiece(loyer.lignes, ['abc', '', '0']).valide).toBe(false);
    expect(totauxPiece(loyer.lignes, ['0,1', '0,2', '0,3']).valide).toBe(true);
  });
});
