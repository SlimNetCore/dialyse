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
import {FormBuilder, FormGroup, ReactiveFormsModule} from '@angular/forms';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatIconModule} from '@angular/material/icon';
import {MatCheckboxModule} from '@angular/material/checkbox';
import {TranslateModule} from '@ngx-translate/core';
import {DropdownItem, SearchableSelectComponent,} from '../../../shared/searchable-select.component';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {
  CategoriesTransportStore,
  MedecinsStore,
  PositionsStore,
  SallesStore,
  TransporteursStore,
} from '../../../core/state/referentials.store';

@Component({
  selector: 'app-step-affectation',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatFormFieldModule,
    MatIconModule,
    MatCheckboxModule,
    TranslateModule,
    SearchableSelectComponent,
  ],
  template: `
    <div class="step-content">
      <h3 class="section-title">{{ 'PATIENT_FORM.SECTION_AFFECTATION' | translate }}</h3>
      <form [formGroup]="form">
        <div class="form-row">
          <app-searchable-select
            [items]="salles()"
            [label]="'PATIENT_FORM.SALLE' | translate"
            [prefixIcon]="'meeting_room'"
            [autofocusFirst]="true"
            [disabled]="readonly"
            [selectedId]="salleIdSignal()"
            (selectionChanged)="form.patchValue({ salleId: $event?.id ?? null })"
            cssClass="flex1"
          />
          <app-searchable-select
            [items]="medecins()"
            [label]="'PATIENT_FORM.MEDECIN_TRAITANT' | translate"
            [prefixIcon]="'medical_services'"
            [disabled]="readonly"
            [selectedId]="medecinTraitantIdSignal()"
            (selectionChanged)="
              form.patchValue({ medecinTraitantId: $event?.id ?? null })
            "
            cssClass="flex1"
          />
        </div>

        <div class="form-row">
          <app-searchable-select
            [items]="positions()"
            [label]="'PATIENT_FORM.POSITION' | translate"
            [prefixIcon]="'schedule'"
            [disabled]="readonly"
            [selectedId]="positionIdSignal()"
            (selectionChanged)="
              form.patchValue({ positionId: $event?.id ?? null })
            "
            cssClass="flex1"
          />
        </div>

        <div class="form-row">
          <app-searchable-select
            [items]="transporteurs()"
            [label]="'PATIENT_FORM.TRANSPORTEUR_ALLER' | translate"
            [prefixIcon]="'directions_car'"
            [disabled]="readonly"
            [selectedId]="transporteurAllerIdSignal()"
            (selectionChanged)="
              form.patchValue({ transporteurAllerId: $event?.id ?? null })
            "
            cssClass="flex1"
          />
          <app-searchable-select
            [items]="transporteurs()"
            [label]="'PATIENT_FORM.TRANSPORTEUR_RETOUR' | translate"
            [prefixIcon]="'local_taxi'"
            [disabled]="readonly"
            [selectedId]="transporteurRetourIdSignal()"
            (selectionChanged)="
              form.patchValue({ transporteurRetourId: $event?.id ?? null })
            "
            cssClass="flex1"
          />
          <app-searchable-select
            [items]="categoriesTransport()"
            [label]="'PATIENT_FORM.CATEGORIE_TRANSPORT' | translate"
            [prefixIcon]="'commute'"
            [disabled]="readonly"
            [selectedId]="categorieTransportIdSignal()"
            (selectionChanged)="
              form.patchValue({ categorieTransportId: $event?.id ?? null })
            "
            cssClass="flex1"
          />
        </div>

        <h4 class="dialyse-title">{{ 'PATIENT_FORM.JOURS_DIALYSE' | translate }} *</h4>
        <div class="jours-row">
          <mat-checkbox formControlName="jourDimanche" [disabled]="readonly">{{
              'PATIENT_FORM.DIMANCHE' | translate
            }}
          </mat-checkbox>
          <mat-checkbox formControlName="jourLundi" [disabled]="readonly">{{
              'PATIENT_FORM.LUNDI' | translate
            }}
          </mat-checkbox>
          <mat-checkbox formControlName="jourMardi" [disabled]="readonly">{{
              'PATIENT_FORM.MARDI' | translate
            }}
          </mat-checkbox>
          <mat-checkbox formControlName="jourMercredi" [disabled]="readonly">{{
              'PATIENT_FORM.MERCREDI' | translate
            }}
          </mat-checkbox>
          <mat-checkbox formControlName="jourJeudi" [disabled]="readonly">{{
              'PATIENT_FORM.JEUDI' | translate
            }}
          </mat-checkbox>
          <mat-checkbox formControlName="jourVendredi" [disabled]="readonly">{{
              'PATIENT_FORM.VENDREDI' | translate
            }}
          </mat-checkbox>
          <mat-checkbox formControlName="jourSamedi" [disabled]="readonly">{{
              'PATIENT_FORM.SAMEDI' | translate
            }}
          </mat-checkbox>
        </div>
      </form>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.Eager,
  styles: [
    `
      .step-content {
        padding: 14px 18px 18px;
      }

      .section-title {
        color: var(--app-text);
        font-size: 1rem;
        font-weight: 600;
        margin: 0 0 12px;
      }

      .form-row {
        display: flex;
        gap: 12px;
        margin-bottom: 8px;
        align-items: flex-start;
      }

      .flex1 {
        flex: 1;
      }

      .form-row app-searchable-select {
        flex: 1;
        min-width: 0;
      }

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

      :host ::ng-deep .mat-mdc-form-field {
        font-size: 13px;
      }

      :host ::ng-deep .mat-mdc-form-field-subscript-wrapper {
        display: none;
      }

      :host ::ng-deep input.mat-mdc-input-element {
        text-align: center;
      }

      :host ::ng-deep .mat-mdc-select-value {
        text-align: center;
      }

      :host ::ng-deep app-searchable-select .mat-mdc-form-field {
        width: 100%;
      }

      @media (max-width: 900px) {
        .step-content {
          padding: 12px;
        }
        .form-row {
          flex-wrap: wrap;
          gap: 10px;
        }
        .flex1,
        .form-row app-searchable-select {
          flex: 1 1 100%;
          width: 100%;
        }
        .jours-row {
          gap: 10px;
          padding: 12px;
        }
        .jours-row mat-checkbox {
          flex: 1 1 calc(50% - 10px);
          min-width: 140px;
        }
        :host ::ng-deep .jours-row .mdc-form-field {
          width: 100%;
        }
      }

      @media (max-width: 600px) {
        .step-content {
          padding: 10px;
        }
        .section-title {
          margin-bottom: 10px;
        }
        .dialyse-title {
          margin: 14px 0 8px;
          font-size: 0.95rem;
        }
        .jours-row {
          gap: 8px;
          padding: 10px;
        }
        .jours-row mat-checkbox {
          flex: 1 1 100%;
          min-width: 0;
          min-height: 40px;
          padding: 6px 8px;
          border-radius: 8px;
          border: 1px solid var(--app-border);
          background: var(--app-surface);
        }
      }
    `,
  ],
})
export class StepAffectationComponent implements OnInit, OnChanges {
  @Input() readonly = false;
  @Output() dataChange = new EventEmitter<Record<string, any>>();
  @Output() validChange = new EventEmitter<boolean>();

  form!: FormGroup;
  private readonly appShell = inject(AppShellStore);
  private readonly sallesStore = inject(SallesStore);
  readonly salles = this.sallesStore.items as unknown as () => DropdownItem[];
  private readonly medecinsStore = inject(MedecinsStore);
  readonly medecins = this.medecinsStore.items as unknown as () => DropdownItem[];
  private readonly positionsStore = inject(PositionsStore);
  readonly positions = this.positionsStore.items as unknown as () => DropdownItem[];
  private readonly transporteursStore = inject(TransporteursStore);
  readonly transporteurs = this.transporteursStore.items as unknown as () => DropdownItem[];
  private readonly categoriesTransportStore = inject(CategoriesTransportStore);
  readonly categoriesTransport = this.categoriesTransportStore
    .items as unknown as () => DropdownItem[];
  private readonly fb = inject(FormBuilder);

  // Signals pour les SearchableSelectComponent — nécessaire en mode zoneless (form.get()?.value n'est pas réactif)
  readonly salleIdSignal = signal<string | null>(null);
  readonly medecinTraitantIdSignal = signal<string | null>(null);
  readonly positionIdSignal = signal<string | null>(null);
  readonly transporteurAllerIdSignal = signal<string | null>(null);
  readonly transporteurRetourIdSignal = signal<string | null>(null);
  readonly categorieTransportIdSignal = signal<string | null>(null);

  constructor() {
    this.form = this.fb.group({
      salleId: [null],
      medecinTraitantId: [null],
      positionId: [null],
      transporteurAllerId: [null],
      transporteurRetourId: [null],
      categorieTransportId: [null],
      jourDimanche: [false],
      jourLundi: [false],
      jourMardi: [false],
      jourMercredi: [false],
      jourJeudi: [false],
      jourVendredi: [false],
      jourSamedi: [false],
    });

    this.form.valueChanges.subscribe((val) => {
      // Mettre à jour les signals pour forcer la réévaluation des SearchableSelectComponent en mode zoneless
      this.salleIdSignal.set(val.salleId ?? null);
      this.medecinTraitantIdSignal.set(val.medecinTraitantId ?? null);
      this.positionIdSignal.set(val.positionId ?? null);
      this.transporteurAllerIdSignal.set(val.transporteurAllerId ?? null);
      this.transporteurRetourIdSignal.set(val.transporteurRetourId ?? null);
      this.categorieTransportIdSignal.set(val.categorieTransportId ?? null);
      this.dataChange.emit(val);
      this.validChange.emit(true); // affectation step is optional
    });
  }

  ngOnInit(): void {
    const cid = this.appShell.currentCenterId();
    if (!cid) return;
    void this.sallesStore.ensureLoaded(cid);
    void this.medecinsStore.ensureLoaded(cid);
    void this.positionsStore.ensureLoaded(cid);
    void this.transporteursStore.ensureLoaded(cid);
    void this.categoriesTransportStore.ensureLoaded(cid);
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['readonly']) this.applyReadonly();
  }

  markTouched(): void {
    this.form.markAllAsTouched();
  }

  isValid(): boolean {
    return true;
  }

  patchData(data: Record<string, any>): void {
    if (!this.form) return;
    const jours = data['joursDialyse'] ?? data['jours_dialyse'] ?? {};
    const asBool = (v: any) => !!v;
    const pick = (camel: string, snake: string, shortKey: string) =>
      data[camel] ?? data[snake] ?? jours[camel] ?? jours[snake] ?? jours[shortKey] ?? false;

    const normalizeId = (value: any): string | null => {
      if (value === null || value === undefined || value === '') return null;
      if (typeof value === 'string' || typeof value === 'number') return String(value);
      if (typeof value === 'object') {
        const nested = value['value'] ?? value['id'] ?? value['ID'];
        if (nested !== undefined && nested !== null && nested !== '') return String(nested);
      }
      return String(value);
    };

    const pickId = (camel: string, snake: string, nested: string) => {
      const direct = data[camel] ?? data[snake];
      const normalizedDirect = normalizeId(direct);
      if (normalizedDirect) return normalizedDirect;
      const obj = data[nested];
      const normalizedObj = normalizeId(obj);
      if (normalizedObj) return normalizedObj;
      return null;
    };

    const salleId = pickId('salleId', 'salle_id', 'salle');
    const medecinTraitantId = pickId('medecinTraitantId', 'medecin_traitant_id', 'medecinTraitant');
    const positionId = pickId('positionId', 'position_id', 'position');
    const transporteurAllerId = pickId('transporteurAllerId', 'transporteur_aller_id', 'transporteurAller');
    const transporteurRetourId = pickId('transporteurRetourId', 'transporteur_retour_id', 'transporteurRetour');
    const categorieTransportId = pickId('categorieTransportId', 'categorie_transport_id', 'categorieTransport');

    this.form.patchValue(
      {
        salleId,
        medecinTraitantId,
        positionId,
        transporteurAllerId,
        transporteurRetourId,
        categorieTransportId,
        jourDimanche: asBool(pick('jourDimanche', 'jour_dimanche', 'dimanche')),
        jourLundi: asBool(pick('jourLundi', 'jour_lundi', 'lundi')),
        jourMardi: asBool(pick('jourMardi', 'jour_mardi', 'mardi')),
        jourMercredi: asBool(pick('jourMercredi', 'jour_mercredi', 'mercredi')),
        jourJeudi: asBool(pick('jourJeudi', 'jour_jeudi', 'jeudi')),
        jourVendredi: asBool(pick('jourVendredi', 'jour_vendredi', 'vendredi')),
        jourSamedi: asBool(pick('jourSamedi', 'jour_samedi', 'samedi')),
      },
      {emitEvent: false},
    );
    // Mettre à jour les signals pour forcer la réévaluation des SearchableSelectComponent en mode zoneless
    this.salleIdSignal.set(salleId);
    this.medecinTraitantIdSignal.set(medecinTraitantId);
    this.positionIdSignal.set(positionId);
    this.transporteurAllerIdSignal.set(transporteurAllerId);
    this.transporteurRetourIdSignal.set(transporteurRetourId);
    this.categorieTransportIdSignal.set(categorieTransportId);
    this.validChange.emit(true);
    this.applyReadonly();
  }

  private applyReadonly(): void {
    if (!this.form) return;
    this.readonly
      ? this.form.disable({emitEvent: false})
      : this.form.enable({emitEvent: false});
  }
}
