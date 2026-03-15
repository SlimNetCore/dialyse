import { Component, inject, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { TranslateModule } from '@ngx-translate/core';
import { PatientListComponent, PatientRow } from './patient-list.component';
import { BackendApiService } from '../../core/api/backend-api.service';
import { AuthSessionService } from '../../core/auth/auth-session.service';
import { AppShellStore } from '../../core/state/app-shell.store';

@Component({
  selector: 'app-patient-dashboard',
  standalone: true,
  imports: [MatButtonModule, MatIconModule, MatCardModule, TranslateModule, PatientListComponent],
  template: `
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
  readonly patients = signal<PatientRow[]>([]);

  ngOnInit(): void {
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
    // future: navigate to detail
  }
}

