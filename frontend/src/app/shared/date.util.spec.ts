import {describe, expect, it} from 'vitest';
import {toLocalIsoDate} from './date.util';

describe('toLocalIsoDate', () => {
  it('conserve le jour saisi à minuit, quel que soit le décalage horaire', () => {
    // minuit local : toISOString() reculerait d'un jour dans les fuseaux en avance sur UTC
    expect(toLocalIsoDate(new Date(2026, 8, 15, 0, 0, 0))).toBe('2026-09-15');
    expect(toLocalIsoDate(new Date(2026, 0, 1, 0, 0, 0))).toBe('2026-01-01');
  });

  it('complète les mois et jours sur deux chiffres', () => {
    expect(toLocalIsoDate(new Date(2026, 2, 5, 12, 30))).toBe('2026-03-05');
  });

  it("garde le jour en fin de soirée (23 h 59) sans passer au lendemain", () => {
    expect(toLocalIsoDate(new Date(2026, 11, 31, 23, 59, 59))).toBe('2026-12-31');
  });
});
