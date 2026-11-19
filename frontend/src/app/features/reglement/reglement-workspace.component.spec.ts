import {signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TranslateService} from '@ngx-translate/core';
import {Subject} from 'rxjs';
import type {NgTableColumn, NgTableRemoteQuery} from '@sbourahla/ng-table';
import {ReglementWorkspaceComponent} from './reglement-workspace.component';
import {ReglementPreviewRow, ReglementStore} from './state/reglement.store';
import {AppShellStore} from '../../core/state/app-shell.store';
import {AuthStore} from '../../core/state/auth.store';
import {NgTableTranslatedLabels} from '../../shared/ng-table-labels';

const ligne = (factureId: string, patientNom: string, montantFacture: number): ReglementPreviewRow => ({
  factureId, numeroFacture: `F-${factureId}`, patientNom, montantFacture, montantRegle: 0, reste: montantFacture,
  tropPercu: 0, etat: 'NON_REGLEE', soldeType: 'RESTE', hasDraft: false, draftAmount: 0,
} as unknown as ReglementPreviewRow);

const requete = (page: number, size: number, extra: Partial<NgTableRemoteQuery> = {}): NgTableRemoteQuery => ({
  sort: {columnId: '', direction: ''} as NgTableRemoteQuery['sort'], filters: {}, page: {index: page, size}, ...extra,
});

