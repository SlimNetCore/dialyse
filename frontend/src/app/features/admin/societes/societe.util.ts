import type {CentrePayload, CentreSociete, Societe, SocietePayload} from '../../../core/api/societe-api.service';

/** Champs texte des formulaires : toujours des chaînes (jamais null) pour les champs de saisie. */
export type SocieteForm = {
  code: string;
  raisonSociale: string;
  nif: string;
  nis: string;
  rc: string;
  adresse: string;
  ville: string;
  wilaya: string;
  telephone: string;
  email: string;
  siteWeb: string;
  piedDePage: string;
};

export type CentreForm = {
  code: string;
  nom: string;
  adresse: string;
  ville: string;
  wilaya: string;
  telephone: string;
  email: string;
  siteWeb: string;
};

export const emptySocieteForm = (): SocieteForm => ({
  code: '',
  raisonSociale: '',
  nif: '',
  nis: '',
  rc: '',
  adresse: '',
  ville: '',
  wilaya: '',
  telephone: '',
  email: '',
  siteWeb: '',
  piedDePage: '',
});

export const emptyCentreForm = (): CentreForm => ({
  code: '', nom: '', adresse: '', ville: '', wilaya: '', telephone: '', email: '', siteWeb: '',
});

/** Champ vide → `null` (le serveur normalise et valide le reste). */
const blankToNull = (value: string): string | null => (value.trim() === '' ? null : value.trim());

export function toSocietePayload(form: SocieteForm): SocietePayload {
  return {
    code: form.code.trim(),
    raisonSociale: form.raisonSociale.trim(),
    nif: blankToNull(form.nif),
    nis: blankToNull(form.nis),
    rc: blankToNull(form.rc),
    adresse: blankToNull(form.adresse),
    ville: blankToNull(form.ville),
    wilaya: blankToNull(form.wilaya),
    telephone: blankToNull(form.telephone),
    email: blankToNull(form.email),
    siteWeb: blankToNull(form.siteWeb),
    piedDePage: blankToNull(form.piedDePage),
  };
}

export function toCentrePayload(form: CentreForm): CentrePayload {
  return {
    code: form.code.trim(),
    nom: form.nom.trim(),
    adresse: blankToNull(form.adresse),
    ville: blankToNull(form.ville),
    wilaya: blankToNull(form.wilaya),
    telephone: blankToNull(form.telephone),
    email: blankToNull(form.email),
    siteWeb: blankToNull(form.siteWeb),
  };
}

const orEmpty = (value: string | null): string => value ?? '';

export function societeToForm(s: Societe): SocieteForm {
  return {
    code: s.code, raisonSociale: s.raisonSociale, nif: orEmpty(s.nif), nis: orEmpty(s.nis), rc: orEmpty(s.rc),
    adresse: orEmpty(s.adresse), ville: orEmpty(s.ville), wilaya: orEmpty(s.wilaya),
    telephone: orEmpty(s.telephone), email: orEmpty(s.email), siteWeb: orEmpty(s.siteWeb),
    piedDePage: orEmpty(s.piedDePage),
  };
}

export function centreToForm(c: CentreSociete): CentreForm {
  return {
    code: c.code, nom: c.nom, adresse: orEmpty(c.adresse), ville: orEmpty(c.ville), wilaya: orEmpty(c.wilaya),
    telephone: orEmpty(c.telephone), email: orEmpty(c.email), siteWeb: orEmpty(c.siteWeb),
  };
}

export const isSocieteFormValid = (f: SocieteForm): boolean => !!f.code.trim() && !!f.raisonSociale.trim();
export const isCentreFormValid = (f: CentreForm): boolean => !!f.code.trim() && !!f.nom.trim();

/** Taille maximale d'un logo (octets) — miroir de la limite du serveur. */
export const MAX_LOGO_BYTES = 512 * 1024;

/** Contrôle rapide avant envoi (code d'erreur traduit sous `SOCIETES.LOGO_ERRORS`) ; le serveur reste l'autorité. */
export function precheckLogoFile(file: { type: string; size: number }): string | null {
  if (file.type !== 'image/png' && file.type !== 'image/jpeg') return 'FILE_TYPE';
  if (file.size === 0) return 'EMPTY';
  if (file.size > MAX_LOGO_BYTES) return 'TOO_LARGE';
  return null;
}

/** « Ville · Wilaya » (ou un tiret si rien n'est renseigné). */
export const location = (x: { ville: string | null; wilaya: string | null }): string =>
  [x.ville, x.wilaya].filter((v): v is string => !!v).join(' · ') || '—';

/** Nombre de centres actifs / total, affiché dans la liste. */
export function centresSummary(s: Pick<Societe, 'centres'>): { actifs: number; total: number } {
  return {actifs: s.centres.filter((c) => c.actif).length, total: s.centres.length};
}
