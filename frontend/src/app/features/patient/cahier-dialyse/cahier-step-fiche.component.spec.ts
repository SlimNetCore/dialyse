import {TestBed} from '@angular/core/testing';
import {of} from 'rxjs';
import {vi, describe, it, expect, beforeEach} from 'vitest';
import {CahierStepFicheComponent} from './cahier-step-fiche.component';
import {BackendApiService} from '../../../core/api/backend-api.service';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {AuthStore} from '../../../core/state/auth.store';
import {TranslateService} from '@ngx-translate/core';
import {MatSnackBar} from '@angular/material/snack-bar';

describe('CahierStepFicheComponent', () => {
  const apiMock = {
    getPatient: vi.fn(() => of({
      id: 'patient-1',
      nom: 'Dupont',
      prenom: 'Jean',
      dateNaissance: '1990-01-01',
      dateAdmission: '2026-07-01',
      numeroAssurance: 'ASS-001',
      generateurNom: 'G10',
      generateurMarque: 'Fresenius',
      generateurEtat: 'FONCTIONNEL',
    })),
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [CahierStepFicheComponent],
      providers: [
        {provide: BackendApiService, useValue: apiMock},
        {provide: AppShellStore, useValue: {currentCenterId: () => 'center-1'}},
        {provide: AuthStore, useValue: {username: () => 'user-1', hasRole: () => true}},
        {provide: TranslateService, useValue: {instant: (key: string) => key}},
        {provide: MatSnackBar, useValue: {open: vi.fn()}},
      ],
    })
      .overrideComponent(CahierStepFicheComponent, {set: {template: '<div></div>'}})
      .compileComponents();
  });

  it('charge la fiche patient et expose les détails du générateur', () => {
    const fixture = TestBed.createComponent(CahierStepFicheComponent);
    fixture.componentRef.setInput('patientId', 'patient-1');
    fixture.detectChanges();

    expect(apiMock.getPatient).toHaveBeenCalledWith('patient-1', 'center-1', 'user-1');
    expect(fixture.componentInstance.patientData().generateurNom).toBe('G10');
    expect(fixture.componentInstance.generatorSummary()).toContain('G10');
    expect(fixture.componentInstance.generatorSummary()).toContain('Fresenius');
  });
});

