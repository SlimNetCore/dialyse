import {Component, Directive, input, provideZonelessChangeDetection, signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {provideRouter, Router} from '@angular/router';
import {RouterTestingHarness} from '@angular/router/testing';
import {BaseChartDirective} from 'ng2-charts';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {TranslateModule} from '@ngx-translate/core';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {AbsencesOverview, AbsencesStats} from '../../core/api/direction-api.service';
import {DirectionLayoutComponent, directionQueryParams} from './direction-layout.component';
import {DirectionAnalysesComponent} from './direction-analyses.component';
import {DirectionStore} from './state/direction.store';
import {DirectionFilters} from './state/direction-filters';
import {periodForPreset} from './direction.util';

const stats: AbsencesStats = {
  nbAbsences: 9, nbJustifiees: 3, nbNonJustifiees: 2, nbAQualifier: 4, nbRattrapees: 0, nbSeances: 90,
  valorisationHt: 900, valorisationTtc: 1071, caHt: 9000, tauxAbsenteisme: 10, partCaHt: 10,
};

/** Effectif de 1 à 4 absences : tous les chiffres issus des absences sont masqués (k-anonymat, KB-ABS-07). */
const statsMasquees: AbsencesStats = {
  nbAbsences: null, nbJustifiees: null, nbNonJustifiees: null, nbAQualifier: null, nbRattrapees: null, nbSeances: 90,
  valorisationHt: null, valorisationTtc: null, caHt: 9000, tauxAbsenteisme: null, partCaHt: null,
};

const absences = (motifs: AbsencesOverview['motifs'], total: AbsencesStats = stats): AbsencesOverview => ({
  societeId: 's', from: '2026-10-01', to: '2026-10-05', generatedAt: '', total, centres: [], motifs, mensuel: [], centresMasques: total === stats ? 0 : 1,
});

/** jsdom n'a pas de canvas : le graphique est remplacé par une directive sans rendu. */
@Directive({selector: 'canvas[baseChart]', standalone: true})
class FakeChartDirective {
  readonly data = input<unknown>();
  readonly options = input<unknown>();
  readonly type = input<unknown>();
}

/** Page fille sans contenu : seule compte ici la page parente (filtres, URL). */
@Component({selector: 'app-empty-page', standalone: true, template: ''})
class EmptyPageComponent {
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

describe('DirectionLayoutComponent — filtres période et centre communs aux pages', () => {
  let store: ReturnType<typeof fakeStore>;
  let from: ReturnType<typeof signal<string>>;
  let to: ReturnType<typeof signal<string>>;
  const mois = periodForPreset('MONTH', new Date());

  async function render(url = '/direction') {
    from = signal('');
    to = signal('');
    store = fakeStore({from, to});
    TestBed.configureTestingModule({
      imports: [TranslateModule.forRoot(), NoopAnimationsModule],
      providers: [
        provideZonelessChangeDetection(),
        {provide: DirectionStore, useValue: store},
        provideRouter([{
          path: 'direction', component: DirectionLayoutComponent,
          children: [{path: '', component: EmptyPageComponent}, {path: 'analyses', component: EmptyPageComponent}],
        }]),
      ],
    });
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url);
    await harness.fixture.whenStable();
    const fixtureRoot = harness.fixture.nativeElement as HTMLElement;
    return {harness, root: fixtureRoot.querySelector('app-direction-layout') as HTMLElement};
  }

  const params = () => TestBed.inject(Router).parseUrl(TestBed.inject(Router).url).queryParams;

  beforeEach(() => {
    TestBed.resetTestingModule();
  });

  it('s\'ouvre sur le mois en cours sans paramètre d\'URL', async () => {
    await render();

    expect((store['load'] as ReturnType<typeof vi.fn>)).toHaveBeenCalledWith(mois.from, mois.to);
    expect(TestBed.inject(DirectionFilters).centre()).toBeNull();
  });

  it('reprend la période et le centre des paramètres d\'URL', async () => {
    await render('/direction/analyses?from=2026-01-01&to=2026-01-31&centre=c1');

    expect((store['load'] as ReturnType<typeof vi.fn>)).toHaveBeenCalledWith('2026-01-01', '2026-01-31');
    expect(TestBed.inject(DirectionFilters).centre()).toBe('c1');
  });

  it('ignore une période d\'URL mal formée et ouvre le mois en cours', async () => {
    await render('/direction?from=hier&to=2026-01-31');

    expect((store['load'] as ReturnType<typeof vi.fn>)).toHaveBeenCalledWith(mois.from, mois.to);
  });

  it('reflète la période et le centre dans l\'URL et les conserve d\'une page à l\'autre', async () => {
    const {harness} = await render();
    from.set('2026-02-01');
    to.set('2026-02-28');
    await harness.fixture.whenStable();
    expect(params()).toEqual({from: '2026-02-01', to: '2026-02-28'});

    TestBed.inject(DirectionFilters).centre.set('c2');
    await harness.fixture.whenStable();
    expect(params()).toEqual({from: '2026-02-01', to: '2026-02-28', centre: 'c2'});

    // une entrée de la barre de module mène à la page sans paramètres : la période et le centre y sont reportés
    await harness.navigateByUrl('/direction/analyses');
    await harness.fixture.whenStable();
    expect(TestBed.inject(Router).url.split('?')[0]).toBe('/direction/analyses');
    expect(params()).toEqual({from: '2026-02-01', to: '2026-02-28', centre: 'c2'});

    TestBed.inject(DirectionFilters).centre.set(null);
    await harness.fixture.whenStable();
    expect(params()).toEqual({from: '2026-02-01', to: '2026-02-28'});
  });

  it('met en évidence le bouton « Ce mois » quand la période est le mois en cours', async () => {
    const {harness, root} = await render();
    from.set(mois.from);
    to.set(mois.to);
    await harness.fixture.whenStable();
    harness.detectChanges();

    const mois$ = root.querySelector<HTMLButtonElement>('[data-testid="preset-MONTH"]')!;
    expect(mois$.classList.contains('preset-active')).toBe(true);
    expect(mois$.getAttribute('aria-pressed')).toBe('true');
    expect(root.querySelectorAll('.preset-active')).toHaveLength(1);
    expect(root.querySelector('[data-testid="preset-YEAR"]')!.getAttribute('aria-pressed')).toBe('false');
  });

  it('ne met aucun raccourci en évidence pour une période personnalisée', async () => {
    const {harness, root} = await render();
    from.set('2026-01-15');
    to.set('2026-02-20');
    await harness.fixture.whenStable();
    harness.detectChanges();

    expect(root.querySelectorAll('.preset-active')).toHaveLength(0);
  });
});

describe('directionQueryParams', () => {
  it('pose la période et le centre en conservant les autres paramètres', () => {
    expect(directionQueryParams({x: '1'}, '2026-01-01', '2026-01-31', 'c1'))
      .toEqual({x: '1', from: '2026-01-01', to: '2026-01-31', centre: 'c1'});
  });

  it('retire le centre pour la vue consolidée', () => {
    expect(directionQueryParams({centre: 'c1'}, '2026-01-01', '2026-01-31', null))
      .toEqual({from: '2026-01-01', to: '2026-01-31'});
  });
});

describe('DirectionAnalysesComponent — motifs d\'absence', () => {
  async function render(motifs: AbsencesOverview['motifs'] = [], total: AbsencesStats = stats) {
    const store = fakeStore({absences: signal(absences(motifs, total))});
    await TestBed.configureTestingModule({
      imports: [DirectionAnalysesComponent, TranslateModule.forRoot(), NoopAnimationsModule],
      providers: [provideZonelessChangeDetection(), {provide: DirectionStore, useValue: store}],
    }).overrideComponent(DirectionAnalysesComponent, {
      remove: {imports: [BaseChartDirective]}, add: {imports: [FakeChartDirective]},
    }).compileComponents();
    const fixture = TestBed.createComponent(DirectionAnalysesComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    return {fixture, root: fixture.nativeElement as HTMLElement};
  }

  beforeEach(() => TestBed.resetTestingModule());

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

  it('affiche « < 5 » et un tiret quand les chiffres d\'absence sont masqués par le k-anonymat', async () => {
    const {root} = await render([], statsMasquees);

    expect(root.querySelector('[data-testid="direction-absences-nb"]')!.textContent).toContain('< 5');
    expect(root.querySelector('[data-testid="direction-absences-kpis"]')!.textContent).toContain('—');
    expect(root.querySelector('[data-testid="direction-absences-masques"]')).not.toBeNull();
  });

  it('affiche les chiffres d\'absence tels quels au-delà du seuil', async () => {
    const {root} = await render([], stats);

    expect(root.querySelector('[data-testid="direction-absences-nb"]')!.textContent).toContain('9');
  });

  it('n\'affiche pas de graphiques sans aucun motif publiable', async () => {
    const {root} = await render([{motif: 'AUTRE', nb: null, valorisationHt: null}]);

    expect(root.querySelector('[data-testid="direction-absences-motifs-charts"]')).toBeNull();
  });
});
