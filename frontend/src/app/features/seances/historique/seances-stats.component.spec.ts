import {TestBed} from '@angular/core/testing';
import {NO_ERRORS_SCHEMA, provideZonelessChangeDetection, signal} from '@angular/core';
import {MatSnackBar} from '@angular/material/snack-bar';
import {TranslateModule} from '@ngx-translate/core';
import {of} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {BaseChartDirective} from 'ng2-charts';
import {SeancesStatsComponent} from './seances-stats.component';
import {SeanceStore} from '../state/seance.store';
import {BackendApiService} from '../../../core/api/backend-api.service';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {WebSocketService} from '../../../core/ws/websocket.service';

const CENTER_ID = '11111111-1111-1111-1111-111111111111';

describe('SeancesStatsComponent', () => {
  const snackBar = {open: vi.fn()};
  let store: Record<string, unknown>;
  let api: { exportSeanceDashboard: ReturnType<typeof vi.fn> };
  let lastEvent: ReturnType<typeof signal<{ type: string; centerId: string } | null>>;

  beforeEach(() => {
    snackBar.open.mockClear();
    lastEvent = signal(null);
    store = {
      dashboardMonth: signal('2026-10'), dashboardLoading: signal(false), seanceDashboard: signal(null),
      dashboardDetailsOpen: signal(false), dashboardDetailsKind: signal<'presence' | 'absence'>('presence'),
      dashboardDetailItems: signal([]), dashboardDetailPageItems: signal([]), dashboardDetailPageIndex: signal(0),
      dashboardDetailTotalPages: signal(1),
      setDashboardMonth: vi.fn(), loadDashboard: vi.fn(), openDashboardDetails: vi.fn(), loadDashboardDetails: vi.fn(),
      closeDashboardDetails: vi.fn(), setDashboardDetailPage: vi.fn(),
    };
    api = {exportSeanceDashboard: vi.fn().mockReturnValue(of(new Blob(['x'])))};
    TestBed.configureTestingModule({
      imports: [SeancesStatsComponent, TranslateModule.forRoot()],
      providers: [
        provideZonelessChangeDetection(),
        {provide: SeanceStore, useValue: store},
        {provide: BackendApiService, useValue: api},
        {provide: AppShellStore, useValue: {currentCenterId: signal(CENTER_ID)}},
        {provide: WebSocketService, useValue: {lastEvent}},
        {provide: MatSnackBar, useValue: snackBar},
      ],
    });
    TestBed.overrideComponent(SeancesStatsComponent, {
      remove: {imports: [BaseChartDirective]},
      add: {schemas: [NO_ERRORS_SCHEMA]}
    });
  });

  function render() {
    const fixture = TestBed.createComponent(SeancesStatsComponent);
    fixture.detectChanges();
    return {fixture, cmp: fixture.componentInstance as any};
  }

  it('charge les statistiques du mois affiché à l\'ouverture', () => {
    render();
    expect(store['loadDashboard']).toHaveBeenCalledWith({centerId: CENTER_ID, year: 2026, month: 10});
  });

  it('un mois saisi est mémorisé puis rechargé', () => {
    const {cmp} = render();
    cmp.onMonthInput({target: {value: '2026-09'}});
    expect(store['setDashboardMonth']).toHaveBeenCalledWith('2026-09');
    expect(store['loadDashboard']).toHaveBeenCalledTimes(2);
  });

  it('prévient quand le mois est invalide au lieu d\'interroger le serveur', () => {
    (store['dashboardMonth'] as ReturnType<typeof signal<string>>).set('n’importe quoi');
    const {cmp} = render();
    (store['loadDashboard'] as ReturnType<typeof vi.fn>).mockClear();

    cmp.refresh(true);

    expect(snackBar.open).toHaveBeenCalledWith('COMMON.INVALID_MONTH', expect.anything(), expect.anything());
    expect(store['loadDashboard']).not.toHaveBeenCalled();
  });

  it('exporte le mois dans le format demandé', () => {
    const {cmp} = render();
    vi.stubGlobal('URL', {createObjectURL: vi.fn(() => 'blob:x'), revokeObjectURL: vi.fn()});
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);

    cmp.exportDashboard('xlsx');

    expect(api.exportSeanceDashboard).toHaveBeenCalledWith(CENTER_ID, 2026, 10, 'xlsx');
    vi.unstubAllGlobals();
  });

  it('ouvre le détail des absences du mois et le referme', () => {
    const {cmp} = render();

    cmp.openDetails('absence');
    cmp.closeDetails();

    expect(store['openDashboardDetails']).toHaveBeenCalledWith('absence');
    expect(store['loadDashboardDetails']).toHaveBeenCalledWith({
      centerId: CENTER_ID,
      year: 2026,
      month: 10,
      kind: 'absence'
    });
    expect(store['closeDashboardDetails']).toHaveBeenCalled();
  });

  it('choisit la clé de traduction du statut selon qu\'on liste des absences ou des présences', () => {
    const {cmp} = render();
    expect(cmp.detailStatusKey('VALIDEE')).toBe('SEANCES.SEANCE_STATUTS.VALIDEE');
    (store['dashboardDetailsKind'] as ReturnType<typeof signal<string>>).set('absence');
    expect(cmp.detailStatusKey('A_QUALIFIER')).toBe('ABSENCES.STATUTS.A_QUALIFIER');
  });

  it('une séance modifiée en temps réel rafraîchit les statistiques du centre', () => {
    const {fixture} = render();
    (store['loadDashboard'] as ReturnType<typeof vi.fn>).mockClear();

    lastEvent.set({type: 'SEANCE_VALIDATED', centerId: 'autre'});
    fixture.detectChanges();
    expect(store['loadDashboard']).not.toHaveBeenCalled();

    lastEvent.set({type: 'SEANCE_VALIDATED', centerId: CENTER_ID});
    fixture.detectChanges();
    expect(store['loadDashboard']).toHaveBeenCalledTimes(1);
  });

  it('pagine le détail et attribue une couleur par jour de la semaine', () => {
    (store['dashboardDetailTotalPages'] as ReturnType<typeof signal<number>>).set(3);
    const {cmp} = render();

    expect(cmp.canPreviousPage()).toBe(false);
    expect(cmp.canNextPage()).toBe(true);
    cmp.nextPage();
    expect(store['setDashboardDetailPage']).toHaveBeenCalledWith(1);
    expect(cmp.weekdayClass({weekday: 'monday'})).toBe('weekday-mon');
    expect(cmp.weekdayClass({weekday: 'x'})).toBe('');
  });
});
