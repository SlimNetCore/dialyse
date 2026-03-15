import { Component, OnInit, Output, EventEmitter, inject, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, FormGroup } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatDividerModule } from '@angular/material/divider';
import { TranslateModule } from '@ngx-translate/core';
import { SearchableSelectComponent, DropdownItem } from '../../../shared/searchable-select.component';
import { ReferentialApiService } from '../../../core/api/referential-api.service';
import { AppShellStore } from '../../../core/state/app-shell.store';

@Component({
  selector: 'app-step-pec',
  standalone: true,
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatDatepickerModule, MatNativeDateModule, MatDividerModule, TranslateModule, SearchableSelectComponent],
  template: `
    <div class="step-content">
      <!-- Demande -->
      <h3 class="section-title">{{ 'WIZARD.PEC_DEMANDE' | translate }}</h3>
      <form [formGroup]="form">
        <div class="form-row">
          <mat-form-field appearance="outline" class="flex1">
            <mat-label>{{ 'WIZARD.PEC_DATE_DEBUT' | translate }}</mat-label>
            <input matInput [matDatepicker]="dpDeb" formControlName="pecDateDebutDemande" />
            <mat-datepicker-toggle matSuffix [for]="dpDeb" /><mat-datepicker #dpDeb />
          </mat-form-field>
          <mat-form-field appearance="outline" class="flex1">
            <mat-label>{{ 'WIZARD.PEC_DATE_FIN' | translate }}</mat-label>
            <input matInput [matDatepicker]="dpFin" formControlName="pecDateFinDemande" />
            <mat-datepicker-toggle matSuffix [for]="dpFin" /><mat-datepicker #dpFin />
          </mat-form-field>
          <app-searchable-select [items]="forfaits()" [label]="'WIZARD.PEC_FORFAIT_DEMANDE' | translate"
            [selectedId]="form.get('pecForfaitDemandeId')?.value" (selectionChanged)="form.patchValue({pecForfaitDemandeId: $event?.id})" cssClass="flex1" />
        </div>
      </form>

      <mat-divider style="margin: 20px 0;" />

      <!-- Accord (informational — filled by admin later) -->
      <h3 class="section-title">{{ 'WIZARD.PEC_ACCORD' | translate }}</h3>
      <p style="color: #888; font-style: italic;">{{ 'WIZARD.PEC_ACCORD_DESC' | translate }}</p>
    </div>
  `,
  styles: [`
    .step-content { padding: 16px 0; }
    .section-title { color: #1b5e20; font-size: 1rem; font-weight: 600; margin-bottom: 12px; }
    .form-row { display: flex; gap: 12px; margin-bottom: 8px; }
    .flex1 { flex: 1; }
    :host ::ng-deep .mat-mdc-form-field { font-size: 13px; }
    :host ::ng-deep .mat-mdc-form-field-subscript-wrapper { display: none; }
    :host ::ng-deep input.mat-mdc-input-element { text-align: center; }
  `]
})
export class StepPecComponent implements OnInit {
  @Output() dataChange = new EventEmitter<Record<string, any>>();
  @Output() validChange = new EventEmitter<boolean>();

  private readonly fb = inject(FormBuilder);
  private readonly refApi = inject(ReferentialApiService);
  private readonly store = inject(AppShellStore);
  forfaits = signal<DropdownItem[]>([]);

  form!: FormGroup;

  ngOnInit(): void {
    this.form = this.fb.group({
      pecDateDebutDemande: [null], pecDateFinDemande: [null], pecForfaitDemandeId: [null]
    });
    this.form.valueChanges.subscribe(val => {
      this.dataChange.emit(val);
      this.validChange.emit(true);
    });

    const cid = this.store.currentCenterId();
    if (!cid) return;
    this.refApi.getForfaits(cid).subscribe(list =>
      this.forfaits.set(list.map((i: any) => ({ ...i, id: i.id, label: `${i.code ?? ''} - ${i.nom}` })))
    );
  }

  markTouched(): void { this.form.markAllAsTouched(); }
  isValid(): boolean { return true; }
}
