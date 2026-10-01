import {ChangeDetectionStrategy, Component, computed, effect, inject, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {compatForm} from '@angular/forms/signals/compat';
import {FormField, FormRoot, required} from '@angular/forms/signals';
import {MatCardModule} from '@angular/material/card';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatSelectModule} from '@angular/material/select';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatTableModule} from '@angular/material/table';
import {MatPaginatorModule, PageEvent} from '@angular/material/paginator';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatTooltipModule} from '@angular/material/tooltip';
import {TranslateModule} from '@ngx-translate/core';
import {Intervenant} from '../../../../core/api/gmao-api.service';
import {GmaoIntervenantsStore} from '../../state/gmao-intervenants.store';

type IntervenantFormModel = {
  nom: string;
  type: string;
  telephone: string;
  email: string;
  tarifHoraireDefaut: string;
};

function emptyForm(): IntervenantFormModel {
  return {nom: '', type: 'INTERNE', telephone: '', email: '', tarifHoraireDefaut: ''};
}

/**
 * Référentiel Intervenant GMAO (technicien interne / prestataire externe) — une seule page
 * formulaire + liste, patron {@code fournisseurs.component.ts} (stock).
 */
@Component({
  selector: 'app-gmao-intervenants',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule, MatCardModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule,
    MatIconModule, MatTableModule, MatPaginatorModule, MatProgressBarModule, MatTooltipModule,
    FormRoot, FormField, TranslateModule,
  ],
  templateUrl: './intervenants.component.html',
  styleUrls: ['./intervenants.component.css', '../../gmao-shared.css'],
})
export class GmaoIntervenantsComponent {
  protected readonly store = inject(GmaoIntervenantsStore);
  protected readonly columns = ['nom', 'type', 'telephone', 'tarifHoraireDefaut', 'actif', 'actions'];

  protected readonly editingId = signal<string | null>(null);
  protected readonly formModel = signal(emptyForm());
  protected readonly intervenantForm = compatForm(this.formModel, (form) => {
    required(form.nom);
    required(form.type);
  });
  protected readonly canSave = computed(() =>
    !!this.formModel().nom.trim() && !!this.formModel().type && !this.store.saving());

  constructor() {
    this.store.loadPage({page: 0, size: this.store.pageSize()});

    effect(() => {
      const msg = this.store.successMessage();
      if (!msg) return;
      this.formModel.set(emptyForm());
      this.editingId.set(null);
      this.store.loadPage({page: this.store.pageIndex(), size: this.store.pageSize()});
    });
  }

  protected save(): void {
    if (!this.canSave()) return;
    const form = this.formModel();
    const payload = {
      nom: form.nom.trim(),
      type: form.type as never,
      telephone: form.telephone.trim() || null,
      email: form.email.trim() || null,
      tarifHoraireDefaut: form.tarifHoraireDefaut ? Number(form.tarifHoraireDefaut) : null,
    };
    const id = this.editingId();
    if (id) {
      this.store.updateIntervenant({id, payload});
    } else {
      this.store.createIntervenant(payload);
    }
  }

  protected edit(row: Intervenant): void {
    this.editingId.set(row.id);
    this.formModel.set({
      nom: row.nom,
      type: row.type,
      telephone: row.telephone ?? '',
      email: row.email ?? '',
      tarifHoraireDefaut: row.tarifHoraireDefaut != null ? String(row.tarifHoraireDefaut) : '',
    });
  }

  protected cancelEdit(): void {
    this.editingId.set(null);
    this.formModel.set(emptyForm());
  }

  protected deactivate(row: Intervenant): void {
    this.store.deactivateIntervenant(row.id);
  }

  protected onPageChange(event: PageEvent): void {
    this.store.setPagination(event.pageIndex, event.pageSize);
    this.store.loadPage({page: event.pageIndex, size: event.pageSize});
  }
}
