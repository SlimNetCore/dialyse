import {ChangeDetectionStrategy, Component, computed, inject, signal} from '@angular/core';
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
  template: `
    <section class="app-page">
      <div class="app-hero-card">
        <span class="app-eyebrow">Module stock</span>
        <h1 class="app-section-title">Gestion des stocks</h1>
        <p class="app-section-copy">
          Stock valorisé en temps réel (PMP), traçabilité lot → patient → séance et centre d'alertes consolidé.
        </p>
        <div class="app-button-cluster">
          <a mat-flat-button color="primary" routerLink="/stock/bons-commande">
            <mat-icon>request_quote</mat-icon>
            Bons de commande
          </a>
          <a mat-stroked-button routerLink="/stock/bons-reception">
            <mat-icon>inventory</mat-icon>
            Réceptions
          </a>
          <a mat-stroked-button routerLink="/stock/bons-sortie">
            <mat-icon>logout</mat-icon>
            Sorties
          </a>
          <a mat-stroked-button routerLink="/stock/fournisseurs">
            <mat-icon>local_shipping</mat-icon>
            Fournisseurs
          </a>
          <a mat-stroked-button routerLink="/stock/articles">
            <mat-icon>inventory_2</mat-icon>
            Articles
          </a>
        </div>
      </div>

      @if (loading()) {
        <mat-progress-bar mode="indeterminate"></mat-progress-bar>
      }

      <mat-card>
        <mat-card-header>
          <mat-card-title>Filtres analytiques</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <div class="filters-grid">
            <div class="filter-block">
              <span class="filter-title">Periode</span>
              <div class="app-button-cluster compact">
                @for (d of daysOptions; track d) {
                  <button mat-stroked-button [color]="selectedDays() === d ? 'primary' : undefined"
                          (click)="onDaysChange(d)">
                    {{ d }} jours
                  </button>
                }
              </div>
            </div>

            <div class="filter-block">
              <span class="filter-title">Top N articles</span>
              <div class="app-button-cluster compact">
                @for (n of topNOptions; track n) {
                  <button mat-stroked-button [color]="selectedTopN() === n ? 'primary' : undefined"
                          (click)="onTopNChange(n)">
                    Top {{ n }}
                  </button>
                }
              </div>
            </div>

            <div class="filter-block">
              <span class="filter-title">Tri Top N</span>
              <div class="app-button-cluster compact">
                <button mat-stroked-button [color]="selectedSortBy() === 'VALUE' ? 'primary' : undefined"
                        (click)="onSortByChange('VALUE')">
                  Valeur
                </button>
                <button mat-stroked-button [color]="selectedSortBy() === 'QUANTITY' ? 'primary' : undefined"
                        (click)="onSortByChange('QUANTITY')">
                  Quantite
                </button>
              </div>
            </div>
          </div>
        </mat-card-content>
      </mat-card>

      <div class="kpi-grid">
        <div class="app-data-pill">
          <span>Valeur totale du stock</span>
          <strong>{{ valeurTotale() | number:'1.0-2' }}</strong>
        </div>
        <div class="app-data-pill">
          <span>Quantité globale</span>
          <strong>{{ quantiteTotale() | number:'1.0-2' }}</strong>
        </div>
        <div class="app-data-pill">
          <span>Articles en stock</span>
          <strong>{{ stock().length }}</strong>
        </div>
        <div class="app-data-pill">
          <span>Alertes actives</span>
          <strong>{{ alertes().length }}</strong>
        </div>
        <div class="app-data-pill warning-pill">
          <span>Préemptions (lots)</span>
          <strong>{{ preemptions().length }}</strong>
        </div>
        <div class="app-data-pill">
          <span>Flux période (valeur)</span>
          <strong>{{ (analytics()?.trend?.length ? periodValueFlow() : 0) | number:'1.0-2' }}</strong>
        </div>
      </div>

      <mat-card>
        <mat-card-header>
          <mat-card-title>Tendance des mouvements ({{ selectedDays() }} jours)</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          @if (analyticsLoading()) {
            <mat-progress-bar mode="indeterminate"></mat-progress-bar>
          }
          @if ((analytics()?.trend?.length ?? 0) === 0) {
            <p class="app-muted-note">Aucune donnée de tendance sur la période.</p>
          } @else {
            <div class="chart-wrap">
              <canvas baseChart
                      [type]="'line'"
                      [data]="trendChartData()"
                      [options]="trendChartOptions">
              </canvas>
            </div>
          }
        </mat-card-content>
      </mat-card>

      <mat-card>
        <mat-card-header>
          <mat-card-title>
            Top {{ selectedTopN() }} articles — {{ selectedSortBy() === 'VALUE' ? 'tri valeur' : 'tri quantite' }}
          </mat-card-title>
        </mat-card-header>
        <mat-card-content>
          @if ((analytics()?.topArticles?.length ?? 0) === 0) {
            <p class="app-muted-note">Aucun mouvement article sur la période.</p>
          } @else {
            <div class="chart-wrap">
              <canvas baseChart
                      [type]="'bar'"
                      [data]="topChartData()"
                      [options]="topChartOptions">
              </canvas>
            </div>
          }
        </mat-card-content>
      </mat-card>

      <mat-card>
        <mat-card-header>
          <mat-card-title>Détails des lots en préemption</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          @if (preemptions().length === 0) {
            <p class="app-muted-note">Aucun lot en préemption.</p>
          } @else {
            <div class="alert-list">
              @for (al of preemptions(); track al.articleId + (al.lotId ?? '')) {
                <div class="alert-row">
                  <mat-chip highlighted="true">PEREMPTION</mat-chip>
                  <span class="alert-label">{{ al.articleLibelle }}</span>
                  <span class="alert-meta">Lot {{ al.numeroLot || '—' }}</span>
                  <span class="alert-meta">Date: {{ al.datePeremption || '—' }}</span>
                  @if (al.quantite != null) {
                    <span class="alert-meta">Qté: {{ al.quantite | number:'1.0-2' }}</span>
                  }
                </div>
              }
            </div>
          }
        </mat-card-content>
      </mat-card>

      <mat-card>
        <mat-card-header>
          <mat-card-title>Stock valorisé</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <table mat-table [dataSource]="stock()" class="full-width">
            <ng-container matColumnDef="code">
              <th mat-header-cell *matHeaderCellDef>Code</th>
              <td mat-cell *matCellDef="let a">{{ a.code }}</td>
            </ng-container>
            <ng-container matColumnDef="libelle">
              <th mat-header-cell *matHeaderCellDef>Article</th>
              <td mat-cell *matCellDef="let a">{{ a.libelle }}</td>
            </ng-container>
            <ng-container matColumnDef="quantite">
              <th mat-header-cell *matHeaderCellDef>Quantité</th>
              <td mat-cell *matCellDef="let a">{{ a.quantite | number:'1.0-2' }} {{ a.unite }}</td>
            </ng-container>
            <ng-container matColumnDef="pmp">
              <th mat-header-cell *matHeaderCellDef>PMP</th>
              <td mat-cell *matCellDef="let a">{{ a.pmpCourant | number:'1.0-4' }}</td>
            </ng-container>
            <ng-container matColumnDef="valeur">
              <th mat-header-cell *matHeaderCellDef>Valeur</th>
              <td mat-cell *matCellDef="let a">{{ a.valeur | number:'1.0-2' }}</td>
            </ng-container>
            <ng-container matColumnDef="actions">
              <th mat-header-cell *matHeaderCellDef>Actions</th>
              <td mat-cell *matCellDef="let a">
                <button mat-stroked-button color="primary" (click)="openPmpExplain(a)">
                  Expliquer PMP
                </button>
              </td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="cols"></tr>
            <tr mat-row *matRowDef="let row; columns: cols"></tr>
          </table>
          @if (!loading() && stock().length === 0) {
            <p class="app-muted-note">Aucun article en stock.</p>
          }
        </mat-card-content>
      </mat-card>

      <mat-card>
        <mat-card-header>
          <mat-card-title>Centre d'alertes</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          @if (alertes().length === 0) {
            <p class="app-muted-note">Aucune alerte. ✅</p>
          } @else {
            <div class="alert-list">
              @for (al of alertes(); track al.articleId + (al.lotId ?? '')) {
                <div class="alert-row" [class.danger]="al.type === 'RUPTURE'">
                  <mat-chip [highlighted]="true">{{ al.type }}</mat-chip>
                  <span class="alert-label">{{ al.articleLibelle }}</span>
                  @if (al.numeroLot) {
                    <span class="alert-meta">Lot {{ al.numeroLot }} · expire {{ al.datePeremption }}</span>
                  }
                  @if (al.type !== 'PEREMPTION') {
                    <span class="alert-meta">Reste {{ al.quantite | number:'1.0-2' }}
                      (seuil {{ al.seuil | number:'1.0-2' }})</span>
                  }
                </div>
              }
            </div>
          }
        </mat-card-content>
      </mat-card>
    </section>
  `,
  styles: [`
    .kpi-grid {
      display: flex;
      gap: 12px;
      flex-wrap: wrap;
    }

    .warning-pill {
      border: 1px solid #ffcc80;
      background: #fff8e1;
    }

    .filters-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
      gap: 12px;
    }

    .filter-block {
      display: grid;
      gap: 8px;
      border: 1px solid var(--app-border);
      border-radius: 12px;
      padding: 10px 12px;
      background: color-mix(in srgb, var(--app-frost) 50%, var(--app-surface));
    }

    .filter-title {
      font-size: 12px;
      color: var(--app-muted);
      font-weight: 600;
    }

    .compact {
      gap: 6px;
    }

    .chart-wrap {
      min-height: 320px;
      display: block;
    }

    .full-width {
      width: 100%;
    }

    .alert-list {
      display: grid;
      gap: 8px;
    }

    .alert-row {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 8px 12px;
      border: 1px solid var(--app-border);
      border-radius: 14px;
      background: var(--app-frost);
    }

    .alert-row.danger {
      border-color: #e5484d;
    }

    .alert-label {
      font-weight: 700;
    }

    .alert-meta {
      color: var(--app-muted);
      font-size: 12px;
    }

    mat-card {
      margin-top: 16px;
    }
  `],
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

  constructor() {
    this.reloadStockAndAlertes();
    this.reloadAnalytics();
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

