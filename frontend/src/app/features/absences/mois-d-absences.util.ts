import {MoisDAbsences} from '../../core/api/absence-patient-api.service';

/** Longueur maximale du motif de réouverture (le backend refuse au-delà). */
export const MOTIF_REOUVERTURE_MAX = 500;

/** Mois AAAA-MM d'une date ISO AAAA-MM-JJ. */
export function moisDeLaDate(dateIso: string): string {
  return dateIso.slice(0, 7);
}

/**
 * Mois AAAA-MM du jour dans le calendrier du navigateur : le fuseau du centre n'est pas exposé au front. Sert seulement à désactiver le bouton
 * de clôture ; le serveur reste juge (422 MOIS_D_ABSENCES_NON_TERMINE au fuseau du centre).
 */
export function moisCourant(now: Date = new Date()): string {
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
}

/** Seul un mois passé se clôture : jamais le mois en cours ni un mois futur (le serveur juge au fuseau du centre). */
export function moisClosable(mois: string, courant: string = moisCourant()): boolean {
  return mois < courant;
}

/** Motif de réouverture saisi, nettoyé ; vide si absent ou trop long. */
export function motifDeReouvertureValide(motif: string): string {
  const net = motif.trim();
  return net.length > 0 && net.length <= MOTIF_REOUVERTURE_MAX ? net : '';
}

/** Index des mois reçus, par `AAAA-MM`. */
export function indexerLesMois(mois: MoisDAbsences[]): Record<string, MoisDAbsences> {
  return Object.fromEntries(mois.map((m) => [m.mois, m]));
}

/** Les douze mois de l'année, ouverts par défaut tant qu'ils ne sont pas chargés. */
export function douzeMois(annee: number, index: Record<string, MoisDAbsences>): MoisDAbsences[] {
  return Array.from({length: 12}, (_, i) => {
    const mois = `${annee}-${String(i + 1).padStart(2, '0')}`;
    return index[mois] ?? {
      mois,
      statut: 'OUVERT',
      cloturePar: null,
      clotureLe: null,
      rouvertPar: null,
      rouvertLe: null,
      motifDeReouverture: null,
    };
  });
}

/** Années distinctes (ordre croissant) des dates ISO données. */
export function anneesDesDates(dates: string[]): number[] {
  return [...new Set(dates.map((d) => Number(d.slice(0, 4))))].filter((a) => a > 0).sort((a, b) => a - b);
}
