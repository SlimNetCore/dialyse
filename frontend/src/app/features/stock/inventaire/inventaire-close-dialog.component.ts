import {ChangeDetectionStrategy, Component, inject} from '@angular/core';
import {DatePipe, DecimalPipe} from '@angular/common';
import {MAT_DIALOG_DATA, MatDialogModule, MatDialogRef} from '@angular/material/dialog';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {TranslateModule} from '@ngx-translate/core';
import {Inventaire} from '../../../core/api/inventaire-api.service';

/** Récapitulatif avant clôture : ce qui va changer, de façon définitive. */
@Component({
  selector: 'app-inventaire-close-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, DecimalPipe, MatDialogModule, MatButtonModule, MatIconModule, TranslateModule],
  template: `
    <h2 mat-dialog-title class="title">
      <mat-icon>task_alt</mat-icon>
      {{ 'STOCK.INVENTORY.CLOSE_TITLE' | translate: {ref: inv.reference} }}
    </h2>
    <mat-dialog-content>
      <div class="kpis">
        <div class="kpi"><span>{{ 'STOCK.INVENTORY.KPI_LINES' | translate }}</span><strong>{{ inv.lignes }}</strong>
        </div>
        <div class="kpi"><span>{{ 'STOCK.INVENTORY.KPI_GAPS' | translate }}</span><strong>{{ inv.ecarts }}</strong>
        </div>
        <div class="kpi" [class.neg]="inv.valeurEcarts < 0" [class.pos]="inv.valeurEcarts > 0">
          <span>{{ 'STOCK.INVENTORY.KPI_GAP_VALUE' | translate }}</span><strong>{{ inv.valeurEcarts | number: '1.2-2' }}</strong>
        </div>
        <div class="kpi">
          <span>{{ 'STOCK.INVENTORY.KPI_COUNTED_VALUE' | translate }}</span><strong>{{ inv.valeurComptee | number: '1.2-2' }}</strong>
        </div>
      </div>
      <p class="lead">{{ 'STOCK.INVENTORY.CLOSE_EFFECTS' | translate }}</p>
      <ul>
        <li>{{ 'STOCK.INVENTORY.CLOSE_EFFECT_1' | translate }}</li>
        <li>{{ 'STOCK.INVENTORY.CLOSE_EFFECT_2' | translate: {date: (inv.dateInventaire | date: 'dd/MM/yyyy')} }}</li>
        <li>{{ 'STOCK.INVENTORY.CLOSE_EFFECT_3' | translate }}</li>
        <li>{{ 'STOCK.INVENTORY.CLOSE_EFFECT_4' | translate }}</li>
      </ul>
      <p class="warn">
        <mat-icon>warning</mat-icon>
        {{ 'STOCK.INVENTORY.CLOSE_IRREVERSIBLE' | translate }}
      </p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-stroked-button type="button" (click)="ref.close(false)">{{ 'COMMON.CANCEL' | translate }}</button>
      <button mat-flat-button color="primary" type="button" (click)="ref.close(true)"
              data-testid="inventory-close-confirm">
        <mat-icon>lock</mat-icon>
        {{ 'STOCK.INVENTORY.CLOSE' | translate }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .title {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .kpis {
      display: grid;
      grid-template-columns: repeat(4, minmax(0, 1fr));
      gap: 10px;
      margin-bottom: 14px;
    }

    .kpi {
      display: flex;
      flex-direction: column;
      gap: 2px;
      padding: 10px 12px;
      border-radius: 12px;
      border: 1px solid var(--app-border);
    }

    .kpi span {
      font-size: 12px;
      color: var(--app-muted);
    }

    .kpi strong {
      font-size: 18px;
    }

    .kpi.neg strong {
      color: #c62828;
    }

    .kpi.pos strong {
      color: #15803d;
    }

    .lead {
      font-weight: 600;
      margin: 4px 0;
    }

    ul {
      margin: 0 0 10px;
      padding-left: 20px;
      display: grid;
      gap: 6px;
      color: var(--app-muted);
    }

    .warn {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 10px 12px;
      border-radius: 10px;
      background: #fff7ed;
      color: #9a3412;
      margin: 0;
    }

    @media (max-width: 767px) {
      .kpis {
        grid-template-columns: repeat(2, minmax(0, 1fr));
      }
    }
  `],
})
export class InventaireCloseDialogComponent {
  protected readonly inv = inject<Inventaire>(MAT_DIALOG_DATA);
  protected readonly ref = inject(MatDialogRef<InventaireCloseDialogComponent, boolean>);
}

