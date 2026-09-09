import {ComponentFixture, TestBed} from '@angular/core/testing';

import {NgTable} from './ng-table';

describe('NgTable', () => {
  let component: NgTable;
  let fixture: ComponentFixture<NgTable>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NgTable],
    }).compileComponents();

    fixture = TestBed.createComponent(NgTable);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
