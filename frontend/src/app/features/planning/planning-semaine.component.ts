import {ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked} from '@angular/core';
import {DatePipe, NgTemplateOutlet, SlicePipe} from '@angular/common';
import {MatButtonModule} from '@angular/material/button';
import {MatButtonToggleModule} from '@angular/material/button-toggle';
import {MatCardModule} from '@angular/material/card';
import {MatDialog} from '@angular/material/dialog';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatTooltipModule} from '@angular/material/tooltip';
import {TranslateModule} from '@ngx-translate/core';
import {AbsenceSemaine} from '../../core/api/absence-patient-api.service';
import {ConflitPlanning, JOURS_SEMAINE, JourSemaine, OccupantPlanning} from '../../core/api/planning-api.service';
import {AppShellStore} from '../../core/state/app-shell.store';
import {todayIso} from '../absences/absences-patients.util';
import {PlanningPatientDialogComponent, PlanningPatientDialogData} from './planning-patient-dialog.component';
import {PlanningStore} from './planning.store';
import {
  absenceDe,
  cellule,
  etatCellule,
  jourFerme,
  jourParDefaut,
  lignesDuJour,
  peutDeclarerAbsence,
} from './planning.util';

export type VuePlanning = 'SEMAINE' | 'JOUR';

/**
 * Planning hebdomadaire réel du centre : pour chaque salle et créneau, qui dialyse quel jour et sur quel générateur,
 * en vue semaine ou en vue d'un jour. Les jours fermés sont grisés, les conflits listés ; un clic sur un patient ouvre
 * son détail et permet de déclarer son absence (la séance change alors de couleur).
 */
@Component({
  selector: 'app-planning-semaine',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe, NgTemplateOutlet, SlicePipe, MatButtonModule, MatButtonToggleModule, MatCardModule, MatIconModule,
    MatProgressBarModule, MatTooltipModule, TranslateModule,
  ],
  templateUrl: './planning-semaine.component.html',
  styleUrl: './planning-semaine.component.css',
})
export class PlanningSemaineComponent {
  protected readonly store = inject(PlanningStore);
  protected readonly jours = JOURS_SEMAINE;
  protected readonly aujourdhuiIso = todayIso();
  protected readonly vue = signal<VuePlanning>('SEMAINE');
  protected readonly lignesJour = computed(() => {
    const s = this.store.semaine();
    const jour = this.jourActif();
    return s && jour ? lignesDuJour(s, jour) : [];
  });
  protected readonly lignes = computed(() => {
    const s = this.store.semaine();
    if (!s) return [];
    return s.salles.flatMap((salle) => s.creneaux.map((creneau) => ({salle, creneau})));
  });
  private readonly jourChoisi = signal<JourSemaine | null>(null);
  /** Jour de la vue « Jour » : celui choisi, sinon aujourd'hui (s'il est dans la semaine) ou le premier jour ouvert. */
  protected readonly jourActif = computed(() => {
    const s = this.store.semaine();
    if (!s) return null;
    const choisi = this.jourChoisi();
    return choisi && s.jours.some((j) => j.jour === choisi) ? choisi : jourParDefaut(s, this.aujourdhuiIso);
  });
  protected readonly etat = etatCellule;
  private readonly shell = inject(AppShellStore);
  private readonly dialog = inject(MatDialog);

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

  protected choisirJour(jour: JourSemaine): void {
    this.jourChoisi.set(jour);
  }

  /** Absence enregistrée de ce patient pour ce jour de la semaine affichée. */
  protected absence(patientId: string, jour: JourSemaine): AbsenceSemaine | undefined {
    const date = this.jourDe(jour)?.date;
    return date ? absenceDe(this.store.absences(), patientId, date) : undefined;
  }

  /** Classe de couleur d'un patient : normal, à risque ou absent (selon le statut de l'absence). */
  protected classePatient(o: OccupantPlanning, jour: JourSemaine): string {
    const a = this.absence(o.patientId, jour);
    if (a) return `absent ${a.statut.toLowerCase()}`;
    return o.aRisque ? 'risk' : '';
  }

  protected declarable(jour: JourSemaine, patientId: string): boolean {
    const date = this.jourDe(jour)?.date;
    return !!date && !this.absence(patientId, jour) && peutDeclarerAbsence(date, this.aujourdhuiIso, this.ferme(jour));
  }

  /** Ouvre le détail du patient pour la séance cliquée (avec la déclaration d'absence si elle est possible). */
  protected ouvrirPatient(o: OccupantPlanning, salleId: string, creneauId: string, jour: JourSemaine): void {
    const date = this.jourDe(jour)?.date;
    if (!date) return;
    const data: PlanningPatientDialogData = {
      occupant: o,
      date,
      salleNom: this.salleNom(salleId),
      creneauLibelle: this.creneauLibelle(creneauId),
      absence: this.absence(o.patientId, jour),
      peutDeclarer: peutDeclarerAbsence(date, this.aujourdhuiIso, this.ferme(jour)),
    };
    this.dialog.open(PlanningPatientDialogComponent, {data, width: '480px', maxWidth: '95vw'});
  }

  protected suivante(): void {
    this.store.changerSemaine(1);
  }

  protected precedente(): void {
    this.store.changerSemaine(-1);
  }

  protected aujourdhui(): void {
    this.jourChoisi.set(null);
    this.store.chargerSemaine(null);
  }

  protected emplacement(c: ConflitPlanning): string {
    return [this.salleNom(c.salleId), this.creneauLibelle(c.creneauId)].filter(Boolean).join(' · ');
  }
}
