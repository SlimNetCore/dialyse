import {describe, expect, it} from 'vitest';
import {accountErrorKey} from './direction-accounts.component';

describe('accountErrorKey', () => {
  it('traduit les règles de mot de passe', () => {
    expect(accountErrorKey('PASSWORD_TOO_SHORT')).toBe('DIRECTION.ACCOUNTS.ERR.PASSWORD_TOO_SHORT');
    expect(accountErrorKey('PASSWORD_CONTAINS_USERNAME')).toBe('DIRECTION.ACCOUNTS.ERR.PASSWORD_CONTAINS_USERNAME');
  });

  it('traduit les codes métier connus (identifiant pris, centre manquant...)', () => {
    expect(accountErrorKey('USERNAME_TAKEN')).toBe('DIRECTION.ACCOUNTS.ERR.USERNAME_TAKEN');
    expect(accountErrorKey('USERNAME_INVALID')).toBe('DIRECTION.ACCOUNTS.ERR.USERNAME_INVALID');
    expect(accountErrorKey('EMAIL_INVALID')).toBe('DIRECTION.ACCOUNTS.ERR.EMAIL_INVALID');
    expect(accountErrorKey('CENTRE_REQUIS')).toBe('DIRECTION.ACCOUNTS.ERR.CENTRE_REQUIS');
    expect(accountErrorKey('CENTRE_HORS_SOCIETE')).toBe('DIRECTION.ACCOUNTS.ERR.CENTRE_HORS_SOCIETE');
    expect(accountErrorKey('SOCIETE_INTROUVABLE')).toBe('DIRECTION.ACCOUNTS.ERR.SOCIETE_INTROUVABLE');
    expect(accountErrorKey('COMPTE_INTROUVABLE')).toBe('DIRECTION.ACCOUNTS.ERR.COMPTE_INTROUVABLE');
  });

  it('retombe sur le message générique pour tout code inconnu', () => {
    expect(accountErrorKey('BUSINESS_RULE_VIOLATION')).toBe('DIRECTION.ACCOUNTS.ERR.GENERIC');
    expect(accountErrorKey(undefined)).toBe('DIRECTION.ACCOUNTS.ERR.GENERIC');
  });
});
