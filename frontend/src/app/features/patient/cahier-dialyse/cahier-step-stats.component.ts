import {ChangeDetectionStrategy, Component, Input} from '@angular/core';
import {CommonModule} from '@angular/common';
import {MatCardModule} from '@angular/material/card';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {TranslateModule} from '@ngx-translate/core';
import {PatientStatsComponent} from '../patient-stats.component';

@Component({
  selector: 'app-cahier-step-stats',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatProgressSpinnerModule,
    TranslateModule,
    PatientStatsComponent,
  ],
  templateUrl: './cahier-step-stats.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './cahier-step-stats.component.css',
})
export class CahierStepStatsComponent {
  @Input() patientId!: string;
}
