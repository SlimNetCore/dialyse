import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
  TemplateRef,
  untracked,
  viewChild
} from '@angular/core';
import {DatePipe, DecimalPipe} from '@angular/common';
import {Router, RouterLink} from '@angular/router';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatExpansionModule} from '@angular/material/expansion';
import {
  NgTableCellFormat,
  NgTableCellFormatComponent,
  NgTableColumn,
  NgTableComponent,
  NgTableRemoteQuery,
  NgTableTone
} from '@sbourahla/ng-table';
import {NgTableTranslatedLabels} from '../../../shared/ng-table-labels';
import {langueCourante, STOCK_PAGE_SIZES} from '../stock-ng-table';
import {MatDialog} from '@angular/material/dialog';
import {MatSnackBar} from '@angular/material/snack-bar';
import {MatTooltipModule} from '@angular/material/tooltip';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {firstValueFrom} from 'rxjs';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {AuthStore} from '../../../core/state/auth.store';
import {InventaireApiService, InventaireResume} from '../../../core/api/inventaire-api.service';
import {ConfirmDialogComponent} from '../../../shared/confirm-dialog.component';
import {requiredValidator, SignalForm} from '../../../shared/forms/signal-form';
import {InventaireStore} from './state/inventaire.store';
import {openPdf, progression} from './inventaire.util';

/** En cours en orange, clôturé en vert, annulé en gris. */
export const INVENTORY_STATUS_TONES: Record<string, NgTableTone> = {EN_COURS: 'warning', CLOTURE: 'success', ANNULE: 'neutral'};

/** `yyyy-MM-dd` ⇒ `dd/MM/yyyy`. */
function shortDate(iso: string | null | undefined): string {
  const [year, month, day] = (iso ?? '').substring(0, 10).split('-');
  return year && month && day ? `${day}/${month}/${year}` : '-';
}

