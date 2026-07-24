import {ActivatedRoute, convertToParamMap, Router} from '@angular/router';
import {TestBed} from '@angular/core/testing';
import {of} from 'rxjs';
import {vi} from 'vitest';
import {TranslateModule} from '@ngx-translate/core';
import {BackendApiService} from '../../../core/api/backend-api.service';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {AuthStore} from '../../../core/state/auth.store';
import {CahierDialyseComponent} from './cahier-dialyse.component';

describe('CahierDialyseComponent', () => {
  const apiMock = {
    listSeances: vi.fn(() =>
      of([
        {id: 's-1', centerId: 'center-1', patientId: 'patient-1', dateSeance: '2026-07-01', status: 'BROUILLON'},
        {id: 's-2', centerId: 'center-1', patientId: 'patient-1', dateSeance: '2026-07-14', status: 'VALIDEE'},
        {id: 's-3', centerId: 'center-1', patientId: 'patient-2', dateSeance: '2026-07-10', status: 'BROUILLON'},
        {id: 's-4', centerId: 'center-2', patientId: 'patient-1', dateSeance: '2026-07-12', status: 'BROUILLON'},
      ])
    ),
    getSeanceSummary: vi.fn((seanceId: string) =>
      of({
        seance: {
          id: seanceId,
          centerId: 'center-1',
          patientId: 'patient-1',
          dateSeance: seanceId === 's-2' ? '2026-07-14' : '2026-07-01',
          status: seanceId === 's-2' ? 'VALIDEE' : 'BROUILLON',
        },
        patient: {id: 'patient-1'},
        paramedical: seanceId === 's-2' ? {taAvant: '120/80'} : null,
        medical: null,
      })
    ),
  };

  const routeMock = {
    snapshot: {
      paramMap: convertToParamMap({id: 'patient-1'}),
    },
  };

  const authMock = {
    hasRole: vi.fn(() => true),
  };

  const appShellMock = {
    currentCenterId: vi.fn(() => 'center-1'),
  };

  const routerMock = {
    navigate: vi.fn(),
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [CahierDialyseComponent, TranslateModule.forRoot()],
      providers: [
        {provide: BackendApiService, useValue: apiMock},
        {provide: ActivatedRoute, useValue: routeMock},
        {provide: AuthStore, useValue: authMock},
        {provide: AppShellStore, useValue: appShellMock},
        {provide: Router, useValue: routerMock},
      ],
    })
      .overrideComponent(CahierDialyseComponent, {
        set: {template: '<div></div>'},
      })
      .compileComponents();
  });

  it('charge uniquement les seances du patient dans le centre actif', () => {
    const fixture = TestBed.createComponent(CahierDialyseComponent);
    fixture.detectChanges();

    expect(apiMock.listSeances).toHaveBeenCalledWith('center-1');
    expect(fixture.componentInstance.patientSeances().map((item) => item.id)).toEqual(['s-2', 's-1']);
    expect(fixture.componentInstance.currentPageIndex()).toBe(0);
  });

  it('charge le resume de la seance active puis change en navigation de page', () => {
    const fixture = TestBed.createComponent(CahierDialyseComponent);
    fixture.detectChanges();

    expect(apiMock.getSeanceSummary).toHaveBeenCalledWith('s-2', 'center-1');
    expect(fixture.componentInstance.selectedSeance()?.id).toBe('s-2');

    fixture.componentInstance.goToNextPage();

    expect(fixture.componentInstance.selectedSeance()?.id).toBe('s-1');
    expect(apiMock.getSeanceSummary).toHaveBeenCalledWith('s-1', 'center-1');
  });
});

