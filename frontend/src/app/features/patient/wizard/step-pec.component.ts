import { Component, OnInit, Output, EventEmitter, inject, signal, Input, OnChanges, SimpleChanges } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatIconModule } from '@angular/material/icon';
import { MatDividerModule } from '@angular/material/divider';
import { MatButtonModule } from '@angular/material/button';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslateModule } from '@ngx-translate/core';
import { ReferentialApiService } from '../../../core/api/referential-api.service';
import { AppShellStore } from '../../../core/state/app-shell.store';
import { BackendApiService } from '../../../core/api/backend-api.service';

@Component({
  selector: 'app-step-pec',
  standalone: true,
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatDatepickerModule, MatNativeDateModule, MatIconModule, MatDividerModule, MatButtonModule, TranslateModule],
  template: `
    <div class="step-content">
      <div class="pec-grid">
        <div class="history-pane">
          <div class="history-head">
            <h3 class="section-title">Historique des prises en charge</h3>
            <button type="button" mat-flat-button class="add-btn" (click)="prepareNew()" [disabled]="readonly">
              <mat-icon>add</mat-icon> {{ 'WIZARD.ENRICH_PEC' | translate }}
            </button>
          </div>

          @if (history().length === 0) {
            <p class="empty">Aucune prise en charge precedente</p>
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
                <strong>Prise en charge selectionnee</strong>
                <button type="button" mat-stroked-button (click)="printSelected()" [disabled]="!patientId">
                  <mat-icon>print</mat-icon>
                  {{ 'COMMON.PRINT' | translate }}
                </button>
              </div>
              <div class="detail-grid">
                <span>Periode</span><strong>{{ selectedPec()?.dateDebutDemande || selectedPec()?.DATE_DEBUT_DEMANDE }} → {{ selectedPec()?.dateFinDemande || selectedPec()?.DATE_FIN_DEMANDE }}</strong>
                <span>Statut</span><strong>{{ selectedPec()?.status || selectedPec()?.STATUT || '—' }}</strong>
              </div>
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
                    <span class="forfait-prix">{{ formatPrix(f) }}</span>
                  }
                </button>
              }
            </div>
          </form>

          <mat-divider style="margin: 20px 0;" />
          <h3 class="section-title">{{ 'WIZARD.PEC_ACCORD' | translate }}</h3>
          <p class="empty">{{ 'WIZARD.PEC_ACCORD_DESC' | translate }}</p>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .step-content { padding: 14px 20px 20px; }
    .pec-grid { display:grid; grid-template-columns: 320px 1fr; gap:18px; align-items:start; }
    .history-pane {
      border:1px solid #d9e7dd; border-radius:12px; padding:12px;
      background: linear-gradient(180deg, #f7fcf8 0%, #f1f8f3 100%);
      box-shadow: 0 6px 18px rgba(27,94,32,.06);
      min-height:160px;
    }
    .history-head { display:flex; align-items:center; justify-content:space-between; gap:8px; margin-bottom:8px; }
    .add-btn { --mdc-filled-button-container-color:#1b5e20; --mdc-filled-button-label-text-color:#fff; }
    .section-title { color: #1b5e20; font-size: 1.02rem; font-weight: 700; margin: 0 0 12px; }
    .empty { color:#888; font-style:italic; }
    .history-list { display:flex; flex-direction:column; gap:6px; }
    .history-item {
      display:flex; justify-content:space-between; align-items:center;
      width:100%; border:1px solid #deebdf; border-radius:10px; padding:8px 10px;
      background:#fff; text-align:left; cursor:pointer;
      font-size:12px;
    }
    .history-item.active { background:#e9f6ed; border-color:#b9ddc2; }
    .detail-card {
      background:#fff;
      border:1px solid #deebdf;
      border-radius:12px;
      padding:10px 12px;
      margin-bottom:12px;
    }
    .detail-head { display:flex; justify-content:space-between; align-items:center; margin-bottom:8px; }
    .detail-grid { display:grid; grid-template-columns:90px 1fr; row-gap:6px; font-size:13px; }
    .form-row { display: flex; gap: 12px; margin-bottom: 8px; }
    .flex1 { flex: 1; }
    .forfait-title { margin: 8px 0; color:#1f2937; }
    .forfait-grid { display:flex; flex-wrap:wrap; gap:10px; }
    .forfait-chip {
      display: inline-flex; align-items: center; gap: 6px;
      padding: 8px 16px;
      border: 2px solid #e5e7eb;
      border-radius: 10px;
      background: #fff;
      cursor: pointer;
      font-size: 13px;
      font-weight: 500;
      color: #1f2937;
      transition: all .2s ease;
      box-shadow: 0 1px 4px rgba(0,0,0,.05);
    }
    .forfait-chip:hover:not([disabled]) {
      border-color: #1b5e20;
      background: #f0fdf4;
      box-shadow: 0 3px 10px rgba(27,94,32,.1);
    }
    .forfait-chip.selected {
      border-color: #1b5e20;
      background: #eefbf0;
      color: #1b5e20;
      box-shadow: 0 3px 12px rgba(27,94,32,.15);
    }
    .forfait-chip[disabled] { opacity: .5; cursor: default; }
    .forfait-icon { font-size: 18px; width: 18px; height: 18px; color: #1b5e20; }
    .forfait-label { white-space: nowrap; }
    .forfait-prix { font-size: 12px; font-weight: 700; color: #1b5e20; margin-left: 2px; }
    .forfait-chip.selected .forfait-prix { color: #166534; }
    :host ::ng-deep .mat-mdc-form-field { font-size: 13px; }
    :host ::ng-deep .mat-mdc-form-field-subscript-wrapper { display: none; }
    :host ::ng-deep input.mat-mdc-input-element { text-align: center; }
  `]
})
export class StepPecComponent implements OnInit, OnChanges {
  @Input() patientId?: string;
  @Input() readonly = false;
  @Output() dataChange = new EventEmitter<Record<string, any>>();
  @Output() validChange = new EventEmitter<boolean>();

  private readonly fb = inject(FormBuilder);
  private readonly refApi = inject(ReferentialApiService);
  private readonly store = inject(AppShellStore);
  private readonly api = inject(BackendApiService);
  private readonly snackBar = inject(MatSnackBar);

  forfaits = signal<any[]>([]);
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

    const cid = this.store.currentCenterId();
    if (!cid) return;
    this.refApi.getForfaits(cid).subscribe(list =>
      this.forfaits.set(list.map((f: any) => ({ ...f, id: f.id, label: f.nom ?? f.code ?? 'Forfait' })))
    );
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

  forfaitColor(f: any): string {
    const code = String(f?.code ?? f?.id ?? 'F').toUpperCase();
    if (code.includes('1') || code.includes('A')) return '#3b82f6';
    if (code.includes('2') || code.includes('B')) return '#8b5cf6';
    if (code.includes('3') || code.includes('C')) return '#0ea5e9';
    return '#14b8a6';
  }

  printSelected(): void {
    const centerId = this.store.currentCenterId();
    if (!centerId || !this.patientId) return;

    this.api.printDocument(centerId, 'PEC', {
      patientId: this.patientId,
      pecId: this.selectedPecId() ?? ''
    }).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        window.open(url, '_blank');
      },
      error: (err) => this.snackBar.open('Erreur impression: ' + (err?.error?.text || err.message), 'OK', { duration: 4000 })
    });
  }
}
