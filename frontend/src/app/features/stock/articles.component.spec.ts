import {provideZonelessChangeDetection} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {MatSnackBar} from '@angular/material/snack-bar';
import {TranslateModule} from '@ngx-translate/core';
import {of} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {ArticlesComponent} from './articles.component';
import {ArticleStock, StockApiService} from '../../core/api/stock-api.service';
import {BackendApiService} from '../../core/api/backend-api.service';
import {AppShellStore} from '../../core/state/app-shell.store';

const CENTRE = '11111111-1111-1111-1111-111111111111';

const epo: ArticleStock = {
  id: 'a1', centerId: CENTRE, code: 'EPO-4000', libelle: 'Époétine 4000 UI', unite: 'seringue', stockQuantity: 12,
  seuilAlerte: 10, pmpCourant: 2500, gereParLot: true, active: true, typeTraitementAnemie: 'EPO',
  dosageParUnite: 4000, uniteDosage: 'UI', dci: 'Époétine alfa', peremptionObligatoire: true,
  conditionConservation: 'REFRIGERE', produitDangereux: false, dechetDasri: true,
};

describe('ArticlesComponent (fiche article)', () => {
  let api: Record<string, ReturnType<typeof vi.fn>>;

  beforeEach(async () => {
    api = {
      searchArticles: vi.fn().mockReturnValue(of({items: [epo], total: 1, page: 0, size: 20})),
      createArticle: vi.fn().mockReturnValue(of(epo)),
      updateArticle: vi.fn().mockReturnValue(of(epo)),
      setArticleActive: vi.fn().mockReturnValue(of({...epo, active: false})),
      listFournisseurs: vi.fn().mockReturnValue(of([])),
    };
    await TestBed.configureTestingModule({
      imports: [ArticlesComponent, TranslateModule.forRoot(), NoopAnimationsModule],
      providers: [
        provideZonelessChangeDetection(),
        {provide: StockApiService, useValue: api},
        {provide: BackendApiService, useValue: {listTvaTypes: vi.fn().mockReturnValue(of({items: []}))}},
        {provide: MatSnackBar, useValue: {open: vi.fn()}},
      ],
    }).compileComponents();
    TestBed.inject(AppShellStore).switchCenter(CENTRE);
  });

  async function render() {
    const fixture = TestBed.createComponent(ArticlesComponent);
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

  it('liste les articles du centre en page paginée avec leur dosage par unité', async () => {
    const {root} = await render();

    expect(api['searchArticles']).toHaveBeenCalledWith(CENTRE, {q: '', active: true, page: 0, size: 20});
    expect(root.querySelectorAll('[data-testid="art-table"] tr.mat-mdc-row')).toHaveLength(1);
    expect(root.textContent).toContain('4000 UI / seringue');
    expect(root.querySelector('mat-paginator')).not.toBeNull();
  });

  it('affiche l\'aperçu de conversion et refuse un dosage sans unité', async () => {
    const {fixture, root} = await render();

    type(root, 'art-code', 'EPO-2000');
    type(root, 'art-libelle', 'Époétine 2000 UI');
    type(root, 'art-unite', 'seringue');
    type(root, 'art-dosage', '2000');
    fixture.detectChanges();

    expect(root.querySelector('[data-testid="art-conversion"]')).toBeNull();
    expect(root.querySelector<HTMLButtonElement>('[data-testid="art-save"]')!.disabled).toBe(true);

    type(root, 'art-unite-dosage', 'UI');
    fixture.detectChanges();

    expect(root.querySelector('[data-testid="art-conversion"]')).not.toBeNull();
    expect(root.querySelector<HTMLButtonElement>('[data-testid="art-save"]')!.disabled).toBe(false);
  });

  it('crée la fiche avec les nombres convertis et les champs vides à null', async () => {
    const {fixture, root} = await render();
    type(root, 'art-code', 'EPO-2000');
    type(root, 'art-libelle', 'Époétine 2000 UI');
    type(root, 'art-unite', 'seringue');
    type(root, 'art-dosage', '2000,5');
    type(root, 'art-unite-dosage', 'UI');
    fixture.detectChanges();

    root.querySelector<HTMLButtonElement>('[data-testid="art-save"]')!.click();

    expect(api['createArticle']).toHaveBeenCalledWith(expect.objectContaining({
      centerId: CENTRE, code: 'EPO-2000', unite: 'seringue', dosageParUnite: 2000.5, uniteDosage: 'UI',
      dci: null, coefficientAchat: null, fournisseurId: null, seuilAlerte: 0, stockMax: null,
      compteStock: null, compteCharge: null,
    }));
  });

  it('envoie les comptes comptables de l\'article et refuse un compte mal formé', async () => {
    const {fixture, root} = await render();
    type(root, 'art-code', 'EPO-2000');
    type(root, 'art-libelle', 'Époétine 2000 UI');
    type(root, 'art-unite', 'seringue');
    type(root, 'art-compte-stock', '32-1');
    fixture.detectChanges();
    expect(root.querySelector<HTMLButtonElement>('[data-testid="art-save"]')!.disabled).toBe(true);

    type(root, 'art-compte-stock', ' 321 ');
    type(root, 'art-compte-charge', '6021');
    fixture.detectChanges();
    root.querySelector<HTMLButtonElement>('[data-testid="art-save"]')!.click();

    expect(api['createArticle']).toHaveBeenCalledWith(
      expect.objectContaining({compteStock: '321', compteCharge: '6021'}));
  });

  it('charge la fiche d\'une ligne dans le formulaire puis la modifie', async () => {
    const {fixture, root} = await render();

    root.querySelector<HTMLButtonElement>('[data-testid="art-edit"]')!.click();
    fixture.detectChanges();

    expect(root.querySelector<HTMLInputElement>('[data-testid="art-code"]')!.value).toBe('EPO-4000');
    expect(root.querySelector<HTMLInputElement>('[data-testid="art-dosage"]')!.value).toBe('4000');

    type(root, 'art-dosage', '2000');
    fixture.detectChanges();
    root.querySelector<HTMLButtonElement>('[data-testid="art-save"]')!.click();

    expect(api['updateArticle']).toHaveBeenCalledWith('a1', expect.objectContaining({dosageParUnite: 2000}));
    expect(api['createArticle']).not.toHaveBeenCalled();
  });

  it('désactive un article depuis la liste', async () => {
    const {root} = await render();

    root.querySelector<HTMLButtonElement>('[data-testid="art-toggle"]')!.click();

    expect(api['setArticleActive']).toHaveBeenCalledWith(CENTRE, 'a1', false);
  });
});
