import { Component, EventEmitter, Input, Output } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';

@Component({
  selector: 'app-pec-workflow',
  standalone: true,
  imports: [MatCardModule, MatButtonModule, MatIconModule, TranslateModule],
  template: `
    <mat-card class="card">
      <mat-card-header>
        <mat-icon mat-card-avatar class="header-icon">receipt_long</mat-icon>
        <mat-card-title>{{ 'PEC.TITLE' | translate }}</mat-card-title>
        <mat-card-subtitle>{{ 'PEC.SUBTITLE' | translate }}</mat-card-subtitle>
      </mat-card-header>

      <mat-card-content>
        @if (pecId) {
          <div class="pec-info">
            <div class="pec-row">
              <span class="pec-label">{{ 'PEC.LABEL_PEC' | translate }}</span>
              <span class="pec-value mono">{{ pecId }}</span>
            </div>
            <div class="pec-row">
              <span class="pec-label">{{ 'PEC.LABEL_STATUS' | translate }}</span>
              <span class="pec-badge" [class]="pecStatus?.toLowerCase() || ''">
                {{ pecStatus || 'N/A' }}
              </span>
            </div>
            <div class="pec-row">
              <span class="pec-label">{{ 'PEC.LABEL_SESSION' | translate }}</span>
              <span class="pec-badge" [class.allowed]="sessionAllowed === true"
                    [class.denied]="sessionAllowed === false">
                {{ sessionAllowed === null ? '—' : (sessionAllowed ? ('PEC.ALLOWED' | translate) : ('PEC.DENIED' | translate)) }}
              </span>
            </div>
          </div>
        } @else {
          <p class="empty">{{ 'PEC.EMPTY' | translate }}</p>
        }
      </mat-card-content>

      <mat-card-actions>
        <div class="actions">
          <button mat-stroked-button (click)="create.emit()" [disabled]="!patientId">
            <mat-icon>add_circle</mat-icon> {{ 'PEC.BTN_CREATE' | translate }}
          </button>
          <button mat-stroked-button (click)="validate.emit()" [disabled]="!pecId">
            <mat-icon>check_circle</mat-icon> {{ 'PEC.BTN_VALIDATE' | translate }}
          </button>
          <button mat-stroked-button (click)="close.emit()" [disabled]="!pecId">
            <mat-icon>cancel</mat-icon> {{ 'PEC.BTN_CLOSE' | translate }}
          </button>
          <button mat-stroked-button (click)="check.emit()" [disabled]="!pecId">
            <mat-icon>fact_check</mat-icon> {{ 'PEC.BTN_CHECK' | translate }}
          </button>
        </div>
      </mat-card-actions>
    </mat-card>
  `,
  styles: [`
    .card { padding: 4px; }

    .header-icon {
      background: #e0f2f1;
      color: #00695c;
      border-radius: 12px;
      display: flex;
      align-items: center;
      justify-content: center;
      width: 40px;
      height: 40px;
    }

    .pec-info {
      display: grid;
      gap: 10px;
    }

    .pec-row {
      display: flex;
      align-items: center;
      gap: 12px;
    }

    .pec-label {
      font-size: 12px;
      text-transform: uppercase;
      letter-spacing: 0.5px;
      color: #64748b;
      min-width: 50px;
    }

    .pec-value {
      font-size: 14px;
      color: #1e293b;
    }

    .mono {
      font-family: 'Courier New', monospace;
      font-size: 12px;
      word-break: break-all;
    }

    .pec-badge {
      display: inline-block;
      padding: 3px 12px;
      border-radius: 20px;
      font-size: 12px;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }

    .pec-badge.cree { background: #fef3c7; color: #92400e; }
    .pec-badge.validee { background: #d1fae5; color: #065f46; }
    .pec-badge.cloturee { background: #fee2e2; color: #991b1b; }
    .pec-badge.allowed { background: #d1fae5; color: #065f46; }
    .pec-badge.denied { background: #fee2e2; color: #991b1b; }

    .empty {
      color: #94a3b8;
      font-style: italic;
      font-size: 14px;
    }

    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }
  `]
})
export class PecWorkflowComponent {
  @Input() patientId: string | null = null;
  @Input() pecId: string | null = null;
  @Input() pecStatus: string | null = null;
  @Input() sessionAllowed: boolean | null = null;
  @Input() feedback = 'Pret.';

  @Output() create = new EventEmitter<void>();
  @Output() validate = new EventEmitter<void>();
  @Output() close = new EventEmitter<void>();
  @Output() check = new EventEmitter<void>();
}

