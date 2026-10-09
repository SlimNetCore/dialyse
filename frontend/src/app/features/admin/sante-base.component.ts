import {DatePipe, DecimalPipe} from '@angular/common';
import {ChangeDetectionStrategy, Component, inject, OnInit} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatTableModule} from '@angular/material/table';
import {TranslateModule} from '@ngx-translate/core';
import {formatTaille} from './performance-base.util';
import {PerformanceBaseStore} from './state/performance-base.store';

/**
 * Santé du moteur PostgreSQL (propriétaire) : taille, cache mémoire, connexions, plus grosses tables avec leur position
 * face aux seuils de partitionnement, index jamais utilisés, et points qui demandent attention.
 */
@Component({
  selector: 'app-sante-base',
  standalone: true,
  imports: [DatePipe, DecimalPipe, TranslateModule, MatTableModule, MatIconModule, MatButtonModule,
    MatProgressBarModule],
  templateUrl: './sante-base.component.html',
  styleUrl: './sante-base.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SanteBaseComponent implements OnInit {
  protected readonly store = inject(PerformanceBaseStore);
  protected readonly taille = formatTaille;
  protected readonly colonnesTables = ['nom', 'taille', 'lignes', 'mortes', 'scansComplets', 'scansIndex', 'partition'];
  protected readonly colonnesIndex = ['table', 'index', 'taille'];

  ngOnInit(): void {
    void this.store.chargerSante();
  }

  protected rafraichir(): void {
    void this.store.chargerSante();
  }
}
