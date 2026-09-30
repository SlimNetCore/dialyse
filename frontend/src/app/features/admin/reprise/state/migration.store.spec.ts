import {signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {of, throwError} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {MigrationApiService, MigrationBatch, MigrationRun} from '../../../../core/api/migration-api.service';
import {AppShellStore} from '../../../../core/state/app-shell.store';
import {MigrationStore} from './migration.store';

const OPEN_BATCH: MigrationBatch = {
  id: 'b1', libelle: 'Reprise', sourceSystem: 'Ancien', dateDebutReprise: '2023-09-30', status: 'EN_COURS',
  createdBy: 'admin', createdAt: '2026-09-30T10:00:00Z', closedAt: null,
};

function run(partial: Partial<MigrationRun>): MigrationRun {
  return {
    entity: 'patients', fileName: 'patients.csv', totalRows: 2, created: 2, updated: 0, missingColumns: [],
    ignoredColumns: [], errors: [], warnings: [], valid: true, dryRun: true, applied: false, executedBy: 'admin',
    executedAt: '2026-09-30T10:00:00Z', ...partial,
  };
}

const page = <T>(items: T[]) => ({items, total: items.length, page: 0, size: 10});

describe('MigrationStore', () => {
  let api: Record<keyof MigrationApiService, ReturnType<typeof vi.fn>>;
  let centerId: ReturnType<typeof signal<string | null>>;
  let store: InstanceType<typeof MigrationStore>;

  beforeEach(() => {
    api = {
      entities: vi.fn(), template: vi.fn(), batches: vi.fn(), open: vi.fn(), detail: vi.fn(), importFile: vi.fn(),
      close: vi.fn(), cancel: vi.fn(), valueMappings: vi.fn(), saveValueMapping: vi.fn(), deleteValueMapping: vi.fn(),
    } as unknown as Record<keyof MigrationApiService, ReturnType<typeof vi.fn>>;
    centerId = signal<string | null>('centre-a');
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        {provide: MigrationApiService, useValue: api},
        {provide: AppShellStore, useValue: {currentCenterId: centerId}},
      ],
    });
    store = TestBed.inject(MigrationStore);
    api.entities.mockReturnValue(of([{slug: 'assures', label: 'Assurés', order: 1, columns: []}]));
    api.valueMappings.mockReturnValue(of([]));
  });

  it('charge le lot en cours du centre et ses derniers comptes rendus', async () => {
    api.batches.mockReturnValue(of(page([OPEN_BATCH])));
    api.detail.mockReturnValue(of({batch: OPEN_BATCH, runs: [run({applied: true, dryRun: false})]}));

    await store.init();

    expect(api.batches).toHaveBeenCalledWith('centre-a', 0, 10);
    expect(store.batch()?.id).toBe('b1');
    expect(store.batchOpen()).toBe(true);
    expect(store.runs()['patients'].applied).toBe(true);
    expect(store.importedCount()).toBe(1);
  });

  it('sans lot en cours, affiche le formulaire d\'ouverture puis ouvre un lot', async () => {
    api.batches.mockReturnValue(of(page([])));
    await store.init();
    expect(store.batch()).toBeNull();

    api.open.mockReturnValue(of(OPEN_BATCH));
    api.batches.mockReturnValue(of(page([OPEN_BATCH])));
    const payload = {libelle: 'Reprise', sourceSystem: 'Ancien', dateDebutReprise: '2023-09-30'};

    expect(await store.open(payload)).toBe(true);
    expect(api.open).toHaveBeenCalledWith('centre-a', payload);
    expect(store.batch()?.status).toBe('EN_COURS');
    expect(store.history()).toHaveLength(1);
  });

  it('expose le refus d\'ouvrir un second lot', async () => {
    api.open.mockReturnValue(throwError(() => ({error: {detail: 'Un lot de reprise est déjà en cours'}})));

    expect(await store.open({libelle: 'X', sourceSystem: null, dateDebutReprise: null})).toBe(false);
    expect(store.error()).toBe('Un lot de reprise est déjà en cours');
  });

  it('vérifie puis importe un fichier et garde le compte rendu par donnée', async () => {
    api.batches.mockReturnValue(of(page([OPEN_BATCH])));
    api.detail.mockReturnValue(of({batch: OPEN_BATCH, runs: []}));
    await store.init();
    const file = new File(['x'], 'patients.csv');
    api.importFile.mockReturnValueOnce(of(run({}))).mockReturnValueOnce(of(run({applied: true, dryRun: false})));

    await store.importFile('patients', file, true);
    expect(api.importFile).toHaveBeenLastCalledWith('centre-a', 'b1', 'patients', file, true);
    expect(store.runs()['patients'].valid).toBe(true);
    expect(store.busyEntity()).toBeNull();

    await store.importFile('patients', file, false);
    expect(store.runs()['patients'].applied).toBe(true);
  });

  it('enregistre une correspondance de valeurs puis recharge la liste', async () => {
    api.saveValueMapping.mockReturnValue(of(undefined));
    api.valueMappings.mockReturnValue(of([{column: 'etatPatient', source: 'D', target: 'DECEDE'}]));

    expect(await store.saveValueMapping({column: 'etatPatient', source: 'D', target: 'DECEDE'})).toBe(true);
    expect(store.valueMappings()).toEqual([{column: 'etatPatient', source: 'D', target: 'DECEDE'}]);
  });

  it('annule le lot ; expose un refus (données déjà utilisées)', async () => {
    api.batches.mockReturnValue(of(page([OPEN_BATCH])));
    api.detail.mockReturnValue(of({batch: OPEN_BATCH, runs: []}));
    await store.init();

    api.cancel.mockReturnValueOnce(throwError(() => ({error: {detail: 'Annulation impossible'}})));
    expect(await store.cancel()).toBe(false);
    expect(store.error()).toBe('Annulation impossible');

    api.cancel.mockReturnValueOnce(of({...OPEN_BATCH, status: 'ANNULE'}));
    expect(await store.cancel()).toBe(true);
    expect(store.batchOpen()).toBe(false);
  });

  it('ne fait rien sans centre actif', async () => {
    centerId.set(null);

    await store.init();

    expect(api.batches).not.toHaveBeenCalled();
  });
});

