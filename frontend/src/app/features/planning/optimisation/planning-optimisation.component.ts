import {ChangeDetectionStrategy, Component, computed, DestroyRef, effect, inject, signal, untracked} from '@angular/core';
import {DatePipe} from '@angular/common';
import {compatForm} from '@angular/forms/signals/compat';
import {FormField, FormRoot, max, min, required} from '@angular/forms/signals';
import {MatButtonModule} from '@angular/material/button';
import {MatCardModule} from '@angular/material/card';
import {MatDialog} from '@angular/material/dialog';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatIconModule} from '@angular/material/icon';
import {MatInputModule} from '@angular/material/input';
import {MatPaginatorModule, PageEvent} from '@angular/material/paginator';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatSelectModule} from '@angular/material/select';
import {MatTableModule} from '@angular/material/table';
import {MatTabsModule} from '@angular/material/tabs';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {
  BORNES_OPTIMISATION,
  ObjectifInfirmiers,
  OBJECTIFS_INFIRMIERS,
  PERIMETRES_OPTIMISATION,
  PerimetreOptimisation,
  PosteOptimisation,
  RunOptimisation,
} from '../../../core/api/planning-optimisation-api.service';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {AuthStore} from '../../../core/state/auth.store';
import {ConfirmDialogComponent} from '../../../shared/confirm-dialog.component';
import {OptimisationStore} from './optimisation.store';
import {
  aujourdhui,
  creerPaginationLocale,
  horizonLibre,
  lignesIndicateurs,
  planifieInfirmiers,
  placePatients,
  proposeTemporaires,
  vacationsNouvelles,
} from './optimisation.util';
import {PlanningPreferencesComponent} from './planning-preferences.component';
import {PlanningProposeComponent} from './planning-propose.component';

type ParametresFormModel = {
  perimetre: PerimetreOptimisation;
  debut: string;
  nbSemaines: number;
  duree: number;
  stabilite: number;
  objectif: ObjectifInfirmiers;
  maxJour: number;
  maxSemaine: number;
};

function parametresParDefaut(): ParametresFormModel {
  return {
    perimetre: 'COMPLET',
    debut: aujourdhui(),
    nbSemaines: BORNES_OPTIMISATION.semaines.min,
    duree: BORNES_OPTIMISATION.duree.defaut,
    stabilite: BORNES_OPTIMISATION.stabilite.defaut,
    objectif: 'EQUITE',
    maxJour: BORNES_OPTIMISATION.vacationsJour.defaut,
    maxSemaine: BORNES_OPTIMISATION.vacationsSemaine.defaut,
  };
}

/**
 * Optimisation du planning du centre (moteur Timefold) : on choisit ce qu'on planifie, le calcul tourne en tâche de
 * fond, puis on compare l'état actuel à la proposition (générateurs, salles ouvertes, vacations d'infirmiers, équité)
 * avant de l'appliquer. Rien ne change tant que l'administrateur n'applique pas la proposition.
 */
@Component({
  selector: 'app-planning-optimisation',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe, MatButtonModule, MatCardModule, MatFormFieldModule, MatIconModule, MatInputModule, MatPaginatorModule,
    MatProgressBarModule, MatSelectModule, MatTableModule, MatTabsModule, FormRoot, FormField, TranslateModule,
    PlanningPreferencesComponent, PlanningProposeComponent,
  ],
  templateUrl: './planning-optimisation.component.html',
  styleUrl: './planning-optimisation.component.css',
})
export class PlanningOptimisationComponent {
  protected readonly store = inject(OptimisationStore);
  protected readonly perimetres = PERIMETRES_OPTIMISATION;
  protected readonly objectifs = OBJECTIFS_INFIRMIERS;
  protected readonly bornes = BORNES_OPTIMISATION;
  protected readonly colonnesIndicateurs = ['indicateur', 'avant', 'apres', 'ecart'];
  protected readonly colonnesDeplacements = ['patient', 'de', 'vers', 'jours'];
  protected readonly colonnesTemporaires = ['date', 'patient', 'de', 'vers', 'motif'];
  protected readonly colonnesSansSolution = ['date', 'patient', 'de', 'motif'];
  protected readonly colonnesNonPlaces = ['patient', 'cause'];
  protected readonly colonnesVacations = ['date', 'salle', 'creneau', 'infirmier', 'statut'];
  protected readonly colonnesManques = ['date', 'salle', 'creneau', 'manque'];
  protected readonly colonnesHistorique = ['date', 'perimetre', 'statut', 'deplacements', 'vacations', 'actions'];
  protected readonly tailles = [10, 20, 50, 100];

