import {ChangeDetectionStrategy, Component, computed, inject, signal, TemplateRef, viewChild} from '@angular/core';
import {CommonModule} from '@angular/common';
import {MatCardModule} from '@angular/material/card';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatSelectModule} from '@angular/material/select';
import {MatDatepickerModule} from '@angular/material/datepicker';
import {MatNativeDateModule} from '@angular/material/core';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatSnackBar} from '@angular/material/snack-bar';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {NgTableColumn, NgTableComponent, NgTableTone} from '@sbourahla/ng-table';
import {AuthStore} from '../../core/state/auth.store';
import {
  DemiJournee,
  LigneSortiePourSeances,
  SortiePourSeances,
  StatutSortiePourSeances,
  StockApiService
} from '../../core/api/stock-api.service';
import {ReferentialApiService, RefItem} from '../../core/api/referential-api.service';
import {NgTableTranslatedLabels} from '../../shared/ng-table-labels';
import {langueCourante, STOCK_DEFAULT_PAGE_SIZE, STOCK_PAGE_SIZES} from './stock-ng-table';

const STATUTS_SORTIE: readonly StatutSortiePourSeances[] = ['ENREGISTREE', 'RAPPROCHEE'];

/** Pièce enregistrée (à rapprocher) en bleu, rapprochée en vert. */
export const TONS_STATUT_SORTIE: Record<StatutSortiePourSeances, NgTableTone> = {
  ENREGISTREE: 'info',
  RAPPROCHEE: 'success',
};

/** `yyyy-MM-dd` ⇒ `dd/MM/yyyy`. */
function jourCourt(iso: string | null | undefined): string {
  const [annee, mois, jour] = (iso ?? '').substring(0, 10).split('-');
  return annee && mois && jour ? `${jour}/${mois}/${annee}` : '';
}

type LigneForm ={ articleId: string | null; quantite: number | null };

type PieceForm = {
  jour: Date | null;
  salle: string;
  demiJournee: DemiJournee | null;
  lignes: LigneForm[];
};

/**
 * Sortie pour séances (S6b, ADR 0045) : le gestionnaire de stock sort la veille les consommables du lendemain pour un périmètre
 * (salle, demi-journée), puis la pièce est rapprochée des consommations imputées par les infirmiers. Le directeur administratif
 * valide le rapprochement mais ne fait aucune autre écriture de stock (T42).
 */
