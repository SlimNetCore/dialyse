import {ChangeDetectionStrategy, Component, computed, inject, LOCALE_ID, TemplateRef, viewChild} from '@angular/core';
import {CommonModule, formatDate, formatNumber} from '@angular/common';
import {toSignal} from '@angular/core/rxjs-interop';
import {MAT_DIALOG_DATA, MatDialogModule, MatDialogRef} from '@angular/material/dialog';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {NgTableColumn, NgTableComponent, NgTableRemoteQuery, NgTableTone} from '@sbourahla/ng-table';
import {map, merge} from 'rxjs';
import {PmpExplanationStep} from '../../core/api/stock-api.service';
import {PageDExplicationDuPmp, StockPagesApiService} from '../../core/api/stock-pages-api.service';
import {ListeServeur} from '../../core/api/liste-serveur';
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

/** Pagination serveur des étapes du calcul (`GET /dashboard/pmp-explain/{articleId}/page`). */
export const PMP_STEPS_PAGE_SIZE = 20;
export const PMP_STEPS_PAGE_SIZES: readonly number[] = [10, 20, 50, 100];

type CellTemplate = NgTableColumn<PmpExplanationStep>['cellTemplate'];

/**
 * Explication du PMP d'un article : méthode, synthèse, puis les étapes du calcul en `<ng-table>` compacte (mode
 * distant, paginée par le serveur, sans tri ni filtre) ; type de mouvement en pastille, quantités et prix alignés à droite, formule en police fixe.
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
  protected readonly ngTableLabels = inject(NgTableTranslatedLabels).labels;
  protected readonly pageSizes = PMP_STEPS_PAGE_SIZES;
  private readonly api = inject(StockPagesApiService);
  private readonly translate = inject(TranslateService);
  private readonly locale = inject(LOCALE_ID);
  private readonly lang = toSignal(
    merge(this.translate.onLangChange, this.translate.onTranslationChange).pipe(map(() => Date.now())),
    {initialValue: 0});
  private readonly numTemplate = viewChild<TemplateRef<unknown>>('numCell');
  private readonly formuleTemplate = viewChild<TemplateRef<unknown>>('formuleCell');

  /** Colonnes ni triables ni filtrables : le serveur ne pagine que l'ordre chronologique du rejeu (contrat E5). */
  protected readonly columns = computed<NgTableColumn<PmpExplanationStep>[]>(() => {
    this.lang();
    const t = (key: string): string => this.translate.instant(`STOCK.PMP_EXPLAIN.${key}`);
    const num = this.numTemplate() as CellTemplate;
    const colonnes: NgTableColumn<PmpExplanationStep>[] = [
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
    return colonnes.map((c) => ({...c, sortable: false}));
  });

  /** Étapes du rejeu paginées par le serveur (RG-STK-093) ; le résumé accompagne chaque page. */
  protected readonly liste = new ListeServeur<PmpExplanationStep, PageDExplicationDuPmp>((p) =>
    this.api.explicationDuPmp(this.data.centerId, this.data.articleId, Number(p['page']), Number(p['size'])),
  {}, PMP_STEPS_PAGE_SIZE);
  protected readonly loading = this.liste.chargement;
  protected readonly explanation = this.liste.derniere;

  constructor() {
    this.liste.recharger();
  }

  /** Changement de page ou de taille demandé par la table (mode remote). */
  protected onRemoteQueryChange(requete: NgTableRemoteQuery): void {
    this.liste.onRemoteQueryChange(requete);
  }

  private number(value: number | null | undefined, digits: string): string {
    return value == null ? '—' : formatNumber(value, this.locale, digits);
  }
}
