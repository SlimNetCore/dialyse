import {ActivatedRoute, convertToParamMap, Router} from '@angular/router';
import {TestBed} from '@angular/core/testing';
import {of} from 'rxjs';
import {vi} from 'vitest';
import {BackendApiService} from '../../../core/api/backend-api.service';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {CahierDialyseComponent} from './cahier-dialyse.component';

describe('CahierDialyseComponent', () => {
  const apiMock = {
    listSeances: vi.fn(() =>
      of([
        {id: 's-5', centerId: 'center-1', patientId: 'patient-1', dateSeance: '2026-07-20', status: 'FACTUREE'},
        {id: 's-1', centerId: 'center-1', patientId: 'patient-1', dateSeance: '2026-07-01', status: 'SIGNEE'},
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
          dateSeance: seanceId === 's-5' ? '2026-07-20' : seanceId === 's-2' ? '2026-07-14' : '2026-07-01',
          status: seanceId === 's-5' ? 'FACTUREE' : seanceId === 's-2' ? 'VALIDEE' : 'SIGNEE',
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

  const appShellMock = {
    currentCenterId: vi.fn(() => 'center-1'),
  };

  const routerMock = {
    navigate: vi.fn(),
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    TestBed.configureTestingModule({
      providers: [
        {provide: BackendApiService, useValue: apiMock},
        {provide: ActivatedRoute, useValue: routeMock},
        {provide: AppShellStore, useValue: appShellMock},
        {provide: Router, useValue: routerMock},
      ],
    });
  });

  function createComponent(): CahierDialyseComponent {
    return TestBed.runInInjectionContext(() => new CahierDialyseComponent());
  }

  it('charge les seances validees, signees et facturees du patient dans le centre actif', () => {
    const component = createComponent();
    component.ngOnInit();

    expect(apiMock.listSeances).toHaveBeenCalledWith('center-1');
    expect(component.patientSeances().map((item) => item.id)).toEqual(['s-5', 's-2', 's-1']);
    expect(component.currentPageIndex()).toBe(0);
  });

  it('charge le resume de la seance active puis change en navigation de page', () => {
    const component = createComponent();
    component.ngOnInit();

    expect(apiMock.getSeanceSummary).toHaveBeenCalledWith('s-5', 'center-1');
    expect(component.selectedSeance()?.id).toBe('s-5');

    component.goToNextPage();

    expect(component.selectedSeance()?.id).toBe('s-2');
    expect(apiMock.getSeanceSummary).toHaveBeenCalledWith('s-2', 'center-1');
  });

  it('applique un style dedie aux seances facturees', () => {
    const component = createComponent();

    expect(component.statusClass('FACTUREE')).toBe('status-facturee');
  });
});

