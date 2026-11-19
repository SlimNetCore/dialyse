import {ChangeDetectionStrategy, Component, input} from '@angular/core';
import {TranslateModule} from '@ngx-translate/core';
import {FACTURATION_VALIDEE} from '../state/facturation.store';

/** Étape du cycle de facturation : 1 Sélection, 2 Aperçu, 3 Validation (RG-FAC-049). */
export type EtapeDeFacturation = 1 | 2 | 3;

/**
 * Étape courante déduite de l'état de l'écran de calcul : un aperçu calculé et non validé ⇒ Aperçu ; une validation
 * réussie (aperçu effacé, message de succès) ⇒ Validation ; sinon Sélection.
 */
export function etapeCourante(etat: { preview: unknown | null; successMessage: string | null }): EtapeDeFacturation {
  if (etat.preview) return 2;
  if (etat.successMessage === FACTURATION_VALIDEE) return 3;
  return 1;
}

const ETAPES: ReadonlyArray<{ numero: EtapeDeFacturation; cle: string }> = [
  {numero: 1, cle: 'SELECT'},
  {numero: 2, cle: 'PREVIEW'},
  {numero: 3, cle: 'VALIDATE'},
];

/**
 * Indicateur compact des trois étapes de la facturation, sur une seule ligne. L'explication de chaque étape est en
 * info-bulle native (`title`, pour la souris) et dans un texte masqué visuellement à l'intérieur de la pastille, lu une
 * seule fois par les lecteurs d'écran (pas d'`aria-describedby` qui la ferait annoncer deux fois). Les pastilles ne
 * sont pas des commandes : aucun arrêt de tabulation. Sur un écran étroit (≤ 480 px), seul le titre de l'étape
 * courante reste affiché (les autres titres restent lus, les numéros et info-bulles demeurent) ; la ligne défile
 * horizontalement plutôt que de couper une étape.
 */
@Component({
  selector: 'app-facturation-etapes',
  standalone: true,
  imports: [TranslateModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ol class="etapes" [attr.aria-label]="'FACTURATION.FLOW.TITLE' | translate">
      @for (e of etapes; track e.numero; let dernier = $last) {
        <li class="etape"
            [class.etape--courante]="e.numero === etape()"
            [class.etape--faite]="e.numero < etape()"
            [attr.aria-current]="e.numero === etape() ? 'step' : null"
            [attr.title]="('FACTURATION.FLOW.' + e.cle + '.DESC') | translate">
          <span class="etape__numero" aria-hidden="true">{{ e.numero }}</span>
          <span class="etape__titre">{{ ('FACTURATION.FLOW.' + e.cle + '.TITLE') | translate }}</span>
          <span class="visually-hidden etape__description">
            {{ ('FACTURATION.FLOW.' + e.cle + '.DESC') | translate }}
          </span>
        </li>
        @if (!dernier) {
          <li class="etape__fleche" aria-hidden="true">›</li>
        }
      }
    </ol>
  `,
  styles: [`
    :host { display: block; min-width: 0; max-width: 100%; }
    .etapes {
      display: flex; flex-wrap: nowrap; align-items: center; gap: 6px;
      margin: 0; padding: 2px 0; list-style: none; overflow-x: auto; scrollbar-width: thin;
    }
    .etape {
      flex: 0 0 auto; display: inline-flex; align-items: center; gap: 6px; height: 32px; padding: 0 12px 0 4px;
      border: 1px solid var(--app-border); border-radius: 999px; background: var(--app-surface);
      color: var(--app-muted); font-size: 0.85rem; cursor: help; white-space: nowrap;
    }
    .etape__numero {
      display: inline-flex; align-items: center; justify-content: center; width: 24px; height: 24px;
      border-radius: 999px; background: var(--app-primary-soft, rgba(0, 106, 106, 0.14)); font-weight: 700;
    }
    .etape--faite { color: var(--app-text); }
    .etape--courante {
      border-color: var(--app-primary); color: var(--app-text); font-weight: 600;
      background: color-mix(in srgb, var(--app-surface) 88%, var(--app-primary-soft) 12%);
    }
    .etape--courante .etape__numero { background: var(--app-primary); color: var(--mat-sys-on-primary, #ffffff); }
    .etape__fleche { flex: 0 0 auto; color: var(--app-muted); font-weight: 700; }
    :host-context([dir='rtl']) .etape__fleche { transform: scaleX(-1); }
    :host-context([dir='rtl']) .etape { padding: 0 4px 0 12px; }
    @media (max-width: 480px) {
      .etape:not(.etape--courante) { padding: 0 4px; }
      /* masqué visuellement seulement : toujours lu par les lecteurs d'écran */
      .etape:not(.etape--courante) .etape__titre {
        position: absolute; width: 1px; height: 1px; margin: -1px; overflow: hidden; clip: rect(0 0 0 0);
        white-space: nowrap;
      }
    }
    .etape { position: relative; }
    .visually-hidden {
      position: absolute; width: 1px; height: 1px; margin: -1px; padding: 0; overflow: hidden;
      clip: rect(0 0 0 0); white-space: nowrap; border: 0;
    }
  `],
})
export class FacturationEtapesComponent {
  readonly etape = input<EtapeDeFacturation>(1);
  protected readonly etapes = ETAPES;
}
