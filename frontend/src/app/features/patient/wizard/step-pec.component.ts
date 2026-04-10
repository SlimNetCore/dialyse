import { Component, OnInit, Output, EventEmitter, inject, signal, Input, OnChanges, SimpleChanges } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatIconModule } from '@angular/material/icon';
import { MatDividerModule } from '@angular/material/divider';
import { MatButtonModule } from '@angular/material/button';
import { TranslateModule } from '@ngx-translate/core';
import { SearchableSelectComponent, DropdownItem } from '../../../shared/searchable-select.component';
import { ReferentialApiService } from '../../../core/api/referential-api.service';
import { AppShellStore } from '../../../core/state/app-shell.store';
import { BackendApiService } from '../../../core/api/backend-api.service';

@Component({
  selector: 'app-step-pec',
  standalone: true,
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatDatepickerModule, MatNativeDateModule, MatIconModule, MatDividerModule, MatButtonModule, TranslateModule, SearchableSelectComponent],
  template: `
    <div class="step-content">
      <div class="pec-grid">
        <div class="history-pane">
          <div style="display:flex;align-items:center;justify-content:space-between;gap:8px;">
            <h3 class="section-title">Historique des prises en charge</h3>
            <button type="button" mat-stroked-button (click)="prepareNew()" [disabled]="readonly">
              <mat-icon>add</mat-icon> {{ 'WIZARD.ENRICH_PEC' | translate }}
            </button>
          </div>
          @if (history().length === 0) {
            <p style="color:#888;font-style:italic">Aucune prise en charge precedente</p>
          } @else {
            <div class="history-box">
              @for (h of history(); track $index) {
                <div class="history-item">
                  <span>{{ h.dateDebutDemande || h.DATE_DEBUT_DEMANDE }} → {{ h.dateFinDemande || h.DATE_FIN_DEMANDE }}</span>
                  <strong>{{ h.status || h.STATUT }}</strong>
                  <button type="button" mat-button (click)="edit(h)" [disabled]="readonly">{{ 'COMMON.UPDATE' | translate }}</button>
                </div>
              }
            </div>
          }
        </div>

        <div>
      <!-- Demande -->
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
          <app-searchable-select [items]="forfaits()" [label]="'WIZARD.PEC_FORFAIT_DEMANDE' | translate"
            [disabled]="readonly"
            [selectedId]="form.get('pecForfaitDemandeId')?.value" (selectionChanged)="form.patchValue({pecForfaitDemandeId: $event?.id})" cssClass="flex1" />
        </div>
      </form>

      <mat-divider style="margin: 20px 0;" />

      <!-- Accord (informational — filled by admin later) -->
      <h3 class="section-title">{{ 'WIZARD.PEC_ACCORD' | translate }}</h3>
      <p style="color: #888; font-style: italic;">{{ 'WIZARD.PEC_ACCORD_DESC' | translate }}</p>

      <mat-divider style="margin: 20px 0;" />
        </div>
      </div>
    </div>
  `,
  styles: [`
    .step-content { padding: 14px 20px 20px; }
    .pec-grid { display:grid; grid-template-columns: 300px 1fr; gap:18px; align-items:start; }
    .history-pane {
      border:1px solid #d9e7dd; border-radius:12px; padding:12px;
      background: linear-gradient(180deg, #f7fcf8 0%, #f1f8f3 100%);
      box-shadow: 0 6px 18px rgba(27,94,32,.06);
      min-height:160px;
    }
    .section-title { color: #1b5e20; font-size: 1.02rem; font-weight: 700; margin: 0 0 12px; }
    .form-row { display: flex; gap: 12px; margin-bottom: 8px; }
    .flex1 { flex: 1; }
    :host ::ng-deep .mat-mdc-form-field { font-size: 13px; }
    :host ::ng-deep .mat-mdc-form-field-subscript-wrapper { display: none; }
    :host ::ng-deep input.mat-mdc-input-element { text-align: center; }
    .history-box { border:1px solid #deebdf; border-radius:10px; padding:10px; background:#ffffff; }
    .history-item {
      display:grid; grid-template-columns: 1fr auto auto; gap:8px; align-items:center;
      padding:6px 0; font-size:12px; border-bottom:1px dashed #dbe8de;
    }
    .history-item:last-child { border-bottom:none; }
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
  forfaits = signal<DropdownItem[]>([]);
  history = signal<any[]>([]);
  selectedPecId = signal<string | null>(null);

  form!: FormGroup;

  ngOnInit(): void {
    this.form = this.fb.group({
      pecDateDebutDemande: [null, Validators.required],
      pecDateFinDemande: [null, Validators.required],
      pecForfaitDemandeId: [null]
    });
    this.form.valueChanges.subscribe(val => {
      const d1 = val.pecDateDebutDemande;
      const d2 = val.pecDateFinDemande;
      const isEmpty = !d1 && !d2;
      const isComplete = !!d1 && !!d2;
      this.dataChange.emit({ ...this.form.getRawValue(), pecId: this.selectedPecId() });
      this.validChange.emit(isEmpty || (isComplete && this.form.valid));
    });

    const cid = this.store.currentCenterId();
    if (!cid) return;
    this.refApi.getForfaits(cid).subscribe(list =>
      this.forfaits.set(list.map((i: any) => ({ ...i, id: i.id, label: `${i.code ?? ''} - ${i.nom}` })))
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
    const v = this.form.getRawValue();
    const isEmpty = !v.pecDateDebutDemande && !v.pecDateFinDemande;
    this.dataChange.emit({ ...v, pecId: this.selectedPecId() });
    this.validChange.emit(isEmpty || this.form.valid);
    this.applyReadonly();
  }

  edit(h: any): void {
    this.selectedPecId.set((h.id ?? h.ID ?? '').toString());
    this.form.patchValue({
      pecDateDebutDemande: h.dateDebutDemande ?? h.DATE_DEBUT_DEMANDE ?? null,
      pecDateFinDemande: h.dateFinDemande ?? h.DATE_FIN_DEMANDE ?? null,
      pecForfaitDemandeId: h.forfaitDemandeId ?? h.FORFAIT_DEMANDE_ID ?? null
    });
  }

  prepareNew(): void {
    this.selectedPecId.set(null);
    this.form.patchValue({ pecDateDebutDemande: null, pecDateFinDemande: null, pecForfaitDemandeId: null });
  }
}
