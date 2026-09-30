import {ChangeDetectionStrategy, Component, computed, inject, signal} from '@angular/core';
import {MatDialogModule, MatDialogRef} from '@angular/material/dialog';
import {MatButtonModule} from '@angular/material/button';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatSelectModule} from '@angular/material/select';
import {MatIconModule} from '@angular/material/icon';
import {TranslateModule} from '@ngx-translate/core';
import {firstValueFrom} from 'rxjs';
import {ArticleStock, StockApiService} from '../../../core/api/stock-api.service';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {requiredValidator, SignalForm, SignalFormValidator} from '../../../shared/forms/signal-form';
import {InventaireStore} from './state/inventaire.store';
import {MOTIFS_ECART} from './inventaire.util';

interface AjoutForm {
  articleId: string;
  numeroLot: string;
  datePeremption: string;
  quantite: string;
  motif: string;
}

const positive: SignalFormValidator<string> = (v) =>
  v !== '' && !Number.isNaN(Number(v)) && Number(v) >= 0 ? null : 'STOCK.INVENTORY.QTY_INVALID';

/** Lot trouvé physiquement mais absent du stock théorique : il est ajouté à l'inventaire avec sa quantité. */
@Component({
  selector: 'app-inventaire-add-line-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatIconModule,
    TranslateModule],
  template: `
    <h2 mat-dialog-title>{{ 'STOCK.INVENTORY.ADD_LINE_TITLE' | translate }}</h2>
    <mat-dialog-content>
      <p class="help">{{ 'STOCK.INVENTORY.ADD_LINE_HELP' | translate }}</p>
      <div class="grid">
        <mat-form-field appearance="outline" class="full">
          <mat-label>{{ 'STOCK.INVENTORY.ARTICLE' | translate }}</mat-label>
          <mat-select [value]="form.value().articleId" (selectionChange)="form.set('articleId', $event.value)"
                      data-testid="add-line-article">
            @for (a of articles(); track a.id) {
              <mat-option [value]="a.id">{{ a.code }} — {{ a.libelle }}</mat-option>
            }
          </mat-select>
          @if (form.showError('articleId')) {
            <mat-error>{{ 'STOCK.ARTICLE_REQUIRED_ERROR' | translate }}</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>{{ 'STOCK.INVENTORY.LOT' | translate }}</mat-label>
          <input matInput [value]="form.value().numeroLot" (input)="form.set('numeroLot', $any($event.target).value)"
                 maxlength="100" data-testid="add-line-lot"/>
          @if (lotRequired()) {
            <mat-hint>{{ 'STOCK.INVENTORY.LOT_REQUIRED_HINT' | translate }}</mat-hint>
          }
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>{{ 'STOCK.INVENTORY.EXPIRY' | translate }}</mat-label>
          <input matInput type="date" [value]="form.value().datePeremption"
                 (input)="form.set('datePeremption', $any($event.target).value)"/>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>{{ 'STOCK.INVENTORY.COUNTED' | translate }}</mat-label>
          <input matInput type="number" min="0" step="any" inputmode="decimal" [value]="form.value().quantite"
                 (input)="form.set('quantite', $any($event.target).value)" (blur)="form.markTouched('quantite')"
                 data-testid="add-line-qty"/>
          @if (form.showError('quantite')) {
            <mat-error>{{ form.firstError('quantite')! | translate }}</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>{{ 'STOCK.INVENTORY.REASON' | translate }}</mat-label>
          <mat-select [value]="form.value().motif" (selectionChange)="form.set('motif', $event.value)">
            @for (m of motifs; track m) {
              <mat-option [value]="m">{{ 'STOCK.INVENTORY.REASONS.' + m | translate }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
      </div>
      @if (store.error(); as err) {
        <p class="error" role="alert">{{ err | translate }}</p>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-stroked-button type="button" (click)="ref.close(false)">{{ 'COMMON.CANCEL' | translate }}</button>
      <button mat-flat-button color="primary" type="button" (click)="submit()" [disabled]="store.busy()"
              data-testid="add-line-save">
        <mat-icon>add</mat-icon>{{ 'STOCK.INVENTORY.ADD_LINE' | translate }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .help { color: var(--app-muted); margin: 0 0 12px; }
    .grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 4px 14px; }
    .full { grid-column: 1 / -1; }
    .error { padding: 8px 12px; border-radius: 8px; background: #fce4ec; color: #c62828; }
    @media (max-width: 767px) { .grid { grid-template-columns: 1fr; } }
  `],
})
export class InventaireAddLineDialogComponent {
  protected readonly ref = inject(MatDialogRef<InventaireAddLineDialogComponent, boolean>);
  protected readonly store = inject(InventaireStore);
  protected readonly motifs = MOTIFS_ECART;
  protected readonly articles = signal<ArticleStock[]>([]);
  protected readonly form = new SignalForm<AjoutForm>(
    {articleId: '', numeroLot: '', datePeremption: '', quantite: '', motif: 'NON_ENREGISTRE'},
    {articleId: [requiredValidator()], quantite: [positive]});
  protected readonly lotRequired = computed(() =>
    !!this.articles().find((a) => a.id === this.form.value().articleId)?.gereParLot);
  private readonly stockApi = inject(StockApiService);
  private readonly appShell = inject(AppShellStore);

  constructor() {
    this.store.clearError();
    const center = this.appShell.currentCenterId();
    if (center) {
      void firstValueFrom(this.stockApi.listArticles(center))
        .then((list) => this.articles.set(list.filter((a) => a.active)))
        .catch(() => this.articles.set([]));
    }
  }

  protected async submit(): Promise<void> {
    this.form.markAllTouched();
    if (this.form.invalid()) return;
    const v = this.form.value();
    const ok = await this.store.ajouterLigne({
      articleId: v.articleId,
      numeroLot: v.numeroLot.trim() || null,
      datePeremption: v.datePeremption || null,
      quantite: Number(v.quantite),
      motif: v.motif || null,
    });
    if (ok) this.ref.close(true);
  }
}

