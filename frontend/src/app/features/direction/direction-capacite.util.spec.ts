import {describe, expect, it} from 'vitest';
import {CapaciteOverview, LigneCapacite} from '../../core/api/direction-api.service';
import {
  capaciteExemple, capaciteHeadline, capaciteRows, jaugeCapacite, nbCentresAtteints, niveauClasse,
} from './direction-capacite.util';

// une file nulle = centre masqué : capacité, niveau et « atteinte » sont masqués avec elle (revue P6, KB-PLN-75)
const ligne = (capacite: number, fileActive: number | null, atteinte = false): LigneCapacite => ({
  generateurs: 16, generateursSecours: 2, postesActifs: 14, series: 2, patientsParPosteEtSerie: 3,
  capacite: fileActive === null ? null : capacite, fileActive,
  tauxOccupation: fileActive === null ? null : Math.round((fileActive / capacite) * 1000) / 10,
  niveau: fileActive === null ? null : atteinte ? 'ATTEINTE' : 'MARGE', atteinte: fileActive === null ? null : atteinte,
});

const overview: CapaciteOverview = {
  societeId: 's', from: '2026-01-01', to: '2026-12-31', generatedAt: '',
  regle: {generateursParSecours: 8, seuilProchePourcent: 90},
  // le centre B (file de 1 à 4) est masqué : sa file est nulle et il est exclu du total consolidé (KB-PLN-75)
  total: ligne(84, 84, true),
  centres: [
    {centerId: 'a', nom: 'Centre A', actif: true, capacite: ligne(84, 84, true)},
    {centerId: 'b', nom: 'Centre B', actif: true, capacite: ligne(21, null)},
  ],
  centresMasques: 1,
};

describe('direction capacité util', () => {
  it('liste un centre par ligne puis le total de la société', () => {
    const rows = capaciteRows(overview, null);
    expect(rows.map((r) => r.nom)).toEqual(['Centre A', 'Centre B', null]);
    expect(rows[2].total).toBe(true);
  });

  it('ne garde que le centre isolé, sans total', () => {
    const rows = capaciteRows(overview, 'b');
    expect(rows).toHaveLength(1);
    expect(rows[0].centerId).toBe('b');
  });

  it('met en avant le total ou le centre isolé', () => {
    expect(capaciteHeadline(overview, null)?.capacite).toBe(84);
    expect(capaciteHeadline(overview, null)?.fileActive).toBe(84);
    expect(overview.centresMasques).toBe(1);
    expect(capaciteHeadline(overview, 'a')?.capacite).toBe(84);
    expect(capaciteHeadline(overview, 'zzz')).toBeNull();
    expect(capaciteHeadline(null, null)).toBeNull();
    expect(capaciteRows(null, null)).toEqual([]);
  });

  it('borne la jauge entre 0 et 100 et la masque sans taux', () => {
    expect(jaugeCapacite({tauxOccupation: 50})).toBe(50);
    expect(jaugeCapacite({tauxOccupation: 130})).toBe(100);
    expect(jaugeCapacite({tauxOccupation: null})).toBe(0);
  });

  it('compte les centres ayant atteint leur capacité', () => {
    expect(nbCentresAtteints(overview)).toBe(1);
    expect(nbCentresAtteints(null)).toBe(0);
  });
});

describe('centre masqué', () => {
  it('masque capacité, niveau et « atteinte » avec la file, et le compte pas parmi les centres atteints', () => {
    const b = overview.centres[1].capacite;
    expect(b.fileActive).toBeNull();
    expect(b.capacite).toBeNull();
    expect(b.niveau).toBeNull();
    expect(b.atteinte).toBeNull();
    expect(niveauClasse(b)).toBe('masque');
    expect(niveauClasse(overview.centres[0].capacite)).toBe('atteinte');
    expect(nbCentresAtteints(overview)).toBe(1);
  });
});

describe('capaciteExemple', () => {
  it('prend le premier centre ayant une capacité, ou le centre isolé', () => {
    expect(capaciteExemple(overview, null)?.nom).toBe('Centre A');
    expect(capaciteExemple(overview, 'b')).toBeNull(); // centre masqué : pas de capacité à citer
    expect(capaciteExemple(overview, 'zzz')).toBeNull();
    expect(capaciteExemple(null, null)).toBeNull();
  });

  it('ignore les centres sans capacité', () => {
    const sans: CapaciteOverview = {
      ...overview,
      centres: [{
        centerId: 'c',
        nom: 'Vide',
        actif: true,
        capacite: {...ligne(0, 0), capacite: 0, niveau: 'SANS_CAPACITE'}
      }],
    };
    expect(capaciteExemple(sans, null)).toBeNull();
  });
});
