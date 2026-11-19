import {ChangeDetectionStrategy, Component, computed, DestroyRef, effect, inject, signal, untracked} from '@angular/core';
import {DatePipe} from '@angular/common';
import {MatButtonModule} from '@angular/material/button';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatIconModule} from '@angular/material/icon';
import {MatInputModule} from '@angular/material/input';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatSelectModule} from '@angular/material/select';
import {MatTooltipModule} from '@angular/material/tooltip';
import {TranslateModule} from '@ngx-translate/core';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {FacturationStore} from '../state/facturation.store';
import {
  estSuccesDeCloture,
  moisClosable,
  MOTIF_REOUVERTURE_MAX,
  motifDeReouvertureValide,
} from '../state/facturation-periodes';

/**
 * Clôture / réouverture mensuelle de la facturation (F7, ADR 0044), extraite de l'espace de facturation et affichée dans
 * Administration › Paramétrage › Clôtures mensuelles. Même store, mêmes appels serveur (réservés à l'ADMIN) : seul un
 * mois terminé se clôture, la réouverture exige un motif ; l'avertissement de clôture (séances restantes, absences non
 * closes) s'affiche ici.
 */
@Component({
  selector: 'app-facturation-cloture',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe, MatButtonModule, MatFormFieldModule, MatIconModule, MatInputModule, MatProgressBarModule, MatSelectModule,
    MatTooltipModule, TranslateModule,
  ],
  templateUrl: './facturation-cloture.component.html',
  styleUrl: './facturation-cloture.component.css',
})
export class FacturationClotureComponent {
  protected readonly store = inject(FacturationStore);
  private readonly appShell = inject(AppShellStore);
  protected readonly currentCenterId = computed(() => this.appShell.currentCenterId());

  /** ADR 0044 : seul un mois passé se clôture (bouton désactivé sinon ; le serveur reste juge). */
  protected readonly moisClosable = moisClosable;
  protected readonly motifMax = MOTIF_REOUVERTURE_MAX;
  protected readonly clotureAConfirmer = signal<string | null>(null);
  protected readonly reouvertureDraft = signal<{ mois: string; motif: string } | null>(null);
  protected readonly reouvertureValide = computed(() => {
    const draft = this.reouvertureDraft();
    return !!draft && motifDeReouvertureValide(draft.motif) !== '';
  });
  /** Message de succès d'une clôture ou d'une réouverture (les autres messages du store concernent l'espace de calcul). */
  protected readonly succesDeCloture = computed(() => {
    const message = this.store.successMessage();
    return estSuccesDeCloture(message) ? message : null;
  });
  protected readonly anneeOptions = computed(() => {
    const courante = new Date().getUTCFullYear();
    const choisie = this.store.anneePeriodes();
    const debut = Math.min(courante - 4, choisie);
    const fin = Math.max(courante + 1, choisie);
    return Array.from({length: fin - debut + 1}, (_, i) => debut + i);
  });

  constructor() {
    // Le store est partagé avec l'espace de facturation et le paramétrage : on n'hérite pas de leurs messages en
    // arrivant, et on ne leur laisse pas ceux de la clôture en partant.
    this.store.clearError();
    this.store.clearSuccess();
    inject(DestroyRef).onDestroy(() => {
      this.store.clearError();
      if (estSuccesDeCloture(this.store.successMessage())) {
        this.store.clearSuccess();
      }
    });

    // Charge les mois de l'année affichée (une fois par année et par centre).
    effect(() => {
      const centerId = this.currentCenterId();
      const annee = this.store.anneePeriodes();
      if (!centerId || this.store.anneesChargees().includes(annee)) {
        return;
      }
      untracked(() => this.store.loadPeriodes({centerId, annee}));
    });
  }

  protected onAnneePeriodes(annee: number): void {
    this.clotureAConfirmer.set(null);
    this.reouvertureDraft.set(null);
    this.store.setAnneePeriodes(annee);
  }

  protected onRafraichirPeriodes(): void {
    const centerId = this.currentCenterId();
    if (centerId) {
      this.store.loadPeriodes({centerId, annee: this.store.anneePeriodes()});
    }
  }

  protected onDemanderCloture(mois: string): void {
    this.reouvertureDraft.set(null);
    this.clotureAConfirmer.set(mois);
  }

  protected onAnnulerCloture(): void {
    this.clotureAConfirmer.set(null);
  }

  protected onConfirmerCloture(): void {
    const centerId = this.currentCenterId();
    const mois = this.clotureAConfirmer();
    if (!centerId || !mois) {
      return;
    }
    this.store.cloturerMois({centerId, mois});
    this.clotureAConfirmer.set(null);
  }

  protected onOuvrirReouverture(mois: string): void {
    this.clotureAConfirmer.set(null);
    this.reouvertureDraft.set({mois, motif: ''});
  }

  protected onAnnulerReouverture(): void {
    this.reouvertureDraft.set(null);
  }

  protected onMotifReouverture(motif: string): void {
    this.reouvertureDraft.update((draft) => (draft ? {...draft, motif} : draft));
  }

  protected onConfirmerReouverture(): void {
    const centerId = this.currentCenterId();
    const draft = this.reouvertureDraft();
    const motif = draft ? motifDeReouvertureValide(draft.motif) : '';
    if (!centerId || !draft || !motif) {
      return;
    }
    this.store.rouvrirMois({centerId, mois: draft.mois, motif});
    this.reouvertureDraft.set(null);
  }
}
