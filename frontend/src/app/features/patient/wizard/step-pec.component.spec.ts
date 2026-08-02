import {TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {TranslateModule} from '@ngx-translate/core';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {signal} from '@angular/core';
import {MatSnackBar} from '@angular/material/snack-bar';
import {MatDialog} from '@angular/material/dialog';
import {StepPecComponent} from './step-pec.component';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {ForfaitsStore} from '../../../core/state/referentials.store';
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
    deletePec: vi.fn(),
    printDocument: vi.fn(),
  };
}

describe('StepPecComponent (signal forms)', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StepPecComponent, TranslateModule.forRoot(), NoopAnimationsModule],
      providers: [
        {provide: AppShellStore, useValue: {currentCenterId: () => 'center-1'}},
        {
          provide: ForfaitsStore,
          useValue: {items: signal([{id: 'f1', label: 'Forfait 1', prix: 3500}]), ensureLoaded: vi.fn()},
        },
        {provide: PatientFicheStore, useValue: ficheStoreMock()},
        {provide: MatSnackBar, useValue: {open: vi.fn()}},
        {provide: MatDialog, useValue: {open: vi.fn()}},
      ],
    }).compileComponents();
  });

  it('est valide quand les deux dates sont vides (étape optionnelle)', () => {
    const fixture = TestBed.createComponent(StepPecComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.isValid()).toBe(true);
  });

  it('est invalide quand une seule date est renseignée', () => {
    const fixture = TestBed.createComponent(StepPecComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.onDateChange('pecDateDebutDemande', new Date(2026, 0, 1));
    expect(component.isValid()).toBe(false);

    component.onDateChange('pecDateFinDemande', new Date(2026, 5, 30));
    expect(component.isValid()).toBe(true);
  });

  it('sélectionne un forfait et émet la donnée', () => {
    const fixture = TestBed.createComponent(StepPecComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    const dataSpy = vi.fn();
    component.dataChange.subscribe(dataSpy);

    component.selectForfait('f1');
    expect(component.form.get('pecForfaitDemandeId')).toBe('f1');
    expect(dataSpy).toHaveBeenLastCalledWith(expect.objectContaining({pecForfaitDemandeId: 'f1'}));
  });

  it('patchData renseigne les dates, le forfait et la PEC sélectionnée', () => {
    const fixture = TestBed.createComponent(StepPecComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.patchData({
      pecId: 'pec-1',
      pecDateDebutDemande: '2026-01-01',
      pecDateFinDemande: '2026-06-30',
      pecForfaitDemandeId: 'f1',
      pecHistory: [{id: 'pec-1', status: 'VALIDEE'}],
    });

    expect(component.selectedPecId()).toBe('pec-1');
    expect(component.form.get('pecDateDebutDemande')).toBe('2026-01-01');
    expect(component.form.get('pecForfaitDemandeId')).toBe('f1');
    expect(component.selectedPec()).toEqual({id: 'pec-1', status: 'VALIDEE'});
    expect(component.isValid()).toBe(true);
  });

  it('prepareNew() vide la demande et redevient valide', () => {
    const fixture = TestBed.createComponent(StepPecComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.select({id: 'pec-9', dateDebutDemande: '2026-02-01', dateFinDemande: '2026-08-01'});
    expect(component.form.get('pecDateDebutDemande')).toBe('2026-02-01');

    component.prepareNew();
    expect(component.selectedPecId()).toBeNull();
    expect(component.form.get('pecDateDebutDemande')).toBeNull();
    expect(component.isValid()).toBe(true);
  });
});

