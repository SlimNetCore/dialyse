import {TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {TranslateModule} from '@ngx-translate/core';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {signal} from '@angular/core';
import {MatSnackBar} from '@angular/material/snack-bar';
import {MatDialog} from '@angular/material/dialog';
import {StepAttestationComponent} from './step-attestation.component';
import {AppShellStore} from '../../../core/state/app-shell.store';
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
    deleteAttestation: vi.fn(),
    printDocument: vi.fn(),
  };
}

describe('StepAttestationComponent (signal forms)', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StepAttestationComponent, TranslateModule.forRoot(), NoopAnimationsModule],
      providers: [
        {provide: AppShellStore, useValue: {currentCenterId: () => 'center-1'}},
        {provide: PatientFicheStore, useValue: ficheStoreMock()},
        {provide: MatSnackBar, useValue: {open: vi.fn()}},
        {provide: MatDialog, useValue: {open: vi.fn()}},
      ],
    }).compileComponents();
  });

  it('est invalide tant que les deux dates ne sont pas saisies', () => {
    const fixture = TestBed.createComponent(StepAttestationComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.isValid()).toBe(false);
  });

  it('devient valide et émet dataChange/validChange quand les deux dates sont saisies', () => {
    const fixture = TestBed.createComponent(StepAttestationComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    const dataSpy = vi.fn();
    const validSpy = vi.fn();
    component.dataChange.subscribe(dataSpy);
    component.validChange.subscribe(validSpy);

    component.onDateChange('attestationDebut', new Date(2026, 0, 1));
    expect(component.isValid()).toBe(false);
    component.onDateChange('attestationFin', new Date(2026, 11, 31));

    expect(component.isValid()).toBe(true);
    expect(validSpy).toHaveBeenLastCalledWith(true);
    expect(dataSpy).toHaveBeenLastCalledWith(
      expect.objectContaining({attestationDebut: expect.any(Date), attestationFin: expect.any(Date)}),
    );
  });

  it('patchData sélectionne la dernière attestation de l’historique', () => {
    const fixture = TestBed.createComponent(StepAttestationComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    const validSpy = vi.fn();
    component.validChange.subscribe(validSpy);

    component.patchData({
      patientId: 'p-1',
      attestationHistory: [
        {id: 'a1', dateDebut: '2025-01-01', dateFin: '2025-06-30'},
        {id: 'a2', dateDebut: '2026-01-01', dateFin: '2026-06-30'},
      ],
    });

    expect(component.selectedAttestationId()).toBe('a2');
    expect(component.form.get('attestationDebut')).toBe('2026-01-01');
    expect(component.form.get('attestationFin')).toBe('2026-06-30');
    expect(validSpy).toHaveBeenCalledWith(true);
  });

  it('select() met à jour la sélection et les dates', () => {
    const fixture = TestBed.createComponent(StepAttestationComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.select({id: 'a9', dateDebut: '2026-02-01', dateFin: '2026-08-01'});

    expect(component.selectedAttestationId()).toBe('a9');
    expect(component.form.get('attestationDebut')).toBe('2026-02-01');
    expect(component.isValid()).toBe(true);
  });

  it('prepareNew() réinitialise la sélection et les champs', () => {
    const fixture = TestBed.createComponent(StepAttestationComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.select({id: 'a9', dateDebut: '2026-02-01', dateFin: '2026-08-01'});
    component.prepareNew();

    expect(component.selectedAttestationId()).toBeNull();
    expect(component.form.get('attestationDebut')).toBeNull();
    expect(component.form.get('attestationFin')).toBeNull();
    expect(component.isValid()).toBe(false);
  });
});

