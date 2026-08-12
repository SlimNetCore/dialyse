import {signal} from '@angular/core';
import {By} from '@angular/platform-browser';
import {TestBed} from '@angular/core/testing';
import {ActivatedRoute} from '@angular/router';
import {MatSnackBar} from '@angular/material/snack-bar';
import {TranslateModule} from '@ngx-translate/core';
import {of} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {BackendApiService} from '../../../core/api/backend-api.service';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {AuthStore} from '../../../core/state/auth.store';
import {PatientStatsComponent} from '../patient-stats.component';
import {CahierStepStatsComponent} from './cahier-step-stats.component';

describe('CahierStepStatsComponent integration', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CahierStepStatsComponent, TranslateModule.forRoot()],
      providers: [
        {
          provide: BackendApiService,
          useValue: {
            getPatientParamedicalStats: vi.fn(() => of({
              seanceCount: 0,
              avgPoidsAvantKg: 0,
              avgPoidsApresKg: 0,
              avgUfReelleMl: 0,
              poidsEvolution: [],
              taEvolution: [],
            })),
            getPatientMedicalStats: vi.fn(() => of({
              avgHbGDl: 0,
              avgKtV: 0,
              avgFerritineNgMl: 0,
              hbTrend: [],
              epoTrend: [],
            })),
            exportPatientStats: vi.fn(),
          },
        },
        {provide: AppShellStore, useValue: {currentCenterId: signal('center-1')}},
        {provide: AuthStore, useValue: {hasRole: vi.fn(() => false)}},
        {provide: MatSnackBar, useValue: {open: vi.fn()}},
        {provide: ActivatedRoute, useValue: {snapshot: {paramMap: {get: () => null}}}},
      ],
    }).compileComponents();
  });

  it('should pass cahier patientId into app-patient-stats input', () => {
    const fixture = TestBed.createComponent(CahierStepStatsComponent);
    fixture.componentInstance.patientId = 'patient-from-cahier';
    fixture.detectChanges();

    const child = fixture.debugElement.query(By.directive(PatientStatsComponent));
    expect(child).toBeTruthy();
    expect((child.componentInstance as PatientStatsComponent).patientId()).toBe('patient-from-cahier');
  });
});

