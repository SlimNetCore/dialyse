import {TestBed} from '@angular/core/testing';
import {of, throwError} from 'rxjs';
import {vi} from 'vitest';
import {MatSnackBar} from '@angular/material/snack-bar';
import {TranslateModule} from '@ngx-translate/core';
import {SortiesPourSeancesComponent} from './sorties-pour-seances.component';
import {SortiePourSeances, StockApiService} from '../../core/api/stock-api.service';
import {PieceSortiePourSeancesResume, StockPagesApiService} from '../../core/api/stock-pages-api.service';
import {ReferentialApiService} from '../../core/api/referential-api.service';
import {AuthStore} from '../../core/state/auth.store';

const piece = (over: Partial<SortiePourSeances> = {}): SortiePourSeances => ({
  id: 'p1', centerId: 'center-1', reference: 'SPS-00001', jour: '2026-10-24', salle: null, demiJournee: null,
  statut: 'ENREGISTREE', enregistreeLe: '2026-10-23T08:00:00Z', enregistreePar: 'g', valideeLe: null, valideePar: null,
  lignes: [
    {id: 'l1', articleId: 'a1', lotId: 'lot1', quantiteSortie: 5, valeurUnitaire: 8, quantiteConsommee: 3, ecart: -2, motif: null},
    {id: 'l2', articleId: 'a2', lotId: 'lot2', quantiteSortie: 2, valeurUnitaire: 4, quantiteConsommee: 2, ecart: 0, motif: null},
  ],
  ...over,
});

const resume = (p: SortiePourSeances): PieceSortiePourSeancesResume => {
  const {lignes, ...reste} = p;
  return {...reste, nombreDeLignes: lignes.length};
};

