import {provideZonelessChangeDetection, signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {TranslateModule} from '@ngx-translate/core';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {SanteBase} from '../../core/api/supervision-api.service';
import {PerformanceBaseStore} from './state/performance-base.store';
import {SanteBaseComponent} from './sante-base.component';

const sante = (surcharges: Partial<SanteBase> = {}): SanteBase => ({
  disponible: true, raison: null, tailleOctets: 250 * 1024 * 1024, cachePct: 99.2, connexions: 12, connexionsMax: 100,
  statsReset: '2026-10-01T08:00:00Z',
  tables: [
    {
      nom: 'audit_log', tailleOctets: 80 * 1024 * 1024, lignes: 900000, mortesPct: 31.5, scansComplets: 40,
      scansIndex: 9000, dernierVacuum: null, partitionnement: 'A_SURVEILLER'
    },
  ],
  indexInutilises: [{table: 'seances', index: 'idx_seances_center_date', tailleOctets: 2 * 1024 * 1024}],
  alertes: [{code: 'LIGNES_MORTES', niveau: 'ATTENTION', cible: 'audit_log', valeur: '32'}],
  ...surcharges,
});

describe('SanteBaseComponent', () => {
  let donnees: ReturnType<typeof signal<SanteBase | null>>;
  let store: Record<string, unknown>;

  beforeEach(async () => {
    donnees = signal<SanteBase | null>(sante());
    store = {
      sante: donnees, santeLoading: signal(false), santeError: signal(false),
      chargerSante: vi.fn().mockResolvedValue(undefined),
    };
    await TestBed.configureTestingModule({
      imports: [SanteBaseComponent, TranslateModule.forRoot(), NoopAnimationsModule],
      providers: [provideZonelessChangeDetection(), {provide: PerformanceBaseStore, useValue: store}],
    }).compileComponents();
  });

  function creer() {
    const fixture = TestBed.createComponent(SanteBaseComponent);
    fixture.detectChanges();
    return {fixture, root: fixture.nativeElement as HTMLElement};
  }

  it('charge la santé à l\'ouverture et affiche taille, cache et connexions', () => {
    const {root} = creer();

    expect(store['chargerSante']).toHaveBeenCalled();
    expect(root.querySelector('[data-testid="sante-taille"]')!.textContent).toContain('250 Mo');
    expect(root.querySelector('[data-testid="sante-cache"]')!.textContent).toContain('99');
    expect(root.querySelector('[data-testid="sante-connexions"]')!.textContent).toContain('12 / 100');
  });

  it('liste les points d\'attention avec leur niveau et la table concernée', () => {
    const {root} = creer();

    const alerte = root.querySelector('[data-testid="sante-alertes"] li')!;
    expect(alerte.classList.contains('attention')).toBe(true);
    expect(alerte.textContent).toContain('SUPERVISION.SANTE.ALERTE.LIGNES_MORTES');
  });

  it('montre les plus grosses tables avec leur position face au partitionnement et les index inutilisés', () => {
    const {root} = creer();

    const ligne = root.querySelector('[data-testid="sante-tables"] tr.mat-mdc-row')!.textContent!;
    expect(ligne).toContain('audit_log');
    expect(ligne).toContain('SUPERVISION.SANTE.PART.A_SURVEILLER');
    expect(root.querySelector('[data-testid="sante-index-inutilises"]')!.textContent).toContain('idx_seances_center_date');
  });

  it('rassure quand rien n\'est à signaler', () => {
    donnees.set(sante({alertes: [], indexInutilises: []}));
    const {root} = creer();

    expect(root.querySelector('[data-testid="sante-aucune-alerte"]')).not.toBeNull();
    expect(root.querySelector('[data-testid="sante-aucun-index-inutile"]')).not.toBeNull();
  });

  it('explique que la santé n\'existe pas sur la base de développement', () => {
    donnees.set({
      ...sante(),
      disponible: false,
      raison: 'BASE_NON_POSTGRESQL',
      tables: [],
      indexInutilises: [],
      alertes: []
    });
    const {root} = creer();

    expect(root.querySelector('[data-testid="sante-indisponible"]')).not.toBeNull();
    expect(root.querySelector('[data-testid="sante-tables"]')).toBeNull();
  });

  it('se rafraîchit à la demande', () => {
    const {root} = creer();

    root.querySelector<HTMLButtonElement>('[data-testid="sante-rafraichir"]')!.click();

    expect(store['chargerSante']).toHaveBeenCalledTimes(2);
  });
});
