import {ChangeDetectionStrategy, Component, computed, effect, inject, untracked} from '@angular/core';
import {DatePipe, SlicePipe} from '@angular/common';
import {MatButtonModule} from '@angular/material/button';
import {MatCardModule} from '@angular/material/card';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatTooltipModule} from '@angular/material/tooltip';
import {TranslateModule} from '@ngx-translate/core';
import {ConflitPlanning, JOURS_SEMAINE, JourSemaine} from '../../core/api/planning-api.service';
import {AppShellStore} from '../../core/state/app-shell.store';
import {PlanningStore} from './planning.store';
import {cellule, etatCellule, jourFerme} from './planning.util';

/**
 * Planning hebdomadaire réel du centre : pour chaque salle et créneau, qui dialyse quel jour et sur quel générateur.
 * Les jours fermés (fermeture hebdomadaire, férié, fermeture exceptionnelle) sont grisés et les conflits listés.
 */
@Component({
  selector: 'app-planning-semaine',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe, SlicePipe, MatButtonModule, MatCardModule, MatIconModule, MatProgressBarModule, MatTooltipModule,
    TranslateModule,
  ],
  templateUrl: './planning-semaine.component.html',
  styleUrl: './planning-semaine.component.css',
})
export class PlanningSemaineComponent {
  protected readonly store = inject(PlanningStore);
  protected readonly jours = JOURS_SEMAINE;
  protected readonly lignes = computed(() => {
    const s = this.store.semaine();
    if (!s) return [];
    return s.salles.flatMap((salle) => s.creneaux.map((creneau) => ({salle, creneau})));
  });
  protected readonly etat = etatCellule;
  private readonly shell = inject(AppShellStore);

  constructor() {
    // Recharge la semaine courante à l'ouverture et à chaque changement de centre actif.
    effect(() => {
      this.shell.currentCenterId();
      untracked(() => this.store.chargerSemaine(null));
    });
  }

  protected readonly jourDe = (jour: JourSemaine) => this.store.semaine()?.jours.find((j) => j.jour === jour);

  protected readonly ferme = (jour: JourSemaine): boolean => {
    const j = this.jourDe(jour);
    return !!j && jourFerme(j);
  };

  protected cellule(salleId: string, creneauId: string, jour: JourSemaine) {
    const s = this.store.semaine();
    return s ? cellule(s, salleId, creneauId, jour) : undefined;
  }

  protected salleNom(id: string | null): string {
    return this.store.semaine()?.salles.find((s) => s.id === id)?.nom ?? '';
  }

  protected creneauLibelle(id: string | null): string {
    return this.store.semaine()?.creneaux.find((c) => c.id === id)?.libelle ?? '';
  }

  protected suivante(): void {
    this.store.changerSemaine(1);
  }

  protected precedente(): void {
    this.store.changerSemaine(-1);
  }

  protected aujourdhui(): void {
    this.store.chargerSemaine(null);
  }

  protected emplacement(c: ConflitPlanning): string {
    return [this.salleNom(c.salleId), this.creneauLibelle(c.creneauId)].filter(Boolean).join(' · ');
  }
}
