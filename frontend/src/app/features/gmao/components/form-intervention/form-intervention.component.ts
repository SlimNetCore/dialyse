import {ChangeDetectionStrategy, Component, computed, inject, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {ActivatedRoute, Router, RouterLink} from '@angular/router';
import {compatForm} from '@angular/forms/signals/compat';
import {FormField, FormRoot, required} from '@angular/forms/signals';
import {MatCardModule} from '@angular/material/card';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatSelectModule} from '@angular/material/select';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {TranslateModule} from '@ngx-translate/core';
import {GmaoApiService} from '../../../../core/api/gmao-api.service';
import {TYPES_INTERVENTION} from '../../gmao-options.util';

type InterventionFormModel = {
  equipementId: string;
  type: string;
  dateDebut: string;
  description: string;
};

function emptyForm(equipementId: string | null): InterventionFormModel {
  return {equipementId: equipementId ?? '', type: '', dateDebut: '', description: ''};
}

/**
 * Création d'une intervention GMAO (signal forms — AGENTS.md §4).
 * Les transitions de statut (démarrer/terminer/annuler) se font depuis la liste.
 */
@Component({
  selector: 'app-gmao-form-intervention',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule, RouterLink, MatCardModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    MatButtonModule, MatIconModule, MatProgressBarModule, FormRoot, FormField, TranslateModule,
  ],
  templateUrl: './form-intervention.component.html',
  styleUrls: ['./form-intervention.component.css', '../../gmao-shared.css'],
})
export class GmaoFormInterventionComponent {
  protected readonly types = TYPES_INTERVENTION;
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly interventionForm = compatForm(this.formModel, (form) => {
    required(form.equipementId);
    required(form.type);
    required(form.dateDebut);
    required(form.description);
  });
  protected readonly canSave = computed(() =>
    !!this.formModel().equipementId.trim()
    && !!this.formModel().type
    && !!this.formModel().dateDebut
    && !!this.formModel().description.trim()
    && !this.saving());
  private readonly api = inject(GmaoApiService);
  private readonly route = inject(ActivatedRoute);
  protected readonly formModel = signal(emptyForm(this.route.snapshot.queryParamMap.get('equipementId')));
  private readonly router = inject(Router);

  protected save(): void {
    if (!this.canSave()) return;
    const form = this.formModel();
    this.saving.set(true);
    this.error.set(null);

    this.api.createIntervention({
      equipementId: form.equipementId.trim(),
      type: form.type as never,
      dateDebut: `${form.dateDebut}T00:00:00`,
      description: form.description.trim(),
    }).subscribe({
      next: () => this.router.navigate(['/gmao/interventions']),
      error: () => {
        this.saving.set(false);
        this.error.set('GMAO.INTERVENTIONS.SAVE_ERROR');
      },
    });
  }
}
