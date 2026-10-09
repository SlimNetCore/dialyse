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

const ligne = (id: string, niveau: RequeteBase['niveau']): RequeteBase => ({
  id, requete: `SELECT ${id} FROM seances WHERE center_id = $1`, appels: 1200, tempsTotalMs: 61000,
  tempsMoyenMs: 650, tempsMaxMs: 2100, lignes: 5000, partTempsTotalPct: 42.5, niveau,
});

describe('PerformanceBaseComponent', () => {
  let statut: ReturnType<typeof signal<StatutSupervision | null>>;
  let rows: ReturnType<typeof signal<RequeteBase[]>>;
  let store: Record<string, unknown>;
  let dialogResult: boolean;

  beforeEach(async () => {
    statut = signal<StatutSupervision | null>({
      disponible: true, raison: null, reinitialiseLe: '2026-10-01T08:00:00Z', tempsTotalMs: 1000,
    });
    rows = signal<RequeteBase[]>([ligne('a', 'CRITIQUE'), ligne('b', 'NORMAL')]);
    dialogResult = true;
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
        {provide: MatDialog, useValue: {open: () => ({afterClosed: () => of(dialogResult)})}},
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
