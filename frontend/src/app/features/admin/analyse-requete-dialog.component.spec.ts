import {provideZonelessChangeDetection, signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {MAT_DIALOG_DATA} from '@angular/material/dialog';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {TranslateModule} from '@ngx-translate/core';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {AnalyseRequete} from '../../core/api/supervision-api.service';
import {AnalyseRequeteDialogComponent} from './analyse-requete-dialog.component';
import {PerformanceBaseStore} from './state/performance-base.store';

const SQL = 'SELECT * FROM seances WHERE center_id = $1';

const analyse = (surcharges: Partial<AnalyseRequete> = {}): AnalyseRequete => ({
  disponible: true, raison: null, requete: SQL, planTexte: 'Seq Scan on seances', coutTotal: 812.3, indexUtilises: 0,
  balayagesComplets: [{
    table: 'seances',
    filtre: '(center_id = $1)',
    colonnes: ['center_id'],
    lignesTable: 130000,
    indexExistants: [],
    verdict: 'INDEX_RECOMMANDE',
    indexSuggere: 'CREATE INDEX IF NOT EXISTS idx_seances_center_id ON seances (center_id);',
  }],
  ...surcharges,
});

describe('AnalyseRequeteDialogComponent', () => {
  let resultat: ReturnType<typeof signal<AnalyseRequete | null>>;
  let store: Record<string, unknown>;
  let ecrire: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    resultat = signal<AnalyseRequete | null>(analyse());
    store = {
      analyse: resultat, analyseLoading: signal(false), analyseError: signal(false),
      analyser: vi.fn().mockResolvedValue(undefined), fermerAnalyse: vi.fn(),
    };
    ecrire = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(globalThis.navigator, 'clipboard', {value: {writeText: ecrire}, configurable: true});
    await TestBed.configureTestingModule({
      imports: [AnalyseRequeteDialogComponent, TranslateModule.forRoot(), NoopAnimationsModule],
      providers: [
        provideZonelessChangeDetection(),
        {provide: PerformanceBaseStore, useValue: store},
        {provide: MAT_DIALOG_DATA, useValue: {id: '42', requete: SQL}},
      ],
    }).compileComponents();
  });

  function creer() {
    const fixture = TestBed.createComponent(AnalyseRequeteDialogComponent);
    fixture.detectChanges();
    return {fixture, root: fixture.nativeElement as HTMLElement};
  }

  it('demande le plan de la requête par son identifiant et affiche son texte', () => {
    const {root} = creer();

    expect(store['analyser']).toHaveBeenCalledWith('42');
    expect(root.querySelector('[data-testid="analyse-requete"]')!.textContent).toContain(SQL);
  });

  it('montre la table lue en entier, le verdict et l\'index à étudier', () => {
    const {root} = creer();

    expect(root.querySelector('[data-testid="analyse-balayage"]')!.textContent).toContain('SUPERVISION.ANALYSE.BALAYAGE_TITRE');
    expect(root.querySelector('[data-testid="analyse-verdict"]')!.textContent)
      .toContain('SUPERVISION.ANALYSE.VERDICT.INDEX_RECOMMANDE');
    expect(root.querySelector('[data-testid="analyse-suggestion"]')!.textContent)
      .toContain('CREATE INDEX IF NOT EXISTS idx_seances_center_id ON seances (center_id);');
  });

  it('copie l\'ordre SQL suggéré', async () => {
    const {root, fixture} = creer();

    root.querySelector<HTMLButtonElement>('[data-testid="analyse-copier"]')!.click();
    await fixture.whenStable();

    expect(ecrire).toHaveBeenCalledWith('CREATE INDEX IF NOT EXISTS idx_seances_center_id ON seances (center_id);');
  });

  it('ne suggère rien pour une table petite et le dit', () => {
    resultat.set(analyse({
      balayagesComplets: [{
        table: 'salle', filtre: '(center_id = $1)', colonnes: ['center_id'], lignesTable: 12, indexExistants: [],
        verdict: 'TABLE_PETITE', indexSuggere: null,
      }],
    }));
    const {root} = creer();

    expect(root.querySelector('[data-testid="analyse-suggestion"]')).toBeNull();
    expect(root.querySelector('[data-testid="analyse-verdict"]')!.textContent)
      .toContain('SUPERVISION.ANALYSE.VERDICT.TABLE_PETITE');
  });

  it('félicite un plan sans lecture complète', () => {
    resultat.set(analyse({balayagesComplets: [], indexUtilises: 2}));
    const {root} = creer();

    expect(root.querySelector('[data-testid="analyse-aucun-balayage"]')).not.toBeNull();
  });

  it('explique pourquoi l\'analyse est impossible', () => {
    resultat.set(analyse({disponible: false, raison: 'NON_SELECT', balayagesComplets: [], planTexte: null}));
    const {root} = creer();

    expect(root.querySelector('[data-testid="analyse-indisponible"]')!.textContent)
      .toContain('SUPERVISION.ANALYSE.RAISON.NON_SELECT');
  });

  it('libère l\'analyse en fermant', () => {
    const {fixture} = creer();

    fixture.destroy();

    expect(store['fermerAnalyse']).toHaveBeenCalled();
  });
});
