import {provideZonelessChangeDetection} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {throwError, of} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {PlanningApiService, SemainePlanning} from '../../core/api/planning-api.service';
import {AppShellStore} from '../../core/state/app-shell.store';
import {PlanningStore} from './planning.store';

const CENTRE = '11111111-1111-1111-1111-111111111111';

function semaine(debut: string): SemainePlanning {
  return {debut, fin: debut, jours: [], salles: [], creneaux: [], cellules: [], conflits: [], patientsAReplanifier: 0};
}

describe('PlanningStore', () => {
  let api: {
    semaine: ReturnType<typeof vi.fn>;
    parametres: ReturnType<typeof vi.fn>;
    enregistrerParametres: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    api = {
      semaine: vi.fn().mockReturnValue(of(semaine('2026-09-27'))),
      parametres: vi.fn().mockReturnValue(of({joursOuverts: ['LUNDI'], sallesIsolement: []})),
      enregistrerParametres: vi.fn().mockImplementation((_c: string, p: unknown) => of(p)),
    };
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), {provide: PlanningApiService, useValue: api}],
    });
    TestBed.inject(AppShellStore).switchCenter(CENTRE);
  });

  it('charge la semaine du centre actif puis navigue de semaine en semaine', () => {
    const store = TestBed.inject(PlanningStore);

    store.chargerSemaine(null);
    expect(api.semaine).toHaveBeenCalledWith(CENTRE, undefined);
    expect(store.semaine()?.debut).toBe('2026-09-27');

    store.changerSemaine(1);
    expect(api.semaine).toHaveBeenLastCalledWith(CENTRE, '2026-10-04');
    store.changerSemaine(-1);
    expect(api.semaine).toHaveBeenLastCalledWith(CENTRE, '2026-09-20');
  });

  it('signale une erreur de chargement de la semaine', () => {
    api.semaine.mockReturnValue(throwError(() => new Error('boom')));
    const store = TestBed.inject(PlanningStore);

    store.chargerSemaine(null);

    expect(store.semaine()).toBeNull();
    expect(store.error()).toBe('PLANNING.SEMAINE.ERR.LOAD');
    expect(store.loading()).toBe(false);
  });

  it('charge puis enregistre les paramètres pour le centre actif', () => {
    const store = TestBed.inject(PlanningStore);

    store.chargerParametres();
    expect(api.parametres).toHaveBeenCalledWith(CENTRE);
    expect(store.parametres()?.joursOuverts).toEqual(['LUNDI']);

    store.enregistrerParametres({joursOuverts: ['LUNDI', 'MARDI'], sallesIsolement: ['s1']});
    expect(api.enregistrerParametres).toHaveBeenCalledWith(CENTRE, {
      joursOuverts: ['LUNDI', 'MARDI'],
      sallesIsolement: ['s1']
    });
    expect(store.parametres()?.sallesIsolement).toEqual(['s1']);
    expect(store.successMessage()).toBe('PLANNING.PARAMS.SAVED_OK');
  });

  it('signale un échec d\'enregistrement des paramètres', () => {
    api.enregistrerParametres.mockReturnValue(throwError(() => new Error('boom')));
    const store = TestBed.inject(PlanningStore);

    store.enregistrerParametres({joursOuverts: ['LUNDI'], sallesIsolement: []});

    expect(store.saving()).toBe(false);
    expect(store.error()).toBe('PLANNING.PARAMS.ERR.SAVE');
  });
});
