import {ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked} from '@angular/core';
import {DatePipe} from '@angular/common';
import {RouterLink} from '@angular/router';
import {compatForm} from '@angular/forms/signals/compat';
import {FormField, FormRoot, required} from '@angular/forms/signals';
import {MatButtonModule} from '@angular/material/button';
import {MatCardModule} from '@angular/material/card';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatIconModule} from '@angular/material/icon';
import {MatInputModule} from '@angular/material/input';
import {MatPaginatorModule, PageEvent} from '@angular/material/paginator';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatSelectModule} from '@angular/material/select';
import {MatTableModule} from '@angular/material/table';
import {TranslateModule} from '@ngx-translate/core';
import {AbsenceInfirmier, TYPES_ABSENCE, TypeAbsence} from '../../core/api/infirmier-api.service';
import {AbsencesStore} from './absences.store';
import {InfirmiersStore} from './infirmiers.store';

type AbsenceFormModel = { infirmierId: string; debut: string; fin: string; type: TypeAbsence; motif: string };

/** Taille de la page d'infirmiers chargée pour la liste déroulante du formulaire. */
const TAILLE_SELECTION = 100;

function emptyAbsence(): AbsenceFormModel {
  return {infirmierId: '', debut: '', fin: '', type: 'CONGE', motif: ''};
}

/**
 * Absences des infirmiers (congé, maladie, formation) : elles se superposent au roulement dans le planning de
 * présence, qui signale alors les créneaux à remplacer.
 */
@Component({
  selector: 'app-absences-infirmiers',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe, RouterLink, MatButtonModule, MatCardModule, MatFormFieldModule, MatIconModule, MatInputModule, MatPaginatorModule,
    MatProgressBarModule, MatSelectModule, MatTableModule, FormRoot, FormField, TranslateModule,
  ],
  templateUrl: './absences.component.html',
  styleUrl: './infirmiers.component.css',
})
export class AbsencesInfirmiersComponent {
  protected readonly store = inject(AbsencesStore);
  protected readonly infirmiers = inject(InfirmiersStore);
  protected readonly columns = ['infirmier', 'periode', 'type', 'motif', 'actions'];
  protected readonly types = TYPES_ABSENCE;
  protected readonly formModel = signal(emptyAbsence());
  protected readonly absenceForm = compatForm(this.formModel, (form) => {
    required(form.infirmierId);
    required(form.debut);
    required(form.fin);
    required(form.type);
  });
  protected readonly periodeValide = computed(() => {
    const {debut, fin} = this.formModel();
    return !debut || !fin || fin >= debut;
  });
  protected readonly canSave = computed(() =>
    this.absenceForm().valid() && this.periodeValide() && !this.store.saving());

  constructor() {
    this.store.loadPage({page: 0, size: this.store.pageSize()});
    // Liste déroulante : une page bornée d'infirmiers du centre (les noms de la liste d'absences en proviennent aussi).
    this.infirmiers.loadPage({page: 0, size: TAILLE_SELECTION});
    effect(() => {
      if (!this.store.successMessage()) return;
      untracked(() => this.formModel.set(emptyAbsence()));
    });
  }

  protected nom(infirmierId: string): string {
    const i = this.infirmiers.rows().find((x) => x.id === infirmierId);
    return i ? (i.prenom ? `${i.prenom} ${i.nom}` : i.nom) : infirmierId;
  }

  protected save(): void {
    if (!this.canSave()) return;
    const m = this.formModel();
    this.store.create({
      infirmierId: m.infirmierId, debut: m.debut, fin: m.fin, type: m.type, motif: m.motif.trim() || null,
    });
  }

  protected remove(row: AbsenceInfirmier): void {
    this.store.remove(row.id);
  }

  protected onPage(event: PageEvent): void {
    this.store.setPagination(event.pageIndex, event.pageSize);
    this.store.loadPage({page: event.pageIndex, size: event.pageSize});
  }
}
