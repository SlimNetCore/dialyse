import {ChangeDetectionStrategy, Component, inject, Input, signal} from '@angular/core';
import {MatCardModule} from '@angular/material/card';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MAT_DIALOG_DATA, MatDialog, MatDialogModule} from '@angular/material/dialog';
import {MatTooltipModule} from '@angular/material/tooltip';
import {TranslateModule} from '@ngx-translate/core';
import * as QRCode from 'qrcode';
import html2canvas from 'html2canvas';
import {jsPDF} from 'jspdf';

type PatientQrDialogData = {
  patientId: string;
  codePatient: string;
  nom: string;
  prenom: string;
  numeroAssurance: string;
  photoBase64: string | null;
  dateAdmission: string;
  groupeSanguin: string;
};

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
  @Input() codePatient = '';
  @Input() nom = '';
  @Input() prenom = '';
  @Input() numeroAssurance = '';
  @Input() photoBase64: string | null = null;
  @Input() dateAdmission = '';
  @Input() groupeSanguin = '';

  openCard(): void {
    if (!this.patientId) return;
    this.dialog.open(PatientQrCardDialogComponent, {
      width: 'min(92vw, 680px)',
      maxWidth: '92vw',
      data: {
        patientId: this.patientId,
        codePatient: this.codePatient,
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
  readonly data: PatientQrDialogData = inject(MAT_DIALOG_DATA);
  readonly qrDataUrl = signal('');
  readonly exporting = signal(false);

  constructor() {
    // Keep scanner payload simple for Seance scan parser: PAT:<patientCode>
    const payload = this.scanToken();
    QRCode.toDataURL(payload, {
      width: 256,
      margin: 1,
      errorCorrectionLevel: 'H',
      color: {dark: '#0f5132', light: '#ffffff'},
    }).then(
      (url: string) => this.qrDataUrl.set(url),
    );
  }

  async downloadPng(): Promise<void> {
    const card = this.cardElement();
    if (!card) return;
    this.exporting.set(true);
    try {
      const canvas = await html2canvas(card, {scale: 3, backgroundColor: '#ffffff', useCORS: true});
      const link = document.createElement('a');
      link.href = canvas.toDataURL('image/png');
      link.download = `${this.safeFilename()}-qr.png`;
      link.click();
    } finally {
      this.exporting.set(false);
    }
  }

  async downloadPdf(): Promise<void> {
    const card = this.cardElement();
    if (!card) return;
    this.exporting.set(true);
    try {
      const canvas = await html2canvas(card, {scale: 3, backgroundColor: '#ffffff', useCORS: true});
      const image = canvas.toDataURL('image/png');
      const pdf = new jsPDF({
        orientation: canvas.width >= canvas.height ? 'landscape' : 'portrait',
        unit: 'px',
        format: [canvas.width, canvas.height],
      });
      pdf.addImage(image, 'PNG', 0, 0, canvas.width, canvas.height);
      pdf.save(`${this.safeFilename()}-qr.pdf`);
    } finally {
      this.exporting.set(false);
    }
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

  private scanToken(): string {
    const code = (this.data.codePatient ?? '').trim();
    const assurance = (this.data.numeroAssurance ?? '').trim();
    const fallback = (this.data.patientId ?? '').trim();
    if (code) return `PAT:${code}`;
    if (assurance) return `ASS:${assurance}`;
    return fallback;
  }

  private safeFilename(): string {
    const code = (this.data.codePatient ?? '').trim() || (this.data.patientId ?? '').slice(0, 8) || 'patient';
    return `patient-${code.replace(/[^a-zA-Z0-9_-]/g, '_')}`;
  }

  private cardElement(): HTMLElement | null {
    return document.getElementById('patientCard');
  }
}
