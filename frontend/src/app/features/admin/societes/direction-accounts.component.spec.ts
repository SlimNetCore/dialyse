import {describe, expect, it} from 'vitest';
import {accountErrorKey} from './direction-accounts.component';

describe('accountErrorKey', () => {
  it('traduit les règles de mot de passe', () => {
    expect(accountErrorKey('PASSWORD_TOO_SHORT')).toBe('DIRECTION.ACCOUNTS.ERR.PASSWORD_TOO_SHORT');
    expect(accountErrorKey('PASSWORD_CONTAINS_USERNAME')).toBe('DIRECTION.ACCOUNTS.ERR.PASSWORD_CONTAINS_USERNAME');
  });

  it('retombe sur le message générique pour tout autre code', () => {
    expect(accountErrorKey('BUSINESS_RULE_VIOLATION')).toBe('DIRECTION.ACCOUNTS.ERR.GENERIC');
    expect(accountErrorKey(undefined)).toBe('DIRECTION.ACCOUNTS.ERR.GENERIC');
  });
});
