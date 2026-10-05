import {Directive, input, provideZonelessChangeDetection, signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {BaseChartDirective} from 'ng2-charts';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {TranslateModule} from '@ngx-translate/core';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {AbsencesOverview, AbsencesStats} from '../../core/api/direction-api.service';
import {DirectionDashboardComponent} from './direction-dashboard.component';
import {DirectionStore} from './state/direction.store';
import {periodForPreset} from './direction.util';

const stats: AbsencesStats = {
  nbAbsences: 9, nbJustifiees: 3, nbNonJustifiees: 2, nbAQualifier: 4, nbRattrapees: 0, nbSeances: 90,
  valorisationHt: 900, valorisationTtc: 1071, caHt: 9000, tauxAbsenteisme: 10, partCaHt: 10,
};

const absences = (motifs: AbsencesOverview['motifs']): AbsencesOverview => ({
  societeId: 's', from: '2026-10-01', to: '2026-10-05', generatedAt: '', total: stats, centres: [], motifs, mensuel: [],
});

/** jsdom n'a pas de canvas : le graphique est remplacé par une directive sans rendu. */
@Directive({selector: 'canvas[baseChart]', standalone: true})
class FakeChartDirective {
  readonly data = input<unknown>();
  readonly options = input<unknown>();
  readonly type = input<unknown>();
}

/** Store minimal : les accès non prévus renvoient `null` (le tableau de bord masque alors la section). */
function fakeStore(overrides: Record<string, unknown>) {
  const base: Record<string, unknown> = {
    from: signal(''), to: signal(''), loading: signal(false), error: signal(null), absences: signal(null),
    rankedCentres: signal([]), alerts: signal([]), snapshots: signal([]), alertHistory: signal([]),
    load: vi.fn().mockResolvedValue(undefined), loadSnapshots: vi.fn().mockResolvedValue(undefined),
    loadAlertHistory: vi.fn().mockResolvedValue(undefined), reportBusy: signal(null),
    ...overrides,
  };
  return new Proxy(base, {get: (target, key: string) => (key in target ? target[key] : () => null)});
}

describe('DirectionDashboardComponent — mois en cours et motifs d\'absence', () => {
  let store: ReturnType<typeof fakeStore>;
  let from: ReturnType<typeof signal<string>>;
  let to: ReturnType<typeof signal<string>>;
  const mois = periodForPreset('MONTH', new Date());

  async function render(motifs: AbsencesOverview['motifs'] = []) {
    from = signal('');
    to = signal('');
    store = fakeStore({from, to, absences: signal(absences(motifs))});
    await TestBed.configureTestingModule({
      imports: [DirectionDashboardComponent, TranslateModule.forRoot(), NoopAnimationsModule],
      providers: [provideZonelessChangeDetection(), {provide: DirectionStore, useValue: store}],
    }).overrideComponent(DirectionDashboardComponent, {
      remove: {imports: [BaseChartDirective]}, add: {imports: [FakeChartDirective]},
    }).compileComponents();
    const fixture = TestBed.createComponent(DirectionDashboardComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    return {fixture, root: fixture.nativeElement as HTMLElement};
  }

  beforeEach(() => TestBed.resetTestingModule());

  it('s\'ouvre sur le mois en cours', async () => {
    await render();

    expect((store['load'] as ReturnType<typeof vi.fn>)).toHaveBeenCalledWith(mois.from, mois.to);
  });

  it('met en évidence le bouton « Ce mois » quand la période est le mois en cours', async () => {
    const {fixture, root} = await render();
    from.set(mois.from);
    to.set(mois.to);
    fixture.detectChanges();

    const mois$ = root.querySelector<HTMLButtonElement>('[data-testid="preset-MONTH"]')!;
    expect(mois$.classList.contains('preset-active')).toBe(true);
    expect(mois$.getAttribute('aria-pressed')).toBe('true');
    expect(root.querySelectorAll('.preset-active')).toHaveLength(1);
    expect(root.querySelector('[data-testid="preset-YEAR"]')!.getAttribute('aria-pressed')).toBe('false');
  });

  it('ne met aucun raccourci en évidence pour une période personnalisée', async () => {
    const {fixture, root} = await render();
    from.set('2026-01-15');
    to.set('2026-02-20');
    fixture.detectChanges();

    expect(root.querySelectorAll('.preset-active')).toHaveLength(0);
  });

  it('affiche la répartition des absences par nombre et par valeur, sans les motifs masqués', async () => {
    const {fixture, root} = await render([
      {motif: 'MALADIE', nb: 6, valorisationHt: 600},
      {motif: 'VOYAGE', nb: 3, valorisationHt: 300},
      {motif: 'AUTRE', nb: null, valorisationHt: null},
    ]);

    expect(root.querySelector('[data-testid="direction-absences-chart-nb"]')).not.toBeNull();
    expect(root.querySelector('[data-testid="direction-absences-chart-valeur"]')).not.toBeNull();
    const cmp = fixture.componentInstance as unknown as {
      motifsNbChart(): { datasets: { data: number[] }[] };
      motifsValeurChart(): { datasets: { data: number[] }[] };
    };
    expect(cmp.motifsNbChart().datasets[0].data).toEqual([6, 3]);
    expect(cmp.motifsValeurChart().datasets[0].data).toEqual([600, 300]);
  });

  it('n\'affiche pas de graphiques sans aucun motif publiable', async () => {
    const {root} = await render([{motif: 'AUTRE', nb: null, valorisationHt: null}]);

    expect(root.querySelector('[data-testid="direction-absences-motifs-charts"]')).toBeNull();
  });
});
