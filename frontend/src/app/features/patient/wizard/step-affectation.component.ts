import {
  ChangeDetectionStrategy,
  Component,
  computed,
  EventEmitter,
  inject,
  input,
  Input,
  OnChanges,
  OnInit,
  Output,
  signal,
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
  GenerateursStore,
  MedecinsStore,
  PositionsStore,
  SallesStore,
  TransporteursStore,
} from '../../../core/state/referentials.store';
import {SignalForm} from '../../../shared/forms/signal-form';
import {GmaoApiService, StatutEquipement} from '../../../core/api/gmao-api.service';
import {PropositionAffectation} from '../../../core/api/planning-api.service';
import {AffectationSuggestionsComponent} from './affectation-suggestions/affectation-suggestions.component';
import {joursCoches, joursVersFlags} from './affectation-suggestions/affectation-suggestions.util';

interface AffectationModel {
  salleId: string | null;
  medecinTraitantId: string | null;
  positionId: string | null;
  transporteurAllerId: string | null;
  transporteurRetourId: string | null;
  categorieTransportId: string | null;
  generateurId: string | null;
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
  | 'categorieTransportId'
  | 'generateurId';

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
    AffectationSuggestionsComponent,
  ],
  templateUrl: './step-affectation.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './step-affectation.component.css',
})
export class StepAffectationComponent implements OnInit, OnChanges {
  @Input() readonly = false;
  /** Patient en cours de modification : sa place actuelle est comptée comme libre par l'aide au placement. */
  readonly patientId = input<string | null>(null);
  @Output() dataChange = new EventEmitter<Record<string, any>>();
  @Output() validChange = new EventEmitter<boolean>();

  readonly form = new SignalForm<AffectationModel>({
    salleId: null,
    medecinTraitantId: null,
    positionId: null,
    transporteurAllerId: null,
    transporteurRetourId: null,
    categorieTransportId: null,
    generateurId: null,
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
  private readonly generateursStore = inject(GenerateursStore);
  /** Statut du générateur quand il est indisponible pour un patient — avertissement, pas un blocage. */
  readonly disponibiliteWarning = signal<StatutEquipement | null>(null);
  /** Tous les générateurs du centre sont toujours proposés ; l'indisponibilité est signalée après sélection. */
  readonly generateurs = this.generateursStore.items as unknown as () => DropdownItem[];
  /** Générateur indisponible (maintenance, attente de pièce, panne, réforme, intervention en cours) : enregistrement bloqué. */
  readonly generateurBloque = computed(() => this.disponibiliteWarning() !== null);
  private readonly gmaoApi = inject(GmaoApiService);

  ngOnInit(): void {
    const cid = this.appShell.currentCenterId();
    if (!cid) return;
    void this.sallesStore.ensureLoaded(cid);
    void this.medecinsStore.ensureLoaded(cid);
    void this.positionsStore.ensureLoaded(cid);
    void this.transporteursStore.ensureLoaded(cid);
    void this.categoriesTransportStore.ensureLoaded(cid);
    void this.generateursStore.ensureLoaded(cid);
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['readonly']) this.applyReadonly();
  }

  onSelect(key: AffectationSelectKey, item: DropdownItem | null): void {
    if (this.readonly) return;
    this.form.set(key, item?.id ?? null);
    if (key === 'generateurId') {
      this.checkDisponibilite(item?.id ?? null);
    }
    this.emit();
  }

  /** Jours cochés sur la fiche : imposés à l'aide au placement. */
  readonly joursImposes = computed(() => joursCoches(this.form.value()));

  /**
   * Applique une place proposée par l'aide au placement : salle, créneau, générateur et jours de dialyse. La
   * disponibilité du générateur est revérifiée (sécurité patient) comme pour une sélection manuelle.
   */
  appliquerProposition(p: PropositionAffectation): void {
    if (this.readonly) return;
    this.form.patch({
      salleId: p.salle.id,
      positionId: p.creneau.id,
      generateurId: p.generateur.id,
      ...joursVersFlags(p.jours),
    });
    this.checkDisponibilite(p.generateur.id);
    this.emit();
  }

  isValid(): boolean {
    // L'étape affectation est facultative, sauf si le générateur choisi est indisponible.
    return !this.generateurBloque();
  }

  onDay(key: AffectationDayKey, checked: boolean): void {
    if (this.readonly) return;
    this.form.set(key, checked);
    this.emit();
  }

  markTouched(): void {
    this.form.markAllTouched();
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
      generateurId: pickId('generateurId', 'generateur_id', 'generateur'),
      jourDimanche: asBool(pick('jourDimanche', 'jour_dimanche', 'dimanche')),
      jourLundi: asBool(pick('jourLundi', 'jour_lundi', 'lundi')),
      jourMardi: asBool(pick('jourMardi', 'jour_mardi', 'mardi')),
      jourMercredi: asBool(pick('jourMercredi', 'jour_mercredi', 'mercredi')),
      jourJeudi: asBool(pick('jourJeudi', 'jour_jeudi', 'jeudi')),
      jourVendredi: asBool(pick('jourVendredi', 'jour_vendredi', 'vendredi')),
      jourSamedi: asBool(pick('jourSamedi', 'jour_samedi', 'samedi')),
    });
    this.applyReadonly();
    this.checkDisponibilite(this.form.value().generateurId);
  }

  /**
   * Sécurité patient (module GMAO v2) : vérifie la disponibilité du générateur choisi. Un générateur en
   * tout générateur indisponible (maintenance, attente de pièce, panne, réforme, intervention en cours)
   * rend l'affectation invalide et bloque l'enregistrement du patient.
   */
  private checkDisponibilite(generateurId: string | null): void {
    this.disponibiliteWarning.set(null);
    this.validChange.emit(true);
    if (!generateurId) return;
    this.gmaoApi.checkDisponibilitePatient(generateurId).subscribe({
      next: (res) => {
        this.disponibiliteWarning.set(res.disponible ? null : res.statut);
        this.validChange.emit(!this.generateurBloque());
      },
      error: () => {
        this.disponibiliteWarning.set(null);
        this.validChange.emit(true);
      },
    });
  }

  private emit(): void {
    this.dataChange.emit({...this.form.value()});
    this.validChange.emit(!this.generateurBloque());
  }

  private applyReadonly(): void {
    this.form.setDisabled(this.readonly);
  }
}
