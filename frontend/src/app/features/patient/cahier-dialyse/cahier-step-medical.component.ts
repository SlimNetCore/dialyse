import {ChangeDetectionStrategy, Component, EventEmitter, Input, Output} from '@angular/core';
import {CommonModule} from '@angular/common';
import {MatCardModule} from '@angular/material/card';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {TranslateModule} from '@ngx-translate/core';

@Component({
  selector: 'app-cahier-step-medical',
  standalone: true,
  imports: [CommonModule, MatCardModule, MatButtonModule, MatIconModule, TranslateModule],
  template: `
    <div class="step-content">
      <mat-card class="placeholder-card" data-testid="cahier-medical-placeholder">
        <mat-card-header>
          <mat-card-title>{{ 'CAHIER.STEP_MEDICAL' | translate }}</mat-card-title>
          <mat-card-subtitle>{{ 'CAHIER.STEP_MEDICAL_DESC' | translate }}</mat-card-subtitle>
        </mat-card-header>
        <mat-card-content>
          <div class="placeholder-message">
            <mat-icon>info</mat-icon>
            <p>{{ 'COMMON.COMING_SOON' | translate }}</p>
            <p class="detail">
              Dossier médical, prescriptions, résultats d'analyses, abords vasculaires et
              traitements.
            </p>
          </div>
          <div class="actions">
            <button mat-raised-button color="primary" (click)="proceed()">
              <mat-icon>arrow_forward</mat-icon>
              {{ 'COMMON.NEXT_STEP' | translate }}
            </button>
          </div>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.Eager,
  styles: [
    `
      .step-content {
        padding: 12px 0;
      }

      .placeholder-card {
        margin: 0;
      }

      .placeholder-message {
        text-align: center;
        padding: 40px 20px;
        color: var(--app-muted);
      }

      .placeholder-message mat-icon {
        font-size: 48px;
        width: 48px;
        height: 48px;
        margin-bottom: 16px;
        color: var(--app-muted);
      }

      .detail {
        font-size: 12px;
        margin-top: 8px;
      }

      .actions {
        display: flex;
        gap: 12px;
        justify-content: center;
        margin-top: 16px;
      }
    `,
  ],
})
export class CahierStepMedicalComponent {
  @Input() patientId!: string;
  @Input() readonly = false;
  @Output() dataChange = new EventEmitter<any>();
  @Output() validChange = new EventEmitter<boolean>();

  proceed(): void {
    this.validChange.emit(true);
  }
}
