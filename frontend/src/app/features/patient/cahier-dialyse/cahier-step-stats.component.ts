import {Component, Input} from '@angular/core';
import {CommonModule} from '@angular/common';
import {MatCardModule} from '@angular/material/card';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {TranslateModule} from '@ngx-translate/core';
import {PatientStatsComponent} from '../patient-stats.component';

@Component({
  selector: 'app-cahier-step-stats',
  standalone: true,
  imports: [CommonModule, MatCardModule, MatProgressSpinnerModule, TranslateModule, PatientStatsComponent],
  template: `
    <div class="step-stats-content">
      <app-patient-stats />
    </div>
  `,
  styles: [`
    .step-stats-content { padding: 0; }
  `]
})
export class CahierStepStatsComponent {
  @Input() patientId!: string;
}


