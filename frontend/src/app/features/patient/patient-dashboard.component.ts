import { Component, inject, OnInit, signal, effect } from '@angular/core';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { TranslateModule } from '@ngx-translate/core';
import { PatientListComponent, PatientRow } from './patient-list.component';
import { BackendApiService } from '../../core/api/backend-api.service';
import { AuthSessionService } from '../../core/auth/auth-session.service';
import { AppShellStore } from '../../core/state/app-shell.store';
import { WebSocketService } from '../../core/ws/websocket.service';

@Component({
  selector: 'app-patient-dashboard',
  standalone: true,
  imports: [MatButtonModule, MatIconModule, MatCardModule, TranslateModule, PatientListComponent],
  template: `
    <div style="display:flex;justify-content:flex-end;gap:8px;margin-bottom:8px;">
      <button mat-stroked-button color="primary" (click)="router.navigate(['/patients/pec-list'])">
        <mat-icon>list_alt</mat-icon> {{ 'PEC_LIST.TITLE' | translate }}
      </button>
      <button mat-stroked-button color="primary" (click)="router.navigate(['/patients/attestations-list'])">
        <mat-icon>fact_check</mat-icon> {{ 'ATTEST_LIST.TITLE' | translate }}
      </button>
    </div>
    <app-patient-list
      [patients]="patients()"
      (newPatient)="router.navigate(['/patients/new'])"
      (selectPatient)="onSelect($event)" />
  `
})
export class PatientDashboardComponent implements OnInit {
  readonly router = inject(Router);
  private readonly api = inject(BackendApiService);
  private readonly auth = inject(AuthSessionService);
  private readonly store = inject(AppShellStore);
  private readonly ws = inject(WebSocketService);
  readonly patients = signal<PatientRow[]>([]);

  constructor() {
    // Auto-refresh on PATIENT_CREATED WebSocket event
    effect(() => {
      const evt = this.ws.lastEvent();
      if (evt?.type === 'PATIENT_CREATED') {
        this.loadPatients();
      }
    });
  }

  ngOnInit(): void {
    this.loadPatients();
  }

  loadPatients(): void {
    const centerId = this.store.currentCenterId();
    if (!centerId) return;
    this.api.listPatients(centerId, this.auth.username() ?? 'demo').subscribe({
      next: (list) => this.patients.set(list.map((p: any) => ({
        id: p.id?.value ?? p.id, code: p.codePatient ?? p.id?.toString().substring(0, 8) ?? '',
        nom: p.nom ?? '', prenom: p.prenom ?? '', sexe: p.sexe ?? '',
        dateAdmission: p.dateAdmission ?? '', numeroAssurance: p.numeroAssurance?.value ?? p.numeroAssurance ?? '',
        etatPatient: p.etatPatient ?? 'PERMANENT'
      }))),
      error: () => this.patients.set([])
    });
  }

  onSelect(row: PatientRow): void {
    this.router.navigate(['/patients', row.id]);
  }
}
