import {provideZonelessChangeDetection} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {HttpErrorResponse} from '@angular/common/http';
import {of, throwError} from 'rxjs';
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
        numeroFacture: 'FAC-2026-000001',
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
    includeSeanceInPreview: ReturnType<typeof vi.fn>;
    updatePreviewSeanceForfait: ReturnType<typeof vi.fn>;
    validateFacturation: ReturnType<typeof vi.fn>;
    getFacturationDashboard: ReturnType<typeof vi.fn>;
    getFacturationSettings: ReturnType<typeof vi.fn>;
    updateFacturationSettings: ReturnType<typeof vi.fn>;
    listForfaitsReferential: ReturnType<typeof vi.fn>;
    getFacturationPeriodes: ReturnType<typeof vi.fn>;
    cloturerMoisFacturation: ReturnType<typeof vi.fn>;
    rouvrirMoisFacturation: ReturnType<typeof vi.fn>;
  };

  const mois = (m: string, statut: 'OUVERT' | 'CLOTURE', extra: object = {}) => ({
    mois: m, statut, cloturePar: null, clotureLe: null, rouvertPar: null, rouvertLe: null, motifDeReouverture: null, ...extra,
  });
  const annee2026 = (clos: string[] = []) => ({
    centerId: CENTER_ID,
    annee: 2026,
    periodes: Array.from({length: 12}, (_, i) => {
      const m = `2026-${String(i + 1).padStart(2, '0')}`;
      return mois(m, clos.includes(m) ? 'CLOTURE' : 'OUVERT');
    }),
  });

  beforeEach(() => {
    mockApi = {
      getFacturationPeriodes: vi.fn().mockReturnValue(of(annee2026(['2026-08']))),
      cloturerMoisFacturation: vi.fn().mockReturnValue(of({
        centerId: CENTER_ID, mois: '2026-07', statut: 'CLOTURE', cloturePar: USER_ID, clotureLe: '2026-09-01T08:00:00Z',
        seancesEligiblesRestantes: 3,
        absencesDuMoisNonCloses: true,
      })),
      rouvrirMoisFacturation: vi.fn().mockReturnValue(of({centerId: CENTER_ID, mois: '2026-08', statut: 'OUVERT'})),
      previewFacturation: vi.fn().mockReturnValue(of(previewFixture(FORFAIT_A))),
      excludeSeanceFromPreview: vi.fn().mockReturnValue(of(previewFixture(FORFAIT_A))),
      includeSeanceInPreview: vi.fn().mockReturnValue(of(previewFixture(FORFAIT_A))),
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

    store.calculatePreview({centerId: CENTER_ID});
    store.removeSeanceFromPreview({
      centerId: CENTER_ID,
      userId: USER_ID,
      seanceId: SEANCE_ID,
      motif: 'AUTRE',
      commentaire: '  Patient en voyage  ',
    });

    expect(mockApi.excludeSeanceFromPreview).toHaveBeenCalledWith(SEANCE_ID, {
      centerId: CENTER_ID,
      userId: USER_ID,
      motif: 'AUTRE',
      commentaire: 'Patient en voyage',
      month: '2026-08',
      regroupementMultiForfait: true,
    });
    expect(store.successMessage()).toBe('FACTURATION.SUCCESS.SEANCE_EXCLUDED');
    expect(store.excludedSeances()).toEqual([
      {seanceId: SEANCE_ID, seanceDate: '2026-08-12', patientLabel: 'PAT-001 Patient Test', motif: 'AUTRE'},
    ]);
  });

  it('n envoie pas de commentaire vide avec l exclusion', () => {
    const store = TestBed.inject(FacturationStore);
    store.setMonth('2026-08');

    store.removeSeanceFromPreview({
      centerId: CENTER_ID,
      userId: USER_ID,
      seanceId: SEANCE_ID,
      motif: 'MALADIE',
      commentaire: ' '
    });

    expect(mockApi.excludeSeanceFromPreview.mock.calls[0][1]).not.toHaveProperty('commentaire');
  });

  it('reinclut une seance exclue, relance la simulation et la retire de la liste', () => {
    const store = TestBed.inject(FacturationStore);
    store.setMonth('2026-08');
    store.calculatePreview({centerId: CENTER_ID});
    store.removeSeanceFromPreview({centerId: CENTER_ID, userId: USER_ID, seanceId: SEANCE_ID, motif: 'MALADIE'});

    store.reincludeSeanceInPreview({centerId: CENTER_ID, seanceId: SEANCE_ID});

    expect(mockApi.includeSeanceInPreview).toHaveBeenCalledWith(SEANCE_ID, {
      centerId: CENTER_ID,
      month: '2026-08',
      regroupementMultiForfait: true,
    });
    expect(store.excludedSeances()).toEqual([]);
    expect(store.successMessage()).toBe('FACTURATION.SUCCESS.SEANCE_REINCLUDED');
  });

  it('affiche le detail du backend quand la reinclusion est refusee (422)', () => {
    mockApi.includeSeanceInPreview.mockReturnValue(throwError(() => new HttpErrorResponse({
      status: 422,
      error: {detail: 'Cette séance n\'a pas été exclue par la facturation'},
    })));
    const store = TestBed.inject(FacturationStore);

    store.reincludeSeanceInPreview({centerId: CENTER_ID, seanceId: SEANCE_ID});

    expect(store.error()).toBe('Cette séance n\'a pas été exclue par la facturation');
    expect(store.seanceActionLoadingId()).toBeNull();
  });

  it('expose le 409 numero deja pris et le 422 seances sans forfait a la validation', () => {
    const store = TestBed.inject(FacturationStore);
    store.calculatePreview({centerId: CENTER_ID});

    mockApi.validateFacturation.mockReturnValueOnce(throwError(() => new HttpErrorResponse({
      status: 409, error: {code: 'NUMERO_DE_FACTURE_DEJA_PRIS', detail: 'FAC-2026-000001'},
    })));
    store.validateFacturation({centerId: CENTER_ID, userId: USER_ID});
    expect(store.error()).toBe('FACTURATION.ERROR.NUMERO_DEJA_PRIS');
    expect(store.preview()).not.toBeNull();

    mockApi.validateFacturation.mockReturnValueOnce(throwError(() => new HttpErrorResponse({
      status: 422, error: {code: 'SEANCES_SANS_FORFAIT', detail: '2 séances', seancesSansForfait: 2},
    })));
    store.validateFacturation({centerId: CENTER_ID, userId: USER_ID});
    expect(store.error()).toBe('FACTURATION.ERROR.SEANCES_SANS_FORFAIT');
    expect(store.errorParams()).toEqual({count: 2, detail: '2 séances'});
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

  describe('clôture mensuelle', () => {
    it('charge les mois de l année et les cumule', () => {
      const store = TestBed.inject(FacturationStore);

      store.loadPeriodes({centerId: CENTER_ID, annee: 2026});

      expect(mockApi.getFacturationPeriodes).toHaveBeenCalledWith(CENTER_ID, 2026);
      expect(store.anneesChargees()).toEqual([2026]);
      expect(store.moisEstClos('2026-08')).toBe(true);
      expect(store.moisEstClos('2026-07')).toBe(false);
      expect(store.moisDeLAnnee().length).toBe(12);
    });

    it('désactivation : tout mois couvert par la période personnalisée, ou le mois choisi, clos', () => {
      const store = TestBed.inject(FacturationStore);
      store.loadPeriodes({centerId: CENTER_ID, annee: 2026});

      store.setPeriodMode('custom');
      store.setStartDate('2026-07-20');
      store.setEndDate('2026-08-05');
      expect(store.moisVises()).toEqual(['2026-07', '2026-08']);
      expect(store.moisClosVises()).toEqual(['2026-08']);

      store.setEndDate('2026-07-31');
      expect(store.moisClosVises()).toEqual([]);

      store.setPeriodMode('month');
      store.setMonth('2026-08');
      expect(store.moisClosVises()).toEqual(['2026-08']);
    });

    it('clôture un mois : avertissement chiffré, message de succès, mois rechargé', () => {
      mockApi.getFacturationPeriodes.mockReturnValue(of(annee2026(['2026-07', '2026-08'])));
      const store = TestBed.inject(FacturationStore);

      store.cloturerMois({centerId: CENTER_ID, mois: '2026-07'});

      expect(mockApi.cloturerMoisFacturation).toHaveBeenCalledWith('2026-07', CENTER_ID);
      expect(store.clotureAvertissement()).toEqual({mois: '2026-07', seancesRestantes: 3, absencesNonCloses: true});
      expect(store.successMessage()).toBe('FACTURATION.SUCCESS.MOIS_CLOTURE');
      expect(store.moisEstClos('2026-07')).toBe(true);
      expect(store.periodeActionMois()).toBeNull();
    });

    it('clôture refusée en 409 : message dédié, aucun avertissement', () => {
      mockApi.cloturerMoisFacturation.mockReturnValue(throwError(() =>
        new HttpErrorResponse({status: 409, error: {code: 'PERIODE_DEJA_CLOTUREE', detail: 'd'}})));
      const store = TestBed.inject(FacturationStore);

      store.cloturerMois({centerId: CENTER_ID, mois: '2026-08'});

      expect(store.error()).toBe('FACTURATION.ERROR.PERIODE_DEJA_CLOTUREE');
      expect(store.clotureAvertissement()).toBeNull();
      expect(store.periodeActionMois()).toBeNull();
    });

    it('rouvre un mois avec son motif puis recharge l année', () => {
      const store = TestBed.inject(FacturationStore);
      store.loadPeriodes({centerId: CENTER_ID, annee: 2026});
      mockApi.getFacturationPeriodes.mockReturnValue(of({
        ...annee2026(),
        periodes: annee2026().periodes.map((p) => p.mois === '2026-08'
          ? mois('2026-08', 'OUVERT', {rouvertPar: USER_ID, rouvertLe: '2026-09-02T08:00:00Z', motifDeReouverture: 'oubli'})
          : p),
      }));

      store.rouvrirMois({centerId: CENTER_ID, mois: '2026-08', motif: 'oubli'});

      expect(mockApi.rouvrirMoisFacturation).toHaveBeenCalledWith('2026-08', {centerId: CENTER_ID, motif: 'oubli'});
      expect(store.moisEstClos('2026-08')).toBe(false);
      expect(store.periodes()['2026-08'].motifDeReouverture).toBe('oubli');
      expect(store.successMessage()).toBe('FACTURATION.SUCCESS.MOIS_ROUVERT');
    });

    it('réouverture refusée par la comptabilité (422) : message clair', () => {
      mockApi.rouvrirMoisFacturation.mockReturnValue(throwError(() =>
        new HttpErrorResponse({status: 422, error: {code: 'PERIODE_COMPTABLE_CLOTUREE'}})));
      const store = TestBed.inject(FacturationStore);

      store.rouvrirMois({centerId: CENTER_ID, mois: '2026-08', motif: 'oubli'});

      expect(store.error()).toBe('FACTURATION.ERROR.PERIODE_COMPTABLE_CLOTUREE');
    });

    it('validation sur un mois clos (422) : message avec le mois', () => {
      mockApi.validateFacturation.mockReturnValue(throwError(() =>
        new HttpErrorResponse({status: 422, error: {code: 'PERIODE_DE_FACTURATION_CLOTUREE', mois: '2026-08', detail: 'd'}})));
      const store = TestBed.inject(FacturationStore);
      store.setMonth('2026-08');
      store.calculatePreview({centerId: CENTER_ID});

      store.validateFacturation({centerId: CENTER_ID, userId: USER_ID});

      expect(store.error()).toBe('FACTURATION.ERROR.PERIODE_CLOTUREE');
      expect(store.errorParams()).toEqual({mois: '2026-08'});
    });
  });
});



