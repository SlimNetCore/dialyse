import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  inject,
  Input,
  OnChanges,
  OnInit,
  Output,
  signal,
  SimpleChanges,
} from '@angular/core';
import {FormBuilder, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatDatepickerModule} from '@angular/material/datepicker';
import {MatNativeDateModule} from '@angular/material/core';
import {MatIconModule} from '@angular/material/icon';
import {MatButtonModule} from '@angular/material/button';
import {MatSnackBar} from '@angular/material/snack-bar';
import {MatDialog} from '@angular/material/dialog';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {ConfirmDialogComponent} from '../../../shared/confirm-dialog.component';
import {PatientFicheStore} from '../state/patient-fiche.store';
import {consumeWizardActionStatus} from './wizard-action-status.util';

@Component({
  selector: 'app-step-attestation',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatIconModule,
    MatButtonModule,
    TranslateModule,
  ],
  templateUrl: './step-attestation.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './step-attestation.component.css',
})
export class StepAttestationComponent implements OnInit, OnChanges {
  @Input() readonly = false;
  @Output() dataChange = new EventEmitter<Record<string, any>>();
  @Output() validChange = new EventEmitter<boolean>();
  @Output() deleteRequest = new EventEmitter<{ type: 'ATTESTATION'; id: string }>();

  form!: FormGroup;
  private readonly appShell = inject(AppShellStore);
  private readonly ficheStore = inject(PatientFicheStore);
  private readonly snackBar = inject(MatSnackBar);
  private readonly dialog = inject(MatDialog);
  private readonly translate = inject(TranslateService);
  private pendingPrint = false;
  private pendingDeleteId: string | null = null;

  history = signal<any[]>([]);
  selectedAttestationId = signal<string | null>(null);
  selectedAttestation = signal<any | null>(null);
  patientId: string | null = null;
  private readonly fb = inject(FormBuilder);

  constructor() {
    consumeWizardActionStatus(this.ficheStore, ({action, success, error, meta}) => {
      const effectiveError = error || this.ficheStore.error() || '';
      const actionMeta = meta ?? {};

      if (
        action === 'PRINT_DOCUMENT' &&
        this.pendingPrint &&
        actionMeta['typeDocument'] === 'ATTESTATION'
      ) {
        this.pendingPrint = false;
        if (!success && effectiveError) {
          this.snackBar.open(
            this.translate.instant('WIZARD.PRINT_ERROR', {detail: effectiveError}),
            'OK',
            {duration: 4000},
          );
        }
      }

      if (
        action === 'DELETE_ATTESTATION' &&
        this.pendingDeleteId &&
        actionMeta['attestationId'] === this.pendingDeleteId
      ) {
        const deletedId = this.pendingDeleteId;
        this.pendingDeleteId = null;
        if (!success) {
          this.snackBar.open(
            this.translate.instant('WIZARD.DELETE_ERROR', {detail: effectiveError}),
            'OK',
            {duration: 4000},
          );
          return;
        }
        this.history.set(
          this.history().filter((h) => (h.id ?? h.ID ?? '').toString() !== deletedId),
        );
        this.prepareNew();
        this.snackBar.open(this.translate.instant('WIZARD.ATTESTATION_DELETED_OK'), 'OK', {
          duration: 3000,
        });
        this.deleteRequest.emit({type: 'ATTESTATION', id: deletedId});
      }
    });
  }

  ngOnInit(): void {
    this.form = this.fb.group({
      attestationDebut: [null, Validators.required],
      attestationFin: [null, Validators.required],
    });
    this.form.valueChanges.subscribe(() => {
      this.dataChange.emit({
        ...this.form.getRawValue(),
        attestationId: this.selectedAttestationId(),
      });
      this.validChange.emit(this.form.valid);
    });
  }

  markTouched(): void {
    this.form.markAllAsTouched();
  }

  isValid(): boolean {
    return this.form.valid;
  }

