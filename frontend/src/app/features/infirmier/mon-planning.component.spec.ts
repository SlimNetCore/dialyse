import {provideZonelessChangeDetection, signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';
import {TranslateModule} from '@ngx-translate/core';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {MonPlanning} from '../../core/api/infirmier-api.service';
import {AppShellStore} from '../../core/state/app-shell.store';
import {aujourdhuiUtc} from './presence.util';
import {MonPlanningComponent} from './mon-planning.component';
import {MonPlanningStore} from './mon-planning.store';

const CENTRE = '11111111-1111-1111-1111-111111111111';

function planningFixture(): MonPlanning {
  const aujourdhui = aujourdhuiUtc();
  return {
    infirmier: {nom: 'Amrani', prenom: 'Sara', qualification: 'INFIRMIER'} as MonPlanning['infirmier'],
    debut: aujourdhui, fin: aujourdhui,
    salles: [{id: 's1', nom: 'Salle 1'}, {id: 's2', nom: 'Salle 2'}] as MonPlanning['salles'],
    creneaux: [{id: 'm', libelle: 'Matin'}] as MonPlanning['creneaux'],
    jours: [
      {jour: 'LUNDI', date: aujourdhui, ouvertHebdomadaire: true, fermetureMotif: null},
      {jour: 'MARDI', date: '2099-01-02', ouvertHebdomadaire: true, fermetureMotif: 'Férié'},
    ],
    mesCreneaux: [{date: aujourdhui, jour: 'LUNDI', salleId: 's1', creneauId: 'm', situation: 'PREVU'}],
    mesCases: [{
      date: aujourdhui, jour: 'LUNDI', salleId: 's1', creneauId: 'm', patients: 6, requis: 2, salleIsolement: false,
      collegues: 1,
    }],
  };
}

describe('MonPlanningComponent — grille de mes salles et créneaux', () => {
  let planning: ReturnType<typeof signal<MonPlanning | null>>;

  beforeEach(async () => {
    planning = signal<MonPlanning | null>(planningFixture());
    const store = {
      planning, nonLie: signal(false), loadingPlanning: signal(false), saving: signal(false), error: signal(null),
      successMessage: signal(null), rows: signal([]), total: signal(0), pageIndex: signal(0), pageSize: signal(10),
      loading: signal(false), chargerPlanning: vi.fn(), loadAbsences: vi.fn(), changerSemaine: vi.fn(),
      setPagination: vi.fn(), declarer: vi.fn(), annuler: vi.fn(),
    };
    await TestBed.configureTestingModule({
      imports: [MonPlanningComponent, TranslateModule.forRoot(), NoopAnimationsModule],
      providers: [provideZonelessChangeDetection(), provideRouter([]), {provide: MonPlanningStore, useValue: store}],
    }).compileComponents();
    TestBed.inject(AppShellStore).switchCenter(CENTRE);
  });

  async function render() {
    const fixture = TestBed.createComponent(MonPlanningComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return {fixture, root: fixture.nativeElement as HTMLElement};
  }

  it('affiche une grille semaine avec seulement mes salles et créneaux', async () => {
    const {root} = await render();

    const rows = root.querySelectorAll('[data-testid="moi-table"] tbody tr');
    expect(rows).toHaveLength(1);
    expect(rows[0].textContent).toContain('Salle 1');
    expect(rows[0].textContent).toContain('Matin');
    expect(root.textContent).not.toContain('Salle 2');
  });

  it('indique ma situation, la charge de la salle et mes collègues dans la case du jour', async () => {
    const {root} = await render();

    const cells = Array.from(root.querySelectorAll('[data-testid="moi-cell"]'));
    const mine = cells.find((c) => c.querySelector('[data-testid="moi-cell-situation"]'))!;
    expect(mine.classList.contains('prevu')).toBe(true);
    expect(mine.textContent).toContain('INFIRMIER.MOI.SITUATION.PREVU');
    expect(mine.textContent).toContain('INFIRMIER.MOI.GRILLE.PATIENTS');
    expect(mine.textContent).toContain('INFIRMIER.MOI.GRILLE.COLLEGUES');
  });

  it('grise un jour de fermeture du centre et n\'y affiche aucun créneau', async () => {
    const {root} = await render();

    const closed = Array.from(root.querySelectorAll('[data-testid="moi-cell"]')).filter((c) =>
      c.classList.contains('closed'));
    expect(closed.length).toBeGreaterThan(0);
    expect(closed.every((c) => !c.querySelector('[data-testid="moi-cell-situation"]'))).toBe(true);
  });

  it('bascule en vue jour : une carte par salle/créneau où je travaille ce jour-là', async () => {
    const {fixture, root} = await render();

    root.querySelector<HTMLElement>('[data-testid="moi-view-jour"] button')!.click();
    fixture.detectChanges();

    expect(root.querySelector('[data-testid="moi-table"]')).toBeNull();
    const cards = root.querySelectorAll('[data-testid="moi-day-card"]');
    expect(cards).toHaveLength(1);
    expect(cards[0].textContent).toContain('Salle 1 · Matin');
  });

  it('propose un message quand je n\'ai aucun créneau cette semaine', async () => {
    planning.set({...planningFixture(), mesCreneaux: [], mesCases: []});
    const {root} = await render();

    expect(root.querySelector('[data-testid="moi-aucun-creneau"]')).not.toBeNull();
    expect(root.querySelector('[data-testid="moi-table"]')).toBeNull();
  });
});
