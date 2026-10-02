import {ChangeDetectionStrategy, Component, effect, inject, untracked} from '@angular/core';
import {DatePipe} from '@angular/common';
import {RouterLink} from '@angular/router';
import {MatButtonModule} from '@angular/material/button';
import {MatCardModule} from '@angular/material/card';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatTooltipModule} from '@angular/material/tooltip';
import {TranslateModule} from '@ngx-translate/core';
import {AppShellStore} from '../../core/state/app-shell.store';
import {MedecinDashboardStore} from './medecin-dashboard.store';

/**
 * Tableau de bord du médecin : planning du jour de tous les infirmiers et de tous les patients, salle par salle et
 * créneau par créneau. Les créneaux en sous-effectif d'infirmiers sont signalés ; chaque patient renvoie à sa fiche
 * (consultation) et à son dossier médical.
 */
@Component({
  selector: 'app-medecin-dashboard',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe, RouterLink, MatButtonModule, MatCardModule, MatIconModule, MatProgressBarModule,
    MatTooltipModule, TranslateModule,
  ],
  templateUrl: './medecin-dashboard.component.html',
  styleUrl: './medecin-dashboard.component.css',
})
export class MedecinDashboardComponent {
  protected readonly store = inject(MedecinDashboardStore);
  private readonly shell = inject(AppShellStore);

  constructor() {
    // Recharge le planning du jour à l'ouverture et à chaque changement de centre actif.
    effect(() => {
      this.shell.currentCenterId();
      untracked(() => this.store.charger());
    });
  }
}
