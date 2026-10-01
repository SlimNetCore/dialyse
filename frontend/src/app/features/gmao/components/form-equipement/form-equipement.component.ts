import {ChangeDetectionStrategy, Component, computed, inject, OnInit, signal} from '@angular/core';
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
import {TYPES_EQUIPEMENT} from '../../gmao-options.util';
import {AuthStore} from '../../../../core/state/auth.store';
import {SallesStore} from '../../../../core/state/referentials.store';

type EquipementFormModel = {
  code: string;
  designation: string;
  type: string;
  fabricant: string;
  modele: string;
  numeroSerie: string;
  dateInstallation: string;
  localisation: string;
  salleId: string;
  prixAcquisition: string;
};

function emptyForm(): EquipementFormModel {
  return {
    code: '', designation: '', type: '', fabricant: '', modele: '', numeroSerie: '',
    dateInstallation: '', localisation: '', salleId: '', prixAcquisition: '',
  };
}

/**
 * Création / édition d'un équipement GMAO (signal forms — AGENTS.md §4).
 * Le code, le type et la date d'installation sont structurants et deviennent lecture seule en édition.
 */
@Component({
  selector: 'app-gmao-form-equipement',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule, RouterLink, MatCardModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    MatButtonModule, MatIconModule, MatProgressBarModule, FormRoot, FormField, TranslateModule,
  ],
  templateUrl: './form-equipement.component.html',
  styleUrls: ['./form-equipement.component.css', '../../gmao-shared.css'],
})
export class GmaoFormEquipementComponent implements OnInit {
  protected readonly types = TYPES_EQUIPEMENT;
  protected readonly loading = signal(false);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly isEditing = signal(false);
  protected readonly equipementId = signal<string | null>(null);
  protected readonly formModel = signal(emptyForm());
  protected readonly equipementForm = compatForm(this.formModel, (form) => {
    required(form.designation);
    required(form.type);
    required(form.code);
    required(form.dateInstallation);
  });
  protected readonly canSave = computed(() =>
    !!this.formModel().designation.trim()
    && !!this.formModel().type
    && !!this.formModel().code.trim()
    && !!this.formModel().dateInstallation
    && !this.saving());
  private readonly sallesStore = inject(SallesStore);
  protected readonly salles = this.sallesStore.items;
  private readonly api = inject(GmaoApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthStore);

  ngOnInit(): void {
    const centerId = this.auth.centerId();
    if (centerId) this.sallesStore.ensureLoaded(centerId);

    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.isEditing.set(true);
      this.equipementId.set(id);
      this.loadEquipement(id);
    }
  }

  protected save(): void {
    if (!this.canSave()) return;
    const form = this.formModel();
    this.saving.set(true);
    this.error.set(null);

    const id = this.equipementId();
    const prixAcquisition = form.prixAcquisition ? Number(form.prixAcquisition) : null;
    const request = id
      ? this.api.updateEquipement(id, {
        designation: form.designation.trim(),
        fabricant: form.fabricant.trim() || null,
        modele: form.modele.trim() || null,
        numeroSerie: form.numeroSerie.trim() || null,
        localisation: form.localisation.trim() || null,
        salleId: form.salleId || null,
        prixAcquisition,
      })
      : this.api.createEquipement({
        code: form.code.trim(),
        designation: form.designation.trim(),
        type: form.type as never,
        fabricant: form.fabricant.trim() || null,
        modele: form.modele.trim() || null,
        numeroSerie: form.numeroSerie.trim() || null,
        dateInstallation: `${form.dateInstallation}T00:00:00`,
        localisation: form.localisation.trim() || null,
        salleId: form.salleId || null,
        prixAcquisition,
      });

    request.subscribe({
      next: () => this.router.navigate(['/gmao/equipements']),
      error: () => {
        this.saving.set(false);
        this.error.set('GMAO.EQUIPEMENTS.SAVE_ERROR');
      },
    });
  }

  private loadEquipement(id: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.getEquipement(id).subscribe({
      next: (data) => {
        this.formModel.set({
          code: data.code,
          designation: data.designation,
          type: data.type,
          fabricant: data.fabricant ?? '',
          modele: data.modele ?? '',
          numeroSerie: data.numeroSerie ?? '',
          dateInstallation: data.dateInstallation.slice(0, 10),
          localisation: data.localisation ?? '',
          salleId: data.salleId ?? '',
          prixAcquisition: data.prixAcquisition != null ? String(data.prixAcquisition) : '',
        });
        this.loading.set(false);
      },
      error: () => {
        this.error.set('GMAO.EQUIPEMENTS.LOAD_ERROR');
        this.loading.set(false);
      },
    });
  }
}
