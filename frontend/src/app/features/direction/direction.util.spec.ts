import {describe, expect, it} from 'vitest';
import {CentreStats, DirectionAlert, MonthlyPoint} from '../../core/api/direction-api.service';
import {
  buildDashboardCsv,
  collectionLevel,
  delta,
  filterByCentre,
  formatHeadcount,
  formatPct,
  kdigoLevel,
  lastCompleteMonths,
  monthlyTotals,
  periodForPreset,
  rankByRevenue,
  rankIndex,
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

  it('calcule le delta par rapport à la période précédente', () => {
    expect(delta(110, 100)).toEqual({pct: 10, direction: 'up'});
    expect(delta(90, 100)).toEqual({pct: -10, direction: 'down'});
    expect(delta(100, 100)).toEqual({pct: 0, direction: 'flat'});
    expect(delta(0, 0)).toEqual({pct: 0, direction: 'flat'});
    expect(delta(50, 0)).toEqual({pct: null, direction: 'up'});
  });

  it('calcule les bornes des raccourcis de période', () => {
    const now = new Date(2026, 8, 27); // 27/09/2026
    expect(periodForPreset('MONTH', now)).toEqual({from: '2026-09-01', to: '2026-09-27'});
    expect(periodForPreset('QUARTER', now)).toEqual({from: '2026-07-01', to: '2026-09-27'});
    expect(periodForPreset('YEAR', now)).toEqual({from: '2026-01-01', to: '2026-09-27'});
    expect(periodForPreset('LAST_12_MONTHS', now)).toEqual({from: '2025-10-01', to: '2026-09-27'});
  });

  it('isole les lignes du centre filtré, ou les garde toutes sans filtre', () => {
    const rows = [{centerId: 'a', v: 1}, {centerId: 'b', v: 2}];
    expect(filterByCentre(rows, null)).toEqual(rows);
    expect(filterByCentre(rows, 'b')).toEqual([{centerId: 'b', v: 2}]);
    expect(filterByCentre(rows, 'inconnu')).toEqual([]);
    // une liste filtrée est une copie : la modifier ne doit pas toucher la source
    const copy = filterByCentre(rows, null);
    copy.pop();
    expect(rows).toHaveLength(2);
  });

  it('indexe le rang de chaque centre par CA (1 = premier)', () => {
    const ranked = rankByRevenue([centre('B', 10), centre('A', 10), centre('C', 99)]);
    expect(rankIndex(ranked)).toEqual({C: 1, A: 2, B: 3});
  });

  it('assemble plusieurs tableaux en un seul CSV, séparés par une ligne vide', () => {
    const csv = buildDashboardCsv([
      {title: 'Finances', headers: ['Centre', 'CA'], rows: [['Alpha', 1000], ['Beta; Gamma', null]]},
      {title: 'Vide', headers: ['X'], rows: []}, // section sans ligne : omise
      {title: 'Stock', headers: ['Article'], rows: [['Compresses "stériles"']]},
    ]);
    expect(csv).toBe(
      'Finances\r\n' +
      'Centre;CA\r\n' +
      'Alpha;1000\r\n' +
      '"Beta; Gamma";\r\n' +
      '\r\n' +
      'Stock\r\n' +
      'Article\r\n' +
      '"Compresses ""stériles"""\r\n' +
      ''
    );
  });

  it('qualifie un marqueur KDIGO (part des patients dans la cible)', () => {
    expect(kdigoLevel(null)).toBe('none');
    expect(kdigoLevel(66)).toBe('good');
    expect(kdigoLevel(90)).toBe('good');
    expect(kdigoLevel(45)).toBe('warn');
    expect(kdigoLevel(65.9)).toBe('warn');
    expect(kdigoLevel(44.9)).toBe('bad');
    expect(kdigoLevel(0)).toBe('bad');
  });

  it('trie les alertes critiques en premier', () => {
    const a = (centre: string, code: string, severity: 'WARNING' | 'CRITICAL'): DirectionAlert =>
      ({centerId: centre, centre, code, severity, valeur: null});
    const sorted = sortAlerts([a('B', 'X', 'WARNING'), a('C', 'Y', 'CRITICAL'), a('A', 'Z', 'WARNING')]);
    expect(sorted.map((x) => x.centre)).toEqual(['C', 'A', 'B']);
  });
});
