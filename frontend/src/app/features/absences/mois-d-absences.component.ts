import {ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked} from '@angular/core';
import {DatePipe} from '@angular/common';
import {MatButtonModule} from '@angular/material/button';
import {MatCardModule} from '@angular/material/card';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatIconModule} from '@angular/material/icon';
import {MatInputModule} from '@angular/material/input';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatSelectModule} from '@angular/material/select';
import {MatTooltipModule} from '@angular/material/tooltip';
import {TranslateModule} from '@ngx-translate/core';
import {AppShellStore} from '../../core/state/app-shell.store';
import {AuthStore} from '../../core/state/auth.store';
import {MoisDAbsencesStore} from './mois-d-absences.store';
import {MOTIF_REOUVERTURE_MAX, moisClosable, motifDeReouvertureValide} from './mois-d-absences.util';

/**
 * Gestion des mois d'absences du centre (A6, ADR 0042) : état des douze mois de l'année, clôture avec avertissement chiffré
 * (absences encore à qualifier, dont en retard) et réouverture avec motif obligatoire. Clôturer et rouvrir sont réservés à
 * l'administrateur ; seul un mois passé se clôture ; un mois clos refuse les écritures d'absence de ce mois, rien n'oblige à
 * respecter l'ordre avec la clôture de la facturation (l'ordre recommandé est : absences, puis facturation).
 */
@Component({
  selector: 'app-mois-d-absences',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe, MatButtonModule, MatCardModule, MatFormFieldModule, MatIconModule, MatInputModule, MatProgressBarModule,
    MatSelectModule, MatTooltipModule, TranslateModule,
  ],
  templateUrl: './mois-d-absences.component.html',
  styleUrl: './mois-d-absences.component.css',
})
export class MoisDAbsencesComponent {
  protected readonly store = inject(MoisDAbsencesStore);
  protected readonly motifMax = MOTIF_REOUVERTURE_MAX;
  protected readonly anneeOptions = computed(() => {
    const courante = new Date().getFullYear();
    return [courante - 2, courante - 1, courante].filter((a) => a >= 2000);
  });
  protected readonly clotureAConfirmer = signal<string | null>(null);
  protected readonly reouvertureDraft = signal<{ mois: string; motif: string } | null>(null);
  protected readonly reouvertureValide = computed(() => {
    const draft = this.reouvertureDraft();
    return !!draft && motifDeReouvertureValide(draft.motif) !== '';
  });
  protected readonly occupe = computed(() => this.store.actionMois() !== null);
  private readonly auth = inject(AuthStore);
  /** Clôturer et rouvrir : administrateur seulement (le serveur applique la même règle). */
  protected readonly estAdmin = computed(() => this.auth.hasRole('ADMIN'));
  private readonly shell = inject(AppShellStore);

  constructor() {
    effect(() => {
      // Rechargement au changement de centre actif.
      this.shell.currentCenterId();
      untracked(() => this.store.charger({annee: this.store.annee()}));
    });
  }

  /** Une action sur ce mois est possible : administrateur, aucune action en cours. */
  protected peutClore(mois: string): boolean {
    return this.estAdmin() && !this.occupe() && moisClosable(mois);
  }

  protected peutRouvrir(): boolean {
    return this.estAdmin() && !this.occupe();
  }

  /** Raison pour laquelle la clôture est désactivée (infobulle). */
  protected raisonDeLaCloture(mois: string): string {
    if (!this.estAdmin()) return 'ABSENCES.MOIS.REQUIERT_ADMIN';
    return moisClosable(mois) ? '' : 'ABSENCES.MOIS.NON_TERMINE';
  }

  protected onAnnee(annee: number): void {
    this.clotureAConfirmer.set(null);
    this.reouvertureDraft.set(null);
    this.store.setAnnee(annee);
  }

  protected onRafraichir(): void {
    this.store.charger({annee: this.store.annee()});
  }

  protected onDemanderCloture(mois: string): void {
    this.store.effacerLesMessages();
    this.reouvertureDraft.set(null);
    this.clotureAConfirmer.set(mois);
  }

  protected onAnnulerCloture(): void {
    this.clotureAConfirmer.set(null);
  }

  protected onConfirmerCloture(): void {
    const mois = this.clotureAConfirmer();
    if (!mois || !this.peutClore(mois)) return;
    this.clotureAConfirmer.set(null);
    this.store.cloturer({mois});
  }

  protected onOuvrirReouverture(mois: string): void {
    this.store.effacerLesMessages();
    this.clotureAConfirmer.set(null);
    this.reouvertureDraft.set({mois, motif: ''});
  }

  protected onAnnulerReouverture(): void {
    this.reouvertureDraft.set(null);
  }

  protected onMotif(motif: string): void {
    this.reouvertureDraft.update((draft) => (draft ? {...draft, motif} : draft));
  }

  protected onConfirmerReouverture(): void {
    const draft = this.reouvertureDraft();
    if (!draft || !this.peutRouvrir()) return;
    const motif = motifDeReouvertureValide(draft.motif);
    if (!motif) return;
    this.reouvertureDraft.set(null);
    this.store.rouvrir({mois: draft.mois, motif});
  }
}
