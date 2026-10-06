import {ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked} from '@angular/core';
import {compatForm} from '@angular/forms/signals/compat';
import {FormField, FormRoot, max, min, required} from '@angular/forms/signals';
import {MatButtonModule} from '@angular/material/button';
import {MatCardModule} from '@angular/material/card';
import {MatCheckboxModule} from '@angular/material/checkbox';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatIconModule} from '@angular/material/icon';
import {MatInputModule} from '@angular/material/input';
import {MatPaginatorModule, PageEvent} from '@angular/material/paginator';
import {MatSelectModule} from '@angular/material/select';
import {MatTableModule} from '@angular/material/table';
import {MatTabsModule} from '@angular/material/tabs';
import {TranslateModule} from '@ngx-translate/core';
import {
  BORNES_PREFERENCES,
  COMPETENCES_INFIRMIER,
  CompetenceInfirmier,
  LignePreferencePatient,
  LigneProfilInfirmier,
} from '../../../core/api/planning-preferences-api.service';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {AuthStore} from '../../../core/state/auth.store';
import {PositionsStore} from '../../../core/state/referentials.store';
import {PreferencesPlanningStore} from './preferences-planning.store';
import {payloadPreference} from './optimisation.util';

type PreferenceFormModel = { creneauPrefereId: string; joursAChoisir: boolean; seancesParSemaine: number };
type ProfilFormModel = { tauxActivite: number; competences: CompetenceInfirmier[] };
type ReglagesFormModel = {
  replanificationAuto: boolean;
  heuresParVacation: number;
  heuresHebdoTempsPlein: number;
  reposHebdoMin: number;
};

const PREFERENCE_VIDE: PreferenceFormModel = {creneauPrefereId: '', joursAChoisir: false, seancesParSemaine: 3};
const PROFIL_VIDE: ProfilFormModel = {tauxActivite: 100, competences: []};
const REGLAGES_DEFAUT: ReglagesFormModel = {
  replanificationAuto: false, heuresParVacation: 5, heuresHebdoTempsPlein: 40, reposHebdoMin: 1,
};

/**
 * Données de planification que l'optimisation prend en compte, propres au centre actif : préférences des patients
 * (créneau souhaité, jours de dialyse à choisir), profils des infirmiers (temps partiel, compétences) et réglages
 * (durée d'une vacation, temps plein, repos hebdomadaire, replanification automatique nocturne — administrateur).
 */
@Component({
  selector: 'app-planning-preferences',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    MatButtonModule, MatCardModule, MatCheckboxModule, MatFormFieldModule, MatIconModule, MatInputModule,
    MatPaginatorModule, MatSelectModule, MatTableModule, MatTabsModule, FormRoot, FormField, TranslateModule,
  ],
  templateUrl: './planning-preferences.component.html',
  styleUrl: './planning-optimisation.component.css',
})
export class PlanningPreferencesComponent {
  protected readonly store = inject(PreferencesPlanningStore);
  protected readonly positions = inject(PositionsStore);
  protected readonly competences = COMPETENCES_INFIRMIER;
  protected readonly bornes = BORNES_PREFERENCES;
  protected readonly colonnesPatients = ['patient', 'jours', 'creneau', 'choix', 'actions'];
  protected readonly colonnesInfirmiers = ['infirmier', 'qualification', 'taux', 'competences', 'actions'];
  protected readonly tailles = [10, 20, 50, 100];
  protected readonly estAdmin = computed(() => this.auth.hasRole('ADMIN'));

  protected readonly patientEdite = signal<LignePreferencePatient | null>(null);
  protected readonly preferenceModel = signal<PreferenceFormModel>({...PREFERENCE_VIDE});
  protected readonly preferenceForm = compatForm(this.preferenceModel, (form) => {
    min(form.seancesParSemaine, BORNES_PREFERENCES.seances.min);
    max(form.seancesParSemaine, BORNES_PREFERENCES.seances.max);
  });

  protected readonly infirmierEdite = signal<LigneProfilInfirmier | null>(null);
  protected readonly profilModel = signal<ProfilFormModel>({...PROFIL_VIDE});
  protected readonly profilForm = compatForm(this.profilModel, (form) => {
    required(form.tauxActivite);
    min(form.tauxActivite, BORNES_PREFERENCES.taux.min);
    max(form.tauxActivite, BORNES_PREFERENCES.taux.max);
  });

