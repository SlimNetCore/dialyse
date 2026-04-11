import { Component, inject } from '@angular/core';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

export interface ConfirmDialogData {
  title: string;
  message: string;
  confirmLabel?: string;
  cancelLabel?: string;
  color?: 'warn' | 'primary' | 'accent';
  icon?: string;
}

@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule, MatIconModule],
  template: `
    <div class="confirm-dialog">
      <div class="dialog-header">
        <mat-icon class="dialog-icon" [class]="data.color || 'warn'">{{ data.icon || 'warning' }}</mat-icon>
        <h2 mat-dialog-title>{{ data.title }}</h2>
      </div>
      <mat-dialog-content>
        <p>{{ data.message }}</p>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-stroked-button (click)="dialogRef.close(false)">
          {{ data.cancelLabel || 'Annuler' }}
        </button>
        <button mat-flat-button [class]="'confirm-btn ' + (data.color || 'warn')" (click)="dialogRef.close(true)">
          <mat-icon>{{ data.icon || 'delete' }}</mat-icon>
          {{ data.confirmLabel || 'Confirmer' }}
        </button>
      </mat-dialog-actions>
    </div>
  `,
  styles: [`
    .confirm-dialog { padding: 8px 4px; }
    .dialog-header {
      display: flex; align-items: center; gap: 12px; margin-bottom: 4px;
    }
    .dialog-icon {
      font-size: 28px; width: 28px; height: 28px;
    }
    .dialog-icon.warn { color: #d32f2f; }
    .dialog-icon.primary { color: #1b5e20; }
    .dialog-icon.accent { color: #ff6f00; }
    h2[mat-dialog-title] { margin: 0; font-size: 1.15rem; font-weight: 700; color: #1f2937; }
    mat-dialog-content p { color: #4b5563; font-size: 14px; line-height: 1.5; margin: 8px 0 0; }
    mat-dialog-actions { padding-top: 12px; }
    .confirm-btn.warn {
      --mdc-filled-button-container-color: #d32f2f;
      --mdc-filled-button-label-text-color: #fff;
    }
    .confirm-btn.primary {
      --mdc-filled-button-container-color: #1b5e20;
      --mdc-filled-button-label-text-color: #fff;
    }
    .confirm-btn.accent {
      --mdc-filled-button-container-color: #ff6f00;
      --mdc-filled-button-label-text-color: #fff;
    }
  `]
})
export class ConfirmDialogComponent {
  readonly data: ConfirmDialogData = inject(MAT_DIALOG_DATA);
  readonly dialogRef = inject(MatDialogRef<ConfirmDialogComponent>);
}