describe('ReglementWorkspaceComponent', () => {
  const storeMock = {
    visibleRows: vi.fn((): ReglementPreviewRow[] => [ligne('a', 'Zid', 300), ligne('b', 'Amrani', 100)]),
    year: vi.fn(() => 2026),
    month: vi.fn(() => 8),
    caisseId: vi.fn(() => null),
    agenceId: vi.fn(() => null),
    centrePayeurId: vi.fn(() => null),
    rows: vi.fn(() => []),
    total: vi.fn(() => 0),
    pageIndex: vi.fn(() => 0),
    pageSize: vi.fn(() => 20),
    loading: vi.fn(() => false),
    referentialsLoading: vi.fn(() => false),
    error: vi.fn(() => null),
    successMessage: vi.fn(() => null),
    caisses: vi.fn(() => []),
    agences: vi.fn(() => []),
    centresPayeurs: vi.fn(() => []),
    savingFactureIds: vi.fn(() => []),
    modeReglement: vi.fn(() => null),
    referencePaiement: vi.fn(() => ''),
    setModeReglement: vi.fn(),
    setReferencePaiement: vi.fn(),
    setYear: vi.fn(),
    setMonth: vi.fn(),
    setCaisseId: vi.fn(),
    setAgenceId: vi.fn(),
    setCentrePayeurId: vi.fn(),
    setPagination: vi.fn(),
    colonnes: vi.fn((): Record<string, string> => ({})),
    setColonnes: vi.fn(),
    clearFilters: vi.fn(),
    clearMessages: vi.fn(),
    loadPage: vi.fn(),
    setPaymentDraft: vi.fn(),
    batchSavePayments: vi.fn(),
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      providers: [
        {provide: ReglementStore, useValue: storeMock},
        {provide: AppShellStore, useValue: {currentCenterId: vi.fn(() => '11111111-1111-1111-1111-111111111111')}},
        {provide: AuthStore, useValue: {username: vi.fn(() => 'sec-user')}},
        {
          provide: TranslateService,
          useValue: {
            onLangChange: new Subject(),
            onTranslationChange: new Subject(),
            instant: vi.fn((key: string, params?: Record<string, unknown>) => params?.['amount'] ? `${key}:${params['amount']}` : key),
          }
        },
        {provide: NgTableTranslatedLabels, useValue: {labels: signal({})}},
      ],
    });
  });

  const colonnes = (component: ReglementWorkspaceComponent): NgTableColumn<ReglementPreviewRow>[] =>
    (component as any).reglementColumns();

  it('définit les colonnes ng-table : états et soldes en pastilles teintées, actions hors export', () => {
    const component = TestBed.runInInjectionContext(() => new ReglementWorkspaceComponent());
    const cols = colonnes(component);

    expect(cols.map((c) => c.id)).toEqual(['numeroFacture', 'numeroAssurance', 'patientNom', 'patientPrenom', 'caisse',
      'agence', 'centrePayeur', 'montantFacture', 'montantRegle', 'solde', 'etat', 'codeReglement', 'actions']);
    expect(cols.find((c) => c.id === 'numeroFacture')!.header).toBe('REGLEMENT_MODULE.TABLE.NUMERO_FACTURE');
    const etat = cols.find((c) => c.id === 'etat')!;
    expect(etat.cell).toMatchObject({type: 'badge', tone: {NON_REGLEE: 'danger', PARTIELLEMENT_REGLEE: 'warning', REGLEE: 'success'}});
    const solde = cols.find((c) => c.id === 'solde')!;
    expect(solde.cell).toMatchObject({type: 'badge', tone: {REGLE: 'success', TROP_PERCU: 'warning', RESTE: 'danger'}});
    expect((solde.cell as any).label('RESTE', ligne('a', 'Zid', 300))).toBe('REGLEMENT_MODULE.TABLE.SOLDE_REMAINING:300');
    const actions = cols.find((c) => c.id === 'actions')!;
    expect(actions.exportable).toBe(false);
    expect(actions.mobileRowActions).toBe(true);
  });

  it('marque les lignes dont le solde et l\'état sont un aperçu de brouillon', () => {
    const component = TestBed.runInInjectionContext(() => new ReglementWorkspaceComponent());
    const rowClassFn = (component as any).rowClassFn as (row: ReglementPreviewRow) => Record<string, boolean>;
    expect(rowClassFn({...ligne('a', 'Zid', 300), hasDraft: true})).toEqual({'row-has-draft': true});
    expect(rowClassFn(ligne('b', 'Amrani', 100))).toEqual({'row-has-draft': false});
  });

  it('pagine côté serveur : un changement de page ou de taille passe par le store', () => {
    const component = TestBed.runInInjectionContext(() => new ReglementWorkspaceComponent());
    component['onRemoteQueryChange'](requete(0, 20));
    expect(storeMock.setPagination).not.toHaveBeenCalled();

    component['onRemoteQueryChange'](requete(2, 20));
    expect(storeMock.setPagination).toHaveBeenLastCalledWith(2, 20);

    component['onRemoteQueryChange'](requete(1, 50));
    expect(storeMock.setPagination).toHaveBeenLastCalledWith(0, 50);
  });

  it('envoie le tri et les filtres de colonne au serveur au format de l\'API (le solde devient soldeType)', () => {
    storeMock.pageIndex.mockReturnValue(2);
    const component = TestBed.runInInjectionContext(() => new ReglementWorkspaceComponent());

    component['onRemoteQueryChange'](requete(0, 20, {
      sort: {columnId: 'montantFacture', direction: 'desc'},
      filters: {patientNom: ' zi ', etat: 'NON_REGLEE,REGLEE', solde: 'TROP_PERCU', montantRegle: '>=100'},
    }));

    expect(storeMock.setColonnes).toHaveBeenCalledWith({
      sort: 'montantFacture,desc', patientNom: 'zi', etat: 'NON_REGLEE,REGLEE', soldeType: 'TROP_PERCU',
      montantRegle: '>=100',
    });
    // setColonnes ramène lui-même en page 0 : pas de setPagination sur la page 0 demandée par la table.
    expect(storeMock.setPagination).not.toHaveBeenCalled();
    storeMock.pageIndex.mockReturnValue(0);
  });

  it('n\'envoie pas de tri sur la colonne Solde (hors liste blanche) et ne réinitialise pas des colonnes identiques', () => {
    storeMock.colonnes.mockReturnValue({etat: 'REGLEE'});
    const component = TestBed.runInInjectionContext(() => new ReglementWorkspaceComponent());

    component['onRemoteQueryChange'](requete(1, 20, {
      sort: {columnId: 'solde', direction: 'asc'}, filters: {etat: 'REGLEE'},
    }));

    expect(storeMock.setColonnes).not.toHaveBeenCalled();
    expect(storeMock.setPagination).toHaveBeenCalledWith(1, 20);
    storeMock.colonnes.mockReturnValue({});
  });

  it('filtres énumérés sur l\'état et le solde (valeurs = noms d\'énumération), numériques sur les montants', () => {
    const component = TestBed.runInInjectionContext(() => new ReglementWorkspaceComponent());
    const cols = colonnes(component);
    const etat = cols.find((c) => c.id === 'etat')!;
    const solde = cols.find((c) => c.id === 'solde')!;

    expect(etat.filter?.type).toBe('enum');
    expect(etat.filter?.options?.map((o) => o.value)).toEqual(['NON_REGLEE', 'PARTIELLEMENT_REGLEE', 'REGLEE']);
    expect(solde.filter?.type).toBe('enum');
    expect(solde.filter?.options?.map((o) => o.value)).toEqual(['RESTE', 'REGLE', 'TROP_PERCU']);
    expect(solde.sortable).toBe(false);
    expect(cols.find((c) => c.id === 'montantFacture')!.filter?.type).toBe('number');
    expect(cols.find((c) => c.id === 'montantRegle')!.filter?.type).toBe('number');
  });

  it('affiche la page renvoyée par le serveur sans la retrier localement', () => {
    const component = TestBed.runInInjectionContext(() => new ReglementWorkspaceComponent());
    component['onRemoteQueryChange'](requete(0, 20, {sort: {columnId: 'montantFacture', direction: 'asc'}}));
    expect(((component as any).lignes() as ReglementPreviewRow[]).map((r) => r.factureId)).toEqual(['a', 'b']);
  });

  it('propage les filtres année/mois au store', () => {
    const component = TestBed.runInInjectionContext(() => new ReglementWorkspaceComponent());
    component['onYearChange']('2025');
    component['onMonthChange']('7');

    expect(storeMock.setYear).toHaveBeenCalledWith(2025);
    expect(storeMock.setMonth).toHaveBeenCalledWith(7);
  });

  it('enregistre les brouillons de paiement puis déclenche la sauvegarde batch avec centerId et userId', () => {
    const component = TestBed.runInInjectionContext(() => new ReglementWorkspaceComponent());
    component['onPaymentInput']('fac-1', '1000');
    component['onBatchSave']();

    expect(storeMock.setPaymentDraft).toHaveBeenCalledWith('fac-1', '1000');
    expect(storeMock.batchSavePayments).toHaveBeenCalledWith({
      centerId: '11111111-1111-1111-1111-111111111111',
    });
  });

  it('transmet le mode et la référence saisis au store', () => {
    const component = TestBed.runInInjectionContext(() => new ReglementWorkspaceComponent());
    component['onModeChange']('ESPECES');
    component['onModeChange']('');
    component['onReferenceInput']('REF-1');

    expect(storeMock.setModeReglement).toHaveBeenNthCalledWith(1, 'ESPECES');
    expect(storeMock.setModeReglement).toHaveBeenNthCalledWith(2, null);
    expect(storeMock.setReferencePaiement).toHaveBeenCalledWith('REF-1');
  });

  it('rafraîchit seulement la liste à régler : les indicateurs vivent dans le tableau de bord', () => {
    const component = TestBed.runInInjectionContext(() => new ReglementWorkspaceComponent());
    component['onRefresh']();

    expect(storeMock.loadPage).toHaveBeenCalledWith({
      centerId: '11111111-1111-1111-1111-111111111111', userId: 'sec-user', page: 0, size: 20,
    });
    expect('loadDashboard' in storeMock).toBe(false);
  });
});

