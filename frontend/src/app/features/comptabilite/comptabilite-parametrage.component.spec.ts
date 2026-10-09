import {provideZonelessChangeDetection} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {provideRouter} from '@angular/router';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {TranslateModule} from '@ngx-translate/core';
import {of} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {
  ComptabiliteApiService,
  CompteItem,
  JournalItem,
  MappingComptableItem,
  PayeurCompteItem,
} from '../../core/api/comptabilite-api.service';
import {AppShellStore} from '../../core/state/app-shell.store';
import {ComptabiliteParametrageComponent} from './comptabilite-parametrage.component';
import {ModelesPieceComponent} from './modeles-piece.component';
import {By} from '@angular/platform-browser';

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
  centerId: CENTRE, compteVentes: '706', compteClientPatient: '411100', compteClientDefaut: '411500',
  compteBanque: '512', compteCaisse: '530', compteTVACollectee: '44571', compteStock: '322', compteConsommation: '602',
  compteFacturesNonParvenues: '408', compteBoniInventaire: '757', compteMaliInventaire: '657',
  journaux: {
    VENTE: 'VE', REGLEMENT_BANQUE: 'BQ', REGLEMENT_CAISSE: 'CA', STOCK_RECEPTION: 'AC', STOCK_SORTIE: 'ST',
    STOCK_INVENTAIRE: 'ST',
  },
};

/** Plan comptable du centre : les comptes du paramétrage, et quelques comptes libres. */
const plan: CompteItem[] = ['706', '411100', '411500', '512', '530', '44571', '322', '602', '408', '757', '657', '32',
  '613', '401', '411210'].map((numero) => ({numero, libelle: `Compte ${numero}`, actif: true}));

const payeurs: PayeurCompteItem[] = [
  {payeurId: 'p1', code: 'CNAS-16', nom: 'CNAS Alger', compte: null},
  {payeurId: 'p2', code: 'MUT-1', nom: 'Mutuelle X', compte: '411500'},
];

describe('ComptabiliteParametrageComponent', () => {
  let api: Record<string, ReturnType<typeof vi.fn>>;

  beforeEach(async () => {
    api = {
      getComptes: vi.fn().mockImplementation((_centre: string, page: number, size: number) =>
        of({items: plan.slice(0, size), total: plan.length, page, size})),
      saveCompte: vi.fn().mockImplementation((_centre: string, c: CompteItem) => of(c)),
      deleteCompte: vi.fn().mockReturnValue(of(undefined)),
      getPayeurs: vi.fn().mockImplementation((_centre: string, page: number, size: number) =>
        of({items: payeurs, total: payeurs.length, page, size})),
      saveComptePayeur: vi.fn().mockReturnValue(of(payeurs[0])),
      getModeles: vi.fn().mockImplementation((_centre: string, page: number, size: number) =>
        of({items: [], total: 0, page, size})),
      createModele: vi.fn().mockImplementation((_centre: string, m: unknown) => of({id: 'm1', ...(m as object)})),
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

  it('bloque l\'enregistrement tant qu\'un compte obligatoire est vide ou absent du plan comptable', async () => {
    const {fixture, root} = await render();
    type(root, 'cpa-compteConsommation', '');
    fixture.detectChanges();
    expect(bouton(root, 'cpa-comptes-save').disabled).toBe(true);

    type(root, 'cpa-compteConsommation', '6029');
    fixture.detectChanges();
    expect(bouton(root, 'cpa-comptes-save').disabled).toBe(true);
    expect(api['saveMapping']).not.toHaveBeenCalled();

    // la TVA collectée, elle, peut rester vide
    type(root, 'cpa-compteConsommation', '602');
    type(root, 'cpa-compteTVACollectee', '');
    fixture.detectChanges();
    expect(bouton(root, 'cpa-comptes-save').disabled).toBe(false);
  });

  it('affiche le plan comptable paginé et y ajoute un compte', async () => {
    const {fixture, root} = await render();

    expect(api['getComptes']).toHaveBeenCalledWith(CENTRE, 0, 10, '');
    expect(root.querySelectorAll('[data-testid="plan-comptes"] tr[data-compte]')).toHaveLength(10);
    type(root, 'plan-numero', '41-12');
    type(root, 'plan-libelle', 'Clients — entreprise Y');
    fixture.detectChanges();
    expect(bouton(root, 'plan-save').disabled).toBe(true);

    type(root, 'plan-numero', ' 411220 ');
    fixture.detectChanges();
    bouton(root, 'plan-save').click();

    expect(api['saveCompte']).toHaveBeenCalledWith(CENTRE,
      {numero: '411220', libelle: 'Clients — entreprise Y', actif: true});
  });

  it('affecte à un payeur un compte du plan, et refuse un compte qui n\'y figure pas', async () => {
    const {fixture, root} = await render();

    expect(root.querySelectorAll('[data-testid="payeurs"] tr[data-payeur]')).toHaveLength(2);
    expect(bouton(root, 'payeur-save-p1').disabled).toBe(true);
    type(root, 'payeur-compte-p1', '411999');
    fixture.detectChanges();
    expect(bouton(root, 'payeur-save-p1').disabled).toBe(true);

    type(root, 'payeur-compte-p1', '411210');
    fixture.detectChanges();
    bouton(root, 'payeur-save-p1').click();

    expect(api['saveComptePayeur']).toHaveBeenCalledWith(CENTRE, 'p1', '411210');
    // vider le compte d'un payeur lui rend le compte client par défaut
    type(root, 'payeur-compte-p2', '');
    fixture.detectChanges();
    bouton(root, 'payeur-save-p2').click();
    expect(api['saveComptePayeur']).toHaveBeenCalledWith(CENTRE, 'p2', '');
  });

  it('crée un modèle de pièce à partir d\'un journal et de comptes du plan', async () => {
    const {fixture, root} = await render();
    const component = fixture.debugElement.query(By.directive(ModelesPieceComponent)).componentInstance;
    expect(bouton(root, 'modele-save').disabled).toBe(true);

    type(root, 'modele-code', 'loyer');
    type(root, 'modele-libelle', 'Loyer du centre');
    component['patch']({journal: 'AC'});
    type(root, 'modele-compte-0', '613');
    type(root, 'modele-compte-1', '401');
    fixture.detectChanges();
    bouton(root, 'modele-save').click();

    expect(api['createModele']).toHaveBeenCalledWith(CENTRE, {
      code: 'LOYER', libelle: 'Loyer du centre', journal: 'AC', actif: true,
      lignes: [{sens: 'DEBIT', compte: '613', libelle: null}, {sens: 'CREDIT', compte: '401', libelle: null}],
    });
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
