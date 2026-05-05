import {computed, inject} from '@angular/core';
import {patchState, signalStore, withComputed, withMethods, withState} from '@ngrx/signals';
import {rxMethod} from '@ngrx/signals/rxjs-interop';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {catchError, of, pipe, switchMap, tap} from 'rxjs';
import {AdminApiService, AppUser} from '../../../core/api/admin-api.service';
import {createSearchablePagedListState, SearchablePagedListState} from '../../../core/state/paged-list-state.util';

type UserListState = SearchablePagedListState<AppUser> & {
  visibleColumns: Record<string, boolean>;
  openFilterColumn: string | null;
  error: string | null;
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
  openFilterColumn: null,
  error: null
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
  withMethods((store, api = inject(AdminApiService)) => ({
    // ✅ Fetch paginé avec filtres dans le store
    loadPage: rxMethod<{ page: number; size: number }>(
      pipe(
        tap(() => patchState(store, {loading: true, error: null})),
        switchMap(({page, size}) =>
          api.listUsersPaged({
            page,
            size,
            search: store.searchTerm(),
            filters: store.columnFilters()
          }).pipe(
            tap((res: any) => patchState(store, {
              rows: res.items ?? [],
              total: res.total ?? 0,
              pageIndex: res.page ?? page,
              loading: false
            })),
            catchError((err: any) => {
              patchState(store, {
                rows: [],
                total: 0,
                pageIndex: page,
                loading: false,
                error: err?.error?.message || err?.statusText || 'Erreur lors du chargement'
              });
              return of(null);
            })
          )
        )
      )
    ),

    // ✅ Suppression utilisateur dans le store
    deleteUser: rxMethod<string>(
      pipe(
        tap(() => patchState(store, {error: null})),
        switchMap((userId) =>
          api.deleteUser(userId).pipe(
            tap(() => {
              // Rafraîchir la liste après suppression
              const page = store.pageIndex();
              const size = store.pageSize();
              patchState(store, {loading: true});
              api.listUsersPaged({
                page,
                size,
                search: store.searchTerm(),
                filters: store.columnFilters()
              }).pipe(
                tap((res: any) => patchState(store, {
                  rows: res.items ?? [],
                  total: res.total ?? 0,
                  pageIndex: res.page ?? page,
                  loading: false
                })),
                catchError((err: any) => {
                  patchState(store, {
                    error: err?.error?.message || err?.statusText || 'Erreur',
                    loading: false
                  });
                  return of(null);
                })
              ).subscribe();
            }),
            catchError((err: any) => {
              patchState(store, {
                error: err?.error?.message || err?.statusText || 'Erreur suppression'
              });
              return of(null);
            })
          )
        )
      )
    ),

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







