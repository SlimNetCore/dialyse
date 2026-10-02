import {ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked} from '@angular/core';
import {compatForm} from '@angular/forms/signals/compat';
import {FormField, FormRoot, max, min, required} from '@angular/forms/signals';
import {MatButtonModule} from '@angular/material/button';
import {MatCardModule} from '@angular/material/card';
import {MatCheckboxModule} from '@angular/material/checkbox';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatIconModule} from '@angular/material/icon';
import {MatInputModule} from '@angular/material/input';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {TranslateModule} from '@ngx-translate/core';
import {JOURS_SEMAINE, JourSemaine} from '../../core/api/planning-api.service';
import {AppShellStore} from '../../core/state/app-shell.store';
import {SallesStore} from '../../core/state/referentials.store';
import {PlanningStore} from './planning.store';

/** Ratio de sécurité par défaut (patients par infirmier) et bornes acceptées par le serveur. */
export const RATIO_DEFAUT = 4;
export const RATIO_MAX = 20;

/**
 * Paramétrage du planning du centre : jours où l'on dialyse, salles d'isolement réservées aux patients à risque
 * infectieux (VHB, VHC, VIH) et ratio de sécurité des infirmiers. Ces réglages pilotent l'aide au placement, le
 * planning de la semaine et le planning de présence des infirmiers.
 */
@Component({
  selector: 'app-planning-parametres',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    MatButtonModule, MatCardModule, MatCheckboxModule, MatFormFieldModule, MatIconModule, MatInputModule,
    MatProgressBarModule, FormRoot, FormField, TranslateModule,
  ],
  templateUrl: './planning-parametres.component.html',
  styleUrl: './planning-parametres.component.css',
})
export class PlanningParametresComponent {
  protected readonly store = inject(PlanningStore);
  protected readonly sallesStore = inject(SallesStore);
  protected readonly jours = JOURS_SEMAINE;
  protected readonly ratioMax = RATIO_MAX;
  protected readonly joursOuverts = signal<ReadonlySet<JourSemaine>>(new Set(JOURS_SEMAINE));
  protected readonly sallesIsolement = signal<ReadonlySet<string>>(new Set());
  protected readonly ratioModel = signal({patientsParInfirmier: RATIO_DEFAUT});
  protected readonly ratioForm = compatForm(this.ratioModel, (form) => {
    required(form.patientsParInfirmier);
    min(form.patientsParInfirmier, 1);
    max(form.patientsParInfirmier, RATIO_MAX);
  });
  protected readonly canSave = computed(() =>
    this.joursOuverts().size > 0 && this.ratioForm().valid() && !this.store.saving());
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
      this.ratioModel.set({patientsParInfirmier: p.patientsParInfirmier});
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
      patientsParInfirmier: Number(this.ratioModel().patientsParInfirmier),
    });
  }
}

function bascule<T>(set: ReadonlySet<T>, valeur: T, coche: boolean): ReadonlySet<T> {
  const next = new Set(set);
  if (coche) next.add(valeur); else next.delete(valeur);
  return next;
}
