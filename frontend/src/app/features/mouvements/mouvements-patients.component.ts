import {ChangeDetectionStrategy, Component, effect, inject, signal, untracked} from '@angular/core';
import {DatePipe} from '@angular/common';
import {compatForm} from '@angular/forms/signals/compat';
import {FormField} from '@angular/forms/signals';
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
import {TYPES_MOUVEMENT} from '../../core/api/mouvement-patient-api.service';
import {AppShellStore} from '../../core/state/app-shell.store';
import {EMPTY_FILTERS, MouvementsPatientsStore} from './mouvements-patients.store';
import {joursDeMouvement} from './mouvements-patients.util';

/**
 * Suivi des mouvements de patients du centre : admissions, séjours temporaires, transferts, décès, greffes,
 * guérisons et libération des places, avec l'affectation que le patient occupait au moment du mouvement.
 */
@Component({
  selector: 'app-mouvements-patients',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe, MatButtonModule, MatCardModule, MatFormFieldModule, MatIconModule, MatInputModule, MatPaginatorModule,
    MatProgressBarModule, MatSelectModule, MatTableModule, FormField, TranslateModule,
  ],
  templateUrl: './mouvements-patients.component.html',
  styleUrl: './mouvements-patients.component.css',
})
export class MouvementsPatientsComponent {
  protected readonly store = inject(MouvementsPatientsStore);
  protected readonly columns = ['date', 'patient', 'type', 'etat', 'affectation'];
  protected readonly types = TYPES_MOUVEMENT;
  protected readonly jours = joursDeMouvement;
  protected readonly filtersModel = signal({...EMPTY_FILTERS});
  protected readonly filtersForm = compatForm(this.filtersModel);
  private readonly shell = inject(AppShellStore);

  constructor() {
    effect(() => {
      // Rechargement au changement de centre actif.
      this.shell.currentCenterId();
      untracked(() => this.store.loadPage({page: 0, size: this.store.pageSize()}));
    });
  }

  protected applyFilters(): void {
    this.store.applyFilters({...this.filtersModel()});
  }

  protected resetFilters(): void {
    this.filtersModel.set({...EMPTY_FILTERS});
    this.store.applyFilters({...EMPTY_FILTERS});
  }

  protected onPage(event: PageEvent): void {
    this.store.loadPage({page: event.pageIndex, size: event.pageSize});
  }
}
