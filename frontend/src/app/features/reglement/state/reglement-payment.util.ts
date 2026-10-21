import type {RegisterFacturePaymentPayload} from '../../../core/api/backend-api.service';

/** Modes de règlement acceptés par le backend (ModeDeReglement). Facultatif : sans mode, le règlement vaut « banque ». */
export const MODES_DE_REGLEMENT = ['BANQUE', 'CHEQUE', 'CAISSE', 'ESPECES'] as const;
export type ModeDeReglement = (typeof MODES_DE_REGLEMENT)[number];

const GENERIC_KEY = 'REGLEMENT_MODULE.ERROR.GENERIC';

/** UUID de la clé d'idempotence ; repli pour les contextes non sécurisés où crypto.randomUUID est absent. */
export function newIdempotenceKey(): string {
  const c = globalThis.crypto;
  if (c && typeof c.randomUUID === 'function') {
    return c.randomUUID();
  }
  const bytes = new Uint8Array(16);
  if (c && typeof c.getRandomValues === 'function') {
    c.getRandomValues(bytes);
  } else {
    for (let i = 0; i < bytes.length; i++) bytes[i] = Math.floor(Math.random() * 256);
  }
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

/**
 * Corps de l'enregistrement d'un règlement : sans userId (l'auteur est l'utilisateur de la session), mode et référence
 * facultatifs (omis si vides). La clé d'idempotence de la facture est celle déjà mémorisée (renvoi) ou une nouvelle ;
 * `keys` est la table à mémoriser (à vider pour la facture au succès ou quand la saisie change).
 */
export function buildPaymentPayload(
  centerId: string,
  factureId: string,
  montant: number,
  mode: string | null,
  reference: string,
  keys: Record<string, string>,
): { payload: RegisterFacturePaymentPayload; keys: Record<string, string> } {
  const cle = keys[factureId] ?? newIdempotenceKey();
  const ref = reference.trim();
  return {
    payload: {
      centerId,
      montant,
      ...(mode ? {modeReglement: mode} : {}),
      ...(ref ? {referencePaiement: ref} : {}),
      cleIdempotence: cle,
    },
    keys: {...keys, [factureId]: cle},
  };
}

/**
 * Message d'erreur affichable (clé i18n ou texte du backend) pour une erreur HTTP de règlement :
 * 403 centre ≠ session, 422 REMBOURSEMENT_SUPERIEUR_AU_TOTAL_REGLE (clé dédiée) ou autre règle (detail),
 * 400 (detail), repli sur le message existant puis sur le message générique.
 */
export function reglementErrorMessage(err: unknown): string {
  if (!err || typeof err !== 'object') {
    return GENERIC_KEY;
  }
  const value = err as Record<string, unknown>;
  const body = value['error'] && typeof value['error'] === 'object' ? value['error'] as Record<string, unknown> : null;
  const detail = body && typeof body['detail'] === 'string' && body['detail'].trim() ? body['detail'] : null;
  const status = value['status'];

  if (status === 403) {
    return 'REGLEMENT_MODULE.ERROR.FORBIDDEN';
  }
  if (status === 422 && body?.['code'] === 'REMBOURSEMENT_SUPERIEUR_AU_TOTAL_REGLE') {
    return 'REGLEMENT_MODULE.ERROR.REMBOURSEMENT_SUPERIEUR_AU_TOTAL_REGLE';
  }
  if ((status === 422 || status === 400) && detail) {
    return detail;
  }
  if (typeof value['message'] === 'string') {
    return value['message'];
  }
  if (body && typeof body['message'] === 'string') {
    return body['message'];
  }
  return GENERIC_KEY;
}
