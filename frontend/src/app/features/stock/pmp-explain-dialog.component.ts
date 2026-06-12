import {ChangeDetectionStrategy, Component, inject, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {MAT_DIALOG_DATA, MatDialogModule, MatDialogRef} from '@angular/material/dialog';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatTableModule} from '@angular/material/table';
import {MatChipsModule} from '@angular/material/chips';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {PmpExplanation, StockApiService} from '../../core/api/stock-api.service';

export interface PmpExplainData {
  articleId: string;
  centerId: string;
  libelle: string;
}

@Component({
  selector: 'app-pmp-explain-dialog',
  standalone: true,
  imports: [
    CommonModule, MatDialogModule, MatButtonModule, MatIconModule,
    MatTableModule, MatChipsModule, MatProgressBarModule,
  ],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    <h2 mat-dialog-title>
      <mat-icon class="title-icon">calculate</mat-icon>
      Calcul du PMP — {{ data.libelle }}
    </h2>

    <mat-dialog-content>
      @if (loading()) {
        <mat-progress-bar mode="indeterminate"></mat-progress-bar>
      }

      <div class="methode">
        <mat-icon>info</mat-icon>
        <p>{{ explanation()?.methode }}</p>
      </div>

      @if (explanation(); as ex) {
        <div class="summary">
          <div class="pill"><span>Quantité en stock</span><strong>{{ ex.quantiteFinale | number:'1.0-2' }}</strong>
          </div>
          <div class="pill"><span>Valeur du stock</span><strong>{{ ex.valeurFinale | number:'1.0-2' }}</strong></div>
          <div class="pill highlight"><span>PMP courant</span><strong>{{ ex.pmpFinal | number:'1.0-4' }}</strong></div>
        </div>

        @if (ex.etapes.length === 0) {
          <p class="app-muted-note">Aucun mouvement de stock pour cet article : le PMP est à 0.</p>
        } @else {
          <table mat-table [dataSource]="ex.etapes" class="full-width">
            <ng-container matColumnDef="piece">
              <th mat-header-cell *matHeaderCellDef>Pièce</th>
              <td mat-cell *matCellDef="let s">{{ s.piece || '—' }}</td>
            </ng-container>
            <ng-container matColumnDef="datePiece">
              <th mat-header-cell *matHeaderCellDef>Date pièce</th>
              <td mat-cell *matCellDef="let s">{{ s.datePiece ? (s.datePiece | date:'dd/MM/yyyy') : '—' }}</td>
            </ng-container>
            <ng-container matColumnDef="type">
              <th mat-header-cell *matHeaderCellDef>Type</th>
              <td mat-cell *matCellDef="let s">
                <mat-chip [class.entry]="s.type === 'ENTREE'" [class.exit]="s.type === 'SORTIE'">{{ s.type }}</mat-chip>
              </td>
            </ng-container>
            <ng-container matColumnDef="qte">
              <th mat-header-cell *matHeaderCellDef>Qté</th>
              <td mat-cell *matCellDef="let s">{{ s.quantite | number:'1.0-2' }}</td>
            </ng-container>
            <ng-container matColumnDef="pu">
              <th mat-header-cell *matHeaderCellDef>PU</th>
              <td mat-cell *matCellDef="let s">{{ s.prixUnitaire != null ? (s.prixUnitaire | number:'1.0-2') : '—' }}
              </td>
            </ng-container>
            <ng-container matColumnDef="pmp">
              <th mat-header-cell *matHeaderCellDef>PMP après</th>
              <td mat-cell *matCellDef="let s">{{ s.pmpApres | number:'1.0-4' }}</td>
            </ng-container>
            <ng-container matColumnDef="formule">
              <th mat-header-cell *matHeaderCellDef>Formule</th>
              <td mat-cell *matCellDef="let s" class="formule">{{ s.formule }}</td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="cols"></tr>
            <tr mat-row *matRowDef="let row; columns: cols" [attr.data-step-date]="row?.date"></tr>
          </table>
        }
      }
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-flat-button color="primary" (click)="ref.close()">Fermer</button>
    </mat-dialog-actions>
  `,
  styles: [`
    .title-icon {
      vertical-align: middle;
      margin-right: 6px;
    }

    .methode {
      display: flex;
      gap: 10px;
      align-items: flex-start;
      background: var(--app-frost);
      border: 1px solid var(--app-border);
      border-radius: 14px;
      padding: 12px;
      margin-bottom: 16px;
    }

    .methode p {
      margin: 0;
      font-size: 13px;
      line-height: 1.5;
    }

    .summary {
      display: flex;
      gap: 12px;
      flex-wrap: wrap;
      margin-bottom: 16px;
    }

    .pill {
      display: grid;
      gap: 2px;
      padding: 8px 14px;
      border: 1px solid var(--app-border);
      border-radius: 14px;
      min-width: 140px;
    }

    .pill span {
      font-size: 11px;
      color: var(--app-muted);
      text-transform: uppercase;
    }

    .pill strong {
      font-size: 18px;
    }

    .pill.highlight {
      border-color: var(--app-accent, #1565c0);
    }

    .full-width {
      width: 100%;
    }

    .formule {
      font-family: 'JetBrains Mono', monospace;
      font-size: 11px;
      white-space: nowrap;
    }

    mat-chip.entry {
      background: #e8f5e9;
    }

    mat-chip.exit {
      background: #fff3e0;
    }
  `],
})
export class PmpExplainDialogComponent {
  protected readonly data = inject<PmpExplainData>(MAT_DIALOG_DATA);
  protected readonly ref = inject(MatDialogRef<PmpExplainDialogComponent>);
  protected readonly cols = ['piece', 'datePiece', 'type', 'qte', 'pu', 'pmp', 'formule'];
  protected readonly loading = signal(true);
  protected readonly explanation = signal<PmpExplanation | null>(null);
  private readonly api = inject(StockApiService);

  constructor() {
    this.api.pmpExplain(this.data.articleId, this.data.centerId).subscribe({
      next: (ex) => this.explanation.set(ex),
      complete: () => this.loading.set(false),
      error: () => this.loading.set(false),
    });
  }
}

