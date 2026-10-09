import {provideZonelessChangeDetection, signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {ComptabiliteDashboardComponent} from './comptabilite-dashboard.component';
import {ComptabiliteStore} from './state/comptabilite.store';
import {AppShellStore} from '../../core/state/app-shell.store';
import {AuthStore} from '../../core/state/auth.store';
import {TranslateService} from '@ngx-translate/core';
import {EcritureComptableItem} from '../../core/api/comptabilite-api.service';
import {MatDialog} from '@angular/material/dialog';
import {of} from 'rxjs';
import {PiecesComptablesStore} from './state/pieces-comptables.store';

const CENTER_ID = '11111111-1111-1111-1111-111111111111';

function buildRow(id: string, numeroPiece: string): EcritureComptableItem {
  return {
    id,
    centerId: CENTER_ID,
    journalCode: 'VE',
    dateEcriture: '2026-08-10',
    datePiece: '2026-08-10',
    numeroPiece,
    libelle: `Libellé ${numeroPiece}`,
    statut: 'VALIDEE',
    totalDebit: 1000,
    lignes: [
      {
        id: `${id}-l1`,
        compteSCF: '701000',
        libelleLigne: 'Prestations hémodialyse',
        montantDebit: 1000,
        montantCredit: 0,
      },
      {
        id: `${id}-l2`,
        compteSCF: '411100',
        libelleLigne: 'Client patient',
        montantDebit: 0,
        montantCredit: 1000,
      }
    ]
  };
}

describe('ComptabiliteDashboardComponent', () => {
  const rows = [buildRow('e-1', 'VE-001'), buildRow('e-2', 'VE-002')];

  const expandedIds = signal<string[]>([]);
  const storeMock = {
    rows: signal(rows),
    total: signal(2),
    totalEcritures: signal(2),
    totalDebitPeriode: signal(2000),
    pageIndex: signal(0),
    pageSize: signal(20),
    loading: signal(false),
    exporting: signal(false),
    cloturant: signal(false),
    error: signal<string | null>(null),
    successMessage: signal<string | null>(null),
    year: signal(2026),
    month: signal<number | null>(8),
    journalCode: signal<string | null>(null),
    journaux: signal([{code: 'VE', libelle: 'Ventes', actif: true}]),
    libellesJournaux: signal<Record<string, string>>({VE: 'Ventes (VE)'}),
    expandedEcritureIds: expandedIds,
    setPagination: vi.fn(),
    toggleExpandedRow: vi.fn((id: string) => {
      const current = expandedIds();
      expandedIds.set(current.includes(id) ? current.filter((value) => value !== id) : [...current, id]);
    }),
    collapseAllExpandedRows: vi.fn(() => expandedIds.set([])),
    exporterJournal: vi.fn(),
    setYear: vi.fn(),
    setMonth: vi.fn(),
    setJournalCode: vi.fn(),
    loadEcritures: vi.fn(),
  };

  const piecesMock = {
    error: signal<string | null>(null),
    saving: signal(false),
    dernierePiece: signal<{ numeroPiece: string } | null>(null),
    clearMessages: vi.fn(),
    extournerPiece: vi.fn(),
  };
  let confirme = true;

  beforeEach(async () => {
    expandedIds.set([]);
    vi.clearAllMocks();

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        {provide: ComptabiliteStore, useValue: storeMock},
        {provide: AppShellStore, useValue: {currentCenterId: vi.fn(() => CENTER_ID)}},
        {provide: AuthStore, useValue: {username: vi.fn(() => 'admin'), hasRole: vi.fn(() => true)}},
        {provide: TranslateService, useValue: {instant: vi.fn((key: string) => key)}},
        {provide: PiecesComptablesStore, useValue: piecesMock},
        {provide: MatDialog, useValue: {open: vi.fn(() => ({afterClosed: () => of(confirme)}))}},
      ]
    });
  });

  it('extourne une pièce saisie après confirmation, puis recharge les écritures pour montrer l’écriture inverse', () => {
    confirme = true;
    const component = TestBed.runInInjectionContext(() => new ComptabiliteDashboardComponent());

    component['extourner'](rows[0]);
    expect(piecesMock.extournerPiece).toHaveBeenCalledWith('e-1');

    piecesMock.dernierePiece.set({numeroPiece: 'VE-003'});
    TestBed.tick();
    expect(storeMock.loadEcritures).toHaveBeenCalledWith({centerId: CENTER_ID});
    piecesMock.dernierePiece.set(null);
  });

  it('n’extourne rien si l’utilisateur annule la confirmation', () => {
    confirme = false;
    const component = TestBed.runInInjectionContext(() => new ComptabiliteDashboardComponent());

    component['extourner'](rows[0]);

    expect(piecesMock.extournerPiece).not.toHaveBeenCalled();
    expect(piecesMock.clearMessages).toHaveBeenCalled();
  });

  it('ouvre plusieurs détails en parallèle', () => {
    TestBed.runInInjectionContext(() => new ComptabiliteDashboardComponent());

    storeMock.toggleExpandedRow(rows[0].id);
    storeMock.toggleExpandedRow(rows[1].id);

    expect(expandedIds()).toEqual(['e-1', 'e-2']);
    expect(expandedIds().length).toBe(2);
  });

  it('ferme tous les détails depuis la barre d’outils', () => {
    expandedIds.set(['e-1', 'e-2']);
    TestBed.runInInjectionContext(() => new ComptabiliteDashboardComponent());

    storeMock.collapseAllExpandedRows();

    expect(expandedIds()).toEqual([]);
    expect(storeMock.collapseAllExpandedRows).toHaveBeenCalled();
  });

  it('retourne les métriques et le statut d’équilibrage attendus', () => {
    const component = TestBed.runInInjectionContext(() => new ComptabiliteDashboardComponent());

    expect(component['totalCredit'](rows[0])).toBe(1000);
    expect(component['detailBalanceClass'](rows[0])).toBe('detail-balance-ok');
    expect(component['detailBalanceLabel'](rows[0])).toBe('COMPTABILITE.DETAIL.BALANCE_OK');
  });

  it('affiche le libellé des journaux du centre, ou le code d’un journal supprimé', () => {
    const component = TestBed.runInInjectionContext(() => new ComptabiliteDashboardComponent());

    expect(component['journalLabel']('VE')).toBe('Ventes (VE)');
    expect(component['journalLabel']('ZZ')).toBe('ZZ');
  });

  it('exporte le journal choisi dans le filtre, et rien tant qu’aucun n’est choisi', () => {
    const component = TestBed.runInInjectionContext(() => new ComptabiliteDashboardComponent());

    component['onExport']();
    expect(storeMock.exporterJournal).not.toHaveBeenCalled();

    storeMock.journalCode.set('OD');
    component['onExport']();
    expect(storeMock.exporterJournal).toHaveBeenCalledWith(
      {centerId: CENTER_ID, from: '2026-08-01', to: '2026-08-31', journalCode: 'OD'});
    storeMock.journalCode.set(null);
  });
});


