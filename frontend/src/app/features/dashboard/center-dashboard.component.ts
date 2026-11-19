import {ChangeDetectionStrategy, Component, computed, DestroyRef, effect, inject, untracked} from '@angular/core';
import {DecimalPipe} from '@angular/common';
import {RouterLink} from '@angular/router';
import {MatCardModule} from '@angular/material/card';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {TranslateModule} from '@ngx-translate/core';
import {AuthStore} from '../../core/state/auth.store';
import {AppShellStore} from '../../core/state/app-shell.store';
import {WebSocketService} from '../../core/ws/websocket.service';
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
  private readonly tuilesDuProfil = tuilesVisibles((role) => this.auth.hasRole(role));
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

  constructor() {
    effect(() => {
      const centerId = this.appShell.currentCenterId();
      if (!centerId) return;
      untracked(() => {
        this.annulerLeRafraichissement();
        this.store.charger(centerId, this.tuilesDuProfil.map((t) => t.module));
      });
    });
    // Temps réel : un évènement du centre ne recharge que la tuile concernée ; une rafale ne déclenche qu'une lecture.
    effect(() => {
      const evenement = this.ws.lastEvent();
      const centerId = this.appShell.currentCenterId();
      if (!evenement || !centerId || evenement.centerId !== centerId) return;
      const module = moduleRafraichiPar(evenement.type);
      if (!module) return;
      untracked(() => this.planifierLeRafraichissement(module));
    });
    inject(DestroyRef).onDestroy(() => this.annulerLeRafraichissement());
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
