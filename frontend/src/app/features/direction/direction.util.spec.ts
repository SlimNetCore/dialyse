import {describe, expect, it} from 'vitest';
import {CentreStats, MonthlyPoint} from '../../core/api/direction-api.service';
import {collectionLevel, formatHeadcount, monthlyTotals, rankByRevenue} from './direction.util';

const centre = (nom: string, caTtc: number): CentreStats => ({
  centerId: nom, nom, actif: true, patients: 10, patientsSousKt: 5, seances: 1, factures: 1,
  caHt: caTtc, caTtc, encaisse: 0, resteARecouvrer: caTtc, tauxEncaissement: 0,
});

describe('direction.util', () => {
  it('affiche « < seuil » pour un effectif masqué', () => {
    expect(formatHeadcount(null, 5)).toBe('< 5');
    expect(formatHeadcount(0, 5)).toBe('0');
    expect(formatHeadcount(42, 5)).toBe('42');
  });

  it("classe les centres par chiffre d'affaires décroissant", () => {
    const ranked = rankByRevenue([centre('B', 10), centre('A', 10), centre('C', 99)]);
    expect(ranked.map((c) => c.nom)).toEqual(['C', 'A', 'B']);
  });

  it("cumule les mois de tous les centres dans l'ordre chronologique", () => {
    const points: MonthlyPoint[] = [
      {mois: '2026-02', centerId: 'a', seances: 2, caHt: 0, caTtc: 20},
      {mois: '2026-01', centerId: 'a', seances: 1, caHt: 0, caTtc: 10},
      {mois: '2026-02', centerId: 'b', seances: 3, caHt: 0, caTtc: 30},
    ];
    expect(monthlyTotals(points)).toEqual([
      {mois: '2026-01', caTtc: 10, seances: 1},
      {mois: '2026-02', caTtc: 50, seances: 5},
    ]);
  });

  it("qualifie le taux d'encaissement", () => {
    expect(collectionLevel(null)).toBe('none');
    expect(collectionLevel(95)).toBe('good');
    expect(collectionLevel(60)).toBe('warn');
    expect(collectionLevel(10)).toBe('bad');
  });
});
