import {describe, expect, it} from 'vitest';
import {ReferentialEntry, ReferentialFieldDef} from '../../../core/api/referential-admin-api.service';
import {displayValue, fieldValidators, httpErrorMessage, httpIssues, issueTranslation} from './referential-admin.util';

function field(partial: Partial<ReferentialFieldDef>): ReferentialFieldDef {
  return {
    key: 'code', label: 'Code', type: 'TEXT', required: true, requiredColumn: true, maxLength: 5,
    allowedValues: [], defaultValue: null, reference: null, example: null, ...partial,
  };
}

function run(def: ReferentialFieldDef, value: string): string[] {
  return fieldValidators(def).map((v) => v(value)).filter((m): m is string => !!m);
}

describe('referential-admin.util', () => {
  const entry: ReferentialEntry = {
    id: 'g1', values: {numero: 'G01', salle: 's-1', prix: null}, references: {salle: 'S1 · Salle 1'},
  };

  it('affiche le libellé de la cible pour une référence et la valeur brute sinon', () => {
    expect(displayValue(entry, field({key: 'salle', type: 'REFERENCE', reference: 'salles'}))).toBe('S1 · Salle 1');
    expect(displayValue(entry, field({key: 'numero'}))).toBe('G01');
    expect(displayValue(entry, field({key: 'prix', type: 'DECIMAL'}))).toBe('');
  });

  it('applique obligatoire et longueur maximale', () => {
    expect(run(field({}), '  ')).toEqual(['ADMIN.REFERENTIALS.ISSUES.REQUIRED']);
    expect(run(field({}), 'ABCDEF')).toEqual(['ADMIN.REFERENTIALS.ISSUES.TOO_LONG']);
    expect(run(field({}), 'CR1')).toEqual([]);
  });

  it('un champ obligatoire avec valeur par défaut peut rester vide', () => {
    expect(run(field({type: 'ENUM', defaultValue: 'FONCTIONNEL', maxLength: 30}), '')).toEqual([]);
  });

  it('contrôle les nombres et les téléphones', () => {
    const prix = field({key: 'prix', type: 'DECIMAL', required: false, maxLength: 0});
    expect(run(prix, '5600,50')).toEqual([]);
    expect(run(prix, '5 600')).toEqual([]);
    expect(run(prix, '12,345')).toEqual(['ADMIN.REFERENTIALS.ISSUES.INVALID_NUMBER']);
    expect(run(prix, '')).toEqual([]);

    const tel = field({key: 'telephone', type: 'PHONE', required: false, maxLength: 50});
    expect(run(tel, '+213 795 00 61 36')).toEqual([]);
    expect(run(tel, 'abc')).toEqual(['ADMIN.REFERENTIALS.ISSUES.INVALID_PHONE']);
  });

  it('construit la clé de traduction d\'une anomalie avec le libellé du champ', () => {
    const t = issueTranslation({
      row: 4,
      field: 'salle',
      code: 'REFERENCE_NOT_FOUND',
      message: 'x',
      params: {value: 'S9'}
    }, 'Salle');
    expect(t).toEqual({key: 'ADMIN.REFERENTIALS.ISSUES.REFERENCE_NOT_FOUND', params: {value: 'S9', field: 'Salle'}});
  });

  it('extrait message et anomalies d\'une réponse d\'erreur du backend', () => {
    const err = {
      error: {
        detail: 'Refusé',
        issues: [{row: 0, field: 'code', code: 'ALREADY_EXISTS', message: 'm', params: {}}]
      }
    };
    expect(httpErrorMessage(err, 'fallback')).toBe('Refusé');
    expect(httpIssues(err)).toHaveLength(1);
    expect(httpErrorMessage(new Error('réseau'), 'fallback')).toBe('fallback');
    expect(httpIssues(new Error('réseau'))).toEqual([]);
  });
});

