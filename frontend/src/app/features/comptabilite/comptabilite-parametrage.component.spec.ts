import {provideZonelessChangeDetection} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {provideRouter} from '@angular/router';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {TranslateModule} from '@ngx-translate/core';
import {of} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {ComptabiliteApiService, JournalItem, MappingComptableItem} from '../../core/api/comptabilite-api.service';
import {AppShellStore} from '../../core/state/app-shell.store';
import {ComptabiliteParametrageComponent} from './comptabilite-parametrage.component';

const CENTRE = '11111111-1111-1111-1111-111111111111';

const journaux: JournalItem[] = [
  {code: 'AC', libelle: 'Achats', actif: true},
  {code: 'BQ', libelle: 'Banque', actif: true},
  {code: 'CA', libelle: 'Caisse', actif: true},
  {code: 'OD', libelle: 'Opérations diverses', actif: true},
  {code: 'ST', libelle: 'Stocks', actif: true},
  {code: 'VE', libelle: 'Ventes', actif: true},
];

const mapping: MappingComptableItem = {
  centerId: CENTRE, compteVentes: '706', compteClientPatient: '411100', compteClientCnas: '411200',
  compteClientCasnos: '411300', compteClientMutuelle: '411400', compteClientAutre: '411500', compteBanque: '512',
  compteCaisse: '530', compteTVACollectee: '44571', compteStock: '322', compteConsommation: '602',
  compteFacturesNonParvenues: '408', compteBoniInventaire: '757', compteMaliInventaire: '657',
  journaux: {
    VENTE: 'VE', REGLEMENT_BANQUE: 'BQ', REGLEMENT_CAISSE: 'CA', STOCK_RECEPTION: 'AC', STOCK_SORTIE: 'ST',
    STOCK_INVENTAIRE: 'ST',
  },
};

describe('ComptabiliteParametrageComponent', () => {
  let api: Record<string, ReturnType<typeof vi.fn>>;

  beforeEach(async () => {
    api = {
      getMapping: vi.fn().mockReturnValue(of(mapping)),
      getJournaux: vi.fn().mockImplementation((_centre: string, page: number, size: number) =>
        of({items: journaux, total: journaux.length, page, size})),
      saveJournal: vi.fn().mockImplementation((_centre: string, j: JournalItem) => of(j)),
      deleteJournal: vi.fn().mockReturnValue(of(undefined)),
      saveMapping: vi.fn().mockImplementation((m: MappingComptableItem) => of(m)),
      synchroniserStock: vi.fn().mockReturnValue(
        of({receptions: 3, joursSorties: 12, inventaires: 1, complements: 0, ignorees: 2})),
    };
    await TestBed.configureTestingModule({
      imports: [ComptabiliteParametrageComponent, TranslateModule.forRoot(), NoopAnimationsModule],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        {provide: ComptabiliteApiService, useValue: api},
      ],
    }).compileComponents();
    TestBed.inject(AppShellStore).switchCenter(CENTRE);
  });

  async function render() {
    const fixture = TestBed.createComponent(ComptabiliteParametrageComponent);
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

  function bouton(root: HTMLElement, testId: string): HTMLButtonElement {
    return root.querySelector<HTMLButtonElement>(`[data-testid="${testId}"]`)!;
  }

  it('affiche les journaux du centre en liste paginée et ses comptes', async () => {
    const {root} = await render();

    expect(api['getMapping']).toHaveBeenCalledWith(CENTRE);
    expect(root.querySelectorAll('[data-testid="cpa-journaux"] tr[data-journal]')).toHaveLength(6);
    expect(root.querySelector('mat-paginator')).not.toBeNull();
    expect(root.querySelector<HTMLInputElement>('[data-testid="cpa-compteStock"]')!.value).toBe('322');
    expect(root.querySelector<HTMLInputElement>('[data-testid="cpa-compteVentes"]')!.value).toBe('706');
  });

  it('interdit de supprimer un journal choisi pour une opération, pas un journal libre', async () => {
    const {root} = await render();
    const supprimer = (code: string) =>
      root.querySelector<HTMLButtonElement>(`tr[data-journal="${code}"] button[color="warn"]`)!;

    expect(supprimer('VE').disabled).toBe(true);
    expect(supprimer('OD').disabled).toBe(false);
  });

  it('ajoute un journal avec son code normalisé et refuse un code mal formé', async () => {
    const {fixture, root} = await render();
    type(root, 'cpa-journal-code', 'p-1');
    type(root, 'cpa-journal-libelle', 'Paie');
    fixture.detectChanges();
    expect(bouton(root, 'cpa-journal-save').disabled).toBe(true);

    type(root, 'cpa-journal-code', ' pa ');
    fixture.detectChanges();
    bouton(root, 'cpa-journal-save').click();

    expect(api['saveJournal']).toHaveBeenCalledWith(CENTRE, {code: 'PA', libelle: 'Paie', actif: true});
  });

  it('enregistre les comptes modifiés avec le journal de chaque opération', async () => {
    const {fixture, root} = await render();
    type(root, 'cpa-compteStock', ' 32 ');
    fixture.detectChanges();

    bouton(root, 'cpa-comptes-save').click();

    expect(api['saveMapping']).toHaveBeenCalledWith(expect.objectContaining({
      centerId: CENTRE, compteStock: '32', compteConsommation: '602', journaux: mapping.journaux,
    }));
  });

  it('bloque l\'enregistrement tant qu\'un compte obligatoire est vide ou mal formé', async () => {
    const {fixture, root} = await render();
    type(root, 'cpa-compteConsommation', '');
    fixture.detectChanges();
    expect(bouton(root, 'cpa-comptes-save').disabled).toBe(true);

    type(root, 'cpa-compteConsommation', '60/2');
    fixture.detectChanges();
    expect(bouton(root, 'cpa-comptes-save').disabled).toBe(true);
    expect(api['saveMapping']).not.toHaveBeenCalled();
  });

  it('comptabilise le stock sur la période saisie et affiche le résultat', async () => {
    const {fixture, root} = await render();
    type(root, 'cpa-synchro-du', '2026-09-01');
    type(root, 'cpa-synchro-au', '2026-09-30');
    fixture.detectChanges();

    bouton(root, 'cpa-synchro').click();
    fixture.detectChanges();

    expect(api['synchroniserStock']).toHaveBeenCalledWith(CENTRE, '2026-09-01', '2026-09-30');
    const resultat = root.querySelector('[data-testid="cpa-synchro-resultat"]')!;
    expect(resultat.textContent).toContain('12');
  });

  it('refuse une période inversée', async () => {
    const {fixture, root} = await render();
    type(root, 'cpa-synchro-du', '2026-09-30');
    type(root, 'cpa-synchro-au', '2026-09-01');
    fixture.detectChanges();

    expect(bouton(root, 'cpa-synchro').disabled).toBe(true);
  });
});
