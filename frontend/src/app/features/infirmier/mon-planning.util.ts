import {CaseMonPlanning, CreneauPersonnel, MonPlanning} from '../../core/api/infirmier-api.service';
import {CreneauRef, JourPlanning, JourSemaine, SalleRef} from '../../core/api/planning-api.service';
import {jourFerme} from '../planning/planning.util';

/** Ligne de ma grille : une salle et un créneau sur lesquels je suis affecté (au moins un jour de la semaine). */
export interface LigneMonPlanning {
  salle: SalleRef;
  creneau: CreneauRef;
}

/**
 * Mes salles et créneaux de la semaine, dans l'ordre salle puis créneau (comme le planning des séances). Seules les
 * lignes où j'ai au moins un créneau (prévu, remplaçant ou absent) sont affichées.
 */
export function lignesMonPlanning(planning: MonPlanning): LigneMonPlanning[] {
  return planning.salles.flatMap((salle) => planning.creneaux
    .filter((creneau) => planning.mesCreneaux.some((c) => c.salleId === salle.id && c.creneauId === creneau.id))
    .map((creneau) => ({salle, creneau})));
}

export function monCreneau(
  planning: MonPlanning, salleId: string, creneauId: string, jour: JourSemaine,
): CreneauPersonnel | undefined {
  return planning.mesCreneaux.find((c) => c.salleId === salleId && c.creneauId === creneauId && c.jour === jour);
}

export function maCase(
  planning: MonPlanning, salleId: string, creneauId: string, jour: JourSemaine,
): CaseMonPlanning | undefined {
  return planning.mesCases.find((c) => c.salleId === salleId && c.creneauId === creneauId && c.jour === jour);
}

/** Mes lignes d'un jour (celles où j'ai un créneau ce jour-là), dans l'ordre salle puis créneau. */
export function lignesMonJour(planning: MonPlanning, jour: JourSemaine): LigneMonPlanning[] {
  return lignesMonPlanning(planning).filter((l) => !!monCreneau(planning, l.salle.id, l.creneau.id, jour));
}

/**
 * Jour affiché par défaut : aujourd'hui s'il est dans la semaine, sinon mon premier jour de travail, sinon le premier
 * jour ouvert.
 */
export function jourParDefautMonPlanning(planning: MonPlanning, aujourdhui: string): JourSemaine | null {
  const jourDuJour = planning.jours.find((j) => j.date === aujourdhui);
  if (jourDuJour) return jourDuJour.jour;
  const premierTravail = planning.jours.find((j) => planning.mesCreneaux.some((c) => c.jour === j.jour));
  return (premierTravail ?? planning.jours.find((j) => !jourFerme(j)) ?? planning.jours[0])?.jour ?? null;
}

export function jourDe(planning: MonPlanning, jour: JourSemaine): JourPlanning | undefined {
  return planning.jours.find((j) => j.jour === jour);
}

/** Classe CSS d'une case : fermée, sans créneau pour moi, ou ma situation (prevu, remplacant, absent). */
export function classeMaCase(creneau: CreneauPersonnel | undefined, ferme: boolean): string {
  if (ferme) return 'closed';
  return creneau ? creneau.situation.toLowerCase() : 'none';
}
