import {TestBed} from '@angular/core/testing';
import {provideZonelessChangeDetection} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {of} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {PatientCouvertureApiService} from './patient-couverture-api.service';

const CENTER_ID = '11111111-1111-1111-1111-111111111111';
const PATIENT_ID = '22222222-2222-2222-2222-222222222222';

describe('PatientCouvertureApiService', () => {
  let http: {
    get: ReturnType<typeof vi.fn>; post: ReturnType<typeof vi.fn>;
    put: ReturnType<typeof vi.fn>; delete: ReturnType<typeof vi.fn>
  };
  let service: PatientCouvertureApiService;

  beforeEach(() => {
    http = {
      get: vi.fn().mockReturnValue(of({})),
      post: vi.fn().mockReturnValue(of({value: 'new-id'})),
      put: vi.fn().mockReturnValue(of(undefined)),
      delete: vi.fn().mockReturnValue(of(undefined)),
    };
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), PatientCouvertureApiService,
        {provide: HttpClient, useValue: http}],
    });
    service = TestBed.inject(PatientCouvertureApiService);
  });

  it('liste les patients paginés du centre', () => {
    service.listerPatients(CENTER_ID, 0, 20).subscribe();
    const [url, options] = http.get.mock.calls[0] as [string, { params: { get: (k: string) => string } }];
    expect(url).toBe('/api/v1/patient-couverture/patients');
    expect(options.params.get('centreId')).toBe(CENTER_ID);
    expect(options.params.get('page')).toBe('0');
  });

  it('affecte un assuré principal', () => {
    service.affecterAssurePrincipal(PATIENT_ID, CENTER_ID, 'NUM-001', 'Dupont', 'Jean',
      null, null, null, null, null, null, null, null, null).subscribe();
    const [url, body] = http.post.mock.calls[0] as [string, Record<string, unknown>];
    expect(url).toBe('/api/v1/patient-couverture/assures/affecter-principal');
    expect(body['patientId']).toBe(PATIENT_ID);
    expect(body['centreId']).toBe(CENTER_ID);
  });

  it('valide une PEC avec les dates effectives', () => {
    service.validerPec('pec-1', CENTER_ID, '2026-01-01', '2026-12-31', null).subscribe();
    expect(http.post).toHaveBeenCalledWith('/api/v1/patient-couverture/pec/pec-1/validate',
      {
        centreId: CENTER_ID, dateDebutEffectif: '2026-01-01',
        dateFinEffectif: '2026-12-31', forfaitEffectifId: null
      });
  });

  it('vérifie l’autorisation de séance via session-allowed', () => {
    service.seanceAutorisee('pec-1', CENTER_ID).subscribe();
    const [url, options] = http.get.mock.calls[0] as [string, { params: { get: (k: string) => string } }];
    expect(url).toBe('/api/v1/patient-couverture/pec/pec-1/session-allowed');
    expect(options.params.get('centreId')).toBe(CENTER_ID);
  });

  it('enregistre une attestation de droits', () => {
    service.enregistrerAttestation(PATIENT_ID, CENTER_ID, '2026-01-01', '2026-12-31').subscribe();
    expect(http.post).toHaveBeenCalledWith('/api/v1/patient-couverture/attestations',
      {
        patientId: PATIENT_ID, centreId: CENTER_ID,
        dateDebut: '2026-01-01', dateFin: '2026-12-31'
      });
  });
});
