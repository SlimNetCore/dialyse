import {ChangeDetectionStrategy, Component, effect, inject, untracked} from '@angular/core';
import {DecimalPipe} from '@angular/common';
import {RouterLink} from '@angular/router';
import {MatCardModule} from '@angular/material/card';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {TranslateModule} from '@ngx-translate/core';
import {AuthStore} from '../../core/state/auth.store';
import {AppShellStore} from '../../core/state/app-shell.store';
import {AccueilStore} from './state/accueil.store';
import {tuilesVisibles} from './accueil-tuiles';

/**
 * Accueil du centre (URL /dashboard conservée) : une tuile par module accessible au profil, avec 1 ou 2 chiffres clés et
 * le lien vers le tableau de bord du module. Les indicateurs détaillés (PEC, attestations…) vivent dans ces tableaux de
 * bord ; l'Accueil n'en garde aucun en double.
 */
@Component({
  selector: 'app-center-dashboard',
  standalone: true,
  imports: [
    DecimalPipe,
    RouterLink,
    MatCardModule,
    MatIconModule,
    MatProgressSpinnerModule,
    TranslateModule,
  ],
  providers: [AccueilStore],
  templateUrl: './center-dashboard.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './center-dashboard.component.css',
})
export class CenterDashboardComponent {
  private readonly store = inject(AccueilStore);
  private readonly auth = inject(AuthStore);
  private readonly appShell = inject(AppShellStore);

  /** Tuiles du profil : un module absent du menu n'a pas de tuile. */
  readonly tuiles = tuilesVisibles((role) => this.auth.hasRole(role));
  readonly valeurs = this.store.valeurs;
  readonly chargement = this.store.chargement;
  readonly displayName = () => this.auth.fullName() || this.auth.username() || '';

  constructor() {
    effect(() => {
      const centerId = this.appShell.currentCenterId();
      if (!centerId) return;
      untracked(() => this.store.charger(centerId, this.tuiles.map((t) => t.module)));
    });
  }
}
