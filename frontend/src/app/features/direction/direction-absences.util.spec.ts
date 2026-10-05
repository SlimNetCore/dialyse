import {describe, expect, it} from 'vitest';
import {AbsencesOverview, AbsencesStats, CentreAbsences} from '../../core/api/direction-api.service';
import {
  absencesHeadline,
  absencesMotifs,
  absencesRows,
  motifLabelKey,
  motifShare,
  motifsChartSeries,
} from './direction-absences.util';

const stats = (nbAbsences: number): AbsencesStats => ({
  nbAbsences, nbJustifiees: 0, nbNonJustifiees: 0, nbAQualifier: nbAbsences, nbRattrapees: 0, nbSeances: 10,
  valorisationHt: nbAbsences * 100, valorisationTtc: nbAbsences * 119, caHt: 10000, tauxAbsenteisme: 10, partCaHt: 1,
});

const centre = (id: string, nb: number): CentreAbsences => ({
  centerId: id, nom: `Centre ${id}`, actif: true, stats: stats(nb),
  motifs: [{motif: 'MALADIE', nb, valorisationHt: nb * 100}],
});

const overview: AbsencesOverview = {
  societeId: 's', from: '2026-01-01', to: '2026-12-31', generatedAt: '',
  total: stats(9), centres: [centre('a', 6), centre('b', 3)],
  motifs: [{motif: 'MALADIE', nb: 6, valorisationHt: 600}, {motif: 'VOYAGE', nb: 2, valorisationHt: 200},
    {motif: 'AUTRE', nb: null, valorisationHt: null}],
  mensuel: [],
};

describe('direction absences util', () => {
  it('lists one row per centre then the societe total', () => {
    const rows = absencesRows(overview, null);
    expect(rows.map((r) => r.nom)).toEqual(['Centre a', 'Centre b', null]);
    expect(rows[2].total).toBe(true);
    expect(rows[2].stats.nbAbsences).toBe(9);
  });

  it('keeps only the isolated centre and drops the total', () => {
    const rows = absencesRows(overview, 'b');
    expect(rows).toHaveLength(1);
    expect(rows[0].centerId).toBe('b');
    expect(rows.some((r) => r.total)).toBe(false);
  });

  it('returns nothing without data', () => {
    expect(absencesRows(null, null)).toEqual([]);
    expect(absencesHeadline(null, null)).toBeNull();
    expect(absencesMotifs(null, null)).toEqual([]);
  });

  it('headlines the total, or the isolated centre', () => {
    expect(absencesHeadline(overview, null)?.nbAbsences).toBe(9);
    expect(absencesHeadline(overview, 'a')?.nbAbsences).toBe(6);
    expect(absencesHeadline(overview, 'zzz')).toBeNull();
  });

  it('uses consolidated motifs, or those of the isolated centre', () => {
    expect(absencesMotifs(overview, null)).toHaveLength(3);
    expect(absencesMotifs(overview, 'b')).toEqual([{motif: 'MALADIE', nb: 3, valorisationHt: 300}]);
  });

  it('computes the share of a motif, never for a masked one', () => {
    expect(motifShare(overview.motifs[0], overview.motifs)).toBe(75);
    expect(motifShare(overview.motifs[2], overview.motifs)).toBeNull();
    expect(motifShare({motif: 'X', nb: 1, valorisationHt: 1}, [{motif: 'X', nb: 1, valorisationHt: 1}])).toBe(100);
    expect(motifShare({motif: 'X', nb: 0, valorisationHt: 0}, [{motif: 'X', nb: 0, valorisationHt: 0}])).toBeNull();
  });

  it('builds the chart series by number and by value, skipping masked and empty motifs', () => {
    const motifs = [...overview.motifs, {motif: 'ZERO', nb: 0, valorisationHt: 0}];

    expect(motifsChartSeries(motifs, 'nb')).toEqual([{motif: 'MALADIE', value: 6}, {motif: 'VOYAGE', value: 2}]);
    expect(motifsChartSeries(motifs, 'valeur')).toEqual([
      {motif: 'MALADIE', value: 600}, {motif: 'VOYAGE', value: 200}]);
    expect(motifsChartSeries([], 'nb')).toEqual([]);
  });

  it('gives the translation key of a motif, with a dedicated one for the unqualified', () => {
    expect(motifLabelKey('MALADIE')).toBe('ABSENCES.MOTIFS.MALADIE');
    expect(motifLabelKey('NON_QUALIFIE')).toBe('DIRECTION.ABSENCES.NON_QUALIFIE');
  });
});
