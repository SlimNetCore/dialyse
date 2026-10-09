import {ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked} from '@angular/core';
import {compatForm} from '@angular/forms/signals/compat';
import {FormField, FormRoot, readonly, required} from '@angular/forms/signals';
import {MatCardModule} from '@angular/material/card';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatTableModule} from '@angular/material/table';
import {MatPaginatorModule, PageEvent} from '@angular/material/paginator';
import {MatSlideToggleModule} from '@angular/material/slide-toggle';
import {MatTooltipModule} from '@angular/material/tooltip';
import {MatDialog} from '@angular/material/dialog';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {CompteItem, PayeurCompteItem} from '../../core/api/comptabilite-api.service';
import {ConfirmDialogComponent} from '../../shared/confirm-dialog.component';
import {CompteSelectComponent} from './compte-select.component';
import {compteDuPlan, compteValide} from './plan-comptable.util';
import {PlanComptableStore} from './state/plan-comptable.store';

function compteVide() {
  return {numero: '', libelle: ''};
}

/**
 * Plan comptable du centre et compte client de chacun de ses payeurs. Un nouveau compte ou un nouveau client se
 * paramètre ici, sans développement : on ajoute le compte au plan, puis on l'affecte au payeur.
 */
@Component({
  selector: 'app-plan-comptable',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule, MatTableModule,
    MatPaginatorModule, MatSlideToggleModule, MatTooltipModule, FormRoot, FormField, TranslateModule,
    CompteSelectComponent,
  ],
  templateUrl: './plan-comptable.component.html',
  styleUrl: './comptabilite-parametrage.component.css',
})
export class PlanComptableComponent {
  protected readonly store = inject(PlanComptableStore);
  protected readonly columns = ['numero', 'libelle', 'actif', 'actions'];
  protected readonly payeurColumns = ['nom', 'compte'];

  /** Numéro du compte dont on modifie le libellé ; `null` = création. */
  protected readonly editingNumero = signal<string | null>(null);
  protected readonly compteModel = signal(compteVide());
  protected readonly compteForm = compatForm(this.compteModel, (form) => {
    required(form.numero);
    required(form.libelle);
    // le numéro identifie le compte : seul le libellé se modifie
    readonly(form.numero, {when: () => !!this.editingNumero()});
  });
  protected readonly canSave = computed(() =>
    compteValide(this.compteModel().numero, this.compteModel().libelle) && !this.store.saving());

  /** Compte en cours de saisie pour chaque payeur, avant validation. */
  protected readonly saisies = signal<Record<string, string>>({});

  private readonly dialog = inject(MatDialog);
  private readonly translate = inject(TranslateService);

  constructor() {
    effect(() => {
      if (this.store.successMessage()) untracked(() => this.cancel());
    });
    // une page de payeurs rechargée repart de ses comptes enregistrés
    effect(() => {
      this.store.payeurs();
      untracked(() => this.saisies.set({}));
    });
  }

  protected save(): void {
    if (!this.canSave()) return;
    const numero = this.compteModel().numero.trim();
    const existant = this.store.rows().find((c) => c.numero === numero);
    this.store.saveCompte({numero, libelle: this.compteModel().libelle.trim(), actif: existant?.actif ?? true});
  }

  protected edit(compte: CompteItem): void {
    this.store.clearMessages();
    this.editingNumero.set(compte.numero);
    this.compteModel.set({numero: compte.numero, libelle: compte.libelle});
  }

  protected cancel(): void {
    this.editingNumero.set(null);
    this.compteModel.set(compteVide());
  }

  protected toggleActif(compte: CompteItem, actif: boolean): void {
    this.store.saveCompte({...compte, actif});
  }

  protected remove(compte: CompteItem): void {
    const ref = this.dialog.open(ConfirmDialogComponent, {
      width: 'min(96vw, 460px)',
      data: {
        title: this.translate.instant('COMPTABILITE.PLAN.SUPPRIMER_TITRE'),
        message: this.translate.instant('COMPTABILITE.PLAN.SUPPRIMER_MESSAGE', {numero: compte.numero}),
        confirmLabel: this.translate.instant('COMMON.CONFIRM'),
        cancelLabel: this.translate.instant('COMMON.CANCEL'),
        color: 'warn',
        icon: 'delete',
      },
    });
    ref.afterClosed().subscribe((confirmed) => {
      if (confirmed) this.store.deleteCompte(compte.numero);
    });
  }

  protected onPage(event: PageEvent): void {
    this.store.setPagination({pageIndex: event.pageIndex, pageSize: event.pageSize});
  }

  protected onRecherche(texte: string): void {
    this.store.setRecherche(texte);
  }

  // ─── Compte client de chaque payeur ──────────────────────────────────────

  protected compteDe(payeur: PayeurCompteItem): string {
    return this.saisies()[payeur.payeurId] ?? payeur.compte ?? '';
  }

  protected saisir(payeur: PayeurCompteItem, compte: string): void {
    this.saisies.update((s) => ({...s, [payeur.payeurId]: compte}));
  }

  /** Le compte saisi diffère de l'enregistré et, s'il n'est pas vide, existe dans le plan. */
  protected peutEnregistrer(payeur: PayeurCompteItem): boolean {
    const saisi = this.compteDe(payeur);
    return saisi !== (payeur.compte ?? '') && !this.store.saving()
      && (!saisi || !!compteDuPlan(this.store.actifs(), saisi));
  }

  protected enregistrerPayeur(payeur: PayeurCompteItem): void {
    if (this.peutEnregistrer(payeur)) {
      this.store.saveComptePayeur({payeurId: payeur.payeurId, compte: this.compteDe(payeur)});
    }
  }

  protected onPagePayeurs(event: PageEvent): void {
    this.store.setPayeursPagination({pageIndex: event.pageIndex, pageSize: event.pageSize});
  }

  protected onRecherchePayeurs(texte: string): void {
    this.store.setPayeursRecherche(texte);
  }
}
