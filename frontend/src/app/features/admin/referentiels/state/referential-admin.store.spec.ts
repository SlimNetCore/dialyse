import {signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {of, throwError} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {
  ImportReport,
  ReferentialAdminApiService,
  ReferentialKindDef,
} from '../../../../core/api/referential-admin-api.service';
import {AppShellStore} from '../../../../core/state/app-shell.store';
import {ReferentialAdminStore} from './referential-admin.store';

const SALLES: ReferentialKindDef = {
  slug: 'salles', label: 'Salles', importOrder: 1, naturalKey: ['code'],
  fields: [
    {
      key: 'code', label: 'Code', type: 'TEXT', required: true, requiredColumn: true, maxLength: 50,
      allowedValues: [], defaultValue: null, reference: null, example: 'S1'
    },
    {
      key: 'nom', label: 'Nom', type: 'TEXT', required: true, requiredColumn: true, maxLength: 255,
      allowedValues: [], defaultValue: null, reference: null, example: 'Salle 1'
    },
  ],
};
const GENERATEURS: ReferentialKindDef = {
  slug: 'generateurs', label: 'Générateurs', importOrder: 2, naturalKey: ['numero'],
  fields: [
    {
      key: 'numero', label: 'Numéro', type: 'TEXT', required: true, requiredColumn: true, maxLength: 50,
      allowedValues: [], defaultValue: null, reference: null, example: 'G01'
    },
    {
      key: 'salle', label: 'Salle (code)', type: 'REFERENCE', required: true, requiredColumn: true, maxLength: 0,
      allowedValues: [], defaultValue: null, reference: 'salles', example: 'S1'
    },
  ],
};

const page = (items: unknown[], total = items.length) => ({items, total, page: 0, size: 20});

describe('ReferentialAdminStore', () => {
  let api: Record<'kinds' | 'list' | 'create' | 'update' | 'delete' | 'importFile' | 'template', ReturnType<typeof vi.fn>>;
  let centerId: ReturnType<typeof signal<string | null>>;
  let store: InstanceType<typeof ReferentialAdminStore>;

  beforeEach(() => {
    api = {
      kinds: vi.fn(),
      list: vi.fn(),
      create: vi.fn(),
      update: vi.fn(),
      delete: vi.fn(),
      importFile: vi.fn(),
      template: vi.fn()
    };
    centerId = signal<string | null>('centre-a');
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        {provide: ReferentialAdminApiService, useValue: api},
        {provide: AppShellStore, useValue: {currentCenterId: centerId}},
      ],
    });
    store = TestBed.inject(ReferentialAdminStore);
    api.kinds.mockReturnValue(of([SALLES, GENERATEURS]));
    api.list.mockReturnValue(of(page([])));
  });

  it('charge les référentiels et sélectionne le premier', async () => {
    await store.loadKinds();

    expect(store.kinds().map((k) => k.slug)).toEqual(['salles', 'generateurs']);
    expect(store.activeSlug()).toBe('salles');
  });

  it('liste la page du centre actif avec recherche et pagination', async () => {
    await store.loadKinds();
    api.list.mockReturnValue(of(page([{id: 's1', values: {code: 'S1', nom: 'Salle 1'}, references: {}}], 41)));

    await store.setSearch('sal');
    await store.setPagination(2, 20);

    expect(api.list).toHaveBeenLastCalledWith('salles', {centerId: 'centre-a', search: 'sal', page: 2, size: 20});
    expect(store.rows()).toHaveLength(1);
    expect(store.total()).toBe(41);
  });

  it('ne charge rien sans centre actif', async () => {
    await store.loadKinds();
    centerId.set(null);

    await store.load();

    expect(api.list).not.toHaveBeenCalled();
  });

  it('charge les options des champs de référence du référentiel choisi', async () => {
    await store.loadKinds();
    api.list.mockImplementation((slug: string) => of(slug === 'salles'
      ? page([{id: 's1', values: {code: 'S1', nom: 'Salle 1'}, references: {}}])
      : page([])));

    await store.selectKind('generateurs');

    expect(api.list).toHaveBeenCalledWith('salles', {centerId: 'centre-a', page: 0, size: 100});
    expect(store.referenceOptions()['salles']).toEqual([{value: 's1', label: 'S1 · Salle 1'}]);
  });

  it('enregistre puis recharge ; expose les anomalies champ par champ en cas de refus', async () => {
    await store.loadKinds();
    api.create.mockReturnValue(of({id: 'new', values: {}, references: {}}));

    expect(await store.save({code: 'S2', nom: 'Salle 2'})).toBe(true);
    expect(api.create).toHaveBeenCalledWith('salles', 'centre-a', {code: 'S2', nom: 'Salle 2'});

    api.update.mockReturnValue(throwError(() => ({
      error: {
        detail: 'Code déjà utilisé',
        issues: [{row: 0, field: 'code', code: 'ALREADY_EXISTS', message: 'm', params: {}}]
      },
    })));
    expect(await store.save({code: 'S1', nom: 'Salle 2'}, 's2')).toBe(false);
    expect(store.formIssues()[0].code).toBe('ALREADY_EXISTS');
    expect(store.error()).toBe('Code déjà utilisé');
  });

  it('signale une suppression refusée (élément encore utilisé)', async () => {
    await store.loadKinds();
    api.delete.mockReturnValue(throwError(() => ({error: {detail: 'Encore utilisé', code: 'REFERENTIAL_IN_USE'}})));

    expect(await store.remove('s1')).toBe(false);
    expect(store.error()).toBe('Encore utilisé');
  });

  it('vérifie un fichier sans recharger, puis recharge après un import appliqué', async () => {
    await store.loadKinds();
    const file = new File(['code;nom'], 'salles.csv');
    const checked: ImportReport = {
      kind: 'salles', totalRows: 2, created: 2, updated: 0, missingColumns: [],
      ignoredColumns: [], errors: [], valid: true, applied: false
    };
    api.importFile.mockReturnValueOnce(of(checked)).mockReturnValueOnce(of({...checked, applied: true}));
    api.list.mockClear();

    await store.importFile(file, true);
    expect(api.importFile).toHaveBeenLastCalledWith('salles', 'centre-a', file, true);
    expect(store.importReport()?.valid).toBe(true);
    expect(api.list).not.toHaveBeenCalled();

    await store.importFile(file, false);
    expect(store.importReport()?.applied).toBe(true);
    expect(api.list).toHaveBeenCalled();
  });

  it('expose l\'erreur d\'un fichier illisible', async () => {
    await store.loadKinds();
    api.importFile.mockReturnValue(throwError(() => ({error: {detail: 'Le classeur Excel est illisible'}})));

    expect(await store.importFile(new File(['x'], 'x.xlsx'), true)).toBeNull();
    expect(store.importError()).toBe('Le classeur Excel est illisible');
  });

  it('oublie les données du centre précédent au changement de centre', async () => {
    await store.loadKinds();
    api.list.mockReturnValue(of(page([{id: 's1', values: {code: 'S1'}, references: {}}])));
    await store.load();

    store.resetForCenterChange();

    expect(store.rows()).toEqual([]);
    expect(store.total()).toBe(0);
  });
});