function todayIso(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

/** Inventaires de stock : ouverture guidée, inventaire en cours, bonnes pratiques et historique. */
@Component({
  selector: 'app-inventaires',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, DecimalPipe, RouterLink, MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule,
    MatExpansionModule, MatTooltipModule, TranslateModule, NgTableComponent, NgTableCellFormatComponent],
  template: `
    <section class="app-page">
      <div class="app-hero-card">
        <span class="app-eyebrow">{{ 'STOCK.INVENTORY.EYEBROW' | translate }}</span>
        <h1 class="app-section-title">{{ 'STOCK.INVENTORY.TITLE' | translate }}</h1>
        <p class="app-section-copy">{{ 'STOCK.INVENTORY.DESC' | translate }}</p>
      </div>

      @if (store.error(); as err) {
        <p class="feedback error" role="alert">{{ err | translate }}</p>
      }

      <div class="top-grid">
        @if (store.etat()?.mouvementsBloques) {
          @let etat = store.etat()!;
          <article class="card current" data-testid="inventory-current">
            <div class="card-head">
              <mat-icon class="badge-icon warn">inventory</mat-icon>
              <div>
                <h2>{{ etat.inventaireEnCoursReference }}</h2>
                <p class="muted">{{
                    'STOCK.INVENTORY.CURRENT_SINCE' | translate: {
                      date: (etat.inventaireEnCoursDate | date: 'dd/MM/yyyy'),
                      user: etat.ouvertPar ?? '-'
                    }
                  }}</p>
              </div>
            </div>
            <p>{{ 'STOCK.INVENTORY.CURRENT_HELP' | translate }}</p>
            <a mat-flat-button color="primary" [routerLink]="['/stock/inventaires', etat.inventaireEnCoursId]"
               data-testid="inventory-resume">
              <mat-icon>checklist</mat-icon>
              {{ 'STOCK.INVENTORY.RESUME' | translate }}
            </a>
          </article>
        } @else if (canManage()) {
          <form class="card open" (submit)="$event.preventDefault(); open()" data-testid="inventory-open-form">
            <div class="card-head">
              <mat-icon class="badge-icon">add_task</mat-icon>
              <div>
                <h2>{{ 'STOCK.INVENTORY.NEW_TITLE' | translate }}</h2>
                @if (store.etat()?.derniereCloture; as last) {
                  <p
                    class="muted">{{ 'STOCK.INVENTORY.LAST_CLOSED' | translate: {date: (last | date: 'dd/MM/yyyy')} }}</p>
                } @else {
                  <p class="muted">{{ 'STOCK.INVENTORY.NEVER' | translate }}</p>
                }
              </div>
            </div>
            <div class="form-grid">
              <mat-form-field appearance="outline">
                <mat-label>{{ 'STOCK.INVENTORY.DATE' | translate }}</mat-label>
                <input matInput type="date" [value]="form.value().date" [max]="today"
                       (input)="form.set('date', $any($event.target).value)" (blur)="form.markTouched('date')"
                       data-testid="inventory-date"/>
                <mat-hint>{{ 'STOCK.INVENTORY.DATE_HINT' | translate }}</mat-hint>
                @if (form.showError('date')) {
                  <mat-error>{{ form.firstError('date')! | translate }}</mat-error>
                }
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>{{ 'STOCK.INVENTORY.COMMENT' | translate }}</mat-label>
                <input matInput [value]="form.value().commentaire" maxlength="1000"
                       (input)="form.set('commentaire', $any($event.target).value)"
                       [placeholder]="'STOCK.INVENTORY.COMMENT_PLACEHOLDER' | translate"/>
              </mat-form-field>
            </div>
            <button mat-flat-button color="primary" type="submit" [disabled]="store.busy()"
                    data-testid="inventory-open">
              <mat-icon>lock_clock</mat-icon>
              {{ 'STOCK.INVENTORY.OPEN' | translate }}
            </button>
          </form>
        }

        <mat-accordion class="card tips">
          <mat-expansion-panel [expanded]="false">
            <mat-expansion-panel-header>
              <mat-panel-title>
                <mat-icon>tips_and_updates</mat-icon>
                {{ 'STOCK.INVENTORY.TIPS_TITLE' | translate }}
              </mat-panel-title>
            </mat-expansion-panel-header>
            <ul>
              @for (tip of tips; track tip) {
                <li>{{ tip | translate }}</li>
              }
            </ul>
          </mat-expansion-panel>
        </mat-accordion>
      </div>

      <h2 class="section-title">{{ 'STOCK.INVENTORY.HISTORY' | translate }}</h2>
      <div class="table-wrapper" data-testid="inventory-history">
        <ng-table
          [columns]="historyColumns()"
          [rows]="store.history()"
          [dataMode]="'remote'"
          [paginator]="true"
          [totalCount]="store.total()"
          [pageIndex]="store.pageIndex()"
          [pageSize]="store.pageSize()"
          [pageSizeOptions]="pageSizes"
          [loading]="store.loading()"
          [labels]="labels()"
          [emptyLabel]="emptyLabel()"
          [rowKeyAccessor]="inventoryKey"
          [rowClassFn]="rowClass"
          (remoteQueryChange)="onRemoteQueryChange($event)"
          (rowClick)="openDetail($event)"
        />
      </div>
    </section>

    <ng-template #referenceCell let-i><strong>{{ i.reference }}</strong></ng-template>

    <ng-template #progressCell let-i>
      <span class="progress-cell">
        <ng-table-cell-format aria-hidden="true" [format]="progressFormat" [row]="i" [value]="percent(i)"/>
        <small>{{ i.comptees }}/{{ i.lignes }}</small>
      </span>
    </ng-template>

    <ng-template #valueCell let-i>
      <span class="num" [class.neg]="i.valeurEcarts < 0" [class.pos]="i.valeurEcarts > 0">
        {{ i.valeurEcarts | number: '1.2-2' }}
      </span>
    </ng-template>

    <ng-template #actionsCell let-i>
      <button mat-icon-button type="button" [disabled]="printingId() === i.id"
              [matTooltip]="'STOCK.INVENTORY.PRINT' | translate"
              [attr.aria-label]="'STOCK.INVENTORY.PRINT' | translate"
              (click)="$event.stopPropagation(); print(i)" [attr.data-testid]="'inventory-print-' + i.id">
        <mat-icon>print</mat-icon>
      </button>
    </ng-template>
  `,
  styles: [`
    .feedback.error {
      padding: 10px 16px;
      border-radius: 10px;
      background: #fce4ec;
      color: #c62828;
    }

    .top-grid {
      display: grid;
      grid-template-columns: minmax(0, 3fr) minmax(0, 2fr);
      gap: 16px;
      align-items: start;
    }

    .card {
      display: flex;
      flex-direction: column;
      gap: 12px;
      padding: 18px 20px;
      border-radius: 18px;
      border: 1px solid var(--app-border);
      background: var(--app-surface);
      box-shadow: var(--app-shadow-soft, 0 8px 20px rgba(15, 23, 42, 0.06));
    }

    .card.current {
      border-color: #f5c35b;
      background: linear-gradient(160deg, #fffaf0, var(--app-surface));
    }

    .card.tips {
      padding: 0;
      overflow: hidden;
    }

    .card.tips ul {
      margin: 0;
      padding-left: 20px;
      display: grid;
      gap: 8px;
      color: var(--app-muted);
    }

    .card.tips mat-panel-title {
      display: flex;
      align-items: center;
      gap: 8px;
      font-weight: 600;
    }

    .card-head {
      display: flex;
      align-items: center;
      gap: 14px;
    }

    .card-head h2 {
      margin: 0;
      font-size: 18px;
    }

    .muted {
      margin: 2px 0 0;
      color: var(--app-muted);
      font-size: 13px;
    }

    .badge-icon {
      width: 44px;
      height: 44px;
      font-size: 26px;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      border-radius: 14px;
      color: var(--app-primary);
      background: color-mix(in srgb, var(--app-primary) 14%, white);
    }

    .badge-icon.warn {
      color: #b45309;
      background: #fef3c7;
    }

    .form-grid {
      display: grid;
      grid-template-columns: minmax(0, 1fr) minmax(0, 2fr);
      gap: 12px;
    }

    .section-title {
      margin: 28px 0 10px;
      font-size: 17px;
    }

    .table-wrapper {
      min-width: 0;
      overflow-x: auto;
    }

    :host ::ng-deep tr.inventory-row {
      cursor: pointer;
    }

    .progress-cell {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .progress-cell ng-table-cell-format {
      flex: 1 1 auto;
      min-width: 60px;
    }

    .num {
      display: block;
      text-align: end;
      font-variant-numeric: tabular-nums;
    }

    .neg {
      color: var(--app-danger, #c62828);
      font-weight: 600;
    }

    .pos {
      color: var(--app-success, #15803d);
      font-weight: 600;
    }

    @media (max-width: 900px) {
      .top-grid, .form-grid {
        grid-template-columns: 1fr;
      }
    }
  `],
})
export class InventairesComponent {
  protected readonly store = inject(InventaireStore);
  protected readonly today = todayIso();
  protected readonly pageSizes = STOCK_PAGE_SIZES;
  protected readonly labels = inject(NgTableTranslatedLabels).labels;
  protected readonly progressFormat: NgTableCellFormat<InventaireResume> = {type: 'progress', max: 100, showValue: false};
  protected readonly inventoryKey = (i: InventaireResume): unknown => i.id;
  protected readonly rowClass = (): string => 'inventory-row';
  private readonly referenceCell = viewChild<TemplateRef<unknown>>('referenceCell');
  private readonly progressCell = viewChild<TemplateRef<unknown>>('progressCell');
  private readonly valueCell = viewChild<TemplateRef<unknown>>('valueCell');
  private readonly actionsCell = viewChild<TemplateRef<unknown>>('actionsCell');
  private readonly langue = langueCourante();
  protected readonly tips = ['STOCK.INVENTORY.TIP_1', 'STOCK.INVENTORY.TIP_2', 'STOCK.INVENTORY.TIP_3', 'STOCK.INVENTORY.TIP_4',
    'STOCK.INVENTORY.TIP_5', 'STOCK.INVENTORY.TIP_6'];
  protected readonly form = new SignalForm<{ date: string; commentaire: string }>(
    {date: todayIso(), commentaire: ''}, {date: [requiredValidator()]});
  private readonly appShell = inject(AppShellStore);
  private readonly auth = inject(AuthStore);
  protected readonly canManage = computed(() => this.auth.hasRole('ADMIN') || this.auth.hasRole('PHARMACIEN'));
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly translate = inject(TranslateService);
  protected readonly emptyLabel = computed(() => {
    this.langue();
    return this.translate.instant('STOCK.INVENTORY.EMPTY') as string;
  });
  /** Historique paginé côté serveur (page et taille seulement : l'API ne trie ni ne filtre). */
  protected readonly historyColumns = computed<NgTableColumn<InventaireResume>[]>(() => {
    this.langue();
    const t = (key: string): string => this.translate.instant(`STOCK.INVENTORY.${key}`);
    const status = (value: unknown): string => t(`STATUS.${String(value)}`);
    type Template = NgTableColumn<InventaireResume>['cellTemplate'];
    return [
      {id: 'reference', header: t('COL_REF'), valueAccessor: (i) => i.reference, cellTemplate: this.referenceCell() as Template},
      {id: 'date', header: t('DATE'), valueAccessor: (i) => shortDate(i.dateInventaire)},
      {
        id: 'statut', header: t('COL_STATUS'), valueAccessor: (i) => i.statut,
        cell: {type: 'badge', tone: INVENTORY_STATUS_TONES, label: status},
      },
      {
        id: 'progression', header: t('COL_PROGRESS'), valueAccessor: (i) => progression(i), minWidthPx: 150,
        exportValueAccessor: (i) => `${i.comptees}/${i.lignes}`, cellTemplate: this.progressCell() as Template,
      },
      {id: 'ecarts', header: t('COL_GAPS'), valueAccessor: (i) => i.ecarts},
      {
        id: 'valeur', header: t('COL_GAP_VALUE'), valueAccessor: (i) => i.valeurEcarts,
        cellTemplate: this.valueCell() as Template,
      },
      {id: 'par', header: t('COL_BY'), valueAccessor: (i) => i.closedBy ?? i.createdBy ?? '-'},
      {
        id: 'actions', header: '', valueAccessor: () => '', exportable: false, mobileRowActions: true, widthPx: 72,
        cellTemplate: this.actionsCell() as Template,
      },
    ];
  });
  /** Inventaire dont le procès-verbal est en cours de génération. */
  protected readonly printingId = signal<string | null>(null);
  private readonly api = inject(InventaireApiService);
  private readonly snackBar = inject(MatSnackBar);

