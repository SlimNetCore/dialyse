import {ChangeDetectionStrategy, Component, computed, inject, signal} from '@angular/core';
import {MatSelectModule} from '@angular/material/select';
import {MatFormFieldModule} from '@angular/material/form-field';
import {TranslateService} from '@ngx-translate/core';
import {MatSnackBar} from '@angular/material/snack-bar';
import {openPdf} from '../../../stock/inventaire/inventaire.util';
import {ConfirmDialogComponent} from '../../../../shared/confirm-dialog.component';
import {CommonModule} from '@angular/common';
import {ActivatedRoute, RouterLink} from '@angular/router';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatTableModule} from '@angular/material/table';
import {MatDialog} from '@angular/material/dialog';
import {TranslateModule} from '@ngx-translate/core';
import {
  AjouterLigneCoutPayload,
  DocumentIntervention,
  Equipement,
  EvenementIntervention,
  IndicateursIntervention,
  GmaoApiService,
  Intervention,
  TypeDocumentIntervention,
  TypeLigneCout,
} from '../../../../core/api/gmao-api.service';
import {
  dureeLabel,
  dureeMinutes,
  browserTimeZone,
  prioriteTone,
  statutEquipementTone,
  statutInterventionTone,
  TYPES_DOCUMENT_INTERVENTION,
} from '../../gmao-options.util';
import {GmaoIntervenantsStore} from '../../state/gmao-intervenants.store';
import {GmaoLigneCoutDialogComponent, LigneCoutDialogData} from '../ligne-cout-dialog/ligne-cout-dialog.component';

const TYPES_LIGNE: TypeLigneCout[] = ['PIECE', 'MAIN_OEUVRE', 'INTERVENANT', 'AUTRE'];

/**
 * Fiche détaillée d'une intervention : période (début/fin/durée), états de l'équipement avant/après,
 * intervenant et décomposition du coût (pièces, main d'œuvre, honoraires de l'intervenant, autres).
 * Les lignes de coût restent ajoutables après la clôture (facture reçue après coup).
 */
