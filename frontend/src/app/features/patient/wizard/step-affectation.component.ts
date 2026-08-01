import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  inject,
  Input,
  OnChanges,
  OnInit,
  Output,
  SimpleChanges,
} from '@angular/core';
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
import {SignalForm} from '../../../shared/forms/signal-form';

interface AffectationModel {
  salleId: string | null;
  medecinTraitantId: string | null;
  positionId: string | null;
  transporteurAllerId: string | null;
  transporteurRetourId: string | null;
  categorieTransportId: string | null;
  jourDimanche: boolean;
  jourLundi: boolean;
  jourMardi: boolean;
  jourMercredi: boolean;
  jourJeudi: boolean;
  jourVendredi: boolean;
  jourSamedi: boolean;
}

type AffectationSelectKey =
  | 'salleId'
  | 'medecinTraitantId'
  | 'positionId'
  | 'transporteurAllerId'
  | 'transporteurRetourId'
  | 'categorieTransportId';

type AffectationDayKey =
  | 'jourDimanche'
  | 'jourLundi'
  | 'jourMardi'
  | 'jourMercredi'
  | 'jourJeudi'
  | 'jourVendredi'
  | 'jourSamedi';

@Component({
  selector: 'app-step-affectation',
  standalone: true,
  imports: [
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

  readonly form = new SignalForm<AffectationModel>({
    salleId: null,
    medecinTraitantId: null,
    positionId: null,
    transporteurAllerId: null,
    transporteurRetourId: null,
    categorieTransportId: null,
    jourDimanche: false,
    jourLundi: false,
    jourMardi: false,
    jourMercredi: false,
    jourJeudi: false,
    jourVendredi: false,
    jourSamedi: false,
  });

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

  onSelect(key: AffectationSelectKey, item: DropdownItem | null): void {
    if (this.readonly) return;
    this.form.set(key, item?.id ?? null);
    this.emit();
  }

  onDay(key: AffectationDayKey, checked: boolean): void {
    if (this.readonly) return;
    this.form.set(key, checked);
    this.emit();
  }

  markTouched(): void {
    this.form.markAllTouched();
  }

  isValid(): boolean {
    // L'étape affectation est facultative.
    return true;
  }

  patchData(data: Record<string, any>): void {
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

    this.form.patch({
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
      jourSamedi: asBool(pick('jourSamedi', 'jour_samedi', 'samedi')),
    });
    this.validChange.emit(true);
    this.applyReadonly();
  }

  private emit(): void {
    this.dataChange.emit({...this.form.value()});
    this.validChange.emit(true);
  }

  private applyReadonly(): void {
    this.form.setDisabled(this.readonly);
  }
}
