import {provideZonelessChangeDetection} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {HttpErrorResponse} from '@angular/common/http';
import {of, throwError} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {ArticleFichePayload, ArticleStock, StockApiService} from '../../../core/api/stock-api.service';
import {BackendApiService} from '../../../core/api/backend-api.service';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {articleErrorKey, ArticlesStore} from './articles.store';

const CENTRE = '11111111-1111-1111-1111-111111111111';

function article(overrides: Partial<ArticleStock> = {}): ArticleStock {
  return {
    id: 'a1', centerId: CENTRE, code: 'EPO-4000', libelle: 'Époétine 4000 UI', unite: 'seringue', stockQuantity: 12,
    seuilAlerte: 10, pmpCourant: 2500, gereParLot: true, active: true, typeTraitementAnemie: 'EPO',
    dosageParUnite: 4000, uniteDosage: 'UI', ...overrides,
  };
}

const fiche: ArticleFichePayload = {
  centerId: '', code: 'EPO-4000', libelle: 'Époétine 4000 UI', unite: 'seringue', dosageParUnite: 4000,
  uniteDosage: 'UI', gereParLot: true, peremptionObligatoire: true, produitDangereux: false, dechetDasri: true,
};

describe('ArticlesStore', () => {
  let api: Record<string, ReturnType<typeof vi.fn>>;

  beforeEach(() => {
    api = {
      searchArticles: vi.fn().mockReturnValue(of({items: [article()], total: 1, page: 0, size: 20})),
      createArticle: vi.fn().mockReturnValue(of(article())),
      updateArticle: vi.fn().mockReturnValue(of(article())),
      setArticleActive: vi.fn().mockReturnValue(of(article({active: false}))),
      listFournisseurs: vi.fn().mockReturnValue(of([{id: 'f1', raisonSociale: 'Fresenius'}])),
    };
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        {provide: StockApiService, useValue: api},
        {
          provide: BackendApiService,
          useValue: {listTvaTypes: vi.fn().mockReturnValue(of({items: [{id: 't1', libelle: 'TVA 9', taux: 9}]}))}
        },
      ],
    });
    TestBed.inject(AppShellStore).switchCenter(CENTRE);
  });

  it('charge une page du centre actif avec la recherche et le filtre d\'état', () => {
    const store = TestBed.inject(ArticlesStore);

    store.loadPage({page: 0, size: 20});
    expect(api['searchArticles']).toHaveBeenCalledWith(CENTRE, {q: '', active: true, page: 0, size: 20});
    expect(store.rows()).toHaveLength(1);
    expect(store.total()).toBe(1);

    store.search('epo');
    expect(api['searchArticles']).toHaveBeenLastCalledWith(CENTRE, {q: 'epo', active: true, page: 0, size: 20});

    store.setActiveFilter('all');
    expect(api['searchArticles']).toHaveBeenLastCalledWith(CENTRE, {q: 'epo', active: null, page: 0, size: 20});
    store.setActiveFilter('inactive');
    expect(api['searchArticles']).toHaveBeenLastCalledWith(CENTRE, {q: 'epo', active: false, page: 0, size: 20});
  });

  it('crée, modifie et (dés)active avec le centre courant puis recharge la page', () => {
    api['searchArticles'].mockReturnValue(of({items: [article()], total: 30, page: 2, size: 10}));
    const store = TestBed.inject(ArticlesStore);
    store.loadPage({page: 2, size: 10});

    store.create(fiche);
    expect(api['createArticle']).toHaveBeenCalledWith({...fiche, centerId: CENTRE});
    expect(store.successMessage()).toBe('STOCK.ARTICLES.SAVED_OK');

    store.update({id: 'a1', payload: fiche});
    expect(api['updateArticle']).toHaveBeenCalledWith('a1', {...fiche, centerId: CENTRE});

    store.setActive({id: 'a1', active: false});
    expect(api['setArticleActive']).toHaveBeenCalledWith(CENTRE, 'a1', false);
    expect(api['searchArticles'].mock.calls.length).toBeGreaterThan(3);
    expect(api['searchArticles']).toHaveBeenLastCalledWith(CENTRE, expect.objectContaining({page: 2, size: 10}));
  });

  it('charge les fournisseurs et les types de TVA du centre', () => {
    const store = TestBed.inject(ArticlesStore);

    store.loadReferentiels();

    expect(api['listFournisseurs']).toHaveBeenCalledWith(CENTRE);
    expect(store.fournisseurs()).toHaveLength(1);
    expect(store.tvaTypes()).toHaveLength(1);
  });

  it('traduit les erreurs métier du serveur', () => {
    api['createArticle'].mockReturnValue(throwError(() =>
      new HttpErrorResponse({status: 422, error: {code: 'ARTICLE_CODE_EXISTANT'}})));
    const store = TestBed.inject(ArticlesStore);

    store.create(fiche);

    expect(store.error()).toBe('STOCK.ARTICLES.ERR.ARTICLE_CODE_EXISTANT');
    expect(store.saving()).toBe(false);
    expect(articleErrorKey(new Error('boom'))).toBe('STOCK.ARTICLES.ERR.SAVE');
  });
});
