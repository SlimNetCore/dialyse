import {describe, expect, it} from 'vitest';
import {horizonDepuisParametres, horizonPourAbsence} from './horizon-absence.util';

describe('horizonDepuisParametres', () => {
  it('lit la période de l\'adresse et la ramène aux bornes', () => {
    expect(horizonDepuisParametres('2026-10-19', '3')).toEqual({debut: '2026-10-19', semaines: 3});
    expect(horizonDepuisParametres('2026-10-19', '99')).toEqual({debut: '2026-10-19', semaines: 8});
    expect(horizonDepuisParametres('2026-10-19', '0')).toEqual({debut: '2026-10-19', semaines: 1});
  });

  it('ignore une adresse sans période ou invalide', () => {
    expect(horizonDepuisParametres(null, '3')).toBeNull();
    expect(horizonDepuisParametres('2026-10-19', null)).toBeNull();
    expect(horizonDepuisParametres('19/10/2026', '3')).toBeNull();
    expect(horizonDepuisParametres('2026-10-19', 'abc')).toBeNull();
  });
});

// mardi 29 septembre 2026 ; la semaine du planning commence le dimanche 27 septembre
const AUJOURDHUI = '2026-09-29';

describe('horizonPourAbsence', () => {
  it('couvre une absence à venir depuis son début jusqu\'à sa fin', () => {
    expect(horizonPourAbsence('2026-10-19', '2026-11-02', AUJOURDHUI)).toEqual({debut: '2026-10-19', semaines: 3});
  });

  it('commence aujourd\'hui quand l\'absence est déjà en cours', () => {
    // du dimanche 27 septembre au samedi 10 octobre : deux semaines pleines
    expect(horizonPourAbsence('2026-09-21', '2026-10-10', AUJOURDHUI)).toEqual({debut: AUJOURDHUI, semaines: 2});
    expect(horizonPourAbsence('2026-09-21', '2026-10-11', AUJOURDHUI)).toEqual({debut: AUJOURDHUI, semaines: 3});
  });

  it('une absence de la semaine en cours tient en une semaine', () => {
    expect(horizonPourAbsence('2026-09-30', '2026-10-03', AUJOURDHUI)).toEqual({debut: '2026-09-30', semaines: 1});
  });

  it('plafonne l\'horizon aux bornes du serveur (8 semaines)', () => {
    expect(horizonPourAbsence('2026-10-01', '2027-06-30', AUJOURDHUI)).toEqual({debut: '2026-10-01', semaines: 8});
  });

  it('refuse une absence terminée ou des dates invalides', () => {
    expect(horizonPourAbsence('2026-09-01', '2026-09-10', AUJOURDHUI)).toBeNull();
    expect(horizonPourAbsence(null, '2026-10-10', AUJOURDHUI)).toBeNull();
    expect(horizonPourAbsence('2026-10-01', 'n\'importe quoi', AUJOURDHUI)).toBeNull();
  });
});