  patchData(data: Record<string, any>): void {
    const pickLatest = (rows: any[]): any | null => {
      if (!Array.isArray(rows) || rows.length === 0) return null;
      const toMs = (raw: any): number => {
        if (!raw) return Number.NEGATIVE_INFINITY;
        const d = raw instanceof Date ? raw : new Date(raw);
        const ms = d.getTime();
        return Number.isNaN(ms) ? Number.NEGATIVE_INFINITY : ms;
      };
      return [...rows].sort((a, b) => {
        const aRef = toMs(a?.dateFin ?? a?.DATE_FIN ?? a?.dateDebut ?? a?.DATE_DEBUT);
        const bRef = toMs(b?.dateFin ?? b?.DATE_FIN ?? b?.dateDebut ?? b?.DATE_DEBUT);
        return bRef - aRef;
      })[0] ?? null;
    };

    if (!this.form) return;
    this.form.patchValue(
      {
        attestationDebut: data['attestationDebut'] ?? null,
        attestationFin: data['attestationFin'] ?? null,
      },
      {emitEvent: false},
    );

    const providedId = data['attestationId'] ?? null;
    this.patientId =
      (data['patientId'] ?? data['id'] ?? this.patientId ?? null)?.toString?.() ?? null;
    if (Array.isArray(data['attestationHistory'])) this.history.set(data['attestationHistory']);

    const latest = pickLatest(this.history());
    const selectedId =
      providedId ?? (latest ? (latest.id ?? latest.ID ?? null)?.toString?.() ?? null : null);
    this.selectedAttestationId.set(selectedId);

    if (!providedId && latest) {
      this.form.patchValue({
        attestationDebut: latest.dateDebut ?? latest.DATE_DEBUT ?? null,
        attestationFin: latest.dateFin ?? latest.DATE_FIN ?? null,
      }, {emitEvent: false});
    }

    const selected =
      this.history().find(
        (h) => (h?.id ?? h?.ID ?? '').toString() === (this.selectedAttestationId() ?? ''),
      ) ?? null;
    this.selectedAttestation.set(selected);

    this.dataChange.emit({
      ...this.form.getRawValue(),
      attestationId: this.selectedAttestationId(),
    });
    // Émettre la validité via les données, pas form.valid (qui serait faux si form disabled)
    const rv = this.form.getRawValue();
    this.validChange.emit(!!(rv.attestationDebut && rv.attestationFin));
    this.applyReadonly();
  }

  select(h: any): void {
    this.selectedAttestationId.set((h.id ?? h.ID ?? '').toString());
    this.patientId = (h.patientId ?? h.PATIENT_ID ?? this.patientId ?? null)?.toString?.() ?? null;
    this.selectedAttestation.set(h);
    this.form.patchValue({
      attestationDebut: h.dateDebut ?? h.DATE_DEBUT ?? null,
      attestationFin: h.dateFin ?? h.DATE_FIN ?? null,
    });
  }

  printSelected(): void {
    const centerId = this.appShell.currentCenterId();
    if (!centerId || !this.patientId) return;

    this.ficheStore.printDocument({
      centerId,
      typeDocument: 'ATTESTATION',
      params: {
        patientId: this.patientId,
        attestationId: this.selectedAttestationId() ?? '',
      },
    });
    this.pendingPrint = true;
  }

  isSelected(h: any): boolean {
    const id = (h?.id ?? h?.ID ?? '').toString();
    return id !== '' && this.selectedAttestationId() === id;
  }

  prepareNew(): void {
    this.selectedAttestationId.set(null);
    this.selectedAttestation.set(null);
    this.form.patchValue({attestationDebut: null, attestationFin: null});
  }

  deleteSelected(): void {
    const id = this.selectedAttestationId();
    if (!id) return;
    const centerId = this.appShell.currentCenterId();
    if (!centerId) return;

    const ref = this.dialog.open(ConfirmDialogComponent, {
      width: 'min(96vw, 440px)',
      data: {
        title: this.translate.instant('WIZARD.DELETE_ATTESTATION_TITLE'),
        message: this.translate.instant('WIZARD.DELETE_ATTESTATION_CONFIRM'),
        confirmLabel: this.translate.instant('COMMON.DELETE'),
        cancelLabel: this.translate.instant('PEC_ADMIN.CANCEL'),
        color: 'warn',
        icon: 'delete',
      },
    });

    ref.afterClosed().subscribe((confirmed) => {
      if (!confirmed) return;
      this.pendingDeleteId = id;
      this.ficheStore.deleteAttestation({
        attestationId: id,
        centerId,
        patientId: this.patientId ?? undefined,
      });
    });
    this.applyReadonly();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['readonly']) this.applyReadonly();
  }

  private applyReadonly(): void {
    if (!this.form) return;
    this.readonly
      ? this.form.disable({emitEvent: false})
      : this.form.enable({emitEvent: false});
  }
}
