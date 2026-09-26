const KNOWN = ['MFA_INVALID', 'MFA_LOCKED', 'MFA_ALREADY_ENABLED', 'MFA_NOT_ENROLLED'];

/** Clé de traduction du refus renvoyé par le serveur pour la double authentification. */
export function mfaErrorKey(code: string | undefined): string {
  return code && KNOWN.includes(code) ? `MFA.ERR.${code}` : 'MFA.ERR.GENERIC';
}
