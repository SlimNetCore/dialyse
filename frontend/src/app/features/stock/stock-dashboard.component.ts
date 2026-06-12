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
import {AlerteStock, StockApiService, StockValoriseItem} from '../../core/api/stock-api.service';
import {PmpExplainDialogComponent} from './pmp-explain-dialog.component';

@Component({
  selector: 'app-stock-dashboard',
  standalone: true,
  imports: [
    CommonModule, RouterLink, MatCardModule, MatButtonModule, MatIconModule,
    MatTableModule, MatChipsModule, MatProgressBarModule,
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
        </div>
      </div>

      @if (loading()) {
        <mat-progress-bar mode="indeterminate"></mat-progress-bar>
      }

      <div class="kpi-grid">
        <div class="app-data-pill">
          <span>Valeur totale du stock</span>
          <strong>{{ valeurTotale() | number:'1.0-2' }}</strong>
        </div>
        <div class="app-data-pill">
          <span>Articles en stock</span>
          <strong>{{ stock().length }}</strong>
        </div>
        <div class="app-data-pill">
          <span>Alertes actives</span>
          <strong>{{ alertes().length }}</strong>
        </div>
      </div>

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
                  Voir PMP
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
  protected readonly loading = signal(false);
  protected readonly stock = signal<StockValoriseItem[]>([]);
  protected readonly alertes = signal<AlerteStock[]>([]);
  protected readonly valeurTotale = computed(() =>
    this.stock().reduce((sum, a) => sum + (a.valeur ?? 0), 0));
  private readonly api = inject(StockApiService);
  private readonly auth = inject(AuthStore);
  private readonly dialog = inject(MatDialog);

  constructor() {
    this.reload();
  }

  private reload(): void {
    const centerId = this.auth.centerId();
    if (!centerId) {
      return;
    }
    this.loading.set(true);
    this.api.stockValorise(centerId).subscribe({
      next: (items) => this.stock.set(items),
      complete: () => this.loading.set(false),
      error: () => this.loading.set(false),
    });
    this.api.alertes(centerId).subscribe({next: (a) => this.alertes.set(a)});
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
}

