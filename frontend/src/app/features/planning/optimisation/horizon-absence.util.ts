import {BORNES_OPTIMISATION} from '../../../core/api/planning-optimisation-api.service';

const JOUR_MS = 24 * 3600 * 1000;
const ISO = /^\d{4}-\d{2}-\d{2}$/;

function enUtc(iso: string): number {
  return Date.parse(`${iso}T00:00:00Z`);
}

/** Dimanche de la semaine (début de semaine du planning) d'une date ISO, en ISO. */
function dimanche(ms: number): number {
  return ms - new Date(ms).getUTCDay() * JOUR_MS;
}

export type HorizonCouverture = { debut: string; semaines: number };

/**
 * Période reçue dans l'adresse de l'écran (`?debut=2026-10-19&semaines=3`) : date valide et nombre de semaines
 * ramené aux bornes du serveur ; {@code null} si l'adresse n'en porte pas ou qu'elle est invalide.
 */
export function horizonDepuisParametres(debut: string | null, semaines: string | null): HorizonCouverture | null {
  if (!debut || !ISO.test(debut) || Number.isNaN(enUtc(debut))) return null;
  const n = Number.parseInt(semaines ?? '', 10);
  if (!Number.isFinite(n)) return null;
  return {debut, semaines: Math.min(BORNES_OPTIMISATION.semaines.max, Math.max(BORNES_OPTIMISATION.semaines.min, n))};
}

/**
 * Horizon à proposer pour pourvoir une absence : il commence aujourd'hui (ou au début de l'absence si elle est à venir)
 * et va jusqu'à sa fin, plafonné aux bornes du serveur. Sans cela l'optimisation ne regarde que la semaine en cours.
 *
 * @returns {@code null} si les dates sont invalides ou si l'absence est déjà terminée
 */
export function horizonPourAbsence(debut: string | null, fin: string | null, aujourdhui: string): HorizonCouverture | null {
  if (!debut || !fin || !ISO.test(debut) || !ISO.test(fin) || !ISO.test(aujourdhui)) return null;
  const jusqua = enUtc(fin);
  const depuis = Math.max(enUtc(debut), enUtc(aujourdhui));
  if (Number.isNaN(jusqua) || Number.isNaN(depuis) || jusqua < enUtc(aujourdhui)) return null;
  const jours = Math.floor((jusqua - dimanche(depuis)) / JOUR_MS) + 1;
  const semaines = Math.ceil(jours / 7);
  return {
    debut: new Date(depuis).toISOString().slice(0, 10),
    semaines: Math.min(BORNES_OPTIMISATION.semaines.max, Math.max(BORNES_OPTIMISATION.semaines.min, semaines)),
  };
}
