import {ChangeDetectionStrategy, Component, computed, inject, OnInit, signal} from '@angular/core';
import {ActivatedRoute} from '@angular/router';
import {MatCardModule} from '@angular/material/card';
import {MatPaginatorModule, PageEvent} from '@angular/material/paginator';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatSelectModule} from '@angular/material/select';
import {MatDatepickerModule} from '@angular/material/datepicker';
import {MatNativeDateModule} from '@angular/material/core';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {MatTooltipModule} from '@angular/material/tooltip';
import {TranslateModule} from '@ngx-translate/core';
import {requiredValidator, SignalForm} from '../../../shared/forms/signal-form';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {PrescriptionMedicale} from '../../../core/api/dossier-medical-api.service';
import {DossierMedicalAccessService} from '../dossier-medical-access.service';
import {PrescriptionsStore} from '../state/prescriptions.store';
import {resolvePatientIdFromRoute} from '../dossier-medical-route.util';

interface PrescriptionFormModel {
  datePrescription: Date | string | null;
  qbCible: number | null;
  qdCible: number | null;
  ufMaxMl: number | null;
  dureeCibleMin: number | null;
  typeDialyseurPrescrit: string;
  anticoagTypePrescrit: string;
  epoMolecule: string;
  epoDoseUi: number | null;
  epoVoie: string;
  epoFrequence: string;
  ferMolecule: string;
  ferDoseMg: number | null;
  ferVoie: string;
  ferFrequence: string;
}

const ANTICOAG_TYPES = ['HNF', 'HBPM', 'CITRATE', 'AUCUN'] as const;
const EPO_VOIES = ['SC', 'IV'] as const;
const FER_VOIES = ['IV'] as const;

