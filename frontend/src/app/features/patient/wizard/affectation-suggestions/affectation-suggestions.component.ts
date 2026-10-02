import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  untracked
} from '@angular/core';
import {DatePipe, SlicePipe} from '@angular/common';
import {MatButtonModule} from '@angular/material/button';
import {MatCheckboxModule} from '@angular/material/checkbox';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatSelectModule} from '@angular/material/select';
import {TranslateModule} from '@ngx-translate/core';
import {
  JourSemaine,
  PlanningApiService,
  PropositionAffectation,
  PropositionsAffectation,
} from '../../../../core/api/planning-api.service';
import {AppShellStore} from '../../../../core/state/app-shell.store';
import {etatCase, trouverCase} from './affectation-suggestions.util';

/**
 * Aide au placement d'un nouveau patient : propose les salles ayant une place, les créneaux et les générateurs
 * libres, et affiche la grille de disponibilité du centre. Les jours cochés sur la fiche sont imposés ; la salle et
 * le créneau déjà choisis servent de préférence. « Appliquer » renseigne la fiche (salle, créneau, générateur, jours).
 */
@Component({
  selector: 'app-affectation-suggestions',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe, SlicePipe, MatButtonModule, MatCheckboxModule, MatFormFieldModule, MatIconModule, MatProgressBarModule, MatSelectModule, TranslateModule,
  ],
  templateUrl: './affectation-suggestions.component.html',
  styleUrl: './affectation-suggestions.component.css',
})
export class AffectationSuggestionsComponent {
  /** Patient dont on modifie le placement : sa place actuelle est comptée comme libre. */
  readonly patientId = input<string | null>(null);
  /** Jours souhaités : imposés à la recherche (vide : le système choisit les jours les mieux espacés). */
  protected readonly joursImposes = signal<readonly JourSemaine[]>([]);
  /** Créneau et salle souhaités : simples préférences qui améliorent le classement des propositions. */
  protected readonly positionId = signal<string | null>(null);
  protected readonly salleId = signal<string | null>(null);

  readonly appliquer = output<PropositionAffectation>();

  protected readonly seances = signal(3);
  protected readonly nbSeancesOptions = [1, 2, 3, 4, 5, 6];
  protected readonly loading = signal(false);
  protected readonly error = signal(false);
  protected readonly resultat = signal<PropositionsAffectation | null>(null);
  protected readonly appliquee = signal<PropositionAffectation | null>(null);
  protected readonly jours: readonly JourSemaine[] = ['DIMANCHE', 'LUNDI', 'MARDI', 'MERCREDI', 'JEUDI', 'VENDREDI', 'SAMEDI'];
  /** Risque infectieux forcé par l'utilisateur ; null = déduit des sérologies du patient. */
  protected readonly isolementForce = signal<boolean | null>(null);
  protected readonly aRisque = computed(() => this.isolementForce() ?? this.resultat()?.patientARisque ?? false);
  protected readonly joursImposesActifs = computed(() => this.joursImposes().length > 0);
  protected readonly propositions = computed(() => this.resultat()?.propositions ?? []);
  protected readonly lignesGrille = computed(() => {
    const r = this.resultat();
    if (!r) return [];
    return r.salles.flatMap((salle) => r.creneaux.map((creneau) => ({salle, creneau})));
  });
  protected readonly etatCase = etatCase;
  private readonly api = inject(PlanningApiService);
  private readonly shell = inject(AppShellStore);

  constructor() {
    // Recherche automatique à l'ouverture et dès que les critères de la fiche changent (réponse rapide).
    effect(() => {
      this.joursImposes();
      this.salleId();
      this.positionId();
      this.seances();
      this.isolementForce();
      untracked(() => this.rechercher());
    });
  }

  protected rechercher(): void {
    const centerId = this.shell.currentCenterId();
    if (!centerId) return;
    this.loading.set(true);
    this.error.set(false);
    this.api.propositions(centerId, {
      seances: this.seances(),
      jours: [...this.joursImposes()],
      creneauId: this.positionId(),
      salleId: this.salleId(),
      patientId: this.patientId(),
      isolement: this.isolementForce(),
      limite: 12,
    }).subscribe({
      next: (r) => {
        this.resultat.set(r);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      },
    });
  }

  protected basculerJour(jour: JourSemaine, coche: boolean): void {
    this.joursImposes.update((jours) => coche ? [...jours, jour] : jours.filter((j) => j !== jour));
  }

  protected choisirCreneau(id: string | null): void {
    this.positionId.set(id || null);
  }

  protected choisirSalle(id: string | null): void {
    this.salleId.set(id || null);
  }

  protected basculerIsolement(coche: boolean): void {
    this.isolementForce.set(coche);
  }

  protected choisirSeances(value: number): void {
    this.seances.set(value);
  }

  protected appliquerProposition(p: PropositionAffectation): void {
    this.appliquee.set(p);
    this.appliquer.emit(p);
  }

  protected estAppliquee(p: PropositionAffectation): boolean {
    const a = this.appliquee();
    return !!a && a.salle.id === p.salle.id && a.creneau.id === p.creneau.id
      && a.generateur.id === p.generateur.id && a.jours.join() === p.jours.join();
  }

  protected caseDe(salleId: string, creneauId: string, jour: JourSemaine) {
    const r = this.resultat();
    return r ? trouverCase(r.grille, salleId, creneauId, jour) : undefined;
  }

  protected libres(salleId: string, creneauId: string, jour: JourSemaine): number {
    const c = this.caseDe(salleId, creneauId, jour);
    return c ? Math.max(0, c.capacite - c.occupes) : 0;
  }
}
