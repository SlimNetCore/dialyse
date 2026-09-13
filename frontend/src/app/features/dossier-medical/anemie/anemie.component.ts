import {ChangeDetectionStrategy, Component, computed, inject, OnInit, signal} from '@angular/core';
import {ActivatedRoute} from '@angular/router';
import {CommonModule} from '@angular/common';
import {MatCardModule} from '@angular/material/card';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatSelectModule} from '@angular/material/select';
import {MatDatepickerModule} from '@angular/material/datepicker';
import {MatNativeDateModule} from '@angular/material/core';
import {MatCheckboxModule} from '@angular/material/checkbox';
import {MatTableModule} from '@angular/material/table';
import {MatPaginatorModule, PageEvent} from '@angular/material/paginator';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {MatTooltipModule} from '@angular/material/tooltip';
import {TranslateModule} from '@ngx-translate/core';
import {BaseChartDirective} from 'ng2-charts';
import {Chart, ChartData, ChartOptions, registerables} from 'chart.js';
import {requiredValidator, SignalForm} from '../../../shared/forms/signal-form';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {DossierMedicalAccessService} from '../dossier-medical-access.service';
import {SuiviAnemieStore} from '../state/suivi-anemie.store';
import {AdministrationsAnemieStore} from '../state/administrations-anemie.store';
import {resolvePatientIdFromRoute} from '../dossier-medical-route.util';

Chart.register(...registerables);

interface AdministrationFormModel {
  typeTraitement: 'EPO' | 'FER_INJECTABLE';
  molecule: string | null;
  dose: number | null;
  uniteDose: string | null;
  voie: string | null;
  dateAdministration: Date | string | null;
  administrePar: string | null;
  administree: boolean;
  motifNonAdministration: string | null;
}

function emptyForm(): AdministrationFormModel {
  return {
    typeTraitement: 'EPO',
    molecule: null,
    dose: null,
    uniteDose: 'UI',
    voie: 'SC',
    dateAdministration: new Date(),
    administrePar: null,
    administree: true,
    motifNonAdministration: null,
  };
}

function toIsoDate(value: Date | string | null): string | null {
  if (!value) return null;
  if (typeof value === 'string') return value;
  const y = value.getFullYear();
  const m = String(value.getMonth() + 1).padStart(2, '0');
  const d = String(value.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

const STATUT_CLASS: Record<string, string> = {
  DANS_CIBLE: 'badge-ok',
  SOUS_CIBLE: 'badge-warn',
  AU_DESSUS_CIBLE: 'badge-warn',
  NON_EVALUABLE: 'badge-muted',
};

/**
 * Suivi de l'anémie : courbe Hb/ferritine, évaluation KDIGO des cibles cliniques, prescription
 * EPO/fer active, et historique + saisie des administrations réellement effectuées.
 */
@Component({
  selector: 'app-anemie',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatCheckboxModule,
    MatTableModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    TranslateModule,
    BaseChartDirective,
  ],
  templateUrl: './anemie.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './anemie.component.css',
})
export class AnemieComponent implements OnInit {
  protected readonly access = inject(DossierMedicalAccessService);
  protected readonly suiviStore = inject(SuiviAnemieStore);
  protected readonly administrationsStore = inject(AdministrationsAnemieStore);
  protected readonly displayedColumns = [
    'dateAdministration', 'typeTraitement', 'molecule', 'dose', 'administree', 'administrePar',
  ];
  protected readonly formOpen = signal(false);
  protected readonly form = new SignalForm<AdministrationFormModel>(emptyForm(), {
    dateAdministration: [requiredValidator()],
  });
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
  private readonly route = inject(ActivatedRoute);
  protected readonly patientId = resolvePatientIdFromRoute(this.route);
  private readonly appShell = inject(AppShellStore);

  ngOnInit(): void {
    this.refresh();
  }

  badgeClass(statut: string): string {
    return STATUT_CLASS[statut] ?? 'badge-muted';
  }

  onPageChange(event: PageEvent): void {
    this.administrationsStore.setPagination(event.pageIndex, event.pageSize);
  }

  openCreateForm(): void {
    this.form.reset(emptyForm());
    this.formOpen.set(true);
  }

  cancelForm(): void {
    this.formOpen.set(false);
  }

  onValue<K extends keyof AdministrationFormModel>(key: K, value: AdministrationFormModel[K]): void {
    this.form.set(key, value);
  }

  onNumber<K extends keyof AdministrationFormModel>(key: K, raw: string): void {
    const value = raw === '' ? null : Number(raw);
    this.form.set(key, (Number.isNaN(value) ? null : value) as AdministrationFormModel[K]);
  }

  save(): void {
    this.form.markAllTouched();
    if (!this.form.valid()) return;
    const centerId = this.appShell.currentCenterId();
    if (!centerId || !this.patientId) return;
    const value = this.form.value();
    this.administrationsStore.create({
      patientId: this.patientId,
      payload: {
        centerId,
        prescriptionMedicaleId: null,
        typeTraitement: value.typeTraitement,
        molecule: value.molecule,
        dose: value.administree ? value.dose : null,
        uniteDose: value.administree ? value.uniteDose : null,
        voie: value.voie,
        dateAdministration: toIsoDate(value.dateAdministration),
        seanceId: null,
        administrePar: value.administrePar,
        administree: value.administree,
        motifNonAdministration: value.administree ? null : value.motifNonAdministration,
      },
    });
    this.formOpen.set(false);
    this.suiviStore.refresh();
  }

  private refresh(): void {
    const centerId = this.appShell.currentCenterId();
    if (!centerId || !this.patientId) return;
    this.suiviStore.load({centerId, patientId: this.patientId});
    this.administrationsStore.load({
      centerId, patientId: this.patientId,
      page: this.administrationsStore.pageIndex(), size: this.administrationsStore.pageSize(),
    });
  }
}
