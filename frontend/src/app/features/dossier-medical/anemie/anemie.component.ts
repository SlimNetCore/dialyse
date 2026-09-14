import {ChangeDetectionStrategy, Component, computed, inject, OnInit} from '@angular/core';
import {ActivatedRoute} from '@angular/router';
import {CommonModule} from '@angular/common';
import {MatCardModule} from '@angular/material/card';
import {MatIconModule} from '@angular/material/icon';
import {MatTableModule} from '@angular/material/table';
import {MatPaginatorModule, PageEvent} from '@angular/material/paginator';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {MatTooltipModule} from '@angular/material/tooltip';
import {TranslateModule} from '@ngx-translate/core';
import {BaseChartDirective} from 'ng2-charts';
import {Chart, ChartData, ChartOptions, registerables} from 'chart.js';
import {MatButtonModule} from '@angular/material/button';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {SuiviAnemieStore} from '../state/suivi-anemie.store';
import {AdministrationsAnemieStore} from '../state/administrations-anemie.store';
import {AlertesObservanceStore} from '../state/alertes-observance.store';
import {resolvePatientIdFromRoute} from '../dossier-medical-route.util';

Chart.register(...registerables);

const STATUT_CLASS: Record<string, string> = {
  DANS_CIBLE: 'badge-ok',
  SOUS_CIBLE: 'badge-warn',
  AU_DESSUS_CIBLE: 'badge-warn',
  NON_EVALUABLE: 'badge-muted',
};

/**
 * Suivi de l'anémie : courbe Hb/ferritine, évaluation KDIGO des cibles cliniques, prescription
 * EPO/fer active, et historique complet des administrations réellement effectuées par l'infirmier
 * pendant les séances. Vue en lecture seule pour le médecin — l'administration elle-même se fait
 * depuis la séance (responsabilité de l'infirmier, qui y suit la prescription en vigueur).
 */
@Component({
  selector: 'app-anemie',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatIconModule,
    MatTableModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    MatButtonModule,
    TranslateModule,
    BaseChartDirective,
  ],
  templateUrl: './anemie.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './anemie.component.css',
})
export class AnemieComponent implements OnInit {
  protected readonly suiviStore = inject(SuiviAnemieStore);
  protected readonly administrationsStore = inject(AdministrationsAnemieStore);
  protected readonly alertesStore = inject(AlertesObservanceStore);
  protected readonly displayedColumns = [
    'dateAdministration', 'typeTraitement', 'molecule', 'dose', 'administree', 'administrePar',
  ];

  private readonly route = inject(ActivatedRoute);
  protected readonly patientId = resolvePatientIdFromRoute(this.route);
  private readonly appShell = inject(AppShellStore);

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
    const points = this.suiviStore.suivi()?.courbe ?? [];
    return {
      labels: points.map((p) => p.date),
      datasets: [
        {
          label: 'Hémoglobine (g/dL)',
          data: points.map((p) => p.hbGDl),
          borderColor: '#7c3aed',
          backgroundColor: 'rgba(124, 58, 237, 0.15)',
          borderWidth: 2,
          tension: 0.35,
          pointRadius: 3,
          pointHoverRadius: 6,
        },
        {
          label: 'Ferritine (ng/mL, /10)',
          data: points.map((p) => p.ferritineNgMl == null ? null : p.ferritineNgMl / 10),
          borderColor: '#0f766e',
          backgroundColor: 'rgba(15, 118, 110, 0.15)',
          borderWidth: 2,
          tension: 0.35,
          pointRadius: 3,
          pointHoverRadius: 6,
        },
      ],
    };
  });

  protected readonly hasCourbeData = computed(() => (this.suiviStore.suivi()?.courbe ?? []).length > 0);

  ngOnInit(): void {
    this.refresh();
  }

  badgeClass(statut: string): string {
    return STATUT_CLASS[statut] ?? 'badge-muted';
  }

  onPageChange(event: PageEvent): void {
    this.administrationsStore.setPagination(event.pageIndex, event.pageSize);
  }

  resoudreAlerte(alerteId: string): void {
    this.alertesStore.resoudre({alerteId});
  }

  private refresh(): void {
    const centerId = this.appShell.currentCenterId();
    if (!centerId || !this.patientId) return;
    this.suiviStore.load({centerId, patientId: this.patientId});
    this.administrationsStore.load({
      centerId, patientId: this.patientId,
      page: this.administrationsStore.pageIndex(), size: this.administrationsStore.pageSize(),
    });
    this.alertesStore.load({centerId, patientId: this.patientId});
  }
}
