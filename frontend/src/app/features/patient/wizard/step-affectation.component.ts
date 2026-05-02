import {Component, EventEmitter, inject, Input, OnChanges, OnInit, Output, signal, SimpleChanges} from '@angular/core';
import {FormBuilder, FormGroup, ReactiveFormsModule} from '@angular/forms';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatSelectModule} from '@angular/material/select';
import {MatIconModule} from '@angular/material/icon';
import {MatCheckboxModule} from '@angular/material/checkbox';
import {TranslateModule} from '@ngx-translate/core';
import {DropdownItem, SearchableSelectComponent} from '../../../shared/searchable-select.component';
import {ReferentialApiService} from '../../../core/api/referential-api.service';
import {AppShellStore} from '../../../core/state/app-shell.store';

@Component({
  selector: 'app-step-affectation',
  standalone: true,
  imports: [ReactiveFormsModule, MatFormFieldModule, MatSelectModule, MatIconModule, MatCheckboxModule, TranslateModule, SearchableSelectComponent],
  template: `
    <div class="step-content">
      <h3 class="section-title">{{ 'PATIENT_FORM.SECTION_AFFECTATION' | translate }}</h3>
      <form [formGroup]="form">
        <div class="form-row">
          <app-searchable-select [items]="salles()" [label]="'PATIENT_FORM.SALLE' | translate" [prefixIcon]="'meeting_room'"
            [disabled]="readonly"
            [selectedId]="form.get('salleId')?.value" (selectionChanged)="form.patchValue({salleId: $event?.id})" cssClass="flex1" />
          <app-searchable-select [items]="medecins()" [label]="'PATIENT_FORM.MEDECIN_TRAITANT' | translate" [prefixIcon]="'medical_services'"
            [disabled]="readonly"
            [selectedId]="form.get('medecinTraitantId')?.value" (selectionChanged)="form.patchValue({medecinTraitantId: $event?.id})" cssClass="flex1" />
        </div>

        <div class="form-row">
          <app-searchable-select [items]="positions()" [label]="'PATIENT_FORM.POSITION' | translate" [prefixIcon]="'schedule'"
            [disabled]="readonly"
            [selectedId]="form.get('positionId')?.value" (selectionChanged)="form.patchValue({positionId: $event?.id})" cssClass="flex1" />
        </div>

        <div class="form-row">
          <app-searchable-select [items]="transporteurs()" [label]="'PATIENT_FORM.TRANSPORTEUR_ALLER' | translate" [prefixIcon]="'directions_car'"
            [disabled]="readonly"
            [selectedId]="form.get('transporteurAllerId')?.value" (selectionChanged)="form.patchValue({transporteurAllerId: $event?.id})" cssClass="flex1" />
          <app-searchable-select [items]="transporteurs()" [label]="'PATIENT_FORM.TRANSPORTEUR_RETOUR' | translate" [prefixIcon]="'local_taxi'"
            [disabled]="readonly"
            [selectedId]="form.get('transporteurRetourId')?.value" (selectionChanged)="form.patchValue({transporteurRetourId: $event?.id})" cssClass="flex1" />
          <app-searchable-select [items]="categoriesTransport()" [label]="'PATIENT_FORM.CATEGORIE_TRANSPORT' | translate" [prefixIcon]="'commute'"
            [disabled]="readonly"
            [selectedId]="form.get('categorieTransportId')?.value" (selectionChanged)="form.patchValue({categorieTransportId: $event?.id})" cssClass="flex1" />
        </div>

        <h4 class="dialyse-title">{{ 'PATIENT_FORM.JOURS_DIALYSE' | translate }} *</h4>
        <div class="jours-row">
          <mat-checkbox formControlName="jourDimanche" [disabled]="readonly">{{ 'PATIENT_FORM.DIMANCHE' | translate }}</mat-checkbox>
          <mat-checkbox formControlName="jourLundi" [disabled]="readonly">{{ 'PATIENT_FORM.LUNDI' | translate }}</mat-checkbox>
          <mat-checkbox formControlName="jourMardi" [disabled]="readonly">{{ 'PATIENT_FORM.MARDI' | translate }}</mat-checkbox>
          <mat-checkbox formControlName="jourMercredi" [disabled]="readonly">{{ 'PATIENT_FORM.MERCREDI' | translate }}</mat-checkbox>
          <mat-checkbox formControlName="jourJeudi" [disabled]="readonly">{{ 'PATIENT_FORM.JEUDI' | translate }}</mat-checkbox>
          <mat-checkbox formControlName="jourVendredi" [disabled]="readonly">{{ 'PATIENT_FORM.VENDREDI' | translate }}</mat-checkbox>
          <mat-checkbox formControlName="jourSamedi" [disabled]="readonly">{{ 'PATIENT_FORM.SAMEDI' | translate }}</mat-checkbox>
        </div>
      </form>
    </div>
  `,
  styles: [`
    .step-content {   padding: 12px 20px 20px; }

    .section-title {
      color: var(--app-text);
      font-size: 1rem;
      font-weight: 600;
      margin: 0 0 12px;
    }
    .form-row { display: flex; gap: 12px; margin-bottom: 8px; }
    .flex1 { flex: 1; }

    .dialyse-title {
      margin: 16px 0 8px;
      color: var(--app-text);
    }
    .jours-row {
      display: flex;
      flex-wrap: wrap;
      gap: 16px;
      padding: 14px;
      background: var(--app-primary-soft);
      border-radius: 10px;
      border: 1px solid var(--app-primary-outline);
    }
    :host ::ng-deep .mat-mdc-form-field { font-size: 13px; }
    :host ::ng-deep .mat-mdc-form-field-subscript-wrapper { display: none; }
    :host ::ng-deep .mat-mdc-select-value { text-align: center; }
  `]
})
export class StepAffectationComponent implements OnInit, OnChanges {
  @Input() readonly = false;
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

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['readonly']) this.applyReadonly();
  }

  markTouched(): void { this.form.markAllAsTouched(); }
  isValid(): boolean { return true; }

  patchData(data: Record<string, any>): void {
    if (!this.form) return;
    const jours = data['joursDialyse'] ?? data['jours_dialyse'] ?? {};
    const asBool = (v: any) => !!v;
    const pick = (camel: string, snake: string, shortKey: string) =>
      data[camel] ?? data[snake] ?? jours[camel] ?? jours[snake] ?? jours[shortKey] ?? false;

    const pickId = (camel: string, snake: string, nested: string) => {
      const direct = data[camel] ?? data[snake];
      if (direct) return String(direct);
      const obj = data[nested];
      if (obj && (obj.id ?? obj.ID)) return String(obj.id ?? obj.ID);
      return null;
    };

    this.form.patchValue({
      salleId: pickId('salleId', 'salle_id', 'salle'),
      medecinTraitantId: pickId('medecinTraitantId', 'medecin_traitant_id', 'medecinTraitant'),
      positionId: pickId('positionId', 'position_id', 'position'),
      transporteurAllerId: pickId('transporteurAllerId', 'transporteur_aller_id', 'transporteurAller'),
      transporteurRetourId: pickId('transporteurRetourId', 'transporteur_retour_id', 'transporteurRetour'),
      categorieTransportId: pickId('categorieTransportId', 'categorie_transport_id', 'categorieTransport'),
      jourDimanche: asBool(pick('jourDimanche', 'jour_dimanche', 'dimanche')),
      jourLundi: asBool(pick('jourLundi', 'jour_lundi', 'lundi')),
      jourMardi: asBool(pick('jourMardi', 'jour_mardi', 'mardi')),
      jourMercredi: asBool(pick('jourMercredi', 'jour_mercredi', 'mercredi')),
      jourJeudi: asBool(pick('jourJeudi', 'jour_jeudi', 'jeudi')),
      jourVendredi: asBool(pick('jourVendredi', 'jour_vendredi', 'vendredi')),
      jourSamedi: asBool(pick('jourSamedi', 'jour_samedi', 'samedi'))
    }, { emitEvent: false });
    this.dataChange.emit(this.form.getRawValue());
    this.validChange.emit(true);
  }

  private applyReadonly(): void {
    if (!this.form) return;
    this.readonly ? this.form.disable({ emitEvent: false }) : this.form.enable({ emitEvent: false });
  }
}
