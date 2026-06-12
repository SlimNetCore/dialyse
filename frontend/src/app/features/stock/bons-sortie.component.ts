import {ChangeDetectionStrategy, Component, inject, OnDestroy, signal} from '@angular/core';
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
import {BonSortie, LotDisponible, StockApiService} from '../../core/api/stock-api.service';
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
        <p class="app-section-copy">Sortie de stock par lot. Les lots sont proposes par ordre de peremption (FEFO).</p>
      </div>

      <mat-card>
        <mat-card-header>
          <mat-card-title>Nouvelle sortie</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <form [formGroup]="form" (ngSubmit)="save()">
            <div class="head-row">
              <mat-form-field appearance="outline" class="flex1">
                <mat-label>Date de sortie</mat-label>
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
                    <mat-select formControlName="articleId" (selectionChange)="onArticleChange($index)">
                      @for (a of articles(); track a.id) {
                        <mat-option [value]="a.id" [disabled]="isArticleLocked(a.id)">
                          {{ a.libelle || a.nom }}
                          @if (isArticleLocked(a.id)) {
                            - recalcul en cours
                          }
                        </mat-option>
                      }
                    </mat-select>
                  </mat-form-field>
                  <mat-form-field appearance="outline" class="flex2">
                    <mat-label>Numero lot</mat-label>
                    <mat-select formControlName="lotId" (selectionChange)="onLotChange($index)">
                      @for (l of lotsForRow($index); track l.id) {
                        <mat-option [value]="l.id">
                          {{ l.numeroLot }}
                          @if (l.datePeremption) {
                            - exp {{ l.datePeremption | date:'dd/MM/yyyy' }}
                          }
                          @if (l.quantiteRestante != null) {
                            - stock {{ l.quantiteRestante | number:'1.0-3' }}
                          }
                        </mat-option>
                      }
                    </mat-select>
                  </mat-form-field>
                  <mat-form-field appearance="outline" class="flex1">
                    <mat-label>Quantité</mat-label>
                    <input matInput type="number" formControlName="quantite" (input)="onQuantiteChange($index)"/>
                  </mat-form-field>
                  <mat-form-field appearance="outline" class="flex1 pmp-field">
                    <mat-label>PMP</mat-label>
                    <input matInput [value]="rowPmp($index) | number:'1.0-4'" readonly tabindex="-1"/>
                  </mat-form-field>
                  <mat-form-field appearance="outline" class="flex1">
                    <mat-label>Valeur sortie</mat-label>
                    <input matInput [value]="rowValeur($index) | number:'1.0-4'" readonly tabindex="-1"/>
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
              <button mat-flat-button color="primary" type="submit" [disabled]="form.invalid || saving() || hasLockedItems()">
                <mat-icon>logout</mat-icon>
                Sortir du stock
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
            <ng-container matColumnDef="lignes">
              <th mat-header-cell *matHeaderCellDef>Lignes</th>
              <td mat-cell *matCellDef="let b">{{ b.lignes.length }}</td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="cols"></tr>
            <tr mat-row *matRowDef="let row; columns: cols" [attr.data-row-id]="row?.id"></tr>
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

    .pmp-field {
      opacity: 0.7;
    }
  `],
})
export class BonsSortieComponent implements OnDestroy {
  protected readonly cols = ['reference', 'date', 'lignes'];
  protected readonly bons = signal<BonSortie[]>([]);
  protected readonly articles = signal<RefItem[]>([]);
  protected readonly lotsByRow = signal<Record<number, LotDisponible[]>>({});
  protected readonly lockedArticleIds = signal<string[]>([]);
  protected readonly saving = signal(false);
  private readonly api = inject(StockApiService);
  private readonly refApi = inject(ReferentialApiService);
  private readonly auth = inject(AuthStore);
  private readonly fb = inject(FormBuilder);
  protected readonly form: FormGroup = this.fb.group({
    dateSortie: [new Date()],
    items: this.fb.array([this.newItem()]),
  });
  private readonly snack = inject(MatSnackBar);
  private lockTimer: ReturnType<typeof setInterval> | null = null;

  constructor() {
    this.reload();
    this.lockTimer = setInterval(() => this.refreshLocks(), 5000);
  }

  ngOnDestroy(): void {
    if (this.lockTimer) {
      clearInterval(this.lockTimer);
    }
  }

  get items(): FormArray {
    return this.form.get('items') as FormArray;
  }

  protected addItem(): void {
    this.items.push(this.newItem());
  }

  protected removeItem(i: number): void {
    this.items.removeAt(i);
    const map = {...this.lotsByRow()};
    delete map[i];
    const reindexed: Record<number, LotDisponible[]> = {};
    Object.keys(map)
      .map(k => Number(k))
      .sort((a, b) => a - b)
      .forEach(oldIndex => {
        reindexed[oldIndex > i ? oldIndex - 1 : oldIndex] = map[oldIndex];
      });
    this.lotsByRow.set(reindexed);
  }

  protected onArticleChange(i: number): void {
    const centerId = this.auth.centerId();
    const group = this.items.at(i) as FormGroup;
    const articleId = group.get('articleId')?.value as string | null;
    group.get('lotId')?.setValue(null);
    if (this.isArticleLocked(articleId)) {
      this.snack.open('Recalcul en cours pour cet article. Saisie temporairement bloquee.', 'Fermer', {duration: 4000});
      group.get('articleId')?.setValue(null);
      this.setLotsForRow(i, []);
      return;
    }
    if (!centerId || !articleId) {
      this.setLotsForRow(i, []);
      return;
    }
    this.api.listLotsDisponibles(centerId, articleId).subscribe({
      next: lots => this.setLotsForRow(i, lots),
      error: () => this.setLotsForRow(i, []),
    });
  }

  protected onLotChange(_i: number): void {
    // Le PMP/valeur sont derives automatiquement depuis le lot + quantite.
  }

  protected onQuantiteChange(_i: number): void {
    // Le PMP/valeur sont derives automatiquement depuis le lot + quantite.
  }

  protected lotsForRow(i: number): LotDisponible[] {
    return this.lotsByRow()[i] ?? [];
  }

  protected rowPmp(i: number): number {
    const group = this.items.at(i) as FormGroup;
    const lotId = group.get('lotId')?.value as string | null;
    const lot = this.lotsForRow(i).find(l => l.id === lotId);
    return Number(lot?.pmp ?? 0);
  }

  protected rowValeur(i: number): number {
    const group = this.items.at(i) as FormGroup;
    const qte = Number(group.get('quantite')?.value ?? 0);
    return qte * this.rowPmp(i);
  }

  protected save(): void {
    const centerId = this.auth.centerId();
    if (!centerId || this.form.invalid) {
      return;
    }
    if (this.hasLockedItems()) {
      this.snack.open('Un ou plusieurs articles sont en recalcul. Reessayez plus tard.', 'Fermer', {duration: 4000});
      return;
    }
    this.saving.set(true);
    this.api.createBonSortie({
      centerId,
      dateSortie: this.toIso(this.form.value.dateSortie),
      userId: this.auth.username() ?? undefined,
      items: this.items.controls.map(c => ({
        articleId: c.get('articleId')?.value,
        lotId: c.get('lotId')?.value,
        quantite: Number(c.get('quantite')?.value),
      })),
    }).subscribe({
      next: () => {
        this.snack.open('Sortie enregistree', 'OK', {duration: 2500});
        this.form.setControl('items', this.fb.array([this.newItem()]));
        this.lotsByRow.set({});
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
      lotId: [null, Validators.required],
      quantite: [1, [Validators.required, Validators.min(0.0001)]],
    });
  }

  private setLotsForRow(i: number, lots: LotDisponible[]): void {
    this.lotsByRow.set({...this.lotsByRow(), [i]: lots});
  }

  protected isArticleLocked(articleId?: string | null): boolean {
    if (!articleId) {
      return false;
    }
    return this.lockedArticleIds().includes(articleId);
  }

  protected hasLockedItems(): boolean {
    return this.items.controls.some(c => this.isArticleLocked(c.get('articleId')?.value));
  }

  private reload(): void {
    const centerId = this.auth.centerId();
    if (!centerId) {
      return;
    }
    this.api.listBonsSortie(centerId).subscribe({next: (b) => this.bons.set(b)});
    this.refApi.getArticles(centerId).subscribe({next: (a) => this.articles.set(a)});
    this.refreshLocks();
  }

  private refreshLocks(): void {
    const centerId = this.auth.centerId();
    if (!centerId) {
      return;
    }
    this.api.listPmpRecalcLocks(centerId).subscribe({
      next: (ids) => this.lockedArticleIds.set(ids),
      error: () => this.lockedArticleIds.set([]),
    });
  }

  private toIso(d: unknown): string | undefined {
    if (!d) {
      return undefined;
    }
    const date = d instanceof Date ? d : new Date(d as string);
    return date.toISOString().substring(0, 10);
  }
}

