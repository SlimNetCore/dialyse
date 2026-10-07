import {CaseCalendrier, JourCalendrier} from '../../../core/api/planning-optimisation-api.service';

/** Colonne d'en-tête du planning proposé : un jour de la semaine, avec sa date et son éventuelle fermeture. */
export interface EnteteJour {
  index: number;
  jour: JourCalendrier['jour'];
  date: string;
  ferme: boolean;
  motifFermeture: string | null;
}

/** Colonnes de la semaine, lues sur la première ligne (toutes les lignes portent les mêmes sept jours). */
export function entetesJours(cases: readonly CaseCalendrier[]): EnteteJour[] {
  return (cases[0]?.jours ?? []).map((j, index) => ({
    index, jour: j.jour, date: j.date, ferme: j.ferme, motifFermeture: j.motifFermeture,
  }));
}

/** Un jour ne montre rien quand ni patient ni infirmier n'y figure et que le centre est ouvert. */
export function jourVide(jour: JourCalendrier | undefined): boolean {
  return !jour || (!jour.ferme && jour.patients.length === 0 && jour.infirmiers.length === 0);
}

/** Lignes (salle × créneau) qui ont une activité le jour d'index donné (dimanche = 0). */
export function lignesDuJour(cases: readonly CaseCalendrier[], index: number): CaseCalendrier[] {
  return cases.filter((c) => !jourVide(c.jours[index]));
}

/**
 * Jour affiché par défaut : aujourd'hui s'il a de l'activité dans la semaine, sinon le premier jour qui en a, sinon
 * le premier jour.
 */
export function jourParDefaut(cases: readonly CaseCalendrier[], aujourdhui: string): number {
  const entetes = entetesJours(cases);
  const actif = (index: number) => lignesDuJour(cases, index).length > 0;
  const duJour = entetes.find((e) => e.date === aujourdhui && actif(e.index));
  return duJour?.index ?? entetes.find((e) => actif(e.index))?.index ?? 0;
}

/** Couleur d'une case : fermée, vide, infirmiers manquants (`manque`), ou couverte (`ok`). */
export function classeJour(jour: JourCalendrier | undefined): 'closed' | 'empty' | 'manque' | 'ok' {
  if (!jour) return 'empty';
  if (jour.ferme) return 'closed';
  if (jour.patients.length === 0 && jour.infirmiers.length === 0) return 'empty';
  return jour.manque > 0 ? 'manque' : 'ok';
}

/** Semaine précédente (`-1`) ou suivante (`1`) dans la liste des semaines de l'horizon, ou `null` aux extrémités. */
export function semaineVoisine(semaines: readonly string[], courante: string | null, sens: -1 | 1): string | null {
  const index = courante ? semaines.indexOf(courante) : -1;
  return index < 0 ? null : (semaines[index + sens] ?? null);
}

/** Fin de semaine (samedi) d'une semaine dont le début (dimanche) est au format `AAAA-MM-JJ`, sans effet de fuseau. */
export function finDeSemaine(debut: string): string {
  const [annee, mois, jour] = debut.split('-').map(Number);
  const fin = new Date(Date.UTC(annee, mois - 1, jour + 6));
  return fin.toISOString().slice(0, 10);
}
