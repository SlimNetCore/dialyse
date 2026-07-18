import {signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {of} from 'rxjs';
import {vi} from 'vitest';
import {TranslateModule} from '@ngx-translate/core';
import {MatSnackBar} from '@angular/material/snack-bar';
import {BackendApiService} from '../../../core/api/backend-api.service';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {AuthStore} from '../../../core/state/auth.store';
import {ArticlesStore} from '../../../core/state/referentials.store';
import {CahierStepParamedicalComponent} from './cahier-step-paramedical.component';

describe('CahierStepParamedicalComponent', () => {
  const apiMock = {
    createSeance: vi.fn(() => of({id: 'seance-1', status: 'BROUILLON', dateSeance: '2026-06-14'})),
    upsertVoletParamedical: vi.fn(() => of({id: 'volet-1', seanceId: 'seance-1', updatedAt: '2026-06-14T10:00:00Z'})),
    validateSeance: vi.fn(() => of({id: 'seance-1', status: 'VALIDEE', validatedAt: '2026-06-14T10:01:00Z'})),
  };

  const appShellMock = {
    currentCenterId: vi.fn(() => 'center-1'),
  };

  const authMock = {
    username: vi.fn(() => 'nurse-1'),
  };

  const articlesStoreMock = {
    items: signal([{id: 'article-1', label: 'Article 1'}]),
    loading: signal(false),
    error: signal<string | null>(null),
    ensureLoaded: vi.fn(),
  };

  const snackBarMock = {
    open: vi.fn(),
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [CahierStepParamedicalComponent, TranslateModule.forRoot()],
      providers: [
        {provide: BackendApiService, useValue: apiMock},
        {provide: AppShellStore, useValue: appShellMock},
        {provide: AuthStore, useValue: authMock},
        {provide: ArticlesStore, useValue: articlesStoreMock},
        {provide: MatSnackBar, useValue: snackBarMock},
      ],
    }).compileComponents();
  });

  it('initialise la date de seance et une ligne de consommation', () => {
    const fixture = TestBed.createComponent(CahierStepParamedicalComponent);
    fixture.componentInstance.patientId = 'patient-1';
    fixture.detectChanges();

    expect(fixture.componentInstance.formModel().dateSeance).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(fixture.componentInstance.formModel().consommations.length).toBe(1);
    expect(articlesStoreMock.ensureLoaded).toHaveBeenCalledWith('center-1');
  });

  it('calcule formValid a true avec les champs minimaux valides', () => {
    const fixture = TestBed.createComponent(CahierStepParamedicalComponent);
    fixture.componentInstance.patientId = 'patient-1';
    fixture.detectChanges();

    fixture.componentInstance.formModel.update((model) => ({
      ...model,
      dateSeance: '2026-06-14',
      consommations: [{articleId: 'article-1', quantite: 1}],
    }));

    expect(fixture.componentInstance.formValid()).toBe(true);
  });

  it('filtre les consommations invalides dans validConsommations', () => {
    const fixture = TestBed.createComponent(CahierStepParamedicalComponent);
    fixture.componentInstance.patientId = 'patient-1';
    fixture.detectChanges();

    fixture.componentInstance.formModel.update((model) => ({
      ...model,
      consommations: [
        {articleId: 'article-1', quantite: 2},
        {articleId: '   ', quantite: 1},
        {articleId: 'article-2', quantite: 0},
      ],
    }));

    const result = (fixture.componentInstance as any).validConsommations();
    expect(result).toEqual([{articleId: 'article-1', quantite: 2}]);
  });

  it('execute la chaine save createSeance -> upsert -> validate', async () => {
    const fixture = TestBed.createComponent(CahierStepParamedicalComponent);
    fixture.componentInstance.patientId = 'patient-1';
    fixture.detectChanges();

    fixture.componentInstance.formModel.update((model) => ({
      ...model,
      dateSeance: '2026-06-14',
      consommations: [{articleId: 'article-1', quantite: 1.5}],
      taAvant: '120/80',
      taApres: '110/70',
    }));

    fixture.componentInstance.save();
    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(apiMock.createSeance).toHaveBeenCalledTimes(1);
    expect(apiMock.upsertVoletParamedical).toHaveBeenCalledTimes(1);
    expect(apiMock.validateSeance).toHaveBeenCalledTimes(1);
    expect(apiMock.validateSeance).toHaveBeenCalledWith(
      'seance-1',
      expect.objectContaining({
        centerId: 'center-1',
        userId: 'nurse-1',
        consommations: [{articleId: 'article-1', quantite: 1.5}],
      }),
    );
  });
});

