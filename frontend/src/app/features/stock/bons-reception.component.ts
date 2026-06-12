import {ChangeDetectionStrategy, Component, inject, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormArray, FormBuilder, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import {MatCardModule} from '@angular/material/card';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatSelectModule} from '@angular/material/select';
import {MatDatepickerModule} from '@angular/material/datepicker';
import {MatNativeDateModule} from '@angular/material/core';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatTableModule} from '@angular/material/table';
import {MatChipsModule} from '@angular/material/chips';
import {MatSnackBar} from '@angular/material/snack-bar';
import {MatTooltipModule} from '@angular/material/tooltip';
import {AuthStore} from '../../core/state/auth.store';
import {BonReception, Emplacement, Fournisseur, StockApiService} from '../../core/api/stock-api.service';
import {ReferentialApiService, RefItem} from '../../core/api/referential-api.service';

@Component({
  selector: 'app-bons-reception',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, MatCardModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatDatepickerModule, MatNativeDateModule, MatButtonModule, MatIconModule,
    MatTableModule, MatChipsModule, MatTooltipModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    {provide: MatNativeDateModule, useClass: MatNativeDateModule},
  ],
  template: `
    <section class="app-page">
      <div class="app-hero-card">
        <span class="app-eyebrow">Stock · Réception</span>
        <h1 class="app-section-title">Bons de réception</h1>
        <p class="app-section-copy">Saisie des lots (n° + péremption). La validation calcule le PMP en cascade.</p>
      </div>

      <mat-card>
        <mat-card-header>
          <mat-card-title>{{ editingId() ? 'Éditer' : 'Nouveau' }} bon de réception</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <form [formGroup]="form" (ngSubmit)="save()">
            <div class="head-row">
              <mat-form-field appearance="outline" class="flex2">
                <mat-label>Fournisseur</mat-label>
                <mat-select formControlName="fournisseurId">
                  @for (f of fournisseurs(); track f.id) {
                    <mat-option [value]="f.id">{{ f.raisonSociale }}</mat-option>
                  }
                </mat-select>
              </mat-form-field>
              <mat-form-field appearance="outline" class="flex1">
                <mat-label>Date de réception</mat-label>
                <input matInput [matDatepicker]="dp" formControlName="dateReception"/>
                <mat-datepicker-toggle matIconSuffix [for]="dp"></mat-datepicker-toggle>
                <mat-datepicker #dp></mat-datepicker>
              </mat-form-field>
            </div>

            <div formArrayName="lignes" class="lines">
              @for (ligne of lignes.controls; track $index) {
                <div [formGroupName]="$index" class="line-row">
                  <mat-form-field appearance="outline" class="flex2">
                    <mat-label>Article</mat-label>
                    <mat-select formControlName="articleId">
                      @for (a of articles(); track a.id) {
                        <mat-option [value]="a.id">{{ a.libelle || a.nom }}</mat-option>
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
                  <mat-form-field appearance="outline" class="flex1">
                    <mat-label>N° lot</mat-label>
                    <input matInput formControlName="numeroLot"/>
                  </mat-form-field>
                  <mat-form-field appearance="outline" class="flex1">
                    <mat-label>Péremption</mat-label>
                    <input matInput [matDatepicker]="dpl" formControlName="datePeremption"/>
                    <mat-datepicker-toggle matIconSuffix [for]="dpl"></mat-datepicker-toggle>
                    <mat-datepicker #dpl></mat-datepicker>
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
                Ajouter un lot
              </button>
              <div class="spacer"></div>
              @if (editingId()) {
                <button mat-stroked-button type="button" (click)="cancel()">Annuler</button>
              }
              <button mat-flat-button color="primary" type="submit" [disabled]="form.invalid || saving()">
                <mat-icon>save</mat-icon>
                {{ editingId() ? 'Mettre à jour' : 'Créer (brouillon)' }}
              </button>
            </div>
          </form>
        </mat-card-content>
      </mat-card>

      <mat-card>
        <mat-card-header>
          <mat-card-title>Bons de réception</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <table mat-table [dataSource]="bons()" class="full-width">
            <ng-container matColumnDef="reference">
              <th mat-header-cell *matHeaderCellDef>Référence</th>
              <td mat-cell *matCellDef="let b">{{ b.reference }}</td>
            </ng-container>
            <ng-container matColumnDef="date">
              <th mat-header-cell *matHeaderCellDef>Date</th>
              <td mat-cell *matCellDef="let b">{{ b.dateReception }}</td>
            </ng-container>
            <ng-container matColumnDef="statut">
              <th mat-header-cell *matHeaderCellDef>Statut</th>
              <td mat-cell *matCellDef="let b">
                <mat-chip>{{ b.statut }}</mat-chip>
              </td>
            </ng-container>
            <ng-container matColumnDef="lignes">
              <th mat-header-cell *matHeaderCellDef>Lots</th>
              <td mat-cell *matCellDef="let b">{{ b.lignes.length }}</td>
            </ng-container>
            <ng-container matColumnDef="actions">
              <th mat-header-cell *matHeaderCellDef>Actions</th>
              <td mat-cell *matCellDef="let b">
                <div class="action-buttons">
                  <button mat-icon-button (click)="edit(b)" matTooltip="Éditer">
                    <mat-icon>edit</mat-icon>
                  </button>
                  @if (b.statut === 'BROUILLON') {
                    <button mat-flat-button color="accent" (click)="valider(b)">
                      Valider
                    </button>
                  }
                </div>
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

    .head-row {
      display: flex;
      gap: 12px;
    }

    .lines {
      display: grid;
      gap: 8px;
      margin: 12px 0;
    }

    .line-row {
      display: flex;
      gap: 8px;
      align-items: center;
      flex-wrap: wrap;
    }

    .flex1 {
      flex: 1;
      min-width: 130px;
    }

    .flex2 {
      flex: 2;
      min-width: 200px;
    }

    mat-card {
      margin-top: 16px;
    }

    .action-buttons {
      display: flex;
      gap: 8px;
      flex-wrap: wrap;
    }

    .app-button-cluster {
      display: flex;
      gap: 12px;
      align-items: center;
      margin-top: 16px;
    }

    .spacer {
      flex: 1;
    }
  `],
})
export class BonsReceptionComponent {
  protected readonly cols = ['reference', 'date', 'statut', 'lignes', 'actions'];
  protected readonly bons = signal<BonReception[]>([]);
  protected readonly fournisseurs = signal<Fournisseur[]>([]);
  protected readonly emplacements = signal<Emplacement[]>([]);
  protected readonly articles = signal<RefItem[]>([]);
  protected readonly saving = signal(false);
  protected readonly editingId = signal<string | null>(null);
  private readonly api = inject(StockApiService);
  private readonly refApi = inject(ReferentialApiService);
  private readonly auth = inject(AuthStore);
  private readonly fb = inject(FormBuilder);
  protected readonly form: FormGroup = this.fb.group({
    fournisseurId: [null],
    dateReception: [new Date()],
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

  protected save(): void {
    const centerId = this.auth.centerId();
    if (!centerId || this.form.invalid) {
      return;
    }
    this.saving.set(true);
    const lignes = this.lignes.controls.map(c => ({
      articleId: c.get('articleId')!.value,
      quantite: Number(c.get('quantite')!.value),
      prixUnitaire: Number(c.get('prixUnitaire')!.value),
      numeroLot: c.get('numeroLot')!.value,
      datePeremption: this.toIso(c.get('datePeremption')!.value),
    }));

    const editingId = this.editingId();
    const saveOp = editingId
      ? this.api.updateBonReception(editingId, {
        centerId,
        fournisseurId: this.form.value.fournisseurId ?? undefined,
        dateReception: this.toIso(this.form.value.dateReception),
        lignes,
      })
      : this.api.createBonReception({
        centerId,
        fournisseurId: this.form.value.fournisseurId ?? undefined,
        dateReception: this.toIso(this.form.value.dateReception),
        userId: this.auth.username() ?? undefined,
        lignes,
      });

    saveOp.subscribe({
      next: () => {
        const message = editingId ? 'Bon de réception mis à jour' : 'Bon de réception créé';
        this.snack.open(message, 'OK', {duration: 2500});
        this.form.setControl('lignes', this.fb.array([this.newLigne()]));
        this.editingId.set(null);
        this.reload();
      },
      complete: () => this.saving.set(false),
      error: () => {
        this.saving.set(false);
        this.snack.open('Erreur lors de l\'opération', 'Fermer', {duration: 4000});
      },
    });
  }

  protected edit(b: BonReception): void {
    this.editingId.set(b.id);
    this.form.patchValue({
      fournisseurId: b.fournisseurId,
      dateReception: b.dateReception ? new Date(b.dateReception) : new Date(),
    });

    const lignesArray = this.fb.array(
      b.lignes.map(l =>
        this.fb.group({
          articleId: [l.articleId, Validators.required],
          quantite: [l.quantite, [Validators.required, Validators.min(0.0001)]],
          prixUnitaire: [l.prixUnitaire, [Validators.required, Validators.min(0)]],
          numeroLot: [l.numeroLot, Validators.required],
          datePeremption: [l.datePeremption ? new Date(l.datePeremption) : null, Validators.required],
        })
      )
    );
    this.form.setControl('lignes', lignesArray);

    // Scroller vers le formulaire
    setTimeout(() => {
      document.querySelector('.app-page')?.scrollIntoView({behavior: 'smooth'});
    }, 100);
  }

  protected cancel(): void {
    this.editingId.set(null);
    this.form.reset({
      fournisseurId: null,
      dateReception: new Date(),
      lignes: [this.newLigne()],
    });
  }


  protected valider(b: BonReception): void {
    const centerId = this.auth.centerId();
    if (!centerId) {
      return;
    }
    this.api.validerBonReception(b.id, centerId, this.auth.username() ?? undefined).subscribe({
      next: () => {
        this.snack.open('Réception validée · PMP recalculé', 'OK', {duration: 3000});
        this.reload();
      },
      error: () => this.snack.open('Erreur de validation', 'Fermer', {duration: 4000}),
    });
  }

  private newLigne(): FormGroup {
    return this.fb.group({
      articleId: [null, Validators.required],
      quantite: [1, [Validators.required, Validators.min(0.0001)]],
      prixUnitaire: [0, [Validators.required, Validators.min(0)]],
      numeroLot: ['', Validators.required],
      datePeremption: [null, Validators.required],
    });
  }

  private reload(): void {
    const centerId = this.auth.centerId();
    if (!centerId) {
      return;
    }
    this.api.listBonsReception(centerId).subscribe({next: (b) => this.bons.set(b)});
    this.api.listFournisseurs(centerId).subscribe({next: (f) => this.fournisseurs.set(f)});
    this.api.listEmplacements(centerId).subscribe({next: (e) => this.emplacements.set(e)});
    this.refApi.getArticles(centerId).subscribe({next: (a) => this.articles.set(a)});
  }

  private toIso(d: unknown): string | undefined {
    if (!d) {
      return undefined;
    }
    const date = d instanceof Date ? d : new Date(d as string);
    return date.toISOString().substring(0, 10);
  }
}

