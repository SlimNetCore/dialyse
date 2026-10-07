import {ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked} from '@angular/core';
import {DatePipe, NgTemplateOutlet, SlicePipe} from '@angular/common';
import {RouterLink} from '@angular/router';
import {compatForm} from '@angular/forms/signals/compat';
import {FormField, FormRoot, required} from '@angular/forms/signals';
import {MatButtonModule} from '@angular/material/button';
import {MatButtonToggleModule} from '@angular/material/button-toggle';
import {MatCardModule} from '@angular/material/card';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatIconModule} from '@angular/material/icon';
import {MatInputModule} from '@angular/material/input';
import {MatPaginatorModule, PageEvent} from '@angular/material/paginator';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatSelectModule} from '@angular/material/select';
import {MatTableModule} from '@angular/material/table';
import {TranslateModule} from '@ngx-translate/core';
import {CreneauPersonnel, TYPES_ABSENCE, TypeAbsence} from '../../core/api/infirmier-api.service';
import {AbsenceSemaine} from '../../core/api/absence-patient-api.service';
import {JOURS_SEMAINE, JourSemaine, OccupantPlanning} from '../../core/api/planning-api.service';
import {AppShellStore} from '../../core/state/app-shell.store';
import {WebSocketService} from '../../core/ws/websocket.service';
import {
  absenceDe,
  classeOccupant,
  evenementRafraichitPlanning,
  jourFerme,
  seanceRealiseeDe,
} from '../planning/planning.util';
import {MonPlanningStore} from './mon-planning.store';
import {
  classeMaCase,
  jourDe,
  jourParDefautMonPlanning,
  lignesMonJour,
  lignesMonPlanning,
  maCase,
  monCreneau,
} from './mon-planning.util';
import {absenceAnnulable, aujourdhuiUtc} from './presence.util';

export type VueMonPlanning = 'SEMAINE' | 'JOUR';

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
    DatePipe, NgTemplateOutlet, SlicePipe, RouterLink, MatButtonModule, MatButtonToggleModule, MatCardModule,
    MatFormFieldModule, MatIconModule, MatInputModule, MatPaginatorModule, MatProgressBarModule, MatSelectModule,
    MatTableModule, FormRoot, FormField, TranslateModule,
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
  protected readonly jours = JOURS_SEMAINE;
  /** Sur petit écran (mobile), la vue jour est lisible d'emblée ; la grille semaine reste choisissable. */
  protected readonly vue = signal<VueMonPlanning>(
    typeof window !== 'undefined' && window.matchMedia?.('(max-width: 767px)').matches ? 'JOUR' : 'SEMAINE');
  /** Mes salles et créneaux de la semaine : les lignes de ma grille. */
  protected readonly lignes = computed(() => {
    const p = this.store.planning();
    return p ? lignesMonPlanning(p) : [];
  });
  protected readonly lignesJour = computed(() => {
    const p = this.store.planning();
    const jour = this.jourActif();
    return p && jour ? lignesMonJour(p, jour) : [];
  });
  private readonly jourChoisi = signal<JourSemaine | null>(null);
  /** Jour de la vue « Jour » : celui choisi, sinon aujourd'hui (s'il est dans la semaine) ou mon premier jour. */
  protected readonly jourActif = computed(() => {
    const p = this.store.planning();
    if (!p) return null;
    const choisi = this.jourChoisi();
    return choisi && p.jours.some((j) => j.jour === choisi) ? choisi : jourParDefautMonPlanning(p, this.aujourdhui);
  });
  protected readonly periodeValide = computed(() => {
    const {debut, fin} = this.formModel();
    return !debut || !fin || fin >= debut;
  });
  protected readonly canSave = computed(() =>
    this.absenceForm().valid() && this.periodeValide() && !this.store.saving());
  private readonly shell = inject(AppShellStore);
  private readonly ws = inject(WebSocketService);

  constructor() {
    // Recharge la semaine courante et les absences à l'ouverture et à chaque changement de centre actif.
    effect(() => {
      this.shell.currentCenterId();
      untracked(() => {
        this.store.chargerPlanning(null);
        this.store.loadAbsences({page: 0, size: this.store.pageSize()});
      });
    });
    // Temps réel : une absence de patient déclarée (même le jour J), une séance validée par le scan du QR code ou un
    // déplacement rechargent la semaine affichée, sans action de l'infirmier.
    effect(() => {
      const evenement = this.ws.lastEvent();
      const centre = this.shell.currentCenterId();
      if (!evenement || !centre || evenement.centerId !== centre) return;
      if (!evenementRafraichitPlanning(evenement.type, evenement.payload)) return;
      untracked(() => this.store.chargerPlanning(this.store.planning()?.debut ?? null));
    });
    effect(() => {
      if (!this.store.successMessage()) return;
      untracked(() => this.formModel.set(emptyAbsence()));
    });
  }

  protected jourPlanning(jour: JourSemaine) {
    const p = this.store.planning();
    return p ? jourDe(p, jour) : undefined;
  }

  protected ferme(jour: JourSemaine): boolean {
    const j = this.jourPlanning(jour);
    return !!j && jourFerme(j);
  }

  protected monCreneau(salleId: string, creneauId: string, jour: JourSemaine) {
    const p = this.store.planning();
    return p ? monCreneau(p, salleId, creneauId, jour) : undefined;
  }

  protected maCase(salleId: string, creneauId: string, jour: JourSemaine) {
    const p = this.store.planning();
    return p ? maCase(p, salleId, creneauId, jour) : undefined;
  }

  protected classe(creneau: CreneauPersonnel | undefined, jour: JourSemaine): string {
    return classeMaCase(creneau, this.ferme(jour));
  }

  /** Couleur d'un patient de ma case : séance validée, absent (barré, selon le statut de l'absence), à risque ou normal. */
  protected classePatient(o: OccupantPlanning, date: string): string {
    return classeOccupant(o, this.realisee(o.patientId, date), this.absence(o.patientId, date));
  }

  protected absence(patientId: string, date: string): AbsenceSemaine | undefined {
    return absenceDe(this.store.absencesPatients(), patientId, date);
  }

  protected realisee(patientId: string, date: string): boolean {
    return !!seanceRealiseeDe(this.store.seancesRealisees(), patientId, date);
  }

  protected choisirJour(jour: JourSemaine): void {
    this.jourChoisi.set(jour);
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
