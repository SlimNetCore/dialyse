import {describe, expect, it} from 'vitest';
import {extractViolations, MAX_TEMPLATE_BYTES, precheckTemplateFile, templateFileName} from './modele-template.util';

describe('precheckTemplateFile', () => {
  it('accepte un .jrxml de taille raisonnable', () => {
    expect(precheckTemplateFile({name: 'Attestation.JRXML', size: 12_000})).toBeNull();
  });

  it('refuse une autre extension', () => {
    expect(precheckTemplateFile({name: 'malware.exe', size: 10})?.code).toBe('FILE_TYPE');
  });

  it('refuse un fichier vide', () => {
    expect(precheckTemplateFile({name: 'a.jrxml', size: 0})?.code).toBe('EMPTY');
  });

  it('refuse un fichier trop volumineux', () => {
    expect(precheckTemplateFile({name: 'a.jrxml', size: MAX_TEMPLATE_BYTES + 1})?.code).toBe('TOO_LARGE');
  });
});

describe('extractViolations', () => {
  it('lit les anomalies d\'une réponse 422', () => {
    const error = {
      status: 422,
      error: {violations: [{code: 'QUERY_MODIFIED', detail: 'x'}, {code: 'EXPRESSION_FORBIDDEN'}]}
    };
    expect(extractViolations(error)).toEqual([
      {code: 'QUERY_MODIFIED', detail: 'x'},
      {code: 'EXPRESSION_FORBIDDEN', detail: ''},
    ]);
  });

  it('ignore les autres erreurs', () => {
    expect(extractViolations({status: 500, error: {violations: []}})).toBeNull();
    expect(extractViolations({status: 422, error: {}})).toBeNull();
    expect(extractViolations(null)).toBeNull();
  });

  it('écarte les entrées sans code', () => {
    expect(extractViolations({status: 422, error: {violations: [{detail: 'sans code'}, {code: 'EMPTY', detail: ''}]}}))
      .toEqual([{code: 'EMPTY', detail: ''}]);
  });
});

describe('templateFileName', () => {
  it('produit un nom de fichier sûr', () => {
    expect(templateFileName('Liste Patients/../x')).toBe('liste_patients____x.jrxml');
    expect(templateFileName('ATTESTATION', 3)).toBe('attestation-v3.jrxml');
    expect(templateFileName('')).toBe('modele.jrxml');
  });
});
