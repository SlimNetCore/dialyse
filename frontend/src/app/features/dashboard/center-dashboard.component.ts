import { Component, inject, OnInit, signal, effect } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';
import { FormsModule } from '@angular/forms';
import { BackendApiService } from '../../core/api/backend-api.service';
import { AppShellStore } from '../../core/state/app-shell.store';
import { WebSocketService } from '../../core/ws/websocket.service';

@Component({
  selector: 'app-center-dashboard',
  standalone: true,
  imports: [MatCardModule, MatIconModule, MatFormFieldModule, MatInputModule, MatProgressSpinnerModule, TranslateModule, FormsModule],
  template: `
    <div class="dashboard">
      <h2 class="dash-title">
        <mat-icon>dashboard</mat-icon> {{ 'DASHBOARD.TITLE' | translate }}
      </h2>

      <div class="config-row">
        <mat-form-field appearance="outline" class="days-field">
          <mat-label>{{ 'DASHBOARD.EXPIRATION_DAYS' | translate }}</mat-label>
          <input matInput type="number" [(ngModel)]="expirationDays" (ngModelChange)="loadStats()" min="1" />
        </mat-form-field>
      </div>

      @if (loading()) {
        <mat-spinner diameter="40" />
      } @else {
        <div class="stats-grid">
          <!-- Patients -->
          <mat-card class="stat-card patients">
            <mat-icon>people</mat-icon>
            <div class="stat-value">{{ stats().patientCount }}</div>
            <div class="stat-label">{{ 'DASHBOARD.PATIENTS' | translate }}</div>
          </mat-card>

          <!-- PEC Créées -->
          <mat-card class="stat-card pec-cree">
            <mat-icon>pending_actions</mat-icon>
            <div class="stat-value">{{ stats().pecCree }}</div>
            <div class="stat-label">{{ 'DASHBOARD.PEC_CREE' | translate }}</div>
          </mat-card>

          <!-- PEC Validées -->
          <mat-card class="stat-card pec-validee">
            <mat-icon>verified</mat-icon>
            <div class="stat-value">{{ stats().pecValidee }}</div>
            <div class="stat-label">{{ 'DASHBOARD.PEC_VALIDEE' | translate }}</div>
          </mat-card>

          <!-- PEC Expiring -->
          <mat-card class="stat-card pec-expiring">
            <mat-icon>warning</mat-icon>
            <div class="stat-value">{{ stats().pecExpiring }}</div>
            <div class="stat-label">{{ 'DASHBOARD.PEC_EXPIRING' | translate:{days: expirationDays} }}</div>
          </mat-card>

          <!-- Attestations -->
          <mat-card class="stat-card attestation">
            <mat-icon>description</mat-icon>
            <div class="stat-value">{{ stats().attestationTotal }}</div>
            <div class="stat-label">{{ 'DASHBOARD.ATTESTATION_TOTAL' | translate }}</div>
          </mat-card>

          <!-- Attestations Expiring -->
          <mat-card class="stat-card attestation-expiring">
            <mat-icon>schedule</mat-icon>
            <div class="stat-value">{{ stats().attestationExpiring }}</div>
            <div class="stat-label">{{ 'DASHBOARD.ATTESTATION_EXPIRING' | translate:{days: expirationDays} }}</div>
          </mat-card>
        </div>
      }
    </div>
  `,
  styles: [`
    .dashboard { max-width: 1100px; margin: 0 auto; }
    .dash-title {
      display: flex; align-items: center; gap: 8px; font-size: 1.3rem;
      color: #1b5e20; margin-bottom: 16px;
    }
    .config-row { margin-bottom: 16px; }
    .days-field { width: 200px; }
    .stats-grid {
      display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px;
    }
    .stat-card {
      display: flex; flex-direction: column; align-items: center; justify-content: center;
      padding: 24px; border-radius: 14px; min-height: 140px;
      transition: transform 0.2s; cursor: default;
    }
    .stat-card:hover { transform: translateY(-2px); box-shadow: 0 4px 20px rgba(0,0,0,0.1); }
    .stat-card mat-icon { font-size: 36px; width: 36px; height: 36px; margin-bottom: 8px; }
    .stat-value { font-size: 2.2rem; font-weight: 800; line-height: 1.1; }
    .stat-label { font-size: 13px; color: #666; text-align: center; margin-top: 4px; }

    .patients { background: #e8f5e9; }
    .patients mat-icon, .patients .stat-value { color: #1b5e20; }
    .pec-cree { background: #fff3e0; }
    .pec-cree mat-icon, .pec-cree .stat-value { color: #e65100; }
    .pec-validee { background: #e3f2fd; }
    .pec-validee mat-icon, .pec-validee .stat-value { color: #1565c0; }
    .pec-expiring { background: #fce4ec; }
    .pec-expiring mat-icon, .pec-expiring .stat-value { color: #c62828; }
    .attestation { background: #f3e5f5; }
    .attestation mat-icon, .attestation .stat-value { color: #6a1b9a; }
    .attestation-expiring { background: #fff8e1; }
    .attestation-expiring mat-icon, .attestation-expiring .stat-value { color: #f57f17; }
  `]
})
export class CenterDashboardComponent implements OnInit {
  private readonly api = inject(BackendApiService);
  private readonly store = inject(AppShellStore);
  private readonly ws = inject(WebSocketService);

  expirationDays = 30;
  readonly loading = signal(true);
  readonly stats = signal<any>({
    patientCount: 0, pecCree: 0, pecValidee: 0, pecExpiring: 0,
    attestationTotal: 0, attestationExpiring: 0
  });

  constructor() {
    // Auto-refresh on WS event
    effect(() => {
      const evt = this.ws.lastEvent();
      if (evt) this.loadStats();
    });
  }

  ngOnInit(): void {
    this.loadStats();
  }

  loadStats(): void {
    const cid = this.store.currentCenterId();
    if (!cid) return;
    this.loading.set(true);
    this.api.getDashboardStats(cid, this.expirationDays).subscribe({
      next: (data) => { this.stats.set(data); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }
}

