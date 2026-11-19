import {TestBed} from '@angular/core/testing';
import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {Observable} from 'rxjs';
import {environment} from '../../../environments/environment';
import {paramsDePage, StockApiService} from './stock-api.service';

describe('StockApiService — listes paginées côté serveur (pagination-serveur B1–B4)', () => {
  let api: StockApiService;
  let http: HttpTestingController;
  const base = `${environment.apiBaseUrl}/stock`;

  beforeEach(() => {
    TestBed.configureTestingModule({providers: [provideHttpClient(), provideHttpClientTesting()]});
    api = TestBed.inject(StockApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('paramsDePage ajoute centerId et ignore les valeurs vides', () => {
    const params = paramsDePage('c1', {page: '0', size: '20', reference: '', statut: 'VALIDE'});
    expect(params.keys()).toEqual(['centerId', 'page', 'size', 'statut']);
    expect(params.get('statut')).toBe('VALIDE');
  });

  it.each([
    ['listFournisseursPage', '/referentiel/fournisseurs/page'],
    ['listBonsCommandePage', '/bons-commande/page'],
    ['listBonsReceptionPage', '/bons-reception/page'],
    ['listBonsSortiePage', '/bons-sortie/page'],
  ] as const)('%s appelle GET %s avec page, taille, tri et filtres', (methode, chemin) => {
    let recu: unknown;
    const lister = api[methode].bind(api) as (c: string, p: Record<string, string>) => Observable<unknown>;
    lister('c1', {page: '1', size: '50', sort: 'reference,desc', reference: 'BC'}).subscribe((p) => (recu = p));
    const req = http.expectOne((r) => r.url === `${base}${chemin}`);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('centerId')).toBe('c1');
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('size')).toBe('50');
    expect(req.request.params.get('sort')).toBe('reference,desc');
    expect(req.request.params.get('reference')).toBe('BC');
    const page = {items: [], total: 0, page: 1, size: 50};
    req.flush(page);
    expect(recu).toEqual(page);
  });
});