  constructor() {
    effect(() => {
      if (!this.appShell.currentCenterId()) return;
      untracked(() => void this.store.init());
    });
  }

  protected percent(i: InventaireResume): number {
    return progression(i);
  }

  protected async open(): Promise<void> {
    this.form.markAllTouched();
    if (this.form.invalid()) return;
    const confirmed = await firstValueFrom(this.dialog.open(ConfirmDialogComponent, {
      data: {
        title: this.translate.instant('STOCK.INVENTORY.OPEN_CONFIRM_TITLE'),
        message: this.translate.instant('STOCK.INVENTORY.OPEN_CONFIRM'),
        confirmLabel: this.translate.instant('STOCK.INVENTORY.OPEN'),
        cancelLabel: this.translate.instant('COMMON.CANCEL'),
        color: 'accent',
        icon: 'lock_clock',
      },
      maxWidth: '95vw',
    }).afterClosed());
    if (!confirmed) return;
    const value = this.form.value();
    const inventaire = await this.store.ouvrir(value.date || null, value.commentaire.trim() || null);
    if (inventaire) void this.router.navigate(['/stock/inventaires', inventaire.id]);
  }

  /** Procès-verbal PDF (Jasper) de l'inventaire, avec l'en-tête et le pied de page de la société et du centre. */
  protected async print(row: InventaireResume): Promise<void> {
    const center = this.appShell.currentCenterId();
    if (!center) return;
    this.printingId.set(row.id);
    const error = await openPdf(this.api.rapport(center, row.id), `inventaire-${row.reference}.pdf`);
    this.printingId.set(null);
    if (error !== null) {
      this.snackBar.open(error || this.translate.instant('STOCK.INVENTORY.PRINT_ERROR'), 'OK', {duration: 7000});
    }
  }

  protected openDetail(row: InventaireResume): void {
    void this.router.navigate(['/stock/inventaires', row.id]);
  }

  /** Changement de page ou de taille : recharge la page demandée, sans relire celle déjà affichée. */
  protected onRemoteQueryChange(query: NgTableRemoteQuery): void {
    const {index, size} = query.page;
    if (index === this.store.pageIndex() && size === this.store.pageSize()) return;
    void this.store.setPage(index, size);
  }
}






