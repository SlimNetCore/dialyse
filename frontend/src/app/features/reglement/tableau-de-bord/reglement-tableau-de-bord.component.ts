import {ChangeDetectionStrategy, Component, computed, effect, inject, untracked} from '@angular/core';
import {PercentPipe} from '@angular/common';
import {MatCardModule} from '@angular/material/card';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatSelectModule} from '@angular/material/select';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {BaseChartDirective} from 'ng2-charts';
import {Chart, ChartData, ChartOptions, registerables} from 'chart.js';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {ReglementTableauDeBordStore} from './reglement-tableau-de-bord.store';

Chart.register(...registerables);

/**
 * Tableau de bord des règlements (/reglement/tableau-de-bord) : 6 indicateurs, donut des statuts et histogramme des
 * montants, avec ses propres filtres année / mois / caisse / agence. Extrait de l'écran de saisie des règlements
 * (/reglement), qui ne garde que la liste à régler.
 */
@Component({
  selector: 'app-reglement-tableau-de-bord',
  standalone: true,
  imports: [
    PercentPipe,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatSelectModule,
    MatProgressBarModule,
    TranslateModule,
    BaseChartDirective,
  ],
  providers: [ReglementTableauDeBordStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './reglement-tableau-de-bord.component.html',
  styleUrl: './reglement-tableau-de-bord.component.css',
})
export class ReglementTableauDeBordComponent {
  protected readonly store = inject(ReglementTableauDeBordStore);
  private readonly appShell = inject(AppShellStore);
  private readonly translate = inject(TranslateService);

  protected readonly currentCenterId = computed(() => this.appShell.currentCenterId());
  protected readonly years = Array.from({length: 6}, (_, index) => new Date().getFullYear() - index);
  protected readonly months = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12];

  protected readonly statusChartData = computed<ChartData<'doughnut'>>(() => {
    const buckets = this.store.dashboard()?.statusBreakdown ?? [];
    return {
      labels: buckets.map((bucket) => this.translate.instant(`REGLEMENT_MODULE.DASHBOARD.BUCKETS.${bucket.code}`)),
      datasets: [
        {
          data: buckets.map((bucket) => Number(bucket.count ?? 0)),
          backgroundColor: ['#c62828', '#ef6c00', '#1b5e20', '#ff9800'],
        },
      ],
    };
  });

  protected readonly amountChartData = computed<ChartData<'bar'>>(() => {
    const cards = this.store.dashboardCards();
    return {
      labels: [
        this.translate.instant('REGLEMENT_MODULE.DASHBOARD.AMOUNTS.TOTAL_FACTURE'),
        this.translate.instant('REGLEMENT_MODULE.DASHBOARD.AMOUNTS.TOTAL_REGLE'),
        this.translate.instant('REGLEMENT_MODULE.DASHBOARD.AMOUNTS.TOTAL_RESTE'),
        this.translate.instant('REGLEMENT_MODULE.DASHBOARD.AMOUNTS.TOTAL_TROP_PERCU'),
      ],
      datasets: [
        {
          label: this.translate.instant('REGLEMENT_MODULE.DASHBOARD.AMOUNTS.TITLE'),
          data: [cards.totalFacture, cards.totalRegle, cards.totalReste, cards.totalTropPercu],
          backgroundColor: ['#006a6a', '#1b5e20', '#c62828', '#ef6c00'],
          borderRadius: 12,
        },
      ],
    };
  });

  constructor() {
    // Référentiels des filtres : à l'ouverture et à chaque changement de centre.
    effect(() => {
      const centerId = this.currentCenterId();
      untracked(() => centerId ? this.store.loadReferentials({centerId}) : this.store.reset());
    });

    // Indicateurs : rechargés à chaque changement de centre ou de filtre.
    effect(() => {
      const centerId = this.currentCenterId();
      this.store.year();
      this.store.month();
      this.store.caisseId();
      this.store.agenceId();
      if (centerId) {
        untracked(() => this.store.loadDashboard({centerId}));
      }
    });
  }

  protected onYearChange(value: string): void {
    this.store.setYear(Number(value));
  }

  protected onMonthChange(value: string): void {
    this.store.setMonth(value ? Number(value) : null);
  }

  protected onCaisseChange(value: string): void {
    this.store.setCaisseId(value || null);
  }

  protected onAgenceChange(value: string): void {
    this.store.setAgenceId(value || null);
  }

  protected onResetFilters(): void {
    this.store.clearFilters();
  }

  protected onRefresh(): void {
    const centerId = this.currentCenterId();
    if (centerId) {
      this.store.loadDashboard({centerId});
    }
  }

  protected monthLabel(month: number): string {
    return this.translate.instant(`REGLEMENT_MODULE.MONTHS.${month}`);
  }

  protected chartOptions(): ChartOptions<'bar' | 'doughnut'> {
    return {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: {display: true, position: 'bottom'},
      },
    };
  }
}
