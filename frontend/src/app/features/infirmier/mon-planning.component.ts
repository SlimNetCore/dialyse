import {ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked} from '@angular/core';
import {DatePipe, SlicePipe} from '@angular/common';
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
import {TYPES_ABSENCE, TypeAbsence} from '../../core/api/infirmier-api.service';
import {AppShellStore} from '../../core/state/app-shell.store';
import {MonPlanningStore} from './mon-planning.store';
import {absenceAnnulable, aujourdhuiUtc} from './presence.util';

type AbsenceFormModel = { debut: string; fin: string; type: TypeAbsence; motif: string };

function emptyAbsence(): AbsenceFormModel {
  return {debut: '', fin: '', type: 'CONGE', motif: ''};
}

/**
 * « Mon planning » : l'infirmier connecté voit ses créneaux de la semaine (prévu, remplaçant, absent), déclare ses
 * absences à venir et retire celles qui n'ont pas commencé. Un compte non relié à une fiche en est informé.
 */
@Component({
  selector: 'app-mon-planning',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe, SlicePipe, RouterLink, MatButtonModule, MatCardModule, MatFormFieldModule, MatIconModule, MatInputModule,
    MatPaginatorModule, MatProgressBarModule, MatSelectModule, MatTableModule, FormRoot, FormField, TranslateModule,
  ],
  templateUrl: './mon-planning.component.html',
  styleUrl: './mon-planning.component.css',
})
export class MonPlanningComponent {
  protected readonly store = inject(MonPlanningStore);
  protected readonly columns = ['periode', 'type', 'motif', 'actions'];
  protected readonly types = TYPES_ABSENCE;
  protected readonly aujourdhui = aujourdhuiUtc();
  protected readonly annulable = absenceAnnulable;
  protected readonly formModel = signal(emptyAbsence());
  protected readonly absenceForm = compatForm(this.formModel, (form) => {
    required(form.debut);
    required(form.fin);
    required(form.type);
  });
  /** Mes créneaux d'aujourd'hui, quand la semaine affichée contient aujourd'hui. */
  protected readonly semaineCourante = computed(() => {
    const p = this.store.planning();
    return !!p && p.debut <= this.aujourdhui && this.aujourdhui <= p.fin;
  });
  protected readonly creneauxDuJour = computed(() =>
    (this.store.planning()?.mesCreneaux ?? []).filter((c) => c.date === this.aujourdhui));
  protected readonly periodeValide = computed(() => {
    const {debut, fin} = this.formModel();
    return !debut || !fin || fin >= debut;
  });
  protected readonly canSave = computed(() =>
    this.absenceForm().valid() && this.periodeValide() && !this.store.saving());
  private readonly shell = inject(AppShellStore);

  constructor() {
    // Recharge la semaine courante et les absences à l'ouverture et à chaque changement de centre actif.
    effect(() => {
      this.shell.currentCenterId();
      untracked(() => {
        this.store.chargerPlanning(null);
        this.store.loadAbsences({page: 0, size: this.store.pageSize()});
      });
    });
    effect(() => {
      if (!this.store.successMessage()) return;
      untracked(() => this.formModel.set(emptyAbsence()));
    });
  }

  protected salleNom(id: string): string {
    return this.store.planning()?.salles.find((s) => s.id === id)?.nom ?? '';
  }

  protected creneauLibelle(id: string): string {
    return this.store.planning()?.creneaux.find((c) => c.id === id)?.libelle ?? '';
  }

  protected save(): void {
    if (!this.canSave()) return;
    const m = this.formModel();
    this.store.declarer({debut: m.debut, fin: m.fin, type: m.type, motif: m.motif.trim() || null});
  }

  protected onPage(event: PageEvent): void {
    this.store.setPagination(event.pageIndex, event.pageSize);
    this.store.loadAbsences({page: event.pageIndex, size: event.pageSize});
  }
}
