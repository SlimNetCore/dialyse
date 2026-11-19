import {ChangeDetectionStrategy, Component, effect, inject, untracked} from '@angular/core';
import {DatePipe, DecimalPipe, SlicePipe} from '@angular/common';
import {RouterLink} from '@angular/router';
import {MatButtonModule} from '@angular/material/button';
import {MatCardModule} from '@angular/material/card';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {TranslateModule} from '@ngx-translate/core';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {HORIZON_COUVERTURE, InfirmiersTableauDeBordStore} from './infirmiers-tableau-de-bord.store';

/**
 * Tableau de bord des infirmiers (`/infirmiers/tableau-de-bord`, lecture seule) : couverture de la semaine (créneaux en
 * sous-effectif, chacun menant à la semaine concernée dans la grille de présence), synthèse de la charge du mois et
 * absences en cours. Les écrans de travail (présence, absences, charge) restent inchangés ; l'infirmier seul n'y a pas
 * accès (roleScopeGuard).
 */
@Component({
  selector: 'app-infirmiers-tableau-de-bord',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe, DecimalPipe, SlicePipe, RouterLink, MatButtonModule, MatCardModule, MatIconModule, MatProgressBarModule,
    TranslateModule,
  ],
  providers: [InfirmiersTableauDeBordStore],
  templateUrl: './infirmiers-tableau-de-bord.component.html',
  styleUrl: './infirmiers-tableau-de-bord.component.css',
})
export class InfirmiersTableauDeBordComponent {
  protected readonly store = inject(InfirmiersTableauDeBordStore);
  protected readonly horizon = HORIZON_COUVERTURE;
  private readonly shell = inject(AppShellStore);

  constructor() {
    // Recharge à l'ouverture et à chaque changement de centre actif.
    effect(() => {
      this.shell.currentCenterId();
      untracked(() => this.store.charger());
    });
  }
}
