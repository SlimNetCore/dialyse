import {provideZonelessChangeDetection} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {of} from 'rxjs';
import {BackendApiService, FacturationPreviewResponse} from '../../../core/api/backend-api.service';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {FacturationStore} from './facturation.store';

const CENTER_ID = '11111111-1111-1111-1111-111111111111';
const USER_ID = '22222222-2222-2222-2222-222222222222';
const SEANCE_ID = '33333333-3333-3333-3333-333333333333';
const FORFAIT_A = '44444444-4444-4444-4444-444444444444';
const FORFAIT_B = '55555555-5555-5555-5555-555555555555';

function previewFixture(forfaitId: string): FacturationPreviewResponse {
  return {
    centerId: CENTER_ID,
    generatedAt: '2026-08-31T09:30:00Z',
    periodStart: '2026-08-01',
    periodEnd: '2026-08-31',
    totalFactures: 1,
    totalHt: 4000,
    totalTva: 760,
    totalTtc: 4760,
    invoices: [
      {
        previewKey: 'preview-1',
        patientId: 'patient-1',
        patientCode: 'PAT-001',
        patientFullName: 'Patient Test',
        patientStatusSnapshot: 'ACTIF',
        totalHt: 4000,
        totalTva: 760,
        totalTtc: 4760,
        lines: [
          {
            forfaitId,
            forfaitLabel: forfaitId === FORFAIT_A ? 'Forfait A' : 'Forfait B',
            unitPriceHt: 4000,
            seanceCount: 1,
            lineHt: 4000,
          },
        ],
        seances: [
          {
            seanceId: SEANCE_ID,
            seanceDate: '2026-08-12',
            seanceStatus: 'VALIDE',
            forfaitId,
            forfaitLabel: forfaitId === FORFAIT_A ? 'Forfait A' : 'Forfait B',
            forfaitPrixHt: 4000,
          },
        ],
      },
    ],
  };
}

describe('FacturationStore', () => {
  let mockApi: {
    previewFacturation: ReturnType<typeof vi.fn>;
    excludeSeanceFromPreview: ReturnType<typeof vi.fn>;
    updatePreviewSeanceForfait: ReturnType<typeof vi.fn>;
    validateFacturation: ReturnType<typeof vi.fn>;
    getFacturationDashboard: ReturnType<typeof vi.fn>;
    getFacturationSettings: ReturnType<typeof vi.fn>;
    updateFacturationSettings: ReturnType<typeof vi.fn>;
    listForfaitsReferential: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    mockApi = {
      previewFacturation: vi.fn().mockReturnValue(of(previewFixture(FORFAIT_A))),
      excludeSeanceFromPreview: vi.fn().mockReturnValue(of(previewFixture(FORFAIT_A))),
      updatePreviewSeanceForfait: vi.fn().mockReturnValue(of(previewFixture(FORFAIT_B))),
      validateFacturation: vi.fn().mockReturnValue(of({createdInvoices: 1})),
      getFacturationDashboard: vi.fn().mockReturnValue(of({
        centerId: CENTER_ID,
        month: '2026-08',
        revenueTtc: 4760,
        revenueHt: 4000,
        billedSeances: 1,
        billedPatients: 1,
        createdInvoices: 1,
        byInsurance: [],
        byPatientStatus: [],
      })),
      getFacturationSettings: vi.fn().mockReturnValue(of({
        codeFormat: 'FAC-{YEAR}-{SEQ}',
        regroupementMultiForfait: true,
        updatedAt: null,
      })),
      updateFacturationSettings: vi.fn().mockReturnValue(of({
        codeFormat: 'FAC-{YEAR}-{SEQ}',
        regroupementMultiForfait: true,
        updatedAt: null,
      })),
      listForfaitsReferential: vi.fn().mockReturnValue(of([])),
    };

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        FacturationStore,
        AppShellStore,
        {provide: BackendApiService, useValue: mockApi},
      ],
    });
  });

  it('calcule un preview mensuel avec centerId et regroupement', () => {
    const store = TestBed.inject(FacturationStore);
    store.setActiveCenterId(CENTER_ID);
    store.setMonth('2026-08');

    store.calculatePreview({centerId: CENTER_ID});

    expect(mockApi.previewFacturation).toHaveBeenCalledWith({
      centerId: CENTER_ID,
      month: '2026-08',
      regroupementMultiForfait: true,
    });
    expect(store.preview()?.invoices[0]?.seances[0]?.forfaitId).toBe(FORFAIT_A);
  });

  it('retire une seance du preview avec centerId, userId et periode courante', () => {
    const store = TestBed.inject(FacturationStore);
    store.setActiveCenterId(CENTER_ID);
    store.setMonth('2026-08');

    store.removeSeanceFromPreview({centerId: CENTER_ID, userId: USER_ID, seanceId: SEANCE_ID});

    expect(mockApi.excludeSeanceFromPreview).toHaveBeenCalledWith(SEANCE_ID, {
      centerId: CENTER_ID,
      userId: USER_ID,
      month: '2026-08',
      regroupementMultiForfait: true,
    });
    expect(store.successMessage()).toBe('FACTURATION.SUCCESS.SEANCE_EXCLUDED');
  });

  it('met a jour le forfait d une seance dans le preview et recalcule', () => {
    const store = TestBed.inject(FacturationStore);
    store.setActiveCenterId(CENTER_ID);
    store.setMonth('2026-08');

    store.updateSeanceForfaitInPreview({
      centerId: CENTER_ID,
      userId: USER_ID,
      seanceId: SEANCE_ID,
      forfaitId: FORFAIT_B,
    });

    expect(mockApi.updatePreviewSeanceForfait).toHaveBeenCalledWith(SEANCE_ID, {
      centerId: CENTER_ID,
      userId: USER_ID,
      forfaitId: FORFAIT_B,
      month: '2026-08',
      regroupementMultiForfait: true,
    });
    expect(store.preview()?.invoices[0]?.seances[0]?.forfaitId).toBe(FORFAIT_B);
    expect(store.successMessage()).toBe('FACTURATION.SUCCESS.SEANCE_FORFAIT_UPDATED');
  });
});


