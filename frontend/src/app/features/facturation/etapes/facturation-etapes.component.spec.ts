import {provideZonelessChangeDetection} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {TranslateModule} from '@ngx-translate/core';
import {describe, expect, it} from 'vitest';
import {etapeCourante, FacturationEtapesComponent} from './facturation-etapes.component';
import {FACTURATION_VALIDEE} from '../state/facturation.store';

describe('etapeCourante (RG-FAC-049)', () => {
  it('sans aperçu ni validation : Sélection', () => {
    expect(etapeCourante({preview: null, successMessage: null})).toBe(1);
  });

  it('un aperçu calculé et non validé : Aperçu, même s\'il reste un message de succès', () => {
    expect(etapeCourante({preview: {lignes: []}, successMessage: null})).toBe(2);
    expect(etapeCourante({preview: {lignes: []}, successMessage: FACTURATION_VALIDEE})).toBe(2);
  });

  it('une validation réussie : Validation', () => {
    expect(etapeCourante({preview: null, successMessage: FACTURATION_VALIDEE})).toBe(3);
  });

  it('un autre message de succès (mois clôturé après une validation) ramène à la Sélection', () => {
    expect(etapeCourante({preview: null, successMessage: 'FACTURATION.SUCCESS.MOIS_CLOTURE'})).toBe(1);
  });
});

describe('FacturationEtapesComponent', () => {
  async function render(etape: 1 | 2 | 3): Promise<HTMLElement> {
    TestBed.configureTestingModule({
      imports: [FacturationEtapesComponent, TranslateModule.forRoot()],
      providers: [provideZonelessChangeDetection()],
    });
    const fixture = TestBed.createComponent(FacturationEtapesComponent);
    fixture.componentRef.setInput('etape', etape);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  const pastilles = (host: HTMLElement): HTMLElement[] => [...host.querySelectorAll<HTMLElement>('li.etape')];

  it('affiche les trois étapes sur une liste ordonnée, sans arrêt de tabulation', async () => {
    const host = await render(1);

    expect(host.querySelector('ol')?.getAttribute('aria-label')).toBe('FACTURATION.FLOW.TITLE');
    expect(pastilles(host).map((li) => li.querySelector('.etape__titre')?.textContent?.trim())).toEqual([
      'FACTURATION.FLOW.SELECT.TITLE', 'FACTURATION.FLOW.PREVIEW.TITLE', 'FACTURATION.FLOW.VALIDATE.TITLE']);
    expect(pastilles(host).some((li) => li.hasAttribute('tabindex'))).toBe(false);
  });

  it('marque l\'étape courante par aria-current="step" et les précédentes comme faites', async () => {
    const host = await render(2);
    const [selection, apercu, validation] = pastilles(host);

    expect(apercu.getAttribute('aria-current')).toBe('step');
    expect(apercu.classList).toContain('etape--courante');
    expect(selection.getAttribute('aria-current')).toBeNull();
    expect(selection.classList).toContain('etape--faite');
    expect(validation.classList).not.toContain('etape--faite');
    expect(validation.getAttribute('aria-current')).toBeNull();
  });

  it('une seule description lue par étape : texte masqué dans la pastille, info-bulle native pour la souris', async () => {
    const host = await render(3);

    for (const li of pastilles(host)) {
      const descriptions = li.querySelectorAll('.etape__description');
      expect(descriptions).toHaveLength(1);
      expect(descriptions[0].textContent?.trim()).toMatch(/^FACTURATION\.FLOW\.\w+\.DESC$/);
      expect(li.getAttribute('title')).toBe(descriptions[0].textContent?.trim());
      // pas de second canal (aria-describedby / matTooltip) qui ferait annoncer la description deux fois
      expect(li.hasAttribute('aria-describedby')).toBe(false);
    }
    expect(host.querySelectorAll('[aria-describedby]')).toHaveLength(0);
    expect(pastilles(host)[2].getAttribute('aria-current')).toBe('step');
  });

  it('les flèches de séparation sont décoratives (masquées aux lecteurs d\'écran)', async () => {
    const host = await render(1);

    const fleches = [...host.querySelectorAll('li.etape__fleche')];
    expect(fleches).toHaveLength(2);
    expect(fleches.every((f) => f.getAttribute('aria-hidden') === 'true')).toBe(true);
  });
});
