export type PagedListState<TRow> = {
  rows: TRow[];
  loading: boolean;
  total: number;
  pageIndex: number;
  pageSize: number;
  columnFilters: Record<string, string>;
};

export type SearchablePagedListState<TRow> = PagedListState<TRow> & {
  searchTerm: string;
};

export function createPagedListState<TRow>(
  overrides: Partial<PagedListState<TRow>> = {}
): PagedListState<TRow> {
  return {
    rows: [],
    loading: false,
    total: 0,
    pageIndex: 0,
    pageSize: 10,
    columnFilters: {},
    ...overrides
  };
}

export function createSearchablePagedListState<TRow>(
  overrides: Partial<SearchablePagedListState<TRow>> = {}
): SearchablePagedListState<TRow> {
  return {
    ...createPagedListState<TRow>(),
    searchTerm: '',
    ...overrides
  };
}
