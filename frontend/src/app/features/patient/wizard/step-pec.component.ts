import {
  ChangeDetectionStrategy,
  Component,
  computed,
  EventEmitter,
  inject,
  Input,
  OnChanges,
  OnInit,
  Output,
  signal,
  SimpleChanges,
} from '@angular/core';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatDatepickerModule} from '@angular/material/datepicker';
import {MatNativeDateModule} from '@angular/material/core';
import {MatIconModule} from '@angular/material/icon';
import {MatDividerModule} from '@angular/material/divider';
import {MatButtonModule} from '@angular/material/button';
import {MatChipsModule} from '@angular/material/chips';
import {MatSnackBar} from '@angular/material/snack-bar';
import {MatDialog} from '@angular/material/dialog';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {ForfaitsStore} from '../../../core/state/referentials.store';
import {ConfirmDialogComponent} from '../../../shared/confirm-dialog.component';
import {PatientFicheStore} from '../state/patient-fiche.store';
import {consumeWizardActionStatus} from './wizard-action-status.util';
import {requiredValidator, SignalForm} from '../../../shared/forms/signal-form';

interface PecModel {
  pecDateDebutDemande: Date | string | null;
  pecDateFinDemande: Date | string | null;
  pecForfaitDemandeId: string | null;
}

@Component({
  selector: 'app-step-pec',
  standalone: true,
  imports: [
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatIconModule,
    MatDividerModule,
    MatButtonModule,
    MatChipsModule,
    TranslateModule,
  ],
  templateUrl: './step-pec.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './step-pec.component.css',
})
export class StepPecComponent implements OnInit, OnChanges {
  @Input() patientId?: string;
  @Input() readonly = false;
  @Output() dataChange = new EventEmitter<Record<string, any>>();
  @Output() validChange = new EventEmitter<boolean>();
  @Output() deleteRequest = new EventEmitter<{ type: 'PEC'; id: string }>();

  readonly form = new SignalForm<PecModel>(
    {pecDateDebutDemande: null, pecDateFinDemande: null, pecForfaitDemandeId: null},
    {
      // Validateurs « requis » utilisés uniquement pour l'affichage des messages
      // (l'étape reste valide si les deux dates sont vides — voir stepValid()).
      pecDateDebutDemande: [requiredValidator()],
      pecDateFinDemande: [requiredValidator()],
    },
  );

  /** Validité métier : soit vide, soit les deux dates renseignées. */
  readonly stepValid = computed(() => {
    const v = this.form.value();
    const isEmpty = !v.pecDateDebutDemande && !v.pecDateFinDemande;
    const isComplete = !!v.pecDateDebutDemande && !!v.pecDateFinDemande;
    return isEmpty || isComplete;
  });

  history = signal<any[]>([]);
  selectedPecId = signal<string | null>(null);
  selectedPec = signal<any | null>(null);

  private readonly appShell = inject(AppShellStore);
  private readonly forfaitsStore = inject(ForfaitsStore);
  readonly forfaits = this.forfaitsStore.items;
  private readonly snackBar = inject(MatSnackBar);
  private readonly dialog = inject(MatDialog);
  private readonly translate = inject(TranslateService);
  private readonly ficheStore = inject(PatientFicheStore);
  private pendingPrint = false;
  private pendingDeleteId: string | null = null;

  constructor() {
    consumeWizardActionStatus(this.ficheStore, ({action, success, error, meta}) => {
      const effectiveError = error || this.ficheStore.error() || '';
      const actionMeta = meta ?? {};
      if (
        action === 'PRINT_DOCUMENT' &&
        this.pendingPrint &&
        actionMeta['typeDocument'] === 'PEC'
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
        action === 'DELETE_PEC' &&
        this.pendingDeleteId &&
        actionMeta['pecId'] === this.pendingDeleteId
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
        this.snackBar.open(this.translate.instant('WIZARD.PEC_DELETED_OK'), 'OK', {
          duration: 3000,
        });
        this.deleteRequest.emit({type: 'PEC', id: deletedId});
      }
    });
  }

