import {describe, expect, it} from 'vitest';
import {userDeleteErrorKey} from './user-list.store';

describe('userDeleteErrorKey', () => {
  it('traduit le refus de supprimer un compte lié à une fiche d\'infirmier active (422)', () => {
    expect(userDeleteErrorKey({error: {code: 'COMPTE_LIE_A_UNE_FICHE_ACTIVE'}}))
      .toBe('ADMIN.USERS.DELETE_BLOCKED_ACTIVE_NURSE');
  });

  it('retombe sur le message générique pour tout autre échec', () => {
    expect(userDeleteErrorKey({error: {code: 'DATA_ACCESS_ERROR'}})).toBe('ADMIN.USERS.DELETE_ERROR');
    expect(userDeleteErrorKey(null)).toBe('ADMIN.USERS.DELETE_ERROR');
    expect(userDeleteErrorKey(new Error('boom'))).toBe('ADMIN.USERS.DELETE_ERROR');
  });
});
