import {TestBed} from '@angular/core/testing';
import {provideZonelessChangeDetection, signal} from '@angular/core';
import {of, throwError} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {InfirmierApiService} from '../../../core/api/infirmier-api.service';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {HORIZON_COUVERTURE, InfirmiersTableauDeBordStore, TAILLE_LECTURE} from './infirmiers-tableau-de-bord.store';

const CENTRE = 'c-1';

const alerte = (date: string, manque: number) => ({
  date, jour: 'LUNDI', salleId: 's1', creneauId: `cr-${date}-${manque}`, patients: 8, requis: 2, manque, absents: [],
});
const charge = (infirmierId: string, nom: string, total: number) => ({
  infirmierId, nom, seances: total - 1, remplacements: 1, joursAbsence: 2, total,
});
const absence = (id: string, infirmierId: string, debut: string, fin: string) => ({
  id, infirmierId, debut, fin, type: 'CONGE', motif: null,
});

describe('InfirmiersTableauDeBordStore', () => {
  let api: Record<'alertes' | 'charge' | 'listAbsences' | 'list', ReturnType<typeof vi.fn>>;

  const creer = () => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        InfirmiersTableauDeBordStore,
        {provide: InfirmierApiService, useValue: api},
        {provide: AppShellStore, useValue: {currentCenterId: signal(CENTRE)}},
      ],
    });
    return TestBed.inject(InfirmiersTableauDeBordStore);
  };

  beforeEach(() => {
    api = {
      alertes: vi.fn(() => of([alerte('2026-11-21', 1), alerte('2026-11-20', 1), alerte('2026-11-20', 2)])),
      charge: vi.fn(() => of({
        items: [charge('i1', 'Amina B', 12), charge('i2', 'Karim D', 20), charge('i3', 'Lina S', 8)],
        total: 3, page: 0, size: 100, moyenne: 13.333,
      })),
      listAbsences: vi.fn(() => of({
        items: [
          absence('a1', 'i1', '2026-11-18', '2026-11-25'),
          absence('a2', 'i2', '2026-11-19', '2026-11-19'),
          absence('a3', 'i3', '2026-11-20', '2026-11-30'),
          absence('a4', 'inconnu', '2026-11-01', '2026-11-10'),
        ], total: 4, page: 0, size: 100,
      })),
      list: vi.fn(() => of({
        items: [{id: 'i1', nom: 'B', prenom: 'Amina'}, {id: 'i2', nom: 'D', prenom: null}], total: 2, page: 0, size: 100,
      })),
    };
  });

  it('agrège les API existantes du centre actif : alertes de la semaine, charge du mois, absences et référentiel', () => {
    const store = creer();

    store.charger('2026-11-19');

    expect(api.alertes).toHaveBeenCalledWith(CENTRE, HORIZON_COUVERTURE);
    expect(api.charge).toHaveBeenCalledWith(CENTRE, '2026-11', 0, TAILLE_LECTURE);
    expect(api.listAbsences).toHaveBeenCalledWith(CENTRE, 0, TAILLE_LECTURE);
    expect(api.list).toHaveBeenCalledWith(CENTRE, 0, TAILLE_LECTURE);
    expect(store.loading()).toBe(false);
    expect(store.error()).toBeNull();
  });

  it('trie les créneaux à risque par date puis par manque décroissant et compte les infirmiers manquants', () => {
    const store = creer();

    store.charger('2026-11-19');

    expect(store.alertesTriees().map((a) => [a.date, a.manque])).toEqual([
      ['2026-11-20', 2], ['2026-11-20', 1], ['2026-11-21', 1]]);
    expect(store.infirmiersManquants()).toBe(4);
  });

  it('résume la charge du mois : effectif, moyenne arrondie, totaux, plus et moins chargés', () => {
    const store = creer();

    store.charger('2026-11-19');

    expect(store.synthese()).toMatchObject({infirmiers: 3, moyenne: 13.3, remplacements: 3, joursAbsence: 6});
    expect(store.synthese().plusCharge?.nom).toBe('Karim D');
    expect(store.synthese().moinsCharge?.nom).toBe('Lina S');
  });

  it('ne garde que les absences en cours le jour de référence, nommées, de la plus proche fin à la plus lointaine', () => {
    const store = creer();

    store.charger('2026-11-19');

    expect(store.absencesEnCours().map((a) => [a.id, a.nom])).toEqual([['a2', 'D'], ['a1', 'Amina B']]);
  });

  it('affiche les autres sources quand l une échoue, et signale l erreur', () => {
    api.charge.mockReturnValue(throwError(() => ({status: 500})));
    const store = creer();

    store.charger('2026-11-19');

    expect(store.alertes()).toHaveLength(3);
    expect(store.charge()).toEqual([]);
    expect(store.synthese().plusCharge).toBeNull();
    expect(store.error()).toBe('INFIRMIER.ERR.LOAD');
  });
});
