import {AbsencesOverview, AbsencesStats, MotifAbsenceStat} from '../../core/api/direction-api.service';

/** Ligne du tableau par centre : un centre, ou le total de la société. */
export type AbsencesRow = { centerId: string | null; nom: string | null; stats: AbsencesStats; total: boolean };

/**
 * Lignes du tableau : un centre par ligne puis le total de la société (masqué quand un centre est isolé par le
 * filtre).
 */
export function absencesRows(o: AbsencesOverview | null, selectedCentre: string | null): AbsencesRow[] {
  if (!o) return [];
  const centres = selectedCentre ? o.centres.filter((c) => c.centerId === selectedCentre) : o.centres;
  const rows: AbsencesRow[] = centres.map((c) => ({centerId: c.centerId, nom: c.nom, stats: c.stats, total: false}));
  return selectedCentre ? rows : [...rows, {centerId: null, nom: null, stats: o.total, total: true}];
}

/** Indicateurs mis en avant : ceux du centre isolé, sinon le total de la société. */
export function absencesHeadline(o: AbsencesOverview | null, selectedCentre: string | null): AbsencesStats | null {
  if (!o) return null;
  if (!selectedCentre) return o.total;
  return o.centres.find((c) => c.centerId === selectedCentre)?.stats ?? null;
}

/** Motifs du centre isolé, sinon consolidés ; les effectifs masqués (`null`) sont placés en dernier. */
export function absencesMotifs(o: AbsencesOverview | null, selectedCentre: string | null): MotifAbsenceStat[] {
  if (!o) return [];
  if (!selectedCentre) return o.motifs;
  return o.centres.find((c) => c.centerId === selectedCentre)?.motifs ?? [];
}

/** Part d'un motif dans l'ensemble des absences affichées (%), `null` si le motif ou le total n'est pas publiable. */
export function motifShare(motif: MotifAbsenceStat, motifs: MotifAbsenceStat[]): number | null {
  if (motif.nb === null) return null;
  const total = motifs.reduce((sum, m) => sum + (m.nb ?? 0), 0);
  return total > 0 ? Math.round((motif.nb / total) * 1000) / 10 : null;
}
