/** Longueur minimale / maximale d'un nouveau mot de passe (mêmes bornes que le serveur). */
export const MOT_DE_PASSE_MIN = 10;
export const MOT_DE_PASSE_MAX = 128;

/**
 * Un nouveau mot de passe est refusé s'il est trop court ou trop long, sans lettre, sans chiffre ou s'il contient
 * l'identifiant de connexion (même règle que le serveur, qui reste juge).
 */
export function motDePasseTropFaible(motDePasse: string, identifiant: string | null): boolean {
  if (motDePasse.length < MOT_DE_PASSE_MIN || motDePasse.length > MOT_DE_PASSE_MAX) return true;
  if (!/\p{L}/u.test(motDePasse) || !/\p{Nd}/u.test(motDePasse)) return true;
  return !!identifiant && motDePasse.toLowerCase().includes(identifiant.toLowerCase());
}
