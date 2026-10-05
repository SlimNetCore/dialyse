import {ChangeDetectionStrategy, Component, computed, effect, inject, OnInit, untracked} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {MatCardModule} from '@angular/material/card';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatIconModule} from '@angular/material/icon';
import {MatInputModule} from '@angular/material/input';
import {MatSnackBar} from '@angular/material/snack-bar';
import {MatTableModule} from '@angular/material/table';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {BaseChartDirective} from 'ng2-charts';
import {Chart, ChartData, ChartOptions, registerables} from 'chart.js';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {BackendApiService, SeanceDashboardDetailItem} from '../../../core/api/backend-api.service';
import {WebSocketService} from '../../../core/ws/websocket.service';
import {SeanceStore} from '../state/seance.store';
import {currentMonthIso, parseMonth} from './seance-historique.util';

Chart.register(...registerables);

/**
 * Statistiques mensuelles des séances : indicateurs (prévues, présences, absences, total), répartitions par sexe et par
 * âge, détail des présences/absences et export. Se rafraîchit seule à chaque évènement de séance du centre.
 * Traçabilité : ce composant → SeanceStore (dashboard) → BackendApiService → /api/v1/seances/dashboard.
 */
@Component({
  selector: 'app-seances-stats',
  standalone: true,
  imports: [MatCardModule, MatIconModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatTableModule,
    TranslateModule, BaseChartDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './seances-stats.component.html',
  styleUrl: './seances-stats.component.css',
})
export class SeancesStatsComponent implements OnInit {
  protected readonly store = inject(SeanceStore);
  protected readonly dashboard = computed(() => this.store.seanceDashboard());
  protected readonly detailCols = ['date', 'patient', 'weekday', 'status'];
  protected readonly chartOptions: ChartOptions<'bar' | 'doughnut'> = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {legend: {labels: {color: '#64748b'}}},
    scales: {x: {ticks: {color: '#64748b'}}, y: {ticks: {color: '#64748b'}, beginAtZero: true}},
  };
  private readonly api = inject(BackendApiService);
  private readonly appShell = inject(AppShellStore);
  private readonly ws = inject(WebSocketService);
  private readonly translate = inject(TranslateService);
  protected readonly presenceAbsenceChart = computed<ChartData<'bar'>>(() => {
    const d = this.dashboard();
    return {
      labels: [this.translate.instant('SEANCES.PRESENCE_KPI'), this.translate.instant('SEANCES.ABSENCE_KPI')],
      datasets: [{
        label: this.translate.instant('SEANCES.PRESENCE_ABSENCE_CHART_TITLE'),
        data: [d?.presenceCount ?? 0, d?.absenceCount ?? 0],
        backgroundColor: ['#16a34a', '#dc2626'],
      }],
    };
  });
  protected readonly sexeChart = computed<ChartData<'doughnut'>>(() => {
    const dist = this.dashboard()?.sexeDistribution ?? {};
    return {
      labels: ['M', 'F', this.translate.instant('SEANCES.OTHER_LABEL')],
      datasets: [{
        label: this.translate.instant('SEANCES.SEX_DISTRIBUTION_CHART_TITLE'),
        data: [dist['M'] ?? 0, dist['F'] ?? 0, dist['AUTRE'] ?? 0],
        backgroundColor: ['#3b82f6', '#ec4899', '#f59e0b'],
      }],
    };
  });
  protected readonly ageChart = computed<ChartData<'bar'>>(() => {
    const dist = this.dashboard()?.ageDistribution ?? {};
    return {
      labels: ['0-17', '18-39', '40-59', '60+', this.translate.instant('SEANCES.UNKNOWN_LABEL')],
      datasets: [{
        label: this.translate.instant('SEANCES.AGE_DISTRIBUTION_CHART_TITLE'),
        data: [dist['0-17'] ?? 0, dist['18-39'] ?? 0, dist['40-59'] ?? 0, dist['60+'] ?? 0, dist['INCONNU'] ?? 0],
        backgroundColor: '#2563eb',
      }],
    };
  });
  private readonly snackBar = inject(MatSnackBar);

  constructor() {
    effect(() => {
      const event = this.ws.lastEvent();
      const centerId = this.appShell.currentCenterId();
      if (!event || !centerId || event.centerId !== centerId) return;
      if (!(event.type.startsWith('SEANCE_') || event.type === 'PATIENT_SCANNED')) return;
      untracked(() => this.refresh(false));
    });
  }

  ngOnInit(): void {
    this.refresh(true);
  }

  protected onMonthInput(event: Event): void {
    this.store.setDashboardMonth((event.target as HTMLInputElement)?.value || currentMonthIso());
    this.refresh(true);
  }

  /** Recharge les statistiques du mois ; {@code warn} : prévient quand le mois saisi est invalide. */
  protected refresh(warn: boolean): void {
    const centerId = this.appShell.currentCenterId();
    if (!centerId) return;
    const month = parseMonth(this.store.dashboardMonth());
    if (!month) {
      if (warn) this.notify('COMMON.INVALID_MONTH');
      return;
    }
    this.store.loadDashboard({centerId, ...month});
  }

  protected exportDashboard(format: 'csv' | 'pdf' | 'xlsx'): void {
    const centerId = this.appShell.currentCenterId();
    const month = parseMonth(this.store.dashboardMonth());
    if (!centerId || !month) return;
    this.api.exportSeanceDashboard(centerId, month.year, month.month, format).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `seances-dashboard-${month.year}-${String(month.month).padStart(2, '0')}.${format}`;
        a.click();
        URL.revokeObjectURL(url);
      },
      error: () => this.notify('COMMON.EXPORT_ERROR'),
    });
  }

  protected openDetails(kind: 'presence' | 'absence'): void {
    const centerId = this.appShell.currentCenterId();
    const month = parseMonth(this.store.dashboardMonth());
    if (!centerId || !month) return;
    this.store.openDashboardDetails(kind);
    this.store.loadDashboardDetails({centerId, ...month, kind});
  }

  protected closeDetails(): void {
    this.store.closeDashboardDetails();
  }

  /** Clé de traduction du statut d'une ligne de détail : absence du suivi ou séance réalisée. */
  protected detailStatusKey(status: string): string {
    return this.store.dashboardDetailsKind() === 'absence' ? `ABSENCES.STATUTS.${status}` : `SEANCES.SEANCE_STATUTS.${status}`;
  }

  protected detailsTitle(): string {
    return this.translate.instant(this.store.dashboardDetailsKind() === 'presence'
      ? 'SEANCES.PRESENCE_LIST_TITLE' : 'SEANCES.ABSENCE_LIST_TITLE');
  }

  protected detailsSubtitle(): string {
    return this.translate.instant('SEANCES.ROWS_COUNT', {count: this.store.dashboardDetailItems().length});
  }

  protected canPreviousPage(): boolean {
    return this.store.dashboardDetailPageIndex() > 0;
  }

  protected canNextPage(): boolean {
    return this.store.dashboardDetailPageIndex() < this.store.dashboardDetailTotalPages() - 1;
  }

  protected previousPage(): void {
    if (this.canPreviousPage()) this.store.setDashboardDetailPage(this.store.dashboardDetailPageIndex() - 1);
  }

  protected nextPage(): void {
    if (this.canNextPage()) this.store.setDashboardDetailPage(this.store.dashboardDetailPageIndex() + 1);
  }

  protected pageLabel(): string {
    if (this.store.dashboardDetailItems().length === 0) return '0 / 0';
    return `${this.store.dashboardDetailPageIndex() + 1} / ${this.store.dashboardDetailTotalPages()}`;
  }

  protected patientLabel(row: SeanceDashboardDetailItem): string {
    return (`${(row.patientNom ?? '').trim()} ${(row.patientPrenom ?? '').trim()}`).trim() || row.patientId;
  }

  protected weekdayLabel(row: SeanceDashboardDetailItem): string {
    const date = new Date(row.dateSeance);
    if (Number.isNaN(date.getTime())) return row.weekday;
    const label = new Intl.DateTimeFormat(this.translate.currentLang || 'fr', {weekday: 'short'}).format(date);
    return label.charAt(0).toUpperCase() + label.slice(1);
  }

  protected weekdayClass(row: SeanceDashboardDetailItem): string {
    const map: Record<string, string> = {
      MONDAY: 'weekday-mon', TUESDAY: 'weekday-tue', WEDNESDAY: 'weekday-wed', THURSDAY: 'weekday-thu',
      FRIDAY: 'weekday-fri', SATURDAY: 'weekday-sat', SUNDAY: 'weekday-sun',
    };
    return map[(row.weekday || '').toUpperCase()] ?? '';
  }

  private notify(key: string): void {
    this.snackBar.open(this.translate.instant(key), this.translate.instant('COMMON.OK'), {duration: 3000});
  }
}
