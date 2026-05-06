import {Component, EventEmitter, inject, Input, OnChanges, OnInit, Output, signal, SimpleChanges} from '@angular/core';
import {FormBuilder, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
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

@Component({
  selector: 'app-step-pec',
  standalone: true,
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatDatepickerModule, MatNativeDateModule, MatIconModule, MatDividerModule, MatButtonModule, MatChipsModule, TranslateModule],
  template: `
    <div class="step-content">
      <div class="pec-grid">
        <div class="history-pane">
          <div class="history-head">
            <h3 class="section-title">{{ 'WIZARD.PEC_HISTORY' | translate }}</h3>
            <button type="button" mat-flat-button class="add-btn" (click)="prepareNew()" [disabled]="readonly">
              <mat-icon>add</mat-icon> {{ 'WIZARD.ENRICH_PEC' | translate }}
            </button>
          </div>

          @if (history().length === 0) {
            <p class="empty">{{ 'WIZARD.PEC_HISTORY_EMPTY' | translate }}</p>
          } @else {
            <div class="history-list">
              @for (h of history(); track $index) {
                <button type="button" class="history-item" [class.active]="isSelected(h)" (click)="select(h)">
                  <div>
                    <strong>{{ h.dateDebutDemande || h.DATE_DEBUT_DEMANDE }}</strong>
                    <span> → {{ h.dateFinDemande || h.DATE_FIN_DEMANDE }}</span>
                  </div>
                  <small>{{ h.status || h.STATUT || 'CREE' }}</small>
                </button>
              }
            </div>
          }
        </div>

        <div>
          @if (selectedPec()) {
            <div class="detail-card">
              <div class="detail-head">
                <strong>{{ 'WIZARD.PEC_SELECTED' | translate }}</strong>
                <div class="detail-actions">
                  <button type="button" mat-stroked-button color="warn" (click)="deleteSelected()" [disabled]="readonly || !selectedPecId()">
                    <mat-icon>delete</mat-icon>
                    {{ 'COMMON.DELETE' | translate }}
                  </button>
                  <button type="button" mat-stroked-button (click)="printSelected()" [disabled]="!patientId">
                    <mat-icon>print</mat-icon>
                    {{ 'COMMON.PRINT' | translate }}
                  </button>
                </div>
              </div>

              <div class="detail-section">
                <h5 class="detail-subtitle"><mat-icon class="detail-subtitle-icon">description</mat-icon> {{ 'WIZARD.PEC_REQUEST_BLOCK' | translate }}</h5>
                <div class="detail-grid">
                  <span>{{ 'WIZARD.FIELD_PERIOD' | translate }}</span><strong>{{ selectedPec()?.dateDebutDemande || selectedPec()?.DATE_DEBUT_DEMANDE }} → {{ selectedPec()?.dateFinDemande || selectedPec()?.DATE_FIN_DEMANDE }}</strong>
                  <span>{{ 'WIZARD.FIELD_STATUS' | translate }}</span>
                  <mat-chip-set class="status-chip-set">
                    <mat-chip [class]="'status-chip status-' + (selectedPec()?.status || selectedPec()?.STATUT || 'CREE')" [disableRipple]="true">
                      <mat-icon matChipAvatar>{{ getStatusIcon(selectedPec()?.status || selectedPec()?.STATUT) }}</mat-icon>
                      {{ getStatusLabel(selectedPec()?.status || selectedPec()?.STATUT) }}
                    </mat-chip>
                  </mat-chip-set>
                </div>
              </div>

              @if ((selectedPec()?.status || selectedPec()?.STATUT) === 'VALIDEE' || (selectedPec()?.status || selectedPec()?.STATUT) === 'CLOTUREE') {
                <div class="detail-section accord-section">
                  <h5 class="detail-subtitle"><mat-icon class="detail-subtitle-icon">verified</mat-icon> {{ 'WIZARD.PEC_APPROVAL_BLOCK' | translate }}</h5>
                  <div class="detail-grid">
                    <span>{{ 'WIZARD.FIELD_EFFECTIVE_PERIOD' | translate }}</span><strong>{{ selectedPec()?.dateDebutEffectif || selectedPec()?.DATE_DEBUT_EFFECTIF || '—' }} → {{ selectedPec()?.dateFinEffectif || selectedPec()?.DATE_FIN_EFFECTIF || '—' }}</strong>
                    @if (selectedPec()?.forfaitEffectifId || selectedPec()?.FORFAIT_EFFECTIF_ID) {
                      <span>{{ 'WIZARD.FIELD_EFFECTIVE_FORFAIT' | translate }}</span><strong>{{ getForfaitLabel(selectedPec()?.forfaitEffectifId || selectedPec()?.FORFAIT_EFFECTIF_ID) }}</strong>
                    }
                  </div>
                </div>
              }
            </div>
          }

          <h3 class="section-title">{{ 'WIZARD.PEC_DEMANDE' | translate }}</h3>
          <form [formGroup]="form">
            <div class="form-row">
              <mat-form-field appearance="outline" class="flex1">
                <mat-label>{{ 'WIZARD.PEC_DATE_DEBUT' | translate }} *</mat-label>
                <mat-icon matPrefix>event</mat-icon>
                <input matInput [matDatepicker]="dpDeb" formControlName="pecDateDebutDemande" />
                <mat-datepicker-toggle matSuffix [for]="dpDeb" /><mat-datepicker #dpDeb />
                @if (form.get('pecDateDebutDemande')?.hasError('required') && form.get('pecDateDebutDemande')?.touched) {
                  <mat-error>{{ 'PATIENT_FORM.REQUIRED' | translate }}</mat-error>
                }
              </mat-form-field>
              <mat-form-field appearance="outline" class="flex1">
                <mat-label>{{ 'WIZARD.PEC_DATE_FIN' | translate }} *</mat-label>
                <mat-icon matPrefix>event_busy</mat-icon>
                <input matInput [matDatepicker]="dpFin" formControlName="pecDateFinDemande" />
                <mat-datepicker-toggle matSuffix [for]="dpFin" /><mat-datepicker #dpFin />
                @if (form.get('pecDateFinDemande')?.hasError('required') && form.get('pecDateFinDemande')?.touched) {
                  <mat-error>{{ 'PATIENT_FORM.REQUIRED' | translate }}</mat-error>
                }
              </mat-form-field>
            </div>

            <h4 class="forfait-title">{{ 'WIZARD.PEC_FORFAIT_DEMANDE' | translate }}</h4>
            <div class="forfait-grid">
              @for (f of forfaits(); track f.id) {
                <button type="button"
                  class="forfait-chip"
                  [class.selected]="form.get('pecForfaitDemandeId')?.value === f.id"
                  [disabled]="readonly"
                  (click)="selectForfait(f.id)">
                  <mat-icon class="forfait-icon">local_hospital</mat-icon>
                  <span class="forfait-label">{{ f.label || f['nom'] || f['code'] || 'Forfait' }}</span>
                  @if (formatPrix(f)) {
                    <span class="forfait-sep">•</span>
                    <span class="forfait-prix">{{ formatPrix(f) }}</span>
                  }
                </button>
              }
            </div>
          </form>

          <mat-divider style="margin: 20px 0;" />
          <h3 class="section-title">{{ 'WIZARD.PEC_ACCORD' | translate }}</h3>
          @if ((selectedPec()?.status || selectedPec()?.STATUT) === 'VALIDEE' || (selectedPec()?.status || selectedPec()?.STATUT) === 'CLOTUREE') {
            <p class="empty">{{ 'WIZARD.PEC_ACCORD_DESC' | translate }}</p>
            <div class="accord-readonly-card">
              <div class="form-row">
                <mat-form-field appearance="outline" class="flex1">
                  <mat-label>{{ 'WIZARD.PEC_EFFECTIVE_START' | translate }}</mat-label>
                  <mat-icon matPrefix>event_available</mat-icon>
                  <input matInput [value]="selectedPec()?.dateDebutEffectif || selectedPec()?.DATE_DEBUT_EFFECTIF || '—'" disabled />
                </mat-form-field>
                <mat-form-field appearance="outline" class="flex1">
                  <mat-label>{{ 'WIZARD.PEC_EFFECTIVE_END' | translate }}</mat-label>
                  <mat-icon matPrefix>event_busy</mat-icon>
                  <input matInput [value]="selectedPec()?.dateFinEffectif || selectedPec()?.DATE_FIN_EFFECTIF || '—'" disabled />
                </mat-form-field>
              </div>
              <div class="form-row">
                <mat-form-field appearance="outline" class="flex1">
                  <mat-label>{{ 'WIZARD.FIELD_EFFECTIVE_FORFAIT' | translate }}</mat-label>
                  <mat-icon matPrefix>local_hospital</mat-icon>
                  <input matInput [value]="getForfaitLabel(selectedPec()?.forfaitEffectifId || selectedPec()?.FORFAIT_EFFECTIF_ID)" disabled />
                </mat-form-field>
                <mat-form-field appearance="outline" class="flex1">
                  <mat-label>{{ 'WIZARD.FIELD_STATUS' | translate }}</mat-label>
                  <mat-icon matPrefix>verified</mat-icon>
                  <input matInput [value]="getStatusLabel(selectedPec()?.status || selectedPec()?.STATUT)" disabled />
                </mat-form-field>
              </div>
            </div>
          } @else {
            <p class="empty">{{ 'WIZARD.PEC_ACCORD_DESC' | translate }}</p>
          }
        </div>
      </div>
    </div>
  `,
  styles: [`
    .step-content { padding: 14px 18px 18px; }
    .pec-grid { display:grid; grid-template-columns: 320px 1fr; gap:18px; align-items:start; }
    .history-pane {
      border:1px solid var(--app-border); border-radius:12px; padding:12px;
      background: var(--app-surface);
      box-shadow: var(--app-shadow);
      min-height:160px;
    }
    .history-head { display:flex; align-items:center; justify-content:space-between; gap:8px; margin-bottom:8px; }
    .add-btn { --mdc-filled-button-container-color:var(--app-primary); --mdc-filled-button-label-text-color:#fff; }
    .section-title { color: var(--app-text); font-size: 1.02rem; font-weight: 700; margin: 0 0 12px; }
    .empty { color:var(--app-muted); font-style:italic; }
    .history-list { display:flex; flex-direction:column; gap:6px; }
    .history-item {
      display:flex; justify-content:space-between; align-items:center;
      width:100%; border:1px solid var(--app-border); border-radius:10px; padding:8px 10px;
      background:var(--app-surface); text-align:left; cursor:pointer;
      font-size:12px;
    }
    .history-item.active { background:var(--app-primary-soft); border-color:var(--app-primary-outline); }
    .detail-card {
      background:var(--app-surface);
      border:1px solid var(--app-border);
      border-radius:12px;
      padding:10px 12px;
      margin-bottom:12px;
    }
    .detail-head { display:flex; justify-content:space-between; align-items:center; margin-bottom:8px; }
    .detail-actions { display:flex; gap:8px; align-items:center; }
    .detail-section { margin-bottom: 8px; }
    .detail-subtitle {
      display: flex; align-items: center; gap: 6px;
      font-size: 12px; font-weight: 600; color: #374151; margin: 0 0 6px;
    }
    .detail-subtitle-icon { font-size: 16px; width: 16px; height: 16px; color: var(--app-primary); }
    .accord-section {
      background: var(--app-primary-soft); border: 1px solid var(--app-primary-outline); border-radius: 8px; padding: 8px 10px; margin-top: 4px;
    }
    .accord-section .detail-subtitle-icon { color: var(--app-primary); }
    .status-chip { font-size: 12px !important; font-weight: 600 !important; height: 26px !important; padding: 0 10px !important; }
    .status-CREE { --mdc-chip-elevated-container-color: #e0f2fe !important; --mdc-chip-label-text-color: #0369a1 !important; }
    .status-VALIDEE { --mdc-chip-elevated-container-color: #dcfce7 !important; --mdc-chip-label-text-color: #166534 !important; }
    .status-CLOTUREE { --mdc-chip-elevated-container-color: #fef3c7 !important; --mdc-chip-label-text-color: #92400e !important; }
    :host ::ng-deep .status-CREE .mat-mdc-chip-action-label { color: #0369a1 !important; }
    :host ::ng-deep .status-VALIDEE .mat-mdc-chip-action-label { color: #166534 !important; }
    :host ::ng-deep .status-CLOTUREE .mat-mdc-chip-action-label { color: #92400e !important; }
    .status-chip-set { margin: 0; }
    .detail-grid { display:grid; grid-template-columns:120px 1fr; row-gap:6px; font-size:13px; align-items:center; }
    .accord-readonly-card {
      margin-top: 8px;
      border: 1px solid var(--app-border);
      border-radius: 12px;
      padding: 10px 12px;
      background: var(--app-surface-soft);
    }
    .form-row { display: flex; gap: 12px; margin-bottom: 8px; }
    .flex1 { flex: 1; }
    .forfait-title { margin: 8px 0; color:var(--app-text); }
    .forfait-grid { display:flex; flex-wrap:wrap; gap:10px; }
    .forfait-chip {
      display: inline-flex; align-items: center; gap: 8px;
      padding: 10px 18px;
      border: 2px solid var(--app-border);
      border-left: 4px solid var(--app-border);
      border-radius: 10px;
      background: var(--app-surface);
      cursor: pointer;
      font-size: 13px;
      font-weight: 500;
      color: var(--app-text);
      transition: all .25s cubic-bezier(.4,0,.2,1);
      box-shadow: 0 1px 4px rgba(0,0,0,.05);
    }
    .forfait-chip:hover:not([disabled]) {
      transform: translateY(-2px) scale(1.02);
      border-color: var(--app-primary-outline);
      border-left-color: var(--app-primary);
      background: var(--app-primary-soft);
      box-shadow: 0 8px 18px rgba(2,6,23,.12);
    }
    .forfait-chip:hover:not([disabled]) .forfait-icon { color: var(--app-primary); transform: scale(1.15); }
    .forfait-chip.selected {
      border-color: var(--app-primary);
      border-left-width: 5px;
      background: var(--app-primary-soft);
      color: var(--app-primary);
      box-shadow: 0 6px 16px rgba(2,6,23,.12);
      font-weight: 600;
    }
    .forfait-chip.selected .forfait-icon { color: var(--app-primary); }
    .forfait-chip[disabled] { opacity: .45; cursor: default; }
    .forfait-icon { font-size: 20px; width: 20px; height: 20px; color: var(--app-primary-outline); transition: all .25s ease; }
    .forfait-label { white-space: nowrap; }
    .forfait-sep { color: #d1d5db; margin: 0 2px; }
    .forfait-prix { font-size: 12px; font-weight: 700; color: var(--app-primary); padding: 2px 8px; background: #e0f2fe; border-radius: 6px; }
    .forfait-chip.selected .forfait-prix { background: #bae6fd; color: #0c4a6e; }
    :host ::ng-deep .mat-mdc-form-field { font-size: 13px; }
    :host ::ng-deep .mat-mdc-form-field-subscript-wrapper { display: none; }
    :host ::ng-deep input.mat-mdc-input-element { text-align: center; }

    @media (max-width: 980px) {
      .pec-grid { grid-template-columns: 1fr; }
      .history-pane { min-height: auto; }
      .detail-head { flex-direction: column; align-items: flex-start; gap: 8px; }
      .detail-actions { width: 100%; flex-wrap: wrap; }
      .detail-grid { grid-template-columns: 100px 1fr; }
      .forfait-grid { gap: 8px; }
      .forfait-chip { width: 100%; justify-content: center; }
    }
  `]
})
export class StepPecComponent implements OnInit, OnChanges {
  @Input() patientId?: string;
  @Input() readonly = false;
  @Output() dataChange = new EventEmitter<Record<string, any>>();
  @Output() validChange = new EventEmitter<boolean>();
  @Output() deleteRequest = new EventEmitter<{ type: 'PEC'; id: string }>();

