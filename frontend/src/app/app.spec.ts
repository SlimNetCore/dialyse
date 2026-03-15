import { TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { TranslateModule } from '@ngx-translate/core';
import { App } from './app';

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

  it('should render shell title', async () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    // When no translations loaded, key is displayed as-is
    expect(compiled.textContent).toContain('APP.TITLE');
  });

  it('should expose a default center context', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;

    expect(app.store.currentCenterId()).toBe('11111111-1111-1111-1111-111111111111');
  });

  it('should have lang service with 4 languages', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;

    expect(app.lang.languages.length).toBe(4);
    expect(app.lang.languages.map(l => l.code)).toEqual(['fr', 'en', 'ar', 'kab']);
  });
});
