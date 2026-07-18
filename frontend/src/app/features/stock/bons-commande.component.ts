import {ChangeDetectionStrategy, Component, computed, inject, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {compatForm} from '@angular/forms/signals/compat';
import {applyEach, FormField, FormRoot, min, required} from '@angular/forms/signals';
import {MatCardModule} from '@angular/material/card';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatSelectModule} from '@angular/material/select';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatTableModule} from '@angular/material/table';
import {MatChipsModule} from '@angular/material/chips';
import {MatSnackBar} from '@angular/material/snack-bar';
import {AuthStore} from '../../core/state/auth.store';
import {BonCommande, Fournisseur, StockApiService} from '../../core/api/stock-api.service';
import {ReferentialApiService, RefItem} from '../../core/api/referential-api.service';

@Component({
  selector: 'app-bons-commande',
  standalone: true,
  imports: [
    CommonModule, MatCardModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatButtonModule, MatIconModule, MatTableModule, MatChipsModule,
    FormRoot, FormField,
  ],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './bons-commande.component.html',
  styleUrl: './bons-commande.component.css',
})
export class BonsCommandeComponent {
  protected readonly cols = ['reference', 'statut', 'lignes', 'actions'];
  protected readonly bons = signal<BonCommande[]>([]);
  protected readonly fournisseurs = signal<Fournisseur[]>([]);
  protected readonly articles = signal<RefItem[]>([]);
  protected readonly saving = signal(false);
  protected readonly formModel = signal({
    fournisseurId: null as string | null,
    lignes: [this.newLigne()],
  });
  protected readonly form = compatForm(this.formModel, (form) => {
    applyEach(form.lignes, (ligne) => {
      required(ligne.articleId);
      required(ligne.quantite);
      required(ligne.prixUnitaire);
      min(ligne.quantite, 0.0001);
      min(ligne.prixUnitaire, 0);
    });
  });
  protected readonly canSave = computed(() => {
    if (this.saving()) return false;
    const model = this.formModel();
    if (!model.lignes.length) return false;
    return model.lignes.every((l) => !!l.articleId && Number(l.quantite) > 0 && Number(l.prixUnitaire) >= 0);
  });
  private readonly api = inject(StockApiService);
  private readonly refApi = inject(ReferentialApiService);
  private readonly auth = inject(AuthStore);
  private readonly snack = inject(MatSnackBar);

  constructor() {
    this.reload();
  }

  protected addLigne(): void {
    this.formModel.update((model) => ({...model, lignes: [...model.lignes, this.newLigne()]}));
  }

  protected removeLigne(i: number): void {
    this.formModel.update((model) => ({
      ...model,
      lignes: model.lignes.filter((_, idx) => idx !== i),
    }));
  }

  protected total(): number {
    return this.formModel().lignes.reduce((sum, c) => {
      const q = Number(c.quantite ?? 0);
      const p = Number(c.prixUnitaire ?? 0);
      return sum + q * p;
    }, 0);
  }

  protected save(): void {
    const centerId = this.auth.centerId();
    const form = this.formModel();
    if (!centerId || !this.canSave()) {
      return;
    }
    const lignes = form.lignes
      .filter((l) => !!l.articleId)
      .map((l) => ({
        articleId: String(l.articleId),
        quantite: Number(l.quantite),
        prixUnitaire: Number(l.prixUnitaire),
      }));
    this.saving.set(true);
    this.api.createBonCommande({
      centerId,
      fournisseurId: form.fournisseurId ?? undefined,
      userId: this.auth.username() ?? undefined,
      lignes,
    }).subscribe({
      next: () => {
        this.snack.open('Bon de commande créé', 'OK', {duration: 2500});
        this.formModel.set({fournisseurId: null, lignes: [this.newLigne()]});
        this.reload();
      },
      complete: () => this.saving.set(false),
      error: () => {
        this.saving.set(false);
        this.snack.open('Erreur lors de la création', 'Fermer', {duration: 4000});
      },
    });
  }

  protected valider(b: BonCommande): void {
    const centerId = this.auth.centerId();
    if (!centerId) {
      return;
    }
    this.api.validerBonCommande(b.id, centerId, this.auth.username() ?? undefined).subscribe({
      next: () => {
        this.snack.open('Bon validé', 'OK', {duration: 2000});
        this.reload();
      },
      error: () => this.snack.open('Erreur de validation', 'Fermer', {duration: 4000}),
    });
  }

  protected transformer(b: BonCommande): void {
    const centerId = this.auth.centerId();
    if (!centerId) {
      return;
    }
    this.api.fromBonCommande({centerId, bonCommandeId: b.id, userId: this.auth.username() ?? undefined}).subscribe({
      next: () => {
        this.snack.open('Bon de réception créé (brouillon)', 'OK', {duration: 3000});
        this.reload();
      },
      error: () => this.snack.open('Erreur de transformation', 'Fermer', {duration: 4000}),
    });
  }

  private newLigne() {
    return {
      articleId: null as string | null,
      quantite: 1,
      prixUnitaire: 0,
    };
  }

  private reload(): void {
    const centerId = this.auth.centerId();
    if (!centerId) {
      return;
    }
    this.api.listBonsCommande(centerId).subscribe({next: (b) => this.bons.set(b)});
    this.api.listFournisseurs(centerId).subscribe({next: (f) => this.fournisseurs.set(f)});
    this.refApi.getArticles(centerId).subscribe({next: (a) => this.articles.set(a)});
  }

  protected articleLabel(a: RefItem): string {
    const code = (a.code ?? '').trim();
    const libelle = (a.libelle ?? a.nom ?? '').trim();
    const unite = (a.unite ?? '').trim();
    const left = code ? `${code} - ${libelle}` : libelle;
    return unite ? `${left} (${unite})` : left;
  }
}



