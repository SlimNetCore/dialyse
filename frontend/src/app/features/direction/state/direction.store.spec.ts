import {TestBed} from '@angular/core/testing';
import {of, throwError} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {DirectionApiService, Snapshot} from '../../../core/api/direction-api.service';
import {DirectionStore, LIVE_REPORT} from './direction.store';

describe('DirectionStore — rapport de la période', () => {
  let api: { downloadLiveReport: ReturnType<typeof vi.fn>; getSnapshot: ReturnType<typeof vi.fn> };
  let store: InstanceType<typeof DirectionStore>;

  beforeEach(() => {
    api = {downloadLiveReport: vi.fn(), getSnapshot: vi.fn()};
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({providers: [{provide: DirectionApiService, useValue: api}]});
    store = TestBed.inject(DirectionStore);
  });

  it('renvoie le PDF et libère l\'indicateur d\'occupation', async () => {
    const pdf = new Blob(['%PDF'], {type: 'application/pdf'});
    api.downloadLiveReport.mockReturnValue(of(pdf));

    const result = await store.liveReport();

    expect(result).toBe(pdf);
    expect(store.reportBusy()).toBeNull();
    expect(store.reportError()).toBe(false);
  });

  it('signale l\'occupation pendant la production du rapport', async () => {
    let busyDuring: string | null = null;
    api.downloadLiveReport.mockImplementation(() => {
      busyDuring = store.reportBusy();
      return of(new Blob());
    });

    await store.liveReport();

    expect(busyDuring).toBe(LIVE_REPORT);
  });

  it('demande la période sans paramètre vide', async () => {
    api.downloadLiveReport.mockReturnValue(of(new Blob()));

    await store.liveReport();

    expect(api.downloadLiveReport).toHaveBeenCalledWith(undefined, undefined);
  });

  it('remonte l\'échec sans PDF', async () => {
    api.downloadLiveReport.mockReturnValue(throwError(() => new Error('boom')));

    expect(await store.liveReport()).toBeNull();
    expect(store.reportBusy()).toBeNull();
    expect(store.reportError()).toBe(true);
  });
});

describe('DirectionStore — comparateur de mois figés', () => {
  let api: { getSnapshot: ReturnType<typeof vi.fn> };
  let store: InstanceType<typeof DirectionStore>;
  const snapshot = (mois: string): Snapshot =>
    ({mois, generatedAt: '2026-09-01T00:00:00Z', overview: {} as never, indicators: {} as never, breakdown: null});

  beforeEach(() => {
    api = {getSnapshot: vi.fn()};
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({providers: [{provide: DirectionApiService, useValue: api}]});
    store = TestBed.inject(DirectionStore);
  });

  it('charge un mois dans l\'emplacement demandé sans toucher à l\'autre', async () => {
    api.getSnapshot.mockReturnValue(of(snapshot('2026-08')));

    await store.loadCompare('a', '2026-08');

    expect(store.compareA()).toEqual(snapshot('2026-08'));
    expect(store.compareB()).toBeUndefined();
    expect(api.getSnapshot).toHaveBeenCalledWith('2026-08');
  });

  it('charge les deux emplacements indépendamment', async () => {
    api.getSnapshot.mockImplementation((mois: string) => of(snapshot(mois)));

    await store.loadCompare('a', '2026-08');
    await store.loadCompare('b', '2026-07');

    expect(store.compareA()?.mois).toBe('2026-08');
    expect(store.compareB()?.mois).toBe('2026-07');
  });

  it('remonte l\'échec sans casser l\'autre emplacement déjà chargé', async () => {
    api.getSnapshot.mockReturnValueOnce(of(snapshot('2026-08')));
    await store.loadCompare('a', '2026-08');

    api.getSnapshot.mockReturnValueOnce(throwError(() => new Error('404')));
    await store.loadCompare('b', '2026-06');

    expect(store.compareA()?.mois).toBe('2026-08');
    expect(store.compareB()).toBeNull();
    expect(store.compareError()).toBe(true);
  });

  it('réinitialise le comparateur', async () => {
    api.getSnapshot.mockReturnValue(of(snapshot('2026-08')));
    await store.loadCompare('a', '2026-08');

    store.clearCompare();

    expect(store.compareA()).toBeUndefined();
    expect(store.compareB()).toBeUndefined();
    expect(store.compareError()).toBe(false);
  });
});

describe('DirectionStore — historique des alertes', () => {
  let api: { alertsHistory: ReturnType<typeof vi.fn> };
  let store: InstanceType<typeof DirectionStore>;

  beforeEach(() => {
    api = {alertsHistory: vi.fn()};
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({providers: [{provide: DirectionApiService, useValue: api}]});
    store = TestBed.inject(DirectionStore);
  });

  it('charge l\'historique', async () => {
    const entry = {
      centerId: 'a', centre: 'A', code: 'STOCK_SOUS_SEUIL', severity: 'WARNING' as const,
      valeur: 2, firstSeenAt: '2026-09-01T00:00:00Z', resolvedAt: null
    };
    api.alertsHistory.mockReturnValue(of([entry]));

    await store.loadAlertHistory();

    expect(store.alertHistory()).toEqual([entry]);
  });

  it('vide la liste en cas d\'échec', async () => {
    api.alertsHistory.mockReturnValue(throwError(() => new Error('boom')));

    await store.loadAlertHistory();

    expect(store.alertHistory()).toEqual([]);
  });
});
