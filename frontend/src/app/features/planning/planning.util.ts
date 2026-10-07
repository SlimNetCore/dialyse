import {
  CellulePlanning,
  CreneauRef,
  JourPlanning,
  JourSemaine,
  OccupantPlanning,
  SalleRef,
  SemainePlanning,
} from '../../core/api/planning-api.service';
import {AbsenceSemaine, SeanceRealisee} from '../../core/api/absence-patient-api.service';

/** Décale une date `yyyy-MM-dd` d'un nombre de jours (calcul en UTC : pas de dérive liée à l'heure d'été). */
export function decalerJours(date: string, jours: number): string {
  const [y, m, d] = date.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d + jours)).toISOString().slice(0, 10);
}

/** Un jour est fermé s'il n'est pas ouvert chaque semaine ou s'il tombe sur une fermeture datée. */
export function jourFerme(jour: JourPlanning): boolean {
  return !jour.ouvertHebdomadaire || jour.fermetureMotif !== null;
}

export function cellule(
  semaine: SemainePlanning, salleId: string, creneauId: string, jour: JourSemaine,
): CellulePlanning | undefined {
  return semaine.cellules.find((c) => c.salleId === salleId && c.creneauId === creneauId && c.jour === jour);
}

/** Cellule pleine (au moins autant d'occupants que de générateurs), vide, ou partiellement occupée. */
export function etatCellule(c: CellulePlanning | undefined): 'closed' | 'empty' | 'partial' | 'full' | 'over' {
  if (!c || c.capacite === 0) return c && c.occupants.length > 0 ? 'over' : 'closed';
  if (c.occupants.length === 0) return 'empty';
  if (c.occupants.length > c.capacite) return 'over';
  return c.occupants.length === c.capacite ? 'full' : 'partial';
}

/** Une absence ne se déclare que pour un jour ouvert, passé ou du jour (jamais pour une séance à venir). */
export function peutDeclarerAbsence(date: string, aujourdhui: string, ferme: boolean): boolean {
  return !ferme && date <= aujourdhui;
}

/**
 * Évènements du centre qui changent le planning de la semaine : séances créées, validées (scan du QR code ou saisie),
 * supprimées, absences de patients ou d'infirmiers, déplacements de patients ou de séances, placements modifiés,
 * générateur indisponible.
 */
const EVENEMENTS_PLANNING = new Set([
  'PATIENT_SCANNED', 'PATIENT_CREATED', 'PATIENT_UPDATED', 'SEANCES_DEPLACEES', 'GENERATEUR_INDISPONIBLE',
  'INFIRMIER_ABSENCE_DECLAREE', 'INFIRMIER_ABSENCE_ENREGISTREE', 'PATIENT_REPLACE_ISOLEMENT', 'ISOLEMENT_IMPOSSIBLE',
  'ABSENCES_A_QUALIFIER',
]);

/** Vrai si un évènement du serveur doit faire recharger le planning affiché. */
export function evenementRafraichitPlanning(type: string, payload: Record<string, string> = {}): boolean {
  if (type === 'SAISIE_INFIRMIER') return payload['saisie'] === 'ABSENCE';
  return type.startsWith('SEANCE_') || EVENEMENTS_PLANNING.has(type);
}

/** Absence enregistrée (non annulée) d'un patient à une date, le cas échéant. */
export function absenceDe(absences: AbsenceSemaine[], patientId: string, date: string): AbsenceSemaine | undefined {
  return absences.find((a) => a.patientId === patientId && a.dateSeance === date);
}

/** Jour affiché par défaut : aujourd'hui s'il est dans la semaine, sinon le premier jour ouvert. */
export function jourParDefaut(semaine: SemainePlanning, aujourdhui: string): JourSemaine | null {
  const jourDuJour = semaine.jours.find((j) => j.date === aujourdhui);
  if (jourDuJour) return jourDuJour.jour;
  return (semaine.jours.find((j) => !jourFerme(j)) ?? semaine.jours[0])?.jour ?? null;
}

export interface LigneJour {
  salle: SalleRef;
  creneau: CreneauRef;
  cellule: CellulePlanning;
}

/** Salles/créneaux d'un jour qui ont des patients ou des générateurs, dans l'ordre salle puis créneau. */
export function lignesDuJour(semaine: SemainePlanning, jour: JourSemaine): LigneJour[] {
  return semaine.salles.flatMap((salle) => semaine.creneaux.map((creneau) => ({
    salle, creneau, cellule: cellule(semaine, salle.id, creneau.id, jour),
  }))).filter((l): l is LigneJour => !!l.cellule && (l.cellule.occupants.length > 0 || l.cellule.capacite > 0));
}

/** Séance réalisée (validée par l'infirmier) d'un patient à une date, le cas échéant. */
export function seanceRealiseeDe(seances: SeanceRealisee[], patientId: string, date: string): SeanceRealisee | undefined {
  return seances.find((s) => s.patientId === patientId && s.dateSeance === date);
}

/**
 * Classe de couleur d'un patient dans une case : séance validée (avec contour pointillé si elle a eu lieu hors du
 * planning actuel du patient), absent selon le statut de l'absence, à risque, ou normal.
 */
export function classeOccupant(o: Pick<OccupantPlanning, 'aRisque' | 'realiseeHorsPlanning'>, realisee: boolean,
                               absence: Pick<AbsenceSemaine, 'statut'> | undefined): string {
  if (realisee) return o.realiseeHorsPlanning ? 'done hors' : 'done';
  if (absence) return `absent ${absence.statut.toLowerCase()}`;
  return o.aRisque ? 'risk' : '';
}

