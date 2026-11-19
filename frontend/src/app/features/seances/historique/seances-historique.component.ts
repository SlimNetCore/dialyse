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
import {toSignal} from '@angular/core/rxjs-interop';
import {RouterLink} from '@angular/router';
import {MatDialog} from '@angular/material/dialog';
import {MatButtonModule} from '@angular/material/button';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatIconModule} from '@angular/material/icon';
import {MatInputModule} from '@angular/material/input';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {NgTableColumn, NgTableComponent, NgTableRemoteQuery} from '@sbourahla/ng-table';
import {map, merge} from 'rxjs';
import {AuthStore} from '../../../core/state/auth.store';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {SeanceListItem} from '../../../core/api/backend-api.service';
import {WebSocketService} from '../../../core/ws/websocket.service';
import {NgTableTranslatedLabels} from '../../../shared/ng-table-labels';
import {SeanceHistoriqueStore} from './seance-historique.store';
import {SEANCE_STATUSES} from './seance-historique.util';
import {SupprimerSeanceDialogComponent, SupprimerSeanceDialogData} from './supprimer-seance-dialog.component';

type SeanceCell = TemplateRef<{ $implicit: SeanceListItem; row: SeanceListItem; value: unknown; column: NgTableColumn<SeanceListItem> }>;

/**
 * Historique des séances : ng-table en mode distant dont la recherche, les filtres, le tri et la pagination sont
 * appliqués par le serveur (jamais sur la seule page affichée).
 * Écran de consultation : la saisie se fait au poste infirmier ; les statistiques du mois sont au tableau de bord
 * (/seances/tableau-de-bord).
 * Traçabilité : ce composant → SeanceHistoriqueStore → BackendApiService.listSeances → GET /api/v1/seances.
 */
@Component({
  selector: 'app-seances-historique',
  standalone: true,
  imports: [RouterLink, MatButtonModule, MatFormFieldModule, MatIconModule, MatInputModule,
    TranslateModule, NgTableComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './seances-historique.component.html',
  styleUrl: './seances-historique.component.css',
})
export class SeancesHistoriqueComponent implements OnInit {
  protected readonly store = inject(SeanceHistoriqueStore);
  protected readonly pageSizeOptions = [10, 20, 50, 100];
  protected readonly hasPeriod = computed(() => !!this.store.periodFrom() || !!this.store.periodTo());
  protected readonly patientCell = viewChild<SeanceCell>('patientCell');
  protected readonly statusCell = viewChild<SeanceCell>('statusCell');
  protected readonly forfaitCell = viewChild<SeanceCell>('forfaitCell');
  protected readonly actionsCell = viewChild<SeanceCell>('actionsCell');
  protected readonly ngTableLabels = inject(NgTableTranslatedLabels).labels;
  protected readonly rowKey = (row: SeanceListItem): unknown => row.id;
  private readonly appShell = inject(AppShellStore);
  private readonly auth = inject(AuthStore);
  protected readonly canOpenStation = computed(() => ['ADMIN', 'INFIRMIER', 'SECRETAIRE'].some((r) => this.auth.hasRole(r)));
  protected readonly canEditCalendar = computed(() => this.auth.hasRole('ADMIN'));
  private readonly ws = inject(WebSocketService);
  private readonly translate = inject(TranslateService);
  private readonly dialog = inject(MatDialog);
  /** Seul l'administrateur supprime une séance (et jamais une séance facturée). */
  protected readonly canDelete = computed(() => this.auth.hasRole('ADMIN'));
  private readonly lang = toSignal(
    merge(this.translate.onLangChange, this.translate.onTranslationChange).pipe(map(() => Date.now())),
    {initialValue: 0});
  protected readonly emptyLabel = computed(() => {
    this.lang();
    return this.translate.instant('SEANCES.NO_SESSIONS_FOUND');
  });
  /**
   * Colonnes : tri et filtres délégués au serveur (mode distant) — date (jour ou plage `du..au` → from/to), patient
   * (texte → q), statut (liste multiple → status en CSV) ; le forfait, calculé après coup, reste informatif.
   */
  protected readonly columns = computed<NgTableColumn<SeanceListItem>[]>(() => {
    this.lang();
    const t = (key: string): string => this.translate.instant(key);
    const statusLabel = (s: unknown): string => t(this.statusKey(String(s)));
    return [
      {
        id: 'dateSeance',
        header: t('COMMON.DATE'),
        valueAccessor: (row) => row.dateSeance,
        sortable: true,
        resizable: true,
        minWidthPx: 130,
        filter: {type: 'range', label: t('COMMON.DATE')},
      },
      {
        id: 'patient',
        header: t('CAHIER.PATIENT'),
        valueAccessor: (row) => this.patientLabel(row),
        sortable: true,
        resizable: true,
        minWidthPx: 220,
        filter: {type: 'text', label: t('SEANCES.HISTORY_PATIENT_FILTER')},
        cellTemplate: this.patientCell(),
      },
      {
        id: 'status',
        header: t('COMMON.STATUS_LABEL'),
        valueAccessor: (row) => row.status,
        exportValueAccessor: (row) => statusLabel(row.status),
        sortable: true,
        resizable: true,
        minWidthPx: 150,
        textOverflow: 'wrap',
        filter: {
          type: 'enum',
          label: t('COMMON.STATUS_LABEL'),
          options: SEANCE_STATUSES.map((s) => ({value: s, label: statusLabel(s)})),
        },
        cellTemplate: this.statusCell(),
      },
      {
        id: 'forfait',
        header: t('CAHIER.FORFAIT'),
        valueAccessor: (row) => this.forfaitName(row),
        resizable: true,
        minWidthPx: 180,
        cellTemplate: this.forfaitCell(),
      },
      ...(this.canDelete() ? [{
        id: 'actions',
        header: t('SEANCES.DELETE.COLUMN'),
        valueAccessor: () => '',
        minWidthPx: 90,
        mobileRowActions: true,
        exportable: false,
        cellTemplate: this.actionsCell(),
      } satisfies NgTableColumn<SeanceListItem>] : []),
    ];
  });

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

  /** Tri, filtres ou page changés dans la table : traduits en critères serveur par le store. */
  protected onRemoteQuery(query: NgTableRemoteQuery): void {
    const centerId = this.appShell.currentCenterId();
    if (!centerId) return;
    this.store.applyTableQuery(centerId, {
      filters: query.filters ?? {},
      sortColumnId: query.sort?.columnId || null,
      sortDirection: query.sort?.columnId ? (query.sort.direction ?? '') : '',
      pageIndex: query.page?.index ?? 0,
      pageSize: query.page?.size ?? this.store.pageSize(),
    });
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

  /** Une séance facturée ne se supprime pas (la facture la référence). */
  protected supprimable(row: SeanceListItem): boolean {
    return row.status !== 'FACTUREE';
  }

  /** Demande le motif puis supprime la séance (administrateur). */
  protected supprimer(row: SeanceListItem): void {
    const centerId = this.appShell.currentCenterId();
    if (!centerId || !this.canDelete() || !this.supprimable(row)) return;
    const data: SupprimerSeanceDialogData = {
      patient: this.patientLabel(row), date: row.dateSeance, statut: row.status,
    };
    this.dialog.open<SupprimerSeanceDialogComponent, SupprimerSeanceDialogData, string>(SupprimerSeanceDialogComponent,
      {data, width: 'min(96vw, 560px)'})
      .afterClosed().subscribe((motif) => {
        if (motif) void this.store.supprimer(centerId, row.id, motif);
      });
  }

  protected statusKey(status: string): string {
    return `SEANCES.STATION.STATUS_${status}`;
  }
}
