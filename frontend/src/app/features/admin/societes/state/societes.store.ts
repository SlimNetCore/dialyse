import {inject} from '@angular/core';
import {patchState, signalStore, withMethods, withState} from '@ngrx/signals';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {firstValueFrom} from 'rxjs';
import {Societe, SocieteApiService} from '../../../../core/api/societe-api.service';
import {createSearchablePagedListState, SearchablePagedListState} from '../../../../core/state/paged-list-state.util';

type SocietesState = SearchablePagedListState<Societe> & { error: string | null };

const initialState: SocietesState = {...createSearchablePagedListState<Societe>(), error: null};

/** Liste paginée et recherchable des sociétés (SUPERADMIN). */
export const SocietesStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withDevtools('SocietesStore'),
  withMethods((store, api = inject(SocieteApiService)) => {
    const load = async (): Promise<void> => {
      patchState(store, {loading: true, error: null});
      try {
        const page = await firstValueFrom(api.list(store.searchTerm(), store.pageIndex(), store.pageSize()));
        patchState(store, {rows: page.items, total: page.total, loading: false});
      } catch {
        patchState(store, {loading: false, error: 'LOAD_ERROR'});
      }
    };
    return {
      load,
      async setSearch(searchTerm: string): Promise<void> {
        patchState(store, {searchTerm, pageIndex: 0});
        await load();
      },
      async setPagination(pageIndex: number, pageSize: number): Promise<void> {
        patchState(store, {pageIndex, pageSize});
        await load();
      },
    };
  }),
);
