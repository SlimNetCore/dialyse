import {ChangeDetectionStrategy, Component, input, output} from '@angular/core';
import {MatIconModule} from '@angular/material/icon';
import {TranslateModule} from '@ngx-translate/core';
import {SeanceListItem} from '../../../core/api/backend-api.service';
import {initials} from './station.util';

/**
 * Séances des jours précédents restées « À valider » (validation oubliée) : l'infirmier peut les rouvrir et les
 * valider. Tant qu'elles ne le sont pas, le patient est compté absent à la détection de la nuit.
 */
@Component({
  selector: 'app-pending-seances',
  standalone: true,
  imports: [MatIconModule, TranslateModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './pending-seances.component.html',
  styleUrl: './pending-seances.component.css',
})
export class PendingSeancesComponent {
  readonly seances = input.required<SeanceListItem[]>();
  readonly selectedId = input<string | null>(null);

  readonly opened = output<string>();

  protected readonly initials = initials;

  protected label(seance: SeanceListItem): string {
    return (`${(seance.patientNom ?? '').trim()} ${(seance.patientPrenom ?? '').trim()}`).trim()
      || seance.patientCode || seance.patientId;
  }
}
