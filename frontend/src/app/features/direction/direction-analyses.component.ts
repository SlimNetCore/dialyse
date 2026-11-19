import {CurrencyPipe, DatePipe, DecimalPipe} from '@angular/common';
import {ChangeDetectionStrategy, Component} from '@angular/core';
import {MatButtonToggleModule} from '@angular/material/button-toggle';
import {MatCardModule} from '@angular/material/card';
import {TranslateModule} from '@ngx-translate/core';
import {BaseChartDirective} from 'ng2-charts';
import {DirectionViewBase} from './direction-view.base';

/**
 * Analyses de la direction (/direction/analyses) : activité et finances par centre, indicateurs cliniques KDIGO, stock
 * et groupes d'articles, GMAO, capacité théorique, absences de patients et répartitions (sexe, âge, caisses, anémie),
 * sur la période et le centre choisis dans la barre de filtres commune.
 */
@Component({
  selector: 'app-direction-analyses',
  standalone: true,
  imports: [CurrencyPipe, DatePipe, DecimalPipe, TranslateModule, MatCardModule, MatButtonToggleModule, BaseChartDirective],
  templateUrl: './direction-analyses.component.html',
  styleUrl: './direction.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DirectionAnalysesComponent extends DirectionViewBase {
}
