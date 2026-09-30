import {ChangeDetectionStrategy, Component} from '@angular/core';
import {RouterOutlet} from '@angular/router';
import {InventaireBannerComponent} from './inventaire/inventaire-banner.component';

/** Mise en page du module stock : bandeau d'inventaire en cours au-dessus de chaque écran. */
@Component({
  selector: 'app-stock-layout',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, InventaireBannerComponent],
  template: `
    <app-inventaire-banner/>
    <router-outlet/>
  `,
})
export class StockLayoutComponent {
}

