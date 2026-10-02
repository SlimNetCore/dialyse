import {ChangeDetectionStrategy, Component, computed, inject} from '@angular/core';
import {AuthStore} from '../../core/state/auth.store';
import {isDoctorOnlyFor} from '../../core/auth/role-scope.guard';
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
  templateUrl: './patient-dashboard.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './patient-dashboard.component.css',
})
export class PatientDashboardComponent {
  readonly router = inject(Router);
  private readonly auth = inject(AuthStore);
  /** Prises en charge et attestations relèvent de l'administration : elles sont masquées au médecin seul. */
  protected readonly canManageAdministrative = computed(() => !isDoctorOnlyFor((role) => this.auth.hasRole(role)));

  onSelect(row: PatientRow): void {
    this.router.navigate(['/patients', row.id]);
  }

  onStats(row: PatientRow): void {
    this.router.navigate(['/patients', row.id, 'stats']);
  }
}
