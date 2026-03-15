import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { BackendApiService } from '../../../core/api/backend-api.service';
import { AppShellStore } from '../../../core/state/app-shell.store';
import { TranslateModule } from '@ngx-translate/core';

@Component({
  selector: 'app-attestation-list',
  standalone: true,
  imports: [CommonModule, MatTableModule, MatButtonModule, MatIconModule, MatCardModule, MatTooltipModule, MatSnackBarModule, TranslateModule],
  template: `
    <mat-card>
      <div class="header">
        <h2>{{ 'ATTEST_LIST.TITLE' | translate }}</h2>
        <span class="spacer"></span>
        <button mat-stroked-button color="primary" (click)="printList()"><mat-icon>print</mat-icon> {{ 'PATIENT_LIST.BTN_PRINT' | translate }}</button>
        <button mat-stroked-button color="primary" (click)="exportExcel()"><mat-icon>table_view</mat-icon> {{ 'PATIENT_LIST.BTN_EXPORT_EXCEL' | translate }}</button>
      </div>
      <table mat-table [dataSource]="rows()" class="w100">
        <ng-container matColumnDef="code"><th mat-header-cell *matHeaderCellDef>{{ 'ATTEST_LIST.COL_CODE' | translate }}</th><td mat-cell *matCellDef="let r">{{r.CODE_PATIENT || r.code_patient}}</td></ng-container>
        <ng-container matColumnDef="nom"><th mat-header-cell *matHeaderCellDef>{{ 'ATTEST_LIST.COL_PATIENT' | translate }}</th><td mat-cell *matCellDef="let r">{{r.NOM || r.nom}} {{r.PRENOM || r.prenom}}</td></ng-container>
        <ng-container matColumnDef="assurance"><th mat-header-cell *matHeaderCellDef>{{ 'ATTEST_LIST.COL_ASSURANCE' | translate }}</th><td mat-cell *matCellDef="let r">{{r.NUMERO_ASSURANCE || r.numero_assurance}}</td></ng-container>
        <ng-container matColumnDef="debut"><th mat-header-cell *matHeaderCellDef>{{ 'ATTEST_LIST.COL_DEBUT' | translate }}</th><td mat-cell *matCellDef="let r">{{r.DATE_DEBUT || r.date_debut}}</td></ng-container>
        <ng-container matColumnDef="fin"><th mat-header-cell *matHeaderCellDef>{{ 'ATTEST_LIST.COL_FIN' | translate }}</th><td mat-cell *matCellDef="let r">{{r.DATE_FIN || r.date_fin}}</td></ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef>{{ 'ATTEST_LIST.COL_ACTIONS' | translate }}</th>
          <td mat-cell *matCellDef="let r">
            <button mat-icon-button color="primary" (click)="printRow(r)" [matTooltip]="'PATIENT_LIST.BTN_PRINT' | translate"><mat-icon>print</mat-icon></button>
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="cols"></tr>
        <tr mat-row *matRowDef="let row; columns: cols"></tr>
      </table>
    </mat-card>
  `,
  styles: [`.header{display:flex;align-items:center;margin-bottom:8px}.spacer{flex:1}.w100{width:100%}`]
})
export class AttestationListComponent implements OnInit {
  private readonly api = inject(BackendApiService);
  private readonly store = inject(AppShellStore);
  private readonly snack = inject(MatSnackBar);
  readonly rows = signal<any[]>([]);
  readonly cols = ['code', 'nom', 'assurance', 'debut', 'fin', 'actions'];

  ngOnInit(): void {
    const cid = this.store.currentCenterId();
    if (!cid) return;
    this.api.listAttestationsByCenter(cid).subscribe(r => this.rows.set(r ?? []));
  }

  printList(): void {
    const cid = this.store.currentCenterId();
    if (!cid) return;
    this.api.printDocument(cid, 'LISTE_ATTESTATIONS', {}).subscribe({
      next: (blob) => window.open(URL.createObjectURL(blob), '_blank'),
      error: (e) => this.snack.open('Erreur impression: ' + (e?.error?.text || e.message), 'OK', { duration: 5000 })
    });
  }

  exportExcel(): void {
    const cid = this.store.currentCenterId();
    if (!cid) return;
    this.api.printDocument(cid, 'LISTE_ATTESTATIONS', {}, 'EXCEL').subscribe({
      next: (blob) => {
        const a = document.createElement('a');
        const url = URL.createObjectURL(blob);
        a.href = url;
        a.download = 'liste-attestations.xls';
        a.click();
      },
      error: (e) => this.snack.open('Erreur export: ' + (e?.error?.text || e.message), 'OK', { duration: 5000 })
    });
  }

  printRow(r: any): void {
    const cid = this.store.currentCenterId();
    const pid = (r.PATIENT_ID || r.patient_id || '').toString();
    if (!cid || !pid) return;
    this.api.printDocument(cid, 'ATTESTATION', { patientId: pid }).subscribe({
      next: (blob) => window.open(URL.createObjectURL(blob), '_blank'),
      error: (e) => this.snack.open('Erreur impression: ' + (e?.error?.text || e.message), 'OK', { duration: 5000 })
    });
  }
}
