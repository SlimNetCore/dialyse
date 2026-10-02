import {describe, expect, it} from 'vitest';
import {etatCapacite, nbEnService, tauxRemplissage} from './salles.util';

const salle = (capacite: number | null, nbGenerateurs: number) => ({capacite, nbGenerateurs});

describe('salles.util', () => {
  it('qualifie le remplissage d\'une salle', () => {
    expect(etatCapacite(salle(null, 5))).toBe('ILLIMITEE');
    expect(etatCapacite(salle(4, 2))).toBe('LIBRE');
    expect(etatCapacite(salle(4, 4))).toBe('PLEINE');
    expect(etatCapacite(salle(2, 3))).toBe('DEPASSEE');
  });

  it('calcule le taux de remplissage borné à 100 %', () => {
    expect(tauxRemplissage(salle(4, 1))).toBe(25);
    expect(tauxRemplissage(salle(4, 4))).toBe(100);
    expect(tauxRemplissage(salle(2, 3))).toBe(100);
    expect(tauxRemplissage(salle(null, 3))).toBe(0);
  });

  it('compte les générateurs en service', () => {
    const generateurs = [
      {id: '1', code: 'G1', designation: 'G1', statut: 'EN_SERVICE'},
      {id: '2', code: 'G2', designation: 'G2', statut: 'HORS_SERVICE'},
      {id: '3', code: 'G3', designation: 'G3', statut: 'EN_SERVICE'},
    ];
    expect(nbEnService({generateurs})).toBe(2);
  });
});