  ngOnInit(): void {
    const cid = this.appShell.currentCenterId();
    if (!cid) return;
    void this.forfaitsStore.ensureLoaded(cid);
    this.applyReadonly();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['readonly']) this.applyReadonly();
  }

  onDateChange(key: 'pecDateDebutDemande' | 'pecDateFinDemande', value: Date | null): void {
    if (this.readonly) return;
    this.form.set(key, value);
    this.form.markTouched(key);
    this.emit();
  }

  markTouched(): void {
    this.form.markAllTouched();
  }

  patchData(data: Record<string, any>): void {
    this.form.patch({
      pecDateDebutDemande: data['pecDateDebutDemande'] ?? null,
      pecDateFinDemande: data['pecDateFinDemande'] ?? null,
      pecForfaitDemandeId: data['pecForfaitDemandeId'] ?? null,
    });

    this.selectedPecId.set(data['pecId'] ?? null);
    if (Array.isArray(data['pecHistory'])) this.history.set(data['pecHistory']);

    const selected =
      this.history().find(
        (h) => (h?.id ?? h?.ID ?? '').toString() === (this.selectedPecId() ?? ''),
      ) ?? null;
    this.selectedPec.set(selected);

    this.emit();
    this.applyReadonly();
  }

  isValid(): boolean {
    return this.stepValid();
  }

  select(h: any): void {
    this.selectedPecId.set((h.id ?? h.ID ?? '').toString());
    this.selectedPec.set(h);
    this.form.patch({
      pecDateDebutDemande: h.dateDebutDemande ?? h.DATE_DEBUT_DEMANDE ?? null,
      pecDateFinDemande: h.dateFinDemande ?? h.DATE_FIN_DEMANDE ?? null,
      pecForfaitDemandeId: h.forfaitDemandeId ?? h.FORFAIT_DEMANDE_ID ?? null,
    });
    this.emit();
  }

  prepareNew(): void {
    this.selectedPecId.set(null);
    this.selectedPec.set(null);
    this.form.patch({
      pecDateDebutDemande: null,
      pecDateFinDemande: null,
      pecForfaitDemandeId: null,
    });
    this.emit();
  }

  isSelected(h: any): boolean {
    const id = (h?.id ?? h?.ID ?? '').toString();
    return id !== '' && this.selectedPecId() === id;
  }

  getForfaitLabel(id: string | null | undefined): string {
    if (!id) return '—';
    const f = this.forfaits().find((x) => x.id === id);
    if (!f) return id;
    const label = f.label || f.nom || f.code || 'Forfait';
    const prix = this.formatPrix(f);
    return prix ? `${label} (${prix})` : label;
  }

  selectForfait(id: string): void {
    if (this.readonly) return;
    this.form.set('pecForfaitDemandeId', id);
    this.emit();
  }

  formatPrix(f: any): string {
    const raw = f?.prix ?? f?.price ?? f?.montant;
    if (raw === null || raw === undefined || raw === '') return '';
    const n = Number(raw);
    return Number.isNaN(n) ? String(raw) : `${n.toLocaleString('fr-FR')} DZD`;
  }

  getStatusIcon(status: string | undefined): string {
    switch (status) {
      case 'VALIDEE':
        return 'check_circle';
      case 'CLOTUREE':
        return 'lock';
      default:
        return 'pending';
    }
  }

  printSelected(): void {
    const centerId = this.appShell.currentCenterId();
    if (!centerId || !this.patientId) return;

    this.ficheStore.printDocument({
      centerId,
      typeDocument: 'PEC',
      params: {
        patientId: this.patientId,
        pecId: this.selectedPecId() ?? '',
      },
    });
    this.pendingPrint = true;
  }

  getStatusLabel(status: string | undefined): string {
    switch (status) {
      case 'VALIDEE':
        return this.translate.instant('STATUS.VALIDEE_TITLE');
      case 'CLOTUREE':
        return this.translate.instant('STATUS.CLOTUREE_TITLE');
      default:
        return this.translate.instant('STATUS.CREE_TITLE');
    }
  }

  deleteSelected(): void {
    const id = this.selectedPecId();
    if (!id) return;
    const centerId = this.appShell.currentCenterId();
    if (!centerId) return;

    const ref = this.dialog.open(ConfirmDialogComponent, {
      width: 'min(96vw, 440px)',
      data: {
        title: this.translate.instant('WIZARD.DELETE_PEC_TITLE'),
        message: this.translate.instant('WIZARD.DELETE_PEC_CONFIRM'),
        confirmLabel: this.translate.instant('COMMON.DELETE'),
        cancelLabel: this.translate.instant('PEC_ADMIN.CANCEL'),
        color: 'warn',
        icon: 'delete',
      },
    });

    ref.afterClosed().subscribe((confirmed) => {
      if (!confirmed) return;
      this.pendingDeleteId = id;
      this.ficheStore.deletePec({pecId: id, centerId, patientId: this.patientId});
    });
  }

  private emit(): void {
    this.dataChange.emit({...this.form.value(), pecId: this.selectedPecId()});
    this.validChange.emit(this.stepValid());
  }

  private applyReadonly(): void {
    this.form.setDisabled(this.readonly);
  }
}
