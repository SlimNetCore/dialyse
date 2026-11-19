import {TestBed} from '@angular/core/testing';
import {provideZonelessChangeDetection} from '@angular/core';
import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {afterEach, beforeEach, describe, expect, it} from 'vitest';
import {BackendApiService, DetailDeFacture, PageDeFactures} from './backend-api.service';
import {environment} from '../../../environments/environment';

const CENTRE = '11111111-1111-1111-1111-111111111111';

describe('BackendApiService — consultation des factures émises', () => {
  let api: BackendApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(BackendApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('liste les factures avec la période, la page, le tri et tous les filtres renseignés', () => {
    let recu: PageDeFactures | undefined;
    api.listFactures(CENTRE, {
      du: '2026-11-01', au: '2026-11-30', page: 2, size: 50,
      sort: {champ: 'montantTtc', direction: 'desc'},
      numero: ' FAC-2026 ', patient: 'benali', caisseId: 'caisse-1', etat: 'REGLEE', montantMin: 0, montantMax: 5000.5,
    }).subscribe((page) => (recu = page));

    const req = http.expectOne((r) => r.url === `${environment.apiBaseUrl}/facturation/factures`);
    expect(req.request.method).toBe('GET');
    const p = req.request.params;
    expect(p.get('centerId')).toBe(CENTRE);
    expect(p.get('du')).toBe('2026-11-01');
    expect(p.get('au')).toBe('2026-11-30');
    expect(p.get('page')).toBe('2');
    expect(p.get('size')).toBe('50');
    expect(p.get('sort')).toBe('montantTtc,desc');
    expect(p.get('numero')).toBe('FAC-2026');
    expect(p.get('patient')).toBe('benali');
    expect(p.get('caisseId')).toBe('caisse-1');
    expect(p.get('etat')).toBe('REGLEE');
    expect(p.get('montantMin')).toBe('0');
    expect(p.get('montantMax')).toBe('5000.5');
    const reponse = {items: [], total: 0, page: 2, size: 50, du: '2026-11-01', au: '2026-11-30', totaux: {
      nombreDeFactures: 0, montantHt: 0, montantTva: 0, montantTtc: 0, montantRegle: 0, reste: 0}} as PageDeFactures;
    req.flush(reponse);
    expect(recu).toEqual(reponse);
  });

  it('omet les critères vides et une période incomplète (le serveur prend alors le mois courant)', () => {
    api.listFactures(CENTRE, {du: '2026-11-01', au: null, page: 0, size: 20, numero: '  ', patient: '', etat: null})
      .subscribe();

    const req = http.expectOne((r) => r.url === `${environment.apiBaseUrl}/facturation/factures`);
    expect(req.request.params.keys().sort()).toEqual(['centerId', 'page', 'size']);
    req.flush({items: [], total: 0, page: 0, size: 20, du: '2026-11-01', au: '2026-11-30', totaux: {}});
  });

  it('lit le détail d\'une facture sur /facturation/factures/{id}/lignes borné au centre', () => {
    let recu: DetailDeFacture | undefined;
    api.getFactureDetail(CENTRE, 'fac-1').subscribe((d) => (recu = d));

    const req = http.expectOne((r) => r.url === `${environment.apiBaseUrl}/facturation/factures/fac-1/lignes`);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('centerId')).toBe(CENTRE);
    const detail = {factureId: 'fac-1', numeroFacture: 'FAC-1', tauxTva: 9, lignes: [], seances: [],
      totaux: {montantHt: 0, montantTva: 0, montantTtc: 0, montantRegle: 0, reste: 0}};
    req.flush(detail);
    expect(recu).toEqual(detail);
  });
});
