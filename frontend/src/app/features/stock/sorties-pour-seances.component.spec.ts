import {TestBed} from '@angular/core/testing';
import {of, throwError} from 'rxjs';
import {vi} from 'vitest';
import {MatSnackBar} from '@angular/material/snack-bar';
import {TranslateModule} from '@ngx-translate/core';
import {SortiesPourSeancesComponent} from './sorties-pour-seances.component';
import {SortiePourSeances, StockApiService} from '../../core/api/stock-api.service';
import {ReferentialApiService} from '../../core/api/referential-api.service';
import {AuthStore} from '../../core/state/auth.store';

const piece = (over: Partial<SortiePourSeances> = {}): SortiePourSeances => ({
  id: 'p1', centerId: 'center-1', reference: 'SPS-00001', jour: '2026-10-24', salle: null, demiJournee: null,
  statut: 'ENREGISTREE', enregistreeLe: '2026-10-23T08:00:00Z', enregistreePar: 'g', valideeLe: null, valideePar: null,
  lignes: [
    {
      id: 'l1',
      articleId: 'a1',
      lotId: 'lot1',
      quantiteSortie: 5,
      valeurUnitaire: 8,
      quantiteConsommee: 3,
      ecart: -2,
      motif: null
    },
    {
      id: 'l2',
      articleId: 'a2',
      lotId: 'lot2',
      quantiteSortie: 2,
      valeurUnitaire: 4,
      quantiteConsommee: 2,
      ecart: 0,
      motif: null
    },
  ],
  ...over,
});

describe('SortiesPourSeancesComponent', () => {
  let roles: string[];

  const api = {
    listSortiesPourSeances: vi.fn(() => of([piece()])),
    createSortiePourSeances: vi.fn(() => of(piece({id: 'p2', reference: 'SPS-00002'}))),
    preparerLeRapprochement: vi.fn(() => of(piece())),
    justifierUnEcart: vi.fn(() => of(piece())),
    validerLeRapprochement: vi.fn(() => of(piece({statut: 'RAPPROCHEE'}))),
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

  beforeEach(async () => {
    vi.clearAllMocks();
    roles = ['PHARMACIEN'];
    await TestBed.configureTestingModule({
      imports: [SortiesPourSeancesComponent, TranslateModule.forRoot()],
      providers: [
        {provide: StockApiService, useValue: api},
        {provide: ReferentialApiService, useValue: refApi},
        {provide: AuthStore, useValue: auth},
        {provide: MatSnackBar, useValue: snack},
      ],
    }).compileComponents();
  });

  it('charge les pieces et les articles du centre', () => {
    const {c} = creer();
    expect(api.listSortiesPourSeances).toHaveBeenCalledWith('center-1');
    expect(refApi.getArticles).toHaveBeenCalledWith('center-1');
    expect(c.pieces().length).toBe(1);
  });

  it('reserve les actions selon le role (T42)', () => {
    roles = ['DIRECTEUR_ADMINISTRATIF'];
    let {c} = creer();
    expect(c.peutRapprocher()).toBe(true);
    expect(c.peutEnregistrer()).toBe(false);
    expect(c.peutJustifier()).toBe(false);

    TestBed.resetTestingModule();
    roles = ['INFIRMIER'];
    TestBed.configureTestingModule({
      imports: [SortiesPourSeancesComponent, TranslateModule.forRoot()],
      providers: [
        {provide: StockApiService, useValue: api}, {provide: ReferentialApiService, useValue: refApi},
        {provide: AuthStore, useValue: auth}, {provide: MatSnackBar, useValue: snack},
      ],
    });
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

  it('enregistre la sortie avec le perimetre saisi et l ajoute en tete de liste', () => {
    const {c} = creer();
    c.modifierEntete({jour: new Date(2026, 9, 24), salle: ' S1 ', demiJournee: 'MATIN'});
    c.modifierLigne(0, {articleId: 'a1', quantite: 4});

    c.enregistrer();

    expect(api.createSortiePourSeances).toHaveBeenCalledWith({
      centerId: 'center-1', jour: '2026-10-24', salle: 'S1', demiJournee: 'MATIN',
      lignes: [{articleId: 'a1', quantite: 4}],
    });
    expect(c.pieces()[0].id).toBe('p2');
    expect(c.selectionId()).toBe('p2');
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

  it('bloque la validation tant qu un ecart n a pas de motif (T40)', () => {
    const {c} = creer();
    c.selectionner(c.pieces()[0]);
    expect(c.peutValider()).toBe(false);
    expect(c.ecartSansMotif(c.pieces()[0].lignes[0])).toBe(true);
    expect(c.ecartSansMotif(c.pieces()[0].lignes[1])).toBe(false);

    c.pieces.set([piece({
      lignes: [{...piece().lignes[0], motif: 'Casse'}, piece().lignes[1]],
    })]);
    expect(c.peutValider()).toBe(true);
  });

  it('enregistre le motif saisi puis valide le rapprochement', () => {
    const {c} = creer();
    const p = c.pieces()[0];
    c.justifier(p, p.lignes[0], {target: {value: '  Casse  '}} as unknown as Event);
    expect(api.justifierUnEcart).toHaveBeenCalledWith('p1', 'l1', 'center-1', 'Casse');

    c.pieces.set([piece({lignes: [{...piece().lignes[0], motif: 'Casse'}, piece().lignes[1]]})]);
    c.selectionner(c.pieces()[0]);
    c.valider(c.pieces()[0]);
    expect(api.validerLeRapprochement).toHaveBeenCalledWith('p1', 'center-1');
    expect(c.pieces()[0].statut).toBe('RAPPROCHEE');
  });

  it('ne renvoie pas un motif vide ou inchange', () => {
    const {c} = creer();
    const p = c.pieces()[0];
    c.justifier(p, p.lignes[0], {target: {value: '   '}} as unknown as Event);
    expect(api.justifierUnEcart).not.toHaveBeenCalled();
  });
});
