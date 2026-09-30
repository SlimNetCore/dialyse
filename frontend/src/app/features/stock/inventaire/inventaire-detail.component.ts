import {ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked} from '@angular/core';
import {toSignal} from '@angular/core/rxjs-interop';
import {DatePipe, DecimalPipe} from '@angular/common';
import {ActivatedRoute, Router, RouterLink} from '@angular/router';
import {MatButtonModule} from '@angular/material/button';
import {MatButtonToggleModule} from '@angular/material/button-toggle';
import {MatIconModule} from '@angular/material/icon';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatSelectModule} from '@angular/material/select';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatSlideToggleModule} from '@angular/material/slide-toggle';
import {MatTooltipModule} from '@angular/material/tooltip';
import {MatDialog} from '@angular/material/dialog';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {firstValueFrom} from 'rxjs';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {AuthStore} from '../../../core/state/auth.store';
import {InventaireApiService, LigneInventaire} from '../../../core/api/inventaire-api.service';
import {WebSocketService} from '../../../core/ws/websocket.service';
import {ConfirmDialogComponent} from '../../../shared/confirm-dialog.component';
import {InventaireStore} from './state/inventaire.store';
import {InventaireAddLineDialogComponent} from './inventaire-add-line-dialog.component';
import {InventaireCloseDialogComponent} from './inventaire-close-dialog.component';
import {ecart, etatPeremption, FiltreLignes, filtrerLignes, MOTIFS_ECART} from './inventaire.util';

