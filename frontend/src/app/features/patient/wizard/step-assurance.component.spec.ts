import {TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {TranslateModule} from '@ngx-translate/core';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {signal} from '@angular/core';
import {MatSnackBar} from '@angular/material/snack-bar';
import {MatDialog} from '@angular/material/dialog';
import {StepAssuranceComponent} from './step-assurance.component';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {CentresPayeursDetailsStore, CentresPayeursStore} from '../../../core/state/referentials.store';
import {PatientFicheStore} from '../state/patient-fiche.store';

function ficheStoreMock() {
  return {
    lastActionId: signal(0),
    lastAction: signal<string | null>(null),
    lastSuccess: signal<boolean | null>(null),
    lastError: signal<string | null>(null),
    lastMessage: signal<string | null>(null),
    lastActionMeta: signal<Record<string, any> | null>(null),
    error: signal<string | null>(null),
    infoMessage: signal<string | null>(null),
    assureCatalog: signal<any[]>([]),
    assureAssignments: signal<any[]>([]),
    loadingAssures: signal(false),
    savingAssureEdit: signal(false),
    updateAssure: vi.fn(),
    assignAssure: vi.fn(),
    searchAssures: vi.fn(),
    loadAssureHistory: vi.fn(),
    updateAssureAssignment: vi.fn(),
  };
}

describe('StepAssuranceComponent (signal forms)', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StepAssuranceComponent, TranslateModule.forRoot(), NoopAnimationsModule],
      providers: [
        {provide: AppShellStore, useValue: {currentCenterId: () => 'center-1'}},
        {
          provide: CentresPayeursStore,
          useValue: {items: signal([{id: 'cp-1', label: 'CNAS', code: 'C1'}]), ensureLoaded: vi.fn()},
        },
        {
          provide: CentresPayeursDetailsStore,
          useValue: {
            items: signal([{id: 'cp-1', codeAgence: 'AG1', libelleAgence: 'Agence 1', libelleCaisse: 'Caisse 1'}]),
            ensureLoaded: vi.fn(),
          },
        },
        {provide: PatientFicheStore, useValue: ficheStoreMock()},
        {provide: MatSnackBar, useValue: {open: vi.fn()}},
        {provide: MatDialog, useValue: {open: vi.fn()}},
      ],
    }).compileComponents();
  });

  it('exige le numéro d’assurance patient', () => {
    const fixture = TestBed.createComponent(StepAssuranceComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    expect(component.isValid()).toBe(false);
    component.onNumeroAssurance('NUM-1');
    expect(component.isValid()).toBe(true);
  });

  it('rend nom/prénom/numéro assuré obligatoires selon la qualité', () => {
    const fixture = TestBed.createComponent(StepAssuranceComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.onNumeroAssurance('NUM-1');
    expect(component.isValid()).toBe(true);

    component.setQualiteAssure('ENFANT');
    expect(component.requiresAssureNumero()).toBe(true);
    expect(component.isValid()).toBe(false);

    component.onAssureText('assureNom', 'Dupont');
    component.onAssureText('assurePrenom', 'Marie');
    component.onAssureText('assureNumeroAssurance', 'ASS-9');
    expect(component.isValid()).toBe(true);
  });

  it('rétablit la validité quand on repasse en « assuré lui-même »', () => {
    const fixture = TestBed.createComponent(StepAssuranceComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.onNumeroAssurance('NUM-1');
    component.setQualiteAssure('CONJOINT');
    expect(component.isValid()).toBe(false);

    component.setQualiteAssure('ASSURE_LUI_MEME');
    expect(component.requiresAssureNumero()).toBe(false);
    expect(component.isValid()).toBe(true);
  });

  it('émet le centre payeur et son code lors de la sélection', () => {
    const fixture = TestBed.createComponent(StepAssuranceComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    const dataSpy = vi.fn();
    component.dataChange.subscribe(dataSpy);

    component.onCentrePayeur({id: 'cp-1', label: 'CNAS', code: 'C1'});

    expect(component.selectedCentrePayeurId()).toBe('cp-1');
    expect(component.form.get('centrePayeurId')).toBe('cp-1');
    expect(dataSpy).toHaveBeenLastCalledWith(expect.objectContaining({codeCentrePayeur: 'C1'}));
  });

  it('patchData renseigne le numéro et émet la validité', () => {
    const fixture = TestBed.createComponent(StepAssuranceComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    const validSpy = vi.fn();
    component.validChange.subscribe(validSpy);

    component.patchData({numeroAssurance: 'NUM-42', qualiteAssure: 'ASSURE_LUI_MEME'});

    expect(component.form.get('numeroAssurance')).toBe('NUM-42');
    expect(validSpy).toHaveBeenLastCalledWith(true);
  });
});

