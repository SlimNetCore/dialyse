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
  templateUrl: './step-affectation.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './step-affectation.component.css',
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
