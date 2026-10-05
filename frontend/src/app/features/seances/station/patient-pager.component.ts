import {ChangeDetectionStrategy, Component, computed, input, output} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {TranslateModule} from '@ngx-translate/core';

/**
 * Navigation avant/arrière entre les patients de la file du jour, sans repasser par la liste. `position` est l'index
 * de la séance ouverte dans la file (-1 si elle n'en fait pas partie : rien n'est affiché).
 */
@Component({
  selector: 'app-patient-pager',
  standalone: true,
  imports: [MatButtonModule, MatIconModule, TranslateModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './patient-pager.component.html',
  styleUrl: './patient-pager.component.css',
})
export class PatientPagerComponent {
  readonly position = input.required<number>();
  readonly total = input.required<number>();

  readonly previous = output<void>();
  readonly next = output<void>();

  protected readonly visible = computed(() => this.position() >= 0 && this.total() > 1);
  protected readonly hasPrevious = computed(() => this.position() > 0);
  protected readonly hasNext = computed(() => this.position() < this.total() - 1);
}
