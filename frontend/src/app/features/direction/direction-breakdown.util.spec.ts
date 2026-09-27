import {describe, expect, it} from 'vitest';
import {AgeRow, CaisseRow, CaisseTotal, SexeRow} from '../../core/api/direction-api.service';
import {ageSeries, pivotCaisses, sexeSeries} from './direction-breakdown.util';

const centres = [{id: 'a', nom: 'Centre A'}, {id: 'b', nom: 'Centre B'}];
const rows: CaisseRow[] = [
  {centerId: 'a', centre: 'Centre A', caisseCode: 'CNAS', caisse: 'CNAS', patients: 12, seances: 30, caHt: 1500},
  {centerId: 'b', centre: 'Centre B', caisseCode: 'CNAS', caisse: 'CNAS', patients: null, seances: 4, caHt: 200},
  {centerId: 'a', centre: 'Centre A', caisseCode: '', caisse: null, patients: 7, seances: 1, caHt: 50},
];
const totals: CaisseTotal[] = [
  {caisseCode: 'CNAS', caisse: 'CNAS Nationale', patients: 14, seances: 34, caHt: 1700},
  {caisseCode: '', caisse: null, patients: 7, seances: 1, caHt: 50},
];

describe('direction-breakdown.util', () => {
  it('croise les caisses et les centres pour chaque mesure', () => {
    const ca = pivotCaisses(centres, rows, totals, 'caHt');
    expect(ca[0]).toEqual({code: 'CNAS', nom: 'CNAS Nationale', cells: {a: 1500, b: 200}, total: 1700});
    expect(ca[1].cells).toEqual({a: 50, b: 0});
    expect(ca[1].nom).toBe('');
  });

  it('conserve les effectifs masqués (null) et met 0 pour une caisse absente du centre', () => {
    const patients = pivotCaisses(centres, rows, totals, 'patients');
    expect(patients[0].cells).toEqual({a: 12, b: null});
    expect(patients[1].cells).toEqual({a: 7, b: 0});
  });

  it('construit les séries sexe et âge (masqué = 0)', () => {
    const sexe: SexeRow[] = [{centerId: 'a', nom: 'A', masculin: 5, feminin: null, autre: 0}];
    expect(sexeSeries(sexe)).toEqual({labels: ['A'], masculin: [5], feminin: [0], autre: [0]});
    const ages: AgeRow[] = [{
      centerId: 'a',
      nom: 'A',
      tranches: [{code: '45_59', count: 10}, {code: '0_17', count: null}]
    }];
    expect(ageSeries(ages, ['0_17', '45_59'])).toEqual({labels: ['A'], series: {'0_17': [0], '45_59': [10]}});
  });
});
