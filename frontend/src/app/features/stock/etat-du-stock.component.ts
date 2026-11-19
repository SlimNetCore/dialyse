import {ChangeDetectionStrategy, Component, computed, effect, inject, signal, TemplateRef, viewChild} from '@angular/core';
import {CommonModule} from '@angular/common';
import {MatCardModule} from '@angular/material/card';
import {MatButtonModule} from '@angular/material/button';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatDialog} from '@angular/material/dialog';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {NgTableCellFormat, NgTableCellFormatComponent, NgTableColumn, NgTableComponent, NgTableTone} from '@sbourahla/ng-table';
import {finalize} from 'rxjs/operators';
import {AuthStore} from '../../core/state/auth.store';
import {StockApiService, StockValoriseItem} from '../../core/api/stock-api.service';
import {WebSocketService} from '../../core/ws/websocket.service';
import {NgTableTranslatedLabels} from '../../shared/ng-table-labels';
import {PmpExplainDialogComponent} from './pmp-explain-dialog.component';
import {langueCourante, STOCK_DEFAULT_PAGE_SIZE, STOCK_PAGE_SIZES} from './stock-ng-table';

type CellTemplate = NgTableColumn<StockValoriseItem>['cellTemplate'];

/** Quantité en stock : rupture (zéro ou moins) en rouge, sinon pastille neutre. */
export function tonDeLaQuantite(quantite: number | null | undefined): NgTableTone {
  return Number(quantite ?? 0) <= 0 ? 'danger' : 'success';
}

/**
 * État du stock (écran de travail, arrivée dans le module `/stock`) : stock valorisé au PMP par article et explication
 * du PMP, dans un `<ng-table>` en mode local (tout le stock du centre est déjà chargé) paginé côté client. Les
 * indicateurs, graphiques et alertes sont au tableau de bord du stock (`/stock/tableau-de-bord`).
 */
@Component({
  selector: 'app-etat-du-stock',
  standalone: true,
  imports: [CommonModule, MatCardModule, MatButtonModule, MatProgressBarModule, TranslateModule, NgTableComponent,
    NgTableCellFormatComponent],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './etat-du-stock.component.html',
  styleUrl: './etat-du-stock.component.css',
})
export class EtatDuStockComponent {
  protected readonly pageSizes = STOCK_PAGE_SIZES;
  protected readonly defaultPageSize = STOCK_DEFAULT_PAGE_SIZE;
  protected readonly labels = inject(NgTableTranslatedLabels).labels;
  /** Pastille de la quantité : rouge en rupture. */
  protected readonly badgeQuantite: NgTableCellFormat<StockValoriseItem> = {
    type: 'badge', tone: (value) => tonDeLaQuantite(value as number),
  };
  protected readonly cleArticle = (row: StockValoriseItem): unknown => row.articleId;
  protected readonly loading = signal(false);
  protected readonly stock = signal<StockValoriseItem[]>([]);
  private readonly quantiteCellTemplate = viewChild<TemplateRef<unknown>>('quantiteCell');
  private readonly pmpCellTemplate = viewChild<TemplateRef<unknown>>('pmpCell');
  private readonly valeurCellTemplate = viewChild<TemplateRef<unknown>>('valeurCell');
  private readonly actionsCellTemplate = viewChild<TemplateRef<unknown>>('actionsCell');
  private readonly translate = inject(TranslateService);
  private readonly langue = langueCourante();
  protected readonly emptyLabel = computed(() => {
    this.langue();
    return this.translate.instant('STOCK.DASHBOARD.EMPTY.NO_STOCK') as string;
  });
  protected readonly stockColumns = computed<NgTableColumn<StockValoriseItem>[]>(() => {
    this.langue();
    const t = (cle: string): string => this.translate.instant(`STOCK.DASHBOARD.STOCK_TABLE.${cle}`);
    return [
      {
        id: 'code', header: t('COL_CODE'), valueAccessor: (row) => row.code ?? '', sortable: true, resizable: true,
        minWidthPx: 120, filter: {type: 'text', label: t('COL_CODE')},
      },
      {
        id: 'libelle', header: t('COL_ARTICLE'), valueAccessor: (row) => row.libelle ?? '', sortable: true,
        resizable: true, minWidthPx: 220, filter: {type: 'text', label: t('COL_ARTICLE')},
      },
      {
        id: 'quantite', header: t('COL_QUANTITY'), valueAccessor: (row) => Number(row.quantite ?? 0), sortable: true,
        resizable: true, minWidthPx: 150, filter: {type: 'text', label: t('COL_QUANTITY')},
        cellTemplate: this.quantiteCellTemplate() as CellTemplate,
      },
      {
        id: 'pmp', header: t('COL_PMP'), valueAccessor: (row) => Number(row.pmpCourant ?? 0), sortable: true,
        resizable: true, minWidthPx: 140, filter: {type: 'text', label: t('COL_PMP')},
        cellTemplate: this.pmpCellTemplate() as CellTemplate,
      },
      {
        id: 'valeur', header: t('COL_VALUE'), valueAccessor: (row) => Number(row.valeur ?? 0), sortable: true,
        resizable: true, minWidthPx: 150, filter: {type: 'text', label: t('COL_VALUE')},
        cellTemplate: this.valeurCellTemplate() as CellTemplate,
      },
      {
        id: 'actions', header: t('COL_ACTIONS'), valueAccessor: () => '', sortable: false, resizable: false,
        mobileRowActions: true, exportable: false, minWidthPx: 200, widthPx: 220, maxWidthPx: 280,
        cellTemplate: this.actionsCellTemplate() as CellTemplate,
      },
    ];
  });
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