  protected readonly formModel = signal(parametresParDefaut());
  protected readonly parametresForm = compatForm(this.formModel, (form) => {
    required(form.perimetre);
    required(form.debut);
    min(form.nbSemaines, BORNES_OPTIMISATION.semaines.min);
    max(form.nbSemaines, BORNES_OPTIMISATION.semaines.max);
    min(form.duree, BORNES_OPTIMISATION.duree.min);
    max(form.duree, BORNES_OPTIMISATION.duree.max);
    min(form.stabilite, BORNES_OPTIMISATION.stabilite.min);
    max(form.stabilite, BORNES_OPTIMISATION.stabilite.max);
    min(form.maxJour, BORNES_OPTIMISATION.vacationsJour.min);
    max(form.maxJour, BORNES_OPTIMISATION.vacationsJour.max);
    min(form.maxSemaine, BORNES_OPTIMISATION.vacationsSemaine.min);
    max(form.maxSemaine, BORNES_OPTIMISATION.vacationsSemaine.max);
  });
  protected readonly horizonLibre = computed(() => horizonLibre(this.formModel().perimetre));
  protected readonly avecInfirmiers = computed(() => planifieInfirmiers(this.formModel().perimetre));
  protected readonly peutLancer = computed(() =>
    this.parametresForm().valid() && !this.store.launching() && !this.store.enCours());
  protected readonly estAdmin = computed(() => this.auth.hasRole('ADMIN'));

  // Détail de la proposition affichée.
  protected readonly resultat = computed(() => this.store.courant()?.resultat ?? null);
  protected readonly indicateurs = computed(() => {
    const r = this.resultat();
    return r ? lignesIndicateurs(r.avant, r.apres) : [];
  });
  private readonly perimetreRun = computed(() => this.store.courant()?.parametres.perimetre ?? null);
  protected readonly avecPatients = computed(() => {
    const p = this.perimetreRun();
    return !!p && placePatients(p);
  });
  protected readonly avecInfirmiersRun = computed(() => {
    const p = this.perimetreRun();
    return !!p && planifieInfirmiers(p);
  });
  protected readonly avecTemporaires = computed(() => {
    const p = this.perimetreRun();
    return !!p && proposeTemporaires(p);
  });
  protected readonly horizonLibreRun = computed(() => {
    const p = this.perimetreRun();
    return !!p && horizonLibre(p);
  });
  protected readonly salles = computed(() => new Map((this.resultat()?.salles ?? []).map((s) => [s.id, s.nom])));
  protected readonly creneaux = computed(() => new Map((this.resultat()?.creneaux ?? []).map((c) => [c.id, c.libelle])));
  protected readonly vacationsNouvelles = computed(() => vacationsNouvelles(this.resultat()?.vacations ?? []));
  protected readonly pageDeplacements = creerPaginationLocale(computed(() => this.resultat()?.deplacements ?? []));
  protected readonly pageNonPlaces = creerPaginationLocale(computed(() => this.resultat()?.nonPlaces ?? []));
  protected readonly pageVacations = creerPaginationLocale(this.vacationsNouvelles);
  protected readonly pageManques = creerPaginationLocale(computed(() => this.resultat()?.manques ?? []));
  protected readonly pageTemporaires = creerPaginationLocale(computed(() => this.resultat()?.temporaires ?? []));
  protected readonly pageSansSolution = creerPaginationLocale(
    computed(() => this.resultat()?.seancesSansSolution ?? []));

  private readonly auth = inject(AuthStore);
  private readonly shell = inject(AppShellStore);
  private readonly dialog = inject(MatDialog);
  private readonly translate = inject(TranslateService);