/** Comptage d'un inventaire : saisie ligne à ligne, écarts, motifs, clôture ou annulation. */
@Component({
  selector: 'app-inventaire-detail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, DecimalPipe, RouterLink, MatButtonModule, MatButtonToggleModule, MatIconModule, MatFormFieldModule,
    MatInputModule, MatSelectModule, MatProgressBarModule, MatSlideToggleModule, MatTooltipModule, TranslateModule],
  template: `
    <section class="app-page">
      <a mat-button routerLink="/stock/inventaires" class="back">
        <mat-icon>arrow_back</mat-icon>
        {{ 'STOCK.INVENTORY.BACK' | translate }}</a>

      @if (store.error(); as err) {
        <p class="feedback error" role="alert">{{ err | translate }}</p>
      }
      @if (store.loading() && !inv()) {
        <mat-progress-bar mode="indeterminate"/>
      }

      @if (inv(); as inv) {
        <div class="app-hero-card head">
          <div>
            <span class="app-eyebrow">{{ 'STOCK.INVENTORY.EYEBROW' | translate }}</span>
            <h1 class="app-section-title">
              {{ inv.reference }}
              <span class="chip"
                    [class]="'chip st-' + inv.statut">{{ 'STOCK.INVENTORY.STATUS.' + inv.statut | translate }}</span>
            </h1>
            <p class="app-section-copy">
              {{
                'STOCK.INVENTORY.DETAIL_SUBTITLE' | translate: {
                  date: (inv.dateInventaire | date: 'dd/MM/yyyy'),
                  user: inv.createdBy ?? '-'
                }
              }}
            </p>
            @if (inv.commentaire) {
              <p class="comment">
                <mat-icon>notes</mat-icon>
                {{ inv.commentaire }}
              </p>
            }
          </div>
          <div class="kpis">
            <div class="kpi"><span>{{ 'STOCK.INVENTORY.KPI_PROGRESS' | translate }}</span><strong>{{ inv.comptees }}
              /{{ inv.lignes }}</strong></div>
            <div class="kpi"><span>{{ 'STOCK.INVENTORY.KPI_GAPS' | translate }}</span><strong>{{ inv.ecarts }}</strong>
            </div>
            <div class="kpi" [class.warn]="inv.ecartsSansMotif > 0">
              <span>{{ 'STOCK.INVENTORY.KPI_MISSING_REASONS' | translate }}</span><strong>{{ inv.ecartsSansMotif }}</strong>
            </div>
            <div class="kpi" [class.neg]="inv.valeurEcarts < 0" [class.pos]="inv.valeurEcarts > 0">
              <span>{{ 'STOCK.INVENTORY.KPI_GAP_VALUE' | translate }}</span><strong>{{ inv.valeurEcarts | number: '1.2-2' }}</strong>
            </div>
          </div>
          <div class="progress" [attr.aria-label]="'STOCK.INVENTORY.KPI_PROGRESS' | translate">
            <mat-progress-bar mode="determinate" [value]="store.progression()"/>
            <small>{{ store.progression() }} %</small>
          </div>
        </div>

        <div class="toolbar">
          <mat-button-toggle-group [value]="filtre()" (change)="filtre.set($event.value)" hideSingleSelectionIndicator>
            <mat-button-toggle value="TOUTES">{{ 'STOCK.INVENTORY.FILTER_ALL' | translate }}</mat-button-toggle>
            <mat-button-toggle value="A_COMPTER"
                               data-testid="filter-to-count">{{ 'STOCK.INVENTORY.FILTER_TO_COUNT' | translate }}
            </mat-button-toggle>
            <mat-button-toggle value="ECARTS">{{ 'STOCK.INVENTORY.FILTER_GAPS' | translate }}</mat-button-toggle>
            <mat-button-toggle value="COMPTEES">{{ 'STOCK.INVENTORY.FILTER_COUNTED' | translate }}</mat-button-toggle>
          </mat-button-toggle-group>
          <mat-form-field appearance="outline" class="search" subscriptSizing="dynamic">
            <mat-icon matPrefix>search</mat-icon>
            <input matInput [value]="recherche()" (input)="recherche.set($any($event.target).value)"
                   [placeholder]="'STOCK.INVENTORY.SEARCH' | translate" data-testid="inventory-search"/>
          </mat-form-field>
          @if (store.enCours()) {
            <mat-slide-toggle [checked]="aveugle()" (change)="aveugle.set($event.checked)"
                              [matTooltip]="'STOCK.INVENTORY.BLIND_HINT' | translate">
              {{ 'STOCK.INVENTORY.BLIND' | translate }}
            </mat-slide-toggle>
          }
          <span class="spacer"></span>
          <button mat-stroked-button type="button" (click)="downloadSheet()" data-testid="inventory-sheet">
            <mat-icon>download</mat-icon>
            {{ 'STOCK.INVENTORY.COUNT_SHEET' | translate }}
          </button>
          @if (store.enCours()) {
            <input #importInput type="file" hidden accept=".xlsx,.xls,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                   (change)="importSheet(importInput)" data-testid="inventory-import-input"/>
            <button mat-stroked-button type="button" (click)="importInput.click()" [disabled]="store.busy()"
                    [matTooltip]="'STOCK.INVENTORY.IMPORT_SHEET_HINT' | translate" data-testid="inventory-import">
              <mat-icon>upload_file</mat-icon>{{ 'STOCK.INVENTORY.IMPORT_SHEET' | translate }}
            </button>
            <button mat-stroked-button type="button" (click)="addLine()" data-testid="inventory-add-line">
              <mat-icon>add</mat-icon>
              {{ 'STOCK.INVENTORY.ADD_LINE' | translate }}
            </button>
          }
        </div>

        @if (store.busy()) {
          <mat-progress-bar mode="indeterminate"/>
        }
        @if (store.importResult(); as res) {
          <div class="import-result" [class.has-issues]="res.anomalies.length > 0" role="status" data-testid="inventory-import-result">
            <div class="import-head">
              <mat-icon>{{ res.anomalies.length ? 'rule' : 'task_alt' }}</mat-icon>
              <strong>{{ 'STOCK.INVENTORY.IMPORT_DONE' | translate }}</strong>
              <span class="pill ok">{{ 'STOCK.INVENTORY.IMPORT_UPDATED' | translate: {count: res.lignesMisesAJour} }}</span>
              <span class="pill">{{ 'STOCK.INVENTORY.IMPORT_UNCHANGED' | translate: {count: res.lignesInchangees} }}</span>
              <span class="pill">{{ 'STOCK.INVENTORY.IMPORT_EMPTY' | translate: {count: res.lignesVides} }}</span>
              @if (res.anomalies.length) {
                <span class="pill warn">{{ 'STOCK.INVENTORY.IMPORT_ISSUES' | translate: {count: res.anomalies.length} }}</span>
              }
              <span class="spacer"></span>
              <button mat-icon-button type="button" (click)="store.clearImportResult()"
                      [attr.aria-label]="'COMMON.OK' | translate"><mat-icon>close</mat-icon></button>
            </div>
            @if (res.anomalies.length) {
              <ul class="issues">
                @for (a of res.anomalies; track $index) {
                  <li><strong>{{ 'STOCK.INVENTORY.IMPORT_ROW' | translate: {row: a.ligne} }}</strong> {{ a.message }}</li>
                }
              </ul>
            }
          </div>
        }

        <div class="table-wrapper">
          <table class="lines" data-testid="inventory-lines">
            <thead>
            <tr>
              <th>{{ 'STOCK.INVENTORY.ARTICLE' | translate }}</th>
              <th>{{ 'STOCK.INVENTORY.LOT' | translate }}</th>
              <th>{{ 'STOCK.INVENTORY.EXPIRY' | translate }}</th>
              @if (!masquerTheorique()) {
                <th class="num">{{ 'STOCK.INVENTORY.THEORETICAL' | translate }}</th>
              }
              <th class="num">{{ 'STOCK.INVENTORY.COUNTED' | translate }}</th>
              @if (!masquerTheorique()) {
                <th class="num">{{ 'STOCK.INVENTORY.GAP' | translate }}</th>
                <th class="num">{{ 'STOCK.INVENTORY.COL_GAP_VALUE' | translate }}</th>
              }
              <th>{{ 'STOCK.INVENTORY.REASON' | translate }}</th>
              <th></th>
            </tr>
            </thead>
            <tbody>
              @for (l of lignes(); track l.id) {
                @let e = ecartAffiche(l);
                <tr [class.done]="l.quantiteComptee !== null" [class.gap]="!masquerTheorique() && e !== null && e !== 0"
                    [attr.data-testid]="'line-' + l.id">
                  <td>
                    <strong>{{ l.articleCode }}</strong> {{ l.articleLibelle }}
                    @if (l.ajoutee) {
                      <span class="tag">{{ 'STOCK.INVENTORY.ADDED' | translate }}</span>
                    }
                    @if (l.unite) {
                      <small class="muted"> · {{ l.unite }}</small>
                    }
                  </td>
                  <td>{{ l.numeroLot ?? '-' }}</td>
                  <td>
                    @if (l.datePeremption) {
                      <span
                        [class]="'exp exp-' + (peremption(l) ?? 'OK')">{{ l.datePeremption | date: 'dd/MM/yyyy' }}</span>
                    } @else {
                      -
                    }
                  </td>
                  @if (!masquerTheorique()) {
                    <td class="num">{{ l.quantiteTheorique | number: '1.0-3' }}</td>
                  }
                  <td class="num">
                    @if (store.enCours()) {
                      <input class="qty" type="number" min="0" step="any" inputmode="decimal"
                             [value]="saisie(l)" [attr.aria-label]="'STOCK.INVENTORY.COUNTED' | translate"
                             [disabled]="store.savingLineId() === l.id"
                             (input)="setDraft(l.id, $any($event.target).value)"
                             (keydown.enter)="$any($event.target).blur()"
                             (blur)="saveCount(l)" [attr.data-testid]="'qty-' + l.id"/>
                    } @else {
                      {{ l.quantiteComptee ?? '-' }}
                    }
                  </td>
                  @if (!masquerTheorique()) {
                    <td class="num" [class.neg]="(e ?? 0) < 0" [class.pos]="(e ?? 0) > 0">
                      {{ e === null ? '-' : (e > 0 ? '+' : '') + (e | number: '1.0-3') }}
                    </td>
                    <td class="num" [class.neg]="(l.valeurEcart ?? 0) < 0" [class.pos]="(l.valeurEcart ?? 0) > 0">
                      {{ l.valeurEcart === null ? '-' : (l.valeurEcart | number: '1.2-2') }}
                    </td>
                  }
                  <td>
                    @if (store.enCours()) {
                      <mat-select class="motif" [value]="l.motifEcart"
                                  [placeholder]="'STOCK.INVENTORY.REASON' | translate"
                                  [class.missing]="l.ecart !== null && l.ecart !== 0 && !l.motifEcart"
                                  [disabled]="l.quantiteComptee === null || store.savingLineId() === l.id"
                                  (selectionChange)="saveReason(l, $event.value)">
                        <mat-option [value]="null">—</mat-option>
                        @for (m of motifs; track m) {
                          <mat-option [value]="m">{{ 'STOCK.INVENTORY.REASONS.' + m | translate }}</mat-option>
                        }
                      </mat-select>
                    } @else if (l.motifEcart) {
                      {{ 'STOCK.INVENTORY.REASONS.' + l.motifEcart | translate }}
                    }
                  </td>
                  <td class="actions">
                    @if (store.savingLineId() === l.id) {
                      <mat-icon class="spin">sync</mat-icon>
                    } @else if (l.quantiteComptee !== null) {
                      <mat-icon class="ok"
                                [matTooltip]="(l.comptePar ?? '') + ' ' + (l.compteLe | date: 'dd/MM HH:mm')">
                        check_circle
                      </mat-icon>
                    }
                    @if (store.enCours() && l.ajoutee) {
                      <button mat-icon-button type="button" (click)="removeLine(l)"
                              [attr.aria-label]="'STOCK.INVENTORY.REMOVE_LINE' | translate">
                        <mat-icon>delete</mat-icon>
                      </button>
                    }
                  </td>
                </tr>
              } @empty {
                <tr>
                  <td colspan="9" class="empty">{{ 'STOCK.INVENTORY.NO_LINES' | translate }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>

        @if (store.enCours() && canManage()) {
          <div class="footer">
            <button mat-stroked-button color="warn" type="button" (click)="cancel()" [disabled]="store.busy()"
                    data-testid="inventory-cancel">
              <mat-icon>cancel</mat-icon>
              {{ 'STOCK.INVENTORY.CANCEL' | translate }}
            </button>
            <button mat-stroked-button type="button" (click)="fillTheoretical()" [disabled]="store.busy()"
                    [matTooltip]="'STOCK.INVENTORY.FILL_THEORETICAL_HINT' | translate">
              <mat-icon>content_copy</mat-icon>
              {{ 'STOCK.INVENTORY.FILL_THEORETICAL' | translate }}
            </button>
            <span class="spacer"></span>
            @if (!store.pretACloturer()) {
              <small class="muted">{{ 'STOCK.INVENTORY.CLOSE_BLOCKED' | translate }}</small>
            }
            <button mat-flat-button color="primary" type="button" (click)="close()"
                    [disabled]="store.busy() || !store.pretACloturer()" data-testid="inventory-close">
              <mat-icon>lock</mat-icon>
              {{ 'STOCK.INVENTORY.CLOSE' | translate }}
            </button>
          </div>
        }
      }
    </section>
  `,
  styles: [`
    .back {
      margin-bottom: 8px;
    }

    .feedback.error {
      padding: 10px 16px;
      border-radius: 10px;
      background: #fce4ec;
      color: #c62828;
    }

    .head {
      display: grid;
      gap: 14px;
    }

    .app-section-title {
      display: flex;
      align-items: center;
      gap: 12px;
      flex-wrap: wrap;
    }

    .comment {
      display: flex;
      align-items: center;
      gap: 6px;
      color: var(--app-muted);
      margin: 4px 0 0;
    }

    .kpis {
      display: grid;
      grid-template-columns: repeat(4, minmax(0, 1fr));
      gap: 10px;
    }

    .kpi {
      display: flex;
      flex-direction: column;
      gap: 2px;
      padding: 10px 12px;
      border-radius: 12px;
      border: 1px solid var(--app-border);
      background: var(--app-surface);
    }

    .kpi span {
      font-size: 12px;
      color: var(--app-muted);
    }

    .kpi strong {
      font-size: 20px;
    }

    .kpi.warn strong {
      color: #b45309;
    }

    .kpi.neg strong, .neg {
      color: #c62828;
      font-weight: 600;
    }

    .kpi.pos strong, .pos {
      color: #15803d;
      font-weight: 600;
    }

    .progress {
      display: flex;
      align-items: center;
      gap: 10px;
    }

    .progress mat-progress-bar {
      flex: 1;
    }

    .chip {
      padding: 3px 10px;
      border-radius: 10px;
      font-size: 12px;
      font-weight: 600;
    }

    .st-EN_COURS {
      background: #fef3c7;
      color: #92400e;
    }

    .st-CLOTURE {
      background: #dcfce7;
      color: #166534;
    }

    .st-ANNULE {
      background: #f1f5f9;
      color: #475569;
    }

    .toolbar {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 12px;
      margin: 16px 0 10px;
    }

    .search {
      width: 260px;
    }

    .spacer {
      flex: 1;
    }

    .table-wrapper {
      overflow-x: auto;
      border: 1px solid var(--app-border);
      border-radius: 14px;
      background: var(--app-surface);
    }

    .lines {
      width: 100%;
      border-collapse: collapse;
      font-size: 14px;
    }

    .lines th {
      text-align: left;
      padding: 10px 12px;
      color: var(--app-primary);
      font-weight: 700;
      border-bottom: 1px solid var(--app-border);
      white-space: nowrap;
    }

    .lines td {
      padding: 6px 12px;
      border-bottom: 1px solid var(--app-border);
      vertical-align: middle;
    }

    .lines tr.done {
      background: color-mix(in srgb, #16a34a 4%, transparent);
    }

    .lines tr.gap {
      background: color-mix(in srgb, #f59e0b 8%, transparent);
    }

    .num {
      text-align: right;
      white-space: nowrap;
    }

    .qty {
      width: 100px;
      padding: 6px 8px;
      border-radius: 8px;
      border: 1px solid var(--app-border);
      text-align: right;
      font: inherit;
      background: var(--app-surface);
      color: inherit;
    }

    .qty:focus {
      outline: 2px solid var(--app-primary);
      border-color: transparent;
    }

    .motif {
      min-width: 150px;
    }

    .motif.missing {
      outline: 2px solid #f59e0b;
      border-radius: 6px;
    }

    .tag {
      margin-left: 6px;
      padding: 1px 6px;
      border-radius: 6px;
      font-size: 11px;
      background: #e0f2fe;
      color: #075985;
    }

    .muted {
      color: var(--app-muted);
    }

    .exp-EXPIRE {
      color: #c62828;
      font-weight: 600;
    }

    .exp-PROCHE {
      color: #b45309;
      font-weight: 600;
    }

    .actions {
      white-space: nowrap;
      text-align: right;
    }

    .ok {
      color: #16a34a;
      vertical-align: middle;
    }

    .spin {
      animation: spin 1s linear infinite;
      color: var(--app-muted);
      vertical-align: middle;
    }

    @keyframes spin {
      to {
        transform: rotate(360deg);
      }
    }

    .empty {
      text-align: center;
      padding: 30px 0;
      color: var(--app-muted);
    }

    .footer {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 12px;
      margin-top: 16px;
    }

    .import-result { margin: 0 0 12px; padding: 12px 16px; border-radius: 14px; border: 1px solid #bbf7d0; background: #f0fdf4; }
    .import-result.has-issues { border-color: #fcd34d; background: #fffbeb; }
    .import-head { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
    .import-head > mat-icon { color: #16a34a; }
    .has-issues .import-head > mat-icon { color: #b45309; }
    .pill { padding: 2px 10px; border-radius: 999px; font-size: 12px; background: var(--app-surface); border: 1px solid var(--app-border); }
    .pill.ok { color: #166534; border-color: #86efac; }
    .pill.warn { color: #92400e; border-color: #fcd34d; }
    .issues { margin: 8px 0 0; padding-left: 20px; max-height: 220px; overflow: auto; display: grid; gap: 4px; font-size: 13px; }

    @media (max-width: 900px) {
      .kpis {
        grid-template-columns: repeat(2, minmax(0, 1fr));
      }
      .search {
        width: 100%;
      }
    }
  `],
})
export class InventaireDetailComponent {
  protected readonly store = inject(InventaireStore);
  protected readonly motifs = MOTIFS_ECART;
  protected readonly filtre = signal<FiltreLignes>('TOUTES');
  protected readonly recherche = signal('');
  /** Comptage « à l'aveugle » : théorique et écarts masqués pendant la saisie (bonne pratique). */
  protected readonly aveugle = signal(false);
  protected readonly inv = computed(() => {
    const current = this.store.current();
    return current?.id === this.id() ? current : null;
  });
  protected readonly masquerTheorique = computed(() => this.store.enCours() && this.aveugle());
  protected readonly lignes = computed(() => filtrerLignes(this.inv()?.details ?? [], this.filtre(), this.recherche()));
  private readonly route = inject(ActivatedRoute);
  private readonly params = toSignal(this.route.paramMap, {initialValue: this.route.snapshot.paramMap});
  protected readonly id = computed(() => this.params().get('id') ?? '');
  private readonly api = inject(InventaireApiService);
  private readonly appShell = inject(AppShellStore);
  private readonly auth = inject(AuthStore);
  protected readonly canManage = computed(() => this.auth.hasRole('ADMIN') || this.auth.hasRole('PHARMACIEN'));
  private readonly ws = inject(WebSocketService);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly translate = inject(TranslateService);
  /** Saisies en cours, non encore enregistrées (clé : id de ligne). */
  private readonly drafts = signal<Record<string, string>>({});

