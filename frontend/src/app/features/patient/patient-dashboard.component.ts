import {Component, inject} from '@angular/core';
import {Router} from '@angular/router';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatCardModule} from '@angular/material/card';
import {TranslateModule} from '@ngx-translate/core';
import {PatientListComponent, PatientRow} from './patient-list.component';

@Component({
  selector: 'app-patient-dashboard',
  standalone: true,
  imports: [MatButtonModule, MatIconModule, MatCardModule, TranslateModule, PatientListComponent],
  template: `
    <div class="dashboard-actions">
      <button mat-stroked-button color="primary" class="action-btn" (click)="router.navigate(['/patients/pec-list'])">
        <mat-icon>list_alt</mat-icon> {{ 'PEC_LIST.TITLE' | translate }}
      </button>
      <button mat-stroked-button color="primary" class="action-btn" (click)="router.navigate(['/patients/attestations-list'])">
        <mat-icon>fact_check</mat-icon> {{ 'ATTEST_LIST.TITLE' | translate }}
      </button>
    </div>
    <app-patient-list
      (newPatient)="router.navigate(['/patients/new'])"
      (selectPatient)="onSelect($event)" />
  `,
  styles: [`
    .dashboard-actions {
      display: flex;
      justify-content: flex-end;
      gap: 8px;
      margin-bottom: 10px;
      flex-wrap: wrap;
    }
    .action-btn {
      background: var(--app-surface);
      border-color: var(--app-primary-outline) !important;
    }
  `]
})
export class PatientDashboardComponent {
  readonly router = inject(Router);

  onSelect(row: PatientRow): void {
    this.router.navigate(['/patients', row.id]);
  }
}
