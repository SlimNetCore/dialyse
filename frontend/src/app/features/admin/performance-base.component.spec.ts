import {provideZonelessChangeDetection, signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {MatDialog} from '@angular/material/dialog';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {TranslateModule} from '@ngx-translate/core';
import {of} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {RequeteBase, StatutSupervision} from '../../core/api/supervision-api.service';
import {PerformanceBaseComponent} from './performance-base.component';
import {PerformanceBaseStore} from './state/performance-base.store';

const ligne = (id: string, niveau: RequeteBase['niveau'], conseils: RequeteBase['conseils'] = []): RequeteBase => ({
  id, requete: `SELECT ${id} FROM seances WHERE center_id = $1`, appels: 1200, tempsTotalMs: 61000,
  tempsMoyenMs: 650, tempsMaxMs: 2100, lignes: 5000, partTempsTotalPct: 42.5, niveau, conseils,
});

describe('PerformanceBaseComponent', () => {
  let statut: ReturnType<typeof signal<StatutSupervision | null>>;
  let rows: ReturnType<typeof signal<RequeteBase[]>>;
  let store: Record<string, unknown>;
  let dialogResult: boolean;
  let dialogOpen: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    statut = signal<StatutSupervision | null>({
      disponible: true, raison: null, reinitialiseLe: '2026-10-01T08:00:00Z', tempsTotalMs: 1000,
    });
    rows = signal<RequeteBase[]>([
      ligne('a', 'CRITIQUE', [{code: 'SCAN_PROBABLE', niveau: 'ACTION', valeur: '650'},
        {code: 'SELECT_ETOILE', niveau: 'INFO', valeur: ''}]),
      ligne('b', 'NORMAL'),
    ]);
    dialogResult = true;
    dialogOpen = vi.fn(() => ({afterClosed: () => of(dialogResult)}));
    store = {
      statut, rows, total: signal(2), pageIndex: signal(0), pageSize: signal(20), tri: signal('TEMPS_TOTAL'),
      loading: signal(false), error: signal(false), resetError: signal(false),
      load: vi.fn().mockResolvedValue(undefined),
      setTri: vi.fn(), setPagination: vi.fn(),
      reinitialiser: vi.fn().mockResolvedValue(true),
    };
    await TestBed.configureTestingModule({
      imports: [PerformanceBaseComponent, TranslateModule.forRoot(), NoopAnimationsModule],
      providers: [
        provideZonelessChangeDetection(),
        {provide: PerformanceBaseStore, useValue: store},
        {provide: MatDialog, useValue: {open: dialogOpen}},
      ],
    }).compileComponents();
  });

  function creer() {
    const fixture = TestBed.createComponent(PerformanceBaseComponent);
    fixture.detectChanges();
    return {fixture, root: fixture.nativeElement as HTMLElement};
  }

  it('charge les statistiques et affiche chaque requête avec sa gravité, sa durée et sa part', () => {
    const {root} = creer();

    expect(store['load']).toHaveBeenCalled();
    expect(root.querySelectorAll('[data-testid="perf-query"]').length).toBe(2);
    const premiere = root.querySelector('tr.mat-mdc-row')!.textContent!;
    expect(premiere).toContain('SUPERVISION.LEVEL.CRITIQUE');
    expect(premiere).toContain('650 ms');
    expect(premiere).toContain('1 min 01 s');
    expect(premiere).toContain('42,5 %');
  });

  it('affiche sous chaque requête ses pistes d\'amélioration, les actions avant les informations', () => {
    const {root} = creer();

    const conseils = root.querySelector('[data-testid="perf-conseils"]')!;
    const lignes = Array.from(conseils.querySelectorAll('li'));
    expect(lignes).toHaveLength(2);
    expect(lignes[0].classList.contains('action')).toBe(true);
    expect(lignes[0].textContent).toContain('SUPERVISION.CONSEIL.SCAN_PROBABLE');
    expect(lignes[1].classList.contains('info')).toBe(true);
    // une requête saine n'a pas de bloc de pistes
    expect(root.querySelectorAll('[data-testid="perf-conseils"]').length).toBe(1);
  });

  it('ouvre l\'analyse du plan de la requête choisie, en ne transmettant que son identifiant et son texte', () => {
    const {root} = creer();

    root.querySelector<HTMLButtonElement>('[data-testid="perf-analyser-a"]')!.click();

    expect(dialogOpen).toHaveBeenCalledTimes(1);
    const [, config] = dialogOpen.mock.calls[0] as unknown as [unknown, { data: { id: string; requete: string } }];
    expect(config.data).toEqual({id: 'a', requete: 'SELECT a FROM seances WHERE center_id = $1'});
  });

  it('propose un onglet Santé de la base, chargé seulement quand on l\'ouvre', () => {
    const {root} = creer();

    expect(root.querySelectorAll('[role="tab"]')).toHaveLength(2);
    expect(root.querySelector('[data-testid="sante-base"]')).toBeNull();
  });

  it('explique pourquoi la mesure est indisponible au lieu d\'afficher un tableau vide', () => {
    statut.set({disponible: false, raison: 'BASE_NON_POSTGRESQL', reinitialiseLe: null, tempsTotalMs: 0});
    const {root} = creer();

    expect(root.querySelector('[data-testid="perf-unavailable"]')!.textContent)
      .toContain('SUPERVISION.UNAVAILABLE.BASE_NON_POSTGRESQL');
    expect(root.querySelector('[data-testid="perf-table"]')).toBeNull();
    expect(root.querySelector('[data-testid="perf-reset"]')).toBeNull();
  });

  it('relance le chargement en changeant de classement', () => {
    const {root} = creer();

    root.querySelector<HTMLButtonElement>('[data-testid="perf-tri-TEMPS_MOYEN"] button')!.click();

    expect(store['setTri']).toHaveBeenCalledWith('TEMPS_MOYEN');
    expect(store['load']).toHaveBeenCalledTimes(2);
  });

  it('remet les compteurs à zéro seulement après confirmation', async () => {
    const {root, fixture} = creer();

    root.querySelector<HTMLButtonElement>('[data-testid="perf-reset"]')!.click();
    await fixture.whenStable();
    expect(store['reinitialiser']).toHaveBeenCalledTimes(1);

    dialogResult = false;
    root.querySelector<HTMLButtonElement>('[data-testid="perf-reset"]')!.click();
    await fixture.whenStable();
    expect(store['reinitialiser']).toHaveBeenCalledTimes(1);
  });

  it('affiche un message quand aucune requête n\'est encore mesurée', () => {
    rows.set([]);
    const {root} = creer();

    expect(root.querySelector('[data-testid="perf-empty"]')).not.toBeNull();
  });

  it('déplie une requête sur demande', () => {
    const {root, fixture} = creer();
    const code = root.querySelector('[data-testid="perf-query"]')!;
    expect(code.classList.contains('expanded')).toBe(false);

    root.querySelector<HTMLButtonElement>('.query-actions button')!.click();
    fixture.detectChanges();

    expect(code.classList.contains('expanded')).toBe(true);
  });
});
