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
