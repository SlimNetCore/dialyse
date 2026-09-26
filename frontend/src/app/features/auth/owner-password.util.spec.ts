import {describe, expect, it} from 'vitest';
import {isOwnerPasswordValid, ownerPasswordChecks, setupErrorKey} from './owner-password.util';

describe('owner-password.util', () => {
  it('accepte un mot de passe exigeant', () => {
    expect(isOwnerPasswordValid('Proprietaire#Solide-2026', 'owner')).toBe(true);
  });

  it('détaille chaque exigence non satisfaite', () => {
    const c = ownerPasswordChecks('abc', 'abc');
    expect(c).toEqual({length: false, upper: false, lower: true, digit: false, symbol: false, noUsername: false});
  });

  it('refuse un mot de passe sans symbole ou contenant l’identifiant', () => {
    expect(isOwnerPasswordValid('ProprietaireSolide2026', 'owner')).toBe(false);
    expect(isOwnerPasswordValid('Zz-Owner-Secret-2026', 'owner')).toBe(false);
    expect(isOwnerPasswordValid('Trop#Court1a', 'owner')).toBe(false);
  });

  it('traduit les refus du serveur', () => {
    expect(setupErrorKey('SETUP_TOKEN_INVALID')).toBe('SETUP.ERR.SETUP_TOKEN_INVALID');
    expect(setupErrorKey('PASSWORD_WEAK')).toBe('SETUP.ERR.PASSWORD_WEAK');
    expect(setupErrorKey('AUTRE')).toBe('SETUP.ERR.GENERIC');
    expect(setupErrorKey(undefined)).toBe('SETUP.ERR.GENERIC');
  });
});
