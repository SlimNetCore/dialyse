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
import {TranslateModule} from '@ngx-translate/core';
import {TranslateService} from '@ngx-translate/core';
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
    MatChipsModule, FormRoot, FormField, TranslateModule,
  ],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './bons-sortie.component.html',
  styleUrl: './bons-sortie.component.css',
})
export class BonsSortieComponent implements OnDestroy {
  protected readonly cols = ['reference', 'date', 'lignes', 'actions'];
  protected readonly bons = signal<BonSortie[]>([]);
  protected readonly articles = signal<RefItem[]>([]);
  protected readonly lotsByRow = signal<Record<number, LotDisponible[]>>({});
  protected readonly lockedArticleIds = signal<string[]>([]);
  protected readonly saving = signal(false);
  protected readonly submitAttempted = signal(false);
  protected readonly editingBonId = signal<string | null>(null);
  protected readonly editingSeanceId = signal<string | null>(null);
  protected readonly editingPatientId = signal<string | null>(null);
  protected readonly editingPoste = signal<string | null>(null);
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
  private readonly translate = inject(TranslateService);
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
      this.snack.open(
        this.translate.instant('STOCK.BON_SORTIE.LOCKED_ARTICLE_SINGLE'),
        this.translate.instant('COMMON.RETRY'),
        {duration: 4000},
      );
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
      this.snack.open(
        this.translate.instant('STOCK.BON_SORTIE.LOCKED_ARTICLES'),
        this.translate.instant('COMMON.RETRY'),
        {duration: 4000},
      );
      return;
    }
    this.saving.set(true);
    const payload = {
      centerId,
      dateSortie: this.toIso(model.dateSortie),
      seanceId: this.editingSeanceId() ?? undefined,
      patientId: this.editingPatientId() ?? undefined,
      poste: this.editingPoste() ?? undefined,
      userId: this.auth.username() ?? undefined,
      items: model.items.map(item => ({
        articleId: item.articleId!,
        lotId: item.lotId!,
        quantite: Number(item.quantite ?? 0),
      })),
    };
    const req$ = this.editingBonId()
      ? this.api.updateBonSortie(this.editingBonId()!, payload)
      : this.api.createBonSortie(payload);
    req$.subscribe({
      next: () => {
        this.snack.open(
          this.editingBonId()
            ? this.translate.instant('STOCK.BON_SORTIE.MODIFIED_OK')
            : this.translate.instant('STOCK.BON_SORTIE.SAVED_OK'),
          this.translate.instant('COMMON.OK'),
          {duration: 2500},
        );
        this.cancelEdit();
        this.reload();
      },
      complete: () => this.saving.set(false),
      error: (e) => {
        this.saving.set(false);
        const detail = e?.error?.message;
        const msg = detail
          ? this.translate.instant('STOCK.BON_SORTIE.SAVE_ERROR_DETAIL', {detail})
          : this.translate.instant('STOCK.BON_SORTIE.SAVE_ERROR');
        this.snack.open(msg, this.translate.instant('COMMON.RETRY'), {duration: 5000});
      },
    });
  }

  protected editBon(bon: BonSortie): void {
    this.editingBonId.set(bon.id);
    this.editingSeanceId.set(bon.seanceId ?? null);
    this.editingPatientId.set(bon.patientId ?? null);
    this.editingPoste.set(bon.poste ?? null);
    this.formModel.set({
      dateSortie: bon.dateSortie ? new Date(bon.dateSortie) : new Date(),
      items: bon.lignes.map((line) => ({
        articleId: line.articleId,
        lotId: line.lotId ?? null,
        quantite: Number(line.quantite ?? 0),
      })),
    });
    const centerId = this.auth.centerId();
    if (centerId) {
      bon.lignes.forEach((line, idx) => {
        this.api.listLotsDisponibles(centerId, line.articleId).subscribe({
          next: (lots) => this.setLotsForRow(idx, lots),
          error: () => this.setLotsForRow(idx, []),
        });
      });
    }
    this.submitAttempted.set(false);
  }

  protected cancelEdit(): void {
    this.editingBonId.set(null);
    this.editingSeanceId.set(null);
    this.editingPatientId.set(null);
    this.editingPoste.set(null);
    this.formModel.set(this.createInitialForm());
    this.submitAttempted.set(false);
    this.lotsByRow.set({});
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

