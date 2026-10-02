import {ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked} from '@angular/core';
import {compatForm} from '@angular/forms/signals/compat';
import {FormField, FormRoot, required} from '@angular/forms/signals';
import {MatButtonModule} from '@angular/material/button';
import {MatCardModule} from '@angular/material/card';
import {MatCheckboxModule} from '@angular/material/checkbox';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatIconModule} from '@angular/material/icon';
import {MatInputModule} from '@angular/material/input';
import {MatPaginatorModule, PageEvent} from '@angular/material/paginator';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatSelectModule} from '@angular/material/select';
import {MatTableModule} from '@angular/material/table';
import {TranslateModule} from '@ngx-translate/core';
import {Infirmier, QUALIFICATIONS, QualificationInfirmier} from '../../core/api/infirmier-api.service';
import {JOURS_SEMAINE, JourSemaine} from '../../core/api/planning-api.service';
import {AppShellStore} from '../../core/state/app-shell.store';
import {PositionsStore, SallesStore} from '../../core/state/referentials.store';
import {InfirmiersStore} from './infirmiers.store';
import {chevauche, joursOrdonnes} from './presence.util';

type InfirmierFormModel = {
  matricule: string;
  nom: string;
  prenom: string;
  telephone: string;
  qualification: QualificationInfirmier;
  habiliteIsolement: boolean;
};

type AffectationFormModel = { salleId: string; creneauId: string };

function emptyInfirmier(): InfirmierFormModel {
  return {matricule: '', nom: '', prenom: '', telephone: '', qualification: 'INFIRMIER', habiliteIsolement: false};
}

/**
 * Référentiel des infirmiers du centre et de leur roulement : salle, créneau (position) et jours de travail. Les
 * affectations alimentent le planning de présence et la détection des absences à remplacer.
 */
@Component({
  selector: 'app-infirmiers',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    MatButtonModule, MatCardModule, MatCheckboxModule, MatFormFieldModule, MatIconModule, MatInputModule,
    MatPaginatorModule, MatProgressBarModule, MatSelectModule, MatTableModule, FormRoot, FormField, TranslateModule,
  ],
  templateUrl: './infirmiers.component.html',
  styleUrl: './infirmiers.component.css',
})
export class InfirmiersComponent {
  protected readonly store = inject(InfirmiersStore);
  protected readonly sallesStore = inject(SallesStore);
  protected readonly positionsStore = inject(PositionsStore);
  protected readonly columns = [
    'matricule', 'nom', 'qualification', 'isolement', 'roulement', 'compte', 'statut', 'actions',
  ];
  protected readonly qualifications = QUALIFICATIONS;
  protected readonly jours = JOURS_SEMAINE;
  protected readonly editingId = signal<string | null>(null);
  protected readonly selectedId = signal<string | null>(null);
  protected readonly selected = computed<Infirmier | null>(() =>
    this.store.rows().find((i) => i.id === this.selectedId()) ?? null);
  protected readonly joursChoisis = signal<ReadonlySet<JourSemaine>>(new Set());
  protected readonly formModel = signal(emptyInfirmier());
  protected readonly infirmierForm = compatForm(this.formModel, (form) => {
    required(form.matricule);
    required(form.nom);
    required(form.qualification);
  });
  protected readonly affectationModel = signal<AffectationFormModel>({salleId: '', creneauId: ''});
  protected readonly affectationForm = compatForm(this.affectationModel, (form) => {
    required(form.salleId);
    required(form.creneauId);
  });
  protected readonly canSave = computed(() => this.infirmierForm().valid() && !this.store.saving());
  protected readonly chevauchement = computed(() => {
    const infirmier = this.selected();
    const m = this.affectationModel();
    return !!infirmier && !!m.creneauId && chevauche(
      {creneauId: m.creneauId, jours: [...this.joursChoisis()]}, infirmier.affectations);
  });
  protected readonly lierModel = signal({userId: ''});
  protected readonly lierForm = compatForm(this.lierModel, (form) => {
    required(form.userId);
  });
  protected readonly creerModel = signal({identifiant: '', email: ''});
  protected readonly creerForm = compatForm(this.creerModel, (form) => {
    required(form.identifiant);
  });
  /** Mot de passe temporaire du compte qui vient d'être créé pour la fiche ouverte (affiché une seule fois). */
  protected readonly motDePasse = computed(() => {
    const cree = this.store.compteCree();
    return cree && cree.infirmierId === this.selectedId() ? cree.motDePasse : null;
  });
  protected readonly canLier = computed(() => this.lierForm().valid() && !this.store.saving());
  protected readonly canCreerCompte = computed(() => this.creerForm().valid() && !this.store.saving());
  protected readonly canAddAffectation = computed(() =>
    !!this.selected() && this.affectationForm().valid() && this.joursChoisis().size > 0
    && !this.chevauchement() && !this.store.saving());
  private readonly shell = inject(AppShellStore);

