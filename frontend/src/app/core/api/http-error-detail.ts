/**
 * Lit le `detail` d'un ProblemDetail du backend dans une erreur HTTP, y compris quand la requête attendait un blob
 * (PDF/Excel) : le corps d'erreur est alors un Blob JSON à relire. Retourne null si aucun détail exploitable.
 */
export async function httpErrorDetail(err: unknown): Promise<string | null> {
  const body = (err && typeof err === 'object' ? (err as Record<string, unknown>)['error'] : null) as unknown;
  let parsed: unknown = body;
  if (typeof Blob !== 'undefined' && body instanceof Blob) {
    try {
      parsed = JSON.parse(await body.text());
    } catch {
      return null;
    }
  }
  if (parsed && typeof parsed === 'object') {
    const detail = (parsed as Record<string, unknown>)['detail'];
    if (typeof detail === 'string' && detail.trim()) {
      return detail;
    }
  }
  return null;
}

/** Statut HTTP d'une erreur (null si ce n'en est pas une). */
export function httpErrorStatus(err: unknown): number | null {
  const status = err && typeof err === 'object' ? (err as Record<string, unknown>)['status'] : null;
  return typeof status === 'number' ? status : null;
}

/** Code unique du refus (HTTP 422) de toute modification d'une séance facturée (F6, ADR 0035). */
export const CODE_SEANCE_FACTUREE_NON_MODIFIABLE = 'SEANCE_FACTUREE_NON_MODIFIABLE';
/** Clé i18n du message de repli quand le backend ne fournit pas de `detail`. */
export const CLE_SEANCE_FACTUREE_NON_MODIFIABLE = 'SEANCES.FACTUREE_NON_MODIFIABLE';

/** Vrai si l'erreur HTTP est le refus 422 `SEANCE_FACTUREE_NON_MODIFIABLE` (corps JSON déjà parsé). */
export function isSeanceFactureeNonModifiable(err: unknown): boolean {
  const body = err && typeof err === 'object' ? (err as Record<string, unknown>)['error'] : null;
  const code = body && typeof body === 'object' ? (body as Record<string, unknown>)['code'] : null;
  return httpErrorStatus(err) === 422 && code === CODE_SEANCE_FACTUREE_NON_MODIFIABLE;
}

/**
 * Message à afficher pour le refus « séance facturée » : le `detail` du backend s'il est présent, sinon la clé i18n
 * de repli (à passer par `translate`). Retourne null pour toute autre erreur (elle garde son traitement habituel).
 */
export function seanceFactureeNonModifiableMessage(err: unknown): string | null {
  if (!isSeanceFactureeNonModifiable(err)) return null;
  const detail = ((err as Record<string, unknown>)['error'] as Record<string, unknown>)['detail'];
  return typeof detail === 'string' && detail.trim() ? detail : CLE_SEANCE_FACTUREE_NON_MODIFIABLE;
}

/** Profils autorisés à imprimer la synthèse mensuelle de facturation (le backend refuse les autres en 403). */
export const ROLES_SYNTHESE_FACTURATION = ['ADMIN', 'SECRETAIRE'] as const;
export const TYPE_DOCUMENT_SYNTHESE_FACTURATION = 'SYNTHESE_FACTURATION_MENSUELLE';
