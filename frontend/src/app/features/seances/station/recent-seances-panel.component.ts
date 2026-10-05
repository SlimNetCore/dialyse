import {ChangeDetectionStrategy, Component, computed, input} from '@angular/core';
import {TranslateModule} from '@ngx-translate/core';
import {SeanceRecent} from '../../../core/api/backend-api.service';
import {weightLossKg} from './station.util';

/** Rappel des dernières séances du patient ouvert : poids avant → après, perte, tension et durée. */
@Component({
  selector: 'app-recent-seances-panel',
  standalone: true,
  imports: [TranslateModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './recent-seances-panel.component.html',
  styleUrl: './recent-seances-panel.component.css',
})
export class RecentSeancesPanelComponent {
  readonly seances = input.required<SeanceRecent[]>();
  /** Séance ouverte, reflétant en direct les constantes en cours de saisie (affichée en tête). */
  readonly current = input<SeanceRecent | null>(null);

  protected readonly items = computed(() => {
    const live = this.current();
    return live ? [live, ...this.seances().filter((s) => s.seanceId !== live.seanceId)] : this.seances();
  });

  protected readonly weightLossKg = weightLossKg;

  protected statusKey(status: string): string {
    return `SEANCES.STATION.STATUS_${status}`;
  }
}
