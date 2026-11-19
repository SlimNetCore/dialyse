import {TestBed} from '@angular/core/testing';
import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {environment} from '../../../environments/environment';
import {InventaireApiService} from './inventaire-api.service';

describe('InventaireApiService.lignesPage (feuille de comptage paginée, E4)', () => {
  let http: HttpTestingController;
  let api: InventaireApiService;

  beforeEach(() => {
    TestBed.configureTestingModule({providers: [provideHttpClient(), provideHttpClientTesting()]});
    http = TestBed.inject(HttpTestingController);
    api = TestBed.inject(InventaireApiService);
  });

  afterEach(() => http.verify());

  it('envoie centre, page, taille, tri, etat et q a GET /stock/inventaires/{id}/lignes/page', () => {
    let total = -1;
    api.lignesPage('c-1', 'inv-1', {page: '2', size: '50', sort: 'ecart,desc', etat: 'ECARTS', q: 'lot'})
      .subscribe((p) => total = p.total);

    const req = http.expectOne((r) => r.url === `${environment.apiBaseUrl}/stock/inventaires/inv-1/lignes/page`);
    expect(req.request.method).toBe('GET');
    const p = req.request.params;
    expect([p.get('centerId'), p.get('page'), p.get('size'), p.get('sort'), p.get('etat'), p.get('q')])
      .toEqual(['c-1', '2', '50', 'ecart,desc', 'ECARTS', 'lot']);
    req.flush({items: [], total: 7, page: 2, size: 50});
    expect(total).toBe(7);
  });
});
