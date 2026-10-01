import {EquipementCout, GmaoOverview} from '../../core/api/direction-api.service';

export type GmaoTopRow = { centre: string | null; e: EquipementCout };

/**
 * Classement des équipements les plus coûteux (période), tous centres confondus ou limité au centre isolé,
 * du plus coûteux au moins coûteux.
 */
export function gmaoTopRows(g: GmaoOverview | null, selectedCentre: string | null): GmaoTopRow[] {
  if (!g) return [];
  return g.centres
    .filter((c) => !selectedCentre || c.centerId === selectedCentre)
    .flatMap((c) => c.topEquipements.map((e) => ({centre: c.nom, e})))
    .sort((a, b) => b.e.coutPeriode - a.e.coutPeriode);
}
