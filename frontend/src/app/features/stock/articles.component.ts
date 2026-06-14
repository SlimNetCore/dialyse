import {ChangeDetectionStrategy, Component, computed, inject, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {compatForm} from '@angular/forms/signals/compat';
import {FormField, FormRoot, maxLength, min, required} from '@angular/forms/signals';
import {MatCardModule} from '@angular/material/card';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatTableModule} from '@angular/material/table';
import {MatSnackBar} from '@angular/material/snack-bar';
import {MatCheckboxModule} from '@angular/material/checkbox';
import {AuthStore} from '../../core/state/auth.store';
import {StockApiService} from '../../core/api/stock-api.service';
import {ReferentialApiService, RefItem} from '../../core/api/referential-api.service';

@Component({
  selector: 'app-stock-articles',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatTableModule,
    MatCheckboxModule,
    FormRoot,
    FormField,
  ],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    <section class="app-page">
      <div class="app-hero-card">
        <span class="app-eyebrow">Stock · Référentiel</span>
        <h1 class="app-section-title">Saisie des articles</h1>
        <p class="app-section-copy">Définir si l'article est géré par lot. Ce paramètre pilote la saisie obligatoire du lot en réception.</p>
      </div>

      <mat-card>
        <mat-card-header>
          <mat-card-title>Nouvel article</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <form [formRoot]="articleForm" (submit)="save(); $event.preventDefault()">
            <div class="form-grid">
              <mat-form-field appearance="outline">
                <mat-label>Code</mat-label>
                <input matInput [formField]="articleForm.code"/>
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Libellé</mat-label>
                <input matInput [formField]="articleForm.libelle"/>
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Unité</mat-label>
                <input matInput [formField]="articleForm.unite"/>
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Seuil d'alerte</mat-label>
                <input matInput type="number" [formField]="articleForm.seuilAlerte"/>
              </mat-form-field>
            </div>

            <mat-checkbox [formField]="articleForm.gereParLot">
              Article géré par lot (lot et péremption obligatoires en réception)
            </mat-checkbox>

            <div class="actions">
              <button mat-flat-button color="primary" type="submit" [disabled]="!canSave()">
                <mat-icon>save</mat-icon>
                Créer l'article
              </button>
            </div>
          </form>
        </mat-card-content>
      </mat-card>

      <mat-card>
        <mat-card-header>
          <mat-card-title>Articles actifs</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <table mat-table [dataSource]="articles()" class="full-width">
            <ng-container matColumnDef="code">
              <th mat-header-cell *matHeaderCellDef>Code</th>
              <td mat-cell *matCellDef="let a">{{ a.code }}</td>
            </ng-container>
            <ng-container matColumnDef="libelle">
              <th mat-header-cell *matHeaderCellDef>Libellé</th>
              <td mat-cell *matCellDef="let a">{{ a.libelle || a.nom }}</td>
            </ng-container>
            <ng-container matColumnDef="unite">
              <th mat-header-cell *matHeaderCellDef>Unité</th>
              <td mat-cell *matCellDef="let a">{{ a.unite || '—' }}</td>
            </ng-container>
            <ng-container matColumnDef="lot">
              <th mat-header-cell *matHeaderCellDef>Gestion lot</th>
              <td mat-cell *matCellDef="let a">{{ a.gereParLot ? 'Oui' : 'Non' }}</td>
            </ng-container>

            <tr mat-header-row *matHeaderRowDef="cols"></tr>
            <tr mat-row *matRowDef="let row; columns: cols"></tr>
          </table>
        </mat-card-content>
      </mat-card>
    </section>
  `,
  styles: [`
    .form-grid {
      display: grid;
      gap: 12px;
      grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
      margin-bottom: 8px;
    }

    .actions {
      margin-top: 16px;
      display: flex;
      justify-content: flex-end;
    }

    .full-width {
      width: 100%;
    }

    mat-card {
      margin-top: 16px;
    }
  `],
})
export class ArticlesComponent {
  protected readonly cols = ['code', 'libelle', 'unite', 'lot'];
  protected readonly articles = signal<RefItem[]>([]);
  protected readonly saving = signal(false);
  protected readonly formModel = signal(this.createInitialForm());
  protected readonly articleForm = compatForm(this.formModel, (form) => {
    required(form.code);
    maxLength(form.code, 50);
    required(form.libelle);
    maxLength(form.libelle, 150);
    required(form.unite);
    maxLength(form.unite, 30);
    min(form.seuilAlerte, 0);
  });
  protected readonly canSave = computed(() => {
    const form = this.formModel();
    return !!form.code.trim() && !!form.libelle.trim() && !!form.unite.trim() && form.seuilAlerte >= 0 && !this.saving();
  });

  private readonly api = inject(StockApiService);
  private readonly refApi = inject(ReferentialApiService);
  private readonly auth = inject(AuthStore);
  private readonly snack = inject(MatSnackBar);

  constructor() {
    this.reload();
  }

  protected save(): void {
    const centerId = this.auth.centerId();
    const form = this.formModel();
    if (!centerId || !this.canSave()) {
      return;
    }
    this.saving.set(true);
    this.api.createArticle({
      centerId,
      code: form.code.trim(),
      libelle: form.libelle.trim(),
      unite: form.unite.trim(),
      seuilAlerte: Number(form.seuilAlerte ?? 0),
      gereParLot: !!form.gereParLot,
    }).subscribe({
      next: () => {
        this.snack.open('Article créé', 'OK', {duration: 2500});
        this.formModel.set(this.createInitialForm());
        this.reload();
      },
      error: () => this.snack.open('Erreur lors de la création', 'Fermer', {duration: 4000}),
      complete: () => this.saving.set(false),
    });
  }

  private reload(): void {
    const centerId = this.auth.centerId();
    if (!centerId) {
      return;
    }
    this.refApi.getArticles(centerId).subscribe({next: items => this.articles.set(items)});
  }

  private createInitialForm() {
    return {
      code: '',
      libelle: '',
      unite: '',
      seuilAlerte: 0,
      gereParLot: true,
    };
  }
}

