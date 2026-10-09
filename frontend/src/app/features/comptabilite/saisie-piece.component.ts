import {ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked} from '@angular/core';
import {DecimalPipe} from '@angular/common';
import {ActivatedRoute, RouterLink} from '@angular/router';
import {toSignal} from '@angular/core/rxjs-interop';
import {compatForm} from '@angular/forms/signals/compat';
import {FormField, FormRoot, required} from '@angular/forms/signals';
import {MatCardModule} from '@angular/material/card';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatSelectModule} from '@angular/material/select';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {TranslateModule} from '@ngx-translate/core';
import {map} from 'rxjs';
import {AppShellStore} from '../../core/state/app-shell.store';
import {AuthStore} from '../../core/state/auth.store';
import {toLocalIsoDate} from '../../shared/date.util';
import {montantSaisi, totauxPiece} from './plan-comptable.util';
import {PiecesComptablesStore} from './state/pieces-comptables.store';
import {PlanComptableStore} from './state/plan-comptable.store';

/**
 * Saisie d'une pièce à partir d'un modèle du centre : on choisit le modèle, on saisit la date, le libellé et un
 * montant par ligne ; la pièce n'est enregistrable qu'équilibrée.
 */
@Component({
  selector: 'app-saisie-piece',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DecimalPipe, RouterLink, MatCardModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule,
    MatIconModule, MatProgressBarModule, FormRoot, FormField, TranslateModule,
  ],
  templateUrl: './saisie-piece.component.html',
  styleUrl: './comptabilite-parametrage.component.css',
})
export class SaisiePieceComponent {
  protected readonly store = inject(PiecesComptablesStore);
  protected readonly plan = inject(PlanComptableStore);
  protected readonly modeleId = signal<string | null>(null);
  protected readonly modele = computed(() => this.store.actifs().find((m) => m.id === this.modeleId()) ?? null);
  protected readonly enteteModel = signal({date: toLocalIsoDate(new Date()), libelle: ''});
  protected readonly enteteForm = compatForm(this.enteteModel, (form) => {
    required(form.date);
  });
  /** Montant saisi pour chaque ligne du modèle, dans l'ordre. */
  protected readonly montants = signal<string[]>([]);
  protected readonly totaux = computed(() => totauxPiece(this.modele()?.lignes ?? [], this.montants()));
  protected readonly canSave = computed(() =>
    !!this.modele() && !!this.enteteModel().date && this.totaux().valide && !this.store.saving());
  private readonly shell = inject(AppShellStore);
  private readonly auth = inject(AuthStore);
  protected readonly peutParametrer = computed(() => this.auth.hasRole('ADMIN'));
  /** Modèle demandé par l'adresse (`?modele=…`), depuis la liste des modèles. */
  private readonly modeleDemande = toSignal(
    inject(ActivatedRoute).queryParamMap.pipe(map((p) => p.get('modele'))), {initialValue: null});

  constructor() {
    // les modèles et le plan sont ceux du centre actif
    effect(() => {
      this.shell.currentCenterId();
      untracked(() => {
        this.store.loadActifs();
        this.plan.loadActifs();
      });
    });
    // présélection du modèle demandé, dès que la liste est chargée
    effect(() => {
      const demande = this.modeleDemande();
      const actifs = this.store.actifs();
      untracked(() => {
        if (!this.modeleId() && demande && actifs.some((m) => m.id === demande)) this.choisir(demande);
      });
    });
    // pièce enregistrée : on repart d'une saisie vierge du même modèle
    effect(() => {
      if (this.store.dernierePiece()) untracked(() => this.vider());
    });
  }

  protected choisir(id: string): void {
    this.modeleId.set(id);
    this.vider();
  }

  protected libelleCompte(numero: string): string {
    return this.plan.libelles()[numero] ?? '';
  }

  /** Montant saisi d'une ligne (vide tant que rien n'est saisi). */
  protected montant(index: number): string {
    return this.montants().at(index) ?? '';
  }

  protected saisirMontant(index: number, texte: string): void {
    this.montants.update((m) => m.map((valeur, i) => (i === index ? texte : valeur)));
  }

  protected montantInvalide(index: number): boolean {
    return montantSaisi(this.montant(index)) === null;
  }

  protected save(): void {
    const modele = this.modele();
    if (!modele || !this.canSave()) return;
    this.store.saisirPiece({
      modeleId: modele.id,
      date: this.enteteModel().date,
      libelle: this.enteteModel().libelle.trim() || null,
      montants: this.montants().map((m) => montantSaisi(m) ?? 0),
    });
  }

  private vider(): void {
    this.montants.set((this.modele()?.lignes ?? []).map(() => ''));
    this.enteteModel.update((e) => ({...e, libelle: ''}));
  }
}
