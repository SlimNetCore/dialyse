/** Message d'erreur affichable : une clé i18n (ou un texte brut du backend) et ses paramètres de traduction. */
export type FacturationErrorState = {
  error: string;
  errorParams: Record<string, unknown> | null;
};

const CODE_NUMERO_DEJA_PRIS = 'NUMERO_DE_FACTURE_DEJA_PRIS';
const CODE_SEANCES_SANS_FORFAIT = 'SEANCES_SANS_FORFAIT';

/**
 * Traduit une erreur HTTP de la facturation (ProblemDetail du backend) :
 * - 409 NUMERO_DE_FACTURE_DEJA_PRIS et 422 SEANCES_SANS_FORFAIT : messages dédiés ;
 * - autres 400/403/422 : le `detail` du backend, tel quel ;
 * - sinon : le message technique s'il existe, à défaut le message générique.
 */
export function facturationErrorState(err: unknown): FacturationErrorState {
  const e = (err && typeof err === 'object' ? err : {}) as Record<string, unknown>;
  const status = typeof e['status'] === 'number' ? e['status'] : null;
  const body = e['error'] && typeof e['error'] === 'object' ? (e['error'] as Record<string, unknown>) : null;
  const code = typeof body?.['code'] === 'string' ? body['code'] : null;
  const detail = typeof body?.['detail'] === 'string' && body['detail'].trim() ? body['detail'] : null;

  if (status === 409 && code === CODE_NUMERO_DEJA_PRIS) {
    return {error: 'FACTURATION.ERROR.NUMERO_DEJA_PRIS', errorParams: {detail: detail ?? ''}};
  }
  if (status === 422 && code === CODE_SEANCES_SANS_FORFAIT) {
    const raw = body?.['seancesSansForfait'];
    const count = Array.isArray(raw) ? raw.length : Number(raw ?? 0);
    return {error: 'FACTURATION.ERROR.SEANCES_SANS_FORFAIT', errorParams: {count, detail: detail ?? ''}};
  }
  if ((status === 400 || status === 403 || status === 422) && detail) {
    return {error: detail, errorParams: null};
  }
  if (typeof e['message'] === 'string') {
    return {error: e['message'], errorParams: null};
  }
  if (body && typeof body['message'] === 'string') {
    return {error: body['message'], errorParams: null};
  }
  return {error: 'FACTURATION.ERROR.GENERIC', errorParams: null};
}
