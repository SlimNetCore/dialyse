import {TestBed} from '@angular/core/testing';
import {provideZonelessChangeDetection} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {of} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {SeanceApiService} from './seance-api.service';

const CENTER_ID = '11111111-1111-1111-1111-111111111111';

describe('SeanceApiService', () => {
  let http: { get: ReturnType<typeof vi.fn>; post: ReturnType<typeof vi.fn> };
  let service: SeanceApiService;

  beforeEach(() => {
    http = {
      get: vi.fn().mockReturnValue(of({items: [], total: 0})),
      post: vi.fn().mockReturnValue(of(undefined)),
    };
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), SeanceApiService,
        {provide: HttpClient, useValue: http}],
    });
    service = TestBed.inject(SeanceApiService);
  });

  it('liste les séances paginées du centre', () => {
    service.lister(CENTER_ID, 0, 20).subscribe();
    const [url, options] = http.get.mock.calls[0] as [string, { params: { get: (k: string) => string } }];
    expect(url).toBe('/api/v1/seances');
    expect(options.params.get('centreId')).toBe(CENTER_ID);
    expect(options.params.get('page')).toBe('0');
  });

  it('liste les séances du jour avec la date', () => {
    service.listerDuJour(CENTER_ID, '2026-11-01', 0, 20).subscribe();
    const [url, options] = http.get.mock.calls[0] as [string, { params: { get: (k: string) => string } }];
    expect(url).toBe('/api/v1/seances/jour');
    expect(options.params.get('date')).toBe('2026-11-01');
  });

  it('valide une séance', () => {
    service.valider('seance-1', CENTER_ID).subscribe();
    expect(http.post).toHaveBeenCalledWith('/api/v1/seances/seance-1/validate',
      {centreId: CENTER_ID});
  });

  it('déclare un forfait surchargé', () => {
    service.declarerForfaitSurcharge('seance-1', CENTER_ID, null, 'F001', 'Forfait',
      3500, 500, 'admin').subscribe();
    expect(http.post).toHaveBeenCalledWith('/api/v1/seances/seance-1/forfait',
      {
        centreId: CENTER_ID, forfaitId: null, code: 'F001', libelle: 'Forfait',
        prixHt: 3500, montantSurcharge: 500, auteur: 'admin'
      });
  });

  it('crée une dérogation de régularisation', () => {
    service.creerDerogation('seance-1', CENTER_ID, 'motif', 'direction').subscribe();
    expect(http.post).toHaveBeenCalledWith('/api/v1/derogations-regularisation',
      {seanceId: 'seance-1', centreId: CENTER_ID, motif: 'motif', autorite: 'direction'});
  });
});
