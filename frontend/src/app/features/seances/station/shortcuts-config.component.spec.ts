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

  /** Affiche le composant puis déplie la configuration (il s'ouvre par un bouton « Choisir les raccourcis »). */
  function render(selected: string[] = []) {
    const fixture = TestBed.createComponent(ShortcutsConfigComponent);
    fixture.componentRef.setInput('articles', articles);
    fixture.componentRef.setInput('selected', selected);
    fixture.detectChanges();
    const saved: string[][] = [];
    let cancelled = 0;
    fixture.componentInstance.saved.subscribe((v) => saved.push(v));
    fixture.componentInstance.cancelled.subscribe(() => cancelled++);
    const root = fixture.nativeElement as HTMLElement;
    const open = () => {
      root.querySelector<HTMLButtonElement>('.link-btn')!.click();
      fixture.detectChanges();
    };
    return {fixture, root, saved, cancelled: () => cancelled, open};
  }

  it('reste replié derrière un bouton tant qu\'on ne l\'ouvre pas', () => {
    const {root} = render();
    expect(root.querySelector('.link-btn')).not.toBeNull();
    expect(root.querySelector('.shortcut-config')).toBeNull();
  });

  it('part de la liste enregistrée et numérote les articles dans l\'ordre', () => {
    const {root, open} = render(['a2', 'a0']);
    open();
    const ranks = Array.from(root.querySelectorAll('.chip-rank')).map((r) => r.textContent?.trim());
    expect(ranks.sort()).toEqual(['1', '2']);
    expect(root.querySelectorAll('.shortcut-chip.on')).toHaveLength(2);
  });

  it('touche pour ajouter dans l\'ordre, retouche pour retirer, émet la sélection puis se replie', () => {
    const {fixture, root, saved, open} = render();
    open();
    const chips = root.querySelectorAll<HTMLButtonElement>('.shortcut-chip');

    chips[3].click();
    chips[1].click();
    chips[3].click();
    fixture.detectChanges();
    root.querySelector<HTMLButtonElement>('button[color="primary"]')!.click();
    fixture.detectChanges();

    expect(saved).toEqual([['a1']]);
    expect(root.querySelector('.shortcut-config')).toBeNull();
  });

  it('ne dépasse pas la limite du serveur', () => {
    const {fixture, root, saved, open} = render();
    open();
    root.querySelectorAll<HTMLButtonElement>('.shortcut-chip').forEach((chip) => chip.click());
    fixture.detectChanges();
    root.querySelector<HTMLButtonElement>('button[color="primary"]')!.click();

    expect(saved[0]).toHaveLength(SHORTCUTS_MAX);
  });

  it('annuler n\'enregistre rien et ne garde pas les modifications en cours', () => {
    const {fixture, root, saved, cancelled, open} = render(['a0']);
    open();
    root.querySelectorAll<HTMLButtonElement>('.shortcut-chip')[5].click();
    fixture.detectChanges();

    root.querySelector<HTMLButtonElement>('.shortcut-actions button')!.click();
    fixture.detectChanges();
    expect(cancelled()).toBe(1);
    expect(saved).toEqual([]);

    open();
    expect(root.querySelectorAll('.shortcut-chip.on')).toHaveLength(1);
  });

  it('désactive l\'enregistrement pendant la sauvegarde', () => {
    const {fixture, root, open} = render();
    open();
    fixture.componentRef.setInput('saving', true);
    fixture.detectChanges();
    expect(root.querySelector<HTMLButtonElement>('button[color="primary"]')!.disabled).toBe(true);
  });
});
