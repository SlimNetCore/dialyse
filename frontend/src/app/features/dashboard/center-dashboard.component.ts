import {ChangeDetectionStrategy, Component, computed, DestroyRef, effect, inject, untracked} from '@angular/core';
import {DecimalPipe} from '@angular/common';
import {RouterLink} from '@angular/router';
import {MatCardModule} from '@angular/material/card';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {TranslateModule} from '@ngx-translate/core';
import {AuthStore} from '../../core/state/auth.store';
import {AppShellStore} from '../../core/state/app-shell.store';
import {WebSocketService, WsEvent} from '../../core/ws/websocket.service';
import {AccueilStore} from './state/accueil.store';
import {AccueilModule, DELAI_RAFRAICHISSEMENT_MS, moduleRafraichiPar, tuilesVisibles} from './accueil-tuiles';

/**
 * Accueil du centre (URL /dashboard conservée) : une tuile par module accessible au profil, avec 1 ou 2 chiffres clés et
 * le lien vers le tableau de bord du module. Les indicateurs détaillés (PEC, attestations…) vivent dans ces tableaux de
 * bord ; l'Accueil n'en garde aucun en double.
 */
@Component({
  selector: 'app-center-dashboard',
  standalone: true,
  imports: [
    DecimalPipe,
    RouterLink,
    MatCardModule,
    MatIconModule,
    MatProgressSpinnerModule,
    TranslateModule,
  ],
  providers: [AccueilStore],
  templateUrl: './center-dashboard.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './center-dashboard.component.css',
})
export class CenterDashboardComponent {
  private readonly store = inject(AccueilStore);
  private readonly auth = inject(AuthStore);
  private readonly appShell = inject(AppShellStore);
  private readonly ws = inject(WebSocketService);

  /** Tuiles du profil (règle de rôle du menu) : un module absent du menu n'a pas de tuile et n'est jamais appelé. */
  private readonly tuilesDuProfil = tuilesVisibles((role) => this.auth.hasRole(role), this.auth.roles());
  /** Tuiles affichées : celles du profil, moins les modules dont le serveur a refusé la lecture (403). */
  readonly tuiles = computed(() => {
    const nonAccessibles = this.store.nonAccessibles();
    return this.tuilesDuProfil.filter((tuile) => !nonAccessibles[tuile.module]);
  });
  readonly valeurs = this.store.valeurs;
  readonly chargement = this.store.chargement;
  readonly displayName = () => this.auth.fullName() || this.auth.username() || '';

  /** Modules à recharger à la fin du délai de regroupement des évènements temps réel. */
  private readonly aRafraichir = new Set<AccueilModule>();
  private minuterie: ReturnType<typeof setTimeout> | null = null;

  /**
   * Évènement temps réel le plus récent déjà pris en compte. Le journal du service est racine et n'est jamais vidé :
   * les évènements présents au montage, ou antérieurs au dernier chargement complet du centre, sont couverts par ce
   * chargement et ne doivent pas déclencher une seconde lecture.
   */
  private dernierEvenementVu: WsEvent | null = untracked(() => this.ws.events()[0] ?? null);

  constructor() {
    effect(() => {
      const centerId = this.appShell.currentCenterId();
      if (!centerId) return;
      untracked(() => {
        this.annulerLeRafraichissement();
        this.dernierEvenementVu = this.ws.events()[0] ?? null;
        this.store.charger(centerId, this.tuilesDuProfil.map((t) => t.module));
      });
    });
    // Temps réel : un évènement du centre ne recharge que la tuile concernée ; une rafale ne déclenche qu'une lecture.
    // L'effet lit le journal `events` et non `lastEvent` : deux messages reçus avant l'exécution de l'effet y restent
    // tous deux visibles, alors que `lastEvent` ne garderait que le dernier (le rafraîchissement du premier module
    // serait perdu). Il ne dépend pas du centre courant : un changement de centre ne rejoue aucun évènement.
    // Limite : le journal garde 50 évènements ; au-delà de 50 messages entre deux exécutions de l'effet, les plus
    // anciens sont perdus (le service n'expose aucun flux d'évènements).
    effect(() => {
      const evenements = this.ws.events();
      untracked(() => {
        const nouveaux = this.evenementsNonVus(evenements);
        this.dernierEvenementVu = evenements[0] ?? null;
        const centerId = this.appShell.currentCenterId();
        if (!centerId) return;
        for (const evenement of nouveaux) {
          if (evenement.centerId !== centerId) continue;
          const module = moduleRafraichiPar(evenement.type);
          if (module) this.planifierLeRafraichissement(module);
        }
      });
    });
    inject(DestroyRef).onDestroy(() => this.annulerLeRafraichissement());
  }

  /** Évènements du journal (du plus récent au plus ancien) arrivés après le dernier déjà pris en compte. */
  private evenementsNonVus(evenements: ReadonlyArray<WsEvent>): ReadonlyArray<WsEvent> {
    if (!this.dernierEvenementVu) return evenements;
    const rang = evenements.indexOf(this.dernierEvenementVu);
    return rang < 0 ? evenements : evenements.slice(0, rang);
  }

  private planifierLeRafraichissement(module: AccueilModule): void {
    const affichee = this.tuiles().some((tuile) => tuile.module === module);
    if (!affichee) return;
    this.aRafraichir.add(module);
    this.minuterie ??= setTimeout(() => {
      const modules = [...this.aRafraichir];
      this.annulerLeRafraichissement();
      const centerId = this.appShell.currentCenterId();
      if (centerId) this.store.charger(centerId, modules, new Date(), {silencieux: true});
    }, DELAI_RAFRAICHISSEMENT_MS);
  }

  private annulerLeRafraichissement(): void {
    if (this.minuterie !== null) clearTimeout(this.minuterie);
    this.minuterie = null;
    this.aRafraichir.clear();
  }
}
