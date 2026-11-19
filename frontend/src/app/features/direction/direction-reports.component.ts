import {ChangeDetectionStrategy, Component, computed, TemplateRef, viewChild} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {MatCardModule} from '@angular/material/card';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatIconModule} from '@angular/material/icon';
import {MatSelectModule} from '@angular/material/select';
import {TranslateModule} from '@ngx-translate/core';
import {DirectionTableComponent} from './direction-table.component';
import {
  compareColumns,
  compareRows,
  DirectionColumn,
  reportColumns,
  ReportRow,
} from './direction-tables.util';
import {DirectionViewBase} from './direction-view.base';
import {lastCompleteMonths} from './direction.util';

/**
 * Rapports de la direction (/direction/rapports) : rapports mensuels figés (PDF) des douze derniers mois écoulés et
 * comparaison de deux mois figés.
 */
@Component({
  selector: 'app-direction-reports',
  standalone: true,
  imports: [TranslateModule, MatCardModule, MatButtonModule, MatIconModule, MatFormFieldModule, MatSelectModule,
    DirectionTableComponent],
  templateUrl: './direction-reports.component.html',
  styleUrl: './direction.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DirectionReportsComponent extends DirectionViewBase {
  /** Douze derniers mois écoulés, avec leur instantané s'il est déjà figé. */
  protected readonly reportRows = computed<ReportRow[]>(() => {
    const frozen = new Map(this.store.snapshots().map((s) => [s.mois, s.generatedAt]));
    return lastCompleteMonths(new Date(), 12).map((mois) => ({mois, generatedAt: frozen.get(mois) ?? null}));
  });
  private readonly actionsCell = viewChild<TemplateRef<unknown>>('actionsCell');
  /** Mois, état (pastille) et bouton de téléchargement du rapport PDF. */
  protected readonly reportColumns = computed<DirectionColumn<ReportRow>[]>(() => {
    const ctx = this.tableContext();
    return [
      ...reportColumns(ctx),
      {
        id: 'actions', header: ctx.t('COMMON.COL_ACTIONS'), valueAccessor: () => '', sortable: false,
        minWidthPx: 190, exportable: false,
        cellTemplate: this.actionsCell() as DirectionColumn<ReportRow>['cellTemplate'],
      },
    ];
  });
  /** Mois déjà figés, sélectionnables dans le comparateur (un mois non figé n'a pas encore d'instantané). */
  protected readonly frozenMonths = computed(() =>
    [...this.store.snapshots()].map((s) => s.mois).sort().reverse());
  /**
   * Comparaison société des deux mois figés choisis (activité et finances) : une ligne par indicateur et ses
   * colonnes ; `null` tant que les deux mois ne sont pas chargés.
   */
  protected readonly compareTable = computed(() => {
    const a = this.store.compareA();
    const b = this.store.compareB();
    if (!a || !b) return null;
    const ctx = this.tableContext();
    return {rows: compareRows(ctx, a, b), columns: compareColumns(ctx, a.mois, b.mois)};
  });

  /** Fige le mois au besoin, puis enregistre le rapport PDF sur le poste. */
  protected async downloadReport(mois: string): Promise<void> {
    this.save(await this.store.report(mois), `rapport-direction-${mois}.pdf`);
  }

  /** Charge le mois choisi dans l'emplacement A ou B du comparateur de mois figés. */
  protected setCompareMonth(slot: 'a' | 'b', mois: string | null): void {
    if (mois) void this.store.loadCompare(slot, mois);
  }
}
