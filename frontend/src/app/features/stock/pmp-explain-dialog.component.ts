import {ChangeDetectionStrategy, Component, computed, inject, LOCALE_ID, signal, TemplateRef, viewChild} from '@angular/core';
import {CommonModule, formatDate, formatNumber} from '@angular/common';
import {toSignal} from '@angular/core/rxjs-interop';
import {MAT_DIALOG_DATA, MatDialogModule, MatDialogRef} from '@angular/material/dialog';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {NgTableColumn, NgTableComponent, NgTableTone} from '@sbourahla/ng-table';
import {map, merge} from 'rxjs';
import {PmpExplanation, PmpExplanationStep, StockApiService} from '../../core/api/stock-api.service';
import {NgTableTranslatedLabels} from '../../shared/ng-table-labels';

export interface PmpExplainData {
  articleId: string;
  centerId: string;
  libelle: string;
}

/** Teinte de la pastille du type de mouvement. */
export const PMP_STEP_TONES: Record<PmpExplanationStep['type'], NgTableTone> = {
  ENTREE: 'success',
  SORTIE: 'warning',
  AJUSTEMENT: 'info',
};

/** Pagination locale des étapes du calcul (toutes chargées en une fois). */
export const PMP_STEPS_PAGE_SIZE = 20;
export const PMP_STEPS_PAGE_SIZES: readonly number[] = [10, 20, 50, 100];

type CellTemplate = NgTableColumn<PmpExplanationStep>['cellTemplate'];

/**
 * Explication du PMP d'un article : méthode, synthèse, puis les étapes du calcul en `<ng-table>` compacte (mode
 * local, paginée) ; type de mouvement en pastille, quantités et prix alignés à droite, formule en police fixe.
 */
@Component({
  selector: 'app-pmp-explain-dialog',
  standalone: true,
  imports: [
    CommonModule, MatDialogModule, MatButtonModule, MatIconModule,
    MatProgressBarModule, TranslateModule, NgTableComponent,
  ],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './pmp-explain-dialog.component.html',
  styleUrl: './pmp-explain-dialog.component.css',
})
export class PmpExplainDialogComponent {
  protected readonly data = inject<PmpExplainData>(MAT_DIALOG_DATA);
  protected readonly ref = inject(MatDialogRef<PmpExplainDialogComponent>);
  protected readonly loading = signal(true);
  protected readonly explanation = signal<PmpExplanation | null>(null);
  protected readonly ngTableLabels = inject(NgTableTranslatedLabels).labels;
  protected readonly pageSize = PMP_STEPS_PAGE_SIZE;
  protected readonly pageSizes = PMP_STEPS_PAGE_SIZES;
  private readonly api = inject(StockApiService);
  private readonly translate = inject(TranslateService);
  private readonly locale = inject(LOCALE_ID);
  private readonly lang = toSignal(
    merge(this.translate.onLangChange, this.translate.onTranslationChange).pipe(map(() => Date.now())),
    {initialValue: 0});
  private readonly numTemplate = viewChild<TemplateRef<unknown>>('numCell');
  private readonly formuleTemplate = viewChild<TemplateRef<unknown>>('formuleCell');

  protected readonly columns = computed<NgTableColumn<PmpExplanationStep>[]>(() => {
    this.lang();
    const t = (key: string): string => this.translate.instant(`STOCK.PMP_EXPLAIN.${key}`);
    const num = this.numTemplate() as CellTemplate;
    return [
      {id: 'piece', header: t('COL_PIECE'), valueAccessor: (s) => s.piece || '—'},
      {
        id: 'datePiece', header: t('COL_DATE_PIECE'),
        valueAccessor: (s) => (s.datePiece ? formatDate(s.datePiece, 'dd/MM/yyyy', this.locale) : '—'),
      },
      {id: 'type', header: t('COL_TYPE'), valueAccessor: (s) => s.type, cell: {type: 'badge', tone: PMP_STEP_TONES}},
      {id: 'qte', header: t('COL_QTE'), valueAccessor: (s) => this.number(s.quantite, '1.0-2'), cellTemplate: num},
      {
        id: 'pu', header: t('COL_PU'), cellTemplate: num,
        valueAccessor: (s) => (s.prixUnitaire != null ? this.number(s.prixUnitaire, '1.0-2') : '—'),
      },
      {id: 'pmp', header: t('COL_PMP_APRES'), valueAccessor: (s) => this.number(s.pmpApres, '1.0-4'), cellTemplate: num},
      {
        id: 'formule', header: t('COL_FORMULE'), valueAccessor: (s) => s.formule, textOverflow: 'wrap',
        cellTemplate: this.formuleTemplate() as CellTemplate,
      },
    ];
  });

  constructor() {
    this.api.pmpExplain(this.data.articleId, this.data.centerId).subscribe({
      next: (ex) => this.explanation.set(ex),
      complete: () => this.loading.set(false),
      error: () => this.loading.set(false),
    });
  }

  private number(value: number | null | undefined, digits: string): string {
    return value == null ? '—' : formatNumber(value, this.locale, digits);
  }
}
