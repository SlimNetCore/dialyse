import {TestBed} from '@angular/core/testing';
import {NO_ERRORS_SCHEMA, provideZonelessChangeDetection, signal} from '@angular/core';
import {provideRouter} from '@angular/router';
import {TranslateModule} from '@ngx-translate/core';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {SeancesHistoriqueComponent} from './seances-historique.component';
import {SeanceHistoriqueStore} from './seance-historique.store';
import {SeancesStatsComponent} from './seances-stats.component';
import {ConfigurableListComponent} from '../../../shared/configurable-list.component';
import {AuthStore} from '../../../core/state/auth.store';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {WebSocketService} from '../../../core/ws/websocket.service';
import {MatDialog} from '@angular/material/dialog';
import {of} from 'rxjs';

const CENTER_ID = '11111111-1111-1111-1111-111111111111';

function fakeStore() {
  return {
    rows: signal<unknown[]>([]), total: signal(0), pageIndex: signal(0), pageSize: signal(20), loading: signal(false),
    error: signal<string | null>(null), periodFrom: signal(''), periodTo: signal(''),
    deleting: signal(false), successMessage: signal<string | null>(null),
    reload: vi.fn(), applyQuery: vi.fn(), setPage: vi.fn(), setPeriod: vi.fn(),
    supprimer: vi.fn().mockResolvedValue(true),
  };
}

