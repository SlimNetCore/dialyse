import {ChangeDetectionStrategy, Component, inject} from '@angular/core';
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

  onSelect(row: PatientRow): void {
    this.router.navigate(['/patients', row.id]);
  }

  onStats(row: PatientRow): void {
    this.router.navigate(['/patients', row.id, 'stats']);
  }
}
