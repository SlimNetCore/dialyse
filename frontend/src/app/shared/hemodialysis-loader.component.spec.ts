import {SimpleChange} from '@angular/core';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {HemodialysisLoaderComponent} from './hemodialysis-loader.component';

const installMatchMedia = (matches = false) => {
  Object.defineProperty(window, 'matchMedia', {
    configurable: true,
    writable: true,
    value: vi.fn().mockImplementation((query: string) => ({
      matches,
      media: query,
      onchange: null,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      addListener: vi.fn(),
      removeListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  });
};

describe('HemodialysisLoaderComponent', () => {
  beforeEach(() => {
    vi.useRealTimers();
    installMatchMedia(false);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('should create and expose three circulating dots by default', () => {
    const component = new HemodialysisLoaderComponent();

    expect(component).toBeTruthy();
    expect((component as any).dots()).toEqual([
      {id: 'a1', color: 'var(--hemo-loader-arteriel)', begin: '0s'},
      {id: 'a2', color: 'var(--hemo-loader-arteriel)', begin: '-1s'},
      {id: 'v1', color: 'var(--hemo-loader-veineux)', begin: '-2s'},
    ]);
  });

  it('should display a reconnect counter and increment seconds while reconnecting', () => {
    vi.useFakeTimers();

    const component = new HemodialysisLoaderComponent();
    component.showReconnectingKidney = true;
    component.ngOnChanges({
      showReconnectingKidney: new SimpleChange(false, true, true),
    });

    vi.advanceTimersByTime(3_000);

    expect(component.reconnectSeconds()).toBe(3);
  });

  it('should enable reduced-motion fallback when the media query matches', () => {
    installMatchMedia(true);

    const component = new HemodialysisLoaderComponent();

    expect(component.reducedMotion()).toBe(true);
  });
});



