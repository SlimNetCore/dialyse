import {ChangeDetectionStrategy, Component, computed, inject, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {ActivatedRoute, Router, RouterLink} from '@angular/router';
import {compatForm} from '@angular/forms/signals/compat';
import {FormField, FormRoot, required} from '@angular/forms/signals';
import {MatCardModule} from '@angular/material/card';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatSelectModule} from '@angular/material/select';
import {MatAutocompleteModule} from '@angular/material/autocomplete';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {TranslateModule} from '@ngx-translate/core';
import {Equipement, GmaoApiService} from '../../../../core/api/gmao-api.service';
import {
  ETATS_AVANT_INTERVENTION,
  localInputToUtcIso,
  PRIORITES_INTERVENTION,
  TYPES_INTERVENTION
} from '../../gmao-options.util';
import {GmaoIntervenantsStore} from '../../state/gmao-intervenants.store';

type InterventionFormModel = {
  equipementId: string;
  type: string;
  dateDebut: string;
  description: string;
  intervenantId: string;
  etatEquipementAvant: string;
  priorite: string;
  echeance: string;
  symptome: string;
};

function emptyForm(equipementId: string | null): InterventionFormModel {
  return {
    equipementId: equipementId ?? '',
    type: '',
    dateDebut: '',
    description: '',
    intervenantId: '',
    etatEquipementAvant: '',
    priorite: 'NORMALE',
    echeance: '',
    symptome: '',
  };
}

/**
 * Création d'une intervention GMAO (signal forms — AGENTS.md §4).
 * Les transitions de statut (démarrer/terminer/annuler) et les lignes de coût se gèrent depuis la
 * liste / la fiche équipement.
 */
@Component({
  selector: 'app-gmao-form-intervention',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule, RouterLink, MatCardModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatAutocompleteModule,
    MatButtonModule, MatIconModule, MatProgressBarModule, FormRoot, FormField, TranslateModule,
  ],
  templateUrl: './form-intervention.component.html',
  styleUrls: ['./form-intervention.component.css', '../../gmao-shared.css'],
})
export class GmaoFormInterventionComponent {
  protected readonly types = TYPES_INTERVENTION;
  protected readonly etats = ETATS_AVANT_INTERVENTION;
  protected readonly priorites = PRIORITES_INTERVENTION;
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  private readonly api = inject(GmaoApiService);
  private readonly route = inject(ActivatedRoute);
  protected readonly formModel = signal(emptyForm(this.route.snapshot.queryParamMap.get('equipementId')));
  private readonly router = inject(Router);
  private readonly intervenantsStore = inject(GmaoIntervenantsStore);
  protected readonly intervenants = this.intervenantsStore.rows;
  protected readonly interventionForm = compatForm(this.formModel, (form) => {
    required(form.equipementId);
    required(form.type);
    required(form.dateDebut);
    required(form.description);
    required(form.etatEquipementAvant);
  });
  protected readonly canSave = computed(() =>
    !!this.formModel().equipementId.trim()
    && !!this.formModel().type
    && !!this.formModel().dateDebut
    && !!this.formModel().description.trim()
    && !!this.formModel().etatEquipementAvant
    && (!this.formModel().echeance || new Date(this.formModel().echeance).getTime() >= new Date(this.formModel().dateDebut).getTime())
    && !this.saving());

  /** Équipements sélectionnables (hors réformés/désactivés), pour la liste déroulante avec recherche. */
  protected readonly equipements = signal<Equipement[]>([]);
  protected readonly equipementSearch = signal('');
  protected readonly filteredEquipements = computed(() => {
    const q = this.equipementSearch().trim().toLowerCase();
    const selected = this.equipements().find((e) => e.id === this.formModel().equipementId);
    if (!q || (selected && this.equipementLabel(selected).toLowerCase() === q)) return this.equipements();
    return this.equipements().filter((e) => this.equipementLabel(e).toLowerCase().includes(q));
  });

  constructor() {
    this.intervenantsStore.loadPage({page: 0, size: 100});
    this.api.listEquipements(0, 200).subscribe({
      next: (res) => {
        this.equipements.set((res.items ?? []).filter((e) => e.statut !== 'REFORME' && e.statut !== 'DESACTIF'));
        const preset = this.equipements().find((e) => e.id === this.formModel().equipementId);
        if (preset) this.equipementSearch.set(this.equipementLabel(preset));
      },
      error: () => this.error.set('GMAO.EQUIPEMENTS.LOAD_ERROR'),
    });
  }

  protected equipementLabel(e: Equipement): string {
    return `${e.code} — ${e.designation}`;
  }

  protected onEquipementSearch(value: string): void {
    this.equipementSearch.set(value);
    // Toute modification du texte invalide la sélection précédente : il faut en choisir une dans la liste.
    this.formModel.update((m) => ({...m, equipementId: ''}));
  }

  protected selectEquipement(e: Equipement): void {
    this.formModel.update((m) => ({...m, equipementId: e.id}));
    this.equipementSearch.set(this.equipementLabel(e));
  }

  protected save(): void {
    if (!this.canSave()) return;
    const form = this.formModel();
    this.saving.set(true);
    this.error.set(null);

    this.api.createIntervention({
      equipementId: form.equipementId.trim(),
      type: form.type as never,
      dateDebut: localInputToUtcIso(form.dateDebut),
      description: form.description.trim(),
      intervenantId: form.intervenantId || null,
      etatEquipementAvant: form.etatEquipementAvant as never,
      priorite: form.priorite as never,
      echeance: form.echeance ? localInputToUtcIso(form.echeance) : null,
      symptome: form.symptome.trim() || null,
    }).subscribe({
      next: () => this.router.navigate(['/gmao/interventions']),
      error: () => {
        this.saving.set(false);
        this.error.set('GMAO.INTERVENTIONS.SAVE_ERROR');
      },
    });
  }
}
