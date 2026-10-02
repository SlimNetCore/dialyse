import {ChangeDetectionStrategy, Component, effect, inject, untracked} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {MatCardModule} from '@angular/material/card';
import {MatIconModule} from '@angular/material/icon';
import {MatPaginatorModule, PageEvent} from '@angular/material/paginator';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatTableModule} from '@angular/material/table';
import {TranslateModule} from '@ngx-translate/core';
import {AppShellStore} from '../../core/state/app-shell.store';
import {PresenceStore} from './presence.store';
import {decalerMois} from './presence.util';

/**
 * Charge de travail mensuelle des infirmiers : séances du roulement, remplacements effectués et jours d'absence, avec
 * l'écart à la moyenne du centre pour répartir équitablement les remplacements.
 */
@Component({
  selector: 'app-charge-infirmiers',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    MatButtonModule, MatCardModule, MatIconModule, MatPaginatorModule, MatProgressBarModule, MatTableModule,
    TranslateModule,
  ],
  templateUrl: './charge.component.html',
  styleUrl: './infirmiers.component.css',
})
export class ChargeInfirmiersComponent {
  protected readonly store = inject(PresenceStore);
  protected readonly columns = ['infirmier', 'seances', 'remplacements', 'absence', 'total', 'ecart'];
  private readonly shell = inject(AppShellStore);

  constructor() {
    effect(() => {
      this.shell.currentCenterId();
      untracked(() => this.charger(this.store.chargeMois(), 0, this.store.chargePageSize()));
    });
  }

  /** Écart du total à la moyenne du centre (une décimale). */
  protected ecart(total: number): number {
    return Math.round((total - this.store.chargeMoyenne()) * 10) / 10;
  }

  protected changerMois(delta: number): void {
    this.charger(decalerMois(this.store.chargeMois(), delta), 0, this.store.chargePageSize());
  }

  protected onPage(event: PageEvent): void {
    this.charger(this.store.chargeMois(), event.pageIndex, event.pageSize);
  }

  private charger(mois: string, page: number, size: number): void {
    this.store.chargerCharge({mois, page, size});
  }
}