  constructor() {
    this.store.loadPage({page: 0, size: this.store.pageSize()});
    effect(() => {
      const centerId = this.shell.currentCenterId();
      untracked(() => {
        void this.sallesStore.ensureLoaded(centerId);
        void this.positionsStore.ensureLoaded(centerId);
      });
    });
    effect(() => {
      if (!this.store.successMessage()) return;
      untracked(() => {
        this.cancelEdit();
        this.joursChoisis.set(new Set());
        this.affectationModel.set({salleId: '', creneauId: ''});
        this.lierModel.set({userId: ''});
        this.creerModel.set({identifiant: '', email: ''});
      });
    });
    // Comptes proposés à la liaison : chargés à l'ouverture d'une fiche sans compte.
    effect(() => {
      const infirmier = this.selected();
      if (infirmier && !infirmier.compte) untracked(() => this.store.loadComptesLiables());
    });
  }

  protected lierCompte(): void {
    const infirmier = this.selected();
    if (!infirmier || !this.canLier()) return;
    this.store.lierCompte({infirmierId: infirmier.id, userId: this.lierModel().userId});
  }

  protected creerCompte(): void {
    const infirmier = this.selected();
    if (!infirmier || !this.canCreerCompte()) return;
    const m = this.creerModel();
    this.store.creerCompte({
      infirmierId: infirmier.id, payload: {identifiant: m.identifiant.trim(), email: m.email.trim() || null},
    });
  }

  protected delierCompte(): void {
    const infirmier = this.selected();
    if (infirmier) this.store.delierCompte(infirmier.id);
  }

  /** Copie le mot de passe temporaire dans le presse-papiers (sans effet si l'API n'est pas disponible). */
  protected async copierMotDePasse(): Promise<void> {
    const mdp = this.motDePasse();
    if (!mdp || !navigator.clipboard) return;
    try {
      await navigator.clipboard.writeText(mdp);
    } catch {
      // refus du navigateur : le mot de passe reste affiché pour être recopié à la main
    }
  }

  protected salleLabel(id: string): string {
    return this.sallesStore.items().find((s) => s.id === id)?.label ?? id;
  }

  protected creneauLabel(id: string): string {
    return this.positionsStore.items().find((p) => p.id === id)?.label ?? id;
  }

  protected ordonnes(jours: readonly JourSemaine[]): JourSemaine[] {
    return joursOrdonnes(jours);
  }

  protected edit(row: Infirmier): void {
    this.editingId.set(row.id);
    this.formModel.set({
      matricule: row.matricule, nom: row.nom, prenom: row.prenom ?? '', telephone: row.telephone ?? '',
      qualification: row.qualification, habiliteIsolement: row.habiliteIsolement,
    });
  }

  protected cancelEdit(): void {
    this.editingId.set(null);
    this.formModel.set(emptyInfirmier());
  }

  protected save(): void {
    if (!this.canSave()) return;
    const m = this.formModel();
    const payload = {
      matricule: m.matricule.trim(),
      nom: m.nom.trim(),
      prenom: m.prenom.trim() || null,
      telephone: m.telephone.trim() || null,
      qualification: m.qualification,
      habiliteIsolement: m.habiliteIsolement,
    };
    const id = this.editingId();
    if (id) this.store.update({id, payload}); else this.store.create(payload);
  }

  protected toggleActif(row: Infirmier): void {
    this.store.setActif({id: row.id, actif: !row.actif});
  }

  protected openRoulement(row: Infirmier): void {
    this.selectedId.set(this.selectedId() === row.id ? null : row.id);
    this.joursChoisis.set(new Set());
  }

  protected basculerJour(jour: JourSemaine, coche: boolean): void {
    this.joursChoisis.update((s) => {
      const next = new Set(s);
      if (coche) next.add(jour); else next.delete(jour);
      return next;
    });
  }

  protected addAffectation(): void {
    const infirmier = this.selected();
    if (!infirmier || !this.canAddAffectation()) return;
    const m = this.affectationModel();
    this.store.addAffectation({
      infirmierId: infirmier.id,
      payload: {
        salleId: m.salleId,
        creneauId: m.creneauId,
        jours: JOURS_SEMAINE.filter((j) => this.joursChoisis().has(j))
      },
    });
  }

  protected deleteAffectation(affectationId: string): void {
    const infirmier = this.selected();
    if (infirmier) this.store.deleteAffectation({infirmierId: infirmier.id, affectationId});
  }

  protected onPage(event: PageEvent): void {
    this.store.setPagination(event.pageIndex, event.pageSize);
    this.store.loadPage({page: event.pageIndex, size: event.pageSize});
  }
}
