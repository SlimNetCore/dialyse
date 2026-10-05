import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  OnInit,
  TemplateRef,
  untracked,
  viewChild
} from '@angular/core';
import {RouterLink} from '@angular/router';
import {MatButtonModule} from '@angular/material/button';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatIconModule} from '@angular/material/icon';
import {MatInputModule} from '@angular/material/input';
import {MatPaginatorModule, PageEvent} from '@angular/material/paginator';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {AuthStore} from '../../../core/state/auth.store';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {SeanceListItem} from '../../../core/api/backend-api.service';
import {WebSocketService} from '../../../core/ws/websocket.service';
import {
  ConfigurableListComponent,
  SharedListColumn,
  SharedListRemoteQuery,
} from '../../../shared/configurable-list.component';
import {SeanceHistoriqueStore} from './seance-historique.store';
import {SEANCE_STATUSES} from './seance-historique.util';
import {SeancesStatsComponent} from './seances-stats.component';

/**
 * Historique des séances : statistiques du mois et tableau paginé dont la recherche, les filtres et le tri sont
 * appliqués par le serveur. Écran de consultation : la saisie se fait au poste infirmier.
 * Traçabilité : ce composant → SeanceHistoriqueStore → BackendApiService.listSeances → GET /api/v1/seances.
 */
@Component({
  selector: 'app-seances-historique',
  standalone: true,
  imports: [RouterLink, MatButtonModule, MatFormFieldModule, MatIconModule, MatInputModule, MatPaginatorModule,
    TranslateModule, ConfigurableListComponent, SeancesStatsComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './seances-historique.component.html',
  styleUrl: './seances-historique.component.css',
})
export class SeancesHistoriqueComponent implements OnInit {
  protected readonly store = inject(SeanceHistoriqueStore);
  protected readonly pageSizeOptions = [10, 20, 50, 100];
  protected readonly hasPeriod = computed(() => !!this.store.periodFrom() || !!this.store.periodTo());
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  protected readonly patientCell = viewChild<TemplateRef<any>>('patientCell');
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  protected readonly statusCell = viewChild<TemplateRef<any>>('statusCell');
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  protected readonly forfaitCell = viewChild<TemplateRef<any>>('forfaitCell');
  private readonly appShell = inject(AppShellStore);
  private readonly auth = inject(AuthStore);
  protected readonly canOpenStation = computed(() => ['ADMIN', 'INFIRMIER', 'SECRETAIRE'].some((r) => this.auth.hasRole(r)));
  protected readonly canEditCalendar = computed(() => this.auth.hasRole('ADMIN'));
  private readonly ws = inject(WebSocketService);
  private readonly translate = inject(TranslateService);
  /** Colonnes : tri et filtres délégués au serveur (mode distant) ; le forfait, calculé après coup, reste informatif. */
  protected readonly columns = computed<SharedListColumn<SeanceListItem>[]>(() => [
    {
      id: 'dateSeance',
      headerKey: 'COMMON.DATE',
      valueAccessor: (row) => row.dateSeance,
      sortable: true,
      resizable: true,
      minWidthPx: 130,
    },
    {
      id: 'patient',
      headerKey: 'CAHIER.PATIENT',
      valueAccessor: (row) => this.patientLabel(row),
      sortable: true,
      resizable: true,
      minWidthPx: 220,
      filter: {type: 'text', labelKey: 'SEANCES.HISTORY_PATIENT_FILTER'},
      cellTemplate: this.patientCell() ?? undefined,
    },
    {
      id: 'status',
      headerKey: 'COMMON.STATUS_LABEL',
      valueAccessor: (row) => row.status,
      sortable: true,
      resizable: true,
      minWidthPx: 150,
      filter: {
        type: 'enum',
        labelKey: 'COMMON.STATUS_LABEL',
        options: SEANCE_STATUSES.map((s) => ({value: s, label: this.translate.instant(`SEANCES.STATION.STATUS_${s}`)})),
      },
      cellTemplate: this.statusCell() ?? undefined,
    },
    {
      id: 'forfait',
      headerKey: 'CAHIER.FORFAIT',
      valueAccessor: (row) => this.forfaitName(row),
      resizable: true,
      minWidthPx: 180,
      cellTemplate: this.forfaitCell() ?? undefined,
    },
  ]);

  constructor() {
    effect(() => {
      const event = this.ws.lastEvent();
      const centerId = this.appShell.currentCenterId();
      if (!event || !centerId || event.centerId !== centerId) return;
      if (!(event.type.startsWith('SEANCE_') || event.type === 'PATIENT_SCANNED')) return;
      untracked(() => this.store.reload(centerId));
    });
  }

  ngOnInit(): void {
    const centerId = this.appShell.currentCenterId();
    if (centerId) this.store.reload(centerId);
  }

  protected onRemoteQuery(query: SharedListRemoteQuery): void {
    const centerId = this.appShell.currentCenterId();
    if (!centerId) return;
    this.store.applyQuery(centerId, {
      filters: query.filters,
      sortColumnId: query.sort.columnId || null,
      sortDirection: query.sort.direction,
    });
  }

  protected onPage(event: PageEvent): void {
    const centerId = this.appShell.currentCenterId();
    if (centerId) this.store.setPage(centerId, event.pageIndex, event.pageSize);
  }

  protected onPeriodInput(which: 'from' | 'to', event: Event): void {
    const centerId = this.appShell.currentCenterId();
    if (!centerId) return;
    const value = (event.target as HTMLInputElement).value ?? '';
    this.store.setPeriod(centerId, which === 'from' ? value : this.store.periodFrom(), which === 'to' ? value : this.store.periodTo());
  }

  protected clearPeriod(): void {
    const centerId = this.appShell.currentCenterId();
    if (centerId) this.store.setPeriod(centerId, '', '');
  }

  protected patientLabel(row: SeanceListItem): string {
    return (`${(row.patientNom ?? '').trim()} ${(row.patientPrenom ?? '').trim()}`).trim() || row.patientCode || row.patientId;
  }

  protected forfaitName(row: SeanceListItem): string {
    return row.forfait?.nom?.trim() || row.forfait?.code?.trim() || '-';
  }

  protected forfaitPrice(row: SeanceListItem): string {
    const prix = row.forfait?.prix;
    if (prix == null || Number.isNaN(Number(prix))) return '';
    return new Intl.NumberFormat(this.translate.currentLang || 'fr', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    })
      .format(Number(prix));
  }

  protected statusKey(status: string): string {
    return `SEANCES.STATION.STATUS_${status}`;
  }
}
