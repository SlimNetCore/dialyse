import {describe, expect, it} from 'vitest';
import {
  anneesDesDates, douzeMois, indexerLesMois, moisClosable, moisCourant, moisDeLaDate, motifDeReouvertureValide,
} from './mois-d-absences.util';

describe('mois d\'absences : outils', () => {
  it('extrait le mois d\'une date ISO et le mois du jour', () => {
    expect(moisDeLaDate('2026-09-28')).toBe('2026-09');
    expect(moisCourant(new Date(2026, 9, 23))).toBe('2026-10');
  });

  it('seul un mois passé se clôture, jamais le mois en cours ni un mois futur', () => {
    expect(moisClosable('2026-09', '2026-10')).toBe(true);
    expect(moisClosable('2026-10', '2026-10')).toBe(false);
    expect(moisClosable('2026-11', '2026-10')).toBe(false);
    expect(moisClosable('2025-12', '2026-01')).toBe(true);
  });

  it('motif nettoyé ; vide si blanc ou au-delà de 500 caractères', () => {
    expect(motifDeReouvertureValide('  oubli  ')).toBe('oubli');
    expect(motifDeReouvertureValide('   ')).toBe('');
    expect(motifDeReouvertureValide('x'.repeat(500))).toHaveLength(500);
    expect(motifDeReouvertureValide('x'.repeat(501))).toBe('');
  });

  it('douze mois ouverts par défaut, avec l\'état connu', () => {
    const clos = {
      mois: '2026-03', statut: 'CLOTURE' as const, cloturePar: 'u', clotureLe: 't', rouvertPar: null, rouvertLe: null,
      motifDeReouverture: null
    };
    const mois = douzeMois(2026, indexerLesMois([clos]));
    expect(mois).toHaveLength(12);
    expect(mois[2].statut).toBe('CLOTURE');
    expect(mois[0]).toMatchObject({mois: '2026-01', statut: 'OUVERT'});
  });

  it('années distinctes triées des dates données', () => {
    expect(anneesDesDates(['2027-01-02', '2026-12-30', '2026-11-01'])).toEqual([2026, 2027]);
  });
});