describe('SeancesHistoriqueComponent', () => {
  let store: ReturnType<typeof fakeStore>;
  let roles: string[];
  let lastEvent: ReturnType<typeof signal<{ type: string; centerId: string } | null>>;
  let dialogOpen: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    store = fakeStore();
    roles = ['ADMIN'];
    lastEvent = signal(null);
    dialogOpen = vi.fn().mockReturnValue({afterClosed: () => of('Séance saisie en double')});
    TestBed.configureTestingModule({
      imports: [SeancesHistoriqueComponent, TranslateModule.forRoot()],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        {provide: SeanceHistoriqueStore, useValue: store},
        {provide: AuthStore, useValue: {hasRole: (r: string) => roles.includes(r)}},
        {provide: AppShellStore, useValue: {currentCenterId: signal(CENTER_ID)}},
        {provide: WebSocketService, useValue: {lastEvent}},
        {provide: MatDialog, useValue: {open: dialogOpen}},
      ],
    });
    TestBed.overrideComponent(SeancesHistoriqueComponent, {
      remove: {imports: [SeancesStatsComponent, ConfigurableListComponent]},
      add: {schemas: [NO_ERRORS_SCHEMA]},
    });
  });

  function render() {
    const fixture = TestBed.createComponent(SeancesHistoriqueComponent);
    fixture.detectChanges();
    return {fixture, cmp: fixture.componentInstance as any, root: fixture.nativeElement as HTMLElement};
  }

  it('charge l\'historique du centre actif à l\'ouverture', () => {
    render();
    expect(store.reload).toHaveBeenCalledWith(CENTER_ID);
  });

  it('transmet au serveur le tri et les filtres de la liste, sans filtrer localement', () => {
    const {cmp} = render();

    cmp.onRemoteQuery({
      sort: {columnId: 'patient', direction: 'asc'},
      filters: {patient: 'dup'},
      page: {index: 0, size: 20}
    });

    expect(store.applyQuery).toHaveBeenCalledWith(CENTER_ID,
      {filters: {patient: 'dup'}, sortColumnId: 'patient', sortDirection: 'asc'});
  });

  it('un tri vide revient au tri par défaut', () => {
    const {cmp} = render();
    cmp.onRemoteQuery({sort: {columnId: '', direction: ''}, filters: {}, page: {index: 0, size: 20}});
    expect(store.applyQuery).toHaveBeenCalledWith(CENTER_ID, {filters: {}, sortColumnId: null, sortDirection: ''});
  });

  it('la pagination demande la page au serveur', () => {
    const {cmp} = render();
    cmp.onPage({pageIndex: 2, pageSize: 50, length: 300});
    expect(store.setPage).toHaveBeenCalledWith(CENTER_ID, 2, 50);
  });

  it('la période combine les deux bornes et peut être effacée', () => {
    store.periodTo.set('2026-10-31');
    const {cmp} = render();

    cmp.onPeriodInput('from', {target: {value: '2026-10-01'}} as unknown as Event);
    expect(store.setPeriod).toHaveBeenCalledWith(CENTER_ID, '2026-10-01', '2026-10-31');

    cmp.clearPeriod();
    expect(store.setPeriod).toHaveBeenLastCalledWith(CENTER_ID, '', '');
  });

  it('configure un filtre statut à 4 valeurs, un filtre texte patient et seules les colonnes serveur sont triables', () => {
    const {cmp} = render();
    const columns = cmp.columns() as Array<{
      id: string;
      sortable?: boolean;
      filter?: { type?: string; options?: unknown[] }
    }>;

    expect(columns.find((c) => c.id === 'status')?.filter?.options).toHaveLength(4);
    expect(columns.find((c) => c.id === 'status')?.filter?.type).toBe('enum');
    expect(columns.find((c) => c.id === 'patient')?.filter?.type).toBe('text');
    expect(columns.filter((c) => c.sortable).map((c) => c.id)).toEqual(['dateSeance', 'patient', 'status']);
    expect(columns.find((c) => c.id === 'forfait')?.sortable).toBeFalsy();
  });

  it('affiche le lien vers le poste infirmier et le calendrier selon le rôle', () => {
    const admin = render();
    expect(admin.root.textContent).toContain('SEANCES.HISTORY_TO_STATION');
    expect(admin.root.textContent).toContain('SEANCES.CALENDAR_CENTER_BTN');

    TestBed.resetTestingModule();
  });

  it('une séance modifiée en temps réel recharge l\'historique du centre, pas celle d\'un autre', () => {
    const {fixture} = render();
    store.reload.mockClear();

    lastEvent.set({type: 'SEANCE_VALIDATED', centerId: 'autre-centre'});
    fixture.detectChanges();
    expect(store.reload).not.toHaveBeenCalled();

    lastEvent.set({type: 'SEANCE_VALIDATED', centerId: CENTER_ID});
    fixture.detectChanges();
    expect(store.reload).toHaveBeenCalledWith(CENTER_ID);

    store.reload.mockClear();
    lastEvent.set({type: 'STOCK_ALERT', centerId: CENTER_ID});
    fixture.detectChanges();
    expect(store.reload).not.toHaveBeenCalled();
  });

  it('affiche le message d\'erreur quand le chargement échoue', () => {
    store.error.set('SEANCES.HISTORY_LOAD_ERROR');
    const {root} = render();
    expect(root.querySelector('.list-error')?.textContent).toContain('SEANCES.HISTORY_LOAD_ERROR');
  });

  it('formate le nom du patient, le forfait et son prix', () => {
    const {cmp} = render();
    const row = {
      patientNom: 'Dupont', patientPrenom: 'Jean', patientCode: 'P1', patientId: 'x',
      forfait: {nom: 'Forfait HD', prix: 3500}
    };

    expect(cmp.patientLabel(row)).toBe('Dupont Jean');
    expect(cmp.patientLabel({patientId: 'x', patientCode: 'P1'})).toBe('P1');
    expect(cmp.forfaitName(row)).toBe('Forfait HD');
    expect(cmp.forfaitName({})).toBe('-');
    expect(cmp.forfaitPrice(row)).toMatch(/3\s?500,00/);
    expect(cmp.forfaitPrice({})).toBe('');
  });

  const ligne = (status: string) => ({id: 's9', patientId: 'p9', patientNom: 'Bensaid', patientPrenom: 'Karim',
    dateSeance: '2026-10-05', status});

  it('propose la suppression à l\'administrateur seulement, jamais pour une séance facturée', () => {
    const {cmp} = render();
    expect(cmp.columns().map((c: { id: string }) => c.id)).toContain('actions');
    expect(cmp.supprimable(ligne('SIGNEE'))).toBe(true);
    expect(cmp.supprimable(ligne('FACTUREE'))).toBe(false);

    roles = ['SECRETAIRE'];
    const autre = TestBed.createComponent(SeancesHistoriqueComponent);
    autre.detectChanges();
    expect((autre.componentInstance as any).columns().map((c: { id: string }) => c.id)).not.toContain('actions');
  });

  it('demande le motif puis supprime la séance du centre actif', () => {
    const {cmp} = render();

    cmp.supprimer(ligne('VALIDEE'));

    expect(dialogOpen).toHaveBeenCalled();
    expect(dialogOpen.mock.calls[0][1].data).toMatchObject({patient: 'Bensaid Karim', date: '2026-10-05'});
    expect(store.supprimer).toHaveBeenCalledWith(CENTER_ID, 's9', 'Séance saisie en double');
  });

  it('ne supprime rien si la boîte de dialogue est annulée ou pour une séance facturée', () => {
    dialogOpen.mockReturnValue({afterClosed: () => of(undefined)});
    const {cmp} = render();

    cmp.supprimer(ligne('VALIDEE'));
    cmp.supprimer(ligne('FACTUREE'));

    expect(store.supprimer).not.toHaveBeenCalled();
    expect(dialogOpen).toHaveBeenCalledTimes(1);
  });
});
