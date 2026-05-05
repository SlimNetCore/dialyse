import {computed} from '@angular/core';
import {patchState, signalStore, withComputed, withMethods, withState} from '@ngrx/signals';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {AppUser} from '../../../core/api/admin-api.service';
import {createSearchablePagedListState, SearchablePagedListState} from '../../../core/state/paged-list-state.util';

type UserListState = SearchablePagedListState<AppUser> & {
  visibleColumns: Record<string, boolean>;
  openFilterColumn: string | null;
};

const initialState: UserListState = {
  ...createSearchablePagedListState<AppUser>(),
  visibleColumns: {
    username: true,
    fullName: true,
    email: true,
    roles: true,
    centers: true,
    active: true,
    actions: true
  },
  openFilterColumn: null
};

export const UserListStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withDevtools('UserListStore'),
  withComputed((store) => ({
    hasActiveFilters: computed(() =>
      Object.values(store.columnFilters()).some((v) => !!v?.toString().trim())
    ),
    isEmpty: computed(() => store.rows().length === 0)
  })),
  withMethods((store) => ({
    setLoading(loading: boolean): void {
      patchState(store, {loading});
    },

    setPageData(rows: AppUser[], total: number, pageIndex: number): void {
      patchState(store, {rows, total, pageIndex});
    },

    setPagination(pageIndex: number, pageSize: number): void {
      patchState(store, {pageIndex, pageSize});
    },

    setSearchTerm(searchTerm: string): void {
      patchState(store, {searchTerm});
    },

    setFilter(column: string, value: string): void {
      patchState(store, {
        columnFilters: {
          ...store.columnFilters(),
          [column]: value
        }
      });
    },

    clearFilter(column: string): void {
      patchState(store, {
        columnFilters: {
          ...store.columnFilters(),
          [column]: ''
        }
      });
    },

    clearAllFilters(): void {
      patchState(store, {columnFilters: {}});
    },

    setVisibleColumn(column: string, visible: boolean): void {
      patchState(store, {
        visibleColumns: {
          ...store.visibleColumns(),
          [column]: visible
        }
      });
    },

    toggleFilterPanel(column: string): void {
      patchState(store, {
        openFilterColumn: store.openFilterColumn() === column ? null : column
      });
    },

    closeFilterPanel(): void {
      patchState(store, {openFilterColumn: null});
    }
  }))
);




