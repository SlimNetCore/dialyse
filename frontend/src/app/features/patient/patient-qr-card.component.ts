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
  templateUrl: './patient-qr-card.component.html',
  styleUrl: './patient-qr-card.component.css',
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
  templateUrl: './patient-qr-card-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './patient-qr-card-dialog.component.css',
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
