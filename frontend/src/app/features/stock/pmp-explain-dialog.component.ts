import {ChangeDetectionStrategy, Component, inject, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {MAT_DIALOG_DATA, MatDialogModule, MatDialogRef} from '@angular/material/dialog';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatTableModule} from '@angular/material/table';
import {MatChipsModule} from '@angular/material/chips';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {TranslateModule} from '@ngx-translate/core';
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
    MatTableModule, MatChipsModule, MatProgressBarModule, TranslateModule,
  ],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './pmp-explain-dialog.component.html',
  styleUrl: './pmp-explain-dialog.component.css',
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

