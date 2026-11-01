import {TestBed} from '@angular/core/testing';
import {provideZonelessChangeDetection} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {of} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {PilotageApiService} from './pilotage-api.service';

const CENTER_ID = '11111111-1111-1111-1111-111111111111';

describe('PilotageApiService', () => {
  let http: { get: ReturnType<typeof vi.fn>; post: ReturnType<typeof vi.fn> };
  let service: PilotageApiService;

  beforeEach(() => {
    http = {
      get: vi.fn().mockReturnValue(of({items: [], total: 0})),
      post: vi.fn().mockReturnValue(of({value: 'new-id'})),
    };
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), PilotageApiService,
        {provide: HttpClient, useValue: http}],
    });
    service = TestBed.inject(PilotageApiService);
  });

  it('consulte le tableau de bord du centre', () => {
    service.consulterTableauDeBord(CENTER_ID).subscribe();
    const [url, options] = http.get.mock.calls[0] as [string, { params: { get: (k: string) => string } }];
    expect(url).toBe('/api/v1/pilotage/tableau-de-bord');
    expect(options.params.get('centreId')).toBe(CENTER_ID);
  });

  it('capture un instantané mensuel', () => {
    service.capturerInstantane(CENTER_ID, 2026, 3, '{"seances":120}').subscribe();
    expect(http.post).toHaveBeenCalledWith('/api/v1/pilotage/instantanes-mensuels',
      {centreId: CENTER_ID, annee: 2026, mois: 3, donnees: '{"seances":120}'});
  });

  it('génère une alerte direction', () => {
    service.genererAlerte(CENTER_ID, 'SOUS_EFFECTIF', 'ratio<0.8', 'message',
      '2026-03-10').subscribe();
    expect(http.post).toHaveBeenCalledWith('/api/v1/pilotage/alertes',
      {
        centreId: CENTER_ID, type: 'SOUS_EFFECTIF', seuil: 'ratio<0.8',
        message: 'message', date: '2026-03-10'
      });
  });

  it('acquitte une alerte', () => {
    service.acquitterAlerte('alerte-1', CENTER_ID).subscribe();
    expect(http.post).toHaveBeenCalledWith('/api/v1/pilotage/alertes/alerte-1/acquittement',
      {centreId: CENTER_ID});
  });

  it('génère un export synthèse', () => {
    service.genererExport(CENTER_ID, '2026-03', 'HTML').subscribe();
    expect(http.post).toHaveBeenCalledWith('/api/v1/pilotage/exports-synthese',
      {centreId: CENTER_ID, periode: '2026-03', format: 'HTML'});
  });
});
