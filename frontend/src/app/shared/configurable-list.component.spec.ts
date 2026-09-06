import {provideZonelessChangeDetection} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {ConfigurableListComponent, SharedListColumn} from './configurable-list.component';

type DemoRow = {
  id: string;
  name: string;
  active: boolean;
};

describe('ConfigurableListComponent', () => {
  const rows: DemoRow[] = [
    {id: '1', name: 'Zara', active: false},
    {id: '2', name: 'Alice', active: true},
    {id: '3', name: 'Bob', active: true},
  ];

  const columns: SharedListColumn<DemoRow>[] = [
    {
      id: 'name',
      headerKey: 'COMMON.NAME',
      valueAccessor: (row) => row.name,
      sortable: true,
      resizable: true,
      filter: {type: 'text'},
    },
    {
      id: 'active',
      headerKey: 'COMMON.STATUS',
      valueAccessor: (row) => row.active,
      sortable: true,
      filter: {type: 'boolean'},
    },
  ];

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ConfigurableListComponent<DemoRow>],
      providers: [provideZonelessChangeDetection()],
    });
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(ConfigurableListComponent<DemoRow>);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should filter rows when a column filter is set', () => {
    const fixture = TestBed.createComponent(ConfigurableListComponent<DemoRow>);
    const component = fixture.componentInstance;

    fixture.componentRef.setInput('rows', rows);
    fixture.componentRef.setInput('columns', columns);

    component.onFilterValue('name', 'ali');

    expect(component.displayedRows().map((row) => row.name)).toEqual(['Alice']);
  });

  it('should sort rows asc then desc on repeated click', () => {
    const fixture = TestBed.createComponent(ConfigurableListComponent<DemoRow>);
    const component = fixture.componentInstance;

    fixture.componentRef.setInput('rows', rows);
    fixture.componentRef.setInput('columns', columns);

    component.onHeaderSort(columns[0]);
    expect(component.displayedRows().map((row) => row.name)).toEqual(['Alice', 'Bob', 'Zara']);

    component.onHeaderSort(columns[0]);
    expect(component.displayedRows().map((row) => row.name)).toEqual(['Zara', 'Bob', 'Alice']);
  });

  it('should emit filtersChange on update', () => {
    const fixture = TestBed.createComponent(ConfigurableListComponent<DemoRow>);
    const component = fixture.componentInstance;
    const listener = vi.fn();

    component.filtersChange.subscribe(listener);
    component.onFilterValue('active', 'true');

    expect(listener).toHaveBeenCalledWith({active: 'true'});
  });
});

