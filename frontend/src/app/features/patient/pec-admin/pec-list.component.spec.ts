import {TestBed} from '@angular/core/testing';
import {provideZonelessChangeDetection} from '@angular/core';
import {ActivatedRoute, convertToParamMap} from '@angular/router';
import {TranslateModule} from '@ngx-translate/core';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {of} from 'rxjs';
import {PecListComponent, statutPecDepuisUrl} from './pec-list.component';
import {PecListStore} from './state/pec-list.store';
import {BackendApiService} from '../../../core/api/backend-api.service';
import {AppShellStore} from '../../../core/state/app-shell.store';

describe('statutPecDepuisUrl', () => {
  it('ne retient qu\'un statut de PEC connu', () => {
    expect(statutPecDepuisUrl('CREE')).toBe('CREE');
    expect(statutPecDepuisUrl('VALIDEE')).toBe('VALIDEE');
    expect(statutPecDepuisUrl('CLOTUREE')).toBe('CLOTUREE');
    expect(statutPecDepuisUrl('')).toBe('');
    expect(statutPecDepuisUrl(null)).toBe('');
    expect(statutPecDepuisUrl('<script>')).toBe('');
  });
});

describe('PecListComponent — filtre de statut transmis par l\'URL', () => {
  let listPecsDetailed: ReturnType<typeof vi.fn>;

  function configurer(query: Record<string, string>) {
    listPecsDetailed = vi.fn(() => of({items: [], total: 0, page: 0}));
    TestBed.configureTestingModule({
      imports: [PecListComponent, TranslateModule.forRoot()],
      providers: [
        provideZonelessChangeDetection(),
        {provide: ActivatedRoute, useValue: {snapshot: {queryParamMap: convertToParamMap(query)}}},
        {provide: BackendApiService, useValue: {listPecsDetailed}},
        {provide: AppShellStore, useValue: {currentCenterId: () => 'c1'}},
      ],
    });
  }

  beforeEach(() => TestBed.resetTestingModule());

  it('?statut=VALIDEE charge la liste filtrée sur les PEC validées', () => {
    configurer({statut: 'VALIDEE'});
    TestBed.inject(PecListStore).clearAllFilters();
    const fixture = TestBed.createComponent(PecListComponent);
    fixture.componentInstance.ngOnInit();

    expect(listPecsDetailed).toHaveBeenLastCalledWith('c1', expect.objectContaining({
      filters: expect.objectContaining({statut: 'VALIDEE'}),
    }));
  });

  it('?statut= vide retire le filtre de statut mémorisé', () => {
    configurer({statut: ''});
    TestBed.inject(PecListStore).setFilter('statut', 'CREE');
    const fixture = TestBed.createComponent(PecListComponent);
    fixture.componentInstance.ngOnInit();

    expect(TestBed.inject(PecListStore).columnFilters()['statut']).toBe('');
  });

  it('sans paramètre, le filtre mémorisé est conservé', () => {
    configurer({});
    TestBed.inject(PecListStore).setFilter('statut', 'CREE');
    const fixture = TestBed.createComponent(PecListComponent);
    fixture.componentInstance.ngOnInit();

    expect(TestBed.inject(PecListStore).columnFilters()['statut']).toBe('CREE');
  });
});
