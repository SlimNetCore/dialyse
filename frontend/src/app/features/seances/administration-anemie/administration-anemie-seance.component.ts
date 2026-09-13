import {ChangeDetectionStrategy, Component, effect, inject, input, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {MatCardModule} from '@angular/material/card';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatSelectModule} from '@angular/material/select';
import {MatCheckboxModule} from '@angular/material/checkbox';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {TranslateModule} from '@ngx-translate/core';
import {requiredValidator, SignalForm} from '../../../shared/forms/signal-form';
import {AuthStore} from '../../../core/state/auth.store';
import {
  AdministrationTraitement,
  DossierMedicalApiService,
  PrescriptionMedicale,
} from '../../../core/api/dossier-medical-api.service';

interface AdministrationFormModel {
  typeTraitement: 'EPO' | 'FER_INJECTABLE';
  molecule: string | null;
  dose: number | null;
  uniteDose: string | null;
  voie: string | null;
  administree: boolean;
  motifNonAdministration: string | null;
}

function emptyForm(): AdministrationFormModel {
  return {
    typeTraitement: 'EPO',
    molecule: null,
    dose: null,
    uniteDose: 'UI',
    voie: 'SC',
    administree: true,
    motifNonAdministration: null,
  };
}

/**
 * Administration de l'EPO / du fer injectable pendant la séance — responsabilité de l'infirmier,
 * qui suit ici la prescription du médecin en vigueur à la date de la séance. L'historique complet
 * (toutes séances confondues) reste consultable par le médecin dans l'onglet Anémie du dossier
 * médical ; ce composant n'affiche que ce qui a déjà été administré pendant CETTE séance.
 */
@Component({
  selector: 'app-administration-anemie-seance',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatCheckboxModule,
    MatProgressSpinnerModule,
    TranslateModule,
  ],
  templateUrl: './administration-anemie-seance.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './administration-anemie-seance.component.css',
})
export class AdministrationAnemieSeanceComponent {
  readonly patientId = input<string | null>(null);
  readonly seanceId = input<string | null>(null);
  readonly seanceDate = input<string | null>(null);
  readonly centerId = input<string | null>(null);
  readonly canEdit = input<boolean>(false);

  protected readonly prescription = signal<PrescriptionMedicale | null>(null);
  protected readonly loadingPrescription = signal(false);
  protected readonly administrationsSeance = signal<AdministrationTraitement[]>([]);
  protected readonly loadingAdministrations = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly formOpen = signal(false);
  protected readonly form = new SignalForm<AdministrationFormModel>(emptyForm(), {
    molecule: [requiredValidator()],
  });

  private readonly api = inject(DossierMedicalApiService);
  private readonly auth = inject(AuthStore);
  private lastKey: string | null = null;

  constructor() {
    effect(() => {
      const patientId = this.patientId();
      const centerId = this.centerId();
      const seanceId = this.seanceId();
      const key = `${patientId ?? ''}|${centerId ?? ''}|${seanceId ?? ''}`;
      if (!patientId || !centerId || !seanceId || key === this.lastKey) return;
      this.lastKey = key;
      this.refresh();
    });
  }

  openCreateForm(type: 'EPO' | 'FER_INJECTABLE'): void {
    const prescription = this.prescription();
    const model = emptyForm();
    model.typeTraitement = type;
    if (prescription) {
      if (type === 'EPO') {
        model.molecule = prescription.epoMolecule;
        model.dose = prescription.epoDoseUi;
        model.voie = prescription.epoVoie ?? 'SC';
        model.uniteDose = 'UI';
      } else {
        model.molecule = prescription.ferMolecule;
        model.dose = prescription.ferDoseMg;
        model.voie = prescription.ferVoie ?? 'IV';
        model.uniteDose = 'mg';
      }
    }
    this.form.reset(model);
    this.formOpen.set(true);
  }

  cancelForm(): void {
    this.formOpen.set(false);
  }

  onValue<K extends keyof AdministrationFormModel>(key: K, value: AdministrationFormModel[K]): void {
    this.form.set(key, value);
  }

  onNumber(key: 'dose', raw: string): void {
    const value = raw === '' ? null : Number(raw);
    this.form.set(key, Number.isNaN(value) ? null : value);
  }

  save(): void {
    const value = this.form.value();
    if (!value.administree) {
      if (!value.motifNonAdministration || value.motifNonAdministration.trim() === '') return;
    } else {
      this.form.markAllTouched();
      if (!this.form.valid()) return;
    }
    const patientId = this.patientId();
    const centerId = this.centerId();
    const seanceId = this.seanceId();
    if (!patientId || !centerId) return;

    this.saving.set(true);
    this.api.createAdministrationAnemie(patientId, {
      centerId,
      prescriptionMedicaleId: this.prescription()?.id ?? null,
      typeTraitement: value.typeTraitement,
      molecule: value.molecule,
      dose: value.administree ? value.dose : null,
      uniteDose: value.administree ? value.uniteDose : null,
      voie: value.voie,
      dateAdministration: this.seanceDate() ?? new Date().toISOString().slice(0, 10),
      seanceId: seanceId,
      administrePar: this.auth.username(),
      administree: value.administree,
      motifNonAdministration: value.administree ? null : value.motifNonAdministration,
    }).subscribe({
      next: () => {
        this.saving.set(false);
        this.formOpen.set(false);
        this.loadAdministrations();
      },
      error: (err) => {
        this.saving.set(false);
        this.error.set(err?.error?.detail || err?.statusText || 'Erreur enregistrement');
      },
    });
  }

  alreadyAdministered(type: 'EPO' | 'FER_INJECTABLE'): boolean {
    return this.administrationsSeance().some((a) => a.typeTraitement === type);
  }

  private refresh(): void {
    this.loadPrescription();
    this.loadAdministrations();
  }

  private loadPrescription(): void {
    const patientId = this.patientId();
    const centerId = this.centerId();
    if (!patientId || !centerId) return;
    this.loadingPrescription.set(true);
    this.api.getActivePrescription(centerId, patientId, this.seanceDate()).subscribe({
      next: (prescription) => {
        this.prescription.set(prescription);
        this.loadingPrescription.set(false);
      },
      error: () => this.loadingPrescription.set(false),
    });
  }

  private loadAdministrations(): void {
    const patientId = this.patientId();
    const centerId = this.centerId();
    const seanceId = this.seanceId();
    if (!patientId || !centerId) return;
    this.loadingAdministrations.set(true);
    this.api.listAdministrationsAnemie(centerId, patientId, 0, 100).subscribe({
      next: (res) => {
        this.administrationsSeance.set(res.items.filter((a) => a.seanceId === seanceId));
        this.loadingAdministrations.set(false);
      },
      error: () => this.loadingAdministrations.set(false),
    });
  }
}
