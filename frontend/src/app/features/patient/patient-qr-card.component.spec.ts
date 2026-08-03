import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {provideZonelessChangeDetection} from '@angular/core';
import {MAT_DIALOG_DATA, MatDialog} from '@angular/material/dialog';
import {PatientQrCardComponent, PatientQrCardDialogComponent} from './patient-qr-card.component';

describe('PatientQrCardComponent', () => {
  it('should open dialog with codePatient payload', () => {
    const open = vi.fn();
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        {provide: MatDialog, useValue: {open}},
      ],
    });

    const component = TestBed.runInInjectionContext(() => new PatientQrCardComponent());
    component.patientId = '11111111-1111-1111-1111-111111111111';
    component.codePatient = 'PAT-001';
    component.nom = 'Dupont';
    component.prenom = 'Nadia';
    component.numeroAssurance = 'ASS-001';

    component.openCard();

    expect(open).toHaveBeenCalledWith(PatientQrCardDialogComponent, expect.objectContaining({
      data: expect.objectContaining({
        codePatient: 'PAT-001',
        patientId: '11111111-1111-1111-1111-111111111111',
      }),
    }));
  });
});

describe('PatientQrCardDialogComponent', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('should build PAT scan token when code patient exists', () => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        {
          provide: MAT_DIALOG_DATA,
          useValue: {
            patientId: '11111111-1111-1111-1111-111111111111',
            codePatient: 'PAT-ABC',
            nom: 'Test',
            prenom: 'Patient',
            numeroAssurance: 'ASS-10',
            photoBase64: null,
            dateAdmission: '2026-08-03',
            groupeSanguin: 'O+',
          },
        },
      ],
    });

    const component = TestBed.runInInjectionContext(() => new PatientQrCardDialogComponent());

    expect(component['scanToken']()).toBe('PAT:PAT-ABC');
  });

  it('should fallback to ASS scan token when code patient is missing', () => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        {
          provide: MAT_DIALOG_DATA,
          useValue: {
            patientId: '11111111-1111-1111-1111-111111111111',
            codePatient: '',
            nom: 'Test',
            prenom: 'Patient',
            numeroAssurance: 'ASS-10',
            photoBase64: null,
            dateAdmission: '2026-08-03',
            groupeSanguin: 'O+',
          },
        },
      ],
    });

    const component = TestBed.runInInjectionContext(() => new PatientQrCardDialogComponent());

    expect(component['scanToken']()).toBe('ASS:ASS-10');
  });
});