@Component({
  selector: 'app-gmao-fiche-intervention',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule, RouterLink, MatButtonModule, MatIconModule, MatProgressBarModule, MatTableModule,
    MatSelectModule, MatFormFieldModule, TranslateModule,
  ],
  templateUrl: './fiche-intervention.component.html',
  styleUrls: ['./fiche-intervention.component.css', '../../gmao-shared.css'],
})
export class GmaoFicheInterventionComponent {
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly intervention = signal<Intervention | null>(null);
  protected readonly equipement = signal<Equipement | null>(null);
  protected readonly columns = ['type', 'libelle', 'quantite', 'prixUnitaire', 'montant'];
  protected readonly statutInterventionTone = statutInterventionTone;
  protected readonly statutEquipementTone = statutEquipementTone;
  protected readonly prioriteTone = prioriteTone;
  protected readonly dureeLabel = dureeLabel;
  protected readonly typesDocument = TYPES_DOCUMENT_INTERVENTION;
  protected readonly indicateurs = signal<IndicateursIntervention | null>(null);
  protected readonly chronologie = signal<EvenementIntervention[]>([]);
  protected readonly documents = signal<DocumentIntervention[]>([]);
  protected readonly uploading = signal(false);
  protected readonly printing = signal(false);
  protected readonly typeDocument = signal<TypeDocumentIntervention>('AUTRE');
  protected readonly duree = computed(() => {
    const i = this.intervention();
    return i ? dureeLabel(dureeMinutes(i.dateDebut, i.dateFin)) : '';
  });
  /** Sous-totaux par nature de coût (affichés même à zéro pour un suivi lisible). */
  protected readonly sousTotaux = computed(() => {
    const lignes = this.intervention()?.lignesCout ?? [];
    return TYPES_LIGNE.map((type) => ({
      type,
      montant: lignes.filter((l) => l.type === type).reduce((s, l) => s + l.montant, 0),
    }));
  });
  /** Une intervention annulée n'accepte plus de ligne de coût ; terminée, elle en accepte encore. */
  protected readonly canAddCost = computed(() => {
    const s = this.intervention()?.statut;
    return !!s && s !== 'ANNULEE';
  });
  private readonly api = inject(GmaoApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly dialog = inject(MatDialog);
  private readonly intervenantsStore = inject(GmaoIntervenantsStore);
  protected readonly intervenantNom = computed(() => {
    const id = this.intervention()?.intervenantId;
    return id ? this.intervenantsStore.rows().find((x) => x.id === id)?.nom ?? null : null;
  });
  private readonly translate = inject(TranslateService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly interventionId = this.route.snapshot.paramMap.get('id')!;

  constructor() {
    this.intervenantsStore.loadPage({page: 0, size: 100});
    this.reload();
  }

  protected reload(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.getIntervention(this.interventionId).subscribe({
      next: (intervention) => {
        this.intervention.set(intervention);
        this.loading.set(false);
        this.api.getEquipement(intervention.equipementId).subscribe({
          next: (e) => this.equipement.set(e),
          error: () => this.equipement.set(null),
        });
        this.loadSuivi();
      },
      error: () => {
        this.error.set('GMAO.INTERVENTIONS.LOAD_ERROR');
        this.loading.set(false);
      },
    });
  }

  protected onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    this.uploading.set(true);
    this.error.set(null);
    this.api.uploadDocumentIntervention(this.interventionId, file, this.typeDocument()).subscribe({
      next: () => {
        this.uploading.set(false);
        this.loadDocuments();
        this.reloadChronologie();
      },
      error: () => {
        this.uploading.set(false);
        this.error.set('GMAO.DOCUMENTS.UPLOAD_ERROR');
      },
    });
  }

  protected download(doc: DocumentIntervention): void {
    this.api.downloadDocumentIntervention(this.interventionId, doc.id).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = doc.nom;
        a.click();
        URL.revokeObjectURL(url);
      },
      error: () => this.error.set('GMAO.DOCUMENTS.DOWNLOAD_ERROR'),
    });
  }

  protected remove(doc: DocumentIntervention): void {
    const ref = this.dialog.open(ConfirmDialogComponent, {
      width: 'min(96vw, 460px)',
      data: {
        title: this.translate.instant('GMAO.DOCUMENTS.CONFIRM_DELETE_TITLE'),
        message: this.translate.instant('GMAO.DOCUMENTS.CONFIRM_DELETE_MESSAGE', {nom: doc.nom}),
        confirmLabel: this.translate.instant('COMMON.CONFIRM'),
        cancelLabel: this.translate.instant('COMMON.CANCEL'),
        color: 'warn',
        icon: 'delete',
      },
    });
    ref.afterClosed().subscribe((confirmed) => {
      if (!confirmed) return;
      this.api.deleteDocumentIntervention(this.interventionId, doc.id).subscribe({
        next: () => this.loadDocuments(),
        error: () => this.error.set('GMAO.DOCUMENTS.DELETE_ERROR'),
      });
    });
  }

  /** Bon d'intervention : modèle de document du centre, dates affichées dans le fuseau du navigateur. */
  protected async printBon(): Promise<void> {
    this.printing.set(true);
    const error = await openPdf(
      this.api.getBonIntervention(this.interventionId, browserTimeZone()), `bon-intervention-${this.interventionId.slice(0, 8)}.pdf`);
    this.printing.set(false);
    if (error !== null) {
      this.snackBar.open(error || this.translate.instant('GMAO.INTERVENTIONS.PRINT_ERROR'), 'OK', {duration: 7000});
    }
  }

  /** Saisie guidée de la main d'œuvre : heures (durée de l'intervention) × taux horaire de l'intervenant. */
  protected saisirMainOeuvre(): void {
    const i = this.intervention();
    const intervenant = this.intervenantsStore.rows().find((x) => x.id === i?.intervenantId);
    const minutes = i ? dureeMinutes(i.dateDebut, i.dateFin) : 0;
    this.addLigneCout({
      preset: 'MAIN_OEUVRE',
      libelle: this.translate.instant('GMAO.COUTS.MAIN_OEUVRE_LIBELLE'),
      heures: minutes > 0 ? Math.round(minutes / 60 * 100) / 100 : undefined,
      tauxHoraire: intervenant?.tarifHoraireDefaut ?? null,
    });
  }

  protected addLigneCout(data?: LigneCoutDialogData): void {
    const ref = this.dialog.open(GmaoLigneCoutDialogComponent, {width: 'min(96vw, 480px)', data});
    ref.afterClosed().subscribe((payload: AjouterLigneCoutPayload | null) => {
      if (!payload) return;
      this.saving.set(true);
      this.api.ajouterLigneCout(this.interventionId, payload).subscribe({
        next: (updated) => {
          this.intervention.set(updated);
          this.saving.set(false);
          this.reloadChronologie();
        },
        error: () => {
          this.error.set('GMAO.INTERVENTIONS.SAVE_ERROR');
          this.saving.set(false);
        },
      });
    });
  }

  /** Indicateurs, ligne de temps et pièces jointes : chargés à part, une erreur n'empêche pas d'afficher la fiche. */
  private loadSuivi(): void {
    this.api.getIndicateursIntervention(this.interventionId).subscribe({
      next: (r) => this.indicateurs.set(r),
      error: () => this.indicateurs.set(null),
    });
    this.api.getChronologieIntervention(this.interventionId).subscribe({
      next: (r) => this.chronologie.set(r),
      error: () => this.chronologie.set([]),
    });
    this.loadDocuments();
  }

  private loadDocuments(): void {
    this.api.listDocumentsIntervention(this.interventionId, 0, 50).subscribe({
      next: (page) => this.documents.set(page.items ?? []),
      error: () => this.documents.set([]),
    });
  }

  private reloadChronologie(): void {
    this.api.getChronologieIntervention(this.interventionId).subscribe({
      next: (r) => this.chronologie.set(r),
    });
  }
}
