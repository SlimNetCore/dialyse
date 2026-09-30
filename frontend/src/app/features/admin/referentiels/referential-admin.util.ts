import {SignalFormValidator} from '../../../shared/forms/signal-form';
import {ReferentialEntry, ReferentialFieldDef, ValidationIssue} from '../../../core/api/referential-admin-api.service';

const PHONE = /^[+0-9 ().\-/]{6,}$/;
const DECIMAL = /^\d+([.,]\d{1,2})?$/;

/** Valeur affichée dans le tableau : libellé de la cible pour une référence, valeur brute sinon. */
export function displayValue(entry: ReferentialEntry, field: ReferentialFieldDef): string {
  if (field.type === 'REFERENCE') return entry.references[field.key] ?? '';
  return entry.values[field.key] ?? '';
}

/**
 * Validateurs de formulaire déduits de la description du champ (mêmes règles que le backend, qui reste
 * l'autorité : ces contrôles évitent seulement un aller-retour pour les erreurs évidentes).
 */
export function fieldValidators(field: ReferentialFieldDef): SignalFormValidator<string>[] {
  const validators: SignalFormValidator<string>[] = [];
  if (field.required && field.defaultValue === null) {
    validators.push((v) => (v ?? '').trim() ? null : 'ADMIN.REFERENTIALS.ISSUES.REQUIRED');
  }
  if (field.maxLength > 0 && field.type !== 'ENUM') {
    validators.push((v) => (v ?? '').trim().length > field.maxLength ? 'ADMIN.REFERENTIALS.ISSUES.TOO_LONG' : null);
  }
  if (field.type === 'DECIMAL') {
    validators.push((v) => !(v ?? '').trim() || DECIMAL.test((v ?? '').replace(/\s/g, ''))
      ? null : 'ADMIN.REFERENTIALS.ISSUES.INVALID_NUMBER');
  }
  if (field.type === 'PHONE') {
    validators.push((v) => !(v ?? '').trim() || PHONE.test((v ?? '').trim()) ? null : 'ADMIN.REFERENTIALS.ISSUES.INVALID_PHONE');
  }
  return validators;
}

/** Clé i18n + paramètres d'une anomalie ; le message français du serveur sert de repli. */
export function issueTranslation(issue: ValidationIssue, fieldLabel: string): {
  key: string;
  params: Record<string, string>
} {
  return {key: `ADMIN.REFERENTIALS.ISSUES.${issue.code}`, params: {...issue.params, field: fieldLabel}};
}

/** Message lisible d'une erreur HTTP (ProblemDetail du backend). */
export function httpErrorMessage(err: unknown, fallback: string): string {
  const body = (err as { error?: { detail?: string } })?.error;
  return body?.detail || fallback;
}

/** Anomalies champ par champ renvoyées par le backend sur une saisie refusée. */
export function httpIssues(err: unknown): ValidationIssue[] {
  const issues = (err as { error?: { issues?: ValidationIssue[] } })?.error?.issues;
  return Array.isArray(issues) ? issues : [];
}