  protected readonly reglagesModel = signal<ReglagesFormModel>({...REGLAGES_DEFAUT});
  protected readonly reglagesForm = compatForm(this.reglagesModel, (form) => {
    min(form.heuresParVacation, BORNES_PREFERENCES.heuresVacation.min);
    max(form.heuresParVacation, BORNES_PREFERENCES.heuresVacation.max);
    min(form.heuresHebdoTempsPlein, BORNES_PREFERENCES.heuresHebdo.min);
    max(form.heuresHebdoTempsPlein, BORNES_PREFERENCES.heuresHebdo.max);
    min(form.reposHebdoMin, BORNES_PREFERENCES.repos.min);
    max(form.reposHebdoMin, BORNES_PREFERENCES.repos.max);
  });

  private readonly libellesCreneaux = computed(() =>
    new Map(this.positions.items().map((p) => [p.id, p.label])));
  private readonly auth = inject(AuthStore);
  private readonly shell = inject(AppShellStore);

  constructor() {
    // Changement de centre : on recharge tout pour le nouveau centre.
    effect(() => {
      const centre = this.shell.currentCenterId();
      untracked(() => {
        this.annuler();
        this.store.reinitialiser();
        void this.positions.ensureLoaded(centre);
        this.store.charger();
      });
    });
    // Les réglages lus du serveur alimentent le formulaire.
    effect(() => {
      const reglages = this.store.reglages();
      if (reglages) untracked(() => this.reglagesModel.set({...reglages}));
    });
  }

  protected creneau(id: string | null): string {
    return (id && this.libellesCreneaux().get(id)) || '—';
  }

  protected modifierPatient(ligne: LignePreferencePatient): void {
    this.patientEdite.set(ligne);
    const p = ligne.preference;
    this.preferenceModel.set({
      creneauPrefereId: p.creneauPrefereId ?? '',
      joursAChoisir: p.joursAChoisir,
      seancesParSemaine: p.seancesParSemaine ?? (ligne.jours.length || PREFERENCE_VIDE.seancesParSemaine),
    });
  }

  protected async enregistrerPreference(): Promise<void> {
    const ligne = this.patientEdite();
    if (!ligne || !this.preferenceForm().valid()) return;
    if (await this.store.enregistrerPreference(ligne.patientId, payloadPreference(this.preferenceModel()))) {
      this.patientEdite.set(null);
    }
  }

  protected modifierInfirmier(ligne: LigneProfilInfirmier): void {
    this.infirmierEdite.set(ligne);
    this.profilModel.set({tauxActivite: ligne.profil.tauxActivite, competences: [...ligne.profil.competences]});
  }

  protected async enregistrerProfil(): Promise<void> {
    const ligne = this.infirmierEdite();
    if (!ligne || !this.profilForm().valid()) return;
    const m = this.profilModel();
    if (await this.store.enregistrerProfil(ligne.infirmierId,
      {tauxActivite: Number(m.tauxActivite), competences: m.competences})) {
      this.infirmierEdite.set(null);
    }
  }

  protected async enregistrerReglages(): Promise<void> {
    if (!this.estAdmin() || !this.reglagesForm().valid()) return;
    const m = this.reglagesModel();
    await this.store.enregistrerReglages({
      replanificationAuto: m.replanificationAuto,
      heuresParVacation: Number(m.heuresParVacation),
      heuresHebdoTempsPlein: Number(m.heuresHebdoTempsPlein),
      reposHebdoMin: Number(m.reposHebdoMin),
    });
  }

  protected annuler(): void {
    this.patientEdite.set(null);
    this.infirmierEdite.set(null);
  }

  protected onPagePatients(event: PageEvent): void {
    this.patientEdite.set(null);
    void this.store.setPatientsPagination(event.pageIndex, event.pageSize);
  }

  protected onPageInfirmiers(event: PageEvent): void {
    this.infirmierEdite.set(null);
    void this.store.setInfirmiersPagination(event.pageIndex, event.pageSize);
  }
}
