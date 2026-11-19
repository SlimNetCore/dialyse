import {DatePipe} from '@angular/common';
import {ChangeDetectionStrategy, Component, computed} from '@angular/core';
import {MatButtonToggleModule} from '@angular/material/button-toggle';
import {MatCardModule} from '@angular/material/card';
import {TranslateModule} from '@ngx-translate/core';
import {BaseChartDirective} from 'ng2-charts';
import {CentreStats} from '../../core/api/direction-api.service';
import {DirectionTableComponent} from './direction-table.component';
import {
  absencesColumns,
  absencesMensuelColumns,
  agesColumns,
  anemieColumns,
  caissesColumns,
  capaciteColumns,
  centresColumns,
  clinicalColumns,
  gmaoColumns,
  gmaoTopColumns,
  motifsColumns,
  sexeColumns,
  stockColumns,
  stockGroupesColumns,
} from './direction-tables.util';
import {DirectionViewBase} from './direction-view.base';

/** Sépare les lignes de centre de la ligne de total, épinglée en bas du tableau (hors tri et hors échelle des barres). */
function split<R extends { total: boolean }>(all: readonly R[]): { rows: R[]; totals: R[] } {
  return {rows: all.filter((r) => !r.total), totals: all.filter((r) => r.total)};
}

/**
 * Analyses de la direction (/direction/analyses) : activité et finances par centre, indicateurs cliniques KDIGO, stock
 * et groupes d'articles, GMAO, capacité théorique, absences de patients et répartitions (sexe, âge, caisses, anémie),
 * sur la période et le centre choisis dans la barre de filtres commune. Les tableaux sont des `<ng-table>` dont les
 * colonnes (cellules prêtes à l'emploi) sont décrites dans `direction-tables.util.ts`.
 */
@Component({
  selector: 'app-direction-analyses',
  standalone: true,
  imports: [DatePipe, TranslateModule, MatCardModule, MatButtonToggleModule, BaseChartDirective,
    DirectionTableComponent],
  templateUrl: './direction-analyses.component.html',
  styleUrl: './direction.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DirectionAnalysesComponent extends DirectionViewBase {
  protected readonly centreInactif = (centre: CentreStats): boolean => !centre.actif;
  protected readonly centresColumns = computed(() => centresColumns(this.tableContext(), this.displayCentres(),
    (centerId) => this.rankOf(centerId), (centerId) => this.caShare(centerId)));
  /** Indicateurs par centre et total société, pour les tableaux cliniques et de stock. */
  protected readonly indicatorTable = computed(() => {
    const {rows, totals} = split(this.indicatorRows());
    const ctx = this.tableContext();
    return {rows, totals, clinical: clinicalColumns(ctx, rows), stock: stockColumns(ctx, rows)};
  });
  protected readonly gmaoTable = computed(() => {
    const {rows, totals} = split(this.gmaoRows());
    return {rows, totals, columns: gmaoColumns(this.tableContext(), rows)};
  });
  protected readonly gmaoTopColumns = computed(() => gmaoTopColumns(this.tableContext(), this.gmaoTopRows()));
  protected readonly capaciteTable = computed(() => {
    const {rows, totals} = split(this.capaciteRows());
    return {rows, totals, columns: capaciteColumns(this.tableContext(), rows)};
  });
  protected readonly absencesTable = computed(() => {
    const {rows, totals} = split(this.absencesRows());
    return {rows, totals, columns: absencesColumns(this.tableContext(), rows)};
  });
  protected readonly motifsColumns = computed(() => motifsColumns(this.tableContext(), this.absencesMotifs()));
  protected readonly absencesMensuel = computed(() => this.store.absences()?.mensuel ?? []);
  protected readonly absencesMensuelColumns = computed(() =>
    absencesMensuelColumns(this.tableContext(), this.absencesMensuel()));
  protected readonly stockGroupesColumns = computed(() =>
    stockGroupesColumns(this.tableContext(), this.stockGroupeRows()));
  protected readonly sexeColumns = computed(() => sexeColumns(this.tableContext(), this.breakdown()?.sexe ?? []));
  protected readonly agesColumns = computed(() =>
    agesColumns(this.tableContext(), this.breakdown()?.ages ?? [], this.ageCodes));
  protected readonly caissesColumns = computed(() =>
    caissesColumns(this.tableContext(), this.pivotCentres(), this.caissePivot(), this.caisseMetric()));
  protected readonly anemieColumns = computed(() => anemieColumns(this.tableContext(), this.breakdown()?.anemie ?? []));
}