  constructor() {
    effect(() => {
      const id = this.id();
      if (!id || !this.appShell.currentCenterId()) return;
      untracked(() => void this.store.load(id));
    });
    effect(() => {
      const evt = this.ws.lastEvent();
      if (evt?.type === 'STOCK_INVENTORY_CHANGED') {
        untracked(() => {
          if (this.store.savingLineId() || this.store.busy()) return;
          void this.store.load(this.id());
        });
      }
    });
  }

  protected peremption(l: LigneInventaire): 'EXPIRE' | 'PROCHE' | null {
    return etatPeremption(l.datePeremption);
  }

  protected saisie(l: LigneInventaire): string {
    const draft = this.drafts()[l.id];
    if (draft !== undefined) return draft;
    return l.quantiteComptee === null ? '' : String(l.quantiteComptee);
  }

  protected ecartAffiche(l: LigneInventaire): number | null {
    const draft = this.drafts()[l.id];
    if (draft === undefined) return l.ecart;
    return draft === '' || Number.isNaN(Number(draft)) ? null : ecart(l, Number(draft));
  }

  protected setDraft(id: string, value: string): void {
    this.drafts.update((d) => ({...d, [id]: value}));
  }

  protected async saveCount(l: LigneInventaire): Promise<void> {
    const draft = this.drafts()[l.id];
    if (draft === undefined) return;
    const qty = Number(draft);
    if (draft === '' || Number.isNaN(qty) || qty < 0 || qty === l.quantiteComptee) {
      this.clearDraft(l.id);
      return;
    }
    const ok = await this.store.compter(l.id, qty, l.motifEcart);
    if (ok) this.clearDraft(l.id);
  }

