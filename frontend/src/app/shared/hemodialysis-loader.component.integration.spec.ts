import {SimpleChange} from '@angular/core';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {HemodialysisLoaderComponent} from './hemodialysis-loader.component';

describe('HemodialysisLoaderComponent integration', () => {
  beforeEach(() => {
    Object.defineProperty(window, 'matchMedia', {
      configurable: true,
      writable: true,
      value: vi.fn().mockImplementation((query: string) => ({
        matches: false,
        media: query,
        onchange: null,
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        addListener: vi.fn(),
        removeListener: vi.fn(),
        dispatchEvent: vi.fn(),
      })),
    });
  });

  it('should increase reconnect size floor and preserve faster dot offsets', () => {
    const component = new HemodialysisLoaderComponent();
    component.size = 96;
    component.speed = 2.4;
    component.showReconnectingKidney = true;
    const dots = (component as any).dots();

    expect((component as any).visualSize()).toBe(148);
    expect(dots[0]).toEqual({id: 'a1', color: 'var(--hemo-loader-arteriel)', begin: '0s'});
    expect(Number.parseFloat(dots[1].begin)).toBeCloseTo(-0.8, 5);
    expect(Number.parseFloat(dots[2].begin)).toBeCloseTo(-1.6, 5);
  });

  it('should reset the reconnect counter when reconnection stops', () => {
    vi.useFakeTimers();

    const component = new HemodialysisLoaderComponent();
    component.showReconnectingKidney = true;
    component.ngOnChanges({
      showReconnectingKidney: new SimpleChange(false, true, true),
    });

    vi.advanceTimersByTime(2_000);
    expect(component.reconnectSeconds()).toBe(2);

    component.showReconnectingKidney = false;
    component.ngOnChanges({
      showReconnectingKidney: new SimpleChange(true, false, false),
    });

    expect(component.reconnectSeconds()).toBe(0);
  });
});




