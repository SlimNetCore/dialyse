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
  template: `
    <section class="app-page">
      <div class="app-hero-card">
        <span class="app-eyebrow">Stock · Approvisionnement</span>
        <h1 class="app-section-title">Bons de commande</h1>
      </div>

      <mat-card>
        <mat-card-header>
          <mat-card-title>Nouveau bon de commande</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <form [formRoot]="form" (submit)="save(); $event.preventDefault()">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Fournisseur</mat-label>
              <mat-select [formField]="form.fournisseurId">
                @for (f of fournisseurs(); track f.id) {
                  <mat-option [value]="f.id">{{ f.raisonSociale }}</mat-option>
                }
              </mat-select>
            </mat-form-field>

            <div class="lines">
              @for (ligne of formModel().lignes; track $index) {
                <div class="line-row">
                  <mat-form-field appearance="outline" class="flex2">
                    <mat-label>Article</mat-label>
                    <mat-select [formField]="form.lignes[$index].articleId">
                      @for (a of articles(); track a.id) {
                        <mat-option [value]="a.id">{{ articleLabel(a) }}</mat-option>
                      }
                    </mat-select>
                  </mat-form-field>
                  <mat-form-field appearance="outline" class="flex1">
                    <mat-label>Quantité</mat-label>
                    <input matInput type="number" [formField]="form.lignes[$index].quantite"/>
                  </mat-form-field>
                  <mat-form-field appearance="outline" class="flex1">
                    <mat-label>Prix unitaire</mat-label>
                    <input matInput type="number" [formField]="form.lignes[$index].prixUnitaire"/>
                  </mat-form-field>
                  <button mat-icon-button type="button" (click)="removeLigne($index)" aria-label="Supprimer">
                    <mat-icon>delete</mat-icon>
                  </button>
                </div>
              }
            </div>

            <div class="app-button-cluster">
              <button mat-stroked-button type="button" (click)="addLigne()">
                <mat-icon>add</mat-icon>
                Ajouter une ligne
              </button>
              <span class="total">Total : {{ total() | number:'1.0-2' }}</span>
              <button mat-flat-button color="primary" type="submit" [disabled]="!canSave()">
                <mat-icon>save</mat-icon>
                Créer le bon
              </button>
            </div>
          </form>
        </mat-card-content>
      </mat-card>

      <mat-card>
        <mat-card-header>
          <mat-card-title>Bons de commande</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <table mat-table [dataSource]="bons()" class="full-width">
            <ng-container matColumnDef="reference">
              <th mat-header-cell *matHeaderCellDef>Référence</th>
              <td mat-cell *matCellDef="let b">{{ b.reference }}</td>
            </ng-container>
            <ng-container matColumnDef="statut">
              <th mat-header-cell *matHeaderCellDef>Statut</th>
              <td mat-cell *matCellDef="let b">
                <mat-chip>{{ b.statut }}</mat-chip>
              </td>
            </ng-container>
            <ng-container matColumnDef="lignes">
              <th mat-header-cell *matHeaderCellDef>Lignes</th>
              <td mat-cell *matCellDef="let b">{{ b.lignes.length }}</td>
            </ng-container>
            <ng-container matColumnDef="actions">
              <th mat-header-cell *matHeaderCellDef>Actions</th>
              <td mat-cell *matCellDef="let b">
                @if (b.statut === 'BROUILLON') {
                  <button mat-stroked-button (click)="valider(b)">Valider</button>
                }
                @if (b.statut === 'VALIDE') {
                  <button mat-stroked-button (click)="transformer(b)">→ Réception</button>
                }
              </td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="cols"></tr>
            <tr mat-row *matRowDef="let row; columns: cols"></tr>
          </table>
        </mat-card-content>
      </mat-card>
    </section>
  `,
  styles: [`
    .full-width {
      width: 100%;
    }

    .lines {
      display: grid;
      gap: 8px;
      margin-bottom: 12px;
    }

    .line-row {
      display: flex;
      gap: 8px;
      align-items: center;
    }

    .flex1 {
      flex: 1;
    }

    .flex2 {
      flex: 2;
    }

    .total {
      font-weight: 800;
      margin-left: auto;
    }

    mat-card {
      margin-top: 16px;
    }
  `],
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



