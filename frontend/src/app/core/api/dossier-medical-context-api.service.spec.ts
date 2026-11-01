import {TestBed} from '@angular/core/testing';
import {provideZonelessChangeDetection} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {of} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {DossierMedicalContexteApiService} from './dossier-medical-context-api.service';

const CENTER_ID = '11111111-1111-1111-1111-111111111111';
const PATIENT_ID = '22222222-2222-2222-2222-222222222222';

describe('DossierMedicalContexteApiService', () => {
  let http: {
    get: ReturnType<typeof vi.fn>; post: ReturnType<typeof vi.fn>;
    put: ReturnType<typeof vi.fn>; delete: ReturnType<typeof vi.fn>
  };
  let service: DossierMedicalContexteApiService;

  beforeEach(() => {
    http = {
      get: vi.fn().mockReturnValue(of({items: [], total: 0})),
      post: vi.fn().mockReturnValue(of({value: 'new-id'})),
      put: vi.fn().mockReturnValue(of(undefined)),
      delete: vi.fn().mockReturnValue(of(undefined)),
    };
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), DossierMedicalContexteApiService,
        {provide: HttpClient, useValue: http}],
    });
    service = TestBed.inject(DossierMedicalContexteApiService);
  });

  it('liste les antécédents paginés du patient', () => {
    service.listerAntecedents(CENTER_ID, PATIENT_ID, 0, 20).subscribe();
    const [url, options] = http.get.mock.calls[0] as [string, { params: { get: (k: string) => string } }];
    expect(url).toBe('/api/v1/dossier-medical/antecedents');
    expect(options.params.get('patientId')).toBe(PATIENT_ID);
    expect(options.params.get('page')).toBe('0');
  });

  it('enregistre une sérologie avec type et résultat', () => {
    service.enregistrerSerologie(PATIENT_ID, CENTER_ID, 'MED-001', '2026-03-10', 'VHB',
      'NEGATIF', null, null, null, null, null).subscribe();
    const [url, body] = http.post.mock.calls[0] as [string, Record<string, unknown>];
    expect(url).toBe('/api/v1/dossier-medical/serologies');
    expect(body['typeSerologie']).toBe('VHB');
    expect(body['centreId']).toBe(CENTER_ID);
  });

  it('demande un examen avec lignes', () => {
    service.demanderExamen(PATIENT_ID, CENTER_ID, null, '2026-03-10', 'BIOLOGIE', true,
      'bilan', [{code: 'HGB', libelle: 'Hémoglobine'}]).subscribe();
    const [url, body] = http.post.mock.calls[0] as [string, Record<string, unknown>];
    expect(url).toBe('/api/v1/dossier-medical/demandes-examen');
    expect(body['categorie']).toBe('BIOLOGIE');
  });

  it('marque une demande comme prélevée', () => {
    service.marquerPreleve('demande-1', CENTER_ID).subscribe();
    expect(http.put).toHaveBeenCalledWith('/api/v1/dossier-medical/demandes-examen/demande-1/preleve',
      {centreId: CENTER_ID});
  });

  it('démarre un bilan pré-greffe avec étapes', () => {
    service.demarrerBilan(PATIENT_ID, CENTER_ID, [{code: 'CARDIO', libelle: 'Bilan cardiaque'}],
      '2026-03-10', null).subscribe();
    const [url, body] = http.post.mock.calls[0] as [string, Record<string, unknown>];
    expect(url).toBe('/api/v1/dossier-medical/bilans-pregreffe');
    expect(body['patientId']).toBe(PATIENT_ID);
  });

  it('enregistre une observation biologique', () => {
    service.enregistrerObservation(PATIENT_ID, CENTER_ID, 'HGB', 'Hémoglobine', '11.5', 'g/dL',
      12, 16, '2026-03-10', null, null, null).subscribe();
    expect(http.post).toHaveBeenCalledWith('/api/v1/dossier-medical/observations-biologiques',
      expect.objectContaining({code: 'HGB', valeur: '11.5'}));
  });
});
