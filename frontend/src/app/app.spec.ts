import {TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {TranslateModule} from '@ngx-translate/core';
import {App} from './app';
import {materialFormFieldDefaults} from './app.config';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App, NoopAnimationsModule, TranslateModule.forRoot()],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('should render router outlet', async () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('router-outlet')).toBeTruthy();
  });

  it('should have showAuthLoader computed signal', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance as any;

    expect(app.showAuthLoader()).toBeFalsy();
  });

  it('should implement OnDestroy', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;

    expect(app.ngOnDestroy).toBeDefined();
  });

  it('uses dynamic Material form field subscript sizing', () => {
    expect(materialFormFieldDefaults.subscriptSizing).toBe('dynamic');
  });
});
