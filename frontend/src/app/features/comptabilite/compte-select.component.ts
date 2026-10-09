import {ChangeDetectionStrategy, Component, computed, input, output, signal} from '@angular/core';
import {MatAutocompleteModule} from '@angular/material/autocomplete';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {TranslateModule} from '@ngx-translate/core';
import {CompteItem} from '../../core/api/comptabilite-api.service';
import {compteDuPlan, filtrerComptes} from './plan-comptable.util';

/**
 * Choix d'un compte dans le plan comptable du centre : on tape un numéro ou un libellé, on choisit dans la liste. Le
 * libellé du compte choisi s'affiche sous le champ ; un numéro absent du plan est signalé.
 */
@Component({
  selector: 'app-compte-select',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatFormFieldModule, MatInputModule, MatAutocompleteModule, TranslateModule],
  template: `
    <mat-form-field appearance="outline" class="compte-select">
      <mat-label>{{ label() }}</mat-label>
      <input (input)="onInput($any($event.target).value)" [attr.data-testid]="testId() || null"
             [disabled]="disabled()" [matAutocomplete]="auto" [value]="value()" autocomplete="off" inputmode="numeric"
             matInput/>
      <mat-autocomplete #auto="matAutocomplete" (optionSelected)="onSelect($event.option.value)">
        @for (c of propositions(); track c.numero) {
          <mat-option [value]="c.numero">{{ c.numero }} — {{ c.libelle }}</mat-option>
        }
      </mat-autocomplete>
      @if (inconnu()) {
        <mat-hint class="compte-select-erreur">{{ 'COMPTABILITE.PLAN.COMPTE_HORS_PLAN' | translate }}</mat-hint>
      } @else if (manquant()) {
        <mat-hint class="compte-select-erreur">{{ 'COMPTABILITE.PLAN.COMPTE_REQUIS' | translate }}</mat-hint>
      } @else if (compte(); as choisi) {
        <mat-hint>{{ choisi.libelle }}</mat-hint>
      } @else if (facultatif()) {
        <mat-hint>{{ aideVide() }}</mat-hint>
      }
    </mat-form-field>
  `,
  styles: `
    .compte-select {
      width: 100%;
    }

    .compte-select-erreur {
      color: #c62828;
    }
  `,
})
export class CompteSelectComponent {
  readonly label = input.required<string>();
  /** Comptes actifs du plan du centre. */
  readonly comptes = input.required<readonly CompteItem[]>();
  readonly value = input<string>('');
  /** Le champ peut rester vide. */
  readonly facultatif = input(false);
  /** Texte d'aide affiché quand un champ facultatif est vide. */
  readonly aideVide = input('');
  readonly disabled = input(false);
  readonly testId = input('');
  readonly valueChange = output<string>();
  protected readonly compte = computed(() => compteDuPlan(this.comptes(), this.value()));
  protected readonly inconnu = computed(() => !!this.value().trim() && !this.compte());
  protected readonly manquant = computed(() => !this.facultatif() && !this.value().trim());
  /** Texte en cours de frappe, pour filtrer les propositions sans attendre que le parent le renvoie. */
  private readonly frappe = signal<string | null>(null);
  protected readonly propositions = computed(() => filtrerComptes(this.comptes(), this.frappe() ?? ''));

  protected onInput(texte: string): void {
    this.frappe.set(texte);
    this.valueChange.emit(texte.trim());
  }

  protected onSelect(numero: string): void {
    this.frappe.set(null);
    this.valueChange.emit(numero);
  }
}
