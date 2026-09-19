import {ChangeDetectionStrategy, Component, OnInit, computed, effect, inject, signal} from '@angular/core';
import {ActivatedRoute} from '@angular/router';
import {CommonModule} from '@angular/common';
import {MatCardModule} from '@angular/material/card';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatSelectModule} from '@angular/material/select';
import {MatDatepickerModule} from '@angular/material/datepicker';
import {MatNativeDateModule} from '@angular/material/core';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {MatTooltipModule} from '@angular/material/tooltip';
import {TranslateModule} from '@ngx-translate/core';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {DossierMedicalAccessService} from '../dossier-medical-access.service';
import {BilanPreGreffeStore} from '../state/bilan-pre-greffe.store';
import {EtapesBilanGreffeStore} from '../state/etapes-bilan-greffe.store';
import {DonneursVivantsStore} from '../state/donneurs-vivants.store';
import {resolvePatientIdFromRoute} from '../dossier-medical-route.util';
import {
  DossierMedicalApiService,
  DonneurVivant,
  EtapeBilanGreffe,
  KdigoGreffe,
} from '../../../core/api/dossier-medical-api.service';

const STATUTS_BILAN = [
  'NON_DEBUTE', 'BILAN_EN_COURS', 'ELIGIBLE', 'INSCRIT_LISTE_ATTENTE',
  'CONTRE_INDICATION_TEMPORAIRE', 'CONTRE_INDICATION_DEFINITIVE', 'GREFFE_REALISEE',
] as const;

const CATEGORIES_ETAPE = [
  'CARDIOLOGIQUE', 'PNEUMOLOGIQUE', 'DENTAIRE', 'ORL', 'GYNECOLOGIQUE', 'UROLOGIQUE', 'DIGESTIF',
  'DERMATOLOGIQUE', 'PSYCHIATRIQUE', 'VIROLOGIQUE', 'IMMUNOLOGIQUE', 'ONCOLOGIQUE', 'VACCINATION',
  'NUTRITIONNEL', 'AUTRE',
] as const;

const STATUTS_ETAPE = ['A_FAIRE', 'PLANIFIE', 'FAIT', 'NON_APPLICABLE'] as const;

const AVIS_RCP = ['FAVORABLE', 'DEFAVORABLE', 'AJOURNE'] as const;

const LIENS_PARENTE = ['CONJOINT', 'PARENT', 'ENFANT', 'FRERE_SOEUR', 'AUTRE_FAMILLE', 'NON_APPARENTE'] as const;

const STATUTS_DONNEUR = ['CANDIDAT', 'BILAN_EN_COURS', 'COMPATIBLE', 'INCOMPATIBLE', 'EXCLU', 'RETENU'] as const;

const RESULTATS_CROSSMATCH = ['NON_FAIT', 'NEGATIF', 'POSITIF'] as const;

interface DecisionRcpForm {
  dateReunion: Date | string | null;
  avis: typeof AVIS_RCP[number];
  compteRendu: string;
  prochaineDateRevue: Date | string | null;
}

interface EtapeForm {
  categorie: typeof CATEGORIES_ETAPE[number];
  libelle: string;
}

interface DonneurForm {
  nom: string;
  prenom: string;
  dateNaissance: Date | string | null;
  lienParente: typeof LIENS_PARENTE[number];
  telephone: string;
  groupeSanguin: string;
  typageHla: string;
  statutBilan: typeof STATUTS_DONNEUR[number];
  crossmatchResultat: typeof RESULTATS_CROSSMATCH[number];
  dateCrossmatch: Date | string | null;
  bilanRealise: string;
  contreIndications: string;
  decisionFinale: string;
  dateDecision: Date | string | null;
}

