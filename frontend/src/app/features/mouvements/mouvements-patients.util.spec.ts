import {describe, expect, it} from 'vitest';
import {joursDeMouvement} from './mouvements-patients.util';

describe('joursDeMouvement', () => {
  it('découpe les codes de jours séparés par des virgules', () => {
    expect(joursDeMouvement('LUNDI,MERCREDI, VENDREDI')).toEqual(['LUNDI', 'MERCREDI', 'VENDREDI']);
  });

  it('retourne une liste vide sans jours', () => {
    expect(joursDeMouvement(null)).toEqual([]);
    expect(joursDeMouvement(undefined)).toEqual([]);
    expect(joursDeMouvement('')).toEqual([]);
  });
});
