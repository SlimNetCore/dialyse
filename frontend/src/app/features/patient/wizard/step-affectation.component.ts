import { Component, OnInit, Output, EventEmitter, inject, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, FormGroup } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { TranslateModule } from '@ngx-translate/core';
import { SearchableSelectComponent, DropdownItem } from '../../../shared/searchable-select.component';
import { ReferentialApiService } from '../../../core/api/referential-api.service';
import { AppShellStore } from '../../../core/state/app-shell.store';

@Component({
  selector: 'app-step-affectation',
  standalone: true,
  imports: [ReactiveFormsModule, MatFormFieldModule, MatSelectModule, MatCheckboxModule, TranslateModule, SearchableSelectComponent],
  template: `
    <div class="step-content">
      <h3 class="section-title">{{ 'PATIENT_FORM.SECTION_AFFECTATION' | translate }}</h3>
      <form [formGroup]="form">
        <div class="form-row">
          <app-searchable-select [items]="salles()" [label]="'PATIENT_FORM.SALLE' | translate"
            [selectedId]="form.get('salleId')?.value" (selectionChanged)="form.patchValue({salleId: $event?.id})" cssClass="flex1" />
          <app-searchable-select [items]="medecins()" [label]="'PATIENT_FORM.MEDECIN_TRAITANT' | translate"
            [selectedId]="form.get('medecinTraitantId')?.value" (selectionChanged)="form.patchValue({medecinTraitantId: $event?.id})" cssClass="flex1" />
        </div>

        <div class="form-row">
          <app-searchable-select [items]="positions()" [label]="'PATIENT_FORM.POSITION' | translate"
            [selectedId]="form.get('positionId')?.value" (selectionChanged)="form.patchValue({positionId: $event?.id})" cssClass="flex1" />
          <mat-form-field appearance="outline" class="flex1">
            <mat-label>{{ 'PATIENT_FORM.ETAT_PATIENT' | translate }}</mat-label>
            <mat-select formControlName="etatPatient">
              <mat-option value="PERMANENT">{{ 'PATIENT_FORM.PERMANENT' | translate }}</mat-option>
              <mat-option value="OCCASIONNEL">{{ 'PATIENT_FORM.OCCASIONNEL' | translate }}</mat-option>
              <mat-option value="TRANSFERE">{{ 'PATIENT_FORM.TRANSFERE' | translate }}</mat-option>
              <mat-option value="DECEDE">{{ 'PATIENT_FORM.DECEDE' | translate }}</mat-option>
              <mat-option value="GREFFE">{{ 'PATIENT_FORM.GREFFE' | translate }}</mat-option>
              <mat-option value="GUERRI">{{ 'PATIENT_FORM.GUERRI' | translate }}</mat-option>
              <mat-option value="VACANCIER_LOCAL">{{ 'PATIENT_FORM.VACANCIER_LOCAL' | translate }}</mat-option>
              <mat-option value="VACANCIER_ETRANGER">{{ 'PATIENT_FORM.VACANCIER_ETRANGER' | translate }}</mat-option>
            </mat-select>
          </mat-form-field>
        </div>

        <div class="form-row">
          <app-searchable-select [items]="transporteurs()" [label]="'PATIENT_FORM.TRANSPORTEUR_ALLER' | translate"
            [selectedId]="form.get('transporteurAllerId')?.value" (selectionChanged)="form.patchValue({transporteurAllerId: $event?.id})" cssClass="flex1" />
          <app-searchable-select [items]="transporteurs()" [label]="'PATIENT_FORM.TRANSPORTEUR_RETOUR' | translate"
            [selectedId]="form.get('transporteurRetourId')?.value" (selectionChanged)="form.patchValue({transporteurRetourId: $event?.id})" cssClass="flex1" />
          <app-searchable-select [items]="categoriesTransport()" [label]="'PATIENT_FORM.CATEGORIE_TRANSPORT' | translate"
            [selectedId]="form.get('categorieTransportId')?.value" (selectionChanged)="form.patchValue({categorieTransportId: $event?.id})" cssClass="flex1" />
        </div>

        <h4 style="margin: 16px 0 8px; color: #37474f;">{{ 'PATIENT_FORM.JOURS_DIALYSE' | translate }} *</h4>
        <div class="jours-row">
          <mat-checkbox formControlName="jourDimanche">{{ 'PATIENT_FORM.DIMANCHE' | translate }}</mat-checkbox>
          <mat-checkbox formControlName="jourLundi">{{ 'PATIENT_FORM.LUNDI' | translate }}</mat-checkbox>
          <mat-checkbox formControlName="jourMardi">{{ 'PATIENT_FORM.MARDI' | translate }}</mat-checkbox>
          <mat-checkbox formControlName="jourMercredi">{{ 'PATIENT_FORM.MERCREDI' | translate }}</mat-checkbox>
          <mat-checkbox formControlName="jourJeudi">{{ 'PATIENT_FORM.JEUDI' | translate }}</mat-checkbox>
          <mat-checkbox formControlName="jourVendredi">{{ 'PATIENT_FORM.VENDREDI' | translate }}</mat-checkbox>
          <mat-checkbox formControlName="jourSamedi">{{ 'PATIENT_FORM.SAMEDI' | translate }}</mat-checkbox>
        </div>
      </form>
    </div>
  `,
  styles: [`
    .step-content { padding: 16px 0; }
    .section-title { color: #1b5e20; font-size: 1rem; font-weight: 600; margin-bottom: 12px; }
    .form-row { display: flex; gap: 12px; margin-bottom: 8px; }
    .flex1 { flex: 1; }
    .jours-row {
      display: flex; flex-wrap: wrap; gap: 16px; padding: 14px; background: #f0fdf4;
      border-radius: 10px; border: 1px solid #c8e6c9;
    }
    :host ::ng-deep .mat-mdc-form-field { font-size: 13px; }
    :host ::ng-deep .mat-mdc-form-field-subscript-wrapper { display: none; }
    :host ::ng-deep .mat-mdc-select-value { text-align: center; }
  `]
})
export class StepAffectationComponent implements OnInit {
  @Output() dataChange = new EventEmitter<Record<string, any>>();
  @Output() validChange = new EventEmitter<boolean>();

