import { Component, OnInit, Output, EventEmitter, inject, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatDividerModule } from '@angular/material/divider';
import { TranslateModule } from '@ngx-translate/core';
import { SearchableSelectComponent, DropdownItem } from '../../../shared/searchable-select.component';
import { ReferentialApiService } from '../../../core/api/referential-api.service';
import { AppShellStore } from '../../../core/state/app-shell.store';

@Component({
  selector: 'app-step-assurance',
  standalone: true,
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatDatepickerModule, MatNativeDateModule, MatDividerModule, TranslateModule, SearchableSelectComponent],
  template: `
    <div class="step-content">
      <h3 class="section-title">{{ 'PATIENT_FORM.SECTION_INSURANCE' | translate }}</h3>
      <form [formGroup]="form">
        <div class="form-row">
          <mat-form-field appearance="outline" class="flex1">
            <mat-label>{{ 'PATIENT_FORM.NUMERO_ASSURANCE' | translate }} *</mat-label>
            <input matInput formControlName="numeroAssurance" />
            @if (form.get('numeroAssurance')?.hasError('required') && form.get('numeroAssurance')?.touched) {
              <mat-error>{{ 'PATIENT_FORM.REQUIRED' | translate }}</mat-error>
            }
          </mat-form-field>

          <mat-form-field appearance="outline" class="flex1">
            <mat-label>{{ 'PATIENT_FORM.TYPE_PATIENT' | translate }}</mat-label>
            <mat-select formControlName="typePatient">
              <mat-option value="NON_VACANCIER">{{ 'PATIENT_FORM.NON_VACANCIER' | translate }}</mat-option>
              <mat-option value="VACANCIER">{{ 'PATIENT_FORM.VACANCIER' | translate }}</mat-option>
            </mat-select>
          </mat-form-field>
        </div>

        <div class="form-row">
          <app-searchable-select
            [items]="centresPayeurs()" [label]="'PATIENT_FORM.CENTRE_PAYEUR' | translate"
            [selectedId]="form.get('centrePayeurId')?.value" (selectionChanged)="onCentrePayeur($event)"
            cssClass="flex1" />
          <mat-form-field appearance="outline" class="flex1">
            <mat-label>{{ 'PATIENT_FORM.CODE_CENTRE_PAYEUR' | translate }}</mat-label>
            <input matInput [value]="codeCentrePayeur()" disabled />
          </mat-form-field>
        </div>

        <mat-divider style="margin: 16px 0;" />

        @if (showAssure()) {
          <h3 class="section-title">{{ 'PATIENT_FORM.SECTION_ASSURE' | translate }}</h3>
          <div class="form-row">
            <mat-form-field appearance="outline" class="flex1">
              <mat-label>{{ 'PATIENT_FORM.ASSURE_NOM' | translate }} *</mat-label>
              <input matInput formControlName="assureNom" />
              @if (form.get('assureNom')?.hasError('required') && form.get('assureNom')?.touched) {
                <mat-error>{{ 'PATIENT_FORM.REQUIRED' | translate }}</mat-error>
              }
            </mat-form-field>
            <mat-form-field appearance="outline" class="flex1">
              <mat-label>{{ 'PATIENT_FORM.ASSURE_PRENOM' | translate }} *</mat-label>
              <input matInput formControlName="assurePrenom" />
              @if (form.get('assurePrenom')?.hasError('required') && form.get('assurePrenom')?.touched) {
                <mat-error>{{ 'PATIENT_FORM.REQUIRED' | translate }}</mat-error>
              }
            </mat-form-field>
            <mat-form-field appearance="outline" class="flex1">
              <mat-label>{{ 'PATIENT_FORM.ASSURE_SEXE' | translate }}</mat-label>
              <mat-select formControlName="assureSexe">
                <mat-option value="M">{{ 'PATIENT_FORM.MASCULIN' | translate }}</mat-option>
                <mat-option value="F">{{ 'PATIENT_FORM.FEMININ' | translate }}</mat-option>
              </mat-select>
            </mat-form-field>
          </div>
          <div class="form-row">
            <mat-form-field appearance="outline" class="flex1">
              <mat-label>{{ 'PATIENT_FORM.ASSURE_DATE_NAISSANCE' | translate }}</mat-label>
              <input matInput [matDatepicker]="dpAssure" formControlName="assureDateNaissance" />
              <mat-datepicker-toggle matSuffix [for]="dpAssure" /><mat-datepicker #dpAssure />
            </mat-form-field>
            <mat-form-field appearance="outline" class="flex1">
              <mat-label>{{ 'PATIENT_FORM.ASSURE_TEL_PERSONNEL' | translate }}</mat-label>
              <input matInput formControlName="assureTelPersonnel" />
            </mat-form-field>
            <mat-form-field appearance="outline" class="flex1">
              <mat-label>{{ 'PATIENT_FORM.ASSURE_GROUPE_SANGUIN' | translate }}</mat-label>
              <mat-select formControlName="assureGroupeSanguin">
                <mat-option value="">—</mat-option>
                <mat-option value="A+">A+</mat-option><mat-option value="A-">A-</mat-option>
                <mat-option value="B+">B+</mat-option><mat-option value="B-">B-</mat-option>
                <mat-option value="O+">O+</mat-option><mat-option value="O-">O-</mat-option>
                <mat-option value="AB+">AB+</mat-option><mat-option value="AB-">AB-</mat-option>
              </mat-select>
            </mat-form-field>
          </div>
          <mat-form-field appearance="outline" class="full-width">
            <mat-label>{{ 'PATIENT_FORM.ASSURE_ADRESSE' | translate }}</mat-label>
            <input matInput formControlName="assureAdresse" />
          </mat-form-field>
        } @else {
          <p style="color:#888; font-style:italic;">{{ 'WIZARD.ASSURE_SAME_AS_PATIENT' | translate }}</p>
        }
      </form>
    </div>
  `,
  styles: [`
    .step-content { padding: 16px 0; }
    .section-title { color: #1b5e20; font-size: 1rem; font-weight: 600; margin-bottom: 12px; }
    .form-row { display: flex; gap: 12px; margin-bottom: 4px; }
    .flex1 { flex: 1; }
    .full-width { width: 100%; }
    :host ::ng-deep .mat-mdc-form-field { font-size: 13px; }
    :host ::ng-deep .mat-mdc-form-field-subscript-wrapper { display: none; }
    :host ::ng-deep .mat-mdc-form-field-infix { min-height: 40px !important; padding-top: 10px !important; padding-bottom: 6px !important; }
    :host ::ng-deep input.mat-mdc-input-element { text-align: center; }
    :host ::ng-deep .mat-mdc-select-value { text-align: center; }
  `]
})
export class StepAssuranceComponent implements OnInit {
  @Output() dataChange = new EventEmitter<Record<string, any>>();
  @Output() validChange = new EventEmitter<boolean>();

