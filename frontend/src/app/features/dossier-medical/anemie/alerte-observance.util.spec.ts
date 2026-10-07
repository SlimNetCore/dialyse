import {describe, expect, it} from 'vitest';
import {alerteDetaillee, alerteEnDose, manqueAlerte} from './alerte-observance.util';

describe('alerte-observance.util', () => {
  it('explique par le détail une alerte qui porte sa prescription, pas une ancienne alerte', () => {
    expect(alerteDetaillee({frequenceValeur: 1, frequenceUnite: 'SEMAINE'})).toBe(true);
    expect(alerteDetaillee({frequenceValeur: null, frequenceUnite: null})).toBe(false);
    expect(alerteDetaillee({})).toBe(false);
    expect(alerteDetaillee({frequenceValeur: 1, frequenceUnite: null})).toBe(false);
  });

  it('distingue les quantités de dose des nombres d\'administrations', () => {
    expect(alerteEnDose({uniteDose: 'UI'})).toBe(true);
    expect(alerteEnDose({uniteDose: 'mg'})).toBe(true);
    expect(alerteEnDose({uniteDose: null})).toBe(false);
    expect(alerteEnDose({})).toBe(false);
  });

  it('calcule ce qui manque, jamais négatif', () => {
    expect(manqueAlerte({dosesAttendues: 8000, dosesAdministrees: 4000})).toBe(4000);
    expect(manqueAlerte({dosesAttendues: 3, dosesAdministrees: 1})).toBe(2);
    expect(manqueAlerte({dosesAttendues: 1, dosesAdministrees: 2})).toBe(0);
  });
});
