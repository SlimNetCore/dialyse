import {describe, expect, it} from 'vitest';
import {CentreGmao, EquipementCout, GmaoOverview} from '../../core/api/direction-api.service';
import {gmaoTopRows} from './direction-gmao.util';

function equip(id: string, coutPeriode: number, reforme = false): EquipementCout {
  return {
    equipementId: id, code: id, designation: id, statut: 'EN_SERVICE', coutPeriode, coutCumule: coutPeriode,
    prixAcquisition: 1000, ratioMaintenance: 0.1, indisponibiliteHeures: 0, reformeRecommandee: reforme,
  };
}

function centre(id: string, top: EquipementCout[]): CentreGmao {
  return {
    centerId: id, nom: `Centre ${id}`, nbEquipements: 1, nbHorsService: 0, nbEnMaintenance: 0, nbReformes: 0,
    interventionsEnCours: 0, coutMaintenancePeriode: 0, indisponibiliteHeuresCumulees: 0,
    patientsSurEquipementIndisponible: 0, nbReformeRecommandee: 0, topEquipements: top,
  };
}

const overview = (centres: CentreGmao[]): GmaoOverview => ({
  societeId: 's', from: '2026-01-01', to: '2026-12-31', generatedAt: '', centres,
  totaux: centre('t', []), alertes: [],
});

describe('gmaoTopRows', () => {
  const g = overview([centre('a', [equip('a1', 100), equip('a2', 500)]), centre('b', [equip('b1', 300, true)])]);

  it('merges the centres and ranks by period cost, most expensive first', () => {
    expect(gmaoTopRows(g, null).map((r) => r.e.equipementId)).toEqual(['a2', 'b1', 'a1']);
  });

  it('keeps only the isolated centre', () => {
    const rows = gmaoTopRows(g, 'b');
    expect(rows.map((r) => r.e.equipementId)).toEqual(['b1']);
    expect(rows[0].centre).toBe('Centre b');
  });

  it('returns nothing without data', () => {
    expect(gmaoTopRows(null, null)).toEqual([]);
  });
});
