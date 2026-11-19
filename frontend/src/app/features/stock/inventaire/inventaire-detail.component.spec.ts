import {computed, signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {ActivatedRoute, convertToParamMap, provideRouter} from '@angular/router';
import {HttpErrorResponse} from '@angular/common/http';
import {Observable, of, throwError} from 'rxjs';
import {vi} from 'vitest';
import {PageServeur} from '../../../core/api/page-serveur';
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

  const premiereVersion = store.current();
  const api = {
    rapport: vi.fn(),
    feuilleComptage: vi.fn(),
    lignesPage: vi.fn((..._args: unknown[]): Observable<PageServeur<LigneInventaire>> =>
      of({items: details.slice(0, 20), total: 25, page: 0, size: 20})),
  };

  function creer() {
    const fixture = TestBed.createComponent(InventaireDetailComponent);
    fixture.detectChanges();
    return {fixture, c: fixture.componentInstance as any, el: fixture.nativeElement as HTMLElement};
  }

  beforeEach(async () => {
    vi.clearAllMocks();
    enCours.set(true);
    store.current.set(premiereVersion);
    await TestBed.configureTestingModule({
      imports: [InventaireDetailComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {paramMap: of(convertToParamMap({id: 'inv-1'})), snapshot: {paramMap: convertToParamMap({id: 'inv-1'})}},
        },
        {provide: InventaireStore, useValue: store},
        {provide: InventaireApiService, useValue: api},
        {provide: AppShellStore, useValue: {currentCenterId: () => 'center-1'}},
        {provide: AuthStore, useValue: {hasRole: () => true}},
        {provide: WebSocketService, useValue: {lastEvent: signal(null)}},
      ],
    }).compileComponents();
  });

  it('affiche la page de lignes lue sur le serveur (GET /{id}/lignes/page, 20 par page)', () => {
    const {c, el} = creer();

    expect(api.lignesPage).toHaveBeenCalledWith('center-1', 'inv-1', {page: '0', size: '20'});
    expect(el.querySelector('[data-testid="inventory-lines"] ng-table')).not.toBeNull();
    expect(el.querySelector('[data-testid="inventory-lines"] ng-table-paginator')).not.toBeNull();
    expect(el.querySelector('table.lines')).toBeNull();
    expect(c.liste.total()).toBe(25);
    expect(c.pageSizes).toEqual([10, 20, 50, 100]);
    expect(el.querySelector('[data-testid="qty-l0"]')).not.toBeNull();
    expect(el.querySelector('[data-testid="qty-l24"]')).toBeNull();
  });

  it('filtre d etat et recherche partent au serveur (etat, q) et ramenent en premiere page', () => {
    const {c, fixture} = creer();
    c.onRemoteQueryChange({sort: {columnId: 'lot', direction: 'asc'}, filters: {}, page: {index: 1, size: 20}});
    c.filtre.set('ECARTS');
    c.recherche.set(' lot-3 ');
    fixture.detectChanges();

    expect(api.lignesPage).toHaveBeenLastCalledWith('center-1', 'inv-1',
      {page: '0', size: '20', sort: 'lot,asc', etat: 'ECARTS', q: 'lot-3'});
    c.filtre.set('TOUTES');
    c.recherche.set('');
    fixture.detectChanges();
    expect(api.lignesPage).toHaveBeenLastCalledWith('center-1', 'inv-1', {page: '0', size: '20', sort: 'lot,asc'});
  });

  it('comptage a l aveugle : pas de tri sur le theorique ni les ecarts', () => {
    const {c, fixture} = creer();
    c.onRemoteQueryChange({sort: {columnId: 'ecart', direction: 'desc'}, filters: {}, page: {index: 0, size: 20}});
    expect(api.lignesPage).toHaveBeenLastCalledWith('center-1', 'inv-1', {page: '0', size: '20', sort: 'ecart,desc'});
    c.aveugle.set(true);
    fixture.detectChanges();
    expect(api.lignesPage).toHaveBeenLastCalledWith('center-1', 'inv-1', {page: '0', size: '20'});
  });

  it('en-tete relu apres une saisie : la page courante est relue et la ligne saisie remplacee aussitot', () => {
    const {c, fixture} = creer();
    c.onRemoteQueryChange({sort: {columnId: '', direction: ''}, filters: {}, page: {index: 1, size: 20}});
    const appels = api.lignesPage.mock.calls.length;
    const comptee = {...details[0], quantiteComptee: 9, ecart: -1};
    store.current.set({...store.current(), comptees: 1, details: [comptee, ...details.slice(1)]});
    fixture.detectChanges();

    expect(api.lignesPage.mock.calls.length).toBe(appels + 1);
    expect(api.lignesPage).toHaveBeenLastCalledWith('center-1', 'inv-1', {page: '1', size: '20'});
    api.lignesPage.mockReturnValueOnce(of({items: [details[0]], total: 1, page: 0, size: 20}));
    c.liste.premierePage();
    expect(c.lignes()[0].quantiteComptee).toBe(9);
  });

  it('une erreur de la page vide la table et affiche le message', () => {
    api.lignesPage.mockReturnValueOnce(throwError(() => new HttpErrorResponse({status: 400, error: {code: 'TRI_INVALIDE'}})));
    const {c, el, fixture} = creer();
    fixture.detectChanges();

    expect(c.lignes()).toEqual([]);
    expect(el.querySelector('[data-testid="liste-erreur"]')?.textContent).toContain('COMMON.LISTE_SERVEUR.TRI_INVALIDE');
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
