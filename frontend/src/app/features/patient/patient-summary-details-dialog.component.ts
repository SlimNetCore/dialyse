import {ChangeDetectionStrategy, Component, inject} from '@angular/core';
import {DatePipe} from '@angular/common';
import {MatButtonModule} from '@angular/material/button';
import {MAT_DIALOG_DATA, MatDialogModule, MatDialogRef} from '@angular/material/dialog';
import {MatIconModule} from '@angular/material/icon';
import {MatTableModule} from '@angular/material/table';
import {TranslateModule} from '@ngx-translate/core';
import {PatientSummaryDetailItem} from '../../core/api/backend-api.service';

export type PatientSummaryDetailsDialogData = {
  month: string;
  total: number;
  items: PatientSummaryDetailItem[];
};

@Component({
  selector: 'app-patient-summary-details-dialog',
  standalone: true,
  imports: [
    DatePipe,
    MatButtonModule,
    MatDialogModule,
    MatIconModule,
    MatTableModule,
    TranslateModule,
  ],
  templateUrl: './patient-summary-details-dialog.component.html',
  styleUrl: './patient-summary-details-dialog.component.css',
  changeDetection: ChangeDetectionStrategy.Eager,
})
export class PatientSummaryDetailsDialogComponent {
  readonly data = inject<PatientSummaryDetailsDialogData>(MAT_DIALOG_DATA);
  readonly dialogRef = inject(MatDialogRef<PatientSummaryDetailsDialogComponent>);
  readonly columns = ['codePatient', 'nomPrenom', 'sexe', 'etatPatient', 'dateAdmission', 'dateEvenementEtat', 'sousKt'];

  close(): void {
    this.dialogRef.close();
  }

  fullName(item: PatientSummaryDetailItem): string {
    return `${item.nom ?? ''} ${item.prenom ?? ''}`.trim();
  }

  eventDateValue(item: PatientSummaryDetailItem): string {
    return item.dateEvenementEtat || '-';
  }
}

