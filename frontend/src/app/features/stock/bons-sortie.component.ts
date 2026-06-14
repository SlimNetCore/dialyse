import {ChangeDetectionStrategy, Component, computed, effect, inject, OnDestroy, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {compatForm} from '@angular/forms/signals/compat';
import {FormField, FormRoot, required} from '@angular/forms/signals';
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
import {AuthStore} from '../../core/state/auth.store';
import {WebSocketService} from '../../core/ws/websocket.service';
import {BonSortie, LotDisponible, StockApiService} from '../../core/api/stock-api.service';
import {ReferentialApiService, RefItem} from '../../core/api/referential-api.service';

@Component({
  selector: 'app-bons-sortie',
  standalone: true,
  imports: [
    CommonModule, MatCardModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatDatepickerModule, MatNativeDateModule, MatButtonModule, MatIconModule, MatTableModule,
    MatChipsModule, FormRoot, FormField,
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
          <form [formRoot]="sortieForm" (submit)="save(); $event.preventDefault()">
            <div class="head-row">
              @if (lockedArticleIds().length > 0) {
                <mat-chip class="lock-chip">{{ lockedArticleIds().length }} article(s) en recalcul</mat-chip>
              }
              <mat-form-field appearance="outline" class="flex1">
                <mat-label>Date de sortie</mat-label>
                <input matInput [matDatepicker]="dp" [formField]="sortieForm.dateSortie"/>
                <mat-datepicker-toggle matIconSuffix [for]="dp"></mat-datepicker-toggle>
                <mat-datepicker #dp></mat-datepicker>
                @if (showDateError()) {
                  <mat-error>Date obligatoire</mat-error>
                }
              </mat-form-field>
            </div>

            <div class="lines">
              @for (item of formModel().items; track $index) {
                <div class="line-row">
                  <mat-form-field appearance="outline" class="flex2">
                    <mat-label>Article</mat-label>
                    <mat-select [value]="item.articleId" (selectionChange)="onArticleSelected($index, $event.value)">
                      @for (a of articles(); track a.id) {
                        <mat-option [value]="a.id" [disabled]="isArticleLocked(a.id)">
                          {{ articleLabel(a) }}
                          @if (isArticleLocked(a.id)) {
                            - recalcul en cours
                          }
                        </mat-option>
                      }
                    </mat-select>
                    @if (showItemError($index, 'article')) {
                      <mat-error>Article obligatoire</mat-error>
                    }
                  </mat-form-field>
                  <mat-form-field appearance="outline" class="flex2">
                    <mat-label>Numero lot</mat-label>
                    <mat-select [value]="item.lotId" (selectionChange)="onLotSelected($index, $event.value)">
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
                    @if (showItemError($index, 'lot')) {
                      <mat-error>Lot obligatoire</mat-error>
                    }
                  </mat-form-field>
                  <mat-form-field appearance="outline" class="flex1">
                    <mat-label>Quantité</mat-label>
                    <input matInput type="number" [value]="item.quantite ?? ''"
                           (input)="onQuantiteInput($index, $event)"/>
                    @if (showItemError($index, 'quantite')) {
                      <mat-error>Quantité invalide</mat-error>
                    }
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
              <button mat-flat-button color="primary" type="submit"
                      [disabled]="!canSave() || saving() || hasLockedItems()">
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
  protected readonly submitAttempted = signal(false);
  protected readonly formModel = signal<SortieFormModel>(this.createInitialForm());
  protected readonly sortieForm = compatForm(this.formModel, (form) => {
    required(form.dateSortie);
  });
  protected readonly canSave = computed(() => {
    const model = this.formModel();
    if (!model.dateSortie || model.items.length === 0) {
      return false;
    }
    return model.items.every(item => {
      const quantite = Number(item.quantite ?? 0);
      return !!item.articleId && !!item.lotId && quantite > 0;
    });
  });
  private readonly api = inject(StockApiService);
  private readonly refApi = inject(ReferentialApiService);
  private readonly auth = inject(AuthStore);
  private readonly ws = inject(WebSocketService);
  private readonly snack = inject(MatSnackBar);
  private lockTimer: ReturnType<typeof setInterval> | null = null;

  constructor() {
    this.reload();
    this.lockTimer = setInterval(() => this.refreshLocks(), 5000);
    effect(() => {
      const evt = this.ws.lastEvent();
      const centerId = this.auth.centerId();
      if (!evt || !centerId) {
        return;
      }
      if (evt.type === 'STOCK_RECALC_LOCKS_CHANGED' && evt.centerId === centerId) {
        this.refreshLocks();
      }
    });
  }

  ngOnDestroy(): void {
    if (this.lockTimer) {
      clearInterval(this.lockTimer);
    }
  }

  protected addItem(): void {
    this.formModel.update(model => ({
      ...model,
      items: [...model.items, this.newItem()],
    }));
  }

  protected removeItem(i: number): void {
    this.formModel.update(model => ({
      ...model,
      items: model.items.filter((_, index) => index !== i),
    }));
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

  protected onArticleSelected(i: number, articleId: string | null): void {
    const centerId = this.auth.centerId();
    this.patchItem(i, {articleId, lotId: null});
    if (this.isArticleLocked(articleId)) {
      this.snack.open('Recalcul en cours pour cet article. Saisie temporairement bloquee.', 'Fermer', {duration: 4000});
      this.patchItem(i, {articleId: null});
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

  protected onLotSelected(i: number, lotId: string | null): void {
    this.patchItem(i, {lotId});
  }

  protected onQuantiteInput(i: number, event: Event): void {
    const raw = (event.target as HTMLInputElement | null)?.value;
    const quantite = raw == null || raw === '' ? null : Number(raw);
    this.patchItem(i, {quantite: Number.isFinite(quantite ?? NaN) ? quantite : null});
  }

  protected lotsForRow(i: number): LotDisponible[] {
    return this.lotsByRow()[i] ?? [];
  }

  protected rowPmp(i: number): number {
    const lotId = this.formModel().items[i]?.lotId ?? null;
    const lot = this.lotsForRow(i).find(l => l.id === lotId);
    return Number(lot?.pmp ?? 0);
  }

  protected rowValeur(i: number): number {
    const qte = Number(this.formModel().items[i]?.quantite ?? 0);
    return qte * this.rowPmp(i);
  }

  protected save(): void {
    this.submitAttempted.set(true);
    const centerId = this.auth.centerId();
    const model = this.formModel();
    if (!centerId || !this.canSave()) {
      return;
    }
    if (this.hasLockedItems()) {
      this.snack.open('Un ou plusieurs articles sont en recalcul. Reessayez plus tard.', 'Fermer', {duration: 4000});
      return;
    }
    this.saving.set(true);
    this.api.createBonSortie({
      centerId,
      dateSortie: this.toIso(model.dateSortie),
      userId: this.auth.username() ?? undefined,
      items: model.items.map(item => ({
        articleId: item.articleId!,
        lotId: item.lotId!,
        quantite: Number(item.quantite ?? 0),
      })),
    }).subscribe({
      next: () => {
        this.snack.open('Sortie enregistree', 'OK', {duration: 2500});
        this.formModel.set(this.createInitialForm());
        this.submitAttempted.set(false);
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

  protected hasLockedItems(): boolean {
    return this.formModel().items.some(item => this.isArticleLocked(item.articleId));
  }

  protected showDateError(): boolean {
    return this.submitAttempted() && !this.formModel().dateSortie;
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

  protected showItemError(index: number, field: 'article' | 'lot' | 'quantite'): boolean {
    if (!this.submitAttempted()) {
      return false;
    }
    const item = this.formModel().items[index];
    if (!item) {
      return false;
    }
    if (field === 'article') {
      return !item.articleId;
    }
    if (field === 'lot') {
      return !item.lotId;
    }
    return Number(item.quantite ?? 0) <= 0;
  }

  private newItem(): SortieItemForm {
    return {
      articleId: null,
      lotId: null,
      quantite: 1,
    };
  }

  private patchItem(index: number, patch: Partial<SortieItemForm>): void {
    this.formModel.update(model => ({
      ...model,
      items: model.items.map((item, itemIndex) => itemIndex === index ? {...item, ...patch} : item),
    }));
  }

  protected articleLabel(a: RefItem): string {
    const code = (a.code ?? '').trim();
    const libelle = (a.libelle ?? a.nom ?? '').trim();
    const unite = (a.unite ?? '').trim();
    const left = code ? `${code} - ${libelle}` : libelle;
    return unite ? `${left} (${unite})` : left;
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

  private createInitialForm(): SortieFormModel {
    return {
      dateSortie: new Date(),
      items: [this.newItem()],
    };
  }
}

type SortieFormModel = {
  dateSortie: Date | string | null;
  items: SortieItemForm[];
};

type SortieItemForm = {
  articleId: string | null;
  lotId: string | null;
  quantite: number | null;
};

