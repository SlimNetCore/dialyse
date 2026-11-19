import {TestBed} from '@angular/core/testing';
import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {environment} from '../../../environments/environment';
import {StockPagesApiService} from './stock-pages-api.service';

const BASE = `${environment.apiBaseUrl}/stock`;

describe('StockPagesApiService', () => {
  let api: StockPagesApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({providers: [provideHttpClient(), provideHttpClientTesting()]});
    api = TestBed.inject(StockPagesApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('E1 : GET /dashboard/stock-valorise/page avec centerId, page, taille, tri et filtres', () => {
    api.etatDuStock('c-1', {page: '1', size: '20', sort: 'valeur,desc', quantite: '<=0'}).subscribe();
    const req = http.expectOne((r) => r.url === `${BASE}/dashboard/stock-valorise/page`);
    expect(req.request.params.get('centerId')).toBe('c-1');
    expect(req.request.params.get('sort')).toBe('valeur,desc');
    expect(req.request.params.get('quantite')).toBe('<=0');
    req.flush({items: [], total: 0, page: 1, size: 20});
  });

  it('E2 et E3 : pièces puis lignes de la pièce', () => {
    api.piecesPourSeances('c-1', {page: '0', size: '20', statut: 'RAPPROCHEE'}).subscribe();
    const pieces = http.expectOne((r) => r.url === `${BASE}/sorties-pour-seances/page`);
    expect(pieces.request.params.get('statut')).toBe('RAPPROCHEE');
    pieces.flush({items: [], total: 0, page: 0, size: 20});

    api.lignesDeLaPiece('c-1', 'p-1', {page: '0', size: '20', article: 'dia'}).subscribe();
    const lignes = http.expectOne((r) => r.url === `${BASE}/sorties-pour-seances/p-1/lignes/page`);
    expect(lignes.request.params.get('article')).toBe('dia');
    expect(lignes.request.params.get('centerId')).toBe('c-1');
    lignes.flush({items: [], total: 0, page: 0, size: 20});
  });

  it('E5 : GET /dashboard/pmp-explain/{articleId}/page sans tri', () => {
    api.explicationDuPmp('c-1', 'a-1', 2, 50).subscribe();
    const req = http.expectOne((r) => r.url === `${BASE}/dashboard/pmp-explain/a-1/page`);
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('50');
    expect(req.request.params.has('sort')).toBe(false);
    req.flush({items: [], total: 0, page: 2, size: 50});
  });
});
