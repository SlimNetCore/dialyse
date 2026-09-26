export type LicenseType = 'STANDARD' | 'TRIAL';

export type LicenseFormValue = {
  societeId: string;
  type: LicenseType;
  maxUsers: number;
  validFrom: string;
  validUntil: string;
};

export const addDays = (date: Date, days: number): Date => {
  const copy = new Date(date);
  copy.setDate(copy.getDate() + days);
  return copy;
};

export const toDateInputValue = (date: Date): string => date.toISOString().slice(0, 10);

/**
 * Contrôle du formulaire d'attribution d'une licence ; renvoie la clé de traduction de l'erreur (sous
 * `LICENCES.`) ou `null` si le formulaire est valide. Le serveur revalide tout.
 */
export function validateLicenseForm(form: LicenseFormValue, selectedCenters: number): string | null {
  if (!form.societeId || !form.validFrom || !form.validUntil) return 'ERR_REQUIRED';
  if (selectedCenters === 0) return 'ERR_NO_CENTRES';
  if (!Number.isFinite(form.maxUsers) || form.maxUsers < 1) return 'ERR_SEATS';
  if (new Date(form.validUntil) <= new Date(form.validFrom)) return 'ERR_DATES';
  return null;
}

/** Durées par défaut : un an pour une licence standard, 30 jours pour une période d'essai. */
export function defaultValidUntil(type: LicenseType, validFrom: string): string {
  return toDateInputValue(addDays(new Date(validFrom), type === 'TRIAL' ? 30 : 365));
}
