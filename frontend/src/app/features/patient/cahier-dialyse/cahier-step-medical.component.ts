import {ChangeDetectionStrategy, Component, EventEmitter, Input, Output} from '@angular/core';
import {CommonModule} from '@angular/common';
import {MatCardModule} from '@angular/material/card';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {TranslateModule} from '@ngx-translate/core';

@Component({
  selector: 'app-cahier-step-medical',
  standalone: true,
  imports: [CommonModule, MatCardModule, MatButtonModule, MatIconModule, TranslateModule],
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
  @Output() dataChange = new EventEmitter<any>();
  @Output() validChange = new EventEmitter<boolean>();

  display(value: string | null | undefined): string {
    const normalized = (value ?? '').trim();
    return normalized || '-';
  }

  proceed(): void {
    this.validChange.emit(true);
  }
}
