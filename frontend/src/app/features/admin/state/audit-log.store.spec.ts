import {TestBed} from '@angular/core/testing';
import {of, throwError} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {AuditApiService, AuditEntry} from '../../../core/api/audit-api.service';
import {AuditLogStore} from './audit-log.store';

describe('AuditLogStore', () => {
  let api: { search: ReturnType<typeof vi.fn>; actionCodes: ReturnType<typeof vi.fn> };
  let store: InstanceType<typeof AuditLogStore>;

  const entry: AuditEntry = {
    occurredAt: '2026-09-27T10:00:00Z', userId: 'u1', username: 'dr.smith', roles: 'ROLE_MEDECIN',
    centerId: 'c1', societeId: null, actionCode: 'PATIENTS_MODIFICATION', entityType: 'patients',
    entityId: 'p1', libelle: 'Modification : patients/{id}', httpMethod: 'PUT', routeTemplate: '/api/v1/patients/{id}',
    statusCode: 200, durationMs: 12, ipAddress: '127.0.0.1',
  };

  beforeEach(() => {
    api = {search: vi.fn(), actionCodes: vi.fn()};
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({providers: [{provide: AuditApiService, useValue: api}]});
    store = TestBed.inject(AuditLogStore);
  });

  it('charge une page et expose le total', async () => {
    api.search.mockReturnValue(of({items: [entry], total: 1, page: 0, size: 20}));

    await store.load();

    expect(store.rows()).toEqual([entry]);
    expect(store.total()).toBe(1);
    expect(store.loading()).toBe(false);
    expect(store.error()).toBe(false);
  });

  it('vide la liste et signale une erreur en cas d\'échec', async () => {
    api.search.mockReturnValue(throwError(() => new Error('500')));

    await store.load();

    expect(store.rows()).toEqual([]);
    expect(store.total()).toBe(0);
    expect(store.error()).toBe(true);
  });

  it('remet la pagination à 0 quand un filtre change', async () => {
    api.search.mockReturnValue(of({items: [], total: 0, page: 0, size: 20}));
    store.setPagination(3, 50);

    store.setFilters({actionCode: 'PATIENTS_MODIFICATION'});

    expect(store.pageIndex()).toBe(0);
    expect(store.filters().actionCode).toBe('PATIENTS_MODIFICATION');
  });

  it('transmet les filtres actifs à la recherche, en omettant les champs vides', async () => {
    api.search.mockReturnValue(of({items: [], total: 0, page: 0, size: 20}));
    store.setFilters({userId: 'u1', societeId: '', from: '2026-09-01'});

    await store.load();

    expect(api.search).toHaveBeenCalledWith(expect.objectContaining({
      userId: 'u1', societeId: undefined, from: '2026-09-01', page: 0, size: 20,
    }));
  });

  it('réinitialise les filtres', () => {
    store.setFilters({userId: 'u1'});

    store.clearFilters();

    expect(store.filters()).toEqual({centerId: '', societeId: '', userId: '', actionCode: '', from: '', to: ''});
  });

  it('charge les codes d\'action disponibles', async () => {
    api.actionCodes.mockReturnValue(of(['PATIENTS_MODIFICATION', 'STOCK_CREATION']));

    await store.loadActionCodes();

    expect(store.actionCodes()).toEqual(['PATIENTS_MODIFICATION', 'STOCK_CREATION']);
  });
});