describe('SortiesPourSeancesComponent', () => {
  let roles: string[];

  const api = {
    listSortiesPourSeances: vi.fn(),
    createSortiePourSeances: vi.fn(() => of(piece({id: 'p2', reference: 'SPS-00002'}))),
    preparerLeRapprochement: vi.fn(() => of(piece())),
    justifierUnEcart: vi.fn(() => of(piece())),
    validerLeRapprochement: vi.fn(() => of(piece({statut: 'RAPPROCHEE'}))),
  };
  const pagesApi = {
    piecesPourSeances: vi.fn((_c: string, p: Record<string, string>) =>
      of({items: [resume(piece())], total: 1, page: Number(p['page']), size: Number(p['size'])})),
    pieceDeSortie: vi.fn((_c: string, _id: string) => of(piece())),
    lignesDeLaPiece: vi.fn((_c: string, _id: string, p: Record<string, string>) => of({
      items: piece().lignes.map((l) => ({...l, articleCode: 'A1', articleLibelle: 'Article 1'})),
      total: 2, page: Number(p['page']), size: Number(p['size']),
    })),
  };
  const refApi = {getArticles: vi.fn(() => of([{id: 'a1', code: 'A1', libelle: 'Article 1'}]))};
  const auth = {
    centerId: vi.fn(() => 'center-1'),
    hasRole: vi.fn((role: string) => roles.includes(role)),
  };
  const snack = {open: vi.fn()};

  function creer() {
    const fixture = TestBed.createComponent(SortiesPourSeancesComponent);
    fixture.detectChanges();
    return {fixture, c: fixture.componentInstance as any};
  }

  function configurer() {
    TestBed.configureTestingModule({
      imports: [SortiesPourSeancesComponent, TranslateModule.forRoot()],
      providers: [
        {provide: StockApiService, useValue: api},
        {provide: StockPagesApiService, useValue: pagesApi},
        {provide: ReferentialApiService, useValue: refApi},
        {provide: AuthStore, useValue: auth},
        {provide: MatSnackBar, useValue: snack},
      ],
    });
  }

  beforeEach(async () => {
    vi.clearAllMocks();
    roles = ['PHARMACIEN'];
    configurer();
    await TestBed.compileComponents();
  });

  it('charge la premiere page des pieces et les articles du centre', () => {
    const {c} = creer();
    expect(pagesApi.piecesPourSeances).toHaveBeenCalledWith('center-1', {page: '0', size: '20'});
    expect(api.listSortiesPourSeances).not.toHaveBeenCalled();
    expect(refApi.getArticles).toHaveBeenCalledWith('center-1');
    expect(c.pieces.lignes().length).toBe(1);
    expect(c.pieces.total()).toBe(1);
  });

  it('envoie tri et filtres des pieces au serveur (statut = valeur d enumeration)', () => {
    const {c} = creer();
    c.surRequeteDesPieces({sort: {columnId: 'jour', direction: 'asc'}, filters: {statut: 'RAPPROCHEE', reference: 'SPS'},
      page: {index: 0, size: 20}});
    expect(pagesApi.piecesPourSeances).toHaveBeenLastCalledWith('center-1',
      {page: '0', size: '20', sort: 'jour,asc', statut: 'RAPPROCHEE', reference: 'SPS'});
  });

  it('reserve les actions selon le role (T42)', () => {
    roles = ['DIRECTEUR_ADMINISTRATIF'];
    let {c} = creer();
    expect(c.peutRapprocher()).toBe(true);
    expect(c.peutEnregistrer()).toBe(false);
    expect(c.peutJustifier()).toBe(false);

    TestBed.resetTestingModule();
    roles = ['INFIRMIER'];
    configurer();
    ({c} = creer());
    expect(c.peutJustifier()).toBe(false);
    expect(c.peutRapprocher()).toBe(false);
    expect(c.peutEnregistrer()).toBe(false);
  });

  it('refuse l enregistrement sans article ni quantite positive', () => {
    const {c} = creer();
    expect(c.peutSauvegarder()).toBe(false);
    c.modifierLigne(0, {articleId: 'a1', quantite: 0});
    expect(c.peutSauvegarder()).toBe(false);
    c.modifierLigne(0, {articleId: 'a1', quantite: 4});
    expect(c.peutSauvegarder()).toBe(true);
  });

  it('enregistre la sortie avec le perimetre saisi, recharge la page et deroule la nouvelle piece', () => {
    const {c} = creer();
    c.modifierEntete({jour: new Date(2026, 9, 24), salle: ' S1 ', demiJournee: 'MATIN'});
    c.modifierLigne(0, {articleId: 'a1', quantite: 4});
    const appels = pagesApi.piecesPourSeances.mock.calls.length;

    c.enregistrer();

    expect(api.createSortiePourSeances).toHaveBeenCalledWith({
      centerId: 'center-1', jour: '2026-10-24', salle: 'S1', demiJournee: 'MATIN',
      lignes: [{articleId: 'a1', quantite: 4}],
    });
    expect(pagesApi.piecesPourSeances.mock.calls.length).toBe(appels + 1);
    expect(c.selectionId()).toBe('p2');
    expect(c.selection().reference).toBe('SPS-00002');
    expect(pagesApi.lignesDeLaPiece).toHaveBeenLastCalledWith('center-1', 'p2', {page: '0', size: '20'});
  });

  it('affiche le detail de l erreur du serveur', () => {
    api.createSortiePourSeances.mockReturnValueOnce(
      throwError(() => ({error: {message: 'Stock insuffisant'}})) as never);
    const {c} = creer();
    c.modifierLigne(0, {articleId: 'a1', quantite: 4});

    c.enregistrer();

    expect(snack.open).toHaveBeenCalled();
    expect(c.saving()).toBe(false);
  });

  it('bloque la validation tant qu un ecart de la piece complete n a pas de motif (T40)', () => {
    const {c} = creer();
    c.selectionner(c.pieces.lignes()[0]);
    expect(pagesApi.pieceDeSortie).toHaveBeenCalledWith('center-1', 'p1');
    expect(c.peutValider()).toBe(false);
    expect(c.ecartSansMotif(piece().lignes[0])).toBe(true);
    expect(c.ecartSansMotif(piece().lignes[1])).toBe(false);

    c.piece.set(piece({lignes: [{...piece().lignes[0], motif: 'Casse'}, piece().lignes[1]]}));
    expect(c.peutValider()).toBe(true);
  });

  it('enregistre le motif saisi, relit la page courante des lignes, puis valide le rapprochement', () => {
    const {c} = creer();
    c.selectionner(c.pieces.lignes()[0]);
    c.surRequeteDesLignes({sort: {columnId: 'ecart', direction: 'desc'}, filters: {article: 'A1'}, page: {index: 0, size: 20}});
    const p = c.selection();
    c.justifier(p, p.lignes[0], {target: {value: '  Casse  '}} as unknown as Event);
    expect(api.justifierUnEcart).toHaveBeenCalledWith('p1', 'l1', 'center-1', 'Casse');
    expect(pagesApi.lignesDeLaPiece).toHaveBeenLastCalledWith('center-1', 'p1',
      {page: '0', size: '20', sort: 'ecart,desc', article: 'A1'});

    c.piece.set(piece({lignes: [{...piece().lignes[0], motif: 'Casse'}, piece().lignes[1]]}));
    const appels = pagesApi.piecesPourSeances.mock.calls.length;
    c.valider(c.selection());
    expect(api.validerLeRapprochement).toHaveBeenCalledWith('p1', 'center-1');
    expect(c.selection().statut).toBe('RAPPROCHEE');
    expect(pagesApi.piecesPourSeances.mock.calls.length).toBe(appels + 1);
  });

  it('affiche les pieces dans une ng-table distante paginee, statut en pastille, sans mat-table', () => {
    const {fixture, c} = creer();
    const el: HTMLElement = fixture.nativeElement;

    expect(el.querySelector('[data-testid="sorties-seances-pieces"] ng-table')).not.toBeNull();
    expect(el.querySelector('table[mat-table], mat-chip')).toBeNull();
    expect(c.defaultPageSize).toBe(20);
    expect(c.pageSizes).toEqual([10, 20, 50, 100]);
    const colonnes = c.colonnesPieces();
    const parId = (id: string) => colonnes.find((col: { id: string }) => col.id === id);
    expect(colonnes.map((col: { id: string }) => col.id)).toEqual(['reference', 'jour', 'perimetre', 'statut', 'lignes', 'actions']);
    expect(parId('statut').cell).toEqual(expect.objectContaining({type: 'badge', tone: {ENREGISTREE: 'info', RAPPROCHEE: 'success'}}));
    expect(parId('statut').filter.options.map((o: { value: string }) => o.value)).toEqual(['ENREGISTREE', 'RAPPROCHEE']);
    expect(parId('jour').valueAccessor(resume(piece()))).toBe('24/10/2026');
    expect(parId('lignes').valueAccessor(resume(piece()))).toBe(2);
    expect(parId('perimetre').sortable).toBe(false);
    expect(parId('perimetre').filter).toBeUndefined();
    expect(parId('actions').exportable).toBe(false);
    expect(el.textContent).toContain('SPS-00001');
  });

  it('deroule les lignes paginees de la piece par le bouton de la ligne, avec la saisie du motif d un ecart', () => {
    const {fixture, c} = creer();
    const el: HTMLElement = fixture.nativeElement;
    const bouton = el.querySelector('button[data-row-id="p1"]') as HTMLButtonElement;
    expect(bouton).not.toBeNull();

    bouton.click();
    fixture.detectChanges();

    expect(c.selectionId()).toBe('p1');
    expect(pagesApi.lignesDeLaPiece).toHaveBeenCalledWith('center-1', 'p1', {page: '0', size: '20'});
    expect(el.querySelector('[data-testid="sorties-seances-lignes"] ng-table')).not.toBeNull();
    const colonnes = c.colonnesLignes();
    expect(colonnes.map((col: { id: string }) => col.id)).toEqual(['article', 'sortie', 'consomme', 'ecart', 'motif']);
    expect(colonnes[0].valueAccessor({articleId: 'a1', articleCode: 'A1', articleLibelle: 'Article 1'})).toBe('A1 - Article 1');
    expect(colonnes.find((col: { id: string }) => col.id === 'motif').sortable).toBe(false);
    const motifs = el.querySelectorAll('[data-testid="sorties-seances-lignes"] input[maxlength="500"]');
    expect(motifs.length).toBe(1);
    expect(el.querySelector('.gap-minus')?.textContent?.trim()).toBe('-2');
  });

  it('ne renvoie pas un motif vide ou inchange', () => {
    const {c} = creer();
    c.justifier(piece(), piece().lignes[0], {target: {value: '   '}} as unknown as Event);
    expect(api.justifierUnEcart).not.toHaveBeenCalled();
  });
});
