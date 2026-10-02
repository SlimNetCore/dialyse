import {ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {MatCardModule} from '@angular/material/card';
import {MatCheckboxModule} from '@angular/material/checkbox';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {TranslateModule} from '@ngx-translate/core';
import {JOURS_SEMAINE, JourSemaine} from '../../core/api/planning-api.service';
import {AppShellStore} from '../../core/state/app-shell.store';
import {SallesStore} from '../../core/state/referentials.store';
import {PlanningStore} from './planning.store';

/**
 * Paramétrage du planning du centre : jours où l'on dialyse et salles d'isolement réservées aux patients à risque
 * infectieux (VHB, VHC, VIH). Ces réglages pilotent l'aide au placement et le planning hebdomadaire.
 */
@Component({
  selector: 'app-planning-parametres',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButtonModule, MatCardModule, MatCheckboxModule, MatIconModule, MatProgressBarModule, TranslateModule],
  templateUrl: './planning-parametres.component.html',
  styleUrl: './planning-parametres.component.css',
})
export class PlanningParametresComponent {
  protected readonly store = inject(PlanningStore);
  protected readonly sallesStore = inject(SallesStore);
  protected readonly jours = JOURS_SEMAINE;
  protected readonly joursOuverts = signal<ReadonlySet<JourSemaine>>(new Set(JOURS_SEMAINE));
  protected readonly sallesIsolement = signal<ReadonlySet<string>>(new Set());
  protected readonly canSave = computed(() => this.joursOuverts().size > 0 && !this.store.saving());
  private readonly shell = inject(AppShellStore);

  constructor() {
    effect(() => {
      const centerId = this.shell.currentCenterId();
      untracked(() => {
        void this.sallesStore.ensureLoaded(centerId);
        this.store.chargerParametres();
      });
    });
    // Recopie les paramètres du serveur dans le formulaire local dès qu'ils sont chargés ou enregistrés.
    effect(() => {
      const p = this.store.parametres();
      if (!p) return;
      this.joursOuverts.set(new Set(p.joursOuverts));
      this.sallesIsolement.set(new Set(p.sallesIsolement));
    });
  }

  protected basculerJour(jour: JourSemaine, coche: boolean): void {
    this.joursOuverts.update((s) => bascule(s, jour, coche));
  }

  protected basculerSalle(id: string, coche: boolean): void {
    this.sallesIsolement.update((s) => bascule(s, id, coche));
  }

  protected enregistrer(): void {
    this.store.enregistrerParametres({
      joursOuverts: JOURS_SEMAINE.filter((j) => this.joursOuverts().has(j)),
      sallesIsolement: [...this.sallesIsolement()],
    });
  }
}

function bascule<T>(set: ReadonlySet<T>, valeur: T, coche: boolean): ReadonlySet<T> {
  const next = new Set(set);
  if (coche) next.add(valeur); else next.delete(valeur);
  return next;
}
