import {computed, signal} from '@angular/core';
import {describe, expect, it} from 'vitest';
import {IndicateursOptimisation, VacationPlanifiee} from '../../../core/api/planning-optimisation-api.service';
import {
  creerPaginationLocale,
  horizonLibre,
  lignesIndicateurs,
  placePatients,
  payloadPreference,
  planifieInfirmiers,
  proposeTemporaires,
  vacationsNouvelles,
} from './optimisation.util';

function indicateurs(overrides: Partial<IndicateursOptimisation> = {}): IndicateursOptimisation {
  return {
    generateursUtilises: 10, sallesOuvertes: 12, vacationsRequises: 20, placesInfirmierInutilisees: 8,
    patientsNonPlaces: 0, vacationsNonPourvues: 3, infirmiersMobilises: 6, ecartCharge: 4, depassementsHebdo: 1,
    ...overrides,
  };
}

describe('lignesIndicateurs', () => {
  it('marque une baisse comme une amélioration et une hausse comme une dégradation', () => {
    const lignes = lignesIndicateurs(indicateurs(), indicateurs({vacationsRequises: 16, sallesOuvertes: 13}));

    const parCle = Object.fromEntries(lignes.map((l) => [l.cle, l]));
    expect(parCle['vacationsRequises']).toMatchObject({avant: 20, apres: 16, ecart: -4, tendance: 'mieux'});
    expect(parCle['sallesOuvertes']).toMatchObject({ecart: 1, tendance: 'pire'});
    expect(parCle['generateursUtilises']).toMatchObject({ecart: 0, tendance: 'egal'});
  });

  it('traite les infirmiers mobilisés comme neutres : ni bon ni mauvais en soi', () => {
    const lignes = lignesIndicateurs(indicateurs(), indicateurs({infirmiersMobilises: 3}));

    expect(lignes.find((l) => l.cle === 'infirmiersMobilises')).toMatchObject({ecart: -3, tendance: 'neutre'});
  });

  it('couvre les neuf indicateurs du serveur, dans un ordre stable', () => {
    expect(lignesIndicateurs(indicateurs(), indicateurs()).map((l) => l.cle)).toEqual([
      'generateursUtilises', 'sallesOuvertes', 'vacationsRequises', 'placesInfirmierInutilisees', 'patientsNonPlaces',
      'vacationsNonPourvues', 'infirmiersMobilises', 'ecartCharge', 'depassementsHebdo',
    ]);
  });
});

describe('périmètres', () => {
  it('indique ce que chaque périmètre planifie', () => {
    expect(placePatients('PATIENTS')).toBe(true);
    expect(placePatients('COMPLET')).toBe(true);
    expect(placePatients('ROULEMENT')).toBe(false);
    expect(planifieInfirmiers('PATIENTS')).toBe(false);
    expect(planifieInfirmiers('COUVERTURE')).toBe(true);
    expect(horizonLibre('COUVERTURE')).toBe(true);
    expect(horizonLibre('COMPLET')).toBe(false);
  });

  it('traite la maintenance comme datée, sans patient à replacer ni infirmier à planifier', () => {
    expect(horizonLibre('MAINTENANCE')).toBe(true);
    expect(placePatients('MAINTENANCE')).toBe(false);
    expect(planifieInfirmiers('MAINTENANCE')).toBe(false);
    expect(proposeTemporaires('MAINTENANCE')).toBe(true);
    expect(proposeTemporaires('COUVERTURE')).toBe(false);
  });
});

describe('payloadPreference', () => {
  it('envoie un créneau nul quand il n\'y a pas de préférence et les séances seulement si les jours sont à choisir', () => {
    expect(payloadPreference({creneauPrefereId: '', joursAChoisir: false, seancesParSemaine: 3}))
      .toEqual({creneauPrefereId: null, joursAChoisir: false, seancesParSemaine: null});
    expect(payloadPreference({creneauPrefereId: 'c1', joursAChoisir: true, seancesParSemaine: '2' as unknown as number}))
      .toEqual({creneauPrefereId: 'c1', joursAChoisir: true, seancesParSemaine: 2});
  });
});

describe('vacationsNouvelles', () => {
  it('ne garde que les vacations que la proposition ajoute', () => {
    const v = (existante: boolean): VacationPlanifiee => ({
      date: '2026-09-28', jour: 'LUNDI', salleId: 's', creneauId: 'c', infirmierId: 'i', nom: 'N', existante,
    });

    expect(vacationsNouvelles([v(true), v(false), v(false)])).toHaveLength(2);
  });
});

describe('creerPaginationLocale', () => {
  const source = signal<number[]>(Array.from({length: 25}, (_, i) => i));

  it('découpe la liste en pages', () => {
    const pagination = creerPaginationLocale(source, 10);

    expect(pagination.total()).toBe(25);
    expect(pagination.page()).toHaveLength(10);
    pagination.aller(2, 10);
    expect(pagination.page()).toEqual([20, 21, 22, 23, 24]);
    pagination.aller(0, 20);
    expect(pagination.page()).toHaveLength(20);
  });

  it('ramène à la dernière page quand la liste rétrécit', () => {
    const liste = signal<number[]>(Array.from({length: 25}, (_, i) => i));
    const pagination = creerPaginationLocale(computed(() => liste()), 10);
    pagination.aller(2, 10);

    liste.set([1, 2, 3]);

    expect(pagination.pageIndex()).toBe(0);
    expect(pagination.page()).toEqual([1, 2, 3]);
  });

  it('gère une liste vide', () => {
    const pagination = creerPaginationLocale(signal<number[]>([]));

    expect(pagination.total()).toBe(0);
    expect(pagination.page()).toEqual([]);
    expect(pagination.pageIndex()).toBe(0);
  });
});