  private readonly fb = inject(FormBuilder);
  private readonly appShell = inject(AppShellStore);
  private readonly forfaitsStore = inject(ForfaitsStore);
  readonly forfaits = this.forfaitsStore.items;
  private readonly snackBar = inject(MatSnackBar);
  private readonly dialog = inject(MatDialog);
  private readonly translate = inject(TranslateService);
  private readonly ficheStore = inject(PatientFicheStore);
  history = signal<any[]>([]);
  selectedPecId = signal<string | null>(null);
  selectedPec = signal<any | null>(null);

  form!: FormGroup;

  ngOnInit(): void {
    this.form = this.fb.group({
      pecDateDebutDemande: [null, Validators.required],
      pecDateFinDemande: [null, Validators.required],
      pecForfaitDemandeId: [null]
    });

    this.form.valueChanges.subscribe(() => {
      const v = this.form.getRawValue();
      const isEmpty = !v.pecDateDebutDemande && !v.pecDateFinDemande;
      const isComplete = !!v.pecDateDebutDemande && !!v.pecDateFinDemande;
      this.dataChange.emit({ ...v, pecId: this.selectedPecId() });
      this.validChange.emit(isEmpty || (isComplete && this.form.valid));
    });

    const cid = this.appShell.currentCenterId();
    if (!cid) return;
    void this.forfaitsStore.ensureLoaded(cid);
    this.applyReadonly();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['readonly']) this.applyReadonly();
  }

  private applyReadonly(): void {
    if (!this.form) return;
    this.readonly ? this.form.disable({ emitEvent: false }) : this.form.enable({ emitEvent: false });
  }

  markTouched(): void { this.form.markAllAsTouched(); }
  isValid(): boolean {
    const v = this.form.getRawValue();
    const isEmpty = !v.pecDateDebutDemande && !v.pecDateFinDemande;
    return isEmpty || this.form.valid;
  }

  patchData(data: Record<string, any>): void {
    if (!this.form) return;
    this.form.patchValue({
      pecDateDebutDemande: data['pecDateDebutDemande'] ?? null,
      pecDateFinDemande: data['pecDateFinDemande'] ?? null,
      pecForfaitDemandeId: data['pecForfaitDemandeId'] ?? null
    }, { emitEvent: false });

    this.selectedPecId.set(data['pecId'] ?? null);
    if (Array.isArray(data['pecHistory'])) this.history.set(data['pecHistory']);

    const selected = this.history().find(h => (h?.id ?? h?.ID ?? '').toString() === (this.selectedPecId() ?? '')) ?? null;
    this.selectedPec.set(selected);

    const v = this.form.getRawValue();
    const isEmpty = !v.pecDateDebutDemande && !v.pecDateFinDemande;
    this.dataChange.emit({ ...v, pecId: this.selectedPecId() });
    this.validChange.emit(isEmpty || this.form.valid);
    this.applyReadonly();
  }

  select(h: any): void {
    this.selectedPecId.set((h.id ?? h.ID ?? '').toString());
    this.selectedPec.set(h);
    this.form.patchValue({
      pecDateDebutDemande: h.dateDebutDemande ?? h.DATE_DEBUT_DEMANDE ?? null,
      pecDateFinDemande: h.dateFinDemande ?? h.DATE_FIN_DEMANDE ?? null,
      pecForfaitDemandeId: h.forfaitDemandeId ?? h.FORFAIT_DEMANDE_ID ?? null
    });
  }

  isSelected(h: any): boolean {
    const id = (h?.id ?? h?.ID ?? '').toString();
    return id !== '' && this.selectedPecId() === id;
  }

  prepareNew(): void {
    this.selectedPecId.set(null);
    this.selectedPec.set(null);
    this.form.patchValue({ pecDateDebutDemande: null, pecDateFinDemande: null, pecForfaitDemandeId: null });
  }

  selectForfait(id: string): void {
    if (this.readonly) return;
    this.form.patchValue({ pecForfaitDemandeId: id });
  }

  formatPrix(f: any): string {
    const raw = f?.prix ?? f?.price ?? f?.montant;
    if (raw === null || raw === undefined || raw === '') return '';
    const n = Number(raw);
    return Number.isNaN(n) ? String(raw) : `${n.toLocaleString('fr-FR')} DZD`;
  }

  getForfaitLabel(id: string | null | undefined): string {
    if (!id) return '—';
    const f = this.forfaits().find(x => x.id === id);
    if (!f) return id;
    const label = f.label || f.nom || f.code || 'Forfait';
    const prix = this.formatPrix(f);
    return prix ? `${label} (${prix})` : label;
  }

  getStatusIcon(status: string | undefined): string {
    switch (status) {
      case 'VALIDEE': return 'check_circle';
      case 'CLOTUREE': return 'lock';
      default: return 'pending';
    }
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

  printSelected(): void {
    const centerId = this.appShell.currentCenterId();
    if (!centerId || !this.patientId) return;

    this.ficheStore.printDocument({
      centerId,
      typeDocument: 'PEC',
      params: {
        patientId: this.patientId,
        pecId: this.selectedPecId() ?? ''
      }
    });

    setTimeout(() => {
      const err = this.ficheStore.error();
      if (err) {
        this.snackBar.open(this.translate.instant('WIZARD.PRINT_ERROR', {detail: err}), 'OK', {duration: 4000});
      }
    });
  }

  deleteSelected(): void {
    const id = this.selectedPecId();
    if (!id) return;
    const centerId = this.appShell.currentCenterId();
    if (!centerId) return;

    const ref = this.dialog.open(ConfirmDialogComponent, {
      width: '440px',
      data: {
        title: this.translate.instant('WIZARD.DELETE_PEC_TITLE'),
        message: this.translate.instant('WIZARD.DELETE_PEC_CONFIRM'),
        confirmLabel: this.translate.instant('COMMON.DELETE'),
        cancelLabel: this.translate.instant('PEC_ADMIN.CANCEL'),
        color: 'warn',
        icon: 'delete'
      }
    });

    ref.afterClosed().subscribe(confirmed => {
      if (!confirmed) return;
      this.ficheStore.deletePec({pecId: id, centerId, patientId: this.patientId});
      setTimeout(() => {
        const err = this.ficheStore.error();
        if (err) {
          this.snackBar.open(this.translate.instant('WIZARD.DELETE_ERROR', {detail: err}), 'OK', {duration: 4000});
          return;
        }
        this.history.set(this.history().filter(h => (h.id ?? h.ID ?? '').toString() !== id));
        this.prepareNew();
        this.snackBar.open(this.translate.instant('WIZARD.PEC_DELETED_OK'), 'OK', {duration: 3000});
        this.deleteRequest.emit({type: 'PEC', id});
      });
    });
  }
}
