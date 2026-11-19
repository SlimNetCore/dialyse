import {ChangeDetectionStrategy, Component, computed, effect, inject, signal, TemplateRef, viewChild} from '@angular/core';
import {CommonModule} from '@angular/common';
import {MatCardModule} from '@angular/material/card';
import {MatButtonModule} from '@angular/material/button';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatDialog} from '@angular/material/dialog';
import {TranslateModule} from '@ngx-translate/core';
import {finalize} from 'rxjs/operators';
import {AuthStore} from '../../core/state/auth.store';
import {StockApiService, StockValoriseItem} from '../../core/api/stock-api.service';
import {WebSocketService} from '../../core/ws/websocket.service';
import {ConfigurableListComponent, SharedListColumn} from '../../shared/configurable-list.component';
import {PmpExplainDialogComponent} from './pmp-explain-dialog.component';

/**
 * État du stock (écran de travail, arrivée dans le module `/stock`) : stock valorisé au PMP par article et explication
 * du PMP. Les indicateurs, graphiques et alertes sont au tableau de bord du stock (`/stock/tableau-de-bord`).
 */
@Component({
  selector: 'app-etat-du-stock',
  standalone: true,
  imports: [CommonModule, MatCardModule, MatButtonModule, MatProgressBarModule, TranslateModule, ConfigurableListComponent],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './etat-du-stock.component.html',
  styleUrl: './etat-du-stock.component.css',
})
export class EtatDuStockComponent {
  protected readonly quantiteCellTemplate = viewChild<TemplateRef<any>>('quantiteCell');
  protected readonly pmpCellTemplate = viewChild<TemplateRef<any>>('pmpCell');
  protected readonly valeurCellTemplate = viewChild<TemplateRef<any>>('valeurCell');
  protected readonly actionsCellTemplate = viewChild<TemplateRef<any>>('actionsCell');
  protected readonly stockColumns = computed<SharedListColumn<StockValoriseItem>[]>(() => [
    {
      id: 'code',
      headerKey: 'STOCK.DASHBOARD.STOCK_TABLE.COL_CODE',
      valueAccessor: (row) => row.code ?? '',
      sortable: true,
      resizable: true,
      minWidthPx: 120,
      filter: {type: 'text', labelKey: 'STOCK.DASHBOARD.STOCK_TABLE.COL_CODE'},
    },
    {
      id: 'libelle',
      headerKey: 'STOCK.DASHBOARD.STOCK_TABLE.COL_ARTICLE',
      valueAccessor: (row) => row.libelle ?? '',
      sortable: true,
      resizable: true,
      minWidthPx: 220,
      filter: {type: 'text', labelKey: 'STOCK.DASHBOARD.STOCK_TABLE.COL_ARTICLE'},
    },
    {
      id: 'quantite',
      headerKey: 'STOCK.DASHBOARD.STOCK_TABLE.COL_QUANTITY',
      valueAccessor: (row) => Number(row.quantite ?? 0),
      sortValueAccessor: (row) => Number(row.quantite ?? 0),
      sortable: true,
      resizable: true,
      minWidthPx: 150,
      filter: {type: 'text', labelKey: 'STOCK.DASHBOARD.STOCK_TABLE.COL_QUANTITY'},
      cellTemplate: this.quantiteCellTemplate() ?? undefined,
    },
    {
      id: 'pmp',
      headerKey: 'STOCK.DASHBOARD.STOCK_TABLE.COL_PMP',
      valueAccessor: (row) => Number(row.pmpCourant ?? 0),
      sortValueAccessor: (row) => Number(row.pmpCourant ?? 0),
      sortable: true,
      resizable: true,
      minWidthPx: 140,
      filter: {type: 'text', labelKey: 'STOCK.DASHBOARD.STOCK_TABLE.COL_PMP'},
      cellTemplate: this.pmpCellTemplate() ?? undefined,
    },
    {
      id: 'valeur',
      headerKey: 'STOCK.DASHBOARD.STOCK_TABLE.COL_VALUE',
      valueAccessor: (row) => Number(row.valeur ?? 0),
      sortValueAccessor: (row) => Number(row.valeur ?? 0),
      sortable: true,
      resizable: true,
      minWidthPx: 150,
      filter: {type: 'text', labelKey: 'STOCK.DASHBOARD.STOCK_TABLE.COL_VALUE'},
      cellTemplate: this.valeurCellTemplate() ?? undefined,
    },
    {
      id: 'actions',
      headerKey: 'STOCK.DASHBOARD.STOCK_TABLE.COL_ACTIONS',
      valueAccessor: () => '',
      sortable: false,
      resizable: false,
      mobileRowActions: true,
      minWidthPx: 200,
      widthPx: 220,
      maxWidthPx: 280,
      cellTemplate: this.actionsCellTemplate() ?? undefined,
    },
  ]);
  protected readonly loading = signal(false);
  protected readonly stock = signal<StockValoriseItem[]>([]);
  private readonly api = inject(StockApiService);
  private readonly auth = inject(AuthStore);
  private readonly dialog = inject(MatDialog);
  private readonly ws = inject(WebSocketService);

  constructor() {
    this.reloadStock();
    effect(() => {
      const event = this.ws.lastEvent();
      const centerId = this.auth.centerId();
      if (!event || !centerId || event.centerId !== centerId) {
        return;
      }
      if (event.type === 'STOCK_MOVEMENT_CHANGED' || event.type === 'STOCK_RECALC_LOCKS_CHANGED') {
        this.reloadStock();
      }
    });
  }

  protected openPmpExplain(article: StockValoriseItem): void {
    const centerId = this.auth.centerId();
    if (!centerId) {
      return;
    }
    this.dialog.open(PmpExplainDialogComponent, {
      width: 'min(1200px, calc(100vw - 24px))',
      maxWidth: 'calc(100vw - 24px)',
      maxHeight: 'calc(100dvh - 24px)',
      data: {
        articleId: article.articleId,
        centerId,
        libelle: article.libelle,
      },
    });
  }

  private reloadStock(): void {
    const centerId = this.auth.centerId();
    if (!centerId) {
      return;
    }
    this.loading.set(true);
    this.api.stockValorise(centerId)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({next: (stock) => this.stock.set(stock)});
  }
}
