import {provideZonelessChangeDetection} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {HttpErrorResponse} from '@angular/common/http';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {of, throwError} from 'rxjs';
import {GroupeArticle, GroupesArticlesApiService} from '../../../core/api/groupes-articles-api.service';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {groupeErrorKey, GroupesArticlesStore} from './groupes-articles.store';

const CENTRE = '11111111-1111-1111-1111-111111111111';

function groupe(overrides: Partial<GroupeArticle> = {}): GroupeArticle {
  return {
    id: 'g1',
    nom: 'KIT CNAS',
    description: null,
    nbArticles: 2,
    articleIds: ['a1', 'a2'],
    updatedAt: '2026-01-01T00:00:00Z', ...overrides
  };
}

describe('GroupesArticlesStore', () => {
  let api: {
    list: ReturnType<typeof vi.fn>;
    create: ReturnType<typeof vi.fn>;
    update: ReturnType<typeof vi.fn>;
    delete: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    api = {
      list: vi.fn().mockReturnValue(of({items: [groupe()], total: 1, page: 0, size: 10})),
      create: vi.fn().mockReturnValue(of(groupe())),
      update: vi.fn().mockReturnValue(of(groupe())),
      delete: vi.fn().mockReturnValue(of(undefined)),
    };
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), {provide: GroupesArticlesApiService, useValue: api}],
    });
    TestBed.inject(AppShellStore).switchCenter(CENTRE);
  });

  it('charge une page de groupes du centre actif', () => {
    const store = TestBed.inject(GroupesArticlesStore);

    store.loadPage({page: 0, size: 10});

    expect(api.list).toHaveBeenCalledWith(CENTRE, 0, 10);
    expect(store.rows()).toHaveLength(1);
    expect(store.total()).toBe(1);
    expect(store.isEmpty()).toBe(false);
  });

  it('crée, modifie et supprime pour le centre actif puis signale le succès', () => {
    const store = TestBed.inject(GroupesArticlesStore);
    const payload = {nom: 'KIT CNAS', description: null, articleIds: ['a1']};

    store.create(payload);
    expect(api.create).toHaveBeenCalledWith(CENTRE, payload);
    expect(store.successMessage()).toBe('GROUPES_ARTICLES.SAVED_OK');

    store.clearMessages();
    store.update({id: 'g1', payload});
    expect(api.update).toHaveBeenCalledWith(CENTRE, 'g1', payload);

    store.remove('g1');
    expect(api.delete).toHaveBeenCalledWith(CENTRE, 'g1');
    expect(store.saving()).toBe(false);
  });

  it('traduit le code métier d\'un nom en doublon', () => {
    api.create.mockReturnValue(throwError(() => new HttpErrorResponse({
      status: 409, error: {code: 'GROUPE_ARTICLE_NOM_EXISTANT'},
    })));
    const store = TestBed.inject(GroupesArticlesStore);

    store.create({nom: 'KIT CNAS', description: null, articleIds: ['a1']});

    expect(store.error()).toBe('GROUPES_ARTICLES.ERR.GROUPE_ARTICLE_NOM_EXISTANT');
    expect(store.saving()).toBe(false);
  });

  it('retombe sur l\'erreur générique pour une erreur inconnue', () => {
    expect(groupeErrorKey(new HttpErrorResponse({status: 500, error: {code: 'INTERNAL_ERROR'}})))
      .toBe('GROUPES_ARTICLES.ERR.SAVE');
    expect(groupeErrorKey(new Error('x'))).toBe('GROUPES_ARTICLES.ERR.SAVE');
  });

  it('expose une erreur de chargement', () => {
    api.list.mockReturnValue(throwError(() => new Error('boom')));
    const store = TestBed.inject(GroupesArticlesStore);

    store.loadPage({page: 0, size: 10});

    expect(store.error()).toBe('GROUPES_ARTICLES.ERR.LOAD');
    expect(store.rows()).toEqual([]);
  });
});