function toIsoDate(value: Date | string | null): string | null {
  if (!value) return null;
  if (typeof value === 'string') return value;
  const y = value.getFullYear();
  const m = String(value.getMonth() + 1).padStart(2, '0');
  const d = String(value.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

function emptyForm(): PrescriptionFormModel {
  return {
    datePrescription: new Date(),
    qbCible: null,
    qdCible: null,
    ufMaxMl: null,
    dureeCibleMin: null,
    typeDialyseurPrescrit: '',
    anticoagTypePrescrit: '',
    epoMolecule: '',
    epoDoseUi: null,
    epoVoie: '',
    epoFrequence: '',
    ferMolecule: '',
    ferDoseMg: null,
    ferVoie: '',
    ferFrequence: '',
  };
}

/**
 * Prescriptions médicales du patient : cibles de dialyse et traitement de l'anémie (EPO, fer
 * injectable). Historique en lecture seule ; seule la prescription la plus récente est modifiable
 * ou supprimable — les précédentes font foi de ce qui a été effectivement prescrit à l'époque.
 */
@Component({
  selector: 'app-prescriptions-list',
  standalone: true,
  imports: [
    MatCardModule,
    MatPaginatorModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    TranslateModule,
  ],
  templateUrl: './prescriptions-list.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './prescriptions-list.component.css',
})
export class PrescriptionsListComponent implements OnInit {
  protected readonly access = inject(DossierMedicalAccessService);
  protected readonly store = inject(PrescriptionsStore);
  protected readonly anticoagTypes = ANTICOAG_TYPES;
  protected readonly epoVoies = EPO_VOIES;
  protected readonly ferVoies = FER_VOIES;
  /** Seule la prescription la plus récente (page 0, première ligne) peut être modifiée. */
  protected readonly mostRecentId = computed(() =>
    this.store.pageIndex() === 0 ? (this.store.rows()[0]?.id ?? null) : null);
  protected readonly formOpen = signal(false);
  protected readonly editingId = signal<string | null>(null);
  protected readonly form = new SignalForm<PrescriptionFormModel>(emptyForm(), {
    datePrescription: [requiredValidator()],
  });
  private readonly route = inject(ActivatedRoute);
  protected readonly patientId = resolvePatientIdFromRoute(this.route);
  private readonly appShell = inject(AppShellStore);

  ngOnInit(): void {
    this.refresh();
  }

  onPageChange(event: PageEvent): void {
    this.store.setPagination(event.pageIndex, event.pageSize);
  }

  canEditRow(prescription: PrescriptionMedicale): boolean {
    return this.access.canEdit() && this.mostRecentId() === prescription.id;
  }

  openCreateForm(): void {
    this.editingId.set(null);
    this.form.reset(emptyForm());
    this.formOpen.set(true);
  }

  openEditForm(prescription: PrescriptionMedicale): void {
    this.editingId.set(prescription.id);
    this.form.reset({
      datePrescription: prescription.datePrescription,
      qbCible: prescription.qbCible,
      qdCible: prescription.qdCible,
      ufMaxMl: prescription.ufMaxMl,
      dureeCibleMin: prescription.dureeCibleMin,
      typeDialyseurPrescrit: prescription.typeDialyseurPrescrit ?? '',
      anticoagTypePrescrit: prescription.anticoagTypePrescrit ?? '',
      epoMolecule: prescription.epoMolecule ?? '',
      epoDoseUi: prescription.epoDoseUi,
      epoVoie: prescription.epoVoie ?? '',
      epoFrequence: prescription.epoFrequence ?? '',
      ferMolecule: prescription.ferMolecule ?? '',
      ferDoseMg: prescription.ferDoseMg,
      ferVoie: prescription.ferVoie ?? '',
      ferFrequence: prescription.ferFrequence ?? '',
    });
    this.formOpen.set(true);
  }

  cancelForm(): void {
    this.formOpen.set(false);
    this.editingId.set(null);
  }

  onValue<K extends keyof PrescriptionFormModel>(key: K, value: PrescriptionFormModel[K]): void {
    this.form.set(key, value);
  }

  onNumber<K extends keyof PrescriptionFormModel>(key: K, raw: string): void {
    const value = raw === '' ? null : Number(raw);
    this.form.set(key, (Number.isNaN(value) ? null : value) as PrescriptionFormModel[K]);
  }

  save(): void {
    this.form.markAllTouched();
    if (!this.form.valid()) return;
    const centerId = this.appShell.currentCenterId();
    if (!centerId || !this.patientId) return;
    const value = this.form.value();
    this.store.save({
      patientId: this.patientId,
      prescriptionId: this.editingId(),
      payload: {
        centerId,
        datePrescription: toIsoDate(value.datePrescription) ?? new Date().toISOString().slice(0, 10),
        medecinId: null,
        qbCible: value.qbCible,
        qdCible: value.qdCible,
        ufMaxMl: value.ufMaxMl,
        dureeCibleMin: value.dureeCibleMin,
        typeDialyseurPrescrit: value.typeDialyseurPrescrit || null,
        anticoagTypePrescrit: value.anticoagTypePrescrit || null,
        epoMolecule: value.epoMolecule || null,
        epoDoseUi: value.epoDoseUi,
        epoVoie: value.epoVoie || null,
        epoFrequence: value.epoFrequence || null,
        ferMolecule: value.ferMolecule || null,
        ferDoseMg: value.ferDoseMg,
        ferVoie: value.ferVoie || null,
        ferFrequence: value.ferFrequence || null,
      },
    });
    this.formOpen.set(false);
    this.editingId.set(null);
  }

  remove(prescription: PrescriptionMedicale): void {
    const centerId = this.appShell.currentCenterId();
    if (!centerId || !this.patientId) return;
    this.store.delete({centerId, patientId: this.patientId, prescriptionId: prescription.id});
  }

  private refresh(): void {
    const centerId = this.appShell.currentCenterId();
    if (!centerId || !this.patientId) return;
    this.store.load({centerId, patientId: this.patientId, page: this.store.pageIndex(), size: this.store.pageSize()});
  }
}
