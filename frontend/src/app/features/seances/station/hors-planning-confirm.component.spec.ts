import {TestBed} from '@angular/core/testing';
import {provideZonelessChangeDetection} from '@angular/core';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {TranslateModule} from '@ngx-translate/core';
import {beforeEach, describe, expect, it} from 'vitest';
import {HorsPlanningConfirmation, HorsPlanningConfirmComponent} from './hors-planning-confirm.component';
import {HorsPlanningCode} from '../state/seance.store';

describe('HorsPlanningConfirmComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HorsPlanningConfirmComponent, TranslateModule.forRoot(), NoopAnimationsModule],
      providers: [provideZonelessChangeDetection()],
    });
  });

  function render(code: HorsPlanningCode, canConfirm: boolean) {
    const fixture = TestBed.createComponent(HorsPlanningConfirmComponent);
    fixture.componentRef.setInput('code', code);
    fixture.componentRef.setInput('canConfirm', canConfirm);
    fixture.detectChanges();
    const confirmations: HorsPlanningConfirmation[] = [];
    let dismissed = 0;
    fixture.componentInstance.confirmed.subscribe((c) => confirmations.push(c));
    fixture.componentInstance.dismissed.subscribe(() => dismissed++);
    return {fixture, root: fixture.nativeElement as HTMLElement, confirmations, dismissedCount: () => dismissed};
  }

  it('explique la raison du hors planning selon le code du serveur', () => {
    const {root} = render('SEANCE_HORS_PLANNING_PATIENT', true);
    expect(root.querySelector('[data-testid="hp-reason"]')!.textContent).toContain('HP.SEANCE_HORS_PLANNING_PATIENT');
  });

  it('confirme avec le motif par défaut (rattrapage) sans précision', () => {
    const {root, confirmations} = render('SEANCE_HORS_PLANNING_JOUR', true);

    root.querySelector<HTMLButtonElement>('[data-testid="hp-submit"]')!.click();

    expect(confirmations).toEqual([{motif: 'RATTRAPAGE', precision: null}]);
  });

  it('exige une précision pour le motif « autre » avant de pouvoir confirmer', () => {
    const {fixture, root, confirmations} = render('SEANCE_HORS_PLANNING_JOUR', true);
    const cmp = fixture.componentInstance as unknown as {
      model: { set(v: { motif: string; precision: string }): void };
    };

    cmp.model.set({motif: 'AUTRE', precision: ''});
    fixture.detectChanges();
    expect(root.querySelector<HTMLButtonElement>('[data-testid="hp-submit"]')!.disabled).toBe(true);
    expect(root.querySelector('[data-testid="hp-precision"]')).not.toBeNull();

    cmp.model.set({motif: 'AUTRE', precision: ' transfert exceptionnel '});
    fixture.detectChanges();
    root.querySelector<HTMLButtonElement>('[data-testid="hp-submit"]')!.click();

    expect(confirmations).toEqual([{motif: 'AUTRE', precision: 'transfert exceptionnel'}]);
  });

  it('sans droit de confirmer, n\'offre que l\'explication et la fermeture', () => {
    const {root, dismissedCount} = render('SEANCE_CENTRE_FERME', false);

    expect(root.querySelector('[data-testid="hp-submit"]')).toBeNull();
    expect(root.querySelector('[data-testid="hp-no-right"]')!.textContent).toContain('NO_RIGHT_CLOSED');
    root.querySelector<HTMLButtonElement>('[data-testid="hp-dismiss"]')!.click();
    expect(dismissedCount()).toBe(1);
  });
});
