import {ChangeDetectionStrategy, Component, computed, effect, inject, signal, TemplateRef, viewChild} from '@angular/core';
import {CommonModule} from '@angular/common';
import {toSignal} from '@angular/core/rxjs-interop';
import {merge, map} from 'rxjs';
import {NgTableColumn, NgTableComponent, NgTableRemoteQuery, NgTableTone} from '@sbourahla/ng-table';
import {NgTableTranslatedLabels} from '../../shared/ng-table-labels';
import {compatForm} from '@angular/forms/signals/compat';
import {applyEach, FormField, FormRoot, min, required} from '@angular/forms/signals';
import {MatCardModule} from '@angular/material/card';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatSelectModule} from '@angular/material/select';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatSnackBar} from '@angular/material/snack-bar';
import {TranslateModule} from '@ngx-translate/core';
import {TranslateService} from '@ngx-translate/core';
import {AuthStore} from '../../core/state/auth.store';
import {WebSocketService} from '../../core/ws/websocket.service';
import {BonCommande, Fournisseur, StatutBonCommande, StockApiService} from '../../core/api/stock-api.service';
import {CorrespondanceDePage} from '../../core/api/page-serveur';
import {ListeServeur} from '../../core/api/liste-serveur';
import {ReferentialApiService, RefItem} from '../../core/api/referential-api.service';

/** Statuts d'un bon de commande (`StatutDuBon` du serveur), dans l'ordre du cycle de vie ; pas d'annulation (ADR 0045). */
export const STATUTS_BON_COMMANDE: readonly StatutBonCommande[] = ['BROUILLON', 'VALIDE', 'RECU'];
/** Teinte de la pastille de statut. */
export const TONS_STATUT_BON_COMMANDE: Record<StatutBonCommande, NgTableTone> = {
  BROUILLON: 'warning',
  VALIDE: 'info',
  RECU: 'success',
};

/** Colonnes ⇒ paramètres de `GET /bons-commande/page` (RG-STK-088). */
export const CORRESPONDANCE_BONS_COMMANDE: CorrespondanceDePage = {
  tris: {reference: 'reference', statut: 'statut', lignes: 'lignes'},
  filtres: {reference: 'reference', statut: 'statut'},
};

@Component({
  selector: 'app-bons-commande',
  standalone: true,
  imports: [
    CommonModule, MatCardModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatButtonModule, MatIconModule,
    FormRoot, FormField, TranslateModule, NgTableComponent,
  ],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './bons-commande.component.html',
  styleUrl: './bons-commande.component.css',
})
export class BonsCommandeComponent {
  /** Liste paginée, triée et filtrée côté serveur (`GET /bons-commande/page`, B2). */
  protected readonly taillesDePage: readonly number[] = [10, 20, 50, 100];
  protected readonly ngTableLabels = inject(NgTableTranslatedLabels).labels;
  protected readonly cleDeBon = (b: BonCommande): unknown => b.id;
  protected readonly actionsTemplate = viewChild<TemplateRef<{ $implicit: BonCommande; row: BonCommande }>>('actionsBon');
  private readonly api = inject(StockApiService);
  private readonly auth = inject(AuthStore);
  protected readonly liste = new ListeServeur<BonCommande>(
    (parametres) => this.api.listBonsCommandePage(this.auth.centerId() ?? '', parametres),
    CORRESPONDANCE_BONS_COMMANDE, 20);
  protected readonly fournisseurs = signal<Fournisseur[]>([]);
  protected readonly articles = signal<RefItem[]>([]);
  protected readonly saving = signal(false);
  protected readonly recalcInProgress = signal(false);
  protected readonly formModel = signal({
    fournisseurId: null as string | null,
    lignes: [this.newLigne()],
  });
  protected readonly form = compatForm(this.formModel, (form) => {
    applyEach(form.lignes, (ligne) => {
      required(ligne.articleId);
      required(ligne.quantite);
      required(ligne.prixUnitaire);
      min(ligne.quantite, 0.0001);
      min(ligne.prixUnitaire, 0);
    });
  });
  protected readonly canSave = computed(() => {
    if (this.saving()) return false;
    const model = this.formModel();
    if (!model.lignes.length) return false;
    return model.lignes.every((l) => !!l.articleId && Number(l.quantite) > 0 && Number(l.prixUnitaire) >= 0);
  });
  private readonly refApi = inject(ReferentialApiService);
  private readonly ws = inject(WebSocketService);
  private readonly snack = inject(MatSnackBar);
  private readonly translate = inject(TranslateService);
  private readonly langue = toSignal(
    merge(this.translate.onLangChange, this.translate.onTranslationChange).pipe(map(() => Date.now())),
    {initialValue: 0});

