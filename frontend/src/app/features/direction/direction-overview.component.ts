import {CurrencyPipe, DatePipe} from '@angular/common';
import {ChangeDetectionStrategy, Component} from '@angular/core';
import {MatCardModule} from '@angular/material/card';
import {MatIconModule} from '@angular/material/icon';
import {TranslateModule} from '@ngx-translate/core';
import {BaseChartDirective} from 'ng2-charts';
import {DirectionViewBase} from './direction-view.base';

/**
 * Vue d'ensemble de la direction (/direction) : alertes actives et leur historique, KPI de la société (ou du centre
 * isolé) avec leurs deltas, graphiques du CA par centre et de sa tendance mensuelle.
 */
@Component({
  selector: 'app-direction-overview',
  standalone: true,
  imports: [CurrencyPipe, DatePipe, TranslateModule, MatCardModule, MatIconModule, BaseChartDirective],
  templateUrl: './direction-overview.component.html',
  styleUrl: './direction.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DirectionOverviewComponent extends DirectionViewBase {
}
