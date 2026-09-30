import {describe, expect, it} from 'vitest';
import {POIDS_SEC_MAX_KG, POIDS_SEC_MIN_KG, poidsSecValidator} from './prescriptions-list.component';

describe('poidsSecValidator', () => {
  const validate = poidsSecValidator();

  it('accepte un poids sec absent (champ facultatif)', () => {
    expect(validate(null)).toBeNull();
    expect(validate(undefined)).toBeNull();
    expect(validate('')).toBeNull();
  });

  it('accepte les valeurs dans les bornes cliniques', () => {
    expect(validate(POIDS_SEC_MIN_KG)).toBeNull();
    expect(validate(68.5)).toBeNull();
    expect(validate(POIDS_SEC_MAX_KG)).toBeNull();
  });

  it('refuse les valeurs hors bornes ou non numériques', () => {
    for (const invalid of [19.99, 300.01, 0, -5, 'abc']) {
      expect(validate(invalid)).toBe('DOSSIER_MEDICAL.POIDS_SEC_RANGE_ERROR');
    }
  });
});

