import {ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked} from '@angular/core';
import {CurrencyPipe, DatePipe} from '@angular/common';
import {compatForm} from '@angular/forms/signals/compat';
import {FormField, FormRoot, required} from '@angular/forms/signals';
import {MatAutocompleteModule} from '@angular/material/autocomplete';
import {MatButtonModule} from '@angular/material/button';
import {MatCardModule} from '@angular/material/card';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatIconModule} from '@angular/material/icon';
import {MatInputModule} from '@angular/material/input';
import {MatPaginatorModule, PageEvent} from '@angular/material/paginator';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatSelectModule} from '@angular/material/select';
import {MatTableModule} from '@angular/material/table';
import {MatTooltipModule} from '@angular/material/tooltip';
import {TranslateModule} from '@ngx-translate/core';
import {firstValueFrom} from 'rxjs';
import {
  AbsencePatient,
  MOTIFS_ABSENCE,
  MotifAbsence,
  STATUTS_ABSENCE,
  StatutAbsence,
} from '../../core/api/absence-patient-api.service';
import {BackendApiService} from '../../core/api/backend-api.service';
import {AppShellStore} from '../../core/state/app-shell.store';
import {AuthStore} from '../../core/state/auth.store';
import {AbsencesPatientsStore, EMPTY_FILTERS} from './absences-patients.store';
import {actionPossible, actionValide, ActionAbsence, estModifiable} from './absences-patients.util';

type PatientOption = { id: string; label: string };

/** Taille maximale de la liste de patients proposée à la déclaration. */
const TAILLE_RECHERCHE = 10;
/** Nombre minimal de caractères et pause (ms) avant de lancer la recherche de patient. */
const MIN_RECHERCHE = 2;
const DELAI_RECHERCHE_MS = 300;

/** Déclaration vierge : la date de la séance manquée est aujourd'hui par défaut. */
function emptyDeclaration() {
  const now = new Date();
  const jour = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
  return {patientId: '', dateSeance: jour, motif: '' as MotifAbsence | '', commentaire: ''};
}

/**
 * Suivi des absences de patients : déclaration, qualification par un motif, rattrapage et annulation. Les absences
 * détectées automatiquement (séance prévue sans séance réalisée) arrivent « à qualifier ».
 */
@Component({
  selector: 'app-absences-patients',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CurrencyPipe, DatePipe, MatAutocompleteModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatIconModule, MatInputModule,
    MatPaginatorModule, MatProgressBarModule, MatSelectModule, MatTableModule, MatTooltipModule, FormRoot, FormField,
    TranslateModule,
  ],
  templateUrl: './absences-patients.component.html',
  styleUrl: './absences-patients.component.css',
})
export class AbsencesPatientsComponent {
  protected readonly store = inject(AbsencesPatientsStore);
  protected readonly columns = ['date', 'patient', 'source', 'statut', 'motif', 'valeur', 'actions'];
  protected readonly statuts = STATUTS_ABSENCE;
  protected readonly motifs = MOTIFS_ABSENCE;
  // --- Filtres
  protected readonly filtersModel = signal({...EMPTY_FILTERS});
  protected readonly filtersForm = compatForm(this.filtersModel);
  // --- Déclaration
  protected readonly declareOpen = signal(false);
  protected readonly declareModel = signal(emptyDeclaration());
  private searchTimer: ReturnType<typeof setTimeout> | undefined;
  protected readonly declareForm = compatForm(this.declareModel, (form) => {
    required(form.patientId);
    required(form.dateSeance);
  });
  protected readonly patientQuery = signal('');
  protected readonly patientOptions = signal<PatientOption[]>([]);
  protected readonly patientSearching = signal(false);
  protected readonly declareCommentaireRequis = computed(() => this.declareModel().motif === 'AUTRE');
  protected readonly canDeclare = computed(() =>
    this.declareForm().valid() && !this.store.saving()
    && (!this.declareCommentaireRequis() || this.declareModel().commentaire.trim().length > 0));
  // --- Action sur une absence (qualifier / rattrapage / annulation)
  protected readonly action = signal<{ mode: ActionAbsence; row: AbsencePatient } | null>(null);
  protected readonly actionModel = signal({motif: '' as MotifAbsence | '', commentaire: '', dateRattrapage: ''});
  protected readonly actionForm = compatForm(this.actionModel);
  protected readonly actionOk = computed(() => {
    const a = this.action();
    return !!a && actionValide(a.mode, a.row.statut, this.actionModel()) && !this.store.saving();
  });
  private readonly api = inject(BackendApiService);
  private readonly shell = inject(AppShellStore);
  private readonly auth = inject(AuthStore);
  /** Un administrateur ou un médecin peut corriger une absence déjà qualifiée. */
  protected readonly peutCorriger = computed(() => this.auth.hasRole('ADMIN') || this.auth.hasRole('MEDECIN'));