  private readonly fb = inject(FormBuilder);
  private readonly refApi = inject(ReferentialApiService);
  private readonly store = inject(AppShellStore);

  salles = signal<DropdownItem[]>([]);
  medecins = signal<DropdownItem[]>([]);
  positions = signal<DropdownItem[]>([]);
  transporteurs = signal<DropdownItem[]>([]);
  categoriesTransport = signal<DropdownItem[]>([]);

  form!: FormGroup;

  ngOnInit(): void {
    this.form = this.fb.group({
      salleId: [null], medecinTraitantId: [null], positionId: [null],
      etatPatient: ['PERMANENT'],
      transporteurAllerId: [null], transporteurRetourId: [null], categorieTransportId: [null],
      jourDimanche: [false], jourLundi: [false], jourMardi: [false],
      jourMercredi: [false], jourJeudi: [false], jourVendredi: [false], jourSamedi: [false]
    });

    this.form.valueChanges.subscribe(val => {
      this.dataChange.emit(val);
      this.validChange.emit(true); // affectation step is optional
    });

    const cid = this.store.currentCenterId();
    if (!cid) return;
    this.refApi.getSalles(cid).subscribe(list => this.salles.set(list.map((i: any) => ({ ...i, id: i.id, label: `${i.code ?? ''} - ${i.nom}` }))));
    this.refApi.getMedecins(cid).subscribe(list => this.medecins.set(list.map((i: any) => ({ ...i, id: i.id, label: `${i.nom} ${i.prenom ?? ''}`.trim() }))));
    this.refApi.getPositions(cid).subscribe(list => this.positions.set(list.map((i: any) => ({ ...i, id: i.id, label: `${i.code ?? ''} - ${i.libelle ?? i.nom ?? ''}` }))));
    this.refApi.getTransporteurs(cid).subscribe(list => this.transporteurs.set(list.map((i: any) => ({ ...i, id: i.id, label: i.nom }))));
    this.refApi.getCategoriesTransport(cid).subscribe(list => this.categoriesTransport.set(list.map((i: any) => ({ ...i, id: i.id, label: i.libelle ?? i.nom }))));
  }

  markTouched(): void { this.form.markAllAsTouched(); }
  isValid(): boolean { return true; }
}
