import {ChangeDetectionStrategy, Component, computed, inject, OnInit, signal} from '@angular/core';
import {ActivatedRoute} from '@angular/router';
import {MatTableModule} from '@angular/material/table';
import {MatPaginatorModule, PageEvent} from '@angular/material/paginator';
import {MatCheckboxModule} from '@angular/material/checkbox';
import {MatCardModule} from '@angular/material/card';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {TranslateModule} from '@ngx-translate/core';
import {BaseChartDirective} from 'ng2-charts';
import {Chart, ChartData, ChartOptions, registerables} from 'chart.js';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {ConstanteSeance} from '../../../core/api/dossier-medical-api.service';
import {ConstantesStore} from '../state/constantes.store';
import {resolvePatientIdFromRoute} from '../dossier-medical-route.util';

Chart.register(...registerables);

type MetricKey =
  | 'poidsAvantKg' | 'poidsApresKg'
  | 'taAvantSys' | 'taAvantDia' | 'taApresSys' | 'taApresDia'
  | 'debitSangMlMin' | 'ultrafiltrationMl' | 'dureeMinutes';

interface MetricDef {
  key: MetricKey;
  label: string;
  color: string;
  accessor: (row: ConstanteSeance) => number | null;
}

function parseTa(value: string | null): { sys: number | null; dia: number | null } {
  if (!value) return {sys: null, dia: null};
  const match = value.match(/(\d+(?:\.\d+)?)\s*\/\s*(\d+(?:\.\d+)?)/);
  if (!match) return {sys: null, dia: null};
  return {sys: Number(match[1]), dia: Number(match[2])};
}

const METRICS: MetricDef[] = [
  {key: 'poidsAvantKg', label: 'DOSSIER_MEDICAL.POIDS_AVANT', color: '#2563eb', accessor: (r) => r.poidsAvantKg},
  {key: 'poidsApresKg', label: 'DOSSIER_MEDICAL.POIDS_APRES', color: '#0f766e', accessor: (r) => r.poidsApresKg},
  {key: 'taAvantSys', label: 'DOSSIER_MEDICAL.TA_AVANT_SYS', color: '#1d4ed8', accessor: (r) => parseTa(r.taAvant).sys},
  {key: 'taAvantDia', label: 'DOSSIER_MEDICAL.TA_AVANT_DIA', color: '#7c3aed', accessor: (r) => parseTa(r.taAvant).dia},
  {key: 'taApresSys', label: 'DOSSIER_MEDICAL.TA_APRES_SYS', color: '#ea580c', accessor: (r) => parseTa(r.taApres).sys},
  {key: 'taApresDia', label: 'DOSSIER_MEDICAL.TA_APRES_DIA', color: '#c2410c', accessor: (r) => parseTa(r.taApres).dia},
  {key: 'debitSangMlMin', label: 'DOSSIER_MEDICAL.DEBIT_SANG', color: '#16a34a', accessor: (r) => r.debitSangMlMin},
  {
    key: 'ultrafiltrationMl',
    label: 'DOSSIER_MEDICAL.ULTRAFILTRATION',
    color: '#db2777',
    accessor: (r) => r.ultrafiltrationMl
  },
  {key: 'dureeMinutes', label: 'DOSSIER_MEDICAL.DUREE', color: '#64748b', accessor: (r) => r.dureeMinutes},
];

const DEFAULT_SELECTION: MetricKey[] = ['poidsAvantKg', 'poidsApresKg'];

/**
 * Vue longitudinale des constantes du patient (poids, TA, débit, UF, durée), agrégée à partir du
 * volet paramédical déjà saisi séance après séance. Aucune saisie ici : lecture seule.
 * Le médecin choisit une ou plusieurs constantes pour en visualiser l'évolution sur un graphe.
 */
@Component({
  selector: 'app-constantes',
  standalone: true,
  imports: [
    MatTableModule,
    MatPaginatorModule,
    MatCheckboxModule,
    MatCardModule,
    MatProgressSpinnerModule,
    TranslateModule,
    BaseChartDirective,
  ],
  templateUrl: './constantes.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './constantes.component.css',
})
export class ConstantesComponent implements OnInit {
  protected readonly store = inject(ConstantesStore);
  protected readonly displayedColumns = [
    'dateSeance', 'poidsAvantKg', 'poidsApresKg', 'taAvant', 'taApres', 'debitSangMlMin',
    'ultrafiltrationMl', 'dureeMinutes',
  ];
  protected readonly metrics = METRICS;
  protected readonly selectedMetrics = signal<Set<MetricKey>>(new Set(DEFAULT_SELECTION));
  protected readonly lineChartOptions: ChartOptions<'line'> = {
    responsive: true,
    maintainAspectRatio: false,
    interaction: {mode: 'nearest', intersect: false},
    plugins: {
      legend: {
        display: true,
        position: 'bottom',
        labels: {
          usePointStyle: true,
          pointStyle: 'circle',
          boxWidth: 8,
          color: '#64748b',
          font: {size: 12, weight: 600}
        },
      },
      tooltip: {enabled: true, backgroundColor: '#0f172a', titleColor: '#f8fafc', bodyColor: '#e2e8f0', padding: 10},
    },
    scales: {
      x: {ticks: {color: '#64748b', maxRotation: 0, autoSkip: true}, grid: {color: 'rgba(100,116,139,0.15)'}},
      y: {ticks: {color: '#64748b'}, grid: {color: 'rgba(100,116,139,0.15)'}},
    },
  };
  protected readonly courbeChart = computed<ChartData<'line'>>(() => {
    const rows = this.store.chartRows();
    const selected = this.selectedMetrics();
    return {
      labels: rows.map((r) => r.dateSeance),
      datasets: this.metrics
        .filter((m) => selected.has(m.key))
        .map((m) => ({
          label: m.label,
          data: rows.map((r) => m.accessor(r)),
          borderColor: m.color,
          backgroundColor: m.color + '26',
          borderWidth: 2,
          tension: 0.3,
          pointRadius: 3,
          pointHoverRadius: 6,
          spanGaps: true,
        })),
    };
  });
  protected readonly hasSelection = computed(() => this.selectedMetrics().size > 0);
  protected readonly hasChartData = computed(() => this.store.chartRows().length > 0);
  private readonly route = inject(ActivatedRoute);
  protected readonly patientId = resolvePatientIdFromRoute(this.route);
  private readonly appShell = inject(AppShellStore);

  ngOnInit(): void {
    this.refresh();
  }

  toggleMetric(key: MetricKey, checked: boolean): void {
    const next = new Set(this.selectedMetrics());
    if (checked) {
      next.add(key);
    } else {
      next.delete(key);
    }
    this.selectedMetrics.set(next);
  }

  isSelected(key: MetricKey): boolean {
    return this.selectedMetrics().has(key);
  }

  onPageChange(event: PageEvent): void {
    this.store.setPagination(event.pageIndex, event.pageSize);
  }

  private refresh(): void {
    const centerId = this.appShell.currentCenterId();
    if (!centerId || !this.patientId) return;
    this.store.load({centerId, patientId: this.patientId, page: this.store.pageIndex(), size: this.store.pageSize()});
    this.store.loadChart({centerId, patientId: this.patientId});
  }
}
