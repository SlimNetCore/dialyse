import {ChangeDetectionStrategy, Component, input, linkedSignal, output} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {TranslateModule} from '@ngx-translate/core';
import {ArticleStock} from '../../../core/api/backend-api.service';
import {SHORTCUTS_MAX} from './station.util';

/**
 * Choix, par l'administrateur, des articles proposés en un toucher à l'infirmier : on touche les articles dans l'ordre
 * souhaité (l'ordre de sélection est l'ordre d'affichage), dans la limite du serveur.
 */
@Component({
  selector: 'app-shortcuts-config',
  standalone: true,
  imports: [MatButtonModule, TranslateModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './shortcuts-config.component.html',
  styleUrl: './shortcuts-config.component.css',
})
export class ShortcutsConfigComponent {
  /** Articles actifs que l'on peut proposer. */
  readonly articles = input.required<ArticleStock[]>();
  /** Raccourcis actuellement enregistrés pour le centre (ordre d'affichage). */
  readonly selected = input<string[]>([]);
  readonly saving = input(false);

  readonly saved = output<string[]>();
  readonly cancelled = output<void>();

  protected readonly max = SHORTCUTS_MAX;
  /** Sélection en cours d'édition ; repart de la liste enregistrée quand elle change. */
  protected readonly draft = linkedSignal(() => [...this.selected()]);

  protected toggle(articleId: string): void {
    const draft = this.draft();
    if (draft.includes(articleId)) {
      this.draft.set(draft.filter((id) => id !== articleId));
    } else if (draft.length < SHORTCUTS_MAX) {
      this.draft.set([...draft, articleId]);
    }
  }
}
