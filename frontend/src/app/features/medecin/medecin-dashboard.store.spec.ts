import {provideZonelessChangeDetection} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {of, throwError} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {InfirmierApiService, SemainePresence} from '../../core/api/infirmier-api.service';
import {PlanningApiService, SemainePlanning} from '../../core/api/planning-api.service';
import {AppShellStore} from '../../core/state/app-shell.store';
import {aujourdhuiUtc} from '../infirmier/presence.util';
import {MedecinDashboardStore} from './medecin-dashboard.store';

const CENTRE = '11111111-1111-1111-1111-111111111111';

const planning: SemainePlanning = {
  debut: '2026-09-27', fin: '2026-10-03', jours: [], salles: [], creneaux: [], cellules: [], conflits: [],
  patientsAReplanifier: 0,
};
const presence: SemainePresence = {
  debut: '2026-09-27', fin: '2026-10-03', jours: [], salles: [], creneaux: [], cases: [], conflits: [],
  patientsParInfirmier: 4, casesSousEffectif: 0,
};

describe('MedecinDashboardStore', () => {
  let planningApi: { semaine: ReturnType<typeof vi.fn> };
  let infirmierApi: { semaine: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    planningApi = {semaine: vi.fn().mockReturnValue(of(planning))};
    infirmierApi = {semaine: vi.fn().mockReturnValue(of(presence))};
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        {provide: PlanningApiService, useValue: planningApi},
        {provide: InfirmierApiService, useValue: infirmierApi},
      ],
    });
    TestBed.inject(AppShellStore).switchCenter(CENTRE);
  });

  it('charge le planning des patients et la présence des infirmiers du jour pour le centre actif', () => {
    const store = TestBed.inject(MedecinDashboardStore);

    store.charger();

    const aujourdhui = aujourdhuiUtc();
    expect(planningApi.semaine).toHaveBeenCalledWith(CENTRE, aujourdhui);
    expect(infirmierApi.semaine).toHaveBeenCalledWith(CENTRE, aujourdhui);
    expect(store.loading()).toBe(false);
    expect(store.journee()?.date).toBe(aujourdhui);
  });

  it('signale une erreur si l\'une des deux lectures échoue', () => {
    infirmierApi.semaine.mockReturnValue(throwError(() => new Error('boom')));
    const store = TestBed.inject(MedecinDashboardStore);

    store.charger();

    expect(store.error()).toBe('MEDECIN.DASHBOARD.ERR.LOAD');
    expect(store.journee()).toBeNull();
    expect(store.loading()).toBe(false);
  });
});
