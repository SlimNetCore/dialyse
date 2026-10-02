import {ChangeDetectionStrategy, Component, effect, inject, untracked} from '@angular/core';
import {RouterLink} from '@angular/router';
import {MatButtonModule} from '@angular/material/button';
import {MatCardModule} from '@angular/material/card';
import {MatIconModule} from '@angular/material/icon';
import {MatPaginatorModule, PageEvent} from '@angular/material/paginator';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatTooltipModule} from '@angular/material/tooltip';
import {TranslateModule} from '@ngx-translate/core';
import {AppShellStore} from '../../core/state/app-shell.store';
import {SallesGenerateursStore} from './salles.store';
import {etatCapacite, nbEnService, tauxRemplissage} from './salles.util';

/**
 * Vision globale des salles du centre : pour chacune, ses générateurs affectés (avec leur statut), sa capacité et les
 * places restantes. Une salle à sa capacité refuse tout nouveau générateur ; une salle au-delà de sa capacité (capacité
 * réduite après affectation) est signalée.
 */
@Component({
  selector: 'app-salles-generateurs',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink, MatButtonModule, MatCardModule, MatIconModule, MatPaginatorModule, MatProgressBarModule,
    MatTooltipModule, TranslateModule,
  ],
  templateUrl: './salles-generateurs.component.html',
  styleUrl: './salles-generateurs.component.css',
})
export class SallesGenerateursComponent {
  protected readonly store = inject(SallesGenerateursStore);
  protected readonly etat = etatCapacite;
  protected readonly taux = tauxRemplissage;
  protected readonly enService = nbEnService;
  private readonly shell = inject(AppShellStore);

  constructor() {
    // Rechargement à l'ouverture et à chaque changement de centre actif.
    effect(() => {
      this.shell.currentCenterId();
      untracked(() => this.store.loadPage({page: 0, size: this.store.pageSize()}));
    });
  }

  protected onPage(event: PageEvent): void {
    this.store.setPagination(event.pageIndex, event.pageSize);
  }
}
