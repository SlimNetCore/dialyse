import {describe, expect, it} from 'vitest';
import {
  ETATS_APRES_INTERVENTION,
  ETATS_AVANT_INTERVENTION,
  STATUTS_INDISPONIBLES_PATIENT,
  statutEquipementTone,
} from './gmao-options.util';

describe('états d\'équipement liés aux interventions', () => {
  it('ne permet jamais de réformer depuis une intervention : « À réformer » seulement après', () => {
    expect(ETATS_AVANT_INTERVENTION).not.toContain('REFORME');
    expect(ETATS_AVANT_INTERVENTION).not.toContain('A_REFORMER');
    expect(ETATS_APRES_INTERVENTION).toContain('A_REFORMER');
    expect(ETATS_APRES_INTERVENTION).not.toContain('REFORME');
  });

  it('un équipement en service reste affectable ; « À réformer » ne l\'est plus', () => {
    expect(STATUTS_INDISPONIBLES_PATIENT).not.toContain('EN_SERVICE');
    expect(STATUTS_INDISPONIBLES_PATIENT).toContain('A_REFORMER');
    expect(statutEquipementTone('A_REFORMER')).toBe('danger');
  });
});
