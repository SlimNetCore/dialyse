import {describe, expect, it} from 'vitest';
import {defaultValidUntil, LicenseFormValue, validateLicenseForm} from './license-form.util';

const valid: LicenseFormValue = {
  societeId: 's1', type: 'STANDARD', maxUsers: 5, validFrom: '2026-01-01', validUntil: '2027-01-01',
};

describe('validateLicenseForm', () => {
  it('accepte un formulaire complet', () => {
    expect(validateLicenseForm(valid, 2)).toBeNull();
  });

  it('exige la société et les dates', () => {
    expect(validateLicenseForm({...valid, societeId: ''}, 2)).toBe('ERR_REQUIRED');
    expect(validateLicenseForm({...valid, validUntil: ''}, 2)).toBe('ERR_REQUIRED');
  });

  it('exige au moins un centre', () => {
    expect(validateLicenseForm(valid, 0)).toBe('ERR_NO_CENTRES');
  });

  it('exige au moins un poste', () => {
    expect(validateLicenseForm({...valid, maxUsers: 0}, 1)).toBe('ERR_SEATS');
    expect(validateLicenseForm({...valid, maxUsers: Number.NaN}, 1)).toBe('ERR_SEATS');
  });

  it('refuse une fin de validité antérieure ou égale au début', () => {
    expect(validateLicenseForm({...valid, validUntil: '2026-01-01'}, 1)).toBe('ERR_DATES');
    expect(validateLicenseForm({...valid, validUntil: '2025-12-31'}, 1)).toBe('ERR_DATES');
  });
});

describe('defaultValidUntil', () => {
  it('propose un an en standard et 30 jours en essai', () => {
    expect(defaultValidUntil('STANDARD', '2026-01-01')).toBe('2027-01-01');
    expect(defaultValidUntil('TRIAL', '2026-01-01')).toBe('2026-01-31');
  });
});
