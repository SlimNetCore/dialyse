import {signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {provideRouter, Router} from '@angular/router';
import {vi} from 'vitest';
import {TranslateModule} from '@ngx-translate/core';
import {InventaireApiService, InventaireResume} from '../../../core/api/inventaire-api.service';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {AuthStore} from '../../../core/state/auth.store';
import {InventaireStore} from './state/inventaire.store';
import {InventairesComponent, INVENTORY_STATUS_TONES} from './inventaires.component';

const resume = (over: Partial<InventaireResume> = {}): InventaireResume => ({
  id: 'inv-1', reference: 'INV-2026-0001', dateInventaire: '2026-09-30', statut: 'CLOTURE', lignes: 4, comptees: 3,
  ecarts: 1, valeurEcarts: -4, createdBy: 'pharma', createdAt: '2026-09-30T08:00:00Z', closedBy: 'chef', closedAt: null,
  ...over,
});

describe('InventairesComponent', () => {
  const store = {
    error: signal<string | null>(null),
    etat: signal(null),
    busy: signal(false),
    loading: signal(false),
    history: signal<InventaireResume[]>([resume()]),
    total: signal(35),
    pageIndex: signal(0),
    pageSize: signal(10),
    init: vi.fn(async () => undefined),
    setPage: vi.fn(async () => undefined),
    ouvrir: vi.fn(),
  };

  function creer() {
    const fixture = TestBed.createComponent(InventairesComponent);
    fixture.detectChanges();
    return {fixture, c: fixture.componentInstance as any};
  }

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [InventairesComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        {provide: InventaireStore, useValue: store},
        {provide: InventaireApiService, useValue: {rapport: vi.fn()}},
        {provide: AppShellStore, useValue: {currentCenterId: () => 'center-1'}},
        {provide: AuthStore, useValue: {hasRole: () => true}},
      ],
    }).compileComponents();
  });

  it('affiche l historique dans une ng-table paginee cote serveur, sans mat-table ni mat-paginator', () => {
    const {fixture, c} = creer();
    const el: HTMLElement = fixture.nativeElement;

    expect(store.init).toHaveBeenCalled();
    expect(el.querySelector('[data-testid="inventory-history"] ng-table')).not.toBeNull();
    expect(el.querySelector('table[mat-table], mat-paginator')).toBeNull();
    expect(c.pageSizes).toEqual([10, 20, 50, 100]);
    expect(el.textContent).toContain('INV-2026-0001');
  });

  it('declare les colonnes de l historique : statut en pastille, avancement, impression hors export', () => {
    const {c} = creer();
    const colonnes = c.historyColumns();
    const parId = (id: string) => colonnes.find((col: { id: string }) => col.id === id);

    expect(colonnes.map((col: { id: string }) => col.id))
      .toEqual(['reference', 'date', 'statut', 'progression', 'ecarts', 'valeur', 'par', 'actions']);
    expect(parId('statut').cell).toEqual(expect.objectContaining({type: 'badge', tone: INVENTORY_STATUS_TONES}));
    expect(parId('date').valueAccessor(resume())).toBe('30/09/2026');
    expect(parId('progression').valueAccessor(resume())).toBe(75);
    expect(parId('progression').exportValueAccessor(resume())).toBe('3/4');
    expect(parId('par').valueAccessor(resume())).toBe('chef');
    expect(parId('actions').exportable).toBe(false);
  });

  it('demande au serveur la page choisie, sans relire la page affichee', () => {
    const {c} = creer();

    c.onRemoteQueryChange({sort: {columnId: '', direction: ''}, filters: {}, page: {index: 0, size: 10}});
    expect(store.setPage).not.toHaveBeenCalled();

    c.onRemoteQueryChange({sort: {columnId: '', direction: ''}, filters: {}, page: {index: 2, size: 10}});
    expect(store.setPage).toHaveBeenCalledWith(2, 10);
  });

  it('ouvre le detail au clic sur la ligne mais pas au clic sur l impression', () => {
    const {fixture, c} = creer();
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    const print = vi.spyOn(c, 'print').mockResolvedValue(undefined);

    (fixture.nativeElement.querySelector('[data-testid="inventory-print-inv-1"]') as HTMLButtonElement).click();
    expect(print).toHaveBeenCalled();
    expect(navigate).not.toHaveBeenCalled();

    c.openDetail(resume());
    expect(navigate).toHaveBeenCalledWith(['/stock/inventaires', 'inv-1']);
  });
});
