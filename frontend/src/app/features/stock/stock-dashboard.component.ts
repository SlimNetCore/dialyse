import {ChangeDetectionStrategy, Component, computed, effect, inject, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {RouterLink} from '@angular/router';
import {MatCardModule} from '@angular/material/card';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatTableModule} from '@angular/material/table';
import {MatChipsModule} from '@angular/material/chips';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatDialog} from '@angular/material/dialog';
import {AuthStore} from '../../core/state/auth.store';
import {
  AlerteStock,
  StockApiService,
  StockDashboardAnalytics,
  StockTopSort,
  StockValoriseItem,
} from '../../core/api/stock-api.service';
import {PmpExplainDialogComponent} from './pmp-explain-dialog.component';
import {WebSocketService} from '../../core/ws/websocket.service';
import {BaseChartDirective} from 'ng2-charts';
import {Chart, ChartData, ChartOptions, registerables} from 'chart.js';
import {forkJoin} from 'rxjs';
import {finalize} from 'rxjs/operators';

const DAYS_OPTIONS = [7, 30, 90] as const;
const TOP_N_OPTIONS = [5, 10, 20, 50] as const;

Chart.register(...registerables);

@Component({
  selector: 'app-stock-dashboard',
  standalone: true,
  imports: [
    CommonModule, RouterLink, MatCardModule, MatButtonModule, MatIconModule,
    MatTableModule, MatChipsModule, MatProgressBarModule, BaseChartDirective,
  ],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './stock-dashboard.component.html',
  styleUrl: './stock-dashboard.component.css',
})
export class StockDashboardComponent {
  protected readonly cols = ['code', 'libelle', 'quantite', 'pmp', 'valeur', 'actions'];
  protected readonly daysOptions = DAYS_OPTIONS;
  protected readonly topNOptions = TOP_N_OPTIONS;
  protected readonly loading = signal(false);
  protected readonly analyticsLoading = signal(false);
  protected readonly stock = signal<StockValoriseItem[]>([]);
  protected readonly alertes = signal<AlerteStock[]>([]);
  protected readonly analytics = signal<StockDashboardAnalytics | null>(null);
  protected readonly selectedDays = signal<number>(30);
  protected readonly selectedTopN = signal<number>(10);
  protected readonly selectedSortBy = signal<StockTopSort>('VALUE');
  protected readonly quantiteTotale = computed(() =>
    this.stock().reduce((sum, a) => sum + (a.quantite ?? 0), 0));
  protected readonly valeurTotale = computed(() =>
    this.stock().reduce((sum, a) => sum + (a.valeur ?? 0), 0));
  protected readonly preemptions = computed(() =>
    this.alertes()
      .filter(a => a.type === 'PEREMPTION')
      .sort((a, b) => (a.datePeremption ?? '').localeCompare(b.datePeremption ?? '')));
  protected readonly periodValueFlow = computed(() =>
    (this.analytics()?.trend ?? []).reduce((sum, p) => sum + Number(p.valeur ?? 0), 0));
  protected readonly trendChartData = computed<ChartData<'line'>>(() => {
    const trend = this.analytics()?.trend ?? [];
    return {
      labels: trend.map(point => this.shortDate(point.date)),
      datasets: [
        {
          label: 'Quantite nette',
          data: trend.map(point => Number(point.quantite ?? 0)),
          borderColor: '#26a69a',
          backgroundColor: 'rgba(38, 166, 154, 0.22)',
          yAxisID: 'y',
          tension: 0.28,
          fill: true,
        },
        {
          label: 'Valeur mouvements',
          data: trend.map(point => Number(point.valeur ?? 0)),
          borderColor: '#1e88e5',
          backgroundColor: 'rgba(30, 136, 229, 0.2)',
          yAxisID: 'y1',
          tension: 0.2,
          fill: true,
        },
      ],
    };
  });
  protected readonly topChartData = computed<ChartData<'bar'>>(() => {
    const top = this.analytics()?.topArticles ?? [];
    const isValue = this.selectedSortBy() === 'VALUE';
    return {
      labels: top.map(a => `${a.code ?? ''} ${a.libelle}`.trim()),
      datasets: [
        {
          label: isValue ? 'Valeur' : 'Quantite',
          data: top.map(a => isValue ? Number(a.valeur ?? 0) : Number(a.quantite ?? 0)),
          backgroundColor: isValue ? '#42a5f5' : '#26a69a',
          borderRadius: 8,
          maxBarThickness: 34,
        },
      ],
    };
  });
  protected readonly trendChartOptions: ChartOptions<'line'> = {
    responsive: true,
    maintainAspectRatio: false,
    interaction: {mode: 'index', intersect: false},
    plugins: {
      legend: {display: true, position: 'bottom'},
    },
    scales: {
      y: {
        position: 'left',
        title: {display: true, text: 'Quantite'},
      },
      y1: {
        position: 'right',
        grid: {drawOnChartArea: false},
        title: {display: true, text: 'Valeur'},
      },
    },
  };
  protected readonly topChartOptions: ChartOptions<'bar'> = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {display: false},
    },
    scales: {
      x: {
        ticks: {
          autoSkip: false,
        },
      },
    },
  };
  private readonly api = inject(StockApiService);
  private readonly auth = inject(AuthStore);
  private readonly dialog = inject(MatDialog);
  private readonly ws = inject(WebSocketService);

  constructor() {
    this.reloadStockAndAlertes();
    this.reloadAnalytics();
    effect(() => {
      const event = this.ws.lastEvent();
      const centerId = this.auth.centerId();
      if (!event || !centerId || event.centerId !== centerId) {
        return;
      }
      if (event.type === 'STOCK_MOVEMENT_CHANGED' || event.type === 'STOCK_RECALC_LOCKS_CHANGED') {
        this.reloadStockAndAlertes();
        this.reloadAnalytics();
      }
    });
  }

  protected onDaysChange(days: number): void {
    if (this.selectedDays() === days) {
      return;
    }
    this.selectedDays.set(days);
    this.reloadAnalytics();
  }

  protected onTopNChange(topN: number): void {
    if (this.selectedTopN() === topN) {
      return;
    }
    this.selectedTopN.set(topN);
    this.reloadAnalytics();
  }

  protected onSortByChange(sortBy: StockTopSort): void {
    if (this.selectedSortBy() === sortBy) {
      return;
    }
    this.selectedSortBy.set(sortBy);
    this.reloadAnalytics();
  }

  private reloadStockAndAlertes(): void {
    const centerId = this.auth.centerId();
    if (!centerId) {
      return;
    }
    this.loading.set(true);
    forkJoin({
      stock: this.api.stockValorise(centerId),
      alertes: this.api.alertes(centerId),
    })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: ({stock, alertes}) => {
          this.stock.set(stock);
          this.alertes.set(alertes);
        },
      });
  }

  private reloadAnalytics(): void {
    const centerId = this.auth.centerId();
    if (!centerId) {
      return;
    }
    this.analyticsLoading.set(true);
    this.api.dashboardAnalytics(centerId, this.selectedDays(), this.selectedTopN(), this.selectedSortBy())
      .pipe(finalize(() => this.analyticsLoading.set(false)))
      .subscribe({
        next: (data) => this.analytics.set(data),
      });
  }

  protected openPmpExplain(article: StockValoriseItem): void {
    const centerId = this.auth.centerId();
    if (!centerId) {
      return;
    }
    this.dialog.open(PmpExplainDialogComponent, {
      width: '1200px',
      maxHeight: '90vh',
      data: {
        articleId: article.articleId,
        centerId,
        libelle: article.libelle,
      },
    });
  }

  private shortDate(raw: string): string {
    if (!raw) {
      return '';
    }
    const date = new Date(raw);
    if (Number.isNaN(date.getTime())) {
      return raw;
    }
    return date.toLocaleDateString('fr-FR', {day: '2-digit', month: '2-digit'});
  }
}

