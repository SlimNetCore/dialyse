import {MoisDeFacturationReponse} from '../../../core/api/backend-api.service';

/** Longueur maximale du motif de réouverture (le backend refuse au-delà). */
export const MOTIF_REOUVERTURE_MAX = 500;

/** Mois (AAAA-MM) couverts par l'intervalle [début, fin] (AAAA-MM-JJ) ; vide si l'intervalle est invalide. */
export function moisCouverts(debut: string, fin: string): string[] {
  const dateIso = /^\d{4}-\d{2}-\d{2}$/;
  if (!dateIso.test(debut) || !dateIso.test(fin) || debut > fin) {
    return [];
  }
  let annee = Number(debut.slice(0, 4));
  let mois = Number(debut.slice(5, 7));
  const anneeFin = Number(fin.slice(0, 4));
  const moisFin = Number(fin.slice(5, 7));
  const result: string[] = [];
  while (annee < anneeFin || (annee === anneeFin && mois <= moisFin)) {
    result.push(`${annee}-${String(mois).padStart(2, '0')}`);
    mois += 1;
    if (mois > 12) {
      mois = 1;
      annee += 1;
    }
  }
  return result;
}

/** Années distinctes (ordre croissant) des mois AAAA-MM donnés. */
export function anneesDesMois(mois: string[]): number[] {
  return [...new Set(mois.map((m) => Number(m.slice(0, 4))))].filter((a) => a > 0).sort((a, b) => a - b);
}

/** Motif de réouverture saisi, nettoyé ; vide si absent ou trop long. */
export function motifDeReouvertureValide(motif: string): string {
  const net = motif.trim();
  return net.length > 0 && net.length <= MOTIF_REOUVERTURE_MAX ? net : '';
}

/**
 * Mois AAAA-MM du jour dans le calendrier du navigateur (le fuseau du centre n'est pas exposé au front). Sert seulement à désactiver
 * le bouton de clôture ; le serveur reste juge (422 PERIODE_NON_TERMINEE au fuseau du centre, ADR 0044).
 */
export function moisCourant(now: Date = new Date()): string {
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
}

/** Seul un mois passé se clôture, comme les mois d'absences : jamais le mois en cours ni un mois futur (ADR 0044). */
export function moisClosable(mois: string, courant: string = moisCourant()): boolean {
  return mois < courant;
}

/** Index des mois reçus, par `AAAA-MM`. */
export function indexerLesMois(periodes: MoisDeFacturationReponse[]): Record<string, MoisDeFacturationReponse> {
  return Object.fromEntries(periodes.map((p) => [p.mois, p]));
}

/** Les douze mois de l'année, ouverts par défaut tant qu'ils ne sont pas chargés. */
export function douzeMois(annee: number, index: Record<string, MoisDeFacturationReponse>): MoisDeFacturationReponse[] {
  return Array.from({length: 12}, (_, i) => {
    const mois = `${annee}-${String(i + 1).padStart(2, '0')}`;
    return index[mois] ?? {
      mois, statut: 'OUVERT', cloturePar: null, clotureLe: null, rouvertPar: null, rouvertLe: null, motifDeReouverture: null,
    };
  });
}

/** Messages de succès propres à la clôture / réouverture d'un mois (écran Administration › Clôtures mensuelles). */
export const SUCCES_DE_CLOTURE: readonly string[] = ['FACTURATION.SUCCESS.MOIS_CLOTURE', 'FACTURATION.SUCCESS.MOIS_ROUVERT'];

/** Vrai si le message de succès vient d'une clôture ou d'une réouverture de mois. */
export function estSuccesDeCloture(message: string | null | undefined): boolean {
  return !!message && SUCCES_DE_CLOTURE.includes(message);
}
