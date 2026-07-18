import {ChangeDetectionStrategy, Component, inject, OnDestroy, OnInit, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {MAT_DIALOG_DATA, MatDialogModule, MatDialogRef} from '@angular/material/dialog';
import {MatButtonModule} from '@angular/material/button';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatIconModule} from '@angular/material/icon';
import {StockApiService} from '../../core/api/stock-api.service';

export interface PmpRecalcDialogData {
  centerId: string;
  articleIds: string[];
}

@Component({
  selector: 'app-pmp-recalc-dialog',
  standalone: true,
  imports: [CommonModule, MatDialogModule, MatButtonModule, MatProgressBarModule, MatIconModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './pmp-recalc-dialog.component.html',
  styleUrl: './pmp-recalc-dialog.component.css',
})
export class PmpRecalcDialogComponent implements OnInit, OnDestroy {
  protected readonly status = signal<'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED'>('PENDING');
  protected readonly processed = signal(0);
  protected readonly total = signal(0);
  protected readonly message = signal('Demarrage du recalcul...');
  protected readonly progressPercent = signal(0);
  private readonly api = inject(StockApiService);
  private readonly dialogRef = inject(MatDialogRef<PmpRecalcDialogComponent>);
  private readonly data = inject<PmpRecalcDialogData>(MAT_DIALOG_DATA);
  private pollTimer: ReturnType<typeof setTimeout> | null = null;

  ngOnInit(): void {
    this.api.startPmpRecalc(this.data.centerId, this.data.articleIds).subscribe({
      next: ({jobId}) => this.poll(jobId),
      error: () => {
        this.status.set('FAILED');
        this.message.set('Impossible de demarrer le recalcul.');
      },
    });
  }

  ngOnDestroy(): void {
    if (this.pollTimer) {
      clearTimeout(this.pollTimer);
    }
  }

  protected close(): void {
    this.dialogRef.close();
  }

  private poll(jobId: string): void {
    this.api.getPmpRecalcJob(jobId).subscribe({
      next: (job) => {
        this.status.set(job.status);
        this.processed.set(job.processed);
        this.total.set(job.total);
        this.message.set(job.message);
        const percent = job.total > 0 ? Math.round((job.processed / job.total) * 100) : 0;
        this.progressPercent.set(percent);

        if (job.status === 'PENDING' || job.status === 'RUNNING') {
          this.pollTimer = setTimeout(() => this.poll(jobId), 1000);
        }
      },
      error: () => {
        this.status.set('FAILED');
        this.message.set('Erreur de suivi du recalcul.');
      },
    });
  }
}

