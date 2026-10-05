import {TestBed} from '@angular/core/testing';
import {provideZonelessChangeDetection} from '@angular/core';
import {TranslateModule} from '@ngx-translate/core';
import {beforeEach, describe, expect, it} from 'vitest';
import {PatientPagerComponent} from './patient-pager.component';

describe('PatientPagerComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [PatientPagerComponent, TranslateModule.forRoot()],
      providers: [provideZonelessChangeDetection()],
    });
  });

  function render(position: number, total: number) {
    const fixture = TestBed.createComponent(PatientPagerComponent);
    fixture.componentRef.setInput('position', position);
    fixture.componentRef.setInput('total', total);
    fixture.detectChanges();
    const events: string[] = [];
    fixture.componentInstance.previous.subscribe(() => events.push('previous'));
    fixture.componentInstance.next.subscribe(() => events.push('next'));
    return {root: fixture.nativeElement as HTMLElement, events};
  }

  it('n\'affiche rien hors de la file ou avec un seul patient', () => {
    expect(render(-1, 5).root.querySelector('.pager')).toBeNull();
    expect(render(0, 1).root.querySelector('.pager')).toBeNull();
  });

  it('affiche le rang et émet précédent / suivant', () => {
    const {root, events} = render(1, 3);

    expect(root.querySelector('.pager-pos')?.textContent?.trim()).toBe('2 / 3');
    root.querySelector<HTMLButtonElement>('.pager-prev')!.click();
    root.querySelector<HTMLButtonElement>('.pager-next')!.click();

    expect(events).toEqual(['previous', 'next']);
  });

  it('désactive précédent au premier patient et suivant au dernier', () => {
    expect(render(0, 3).root.querySelector<HTMLButtonElement>('.pager-prev')!.disabled).toBe(true);
    expect(render(2, 3).root.querySelector<HTMLButtonElement>('.pager-next')!.disabled).toBe(true);
    expect(render(1, 3).root.querySelector<HTMLButtonElement>('.pager-next')!.disabled).toBe(false);
  });
});
