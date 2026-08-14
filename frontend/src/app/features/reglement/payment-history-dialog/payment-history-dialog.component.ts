import {ChangeDetectionStrategy, Component, computed, inject, signal} from '@angular/core';
import {DecimalPipe} from '@angular/common';
import {MAT_DIALOG_DATA, MatDialogModule, MatDialogRef} from '@angular/material/dialog';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatTableModule} from '@angular/material/table';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {TranslateModule} from '@ngx-translate/core';
import {rxMethod} from '@ngrx/signals/rxjs-interop';
import {pipe, switchMap, tap, catchError, EMPTY} from 'rxjs';
import {BackendApiService, FacturePaymentItem, ReglementInvoiceRow} from '../../../core/api/backend-api.service';

export type PaymentHistoryDialogData = {
  row: ReglementInvoiceRow;
  centerId: string;
};

@Component({
  selector: 'app-payment-history-dialog',
  standalone: true,
  imports: [
    DecimalPipe,
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    MatTableModule,
    MatProgressBarModule,
    TranslateModule,
  ],
  templateUrl: './payment-history-dialog.component.html',
  styleUrl: './payment-history-dialog.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PaymentHistoryDialogComponent {
  protected readonly data = inject<PaymentHistoryDialogData>(MAT_DIALOG_DATA);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly payments = signal<FacturePaymentItem[]>([]);
  protected readonly totalPaid = computed(() =>
    this.payments().reduce((acc, p) => acc + Number(p.montant ?? 0), 0)
  );
  protected readonly displayedColumns = ['codeReglement', 'dateReglement', 'montant', 'saisiPar'];
  private readonly api = inject(BackendApiService);
  private readonly dialogRef = inject(MatDialogRef<PaymentHistoryDialogComponent>);
  private readonly loadHistory = rxMethod<{ factureId: string; centerId: string }>(
    pipe(
      tap(() => {
        this.loading.set(true);
        this.error.set(null);
      }),
      switchMap(({factureId, centerId}) =>
        this.api.getFacturePayments(factureId, centerId).pipe(
          tap((items) => {
            this.payments.set(items);
            this.loading.set(false);
          }),
          catchError(() => {
            this.error.set('REGLEMENT_MODULE.HISTORY.ERROR');
            this.loading.set(false);
            return EMPTY;
          })
        )
      )
    )
  );

  constructor() {
    this.loadHistory({factureId: this.data.row.factureId, centerId: this.data.centerId});
  }

  protected close(): void {
    this.dialogRef.close();
  }
}


