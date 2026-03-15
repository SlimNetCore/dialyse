import { Component, EventEmitter, Input, Output } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';

@Component({
  selector: 'app-patient-form',
  standalone: true,
  imports: [MatCardModule, MatButtonModule, MatIconModule, TranslateModule],
  template: `
    <mat-card class="card">
      <mat-card-header>
        <mat-icon mat-card-avatar class="header-icon">person</mat-icon>
        <mat-card-title>{{ 'PATIENT.TITLE' | translate }}</mat-card-title>
        <mat-card-subtitle>{{ 'PATIENT.SUBTITLE' | translate }}</mat-card-subtitle>
      </mat-card-header>

      <mat-card-content>
        @if (patientId) {
          <div class="id-badge">
            <mat-icon>badge</mat-icon>
            <span>{{ patientId }}</span>
          </div>
        } @else {
          <p class="empty">{{ 'PATIENT.EMPTY' | translate }}</p>
        }
      </mat-card-content>

      <mat-card-actions align="end">
        <button mat-flat-button color="primary" (click)="create.emit()">
          <mat-icon>person_add</mat-icon>
          {{ 'PATIENT.CREATE' | translate }}
        </button>
      </mat-card-actions>
    </mat-card>
  `,
  styles: [`
    .card { padding: 4px; }

    .header-icon {
      background: #e8f5e9;
      color: #1b5e20;
      border-radius: 12px;
      display: flex;
      align-items: center;
      justify-content: center;
      width: 40px;
      height: 40px;
    }

    .id-badge {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 10px 14px;
      background: #f0fdf4;
      border: 1px solid #bbf7d0;
      border-radius: 10px;
      font-family: 'Courier New', monospace;
      font-size: 13px;
      color: #166534;
      word-break: break-all;
    }

    .empty {
      color: #94a3b8;
      font-style: italic;
      font-size: 14px;
    }
  `]
})
export class PatientFormComponent {
  @Input() patientId: string | null = null;
  @Output() create = new EventEmitter<void>();
}
