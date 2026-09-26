import {describe, expect, it} from 'vitest';
import {CentreStats, DirectionAlert, MonthlyPoint} from '../../core/api/direction-api.service';
import {
  collectionLevel,
  formatHeadcount,
  formatPct,
  lastCompleteMonths,
  monthlyTotals,
  rankByRevenue,
  sortAlerts
} from './direction.util';

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

  it('masque un taux non publiable', () => {
    expect(formatPct(null)).toBe('—');
    expect(formatPct(66.7)).toBe('66.7 %');
  });

  it('liste les derniers mois écoulés, année précédente comprise', () => {
    expect(lastCompleteMonths(new Date(2026, 1, 15), 3)).toEqual(['2026-01', '2025-12', '2025-11']);
    expect(lastCompleteMonths(new Date(2026, 9, 1), 1)).toEqual(['2026-09']);
  });

  it('trie les alertes critiques en premier', () => {
    const a = (centre: string, code: string, severity: 'WARNING' | 'CRITICAL'): DirectionAlert =>
      ({centerId: centre, centre, code, severity, valeur: null});
    const sorted = sortAlerts([a('B', 'X', 'WARNING'), a('C', 'Y', 'CRITICAL'), a('A', 'Z', 'WARNING')]);
    expect(sorted.map((x) => x.centre)).toEqual(['C', 'A', 'B']);
  });
});