@Component({
  selector: 'app-sorties-pour-seances',
  standalone: true,
  imports: [
    CommonModule, MatCardModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatDatepickerModule,
    MatNativeDateModule, MatButtonModule, MatIconModule, NgTableComponent,
    TranslateModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './sorties-pour-seances.component.html',
  styleUrl: './sorties-pour-seances.component.css',
})
export class SortiesPourSeancesComponent {
  protected readonly demiJournees: DemiJournee[] = ['MATIN', 'APRES_MIDI'];
  protected readonly pageSizes = STOCK_PAGE_SIZES;
  protected readonly defaultPageSize = STOCK_DEFAULT_PAGE_SIZE;
  protected readonly labels = inject(NgTableTranslatedLabels).labels;
  protected readonly clePiece = (p: SortiePourSeances): unknown => p.id;
  protected readonly cleLigne = (l: LigneSortiePourSeances): unknown => l.id;
  /** Pièce dont le détail est déroulé : surlignée (fonction recréée à chaque sélection pour que ng-table la relise). */
  protected readonly classeDePiece = computed(() => {
    const selectionnee = this.selectionId();
    return (p: SortiePourSeances): Record<string, boolean> => ({'piece-selectionnee': p.id === selectionnee});
  });
  private readonly actionsPieceCell = viewChild<TemplateRef<unknown>>('actionsPieceCell');
  private readonly quantiteCell = viewChild<TemplateRef<unknown>>('quantiteCell');
  private readonly ecartCell = viewChild<TemplateRef<unknown>>('ecartCell');
  private readonly motifCell = viewChild<TemplateRef<unknown>>('motifCell');

  protected readonly pieces = signal<SortiePourSeances[]>([]);
  protected readonly articles = signal<RefItem[]>([]);
  protected readonly selectionId = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly working = signal(false);
  protected readonly submitAttempted = signal(false);
  protected readonly form = signal<PieceForm>(this.formulaireInitial());
  protected readonly selection = computed(() => this.pieces().find(p => p.id === this.selectionId()) ?? null);
  protected readonly peutValider = computed(() => {
    const piece = this.selection();
    return !!piece && piece.statut === 'ENREGISTREE' && piece.lignes.every(l => !this.ecartSansMotif(l));
  });
  protected readonly peutSauvegarder = computed(() => {
    const f = this.form();
    return !!f.jour && f.lignes.length > 0
      && f.lignes.every(l => !!l.articleId && Number(l.quantite ?? 0) > 0);
  });
  private readonly api = inject(StockApiService);
  private readonly refApi = inject(ReferentialApiService);
  private readonly auth = inject(AuthStore);
  /** Enregistrement d'une pièce : gestionnaire de stock et administrateur. */
  protected readonly peutEnregistrer = computed(() => this.auth.hasRole('ADMIN') || this.auth.hasRole('PHARMACIEN'));
  /** Préparation et validation du rapprochement : aussi le directeur administratif. */
  protected readonly peutRapprocher = computed(() =>
    this.auth.hasRole('ADMIN') || this.auth.hasRole('PHARMACIEN') || this.auth.hasRole('DIRECTEUR_ADMINISTRATIF'));
  /** Le motif d'un écart est saisi par le gestionnaire de stock ou l'administrateur ; l'infirmier ne justifie pas. */
  protected readonly peutJustifier = computed(() => this.auth.hasRole('ADMIN') || this.auth.hasRole('PHARMACIEN'));
  private readonly snack = inject(MatSnackBar);
  private readonly translate = inject(TranslateService);
  private readonly langue = langueCourante();
  protected readonly libelleVide = computed(() => {
    this.langue();
    return this.translate.instant('STOCK.SORTIES_SEANCES.EMPTY') as string;
  });
  /** Pièces du centre : ng-table locale paginée, statut en pastille, bouton qui déroule le détail. */
  protected readonly colonnesPieces = computed<NgTableColumn<SortiePourSeances>[]>(() => {
    this.langue();
    const t = (cle: string): string => this.translate.instant(cle);
    const statut = (valeur: unknown): string => t('STOCK.SORTIES_SEANCES.STATUT.' + String(valeur));
    return [
      {
        id: 'reference', header: t('STOCK.REFERENCE_LABEL'), valueAccessor: p => p.reference, sortable: true,
        filter: {type: 'text', label: t('STOCK.REFERENCE_LABEL')},
      },
      {
        id: 'jour', header: t('STOCK.SORTIES_SEANCES.DAY_LABEL'), valueAccessor: p => jourCourt(p.jour),
        sortValueAccessor: p => p.jour, sortable: true,
      },
      {id: 'perimetre', header: t('STOCK.SORTIES_SEANCES.PERIMETER_LABEL'), valueAccessor: p => this.perimetre(p)},
      {
        id: 'statut', header: t('STOCK.SORTIES_SEANCES.STATUS_LABEL'), valueAccessor: p => p.statut, sortable: true,
        cell: {type: 'badge', tone: TONS_STATUT_SORTIE, label: statut},
        filter: {
          type: 'enum', label: t('STOCK.SORTIES_SEANCES.STATUS_LABEL'),
          options: STATUTS_SORTIE.map(s => ({value: s, label: statut(s)})),
        },
      },
      {id: 'lignes', header: t('STOCK.LINES_LABEL'), valueAccessor: p => p.lignes.length, sortable: true},
      {
        id: 'actions', header: t('COMMON.COL_ACTIONS'), valueAccessor: () => '', exportable: false,
        mobileRowActions: true, widthPx: 96, cellTemplate: this.actionsPieceCell() as NgTableColumn<SortiePourSeances>['cellTemplate'],
      },
    ];
  });
  /** Lignes de la pièce sélectionnée : quantités alignées à droite, écart coloré, motif saisi en ligne. */
  protected readonly colonnesLignes = computed<NgTableColumn<LigneSortiePourSeances>[]>(() => {
    this.langue();
    this.articles();
    const t = (cle: string): string => this.translate.instant(cle);
    type Gabarit = NgTableColumn<LigneSortiePourSeances>['cellTemplate'];
    const quantite = this.quantiteCell() as Gabarit;
    return [
      {
        id: 'article', header: t('CAHIER.CONSOMMABLES_ARTICLE'), valueAccessor: l => this.libelleArticle(l.articleId),
        sortable: true, filter: {type: 'text', label: t('CAHIER.CONSOMMABLES_ARTICLE')},
      },
      {
        id: 'sortie', header: t('STOCK.SORTIES_SEANCES.OUT_LABEL'), valueAccessor: l => l.quantiteSortie,
        sortable: true, cellTemplate: quantite,
      },
      {
        id: 'consomme', header: t('STOCK.SORTIES_SEANCES.CONSUMED_LABEL'), valueAccessor: l => l.quantiteConsommee,
        sortable: true, cellTemplate: quantite,
      },
      {
        id: 'ecart', header: t('STOCK.SORTIES_SEANCES.GAP_LABEL'), valueAccessor: l => l.ecart, sortable: true,
        cellTemplate: this.ecartCell() as Gabarit,
      },
      {
        id: 'motif', header: t('STOCK.SORTIES_SEANCES.REASON_LABEL'), valueAccessor: l => l.motif ?? '',
        minWidthPx: 240, textOverflow: 'wrap', cellTemplate: this.motifCell() as Gabarit,
      },
    ];
  });

  constructor() {
    this.recharger();
  }

  protected ajouterLigne(): void {
    this.form.update(f => ({...f, lignes: [...f.lignes, this.nouvelleLigne()]}));
  }

  protected retirerLigne(index: number): void {
    this.form.update(f => ({...f, lignes: f.lignes.filter((_, i) => i !== index)}));
  }

  protected modifierEntete(patch: Partial<Omit<PieceForm, 'lignes'>>): void {
    this.form.update(f => ({...f, ...patch}));
  }

  protected modifierLigne(index: number, patch: Partial<LigneForm>): void {
    this.form.update(f => ({
      ...f,
      lignes: f.lignes.map((l, i) => (i === index ? {...l, ...patch} : l)),
    }));
  }

  protected saisirQuantite(index: number, event: Event): void {
    const brut = (event.target as HTMLInputElement | null)?.value;
    const valeur = brut == null || brut === '' ? null : Number(brut);
    this.modifierLigne(index, {quantite: Number.isFinite(valeur ?? NaN) ? valeur : null});
  }

  protected saisirSalle(event: Event): void {
    this.modifierEntete({salle: (event.target as HTMLInputElement | null)?.value ?? ''});
  }

  protected enregistrer(): void {
    this.submitAttempted.set(true);
    const centerId = this.auth.centerId();
    const f = this.form();
    if (!centerId || !this.peutSauvegarder() || !f.jour) {
      return;
    }
    this.saving.set(true);
    this.api.createSortiePourSeances({
      centerId,
      jour: this.versIso(f.jour),
      salle: f.salle.trim() || null,
      demiJournee: f.demiJournee,
      lignes: f.lignes.map(l => ({articleId: l.articleId!, quantite: Number(l.quantite)})),
    }).subscribe({
      next: piece => {
        this.saving.set(false);
        this.submitAttempted.set(false);
        this.form.set(this.formulaireInitial());
        this.notifier('STOCK.SORTIES_SEANCES.SAVED_OK');
        this.pieces.update(liste => [piece, ...liste]);
        this.selectionId.set(piece.id);
      },
      error: e => {
        this.saving.set(false);
        this.notifierErreur(e, 'STOCK.SORTIES_SEANCES.SAVE_ERROR');
      },
    });
  }

  protected selectionner(piece: SortiePourSeances): void {
    this.selectionId.set(this.selectionId() === piece.id ? null : piece.id);
  }

  protected preparer(piece: SortiePourSeances): void {
    const centerId = this.auth.centerId();
    if (!centerId || this.working()) {
      return;
    }
    this.working.set(true);
    this.api.preparerLeRapprochement(piece.id, centerId).subscribe({
      next: maj => {
        this.working.set(false);
        this.remplacer(maj);
        this.selectionId.set(maj.id);
        this.notifier('STOCK.SORTIES_SEANCES.PREPARED_OK');
      },
      error: e => {
        this.working.set(false);
        this.notifierErreur(e, 'STOCK.SORTIES_SEANCES.PREPARE_ERROR');
      },
    });
  }

  protected justifier(piece: SortiePourSeances, ligne: LigneSortiePourSeances, event: Event): void {
    const centerId = this.auth.centerId();
    const motif = ((event.target as HTMLInputElement | null)?.value ?? '').trim();
    if (!centerId || !motif || motif === (ligne.motif ?? '')) {
      return;
    }
    this.api.justifierUnEcart(piece.id, ligne.id, centerId, motif).subscribe({
      next: maj => this.remplacer(maj),
      error: e => this.notifierErreur(e, 'STOCK.SORTIES_SEANCES.MOTIF_ERROR'),
    });
  }

  protected valider(piece: SortiePourSeances): void {
    const centerId = this.auth.centerId();
    if (!centerId || this.working() || !this.peutValider()) {
      return;
    }
    this.working.set(true);
    this.api.validerLeRapprochement(piece.id, centerId).subscribe({
      next: maj => {
        this.working.set(false);
        this.remplacer(maj);
        this.notifier('STOCK.SORTIES_SEANCES.VALIDATED_OK');
      },
      error: e => {
        this.working.set(false);
        this.notifierErreur(e, 'STOCK.SORTIES_SEANCES.VALIDATE_ERROR');
      },
    });
  }

  protected libelleArticle(articleId: string): string {
    const a = this.articles().find(x => x.id === articleId);
    if (!a) {
      return articleId;
    }
    const code = (a.code ?? '').trim();
    const libelle = (a.libelle ?? a.nom ?? '').trim();
    return code ? `${code} - ${libelle}` : libelle;
  }

  protected perimetre(piece: SortiePourSeances): string {
    const parties: string[] = [];
    if (piece.salle) {
      parties.push(piece.salle);
    }
    if (piece.demiJournee) {
      parties.push(this.translate.instant('STOCK.SORTIES_SEANCES.DEMI_JOURNEE.' + piece.demiJournee));
    }
    return parties.length > 0 ? parties.join(' · ') : this.translate.instant('STOCK.SORTIES_SEANCES.JOURNEE');
  }

  /** Un écart non nul exige un motif avant la validation (T40). */
  protected ecartSansMotif(ligne: LigneSortiePourSeances): boolean {
    return Number(ligne.ecart ?? 0) !== 0 && !(ligne.motif ?? '').trim();
  }

  protected afficherErreurQuantite(index: number): boolean {
    return this.submitAttempted() && Number(this.form().lignes[index]?.quantite ?? 0) <= 0;
  }

  protected afficherErreurArticle(index: number): boolean {
    return this.submitAttempted() && !this.form().lignes[index]?.articleId;
  }

  private recharger(): void {
    const centerId = this.auth.centerId();
    if (!centerId) {
      return;
    }
    this.api.listSortiesPourSeances(centerId).subscribe({next: liste => this.pieces.set(liste)});
    this.refApi.getArticles(centerId).subscribe({next: liste => this.articles.set(liste)});
  }

  private remplacer(piece: SortiePourSeances): void {
    this.pieces.update(liste => liste.map(p => (p.id === piece.id ? piece : p)));
  }

  private notifier(cle: string): void {
    this.snack.open(this.translate.instant(cle), this.translate.instant('COMMON.OK'), {duration: 2500});
  }

  private notifierErreur(e: { error?: { message?: string } }, cle: string): void {
    const detail = e?.error?.message;
    const message = detail
      ? this.translate.instant('STOCK.SORTIES_SEANCES.ERROR_DETAIL', {detail})
      : this.translate.instant(cle);
    this.snack.open(message, this.translate.instant('COMMON.RETRY'), {duration: 5000});
  }

  private versIso(date: Date): string {
    const d = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
    return d.toISOString().substring(0, 10);
  }

  private nouvelleLigne(): LigneForm {
    return {articleId: null, quantite: 1};
  }

  private formulaireInitial(): PieceForm {
    const demain = new Date();
    demain.setDate(demain.getDate() + 1);
    return {jour: demain, salle: '', demiJournee: null, lignes: [this.nouvelleLigne()]};
  }
}

