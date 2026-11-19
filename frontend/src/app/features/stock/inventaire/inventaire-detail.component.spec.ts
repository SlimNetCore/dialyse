import {computed, signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {ActivatedRoute, convertToParamMap, provideRouter} from '@angular/router';
import {of} from 'rxjs';
import {vi} from 'vitest';
import {TranslateModule} from '@ngx-translate/core';
import {InventaireApiService, LigneInventaire} from '../../../core/api/inventaire-api.service';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {AuthStore} from '../../../core/state/auth.store';
import {WebSocketService} from '../../../core/ws/websocket.service';
import {InventaireStore} from './state/inventaire.store';
import {InventaireDetailComponent} from './inventaire-detail.component';

const ligne = (id: string, over: Partial<LigneInventaire> = {}): LigneInventaire => ({
  id, articleId: `a-${id}`, articleCode: `ART-${id}`, articleLibelle: `Article ${id}`, unite: 'U', lotId: null,
  numeroLot: `LOT-${id}`, datePeremption: null, quantiteTheorique: 10, quantiteComptee: null, ecart: null, pmp: 2,
  valeurEcart: null, motifEcart: null, comptePar: null, compteLe: null, ajoutee: false, ...over,
});

describe('InventaireDetailComponent', () => {
  const details = Array.from({length: 25}, (_, i) => ligne(`l${i}`));
  const enCours = signal(true);
  const store = {
    current: signal<any>({
      id: 'inv-1', reference: 'INV-2026-0001', statut: 'EN_COURS', dateInventaire: '2026-11-19', createdBy: 'pharma',
      commentaire: null, lignes: 25, comptees: 0, ecarts: 0, ecartsSansMotif: 0, valeurEcarts: 0, details,
    }),
    error: signal<string | null>(null),
    loading: signal(false),
    busy: signal(false),
    importResult: signal(null),
    savingLineId: signal<string | null>(null),
    enCours: computed(() => enCours()),
    progression: signal(0),
    pretACloturer: signal(false),
    load: vi.fn(async () => undefined),
    compter: vi.fn(async () => true),
    retirerLigne: vi.fn(),
  };

  function creer() {
    const fixture = TestBed.createComponent(InventaireDetailComponent);
    fixture.detectChanges();
    return {fixture, c: fixture.componentInstance as any, el: fixture.nativeElement as HTMLElement};
  }

  beforeEach(async () => {
    vi.clearAllMocks();
    enCours.set(true);
    await TestBed.configureTestingModule({
      imports: [InventaireDetailComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {paramMap: of(convertToParamMap({id: 'inv-1'})), snapshot: {paramMap: convertToParamMap({id: 'inv-1'})}},
        },
        {provide: InventaireStore, useValue: store},
        {provide: InventaireApiService, useValue: {rapport: vi.fn(), feuilleComptage: vi.fn()}},
        {provide: AppShellStore, useValue: {currentCenterId: () => 'center-1'}},
        {provide: AuthStore, useValue: {hasRole: () => true}},
        {provide: WebSocketService, useValue: {lastEvent: signal(null)}},
      ],
    }).compileComponents();
  });

  it('affiche les lignes de comptage dans une ng-table paginee localement (20 par page)', () => {
    const {c, el} = creer();

    expect(el.querySelector('[data-testid="inventory-lines"] ng-table')).not.toBeNull();
    expect(el.querySelector('[data-testid="inventory-lines"] ng-table-paginator')).not.toBeNull();
    expect(el.querySelector('table.lines')).toBeNull();
    expect(c.defaultPageSize).toBe(20);
    expect(c.pageSizes).toEqual([10, 20, 50, 100]);
    expect(el.querySelector('[data-testid="qty-l0"]')).not.toBeNull();
    expect(el.querySelector('[data-testid="qty-l24"]')).toBeNull();
  });

  it('masque theorique et ecarts pendant un comptage a l aveugle', () => {
    const {c} = creer();
    const visibles = () => c.colonnes().filter((col: any) => col.visible !== false).map((col: any) => col.id);

    expect(visibles()).toEqual(['article', 'lot', 'peremption', 'theorique', 'comptee', 'ecart', 'valeurEcart', 'motif', 'actions']);
    c.aveugle.set(true);
    expect(visibles()).toEqual(['article', 'lot', 'peremption', 'comptee', 'motif', 'actions']);
    expect(c.colonnes().find((col: any) => col.id === 'actions').exportable).toBe(false);
  });

  it('colore les lignes comptees et les ecarts, en suivant la saisie en cours', () => {
    const {c} = creer();
    const l = ligne('x');

    expect(c.classeLigne(l)).toEqual({'line-done': false, 'line-gap': false});
    c.setDraft('x', '7');
    expect(c.classeLigne(l)).toEqual({'line-done': false, 'line-gap': true});
    expect(c.classeLigne(ligne('y', {quantiteComptee: 10, ecart: 0}))).toEqual({'line-done': true, 'line-gap': false});
  });

  it('enregistre la quantite saisie dans la cellule a la sortie du champ', async () => {
    const {fixture, el} = creer();
    const input = el.querySelector<HTMLInputElement>('[data-testid="qty-l0"]')!;

    input.value = '8';
    input.dispatchEvent(new Event('input'));
    input.dispatchEvent(new Event('blur'));
    await fixture.whenStable();

    expect(store.compter).toHaveBeenCalledWith('l0', 8, null);
  });
});
