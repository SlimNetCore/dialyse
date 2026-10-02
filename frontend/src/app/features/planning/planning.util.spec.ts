import {describe, expect, it} from 'vitest';
import {CellulePlanning, JourPlanning, SemainePlanning} from '../../core/api/planning-api.service';
import {cellule, decalerJours, etatCellule, jourFerme} from './planning.util';

const jour = (ouvert: boolean, motif: string | null): JourPlanning =>
  ({jour: 'LUNDI', date: '2026-09-28', ouvertHebdomadaire: ouvert, fermetureMotif: motif});

const cell = (capacite: number, occupants: number): CellulePlanning => ({
  salleId: 's', creneauId: 'c', jour: 'LUNDI', capacite,
  occupants: Array.from({length: occupants}, (_, i) => ({
    patientId: `p${i}`,
    nom: `P${i}`,
    generateurCode: null,
    aRisque: false
  })),
});

describe('planning.util', () => {
  it('décale une date de semaines entières, y compris à travers un changement d\'heure', () => {
    expect(decalerJours('2026-09-27', 7)).toBe('2026-10-04');
    expect(decalerJours('2026-03-29', -7)).toBe('2026-03-22');
    expect(decalerJours('2026-12-28', 7)).toBe('2027-01-04');
  });

  it('un jour est fermé s\'il n\'est pas ouvert chaque semaine ou s\'il a une fermeture datée', () => {
    expect(jourFerme(jour(true, null))).toBe(false);
    expect(jourFerme(jour(false, null))).toBe(true);
    expect(jourFerme(jour(true, 'Férié'))).toBe(true);
    expect(jourFerme(jour(true, ''))).toBe(true);
  });

  it('classe l\'état d\'une cellule', () => {
    expect(etatCellule(undefined)).toBe('closed');
    expect(etatCellule(cell(0, 0))).toBe('closed');
    expect(etatCellule(cell(2, 0))).toBe('empty');
    expect(etatCellule(cell(2, 1))).toBe('partial');
    expect(etatCellule(cell(2, 2))).toBe('full');
    expect(etatCellule(cell(1, 2))).toBe('over');
    expect(etatCellule(cell(0, 1))).toBe('over');
  });

  it('retrouve une cellule par salle, créneau et jour', () => {
    const semaine = {cellules: [cell(1, 0)]} as unknown as SemainePlanning;
    expect(cellule(semaine, 's', 'c', 'LUNDI')).toBeDefined();
    expect(cellule(semaine, 's', 'c', 'MARDI')).toBeUndefined();
    expect(cellule(semaine, 'autre', 'c', 'LUNDI')).toBeUndefined();
  });
});
