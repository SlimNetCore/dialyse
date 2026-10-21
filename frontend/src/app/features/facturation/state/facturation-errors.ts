import {httpErrorDetail, httpErrorStatus} from '../../../core/api/http-error-detail';

/**
 * Erreur d'impression de la synthèse (réponse blob) : `detail` du backend s'il existe (lu dans le blob d'erreur),
 * sinon message dédié au 403, sinon message générique d'impression. `translatable` = `message` est une clé i18n.
 */
export async function synthesePrintError(err: unknown): Promise<{ message: string; translatable: boolean }> {
  const detail = await httpErrorDetail(err);
  if (detail) {
    return {message: detail, translatable: false};
  }
  if (httpErrorStatus(err) === 403) {
    return {message: 'FACTURATION.ERROR.SYNTHESE_FORBIDDEN', translatable: true};
  }
  return {message: 'COMMON.PRINT_ERROR', translatable: true};
}

/** Message d'erreur affichable : une clé i18n (ou un texte brut du backend) et ses paramètres de traduction. */
export type FacturationErrorState = {
  error: string;
  errorParams: Record<string, unknown> | null;
};

const CODE_NUMERO_DEJA_PRIS = 'NUMERO_DE_FACTURE_DEJA_PRIS';
const CODE_SEANCES_SANS_FORFAIT = 'SEANCES_SANS_FORFAIT';
const CODE_PERIODE_CLOTUREE = 'PERIODE_DE_FACTURATION_CLOTUREE';

/** Refus de la clôture mensuelle (F7) : « statut code » -> clé i18n. */
const CLES_CLOTURE: Record<string, string> = {
  '409 PERIODE_DEJA_CLOTUREE': 'FACTURATION.ERROR.PERIODE_DEJA_CLOTUREE',
  '409 PERIODE_NON_CLOTUREE': 'FACTURATION.ERROR.PERIODE_NON_CLOTUREE',
  '422 PERIODE_COMPTABLE_CLOTUREE': 'FACTURATION.ERROR.PERIODE_COMPTABLE_CLOTUREE',
  '400 MOTIF_DE_REOUVERTURE_OBLIGATOIRE': 'FACTURATION.ERROR.MOTIF_OBLIGATOIRE',
  '400 MOIS_INVALIDE': 'FACTURATION.ERROR.MOIS_INVALIDE',
  '400 ANNEE_INVALIDE': 'FACTURATION.ERROR.ANNEE_INVALIDE',
};

/**
 * Traduit une erreur HTTP de la facturation (ProblemDetail du backend) :
 * - 409 NUMERO_DE_FACTURE_DEJA_PRIS et 422 SEANCES_SANS_FORFAIT : messages dédiés ;
 * - autres 400/403/422 : le `detail` du backend, tel quel ;
 * - 403 sans detail (centre différent de la session, profil insuffisant) : message dédié ;
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
  if (status === 422 && code === CODE_PERIODE_CLOTUREE) {
    const mois = typeof body?.['mois'] === 'string' ? body['mois'] : null;
    if (mois) {
      return {error: 'FACTURATION.ERROR.PERIODE_CLOTUREE', errorParams: {mois}};
    }
    return detail ? {error: detail, errorParams: null} : {error: 'FACTURATION.ERROR.PERIODE_CLOTUREE_SANS_MOIS', errorParams: null};
  }
  const cle = status !== null ? CLES_CLOTURE[`${status} ${code}`] : undefined;
  if (cle) {
    return {error: cle, errorParams: null};
  }
  if ((status === 400 || status === 403 || status === 422) && detail) {
    return {error: detail, errorParams: null};
  }
  if (status === 403) {
    return {error: 'FACTURATION.ERROR.FORBIDDEN', errorParams: null};
  }
  if (typeof e['message'] === 'string') {
    return {error: e['message'], errorParams: null};
  }
  if (body && typeof body['message'] === 'string') {
    return {error: body['message'], errorParams: null};
  }
  return {error: 'FACTURATION.ERROR.GENERIC', errorParams: null};
}
