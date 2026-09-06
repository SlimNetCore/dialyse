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

  it('should render loader or router outlet depending on startup state', async () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    const hasRouterOutlet = !!compiled.querySelector('router-outlet');
    const hasLoader = !!compiled.querySelector('app-hemodialysis-loader');
    expect(hasRouterOutlet || hasLoader).toBeTruthy();
  });

  it('should expose startup/connection loader computed signals', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance as any;

    expect(typeof app.showStartupLoader).toBe('function');
    expect(typeof app.showConnectionLoader).toBe('function');
  });

  it('should destroy cleanly', () => {
    const fixture = TestBed.createComponent(App);
    expect(() => fixture.destroy()).not.toThrow();
  });

  it('uses dynamic Material form field subscript sizing', () => {
    expect(materialFormFieldDefaults.subscriptSizing).toBe('dynamic');
  });
});
