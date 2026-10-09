import {ChangeDetectionStrategy, Component, computed, inject, input, output, signal} from '@angular/core';
import {DatePipe, NgTemplateOutlet, SlicePipe} from '@angular/common';
import {MatButtonModule} from '@angular/material/button';
import {MatButtonToggleModule} from '@angular/material/button-toggle';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatTooltipModule} from '@angular/material/tooltip';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {
  CalendrierProposition,
  JourCalendrier,
  PatientCalendrier,
} from '../../../core/api/planning-optimisation-api.service';
import {
  classeJour,
  entetesJours,
  finDeSemaine,
  jourParDefaut,
  lignesDuJour,
  semaineVoisine,
} from './calendrier-proposition.util';

export type VuePlanningPropose = 'SEMAINE' | 'JOUR';

/**
 * Planning calendaire d'une proposition d'optimisation, comme celui des séances : semaine par semaine, salle et
 * créneau en lignes, jours en colonnes, avec patients (et générateur), infirmiers et effectif de chaque case. Vue
 * semaine (grille) ou vue jour (cartes, lisible sur mobile) ; impression par le modèle de document du centre.
 */
@Component({
  selector: 'app-planning-propose',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe, NgTemplateOutlet, SlicePipe, MatButtonModule, MatButtonToggleModule, MatIconModule,
    MatProgressBarModule, MatTooltipModule, TranslateModule,
  ],
  templateUrl: './planning-propose.component.html',
  styleUrl: './planning-propose.component.css',
})
export class PlanningProposeComponent {
  readonly calendrier = input.required<CalendrierProposition | null>();
  readonly loading = input(false);
  readonly printing = input(false);
  /** Aujourd'hui (AAAA-MM-JJ), pour choisir le jour affiché par défaut ; surchargeable pour les tests. */
  readonly aujourdhui = input(new Date().toISOString().slice(0, 10));

  readonly semaineChoisie = output<string>();
  readonly imprimer = output<void>();

  /** Sur petit écran (mobile), la vue jour est lisible d'emblée ; la grille semaine reste choisissable. */
  protected readonly vue = signal<VuePlanningPropose>(
    typeof window !== 'undefined' && window.matchMedia?.('(max-width: 767px)').matches ? 'JOUR' : 'SEMAINE');
  protected readonly cases = computed(() => this.calendrier()?.cases ?? []);
  protected readonly semaines = computed(() => this.calendrier()?.semaines ?? []);
  protected readonly semaine = computed(() => this.calendrier()?.semaine ?? null);
  protected readonly fin = computed(() => {
    const debut = this.semaine();
    return debut ? finDeSemaine(debut) : null;
  });
  protected readonly entetes = computed(() => entetesJours(this.cases()));
  protected readonly precedente = computed(() => semaineVoisine(this.semaines(), this.semaine(), -1));
  protected readonly suivante = computed(() => semaineVoisine(this.semaines(), this.semaine(), 1));
  /** Rang (à partir de 1) de la semaine affichée parmi celles de la proposition, pour « Semaine 2 sur 4 ». */
  protected readonly rang = computed(() => this.semaines().indexOf(this.semaine() ?? '') + 1);
  protected readonly lignesJour = computed(() => lignesDuJour(this.cases(), this.jourActif()));
  protected readonly classe = classeJour;
  private readonly translate = inject(TranslateService);
  private readonly jourChoisi = signal<number | null>(null);
  /** Jour de la vue « Jour » : celui choisi, sinon aujourd'hui s'il a de l'activité ou le premier jour actif. */
  protected readonly jourActif = computed(() => this.jourChoisi() ?? jourParDefaut(this.cases(), this.aujourdhui()));

  protected choisirJour(index: number): void {
    this.jourChoisi.set(index);
  }

  protected allerA(semaine: string | null): void {
    if (!semaine) return;
    this.jourChoisi.set(null);
    this.semaineChoisie.emit(semaine);
  }

  protected jour(ligne: { jours: JourCalendrier[] }, index: number): JourCalendrier | undefined {
    return ligne.jours[index];
  }

  /** Infirmiers affectés à la case ce jour-là (les absents ne comptent pas). */
  protected affectes(jour: JourCalendrier): number {
    return jour.infirmiers.filter((i) => i.situation !== 'ABSENT').length;
  }

  /** Infobulle d'un patient déplacé ou placé temporairement : où il était avant ; vide pour les autres. */
  protected placeAvant(p: PatientCalendrier): string {
    if (!p.deplace && !p.temporaire) return '';
    const cle = p.temporaire ? 'PLANNING.OPTIM.CALENDRIER.HABITUELLE'
      : p.avant ? 'PLANNING.OPTIM.CALENDRIER.AVANT' : 'PLANNING.OPTIM.CALENDRIER.AVANT_AUCUNE';
    return this.translate.instant(cle, {place: p.avant ?? ''});
  }
}
