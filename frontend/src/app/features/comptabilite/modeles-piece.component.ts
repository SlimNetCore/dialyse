import {ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked} from '@angular/core';
import {compatForm} from '@angular/forms/signals/compat';
import {FormField, FormRoot, required} from '@angular/forms/signals';
import {RouterLink} from '@angular/router';
import {MatCardModule} from '@angular/material/card';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatSelectModule} from '@angular/material/select';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatTableModule} from '@angular/material/table';
import {MatPaginatorModule, PageEvent} from '@angular/material/paginator';
import {MatSlideToggleModule} from '@angular/material/slide-toggle';
import {MatTooltipModule} from '@angular/material/tooltip';
import {MatDialog} from '@angular/material/dialog';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {CompteItem, JournalItem, ModelePieceItem, SensEcriture} from '../../core/api/comptabilite-api.service';
import {ConfirmDialogComponent} from '../../shared/confirm-dialog.component';
import {CompteSelectComponent} from './compte-select.component';
import {erreursModele, LIGNES_MODELE_MAX, modeleDepuis, ModeleFormModel, modeleVide} from './plan-comptable.util';
import {PiecesComptablesStore} from './state/pieces-comptables.store';

/**
 * Modèles de pièces du centre : chaque modèle fixe le journal et les lignes (sens et compte) d'un type de pièce —
 * loyer, salaires, facture fournisseur, opération diverse… Créer un nouveau type de pièce ne demande qu'un modèle.
 */
@Component({
  selector: 'app-modeles-piece',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink, MatCardModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule, MatIconModule,
    MatTableModule, MatPaginatorModule, MatSlideToggleModule, MatTooltipModule, TranslateModule, CompteSelectComponent,
    FormRoot, FormField,
  ],
  templateUrl: './modeles-piece.component.html',
  styleUrl: './comptabilite-parametrage.component.css',
})
export class ModelesPieceComponent {
  /** Comptes actifs du plan et journaux actifs du centre, fournis par l'écran de paramétrage. */
  readonly comptes = input.required<readonly CompteItem[]>();
  readonly journaux = input.required<readonly JournalItem[]>();

  protected readonly store = inject(PiecesComptablesStore);
  protected readonly columns = ['code', 'libelle', 'journal', 'lignes', 'actif', 'actions'];
  protected readonly sens: SensEcriture[] = ['DEBIT', 'CREDIT'];
  protected readonly lignesMax = LIGNES_MODELE_MAX;

  /** Identifiant du modèle modifié ; `null` = création. */
  protected readonly editingId = signal<string | null>(null);
  protected readonly modele = signal<ModeleFormModel>(modeleVide());
  /** En-tête du modèle en formulaire signal ; les lignes, de nombre variable, se pilotent par le même signal. */
  protected readonly modeleForm = compatForm(this.modele, (form) => {
    required(form.code);
    required(form.libelle);
  });
  protected readonly erreurs = computed(() =>
    erreursModele(this.modele(), this.comptes(), this.journaux().map((j) => j.code)));
  protected readonly canSave = computed(() => this.erreurs().length === 0 && !this.store.saving());

  private readonly dialog = inject(MatDialog);
  private readonly translate = inject(TranslateService);

  constructor() {
    this.store.load();
    effect(() => {
      if (this.store.successMessage()) untracked(() => this.cancel());
    });
  }

  protected patch(changement: Partial<ModeleFormModel>): void {
    this.modele.update((m) => ({...m, ...changement}));
  }

  protected patchLigne(index: number, changement: Partial<ModeleFormModel['lignes'][number]>): void {
    this.modele.update((m) => ({
      ...m, lignes: m.lignes.map((l, i) => (i === index ? {...l, ...changement} : l)),
    }));
  }

  protected ajouterLigne(): void {
    this.modele.update((m) => ({...m, lignes: [...m.lignes, {sens: 'DEBIT', compte: '', libelle: ''}]}));
  }

  protected retirerLigne(index: number): void {
    this.modele.update((m) => ({...m, lignes: m.lignes.filter((_, i) => i !== index)}));
  }

  protected save(): void {
    if (!this.canSave()) return;
    const m = this.modele();
    this.store.saveModele({
      id: this.editingId(),
      modele: {
        code: m.code.trim().toUpperCase(), libelle: m.libelle.trim(), journal: m.journal, actif: m.actif,
        lignes: m.lignes.map((l) => ({sens: l.sens, compte: l.compte.trim(), libelle: l.libelle?.trim() || null})),
      },
    });
  }

  protected edit(row: ModelePieceItem): void {
    this.store.clearMessages();
    this.editingId.set(row.id);
    this.modele.set(modeleDepuis(row));
  }

  protected cancel(): void {
    this.editingId.set(null);
    this.modele.set(modeleVide());
  }

  protected toggleActif(row: ModelePieceItem, actif: boolean): void {
    const {id, ...modele} = row;
    this.store.saveModele({id, modele: {...modele, actif}});
  }

  protected remove(row: ModelePieceItem): void {
    const ref = this.dialog.open(ConfirmDialogComponent, {
      width: 'min(96vw, 460px)',
      data: {
        title: this.translate.instant('COMPTABILITE.MODELES.SUPPRIMER_TITRE'),
        message: this.translate.instant('COMPTABILITE.MODELES.SUPPRIMER_MESSAGE', {code: row.code}),
        confirmLabel: this.translate.instant('COMMON.CONFIRM'),
        cancelLabel: this.translate.instant('COMMON.CANCEL'),
        color: 'warn',
        icon: 'delete',
      },
    });
    ref.afterClosed().subscribe((confirmed) => {
      if (confirmed) this.store.deleteModele(row.id);
    });
  }

  protected onPage(event: PageEvent): void {
    this.store.setPagination({pageIndex: event.pageIndex, pageSize: event.pageSize});
  }
}
