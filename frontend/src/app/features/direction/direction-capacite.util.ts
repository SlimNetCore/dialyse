import {CapaciteOverview, LigneCapacite} from '../../core/api/direction-api.service';

/** Ligne du tableau : un centre, ou le total de la société. */
export type CapaciteRow = { centerId: string | null; nom: string | null; ligne: LigneCapacite; total: boolean };

/** Un centre par ligne puis le total (masqué quand un centre est isolé par le filtre). */
export function capaciteRows(o: CapaciteOverview | null, selectedCentre: string | null): CapaciteRow[] {
  if (!o) return [];
  const centres = selectedCentre ? o.centres.filter((c) => c.centerId === selectedCentre) : o.centres;
  const rows: CapaciteRow[] = centres.map((c) => ({centerId: c.centerId, nom: c.nom, ligne: c.capacite, total: false}));
  return selectedCentre ? rows : [...rows, {centerId: null, nom: null, ligne: o.total, total: true}];
}

/** Capacité mise en avant : celle du centre isolé, sinon le total de la société. */
export function capaciteHeadline(o: CapaciteOverview | null, selectedCentre: string | null): LigneCapacite | null {
  if (!o) return null;
  if (!selectedCentre) return o.total;
  return o.centres.find((c) => c.centerId === selectedCentre)?.capacite ?? null;
}

/** Largeur de la jauge d'occupation (0 à 100), nulle quand le taux n'est pas publiable. */
export function jaugeCapacite(ligne: Pick<LigneCapacite, 'tauxOccupation'>): number {
  return ligne.tauxOccupation === null ? 0 : Math.max(0, Math.min(100, ligne.tauxOccupation));
}

/** Nombre de centres ayant atteint ou dépassé leur capacité théorique. */
export function nbCentresAtteints(o: CapaciteOverview | null): number {
  return o ? o.centres.filter((c) => c.capacite.atteinte).length : 0;
}

/**
 * Centre servant d'exemple chiffré à la méthode de calcul : le centre isolé, sinon le premier centre disposant d'une
 * capacité (les totaux n'ont pas de nombre de séries propre).
 */
export function capaciteExemple(
  o: CapaciteOverview | null, selectedCentre: string | null,
): { nom: string; ligne: LigneCapacite } | null {
  if (!o) return null;
  const candidats = selectedCentre ? o.centres.filter((c) => c.centerId === selectedCentre) : o.centres;
  const centre = candidats.find((c) => c.capacite.capacite > 0);
  return centre ? {nom: centre.nom, ligne: centre.capacite} : null;
}