function toIsoDate(value: Date | string | null): string | null {
  if (!value) return null;
  if (typeof value === 'string') return value;
  const y = value.getFullYear();
  const m = String(value.getMonth() + 1).padStart(2, '0');
  const d = String(value.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

function emptyDecisionForm(): DecisionRcpForm {
  return {dateReunion: new Date(), avis: 'FAVORABLE', compteRendu: '', prochaineDateRevue: null};
}

function emptyEtapeForm(): EtapeForm {
  return {categorie: 'AUTRE', libelle: ''};
}

function emptyDonneurForm(): DonneurForm {
  return {
    nom: '', prenom: '', dateNaissance: null, lienParente: 'NON_APPARENTE', telephone: '', groupeSanguin: '',
    typageHla: '', statutBilan: 'CANDIDAT', crossmatchResultat: 'NON_FAIT', dateCrossmatch: null, bilanRealise: '',
    contreIndications: '', decisionFinale: '', dateDecision: null,
  };
}

/**
 * Dossier de préparation à la greffe rénale : éligibilité du patient receveur, bilan
 * immunologique, checklist du bilan pré-greffe, décisions de RCP et candidats donneurs vivants.
 */
@Component({
  selector: 'app-greffe',
  standalone: true,
  imports: [
    CommonModule, MatCardModule, MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatDatepickerModule, MatNativeDateModule, MatProgressSpinnerModule, MatTooltipModule,
    TranslateModule,
  ],
  templateUrl: './greffe.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './greffe.component.css',
})
export class GreffeComponent implements OnInit {
  protected readonly access = inject(DossierMedicalAccessService);
  protected readonly bilanStore = inject(BilanPreGreffeStore);
  protected readonly etapesStore = inject(EtapesBilanGreffeStore);
  protected readonly donneursStore = inject(DonneursVivantsStore);
  protected readonly statutsBilan = STATUTS_BILAN;
  protected readonly categoriesEtape = CATEGORIES_ETAPE;
  protected readonly statutsEtape = STATUTS_ETAPE;
  protected readonly avisRcp = AVIS_RCP;
  protected readonly liensParente = LIENS_PARENTE;
  protected readonly statutsDonneur = STATUTS_DONNEUR;
  protected readonly resultatsCrossmatch = RESULTATS_CROSSMATCH;

  protected readonly groupeSanguinConfirme = signal<string>('');
  protected readonly typageHla = signal<string>('');
  protected readonly praClasseI = signal<number | null>(null);
  protected readonly praClasseII = signal<number | null>(null);
  protected readonly contreIndications = signal<string>('');
  protected readonly conclusionNephrologue = signal<string>('');

  protected readonly decisionFormOpen = signal(false);
  protected readonly decisionForm = signal<DecisionRcpForm>(emptyDecisionForm());

  protected readonly etapeFormOpen = signal(false);
  protected readonly etapeForm = signal<EtapeForm>(emptyEtapeForm());

  protected readonly donneurFormOpen = signal(false);
  protected readonly editingDonneurId = signal<string | null>(null);
  protected readonly donneurForm = signal<DonneurForm>(emptyDonneurForm());

  protected readonly kdigo = signal<KdigoGreffe | null>(null);
  protected readonly loadingKdigo = signal(false);

  protected readonly etapesParCategorie = computed(() => {
    const groupes = new Map<string, EtapeBilanGreffe[]>();
    for (const etape of this.etapesStore.etapes()) {
      const liste = groupes.get(etape.categorie) ?? [];
      liste.push(etape);
      groupes.set(etape.categorie, liste);
    }
    return Array.from(groupes.entries()).map(([categorie, etapes]) => ({categorie, etapes}));
  });

  private readonly route = inject(ActivatedRoute);
  protected readonly patientId = resolvePatientIdFromRoute(this.route);
  private readonly appShell = inject(AppShellStore);
  private readonly api = inject(DossierMedicalApiService);
  private hasSyncedForm = false;
  private lastKdigoRefreshKey: string | null = null;

  constructor() {
    effect(() => {
      const bilan = this.bilanStore.bilan();
      if (!bilan || this.hasSyncedForm) return;
      this.groupeSanguinConfirme.set(bilan.groupeSanguinConfirme ?? '');
      this.typageHla.set(bilan.typageHla ?? '');
      this.praClasseI.set(bilan.praClasseI);
      this.praClasseII.set(bilan.praClasseII);
      this.contreIndications.set(bilan.contreIndications ?? '');
      this.conclusionNephrologue.set(bilan.conclusionNephrologue ?? '');
      this.hasSyncedForm = true;
    });

    // Le risque immunologique KDIGO depend du PRA du bilan : recalcule des que le bilan est
    // (re)charge ou modifie (ex. apres enregistrement du bilan immunologique).
    effect(() => {
      const bilan = this.bilanStore.bilan();
      const centerId = this.appShell.currentCenterId();
      if (!bilan || !centerId) return;
      const key = `${bilan.updatedAt}`;
      if (key === this.lastKdigoRefreshKey) return;
      this.lastKdigoRefreshKey = key;
      this.loadKdigo(centerId, bilan.patientId);
    });
  }

  ngOnInit(): void {
    this.refresh();
  }

  badgeClassStatut(statut: string): string {
    if (statut === 'ELIGIBLE' || statut === 'INSCRIT_LISTE_ATTENTE' || statut === 'GREFFE_REALISEE') return 'badge-ok';
    if (statut.startsWith('CONTRE_INDICATION')) return 'badge-warn';
    return 'badge-muted';
  }

  changerStatut(statut: string): void {
    this.bilanStore.changerStatut(statut);
  }

  onPraInput(key: 'praClasseI' | 'praClasseII', raw: string): void {
    const value = raw === '' ? null : Number(raw);
    const target = key === 'praClasseI' ? this.praClasseI : this.praClasseII;
    target.set(Number.isNaN(value) ? null : value);
  }

  saveBilanImmunologique(): void {
    this.bilanStore.mettreAJourBilanImmunologique(
      this.groupeSanguinConfirme() || null, this.typageHla() || null, this.praClasseI(), this.praClasseII());
  }

  saveNotes(): void {
    this.bilanStore.mettreAJourNotes(this.contreIndications() || null, this.conclusionNephrologue() || null);
  }

  openDecisionForm(): void {
    this.decisionForm.set(emptyDecisionForm());
    this.decisionFormOpen.set(true);
  }

  cancelDecisionForm(): void {
    this.decisionFormOpen.set(false);
  }

  onDecisionValue<K extends keyof DecisionRcpForm>(key: K, value: DecisionRcpForm[K]): void {
    this.decisionForm.update((f) => ({...f, [key]: value}));
  }

  saveDecisionRcp(): void {
    const value = this.decisionForm();
    const dateReunion = toIsoDate(value.dateReunion);
    if (!dateReunion) return;
    this.bilanStore.ajouterDecisionRcp(dateReunion, value.avis, value.compteRendu || null,
      toIsoDate(value.prochaineDateRevue));
    this.decisionFormOpen.set(false);
  }

  genererEtapesStandard(): void {
    this.etapesStore.genererEtapesStandard();
  }

  openEtapeForm(): void {
    this.etapeForm.set(emptyEtapeForm());
    this.etapeFormOpen.set(true);
  }

  cancelEtapeForm(): void {
    this.etapeFormOpen.set(false);
  }

  onEtapeValue<K extends keyof EtapeForm>(key: K, value: EtapeForm[K]): void {
    this.etapeForm.update((f) => ({...f, [key]: value}));
  }

  saveEtape(): void {
    const value = this.etapeForm();
    if (!value.libelle.trim()) return;
    this.etapesStore.create(value.categorie, value.libelle.trim());
    this.etapeFormOpen.set(false);
  }

  updateEtapeStatut(etape: EtapeBilanGreffe, statut: string): void {
    const dateRealisation = statut === 'FAIT' ? (etape.dateRealisation ?? toIsoDate(new Date())) : etape.dateRealisation;
    this.etapesStore.update(etape.id, {
      centerId: this.appShell.currentCenterId() ?? '',
      statut,
      dateRealisation,
      resultat: etape.resultat,
      dateExpiration: etape.dateExpiration,
      demandeExamenId: etape.demandeExamenId,
      serologieId: etape.serologieId,
    });
  }

  updateEtapeResultat(etape: EtapeBilanGreffe, resultat: string): void {
    this.etapesStore.update(etape.id, {
      centerId: this.appShell.currentCenterId() ?? '',
      statut: etape.statut,
      dateRealisation: etape.dateRealisation,
      resultat: resultat || null,
      dateExpiration: etape.dateExpiration,
      demandeExamenId: etape.demandeExamenId,
      serologieId: etape.serologieId,
    });
  }

  removeEtape(etape: EtapeBilanGreffe): void {
    this.etapesStore.remove(etape.id);
  }

  openCreateDonneurForm(): void {
    this.editingDonneurId.set(null);
    this.donneurForm.set(emptyDonneurForm());
    this.donneurFormOpen.set(true);
  }

  openEditDonneurForm(donneur: DonneurVivant): void {
    this.editingDonneurId.set(donneur.id);
    this.donneurForm.set({
      nom: donneur.nom,
      prenom: donneur.prenom ?? '',
      dateNaissance: donneur.dateNaissance,
      lienParente: donneur.lienParente as typeof LIENS_PARENTE[number],
      telephone: donneur.telephone ?? '',
      groupeSanguin: donneur.groupeSanguin ?? '',
      typageHla: donneur.typageHla ?? '',
      statutBilan: donneur.statutBilan as typeof STATUTS_DONNEUR[number],
      crossmatchResultat: donneur.crossmatchResultat as typeof RESULTATS_CROSSMATCH[number],
      dateCrossmatch: donneur.dateCrossmatch,
      bilanRealise: donneur.bilanRealise ?? '',
      contreIndications: donneur.contreIndications ?? '',
      decisionFinale: donneur.decisionFinale ?? '',
      dateDecision: donneur.dateDecision,
    });
    this.donneurFormOpen.set(true);
  }

  cancelDonneurForm(): void {
    this.donneurFormOpen.set(false);
    this.editingDonneurId.set(null);
  }

  onDonneurValue<K extends keyof DonneurForm>(key: K, value: DonneurForm[K]): void {
    this.donneurForm.update((f) => ({...f, [key]: value}));
  }

  saveDonneur(): void {
    const value = this.donneurForm();
    if (!value.nom.trim()) return;
    const centerId = this.appShell.currentCenterId();
    if (!centerId) return;
    const payload = {
      centerId,
      nom: value.nom.trim(),
      prenom: value.prenom || null,
      dateNaissance: toIsoDate(value.dateNaissance),
      lienParente: value.lienParente,
      telephone: value.telephone || null,
      groupeSanguin: value.groupeSanguin || null,
      typageHla: value.typageHla || null,
      statutBilan: value.statutBilan,
      crossmatchResultat: value.crossmatchResultat,
      dateCrossmatch: toIsoDate(value.dateCrossmatch),
      bilanRealise: value.bilanRealise || null,
      contreIndications: value.contreIndications || null,
      decisionFinale: value.decisionFinale || null,
      dateDecision: toIsoDate(value.dateDecision),
    };
    const editingId = this.editingDonneurId();
    if (editingId) {
      this.donneursStore.update(editingId, payload);
    } else {
      this.donneursStore.create(payload);
    }
    this.donneurFormOpen.set(false);
    this.editingDonneurId.set(null);
  }

  removeDonneur(donneur: DonneurVivant): void {
    this.donneursStore.remove(donneur.id);
  }

  exportPdf(): void {
    const centerId = this.appShell.currentCenterId();
    if (!centerId || !this.patientId) return;
    this.api.exportGreffePdf(centerId, this.patientId).subscribe((blob) => {
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `dossier-greffe-${this.patientId}.pdf`;
      a.click();
      window.URL.revokeObjectURL(url);
    });
  }

  private refresh(): void {
    const centerId = this.appShell.currentCenterId();
    if (!centerId || !this.patientId) return;
    this.bilanStore.load({centerId, patientId: this.patientId});
    this.etapesStore.load({centerId, patientId: this.patientId});
    this.donneursStore.load({centerId, patientId: this.patientId});
  }

  private loadKdigo(centerId: string, patientId: string): void {
    this.loadingKdigo.set(true);
    this.api.getKdigoGreffe(centerId, patientId).subscribe({
      next: (kdigo) => {
        this.kdigo.set(kdigo);
        this.loadingKdigo.set(false);
      },
      error: () => this.loadingKdigo.set(false),
    });
  }
}