  constructor() {
    // Changement de centre : on repart d'un écran vierge et on recharge l'historique du nouveau centre.
    effect(() => {
      this.shell.currentCenterId();
      untracked(() => {
        this.store.reinitialiser();
        this.store.chargerHistorique({page: 0, size: this.store.pageSize()});
      });
    });
    // Proposition terminée affichée : on charge son planning calendaire (une fois par exécution consultée).
    effect(() => {
      const run = this.store.courant();
      if (run?.statut === 'TERMINEE' && run.resultat && !untracked(() => this.store.calendrier())) {
        untracked(() => void this.store.chargerCalendrier(null));
      }
    });
    // Quitter l'écran n'interrompt pas le calcul (il continue côté serveur) mais arrête la lecture périodique.
    inject(DestroyRef).onDestroy(() => this.store.reinitialiser());
  }

  protected lancer(): void {
    if (!this.peutLancer()) return;
    const m = this.formModel();
    void this.store.lancer({
      perimetre: m.perimetre,
      debutSemaine: m.debut,
      nbSemaines: horizonLibre(m.perimetre) ? Number(m.nbSemaines) : 1,
      dureeMaxSecondes: Number(m.duree),
      stabilite: Number(m.stabilite),
      objectif: m.objectif,
      maxVacationsParJour: Number(m.maxJour),
      maxVacationsParSemaine: Number(m.maxSemaine),
    });
  }

  protected arreter(): void {
    void this.store.arreter();
  }

  protected appliquer(): void {
    const run = this.store.courant();
    if (!run || !this.store.appliquable()) return;
    const ref = this.dialog.open(ConfirmDialogComponent, {
      width: 'min(96vw, 480px)',
      data: {
        title: this.translate.instant('PLANNING.OPTIM.CONFIRM.TITLE'),
        message: this.translate.instant(`PLANNING.OPTIM.CONFIRM.MESSAGE_${run.parametres.perimetre}`),
        confirmLabel: this.translate.instant('PLANNING.OPTIM.APPLIQUER'),
        cancelLabel: this.translate.instant('COMMON.CANCEL'),
        color: 'primary',
        icon: 'task_alt',
      },
    });
    ref.afterClosed().subscribe((confirme) => {
      if (confirme) void this.store.appliquer();
    });
  }

  protected choisirSemaine(semaine: string): void {
    void this.store.chargerCalendrier(semaine);
  }

  protected imprimerCalendrier(): void {
    void this.store.imprimerCalendrier();
  }

  protected ouvrir(run: RunOptimisation): void {
    void this.store.ouvrir(run.id);
  }

  protected supprimer(run: RunOptimisation): void {
    if (!this.estAdmin() || run.statut === 'EN_COURS') return;
    const ref = this.dialog.open(ConfirmDialogComponent, {
      width: 'min(96vw, 480px)',
      data: {
        title: this.translate.instant('PLANNING.OPTIM.SUPPRIMER_CONFIRM.TITLE'),
        message: this.translate.instant(
          run.appliqueLe ? 'PLANNING.OPTIM.SUPPRIMER_CONFIRM.MESSAGE_APPLIQUEE' : 'PLANNING.OPTIM.SUPPRIMER_CONFIRM.MESSAGE'),
        confirmLabel: this.translate.instant('PLANNING.OPTIM.SUPPRIMER'),
        cancelLabel: this.translate.instant('COMMON.CANCEL'),
        color: 'warn',
        icon: 'delete',
      },
    });
    ref.afterClosed().subscribe((confirme) => {
      if (confirme) void this.store.supprimer(run.id);
    });
  }

  protected onPageHistorique(event: PageEvent): void {
    this.store.setPagination(event.pageIndex, event.pageSize);
  }

  protected salle(id: string | null): string {
    return (id && this.salles().get(id)) || '—';
  }

  protected creneau(id: string | null): string {
    return (id && this.creneaux().get(id)) || '—';
  }

  /** « Salle · créneau · générateur » d'une place, ou un tiret pour un patient jusqu'ici non placé. */
  protected poste(poste: PosteOptimisation | null): string {
    if (!poste?.salleId) return '—';
    return [this.salle(poste.salleId), this.creneau(poste.creneauId), poste.generateurCode ?? '—'].join(' · ');
  }
}
