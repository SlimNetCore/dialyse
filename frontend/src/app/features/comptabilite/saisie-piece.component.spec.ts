import {provideZonelessChangeDetection} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {ActivatedRoute, convertToParamMap, provideRouter} from '@angular/router';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {of} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {ComptabiliteApiService, ModelePieceItem} from '../../core/api/comptabilite-api.service';
import {AppShellStore} from '../../core/state/app-shell.store';
import {AuthStore} from '../../core/state/auth.store';
import {SaisiePieceComponent} from './saisie-piece.component';

const CENTRE = '11111111-1111-1111-1111-111111111111';

const loyer: ModelePieceItem = {
  id: 'm1', code: 'LOYER', libelle: 'Loyer du centre', journal: 'AC', actif: true,
  lignes: [
    {sens: 'DEBIT', compte: '613', libelle: 'Loyer'},
    {sens: 'DEBIT', compte: '4456'},
    {sens: 'CREDIT', compte: '401'},
  ],
};

describe('SaisiePieceComponent', () => {
  let api: Record<string, ReturnType<typeof vi.fn>>;
  let modeles: ModelePieceItem[];
  let modeleDemande: string | null;

  beforeEach(() => {
    modeles = [loyer];
    modeleDemande = 'm1';
    api = {
      getModeles: vi.fn().mockImplementation(() => of({items: modeles, total: modeles.length, page: 0, size: 100})),
      getComptes: vi.fn().mockReturnValue(of({
        items: [{numero: '401', libelle: 'Fournisseurs', actif: true}], total: 1, page: 0, size: 500,
      })),
      saisirPiece: vi.fn().mockReturnValue(of({
        id: 'e1', numeroPiece: 'AC-2026-000001', journalCode: 'AC', dateEcriture: '2026-10-05', libelle: 'Loyer',
        total: 119000,
      })),
    };
  });

  async function render() {
    await TestBed.configureTestingModule({
      imports: [SaisiePieceComponent, TranslateModule.forRoot(), NoopAnimationsModule],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        {provide: ComptabiliteApiService, useValue: api},
        {provide: AuthStore, useValue: {hasRole: (role: string) => role === 'SECRETAIRE', centerId: () => CENTRE}},
        {
          provide: ActivatedRoute,
          useValue: {queryParamMap: of(convertToParamMap(modeleDemande ? {modele: modeleDemande} : {}))},
        },
      ],
    }).compileComponents();
    TestBed.inject(AppShellStore).switchCenter(CENTRE);
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('fr', {COMPTABILITE: {PIECES: {CONFIRMATION: 'Pièce {{numero}} enregistrée pour {{total}}.'}}});
    translate.use('fr');
    const fixture = TestBed.createComponent(SaisiePieceComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return {fixture, root: fixture.nativeElement as HTMLElement};
  }

  function type(root: HTMLElement, testId: string, value: string) {
    const input = root.querySelector<HTMLInputElement>(`[data-testid="${testId}"]`)!;
    input.value = value;
    input.dispatchEvent(new Event('input', {bubbles: true}));
  }

  const enregistrer = (root: HTMLElement) => root.querySelector<HTMLButtonElement>('[data-testid="piece-save"]')!;

  it('présélectionne le modèle demandé et affiche une ligne de montant par ligne du modèle', async () => {
    const {root} = await render();

    expect(api['getModeles']).toHaveBeenCalledWith(CENTRE, 0, 100, true);
    expect(root.querySelectorAll('[data-testid^="piece-montant-"]')).toHaveLength(3);
    expect(root.textContent).toContain('Fournisseurs');
    expect(enregistrer(root).disabled).toBe(true);
  });

  it('n\'enregistre la pièce qu\'équilibrée, avec les montants lus comme des nombres', async () => {
    const {fixture, root} = await render();
    type(root, 'piece-montant-0', '100 000');
    type(root, 'piece-montant-1', '19000,00');
    type(root, 'piece-montant-2', '100000');
    fixture.detectChanges();
    expect(enregistrer(root).disabled).toBe(true);

    type(root, 'piece-montant-2', '119000');
    type(root, 'piece-date', '2026-10-05');
    type(root, 'piece-libelle', ' Loyer octobre ');
    fixture.detectChanges();
    enregistrer(root).click();
    fixture.detectChanges();

    expect(api['saisirPiece']).toHaveBeenCalledWith(CENTRE,
      {modeleId: 'm1', date: '2026-10-05', libelle: 'Loyer octobre', montants: [100000, 19000, 119000]});
    expect(root.querySelector('[data-testid="piece-succes"]')!.textContent).toContain('AC-2026-000001');
    // la saisie repart vierge, sur le même modèle
    expect(root.querySelector<HTMLInputElement>('[data-testid="piece-montant-0"]')!.value).toBe('');
  });

  it('refuse un montant qui n\'est pas un nombre positif', async () => {
    const {fixture, root} = await render();
    type(root, 'piece-montant-0', '-5');
    type(root, 'piece-montant-2', 'abc');
    fixture.detectChanges();

    expect(enregistrer(root).disabled).toBe(true);
    expect(api['saisirPiece']).not.toHaveBeenCalled();
  });

  it('explique qu\'il faut un modèle quand le centre n\'en a aucun d\'actif', async () => {
    modeles = [];
    modeleDemande = null;
    const {root} = await render();

    expect(root.querySelector('[data-testid="piece-aucun-modele"]')).not.toBeNull();
    expect(root.querySelector('[data-testid="piece-save"]')).toBeNull();
  });
});
