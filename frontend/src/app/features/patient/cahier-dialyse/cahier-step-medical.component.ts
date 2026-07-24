import {ChangeDetectionStrategy, Component, Input} from '@angular/core';
import {CommonModule} from '@angular/common';
import {MatCardModule} from '@angular/material/card';
import {TranslateModule} from '@ngx-translate/core';

@Component({
  selector: 'app-cahier-step-medical',
  standalone: true,
  imports: [CommonModule, MatCardModule, TranslateModule],
  templateUrl: './cahier-step-medical.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './cahier-step-medical.component.css',
})
export class CahierStepMedicalComponent {
  @Input() patientId!: string;
  @Input() readonly = false;
  @Input() seanceDate: string | null = null;
  @Input() seanceStatus: string | null = null;
  @Input() medicalData: {
    prescription?: string;
    toleranceSeance?: string;
    examenClinique?: string;
    resultatsBiologiques?: string;
    ajustementsTherapeutiques?: string;
    conclusionMedicale?: string;
  } | null = null;

  display(value: string | null | undefined): string {
    const normalized = (value ?? '').trim();
    return normalized || '-';
  }
}
