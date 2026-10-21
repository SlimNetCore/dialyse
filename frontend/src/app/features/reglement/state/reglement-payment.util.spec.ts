import {describe, expect, it} from 'vitest';
import {buildPaymentPayload, newIdempotenceKey, reglementErrorMessage} from './reglement-payment.util';

describe('reglement-payment.util', () => {
  it('génère un UUID', () => {
    expect(newIdempotenceKey()).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
  });

  it('omet mode et référence vides, sans userId', () => {
    const {payload} = buildPaymentPayload('c', 'f1', 10, null, '  ', {});
    expect(payload).toEqual({centerId: 'c', montant: 10, cleIdempotence: payload.cleIdempotence});
  });

  it('réutilise la clé mémorisée de la facture', () => {
    const {payload, keys} = buildPaymentPayload('c', 'f1', 10, 'CAISSE', 'R1', {f1: 'cle-existante'});
    expect(payload.cleIdempotence).toBe('cle-existante');
    expect(payload.modeReglement).toBe('CAISSE');
    expect(payload.referencePaiement).toBe('R1');
    expect(keys['f1']).toBe('cle-existante');
  });

  it('mappe 403 vers le message de centre', () => {
    expect(reglementErrorMessage({status: 403, message: 'Http failure'})).toBe('REGLEMENT_MODULE.ERROR.FORBIDDEN');
  });

  it('mappe le refus de remboursement trop élevé', () => {
    expect(reglementErrorMessage({status: 422, error: {code: 'REMBOURSEMENT_SUPERIEUR_AU_TOTAL_REGLE', detail: 'd'}}))
      .toBe('REGLEMENT_MODULE.ERROR.REMBOURSEMENT_SUPERIEUR_AU_TOTAL_REGLE');
  });

  it('affiche le detail du backend pour 422 et 400', () => {
    expect(reglementErrorMessage({status: 422, error: {detail: 'Règle violée'}})).toBe('Règle violée');
    expect(reglementErrorMessage({status: 400, error: {detail: 'Mode de reglement inconnu : X'}}))
      .toBe('Mode de reglement inconnu : X');
  });

  it('se replie sur le message puis sur le message générique', () => {
    expect(reglementErrorMessage({status: 500, message: 'boom'})).toBe('boom');
    expect(reglementErrorMessage(null)).toBe('REGLEMENT_MODULE.ERROR.GENERIC');
  });
});
