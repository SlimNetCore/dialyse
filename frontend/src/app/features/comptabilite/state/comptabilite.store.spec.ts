import {TestBed} from '@angular/core/testing';
import {provideZonelessChangeDetection} from '@angular/core';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {of} from 'rxjs';
import {ComptabiliteStore} from './comptabilite.store';
import {ComptabiliteApiService} from '../../../core/api/comptabilite-api.service';
import {AppShellStore} from '../../../core/state/app-shell.store';

const CENTER_ID = '11111111-1111-1111-1111-111111111111';

describe('ComptabiliteStore', () => {
  let apiMock: {
    searchEcritures: ReturnType<typeof vi.fn>;
    getMapping: ReturnType<typeof vi.fn>;
    getRegles: ReturnType<typeof vi.fn>;
    exporterJournal: ReturnType<typeof vi.fn>;
    cloturerPeriode: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    apiMock = {
      searchEcritures: vi.fn().mockReturnValue(of({
        items: [
          {
            id: 'e-1',
            centerId: CENTER_ID,
            journalCode: 'VE',
            dateEcriture: '2026-08-10',
            datePiece: '2026-08-10',
            numeroPiece: 'VE-001',
            libelle: 'Facture 1',
            statut: 'VALIDEE',
            totalDebit: 1000,
            lignes: [],
          },
          {
            id: 'e-2',
            centerId: CENTER_ID,
            journalCode: 'BQ',
            dateEcriture: '2026-08-11',
            datePiece: '2026-08-11',
            numeroPiece: 'BQ-001',
            libelle: 'Règlement 1',
            statut: 'VALIDEE',
            totalDebit: 500,
            lignes: [],
          }
        ],
        total: 2,
        page: 0,
        size: 20,
      })),
      getMapping: vi.fn().mockReturnValue(of(null)),
      getRegles: vi.fn().mockReturnValue(of([])),
      exporterJournal: vi.fn(),
      cloturerPeriode: vi.fn(),
    };

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        ComptabiliteStore,
        {provide: ComptabiliteApiService, useValue: apiMock},
        {provide: AppShellStore, useValue: {currentCenterId: vi.fn(() => CENTER_ID)}},
      ]
    });
  });

  it('autorise plusieurs détails ouverts simultanément', () => {
    const store = TestBed.inject(ComptabiliteStore);

    store.toggleExpandedRow('e-1');
    store.toggleExpandedRow('e-2');

    expect(store.expandedEcritureIds()).toEqual(['e-1', 'e-2']);
  });

  it('referme uniquement la ligne ciblée', () => {
    const store = TestBed.inject(ComptabiliteStore);

    store.toggleExpandedRow('e-1');
    store.toggleExpandedRow('e-2');
    store.toggleExpandedRow('e-1');

    expect(store.expandedEcritureIds()).toEqual(['e-2']);
  });

  it('collapseAllExpandedRows ferme tous les détails', () => {
    const store = TestBed.inject(ComptabiliteStore);

    store.toggleExpandedRow('e-1');
    store.toggleExpandedRow('e-2');
    store.collapseAllExpandedRows();

    expect(store.expandedEcritureIds()).toEqual([]);
  });

  it('supprime les détails ouverts qui ne sont plus présents après rechargement', () => {
    const store = TestBed.inject(ComptabiliteStore);

    store.toggleExpandedRow('e-1');
    store.toggleExpandedRow('ghost');
    store.loadEcritures({centerId: CENTER_ID});

    expect(store.expandedEcritureIds()).toEqual(['e-1']);
  });
});

