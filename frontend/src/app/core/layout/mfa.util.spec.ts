import {describe, expect, it} from 'vitest';
import {mfaErrorKey} from './mfa.util';

describe('mfaErrorKey', () => {
  it('traduit les refus connus', () => {
    expect(mfaErrorKey('MFA_INVALID')).toBe('MFA.ERR.MFA_INVALID');
    expect(mfaErrorKey('MFA_LOCKED')).toBe('MFA.ERR.MFA_LOCKED');
  });

  it('retombe sur le message générique', () => {
    expect(mfaErrorKey('AUTRE')).toBe('MFA.ERR.GENERIC');
    expect(mfaErrorKey(undefined)).toBe('MFA.ERR.GENERIC');
  });
});
