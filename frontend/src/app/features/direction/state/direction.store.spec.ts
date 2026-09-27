import {TestBed} from '@angular/core/testing';
import {of, throwError} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {DirectionApiService} from '../../../core/api/direction-api.service';
import {DirectionStore, LIVE_REPORT} from './direction.store';

describe('DirectionStore — rapport de la période', () => {
  let api: { downloadLiveReport: ReturnType<typeof vi.fn> };
  let store: InstanceType<typeof DirectionStore>;

  beforeEach(() => {
    api = {downloadLiveReport: vi.fn()};
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
