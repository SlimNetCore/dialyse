import {TestBed} from '@angular/core/testing';
import {provideZonelessChangeDetection} from '@angular/core';
import {TranslateModule} from '@ngx-translate/core';
import {beforeEach, describe, expect, it} from 'vitest';
import {PendingSeancesComponent} from './pending-seances.component';
import {SeanceListItem} from '../../../core/api/backend-api.service';

const seance = (overrides: Partial<SeanceListItem> = {}): SeanceListItem => ({
  id: 's1', centerId: 'c', patientId: 'p1', patientCode: 'PAT-1', patientNom: 'Dupont', patientPrenom: 'Jean',
  dateSeance: '2026-10-01', status: 'CREE', ...overrides,
});

describe('PendingSeancesComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [PendingSeancesComponent, TranslateModule.forRoot()],
      providers: [provideZonelessChangeDetection()],
    });
  });

  function render(seances: SeanceListItem[], selectedId: string | null = null) {
    const fixture = TestBed.createComponent(PendingSeancesComponent);
    fixture.componentRef.setInput('seances', seances);
    fixture.componentRef.setInput('selectedId', selectedId);
    fixture.detectChanges();
    const opened: string[] = [];
    fixture.componentInstance.opened.subscribe((id) => opened.push(id));
    return {root: fixture.nativeElement as HTMLElement, opened};
  }

  it('ne montre rien quand aucune séance n\'est en attente', () => {
    expect(render([]).root.querySelector('.pending')).toBeNull();
  });

  it('liste les séances oubliées avec le nom, la date et le nombre', () => {
    const {root} = render([seance(), seance({
      id: 's2',
      patientNom: 'Benali',
      patientPrenom: 'Amine',
      dateSeance: '2026-10-02'
    })]);

    expect(root.querySelectorAll('.pending-item')).toHaveLength(2);
    expect(root.querySelector('.count')?.textContent?.trim()).toBe('2');
    expect(root.querySelector('.pending-item')!.textContent).toContain('Dupont Jean');
    expect(root.querySelector('.pending-item')!.textContent).toContain('2026-10-01');
  });

  it('retombe sur le code patient quand le nom est inconnu', () => {
    const {root} = render([seance({patientNom: null, patientPrenom: null})]);
    expect(root.querySelector('.name strong')?.textContent).toContain('PAT-1');
  });

  it('l\'administrateur peut déverrouiller une séance, puis la voit marquée « déverrouillée »', () => {
    const fixture = TestBed.createComponent(PendingSeancesComponent);
    fixture.componentRef.setInput('seances', [seance(), seance({
      id: 's2',
      regularisationDeverrouilleeAt: '2026-10-05T07:00:00Z'
    })]);
    fixture.componentRef.setInput('canUnlock', true);
    fixture.detectChanges();
    const unlocked: string[] = [];
    fixture.componentInstance.unlock.subscribe((id) => unlocked.push(id));
    const root = fixture.nativeElement as HTMLElement;

    expect(root.querySelectorAll('.unlock-btn')).toHaveLength(1);
    expect(root.querySelectorAll('.unlocked')).toHaveLength(1);
    root.querySelector<HTMLButtonElement>('.unlock-btn')!.click();

    expect(unlocked).toEqual(['s1']);
    expect(root.querySelector('.hint')?.textContent).toContain('PENDING_HINT_ADMIN');
  });

  it('l\'infirmier n\'a aucun bouton de déverrouillage et un texte adapté', () => {
    const {root} = render([seance({regularisationDeverrouilleeAt: '2026-10-05T07:00:00Z'})]);

    expect(root.querySelector('.unlock-btn')).toBeNull();
    expect(root.querySelector('.unlocked')).toBeNull();
    expect(root.querySelector('.hint')?.textContent).toContain('PENDING_HINT_NURSE');
  });

  it('émet la séance choisie et met en évidence celle qui est ouverte', () => {
    const {root, opened} = render([seance(), seance({id: 's2'})], 's2');

    root.querySelectorAll<HTMLButtonElement>('.pending-item')[0].click();

    expect(opened).toEqual(['s1']);
    expect(root.querySelectorAll('.pending-item.selected')).toHaveLength(1);
  });
});
