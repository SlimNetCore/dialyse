import {signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {MatSnackBar} from '@angular/material/snack-bar';
import {TranslateModule} from '@ngx-translate/core';
import {of} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {ActivatedRoute} from '@angular/router';
import {BackendApiService} from '../../core/api/backend-api.service';
import {AppShellStore} from '../../core/state/app-shell.store';
import {AuthStore} from '../../core/state/auth.store';
import {PatientStatsComponent} from './patient-stats.component';

describe('PatientStatsComponent', () => {
  const getPatientParamedicalStats = vi.fn((_centerId: string, _patientId: string, _from: string, _to: string) => of({
    seanceCount: 2,
    avgPoidsAvantKg: 71.2,
    avgPoidsApresKg: 69.9,
    avgUfReelleMl: 1400,
    poidsEvolution: [
      {date_seance: '2026-03-01', poids_avant_kg: 72, poids_apres_kg: 70, uf_reelle_ml: 2000},
      {date_seance: '2026-03-15', poids_avant_kg: 70.4, poids_apres_kg: 69.8, uf_reelle_ml: 800},
    ],
    taEvolution: [
      {
        date_seance: '2026-03-01',
        ta_systolique_avant: 150,
        ta_diastolique_avant: 90,
        ta_systolique_apres: 138,
        ta_diastolique_apres: 82,
      },
      {
        date_seance: '2026-03-15',
        ta_systolique_avant: 145,
        ta_diastolique_avant: 88,
        ta_systolique_apres: 136,
        ta_diastolique_apres: 80,
      },
    ],
  }));

  const getPatientMedicalStats = vi.fn((_centerId: string, _patientId: string, _from: string, _to: string) => of({
    avgHbGDl: 10.8,
    avgKtV: 1.2,
    avgFerritineNgMl: 320,
    hbTrend: [
      {date_prelevement: '2026-03-01', hb_g_dl: 10.5, kt_v_mensuel: 1.1, ferritine_ng_ml: 300},
      {date_prelevement: '2026-04-01', hb_g_dl: 11.1, kt_v_mensuel: 1.3, ferritine_ng_ml: 340},
    ],
    epoTrend: [],
  }));

  const apiMock = {
    getPatientParamedicalStats,
    getPatientMedicalStats,
    exportPatientStats: vi.fn(),
  };

  const appShellMock = {
    currentCenterId: signal('center-1'),
  };

  const authMock = {
    hasRole: vi.fn(() => true),
  };

  const snackBarMock = {open: vi.fn()};

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [PatientStatsComponent, TranslateModule.forRoot()],
      providers: [
        {provide: BackendApiService, useValue: apiMock},
        {provide: AppShellStore, useValue: appShellMock},
        {provide: AuthStore, useValue: authMock},
        {provide: MatSnackBar, useValue: snackBarMock},
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {paramMap: {get: (key: string) => (key === 'id' ? 'patient-route' : null)}},
          },
        },
      ],
    }).compileComponents();
  });

  it('should build poids and tension charts from cahier-derived stats data', () => {
    const fixture = TestBed.createComponent(PatientStatsComponent);
    fixture.detectChanges();

    const component = fixture.componentInstance;
    expect(component.poidsChart().hasData).toBe(true);
    expect(component.taChart().hasData).toBe(true);
    expect(component.taChart().pointsByKey['ta_systolique_avant']).toContain(',');
    expect(component.ufBars()[0]?.label).toBe('01/03/2026');
  });

  it('should use patientId input when component is mounted from cahier step', () => {
    const fixture = TestBed.createComponent(PatientStatsComponent);
    fixture.componentRef.setInput('patientId', 'patient-cahier');
    fixture.detectChanges();

    const lastParamedicalCall = getPatientParamedicalStats.mock.calls.at(-1);
    expect(lastParamedicalCall?.[1]).toBe('patient-cahier');
    expect(fixture.componentInstance.patientId()).toBe('patient-cahier');
  });
});