  /** Colonnes de la liste : référence, statut en pastille, nombre de lignes, actions (hors export). */
  protected readonly colonnes = computed<NgTableColumn<BonCommande>[]>(() => {
    this.langue();
    const t = (cle: string): string => this.translate.instant(cle);
    const statut = (valeur: unknown): string => t(`STOCK.BON_STATUT.${String(valeur)}`);
    const actions = this.actionsTemplate() as NgTableColumn<BonCommande>['cellTemplate'];
    return [
      {
        id: 'reference', header: t('STOCK.REFERENCE_LABEL'), valueAccessor: (b) => b.reference, sortable: true,
        filter: {type: 'text', label: t('STOCK.REFERENCE_LABEL')},
      },
      {
        id: 'statut', header: t('COMMON.STATUS_LABEL'), valueAccessor: (b) => b.statut, sortable: true,
        cell: {type: 'badge', tone: TONS_STATUT_BON_COMMANDE, label: statut},
        searchable: (b) => statut(b.statut), exportValueAccessor: (b) => statut(b.statut),
        filter: {
          type: 'enum', label: t('COMMON.STATUS_LABEL'),
          options: STATUTS_BON_COMMANDE.map((s) => ({value: s, label: statut(s)})),
        },
      },
      {
        id: 'lignes', header: t('STOCK.LINES_LABEL'), valueAccessor: (b) => b.lignes?.length ?? 0, sortable: true,
      },
      {
        id: 'actions', header: t('COMMON.COL_ACTIONS'), valueAccessor: () => '', sortable: false,
        exportable: false, searchable: false, cellTemplate: actions, textOverflow: 'wrap',
      },
    ];
  });

  constructor() {
    this.reload();
    if (this.auth.centerId()) this.liste.charger();
    effect(() => {
      const evt = this.ws.lastEvent();
      const centerId = this.auth.centerId();
      if (!evt || !centerId || evt.type !== 'STOCK_RECALC_LOCKS_CHANGED' || evt.centerId !== centerId) {
        return;
      }
      const status = String((evt.payload as { status?: string } | undefined)?.status ?? '').toUpperCase();
      if (status === 'STARTED') {
        this.recalcInProgress.set(true);
        this.snack.open(
          this.translate.instant('STOCK.BON_COMMANDE.RECALC_STARTED_INFO'),
          this.translate.instant('COMMON.OK'),
          {duration: 4200},
        );
      }
      if (status === 'COMPLETED' || status === 'FINISHED') {
        this.recalcInProgress.set(false);
        this.refreshArticles();
        this.snack.open(
          this.translate.instant('STOCK.BON_COMMANDE.RECALC_COMPLETED_REFRESHED'),
          this.translate.instant('COMMON.OK'),
          {duration: 5200},
        );
      }
      if (status === 'FAILED') {
        this.recalcInProgress.set(false);
        this.snack.open(
          this.translate.instant('STOCK.BON_COMMANDE.RECALC_FAILED_INFO'),
          this.translate.instant('COMMON.OK'),
          {duration: 4200},
        );
      }
    });
  }

  protected onRemoteQueryChange(requete: NgTableRemoteQuery): void {
    if (this.auth.centerId()) this.liste.onRemoteQueryChange(requete);
  }

  protected addLigne(): void {
    this.formModel.update((model) => ({...model, lignes: [...model.lignes, this.newLigne()]}));
  }

