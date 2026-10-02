import {describe, expect, it} from 'vitest';
import {MOT_DE_PASSE_MAX, motDePasseTropFaible} from './password-policy.util';

describe('password-policy.util', () => {
  it('accepte un mot de passe long avec une lettre et un chiffre', () => {
    expect(motDePasseTropFaible('Nouveau-mdp-2026', 'sara')).toBe(false);
    expect(motDePasseTropFaible('abcdefghi1', null)).toBe(false);
  });

  it('refuse un mot de passe trop court, sans lettre, sans chiffre ou contenant l\'identifiant', () => {
    expect(motDePasseTropFaible('abc1', 'sara')).toBe(true);
    expect(motDePasseTropFaible('abcdefghijk', 'sara')).toBe(true);
    expect(motDePasseTropFaible('12345678901', 'sara')).toBe(true);
    expect(motDePasseTropFaible('xxSARAxx123456', 'sara')).toBe(true);
  });

  it('refuse un mot de passe plus long que le maximum', () => {
    expect(motDePasseTropFaible('a1'.repeat(MOT_DE_PASSE_MAX), 'sara')).toBe(true);
  });
});
