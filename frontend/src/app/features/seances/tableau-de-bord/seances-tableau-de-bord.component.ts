import {ChangeDetectionStrategy, Component} from '@angular/core';
import {MatIconModule} from '@angular/material/icon';
import {TranslateModule} from '@ngx-translate/core';
import {SeancesStatsComponent} from './seances-stats.component';
import {SeancesTableauDeBordStore} from './seances-tableau-de-bord.store';

/**
 * Tableau de bord du module Séances (/seances/tableau-de-bord) : statistiques mensuelles des séances, sorties de
 * l'historique (devenu une liste seule). Le magasin est propre à la page.
 * Traçabilité : ce composant → SeancesStatsComponent → SeancesTableauDeBordStore → GET /api/v1/seances/dashboard.
 */
@Component({
  selector: 'app-seances-tableau-de-bord',
  standalone: true,
  imports: [MatIconModule, TranslateModule, SeancesStatsComponent],
  providers: [SeancesTableauDeBordStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="app-page seances-tdb">
      <header class="app-hero-card tdb-hero">
        <span class="app-eyebrow">
          <mat-icon aria-hidden="true">insights</mat-icon>
          {{ 'NAV.SEANCES' | translate }}
        </span>
        <h1 class="app-section-title">{{ 'SEANCES_TABLEAU_DE_BORD.TITLE' | translate }}</h1>
        <p class="app-section-copy">{{ 'SEANCES_TABLEAU_DE_BORD.DESC' | translate }}</p>
      </header>

      <app-seances-stats/>
    </section>
  `,
  styles: `
    :host {
      display: block;
      min-width: 0;
    }

    .seances-tdb {
      grid-template-columns: minmax(0, 1fr);
      gap: 18px;
    }

    .seances-tdb > * {
      min-width: 0;
    }

    .tdb-hero {
      display: grid;
      gap: 10px;
    }

    .tdb-hero h1 {
      margin: 0;
    }
  `,
})
export class SeancesTableauDeBordComponent {
}
