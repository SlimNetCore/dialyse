import {TestBed} from '@angular/core/testing';
import {provideZonelessChangeDetection} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {of} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {OrganisationApiService} from './organisation-api.service';

const CENTER_ID = '11111111-1111-1111-1111-111111111111';

describe('OrganisationApiService', () => {
  let http: {
    get: ReturnType<typeof vi.fn>; post: ReturnType<typeof vi.fn>;
    put: ReturnType<typeof vi.fn>; delete: ReturnType<typeof vi.fn>
  };
  let service: OrganisationApiService;

  beforeEach(() => {
    http = {
      get: vi.fn().mockReturnValue(of([])),
      post: vi.fn().mockReturnValue(of({value: 'new-id'})),
      put: vi.fn().mockReturnValue(of(undefined)),
      delete: vi.fn().mockReturnValue(of(undefined)),
    };
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), OrganisationApiService,
        {provide: HttpClient, useValue: http}],
    });
    service = TestBed.inject(OrganisationApiService);
  });

  it('liste les sociétés paginées avec recherche sur /api/v1/organisation/societes', () => {
    service.listerSocietes('rena', 0, 20).subscribe();
    const [url, options] = http.get.mock.calls[0] as [string, { params: { get: (k: string) => string } }];
    expect(url).toBe('/api/v1/organisation/societes');
    expect(options.params.get('q')).toBe('rena');
    expect(options.params.get('page')).toBe('0');
    expect(options.params.get('size')).toBe('20');
  });

  it('consulte une société par id', () => {
    service.consulterSociete('soc-1').subscribe();
    expect(http.get).toHaveBeenCalledWith('/api/v1/organisation/societes/soc-1');
  });

  it('envoie un logo en multipart', () => {
    const file = new File(['bytes'], 'logo.png', {type: 'image/png'});
    service.uploadLogo('soc-1', file).subscribe();
    const [url, body] = http.put.mock.calls[0] as [string, FormData];
    expect(url).toBe('/api/v1/organisation/societes/soc-1/logo');
    const envoye = body.get('file') as File;
    expect(envoye.name).toBe('logo.png');
    expect(envoye.type).toBe('image/png');
  });

  it('liste les sociétés avec logo', () => {
    service.societesAvecLogo(['soc-1', 'soc-2']).subscribe();
    const [url, options] = http.get.mock.calls[0] as [string, { params: { getAll: (k: string) => string[] } }];
    expect(url).toBe('/api/v1/organisation/societes/logos');
    expect(options.params.getAll('ids')).toEqual(['soc-1', 'soc-2']);
  });

  it('liste tous les centres de la société', () => {
    service.listerCentres('soc-1').subscribe();
    const [url, options] = http.get.mock.calls[0] as [string, { params: { get: (k: string) => string } }];
    expect(url).toBe('/api/v1/organisation/centres');
    expect(options.params.get('societeId')).toBe('soc-1');
  });

  it('crée une société avec identité complète', () => {
    service.creerSociete({
      code: 'RENADIAL', raisonSociale: 'Renadial', nif: '12345678901234', nis: null,
      rc: null, coordonnees: null, piedDePage: null,
    }).subscribe();
    expect(http.post).toHaveBeenCalledWith('/api/v1/organisation/societes',
      {
        code: 'RENADIAL', raisonSociale: 'Renadial', nif: '12345678901234',
        nis: null, rc: null, coordonnees: null, piedDePage: null
      });
  });

  it('transfère un centre vers la société cible', () => {
    service.transfererCentre('centre-1', 'societe-2').subscribe();
    expect(http.post).toHaveBeenCalledWith('/api/v1/organisation/centres/centre-1/transferer',
      {nouvelleSocieteId: 'societe-2'});
  });

  it('liste les salles du centre actif via centerId', () => {
    service.listerSalles(CENTER_ID).subscribe();
    const [url, options] = http.get.mock.calls[0] as [string, { params: { get: (k: string) => string } }];
    expect(url).toBe('/api/v1/organisation/salles');
    expect(options.params.get('centreId')).toBe(CENTER_ID);
  });

  it('liste les médecins paginés du centre', () => {
    service.listerMedecins(CENTER_ID, 0, 20).subscribe();
    const [url, options] = http.get.mock.calls[0] as [string, { params: { get: (k: string) => string } }];
    expect(url).toBe('/api/v1/organisation/referentiels/medecins');
    expect(options.params.get('centreId')).toBe(CENTER_ID);
    expect(options.params.get('page')).toBe('0');
    expect(options.params.get('size')).toBe('20');
  });

  it('supprime un protocole par id', () => {
    service.supprimerProtocole('proto-1').subscribe();
    expect(http.delete).toHaveBeenCalledWith('/api/v1/organisation/referentiels/protocoles/proto-1');
  });

  it('liste les articles paginés du catalogue', () => {
    service.listerArticles(CENTER_ID, 0, 20).subscribe();
    const [url, options] = http.get.mock.calls[0] as [string, { params: { get: (k: string) => string } }];
    expect(url).toBe('/api/v1/organisation/referentiels/articles');
    expect(options.params.get('centreId')).toBe(CENTER_ID);
    expect(options.params.get('page')).toBe('0');
  });

  it('liste les générateurs du centre', () => {
    service.listerGenerateurs(CENTER_ID).subscribe();
    const [url, options] = http.get.mock.calls[0] as [string, { params: { get: (k: string) => string } }];
    expect(url).toBe('/api/v1/organisation/referentiels/generateurs');
    expect(options.params.get('centreId')).toBe(CENTER_ID);
  });

  it('liste les centres payeurs du centre', () => {
    service.listerCentresPayeurs(CENTER_ID).subscribe();
    const [url, options] = http.get.mock.calls[0] as [string, { params: { get: (k: string) => string } }];
    expect(url).toBe('/api/v1/organisation/referentiels/centres-payeurs');
    expect(options.params.get('centreId')).toBe(CENTER_ID);
  });

  it('liste les forfaits du centre', () => {
    service.listerForfaits(CENTER_ID).subscribe();
    const [url, options] = http.get.mock.calls[0] as [string, { params: { get: (k: string) => string } }];
    expect(url).toBe('/api/v1/organisation/referentiels/forfaits');
    expect(options.params.get('centreId')).toBe(CENTER_ID);
  });

  it('liste les états patient statiques', () => {
    service.listerEtatsPatients().subscribe();
    expect(http.get).toHaveBeenCalledWith('/api/v1/organisation/referentiels/etats-patients');
  });
});
