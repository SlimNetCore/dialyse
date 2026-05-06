import {inject} from '@angular/core';
import {patchState, signalStore, withMethods, withState} from '@ngrx/signals';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {firstValueFrom, Observable} from 'rxjs';
import {ReferentialApiService} from '../api/referential-api.service';

export type ReferentialStoreState<TItem> = {
  items: TItem[];
  loading: boolean;
  loadedCenterId: string | null;
  error: string | null;
};

type ReferentialLoadFn<TRow> = (api: ReferentialApiService, centerId: string) => Observable<TRow[]>;

function createInitialState<TItem>(): ReferentialStoreState<TItem> {
  return {
    items: [],
    loading: false,
    loadedCenterId: null,
    error: null
  };
}

export function createReferentialStore<TRow, TItem = TRow>(options: {
  storeName: string;
  load: ReferentialLoadFn<TRow>;
  mapItem?: (row: TRow) => TItem;
}) {
  return signalStore(
    {providedIn: 'root'},
    withState<ReferentialStoreState<TItem>>(createInitialState<TItem>()),
    withDevtools(options.storeName),
    withMethods((store, api = inject(ReferentialApiService)) => ({
      async ensureLoaded(centerId: string | null | undefined): Promise<void> {
        const normalizedCenterId = (centerId ?? '').trim();
        if (!normalizedCenterId) {
          patchState(store, createInitialState<TItem>());
          return;
        }

        if (store.loadedCenterId() === normalizedCenterId && (store.loading() || !store.error())) {
          return;
        }

        patchState(store, {
          loading: true,
          error: null,
          loadedCenterId: normalizedCenterId
        });

        try {
          const rows = await firstValueFrom(options.load(api, normalizedCenterId));
          const items = (rows ?? []).map((row) => options.mapItem ? options.mapItem(row) : (row as unknown as TItem));
          patchState(store, {
            items,
            loading: false,
            error: null,
            loadedCenterId: normalizedCenterId
          });
        } catch (err: any) {
          patchState(store, {
            items: [],
            loading: false,
            error: err?.error?.message || err?.statusText || `Erreur chargement ${options.storeName}`,
            loadedCenterId: normalizedCenterId
          });
        }
      },

      clear(): void {
        patchState(store, createInitialState<TItem>());
      }
    }))
  );
}


