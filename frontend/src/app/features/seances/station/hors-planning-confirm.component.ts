import {ChangeDetectionStrategy, Component, computed, input, output, signal} from '@angular/core';
import {compatForm} from '@angular/forms/signals/compat';
import {FormField, maxLength} from '@angular/forms/signals';
import {MatButtonModule} from '@angular/material/button';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatIconModule} from '@angular/material/icon';
import {MatInputModule} from '@angular/material/input';
import {MatSelectModule} from '@angular/material/select';
import {TranslateModule} from '@ngx-translate/core';
import {MotifHorsPlanning} from '../../../core/api/backend-api.service';
import {HorsPlanningCode} from '../state/seance.store';

export type HorsPlanningConfirmation = { motif: MotifHorsPlanning; precision: string | null };

/**
 * Scan d'un patient non programmé ce jour-là : explique pourquoi et laisse l'infirmier confirmer la séance en
 * déclarant un motif (rattrapage, urgence, autre + précision). Sans droit de confirmer (secrétaire, jour de fermeture
 * pour un infirmier), il se limite à l'explication.
 */
@Component({
  selector: 'app-hors-planning-confirm',
  standalone: true,
  imports: [FormField, MatButtonModule, MatFormFieldModule, MatIconModule, MatInputModule, MatSelectModule,
    TranslateModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './hors-planning-confirm.component.html',
  styleUrl: './hors-planning-confirm.component.css',
})
export class HorsPlanningConfirmComponent {
  readonly code = input.required<HorsPlanningCode>();
  /** L'utilisateur peut-il confirmer (infirmier ou administrateur ; administrateur seul un jour de fermeture) ? */
  readonly canConfirm = input(false);
  readonly busy = input(false);

  readonly confirmed = output<HorsPlanningConfirmation>();
  readonly dismissed = output<void>();

  protected readonly motifs: MotifHorsPlanning[] = ['RATTRAPAGE', 'URGENCE', 'AUTRE'];
  protected readonly model = signal<{ motif: MotifHorsPlanning; precision: string }>({
    motif: 'RATTRAPAGE', precision: '',
  });
  protected readonly form = compatForm(this.model, (f) => {
    maxLength(f.precision, 255);
  });
  protected readonly precisionRequise = computed(() => this.model().motif === 'AUTRE');
  protected readonly canSubmit = computed(() =>
    this.canConfirm() && !this.busy() && (!this.precisionRequise() || !!this.model().precision.trim()));

  protected submit(): void {
    if (!this.canSubmit()) return;
    const {motif, precision} = this.model();
    this.confirmed.emit({motif, precision: precision.trim() || null});
  }
}
