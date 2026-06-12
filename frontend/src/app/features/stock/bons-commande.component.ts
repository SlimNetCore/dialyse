import {ChangeDetectionStrategy, Component, inject, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormArray, FormBuilder, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
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
    CommonModule, ReactiveFormsModule, MatCardModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatButtonModule, MatIconModule, MatTableModule, MatChipsModule,
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
          <form [formGroup]="form" (ngSubmit)="save()">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Fournisseur</mat-label>
              <mat-select formControlName="fournisseurId">
                @for (f of fournisseurs(); track f.id) {
                  <mat-option [value]="f.id">{{ f.raisonSociale }}</mat-option>
                }
              </mat-select>
            </mat-form-field>

            <div formArrayName="lignes" class="lines">
              @for (ligne of lignes.controls; track $index) {
                <div [formGroupName]="$index" class="line-row">
                  <mat-form-field appearance="outline" class="flex2">
                    <mat-label>Article</mat-label>
                    <mat-select formControlName="articleId">
                      @for (a of articles(); track a.id) {
                        <mat-option [value]="a.id">{{ articleLabel(a) }}</mat-option>
                      }
                    </mat-select>
                  </mat-form-field>
                  <mat-form-field appearance="outline" class="flex1">
                    <mat-label>Quantité</mat-label>
                    <input matInput type="number" formControlName="quantite"/>
                  </mat-form-field>
                  <mat-form-field appearance="outline" class="flex1">
                    <mat-label>Prix unitaire</mat-label>
                    <input matInput type="number" formControlName="prixUnitaire"/>
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
              <button mat-flat-button color="primary" type="submit" [disabled]="form.invalid || saving()">
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
  private readonly api = inject(StockApiService);
  private readonly refApi = inject(ReferentialApiService);
  private readonly auth = inject(AuthStore);
  private readonly fb = inject(FormBuilder);
  protected readonly form: FormGroup = this.fb.group({
    fournisseurId: [null],
    lignes: this.fb.array([this.newLigne()]),
  });
  private readonly snack = inject(MatSnackBar);

  constructor() {
    this.reload();
  }

  get lignes(): FormArray {
    return this.form.get('lignes') as FormArray;
  }

  protected addLigne(): void {
    this.lignes.push(this.newLigne());
  }

  protected removeLigne(i: number): void {
    this.lignes.removeAt(i);
  }

  protected total(): number {
    return this.lignes.controls.reduce((sum, c) => {
      const q = Number(c.get('quantite')?.value ?? 0);
      const p = Number(c.get('prixUnitaire')?.value ?? 0);
      return sum + q * p;
    }, 0);
  }

  protected save(): void {
    const centerId = this.auth.centerId();
    if (!centerId || this.form.invalid) {
      return;
    }
    this.saving.set(true);
    this.api.createBonCommande({
      centerId,
      fournisseurId: this.form.value.fournisseurId ?? undefined,
      userId: this.auth.username() ?? undefined,
      lignes: this.lignes.value,
    }).subscribe({
      next: () => {
        this.snack.open('Bon de commande créé', 'OK', {duration: 2500});
        this.form.setControl('lignes', this.fb.array([this.newLigne()]));
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

  private newLigne(): FormGroup {
    return this.fb.group({
      articleId: [null, Validators.required],
      quantite: [1, [Validators.required, Validators.min(0.0001)]],
      prixUnitaire: [0, [Validators.required, Validators.min(0)]],
    });
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



