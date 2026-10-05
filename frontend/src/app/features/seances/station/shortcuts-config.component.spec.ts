import {TestBed} from '@angular/core/testing';
import {provideZonelessChangeDetection} from '@angular/core';
import {TranslateModule} from '@ngx-translate/core';
import {beforeEach, describe, expect, it} from 'vitest';
import {ShortcutsConfigComponent} from './shortcuts-config.component';
import {SHORTCUTS_MAX} from './station.util';

const articles = Array.from({length: SHORTCUTS_MAX + 2}, (_, i) => ({
  id: `a${i}`, centerId: 'c', code: `A${i}`, libelle: `Article ${i}`, active: true,
}));

describe('ShortcutsConfigComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ShortcutsConfigComponent, TranslateModule.forRoot()],
      providers: [provideZonelessChangeDetection()],
    });
  });

  function render(selected: string[] = []) {
    const fixture = TestBed.createComponent(ShortcutsConfigComponent);
    fixture.componentRef.setInput('articles', articles);
    fixture.componentRef.setInput('selected', selected);
    fixture.detectChanges();
    const saved: string[][] = [];
    let cancelled = 0;
    fixture.componentInstance.saved.subscribe((v) => saved.push(v));
    fixture.componentInstance.cancelled.subscribe(() => cancelled++);
    return {fixture, root: fixture.nativeElement as HTMLElement, saved, cancelled: () => cancelled};
  }

  it('part de la liste enregistrée et numérote les articles dans l\'ordre', () => {
    const {root} = render(['a2', 'a0']);
    const ranks = Array.from(root.querySelectorAll('.chip-rank')).map((r) => r.textContent?.trim());
    expect(ranks.sort()).toEqual(['1', '2']);
    expect(root.querySelectorAll('.shortcut-chip.on')).toHaveLength(2);
  });

  it('touche pour ajouter dans l\'ordre, retouche pour retirer, et émet la sélection', () => {
    const {fixture, root, saved} = render();
    const chips = root.querySelectorAll<HTMLButtonElement>('.shortcut-chip');

    chips[3].click();
    chips[1].click();
    chips[3].click();
    fixture.detectChanges();
    root.querySelector<HTMLButtonElement>('button[color="primary"]')!.click();

    expect(saved).toEqual([['a1']]);
  });

  it('ne dépasse pas la limite du serveur', () => {
    const {fixture, root, saved} = render();
    root.querySelectorAll<HTMLButtonElement>('.shortcut-chip').forEach((chip) => chip.click());
    fixture.detectChanges();
    root.querySelector<HTMLButtonElement>('button[color="primary"]')!.click();

    expect(saved[0]).toHaveLength(SHORTCUTS_MAX);
  });

  it('annuler n\'enregistre rien', () => {
    const {root, saved, cancelled} = render();
    root.querySelector<HTMLButtonElement>('.shortcut-actions button')!.click();
    expect(cancelled()).toBe(1);
    expect(saved).toEqual([]);
  });

  it('désactive l\'enregistrement pendant la sauvegarde', () => {
    const {fixture, root} = render();
    fixture.componentRef.setInput('saving', true);
    fixture.detectChanges();
    expect(root.querySelector<HTMLButtonElement>('button[color="primary"]')!.disabled).toBe(true);
  });
});
