/** Exigences du mot de passe du compte propriétaire (miroir de `PasswordPolicy.ownerViolation` côté serveur). */
export const OWNER_PASSWORD_MIN_LENGTH = 14;

export type OwnerPasswordChecks = {
  length: boolean;
  upper: boolean;
  lower: boolean;
  digit: boolean;
  symbol: boolean;
  noUsername: boolean;
};

export function ownerPasswordChecks(password: string, username: string): OwnerPasswordChecks {
  const login = username.trim().toLowerCase();
  return {
    length: password.length >= OWNER_PASSWORD_MIN_LENGTH,
    upper: /\p{Lu}/u.test(password),
    lower: /\p{Ll}/u.test(password),
    digit: /\p{Nd}/u.test(password),
    symbol: /[^\p{L}\p{N}\s]/u.test(password),
    noUsername: login === '' || !password.toLowerCase().includes(login),
  };
}

export function isOwnerPasswordValid(password: string, username: string): boolean {
  return Object.values(ownerPasswordChecks(password, username)).every(Boolean);
}

/** Clé de traduction du refus renvoyé par le serveur pour la création du propriétaire. */
export function setupErrorKey(code: string | undefined): string {
  const known = [
    'PASSWORD_TOO_SHORT', 'PASSWORD_TOO_LONG', 'PASSWORD_WEAK', 'PASSWORD_CONTAINS_USERNAME', 'USERNAME_INVALID',
    'USERNAME_TAKEN', 'EMAIL_INVALID', 'SETUP_ALREADY_DONE', 'SETUP_TOKEN_INVALID',
  ];
  return code && known.includes(code) ? `SETUP.ERR.${code}` : 'SETUP.ERR.GENERIC';
}