  protected async saveReason(l: LigneInventaire, motif: string | null): Promise<void> {
    if (l.quantiteComptee === null) return;
    await this.store.compter(l.id, l.quantiteComptee, motif);
  }

  protected async addLine(): Promise<void> {
    await firstValueFrom(this.dialog.open(InventaireAddLineDialogComponent, {
      width: '640px',
      maxWidth: '95vw'
    }).afterClosed());
  }

  protected async removeLine(l: LigneInventaire): Promise<void> {
    if (!await this.confirm('STOCK.INVENTORY.REMOVE_LINE', 'STOCK.INVENTORY.REMOVE_LINE_CONFIRM', 'delete', 'warn')) return;
    await this.store.retirerLigne(l.id);
  }

  protected async fillTheoretical(): Promise<void> {
    if (!await this.confirm('STOCK.INVENTORY.FILL_THEORETICAL', 'STOCK.INVENTORY.FILL_THEORETICAL_CONFIRM', 'content_copy', 'accent')) return;
    this.drafts.set({});
    await this.store.reporterTheorique();
  }

  protected async cancel(): Promise<void> {
    if (!await this.confirm('STOCK.INVENTORY.CANCEL', 'STOCK.INVENTORY.CANCEL_CONFIRM', 'cancel', 'warn')) return;
    if (await this.store.annuler()) void this.router.navigate(['/stock/inventaires']);
  }