  protected removeLigne(i: number): void {
    this.formModel.update((model) => ({
      ...model,
      lignes: model.lignes.filter((_, idx) => idx !== i),
    }));
  }

  protected total(): number {
    return this.formModel().lignes.reduce((sum, c) => {
      const q = Number(c.quantite ?? 0);
      const p = Number(c.prixUnitaire ?? 0);
      return sum + q * p;
    }, 0);
  }

  protected save(): void {
    const centerId = this.auth.centerId();
    const form = this.formModel();
    if (!centerId || !this.canSave() || this.recalcInProgress()) {
      return;
    }
    const lignes = form.lignes
      .filter((l) => !!l.articleId)
      .map((l) => ({
        articleId: String(l.articleId),
        quantite: Number(l.quantite),
        prixUnitaire: Number(l.prixUnitaire),
      }));
    this.saving.set(true);
    this.api.createBonCommande({
      centerId,
      fournisseurId: form.fournisseurId ?? undefined,
      userId: this.auth.username() ?? undefined,
      lignes,
    }).subscribe({
      next: () => {
        this.snack.open(
          this.translate.instant('STOCK.BON_COMMANDE.CREATED_OK'),
          this.translate.instant('COMMON.OK'),
          {duration: 2500},
        );
        this.formModel.set({fournisseurId: null, lignes: [this.newLigne()]});
        this.reload();
      },
      complete: () => this.saving.set(false),
      error: () => {
        this.saving.set(false);
        this.snack.open(
          this.translate.instant('STOCK.BON_COMMANDE.CREATE_ERROR'),
          this.translate.instant('COMMON.RETRY'),
          {duration: 4000},
        );
      },
    });
  }

  protected valider(b: BonCommande): void {
    const centerId = this.auth.centerId();
    if (!centerId) {
      return;
    }
    this.api.validerBonCommande(b.id, centerId, this.auth.username() ?? undefined).subscribe({
      next: () => {
        this.snack.open(
          this.translate.instant('STOCK.BON_COMMANDE.VALIDATED_OK'),
          this.translate.instant('COMMON.OK'),
          {duration: 2000},
        );
        this.reload();
      },
      error: () => this.snack.open(
        this.translate.instant('STOCK.BON_COMMANDE.VALIDATION_ERROR'),
        this.translate.instant('COMMON.RETRY'),
        {duration: 4000},
      ),
    });
  }

  protected transformer(b: BonCommande): void {
    const centerId = this.auth.centerId();
    if (!centerId) {
      return;
    }
    this.api.fromBonCommande({centerId, bonCommandeId: b.id, userId: this.auth.username() ?? undefined}).subscribe({
      next: () => {
        this.snack.open(
          this.translate.instant('STOCK.BON_COMMANDE.TRANSFORM_OK'),
          this.translate.instant('COMMON.OK'),
          {duration: 3000},
        );
        this.reload();
      },
      error: () => this.snack.open(
        this.translate.instant('STOCK.BON_COMMANDE.TRANSFORM_ERROR'),
        this.translate.instant('COMMON.RETRY'),
        {duration: 4000},
      ),
    });
  }

  private newLigne() {
    return {
      articleId: null as string | null,
      quantite: 1,
      prixUnitaire: 0,
    };
  }

  private reload(): void {
    const centerId = this.auth.centerId();
    if (!centerId) {
      return;
    }
    if (this.liste.parametresCourants()) this.liste.recharger();
    this.api.listFournisseurs(centerId).subscribe({next: (f) => this.fournisseurs.set(f)});
    this.refreshArticles();
  }

  private refreshArticles(): void {
    const centerId = this.auth.centerId();
    if (!centerId) {
      return;
    }
    this.refApi.getArticles(centerId).subscribe({next: (a) => this.articles.set(a)});
  }

  protected articleLabel(a: RefItem): string {
    const code = (a.code ?? '').trim();
    const libelle = (a.libelle ?? a.nom ?? '').trim();
    const unite = (a.unite ?? '').trim();
    const left = code ? `${code} - ${libelle}` : libelle;
    return unite ? `${left} (${unite})` : left;
  }
}



