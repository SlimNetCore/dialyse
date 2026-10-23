import {describe, expect, it} from 'vitest';
import {
  anneesDesMois,
  douzeMois,
  indexerLesMois,
  moisClosable,
  moisCourant,
  motifDeReouvertureValide,
  moisCouverts
} from './facturation-periodes';

describe('moisClosable (ADR 0044, comme les absences)', () => {
  it('seul un mois passé se clôture : ni le mois en cours, ni un mois futur', () => {
    expect(moisClosable('2026-09', '2026-10')).toBe(true);
    expect(moisClosable('2025-12', '2026-01')).toBe(true);
    expect(moisClosable('2026-10', '2026-10')).toBe(false);
    expect(moisClosable('2027-03', '2026-10')).toBe(false);
  });

  it('mois courant du navigateur au format AAAA-MM', () => {
    expect(moisCourant(new Date(2026, 0, 31))).toBe('2026-01');
    expect(moisCourant(new Date(2026, 9, 1))).toBe('2026-10');
  });
});

describe('moisCouverts', () => {
  it('un seul mois pour un intervalle dans le même mois', () => {
    expect(moisCouverts('2026-08-01', '2026-08-31')).toEqual(['2026-08']);
  });

  it('tous les mois touchés, y compris à cheval sur deux années', () => {
    expect(moisCouverts('2026-11-15', '2027-02-03')).toEqual(['2026-11', '2026-12', '2027-01', '2027-02']);
  });

  it('vide si les dates sont invalides ou inversées', () => {
    expect(moisCouverts('', '2026-08-31')).toEqual([]);
    expect(moisCouverts('2026-09-01', '2026-08-31')).toEqual([]);
  });
});

describe('anneesDesMois, motif et douze mois', () => {
  it('années distinctes triées', () => {
    expect(anneesDesMois(['2027-01', '2026-12', '2026-11'])).toEqual([2026, 2027]);
  });

  it('motif nettoyé ; vide si blanc ou au-delà de 500 caractères', () => {
    expect(motifDeReouvertureValide('  oubli  ')).toBe('oubli');
    expect(motifDeReouvertureValide('   ')).toBe('');
    expect(motifDeReouvertureValide('x'.repeat(500))).toHaveLength(500);
    expect(motifDeReouvertureValide('x'.repeat(501))).toBe('');
  });

  it('douze mois, ouverts par défaut, avec l état connu', () => {
    const clos = {mois: '2026-03', statut: 'CLOTURE' as const, cloturePar: 'u', clotureLe: 't', rouvertPar: null, rouvertLe: null, motifDeReouverture: null};
    const mois = douzeMois(2026, indexerLesMois([clos]));
    expect(mois).toHaveLength(12);
    expect(mois[2].statut).toBe('CLOTURE');
    expect(mois[0]).toMatchObject({mois: '2026-01', statut: 'OUVERT'});
  });
});
