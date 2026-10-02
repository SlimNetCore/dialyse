import {
  AbsenceInfirmier,
  AffectationInfirmier,
  CasePresence,
  SemainePresence,
  StatutCasePresence,
} from '../../core/api/infirmier-api.service';
import {JOURS_SEMAINE, JourSemaine} from '../../core/api/planning-api.service';

export function trouverCase(
  semaine: SemainePresence, salleId: string, creneauId: string, jour: JourSemaine,
): CasePresence | undefined {
  return semaine.cases.find((c) => c.salleId === salleId && c.creneauId === creneauId && c.jour === jour);
}

/** Classe CSS d'une case : fermé, sans patient, couvert ou en sous-effectif. */
export function classeStatut(statut: StatutCasePresence | undefined): string {
  switch (statut) {
    case 'SOUS_EFFECTIF':
      return 'under';
    case 'COUVERT':
      return 'covered';
    case 'SANS_PATIENT':
      return 'idle';
    default:
      return 'closed';
  }
}

/** Mois `yyyy-MM` décalé d'un nombre de mois (calcul pur, sans fuseau). */
export function decalerMois(mois: string, delta: number): string {
  const [annee, m] = mois.split('-').map(Number);
  const index = annee * 12 + (m - 1) + delta;
  return `${Math.floor(index / 12)}-${String((index % 12) + 1).padStart(2, '0')}`;
}

/** Mois courant `yyyy-MM` en UTC. */
export function moisCourant(maintenant: Date = new Date()): string {
  return maintenant.toISOString().slice(0, 7);
}

/** Jour courant `yyyy-MM-dd` en UTC (même référence que le serveur). */
export function aujourdhuiUtc(maintenant: Date = new Date()): string {
  return maintenant.toISOString().slice(0, 10);
}

/** Une absence n'est retirable par l'infirmier que si elle n'a pas encore commencé (le serveur reste juge). */
export function absenceAnnulable(absence: Pick<AbsenceInfirmier, 'debut'>, aujourdhui: string): boolean {
  return absence.debut > aujourdhui;
}

/** Jours d'une affectation dans l'ordre de la semaine (dimanche → samedi). */
export function joursOrdonnes(jours: readonly JourSemaine[]): JourSemaine[] {
  return JOURS_SEMAINE.filter((j) => jours.includes(j));
}

/** Deux affectations de l'infirmier se chevauchent-elles (même créneau, un jour en commun) ? */
export function chevauche(a: Pick<AffectationInfirmier, 'creneauId' | 'jours'>,
                          autres: readonly Pick<AffectationInfirmier, 'creneauId' | 'jours'>[]): boolean {
  return autres.some((b) => b.creneauId === a.creneauId && b.jours.some((j) => a.jours.includes(j)));
}
