import {ChangeDetectionStrategy, Component, inject, Input, signal} from '@angular/core';
import {MatCardModule} from '@angular/material/card';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MAT_DIALOG_DATA, MatDialog, MatDialogModule} from '@angular/material/dialog';
import {MatTooltipModule} from '@angular/material/tooltip';
import {TranslateModule} from '@ngx-translate/core';
import * as QRCode from 'qrcode';

@Component({
  selector: 'app-patient-qr-card',
  standalone: true,
  imports: [
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatDialogModule,
    MatTooltipModule,
    TranslateModule,
  ],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    <button
      mat-icon-button
      color="primary"
      (click)="openCard()"
      [disabled]="!patientId"
      [matTooltip]="'PATIENT_FORM.QR_CARD' | translate"
      [attr.aria-label]="'PATIENT_FORM.QR_CARD' | translate"
    >
      <mat-icon>qr_code_2</mat-icon>
    </button>
  `,
})
export class PatientQrCardComponent {
  private readonly dialog = inject(MatDialog);
  @Input() patientId: string | null = null;
  @Input() nom = '';
  @Input() prenom = '';
  @Input() numeroAssurance = '';
  @Input() photoBase64: string | null = null;
  @Input() dateAdmission = '';
  @Input() groupeSanguin = '';

  openCard(): void {
    if (!this.patientId) return;
    this.dialog.open(PatientQrCardDialogComponent, {
      width: '420px',
      data: {
        patientId: this.patientId,
        nom: this.nom,
        prenom: this.prenom,
        numeroAssurance: this.numeroAssurance,
        photoBase64: this.photoBase64,
        dateAdmission: this.dateAdmission,
        groupeSanguin: this.groupeSanguin,
      },
    });
  }
}

@Component({
  selector: 'app-patient-qr-card-dialog',
  standalone: true,
  imports: [MatCardModule, MatButtonModule, MatIconModule, TranslateModule],
  template: `
    <div class="card-print" id="patientCard">
      <div class="card-header">
        <mat-icon class="card-logo">monitor_heart</mat-icon>
        <span class="card-title">HemoDialyse</span>
      </div>
      <div class="card-body">
        <div class="card-photo">
          @if (data.photoBase64) {
            <img [src]="data.photoBase64" alt="Photo" />
          } @else {
            <mat-icon class="no-photo">person</mat-icon>
          }
        </div>
        <div class="card-info">
          <h2>{{ data.nom }} {{ data.prenom }}</h2>
          <div class="info-line"><strong>ID:</strong> {{ data.patientId?.substring(0, 8) }}</div>
          <div class="info-line"><strong>N° Assurance:</strong> {{ data.numeroAssurance }}</div>
          @if (data.groupeSanguin) {
            <div class="info-line"><strong>Grp:</strong> {{ data.groupeSanguin }}</div>
          }
          @if (data.dateAdmission) {
            <div class="info-line"><strong>Adm:</strong> {{ data.dateAdmission }}</div>
          }
        </div>
        <div class="card-qr">
          <img [src]="qrDataUrl()" alt="QR Code" />
        </div>
      </div>
    </div>
    <div class="dialog-actions">
      <button mat-flat-button color="primary" (click)="print()">
        <mat-icon>print</mat-icon>
        {{ 'PATIENT_FORM.PRINT_CARD' | translate }}
      </button>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.Eager,
  styles: [
    `
      .card-print {
        padding: 20px;
        border: 2px solid #1b5e20;
        border-radius: 14px;
        background: linear-gradient(135deg, #f7faf8 0%, #e8f5e9 100%);
      }
      .card-header {
        display: flex;
        align-items: center;
        gap: 8px;
        margin-bottom: 16px;
        padding-bottom: 12px;
        border-bottom: 2px solid #c8e6c9;
      }
      .card-logo {
        color: #1b5e20;
        font-size: 28px;
        width: 28px;
        height: 28px;
      }
      .card-title {
        font-weight: 700;
        font-size: 18px;
        color: #1b5e20;
        letter-spacing: -0.3px;
      }

      .card-body {
        display: flex;
        gap: 16px;
        align-items: flex-start;
      }

      .card-photo {
        width: 80px;
        height: 100px;
        border-radius: 8px;
        overflow: hidden;
        border: 1px solid #c8e6c9;
        flex-shrink: 0;
        display: flex;
        align-items: center;
        justify-content: center;
        background: #f0fdf4;
        img {
          width: 100%;
          height: 100%;
          object-fit: cover;
        }
      }
      .no-photo {
        font-size: 40px;
        width: 40px;
        height: 40px;
        color: #a5d6a7;
      }

      .card-info {
        flex: 1;
      }
      .card-info h2 {
        margin: 0 0 8px;
        font-size: 16px;
        font-weight: 700;
        color: #1e293b;
      }
      .info-line {
        font-size: 13px;
        color: #475569;
        margin-bottom: 3px;
      }
      .info-line strong {
        color: #1b5e20;
      }

      .card-qr {
        width: 90px;
        height: 90px;
        flex-shrink: 0;
        img {
          width: 100%;
          height: 100%;
        }
      }

      .dialog-actions {
        display: flex;
        justify-content: center;
        padding: 16px 0 0;
      }
    `,
  ],
})
export class PatientQrCardDialogComponent {
  readonly data: any = inject(MAT_DIALOG_DATA);
  readonly qrDataUrl = signal('');

  constructor() {
    const payload = JSON.stringify({
      id: this.data.patientId,
      nom: this.data.nom,
      prenom: this.data.prenom,
      assurance: this.data.numeroAssurance,
    });
    QRCode.toDataURL(payload, {width: 180, margin: 1, color: {dark: '#1b5e20'}}).then(
      (url: string) => this.qrDataUrl.set(url),
    );
  }

  print(): void {
    const card = document.getElementById('patientCard');
    if (!card) return;
    const win = window.open('', '_blank');
    if (!win) return;
    win.document.write(`
      <html lang="fr"><head><title>Carte Patient</title>
      <style>
        body { font-family: 'Inter', 'Segoe UI', sans-serif; padding: 20px; }
        ${card.closest('app-patient-qr-card-dialog')?.querySelector('style')?.textContent || ''}
      </style></head>
      <body>${card.outerHTML}</body></html>
    `);
    win.document.close();
    win.focus();
    win.print();
  }
}
