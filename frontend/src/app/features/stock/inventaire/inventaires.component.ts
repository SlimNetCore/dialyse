import {ChangeDetectionStrategy, Component, computed, effect, inject, untracked} from '@angular/core';
import {DatePipe, DecimalPipe} from '@angular/common';
import {Router, RouterLink} from '@angular/router';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatTableModule} from '@angular/material/table';
import {MatPaginatorModule, PageEvent} from '@angular/material/paginator';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatExpansionModule} from '@angular/material/expansion';
import {MatDialog} from '@angular/material/dialog';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {firstValueFrom} from 'rxjs';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {AuthStore} from '../../../core/state/auth.store';
import {InventaireResume} from '../../../core/api/inventaire-api.service';
import {ConfirmDialogComponent} from '../../../shared/confirm-dialog.component';
import {requiredValidator, SignalForm} from '../../../shared/forms/signal-form';
import {InventaireStore} from './state/inventaire.store';
import {progression} from './inventaire.util';

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
    MatTableModule, MatPaginatorModule, MatProgressBarModule, MatExpansionModule, TranslateModule],
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
      @if (store.loading()) {
        <mat-progress-bar mode="indeterminate"/>
      }
      @if (store.total() === 0 && !store.loading()) {
        <div class="empty">
          <mat-icon>inventory_2</mat-icon>
          <p>{{ 'STOCK.INVENTORY.EMPTY' | translate }}</p>
        </div>
      } @else {
        <div class="table-wrapper">
          <table mat-table [dataSource]="store.history()" class="data-table" data-testid="inventory-history">
            <ng-container matColumnDef="reference">
              <th mat-header-cell *matHeaderCellDef>{{ 'STOCK.INVENTORY.COL_REF' | translate }}</th>
              <td mat-cell *matCellDef="let i" data-label="Réf."><strong>{{ i.reference }}</strong></td>
            </ng-container>
            <ng-container matColumnDef="date">
              <th mat-header-cell *matHeaderCellDef>{{ 'STOCK.INVENTORY.DATE' | translate }}</th>
              <td mat-cell *matCellDef="let i">{{ i.dateInventaire | date: 'dd/MM/yyyy' }}</td>
            </ng-container>
            <ng-container matColumnDef="statut">
              <th mat-header-cell *matHeaderCellDef>{{ 'STOCK.INVENTORY.COL_STATUS' | translate }}</th>
              <td mat-cell *matCellDef="let i">
                <span class="chip"
                      [class]="'chip st-' + i.statut">{{ 'STOCK.INVENTORY.STATUS.' + i.statut | translate }}</span>
              </td>
            </ng-container>
            <ng-container matColumnDef="progression">
              <th mat-header-cell *matHeaderCellDef>{{ 'STOCK.INVENTORY.COL_PROGRESS' | translate }}</th>
              <td mat-cell *matCellDef="let i">
                <div class="mini-progress"><span [style.width.%]="percent(i)"></span></div>
                <small>{{ i.comptees }}/{{ i.lignes }}</small>
              </td>
            </ng-container>
            <ng-container matColumnDef="ecarts">
              <th mat-header-cell *matHeaderCellDef>{{ 'STOCK.INVENTORY.COL_GAPS' | translate }}</th>
              <td mat-cell *matCellDef="let i">{{ i.ecarts }}</td>
            </ng-container>
            <ng-container matColumnDef="valeur">
              <th mat-header-cell *matHeaderCellDef>{{ 'STOCK.INVENTORY.COL_GAP_VALUE' | translate }}</th>
              <td mat-cell *matCellDef="let i" [class.neg]="i.valeurEcarts < 0" [class.pos]="i.valeurEcarts > 0">
                {{ i.valeurEcarts | number: '1.2-2' }}
              </td>
            </ng-container>
            <ng-container matColumnDef="par">
              <th mat-header-cell *matHeaderCellDef>{{ 'STOCK.INVENTORY.COL_BY' | translate }}</th>
              <td mat-cell *matCellDef="let i">{{ i.closedBy ?? i.createdBy ?? '-' }}</td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="columns"></tr>
            <tr mat-row *matRowDef="let row; columns: columns" class="clickable" (click)="openDetail(row)"></tr>
          </table>
          <mat-paginator [length]="store.total()" [pageIndex]="store.pageIndex()" [pageSize]="store.pageSize()"
                         [pageSizeOptions]="[10, 20, 50, 100]" (page)="onPage($event)"/>
        </div>
      }
    </section>
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
      overflow-x: auto;
      border: 1px solid var(--app-border);
      border-radius: 14px;
      background: var(--app-surface);
    }

    .data-table {
      width: 100%;
    }

    .data-table .mat-mdc-header-cell {
      color: var(--app-primary);
      font-weight: 700;
    }

    .clickable {
      cursor: pointer;
    }

    .clickable:hover {
      background: color-mix(in srgb, var(--app-primary) 5%, transparent);
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

    .mini-progress {
      width: 90px;
      height: 6px;
      border-radius: 6px;
      background: var(--app-border);
      overflow: hidden;
      display: inline-block;
      vertical-align: middle;
      margin-right: 8px;
    }

    .mini-progress span {
      display: block;
      height: 100%;
      background: var(--app-primary);
    }

    .neg {
      color: #c62828;
      font-weight: 600;
    }

    .pos {
      color: #15803d;
      font-weight: 600;
    }

    .empty {
      text-align: center;
      padding: 40px 0;
      color: var(--app-muted);
    }

    .empty mat-icon {
      font-size: 44px;
      width: 44px;
      height: 44px;
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
  protected readonly columns = ['reference', 'date', 'statut', 'progression', 'ecarts', 'valeur', 'par'];
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

  protected openDetail(row: InventaireResume): void {
    void this.router.navigate(['/stock/inventaires', row.id]);
  }

  protected onPage(event: PageEvent): void {
    void this.store.setPage(event.pageIndex, event.pageSize);
  }
}

