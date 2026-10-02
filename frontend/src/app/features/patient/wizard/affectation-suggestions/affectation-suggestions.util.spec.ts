import {describe, expect, it} from 'vitest';
import {CaseGrille} from '../../../../core/api/planning-api.service';
import {etatCase, joursCoches, joursVersFlags, trouverCase} from './affectation-suggestions.util';

const caseGrille = (capacite: number, occupes: number): CaseGrille =>
  ({salleId: 's', creneauId: 'c', jour: 'LUNDI', capacite, occupes});

describe('affectation-suggestions.util', () => {
  it('convertit les jours cochés en jours imposés, dans l\'ordre de la semaine', () => {
    expect(joursCoches({jourVendredi: true, jourLundi: true, jourMercredi: true}))
      .toEqual(['LUNDI', 'MERCREDI', 'VENDREDI']);
    expect(joursCoches({})).toEqual([]);
  });

  it('convertit les jours d\'une proposition en cases de la fiche patient', () => {
    expect(joursVersFlags(['MARDI', 'JEUDI', 'SAMEDI'])).toEqual({
      jourDimanche: false, jourLundi: false, jourMardi: true, jourMercredi: false,
      jourJeudi: true, jourVendredi: false, jourSamedi: true,
    });
  });

  it('est réversible entre jours et cases', () => {
    const jours = ['DIMANCHE', 'MARDI', 'JEUDI'] as const;
    expect(joursCoches(joursVersFlags(jours))).toEqual([...jours]);
  });

  it('qualifie l\'état d\'une case de la grille', () => {
    expect(etatCase(caseGrille(0, 0))).toBe('none');
    expect(etatCase(caseGrille(3, 3))).toBe('full');
    expect(etatCase(caseGrille(3, 1))).toBe('partial');
    expect(etatCase(caseGrille(3, 0))).toBe('free');
    expect(etatCase(caseGrille(2, 5))).toBe('full');
  });

  it('retrouve la case d\'une salle, d\'un créneau et d\'un jour', () => {
    const grille = [caseGrille(2, 1)];
    expect(trouverCase(grille, 's', 'c', 'LUNDI')?.capacite).toBe(2);
    expect(trouverCase(grille, 's', 'c', 'MARDI')).toBeUndefined();
  });
});
