import {ChangeDetectionStrategy, Component, computed, inject, input, TemplateRef, viewChild} from '@angular/core';
import {NgTableCellFormat, NgTableCellFormatComponent, NgTableColumn, NgTableComponent} from '@sbourahla/ng-table';
import {cellFormatOf, cellTextOf, DirectionColumn, isTotalRow} from './direction-tables.util';
import {NgTableTranslatedLabels} from '../../shared/ng-table-labels';

/**
 * Tableau d'analyse de la direction : un `<ng-table>` en mode local (données déjà en mémoire), sans barre d'outils,
 * dont les cellules sont celles prêtes à l'emploi de ng-table (avatar, pastille, barre de données, progression,
 * dégradé) déclarées par `cell` sur chaque colonne. Le gabarit commun ne fait qu'y écrire le texte mis en forme
 * (dinars, effectif masqué, « — ») que ng-table ne sait pas produire seul, et les lignes de total, épinglées en bas,
 * restent hors du tri.
 */
@Component({
  selector: 'app-direction-table',
  standalone: true,
  imports: [NgTableComponent, NgTableCellFormatComponent],
  template: `
    <ng-table [ariaLabel]="label()" [columns]="tableColumns()" [columnsMenuEnabled]="false" [labels]="labels()"
              [pinnedBottomRows]="totals()" [rowClassFn]="rowClassFn()" [rows]="tableRows()"
              [showResetFilters]="false" density="compact"/>

    <ng-template #cell let-column="column" let-row let-value="value">
      @let text = textOf(column, row);
      @let format = formatOf(column, row, value, text);
      <span [class]="cellClass(column, row, format)">
        @if (format) {
          <!-- Taux : la barre de ng-table borne aria-valuenow à 0-100 alors qu'un taux peut dépasser 100 % ; elle
               est donc décorative, la valeur exacte écrite à côté étant celle que lit un lecteur d'écran. -->
          <ng-table-cell-format [attr.aria-hidden]="format.type === 'progress' ? 'true' : null" [format]="format"
                                [label]="column.header" [row]="row" [text]="text" [value]="value"/>
          @if (format.type === 'progress') {
            <span class="dt-rate">{{ text }}</span>
          }
        } @else {
          {{ text }}
        }
      </span>
    </ng-template>
  `,
  styles: `
    :host {
      display: block;
      min-width: 0;
    }

    .dt-cell {
      display: block;
      min-width: 0;
    }

    .dt-num {
      text-align: end;
      font-variant-numeric: tabular-nums;
    }

    .dt-strong {
      font-weight: 700;
    }

    .dt-good {
      color: #2e7d32;
    }

    .dt-warn {
      color: #ed6c02;
    }

    .dt-bad {
      color: #c62828;
    }

    .dt-progress {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .dt-progress ng-table-cell-format {
      flex: 1 1 auto;
    }

    .dt-rate {
      flex: 0 0 auto;
      min-width: 4.4em;
      text-align: end;
      font-weight: 600;
    }

    :host ::ng-deep tr.direction-total td {
      font-weight: 700;
    }

    :host ::ng-deep tr.direction-inactive td {
      opacity: 0.55;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DirectionTableComponent<T> {
  readonly columns = input.required<readonly DirectionColumn<T>[]>();
  /** Lignes triables (un centre, un motif, un mois…). */
  readonly rows = input.required<readonly T[]>();
  /** Lignes de total, épinglées en bas : ni triées, ni comptées dans l'échelle des barres. */
  readonly totals = input<readonly T[]>([]);
  /** Nom accessible du tableau. */
  readonly label = input<string | null>(null);
  /** Lignes à estomper (centre inactif). */
  readonly inactive = input<((row: T) => boolean) | null>(null);

  /** Textes de ng-table (tri, tableau vide…) dans la langue de l'application. */
  protected readonly labels = inject(NgTableTranslatedLabels).labels;
  /** Lignes que les barres de données comparent entre elles : celles du tableau, hors totaux. */
  private readonly comparedRows = computed(() => this.rows().filter((row) => !isTotalRow(row)).length);
  private readonly cellTemplate = viewChild<TemplateRef<unknown>>('cell');
  /** Colonnes ng-table : le gabarit commun, sauf pour une colonne qui apporte le sien (boutons d'action). */
  protected readonly tableColumns = computed<NgTableColumn<T>[]>(() => {
    const template = this.cellTemplate() as NgTableColumn<T>['cellTemplate'];
    return this.columns().map((column) => (column.cellTemplate || !template ? column : {...column, cellTemplate: template}));
  });
  protected readonly tableRows = computed<T[]>(() => [...this.rows()]);
  protected readonly rowClassFn = computed(() => {
    const inactive = this.inactive();
    return (row: T): Record<string, boolean> => ({
      'direction-total': isTotalRow(row),
      'direction-inactive': !!inactive?.(row),
    });
  });

  protected textOf(column: DirectionColumn<T>, row: T): string {
    return cellTextOf(column, row);
  }

  protected formatOf(column: DirectionColumn<T>, row: T, value: unknown, text: string): NgTableCellFormat<T> | null {
    return cellFormatOf(column, row, value, text, this.comparedRows());
  }

  protected cellClass(column: DirectionColumn<T>, row: T, format: NgTableCellFormat<T> | null): string {
    const emphasis = column.emphasis?.(row);
    return [
      'dt-cell',
      column.numeric ? 'dt-num' : '',
      column.strong ? 'dt-strong' : '',
      emphasis ? `dt-${emphasis}` : '',
      format ? `dt-${format.type}` : '',
    ].filter(Boolean).join(' ');
  }
}