  protected async close(): Promise<void> {
    const inv = this.inv();
    if (!inv) return;
    const confirmed = await firstValueFrom(this.dialog.open(InventaireCloseDialogComponent, {
      data: inv, width: '640px', maxWidth: '95vw',
    }).afterClosed());
    if (confirmed) await this.store.cloturer();
  }

  protected downloadSheet(): void {
    const inv = this.inv();
    const center = this.appShell.currentCenterId();
    if (!inv || !center) return;
    this.api.feuilleComptage(center, inv.id, this.store.enCours()).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `feuille-comptage-${inv.reference}.xlsx`;
        a.click();
        URL.revokeObjectURL(url);
      },
    });
  }

  /** Import de la feuille de comptage remplie : les saisies en cours à l'écran sont remplacées par le fichier. */
  protected async importSheet(input: HTMLInputElement): Promise<void> {
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    this.drafts.set({});
    await this.store.importerFeuille(file);
  }

  private clearDraft(id: string): void {
    this.drafts.update((d) => {
      const {[id]: _, ...rest} = d;
      return rest;
    });
  }

  private async confirm(title: string, message: string, icon: string, color: 'warn' | 'accent'): Promise<boolean> {
    return !!await firstValueFrom(this.dialog.open(ConfirmDialogComponent, {
      data: {
        title: this.translate.instant(title),
        message: this.translate.instant(message),
        confirmLabel: this.translate.instant(title),
        cancelLabel: this.translate.instant('COMMON.CANCEL'),
        color,
        icon,
      },
      maxWidth: '95vw',
    }).afterClosed());
  }
}










