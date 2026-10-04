/**
 * Date saisie dans un sélecteur de date (minuit, heure locale) au format `yyyy-MM-dd`, **dans le fuseau local**.
 *
 * `Date.toISOString()` convertit en UTC : à minuit en Algérie (UTC+1) la date saisie le 15 devient « le 14 à 23 h » et
 * l'ISO donne le 14 — d'où le jour en moins à l'enregistrement.
 */
export function toLocalIsoDate(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}