  constructor() {
    effect(() => {
      // Rechargement au changement de centre actif.
      this.shell.currentCenterId();
      untracked(() => this.store.loadPage({page: 0, size: this.store.pageSize()}));
    });
    effect(() => {
      if (!this.store.successMessage()) return;
      untracked(() => {
        this.action.set(null);
        this.declareModel.set(emptyDeclaration());
        this.patientQuery.set('');
        this.patientOptions.set([]);
        this.declareOpen.set(false);
      });
    });
  }

  protected applyFilters(): void {
    this.store.applyFilters({...this.filtersModel()});
  }

  protected resetFilters(): void {
    this.filtersModel.set({...EMPTY_FILTERS});
    this.store.applyFilters({...EMPTY_FILTERS});
  }

  /** Saisie dans le champ patient : la sélection précédente est oubliée et la recherche part après une courte pause. */
  protected onPatientInput(value: string): void {
    this.patientQuery.set(value);
    this.declareModel.update((m) => ({...m, patientId: ''}));
    clearTimeout(this.searchTimer);
    if (value.trim().length < MIN_RECHERCHE) {
      this.patientOptions.set([]);
      return;
    }
    this.searchTimer = setTimeout(() => void this.searchPatients(), DELAI_RECHERCHE_MS);
  }

  protected selectPatient(option: PatientOption): void {
    this.patientQuery.set(option.label);
    this.declareModel.update((m) => ({...m, patientId: option.id}));
  }

  /** Qualification en un clic depuis la ligne ; « Autre » ouvre le panneau pour saisir le commentaire obligatoire. */
  protected quickQualify(row: AbsencePatient, motif: MotifAbsence): void {
    if (motif === 'AUTRE') {
      this.openAction('qualifier', row);
      this.actionModel.update((m) => ({...m, motif}));
      return;
    }
    this.store.clearMessages();
    this.store.qualify({id: row.id, motif, commentaire: null});
  }

  /** Affiche uniquement les absences en attente de motif. */
  protected showToQualify(): void {
    const filters = {...EMPTY_FILTERS, statut: 'A_QUALIFIER' as const};
    this.filtersModel.set(filters);
    this.store.applyFilters(filters);
  }

  private async searchPatients(): Promise<void> {
    const centerId = this.shell.currentCenterId();
    const nom = this.patientQuery().trim();
    if (!centerId || !nom) return;
    this.patientSearching.set(true);
    try {
      const res = await firstValueFrom(this.api.listPatients(centerId, '', {
        page: 0, size: TAILLE_RECHERCHE, filters: {nom},
      }));
      this.patientOptions.set((res.items ?? []).map((p) => ({
        id: p.id,
        label: `${p.codePatient ?? ''} ${p.prenom ?? ''} ${p.nom ?? ''}`.trim(),
      })));
    } catch {
      this.patientOptions.set([]);
    } finally {
      this.patientSearching.set(false);
    }
  }

  protected save(): void {
    if (!this.canDeclare()) return;
    const m = this.declareModel();
    this.store.declare({
      patientId: m.patientId,
      dateSeance: m.dateSeance,
      motif: m.motif || null,
      commentaire: m.commentaire.trim() || null,
    });
  }

  protected modifiable(row: AbsencePatient): boolean {
    return estModifiable(row.statut);
  }

  protected possible(mode: ActionAbsence, row: AbsencePatient): boolean {
    return actionPossible(mode, row.statut, this.peutCorriger());
  }

  protected openAction(mode: ActionAbsence, row: AbsencePatient): void {
    this.store.clearMessages();
    this.actionModel.set({
      motif: mode === 'qualifier' ? (row.motif ?? '') : '', commentaire: '', dateRattrapage: '',
    });
    this.action.set({mode, row});
  }

  protected closeAction(): void {
    this.action.set(null);
  }

  protected commentaireRequis(): boolean {
    const a = this.action();
    if (!a) return false;
    return a.mode === 'annuler' || (a.mode === 'qualifier'
      && (this.actionModel().motif === 'AUTRE' || a.row.statut !== 'A_QUALIFIER'));
  }

  protected confirmAction(): void {
    const a = this.action();
    if (!a || !this.actionOk()) return;
    const m = this.actionModel();
    const commentaire = m.commentaire.trim();
    if (a.mode === 'qualifier') {
      this.store.qualify({id: a.row.id, motif: m.motif as MotifAbsence, commentaire: commentaire || null});
    } else if (a.mode === 'rattrapage') {
      this.store.makeUp({id: a.row.id, dateRattrapage: m.dateRattrapage});
    } else {
      this.store.cancel({id: a.row.id, commentaire});
    }
  }

  protected statutClass(statut: StatutAbsence): string {
    return `abs-badge ${statut.toLowerCase()}`;
  }

  protected onPage(event: PageEvent): void {
    this.store.setPagination(event.pageIndex, event.pageSize);
    this.store.loadPage({page: event.pageIndex, size: event.pageSize});
  }
}
