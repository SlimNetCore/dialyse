import {ChangeDetectionStrategy, Component, computed, effect, inject, input, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {RouterLink} from '@angular/router';
import {MatCardModule} from '@angular/material/card';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {TranslateModule} from '@ngx-translate/core';
import {AuthStore} from '../../../core/state/auth.store';
import {
  DossierMedicalApiService,
  DossierMedicalPatient,
  PrescriptionMedicale
} from '../../../core/api/dossier-medical-api.service';

/**
 * Résumé du dossier médical du patient, affiché en lecture seule dans le cahier de dialyse — le
 * dossier médical (module réservé au médecin) reste l'unique lieu de saisie ; ce composant n'en
 * reprend qu'un extrait déjà autorisé pour tout profil consultant le cahier de dialyse (base du
 * dossier + prescription en vigueur à la date de la séance).
 */
@Component({
  selector: 'app-cahier-step-medical',
  standalone: true,
  imports: [CommonModule, RouterLink, MatCardModule, MatButtonModule, MatIconModule, MatProgressSpinnerModule, TranslateModule],
  templateUrl: './cahier-step-medical.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './cahier-step-medical.component.css',
})
export class CahierStepMedicalComponent {
  readonly patientId = input<string | null>(null);
  readonly centerId = input<string | null>(null);
  readonly seanceDate = input<string | null>(null);
  readonly seanceStatus = input<string | null>(null);

  protected readonly dossier = signal<DossierMedicalPatient | null>(null);
  protected readonly prescription = signal<PrescriptionMedicale | null>(null);
  protected readonly loading = signal(false);
  private readonly api = inject(DossierMedicalApiService);
  private readonly auth = inject(AuthStore);
  protected readonly canOpenDossierMedical = computed(() =>
    this.auth.hasRole('MEDECIN') || this.auth.hasRole('ADMIN'));
  private lastKey: string | null = null;

  constructor() {
    effect(() => {
      const patientId = this.patientId();
      const centerId = this.centerId();
      const key = `${patientId ?? ''}|${centerId ?? ''}|${this.seanceDate() ?? ''}`;
      if (!patientId || !centerId || key === this.lastKey) return;
      this.lastKey = key;
      this.refresh(patientId, centerId);
    });
  }

  display(value: string | null | undefined): string {
    const normalized = (value ?? '').trim();
    return normalized || '-';
  }

  private refresh(patientId: string, centerId: string): void {
    this.loading.set(true);
    this.api.getDossier(centerId, patientId).subscribe({
      next: (dossier) => this.dossier.set(dossier),
      error: () => this.dossier.set(null),
    });
    this.api.getActivePrescription(centerId, patientId, this.seanceDate()).subscribe({
      next: (prescription) => {
        this.prescription.set(prescription);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}
