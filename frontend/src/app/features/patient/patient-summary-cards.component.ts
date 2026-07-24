import {Component, EventEmitter, Input, Output, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {MatButtonModule} from '@angular/material/button';
import {MatCardModule} from '@angular/material/card';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatIconModule} from '@angular/material/icon';
import {MatInputModule} from '@angular/material/input';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {TranslateModule} from '@ngx-translate/core';
import {PatientSummary, SummaryBucket} from '../../core/api/backend-api.service';

function currentYearMonth(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
}

@Component({
  selector: 'app-patient-summary-cards',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MatFormFieldModule,
    MatInputModule,
    TranslateModule,
  ],
  templateUrl: './patient-summary-cards.component.html',
  styleUrl: './patient-summary-cards.component.css',
})
export class PatientSummaryCardsComponent {
  @Output() printReport = new EventEmitter<string>();
  @Output() monthChanged = new EventEmitter<string>();
  @Output() viewDetails = new EventEmitter<string>();
  private readonly summaryState = signal<PatientSummary | null>(null);
  readonly summary = this.summaryState.asReadonly();
  private readonly loadingState = signal(false);
  readonly loading = this.loadingState.asReadonly();
  private readonly printingState = signal(false);
  readonly printing = this.printingState.asReadonly();
  private readonly selectedMonthState = signal(currentYearMonth());
  readonly selectedMonth = this.selectedMonthState.asReadonly();

  @Input({alias: 'summary'})
  set summaryInput(value: PatientSummary | null) {
    this.summaryState.set(value);
  }

  @Input({alias: 'loading'})
  set loadingInput(value: boolean) {
    this.loadingState.set(value);
  }

  @Input({alias: 'printing'})
  set printingInput(value: boolean) {
    this.printingState.set(value);
  }

  @Input({alias: 'selectedMonth'})
  set selectedMonthInput(value: string | null | undefined) {
    this.selectedMonthState.set(value && /^\d{4}-\d{2}$/.test(value) ? value : currentYearMonth());
  }

  bucketWidth(bucket: SummaryBucket, buckets: SummaryBucket[]): number {
    if (bucket.count <= 0) {
      return 0;
    }
    const max = Math.max(...buckets.map((item) => item.count), 1);
    return Math.max(4, Math.round((bucket.count / max) * 100));
  }

  onSelectedMonthChange(event: Event): void {
    const value = (event.target as HTMLInputElement | null)?.value?.trim() ?? '';
    if (!/^\d{4}-\d{2}$/.test(value) || value === this.selectedMonth()) {
      return;
    }
    this.selectedMonthState.set(value);
    this.monthChanged.emit(value);
  }
}



