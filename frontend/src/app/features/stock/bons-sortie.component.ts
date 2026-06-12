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
import {MatSnackBar} from '@angular/material/snack-bar';
import {AuthStore} from '../../core/state/auth.store';
import {BonSortie, StockApiService} from '../../core/api/stock-api.service';
import {ReferentialApiService, RefItem} from '../../core/api/referential-api.service';

@Component({
  selector: 'app-bons-sortie',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, MatCardModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatDatepickerModule, MatNativeDateModule, MatButtonModule, MatIconModule, MatTableModule,
  ],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    <section class="app-page">
      <div class="app-hero-card">
        <span class="app-eyebrow">Stock · Sortie</span>
        <h1 class="app-section-title">Bons de sortie</h1>
        <p class="app-section-copy">Sortie FEFO (lot à péremption la plus proche), liée à une séance d'hémodialyse.</p>
      </div>

      <mat-card>
        <mat-card-header>
          <mat-card-title>Nouvelle sortie</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <form [formGroup]="form" (ngSubmit)="save()">
            <div class="head-row">
              <mat-form-field appearance="outline" class="flex2">
                <mat-label>Séance (UUID)</mat-label>
                <input matInput formControlName="seanceId" required/>
              </mat-form-field>
              <mat-form-field appearance="outline" class="flex2">
                <mat-label>Patient (UUID)</mat-label>
                <input matInput formControlName="patientId"/>
              </mat-form-field>
              <mat-form-field appearance="outline" class="flex1">
                <mat-label>Poste</mat-label>
                <input matInput formControlName="poste"/>
              </mat-form-field>
              <mat-form-field appearance="outline" class="flex1">
                <mat-label>Date</mat-label>
                <input matInput [matDatepicker]="dp" formControlName="dateSortie"/>
                <mat-datepicker-toggle matIconSuffix [for]="dp"></mat-datepicker-toggle>
                <mat-datepicker #dp></mat-datepicker>
              </mat-form-field>
            </div>

            <div formArrayName="items" class="lines">
              @for (item of items.controls; track $index) {
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
                  <button mat-icon-button type="button" (click)="removeItem($index)" aria-label="Supprimer">
                    <mat-icon>delete</mat-icon>
                  </button>
                </div>
              }
            </div>

            <div class="app-button-cluster">
              <button mat-stroked-button type="button" (click)="addItem()">
                <mat-icon>add</mat-icon>
                Ajouter un article
              </button>
              <button mat-flat-button color="primary" type="submit" [disabled]="form.invalid || saving()">
                <mat-icon>logout</mat-icon>
                Sortir du stock (FEFO)
              </button>
            </div>
          </form>
        </mat-card-content>
      </mat-card>

      <mat-card>
        <mat-card-header>
          <mat-card-title>Bons de sortie</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <table mat-table [dataSource]="bons()" class="full-width">
            <ng-container matColumnDef="reference">
              <th mat-header-cell *matHeaderCellDef>Référence</th>
              <td mat-cell *matCellDef="let b">{{ b.reference }}</td>
            </ng-container>
            <ng-container matColumnDef="date">
              <th mat-header-cell *matHeaderCellDef>Date</th>
              <td mat-cell *matCellDef="let b">{{ b.dateSortie }}</td>
            </ng-container>
            <ng-container matColumnDef="poste">
              <th mat-header-cell *matHeaderCellDef>Poste</th>
              <td mat-cell *matCellDef="let b">{{ b.poste }}</td>
            </ng-container>
            <ng-container matColumnDef="lignes">
              <th mat-header-cell *matHeaderCellDef>Lignes</th>
              <td mat-cell *matCellDef="let b">{{ b.lignes.length }}</td>
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
      flex-wrap: wrap;
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
  `],
})
export class BonsSortieComponent {
  protected readonly cols = ['reference', 'date', 'poste', 'lignes'];
  protected readonly bons = signal<BonSortie[]>([]);
  protected readonly articles = signal<RefItem[]>([]);
  protected readonly saving = signal(false);
  private readonly api = inject(StockApiService);
  private readonly refApi = inject(ReferentialApiService);
  private readonly auth = inject(AuthStore);
  private readonly fb = inject(FormBuilder);
  protected readonly form: FormGroup = this.fb.group({
    seanceId: ['', Validators.required],
    patientId: [''],
    poste: [''],
    dateSortie: [new Date()],
    items: this.fb.array([this.newItem()]),
  });
  private readonly snack = inject(MatSnackBar);

  constructor() {
    this.reload();
  }

  get items(): FormArray {
    return this.form.get('items') as FormArray;
  }

  protected addItem(): void {
    this.items.push(this.newItem());
  }

  protected removeItem(i: number): void {
    this.items.removeAt(i);
  }

  protected save(): void {
    const centerId = this.auth.centerId();
    if (!centerId || this.form.invalid) {
      return;
    }
    this.saving.set(true);
    this.api.createBonSortie({
      centerId,
      seanceId: this.form.value.seanceId,
      patientId: this.form.value.patientId || undefined,
      poste: this.form.value.poste || undefined,
      dateSortie: this.toIso(this.form.value.dateSortie),
      userId: this.auth.username() ?? undefined,
      items: this.items.value,
    }).subscribe({
      next: () => {
        this.snack.open('Sortie enregistrée (FEFO)', 'OK', {duration: 2500});
        this.form.setControl('items', this.fb.array([this.newItem()]));
        this.reload();
      },
      complete: () => this.saving.set(false),
      error: (e) => {
        this.saving.set(false);
        const msg = e?.error?.message ?? 'Erreur lors de la sortie';
        this.snack.open(msg, 'Fermer', {duration: 5000});
      },
    });
  }

  private newItem(): FormGroup {
    return this.fb.group({
      articleId: [null, Validators.required],
      quantite: [1, [Validators.required, Validators.min(0.0001)]],
    });
  }

  private reload(): void {
    const centerId = this.auth.centerId();
    if (!centerId) {
      return;
    }
    this.api.listBonsSortie(centerId).subscribe({next: (b) => this.bons.set(b)});
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

