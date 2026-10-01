import {ChangeDetectionStrategy, Component, inject, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {RouterLink} from '@angular/router';
import {MatIconModule} from '@angular/material/icon';
import {MatButtonModule} from '@angular/material/button';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {TranslateModule} from '@ngx-translate/core';
import {GmaoApiService, GmaoStats} from '../../../../core/api/gmao-api.service';

/**
 * Tableau de bord GMAO : statistiques agrégées calculées côté serveur (jamais en rapatriant
 * les listes complètes — AGENTS.md §9) et accès rapides aux équipements/interventions.
 */
@Component({
  selector: 'app-gmao-dashboard',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, RouterLink, MatIconModule, MatButtonModule, MatProgressBarModule, TranslateModule],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css', '../../gmao-shared.css'],
})
export class GmaoDashboardComponent {
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly stats = signal<GmaoStats | null>(null);
  private readonly api = inject(GmaoApiService);

  constructor() {
    this.reload();
  }

  protected reload(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.getStats().subscribe({
      next: (stats) => {
        this.stats.set(stats);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('GMAO.DASHBOARD.LOAD_ERROR');
        this.loading.set(false);
      },
    });
  }
}
