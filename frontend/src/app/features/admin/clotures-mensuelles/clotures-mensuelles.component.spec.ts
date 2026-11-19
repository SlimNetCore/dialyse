import {Component} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {TranslateModule} from '@ngx-translate/core';
import {describe, expect, it} from 'vitest';
import {CloturesMensuellesComponent} from './clotures-mensuelles.component';
import {FacturationClotureComponent} from '../../facturation/cloture/facturation-cloture.component';
import {MoisDAbsencesComponent} from '../../absences/mois-d-absences.component';

@Component({selector: 'app-facturation-cloture', standalone: true, template: '<p>cloture-facturation</p>'})
class FacturationClotureStub {
}

@Component({selector: 'app-mois-d-absences', standalone: true, template: '<p>mois-d-absences</p>'})
class MoisDAbsencesStub {
}

describe('CloturesMensuellesComponent (Administration › Paramétrage › Clôtures mensuelles)', () => {
  const rendre = async (): Promise<HTMLElement> => {
    TestBed.configureTestingModule({
      imports: [CloturesMensuellesComponent, NoopAnimationsModule, TranslateModule.forRoot()],
    });
    TestBed.overrideComponent(CloturesMensuellesComponent, {
      remove: {imports: [FacturationClotureComponent, MoisDAbsencesComponent]},
      add: {imports: [FacturationClotureStub, MoisDAbsencesStub]},
    });
    const fixture = TestBed.createComponent(CloturesMensuellesComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  };

  it('réunit deux onglets, Facturation puis Séances (absences), et ouvre la clôture de la facturation', async () => {
    const root = await rendre();

    const onglets = Array.from(root.querySelectorAll('[role="tab"]')).map((t) => t.textContent?.trim());
    expect(onglets).toHaveLength(2);
    expect(onglets[0]).toContain('NAV.ADMIN_CLOTURES_FACTURATION');
    expect(onglets[1]).toContain('NAV.ADMIN_CLOTURES_SEANCES');
    expect(root.querySelector('[data-testid="clotures-facturation"] app-facturation-cloture')).not.toBeNull();
    expect(root.querySelector('h1')?.textContent).toContain('NAV.ADMIN_CLOTURES');
  });

  it('affiche la clôture des mois d\'absences dans l\'onglet Séances', async () => {
    const root = await rendre();

    (root.querySelectorAll('[role="tab"]')[1] as HTMLElement).click();
    await new Promise((resolve) => setTimeout(resolve));
    TestBed.tick();

    expect(root.querySelector('[data-testid="clotures-seances"] app-mois-d-absences')).not.toBeNull();
  });
});
