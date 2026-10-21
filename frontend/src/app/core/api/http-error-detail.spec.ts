import {describe, expect, it} from 'vitest';
import {
  CLE_SEANCE_FACTUREE_NON_MODIFIABLE,
  isSeanceFactureeNonModifiable,
  seanceFactureeNonModifiableMessage,
} from './http-error-detail';

const refus = (detail?: string) => ({
  status: 422,
  error: {code: 'SEANCE_FACTUREE_NON_MODIFIABLE', title: 'Séance facturée', ...(detail ? {detail} : {})},
});

describe('refus SEANCE_FACTUREE_NON_MODIFIABLE', () => {
  it('reconnaît le 422 de code SEANCE_FACTUREE_NON_MODIFIABLE', () => {
    expect(isSeanceFactureeNonModifiable(refus())).toBe(true);
  });

  it('ne reconnaît ni un autre code, ni un autre statut, ni une valeur inattendue', () => {
    expect(isSeanceFactureeNonModifiable({status: 422, error: {code: 'BUSINESS_RULE_VIOLATION'}})).toBe(false);
    expect(isSeanceFactureeNonModifiable({status: 400, error: {code: 'SEANCE_FACTUREE_NON_MODIFIABLE'}})).toBe(false);
    expect(isSeanceFactureeNonModifiable({status: 422, error: {code: 'SEANCE_FACTUREE_NON_SUPPRIMABLE'}})).toBe(false);
    expect(isSeanceFactureeNonModifiable(null)).toBe(false);
    expect(isSeanceFactureeNonModifiable({status: 422})).toBe(false);
  });

  it('affiche le detail du backend s\'il est présent', () => {
    expect(seanceFactureeNonModifiableMessage(refus('Séance facturée.'))).toBe('Séance facturée.');
  });

  it('retombe sur la clé i18n sans detail, et ignore les autres erreurs', () => {
    expect(seanceFactureeNonModifiableMessage(refus())).toBe(CLE_SEANCE_FACTUREE_NON_MODIFIABLE);
    expect(seanceFactureeNonModifiableMessage({status: 500, error: {detail: 'x'}})).toBeNull();
  });
});
