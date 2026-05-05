import {computed} from '@angular/core';
import {patchState, signalStore, withComputed, withMethods, withState} from '@ngrx/signals';
import {withDevtools} from '@angular-architects/ngrx-toolkit';
import {createPagedListState, PagedListState} from '../../../core/state/paged-list-state.util';

type PatientListState = PagedListState<any> & {
  printingList: boolean;
  exportingList: boolean;
  printingRowId: string | null;
};

const initialState: PatientListState = {
  ...createPagedListState<any>(),
  printingList: false,
  exportingList: false,
  printingRowId: null
};

export const PatientListStore = signalStore(
  {providedIn: 'root'},
  withState(initialState),
  withDevtools('PatientListStore'),
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

    setPrintingList(printingList: boolean): void {
      patchState(store, {printingList});
    },

    setExportingList(exportingList: boolean): void {
      patchState(store, {exportingList});
    },

    setPrintingRowId(printingRowId: string | null): void {
      patchState(store, {printingRowId});
    },

    setPageData(rows: any[], total: number, pageIndex: number): void {
      patchState(store, {rows, total, pageIndex});
    },

    setPagination(pageIndex: number, pageSize: number): void {
      patchState(store, {pageIndex, pageSize});
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
    }
  }))
);