  private readonly fb = inject(FormBuilder);
  private readonly refApi = inject(ReferentialApiService);
  private readonly store = inject(AppShellStore);

  centresPayeurs = signal<DropdownItem[]>([]);
  codeCentrePayeur = signal('');
  showAssure = signal(false);

  form!: FormGroup;

  ngOnInit(): void {
    this.form = this.fb.group({
      numeroAssurance: ['', Validators.required],
      typePatient: ['NON_VACANCIER'],
      centrePayeurId: [null],
      assureNom: [''],
      assurePrenom: [''],
      assureSexe: [''],
      assureDateNaissance: [null],
      assureTelPersonnel: [''],
      assureGroupeSanguin: [''],
      assureAdresse: ['']
    });

    this.form.valueChanges.subscribe(val => {
      this.dataChange.emit(val);
      this.validChange.emit(this.form.valid);
    });

    const cid = this.store.currentCenterId();
    if (cid) {
      this.refApi.getCentresPayeurs(cid).subscribe(list =>
        this.centresPayeurs.set(list.map((i: any) => ({ ...i, id: i.id, label: `${i.nom} (${i.code ?? ''})` })))
      );
    }
  }

  /** Called externally with qualiteAssure from step 1 */
  setQualiteAssure(qa: string): void {
    const needsAssure = !!(qa && qa !== 'ASSURE_LUI_MEME');
    this.showAssure.set(needsAssure);
    if (needsAssure) {
      this.form.get('assureNom')?.setValidators(Validators.required);
      this.form.get('assurePrenom')?.setValidators(Validators.required);
    } else {
      this.form.get('assureNom')?.clearValidators();
      this.form.get('assurePrenom')?.clearValidators();
    }
    this.form.get('assureNom')?.updateValueAndValidity();
    this.form.get('assurePrenom')?.updateValueAndValidity();
  }

  onCentrePayeur(item: DropdownItem | null): void {
    this.form.patchValue({ centrePayeurId: item?.id ?? null });
    this.codeCentrePayeur.set(item?.['code'] ?? '');
  }

  markTouched(): void { this.form.markAllAsTouched(); }
  isValid(): boolean { return this.form.valid; }
}
