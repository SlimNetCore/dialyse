import {AbsentCase, PresentCase, SemainePresence, StatutCasePresence} from '../../core/api/infirmier-api.service';
import {JOURS_SEMAINE, JourSemaine, OccupantPlanning, SemainePlanning} from '../../core/api/planning-api.service';

/** Une salle sur un créneau, aujourd'hui : patients attendus et infirmiers prévus. */
export interface LigneJournee {
  salleId: string;
  salleNom: string;
  creneauId: string;
  creneauLibelle: string;
  patients: OccupantPlanning[];
  infirmiers: PresentCase[];
  absents: AbsentCase[];
  requis: number;
  statut: StatutCasePresence | null;
}

export interface Journee {
  date: string;
  jour: JourSemaine;
  /** Le centre ne dialyse pas aujourd'hui (fermeture hebdomadaire, férié ou fermeture exceptionnelle). */
  ferme: boolean;
  motifFermeture: string | null;
  lignes: LigneJournee[];
  nbPatients: number;
  nbSousEffectif: number;
}

/** Jour de la semaine d'une date `yyyy-MM-dd` (la semaine commence le dimanche, comme sur le serveur). */
export function jourDeLaSemaine(date: string): JourSemaine {
  return JOURS_SEMAINE[new Date(`${date}T00:00:00Z`).getUTCDay()];
}

/**
 * Journée du médecin : croise le planning des patients et la présence des infirmiers de la semaine pour la date
 * demandée. Les lignes sans patient ni infirmier sont omises ; les lignes en sous-effectif remontent en premier.
 */
export function construireJournee(
  planning: SemainePlanning, presence: SemainePresence, date: string,
): Journee {
  const jour = jourDeLaSemaine(date);
  const jourPlanning = planning.jours.find((j) => j.jour === jour);
  const motifFermeture = jourPlanning?.fermetureMotif ?? null;
  const ferme = !!jourPlanning && (!jourPlanning.ouvertHebdomadaire || motifFermeture !== null);

  const lignes: LigneJournee[] = [];
  if (!ferme) {
    for (const creneau of planning.creneaux) {
      for (const salle of planning.salles) {
        const cellule = planning.cellules.find(
          (c) => c.salleId === salle.id && c.creneauId === creneau.id && c.jour === jour);
        const presente = presence.cases.find(
          (c) => c.salleId === salle.id && c.creneauId === creneau.id && c.jour === jour);
        const patients = cellule?.occupants ?? [];
        const infirmiers = presente?.presents ?? [];
        const absents = presente?.absents ?? [];
        if (patients.length === 0 && infirmiers.length === 0 && absents.length === 0) continue;
        lignes.push({
          salleId: salle.id, salleNom: salle.nom, creneauId: creneau.id, creneauLibelle: creneau.libelle,
          patients, infirmiers, absents, requis: presente?.requis ?? 0, statut: presente?.statut ?? null,
        });
      }
    }
  }
  // sous-effectif d'abord, puis l'ordre naturel (créneau, salle) conservé par le tri stable
  lignes.sort((a, b) => Number(b.statut === 'SOUS_EFFECTIF') - Number(a.statut === 'SOUS_EFFECTIF'));

  return {
    date, jour, ferme, motifFermeture, lignes,
    nbPatients: lignes.reduce((total, l) => total + l.patients.length, 0),
    nbSousEffectif: lignes.filter((l) => l.statut === 'SOUS_EFFECTIF').length,
  };
}
