import {ChangeDetectionStrategy, Component, computed, inject, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {MatCardModule} from '@angular/material/card';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatSelectModule} from '@angular/material/select';
import {MatDatepickerModule} from '@angular/material/datepicker';
import {MatNativeDateModule} from '@angular/material/core';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatTableModule} from '@angular/material/table';
import {MatChipsModule} from '@angular/material/chips';
import {MatSnackBar} from '@angular/material/snack-bar';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {AuthStore} from '../../core/state/auth.store';
import {
  DemiJournee,
  LigneSortiePourSeances,
  SortiePourSeances,
  StockApiService
} from '../../core/api/stock-api.service';
import {ReferentialApiService, RefItem} from '../../core/api/referential-api.service';

type LigneForm = { articleId: string | null; quantite: number | null };

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
    MatNativeDateModule, MatButtonModule, MatIconModule, MatTableModule, MatChipsModule,
    TranslateModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './sorties-pour-seances.component.html',
  styleUrl: './sorties-pour-seances.component.css',
})
export class SortiesPourSeancesComponent {
  protected readonly colsPieces = ['reference', 'jour', 'perimetre', 'statut', 'lignes', 'actions'];
  protected readonly colsLignes = ['article', 'sortie', 'consomme', 'ecart', 'motif'];
  protected readonly demiJournees: DemiJournee[] = ['MATIN', 'APRES_MIDI'];

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

