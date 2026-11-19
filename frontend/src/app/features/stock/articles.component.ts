import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
  TemplateRef,
  untracked,
  viewChild,
} from '@angular/core';
import {toSignal} from '@angular/core/rxjs-interop';
import {compatForm} from '@angular/forms/signals/compat';
import {FormField, FormRoot, maxLength, required} from '@angular/forms/signals';
import {MatCardModule} from '@angular/material/card';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatSnackBar} from '@angular/material/snack-bar';
import {MatCheckboxModule} from '@angular/material/checkbox';
import {MatSelectModule} from '@angular/material/select';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {NgTableColumn, NgTableComponent, NgTableRemoteQuery, NgTableTone} from '@sbourahla/ng-table';
import {map, merge} from 'rxjs';
import {AppShellStore} from '../../core/state/app-shell.store';
import {NgTableTranslatedLabels} from '../../shared/ng-table-labels';
import {
  ArticleFichePayload,
  ArticleStock,
  ConditionConservation,
  TypeTraitementAnemie,
} from '../../core/api/stock-api.service';
import {ArticleActiveFilter, ArticlesStore} from './state/articles.store';

interface ArticleFormModel {
  code: string;
  libelle: string;
  dci: string;
  formeGalenique: string;
  codeBarres: string;
  referenceFabricant: string;
  unite: string;
  uniteAchat: string;
  coefficientAchat: string;
  dosageParUnite: string;
  uniteDosage: string;
  fournisseurId: string;
  tvaTypeId: string;
  prixAchat: string;
  seuilAlerte: string;
  stockMax: string;
  gereParLot: boolean;
  peremptionObligatoire: boolean;
  conditionConservation: ConditionConservation | '';
  produitDangereux: boolean;
  dechetDasri: boolean;
  estTraitementAnemie: boolean;
  typeTraitementAnemie: TypeTraitementAnemie | '';
  compteStock: string;
  compteCharge: string;
}

const emptyForm = (): ArticleFormModel => ({
  code: '', libelle: '', dci: '', formeGalenique: '', codeBarres: '', referenceFabricant: '',
  unite: '', uniteAchat: '', coefficientAchat: '', dosageParUnite: '', uniteDosage: '',
  fournisseurId: '', tvaTypeId: '', prixAchat: '', seuilAlerte: '0', stockMax: '',
  gereParLot: true, peremptionObligatoire: false, conditionConservation: '', produitDangereux: false,
  dechetDasri: false, estTraitementAnemie: false, typeTraitementAnemie: '', compteStock: '', compteCharge: '',
});

/** Nombre saisi (virgule ou point) ; vide ou invalide = absent. */
const toNumber = (raw: string): number | null => {
  const text = (raw ?? '').trim().replace(',', '.');
  if (!text) return null;
  const value = Number(text);
  return Number.isFinite(value) ? value : null;
};

/** Tailles de page proposées par la liste des articles (pagination côté serveur). */
export const ARTICLE_PAGE_SIZES: readonly number[] = [10, 20, 50, 100];

/** Teinte de la pastille de statut d'un article. */
export const ARTICLE_STATUT_TONES: Record<string, NgTableTone> = {ACTIVE: 'success', INACTIVE: 'neutral'};

type CellTemplate = NgTableColumn<ArticleStock>['cellTemplate'];

const toText = (value: string): string | null => value.trim() || null;
const fromNumber = (value: number | null | undefined): string => (value == null ? '' : String(value));

/**
 * Fiche article complète : identité, unités et conditionnement (dont le dosage par unité qui relie la prescription
 * du médecin à la sortie de stock), achat et valorisation, conservation et sécurité. Liste en `<ng-table>` paginée
 * côté serveur (mode `remote`) ; recherche et filtre actif/inactif restent dans la barre au-dessus de la table.
 * Traçabilité : ArticlesComponent → ArticlesStore → StockApiService → /stock/referentiel/articles
 * → StockReferentialUseCase → Article (agrégat) → ArticleRepositoryPort / ArticleCatalogPort.
 */
@Component({
  selector: 'app-stock-articles',
  standalone: true,
  imports: [
    MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule,
    MatCheckboxModule, MatSelectModule, FormRoot, FormField, TranslateModule, NgTableComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './articles.component.html',
  styleUrl: './articles.component.css',
})
export class ArticlesComponent {
  protected readonly store = inject(ArticlesStore);
  protected readonly ngTableLabels = inject(NgTableTranslatedLabels).labels;
  protected readonly pageSizes = ARTICLE_PAGE_SIZES;
  protected readonly articleKey = (row: ArticleStock): unknown => row.id;
  private readonly translate = inject(TranslateService);
  private readonly lang = toSignal(
    merge(this.translate.onLangChange, this.translate.onTranslationChange).pipe(map(() => Date.now())),
    {initialValue: 0});
  private readonly numTemplate = viewChild<TemplateRef<unknown>>('numCell');
  private readonly actionsTemplate = viewChild<TemplateRef<unknown>>('actionsCell');
  protected readonly emptyLabel = computed(() => {
    this.lang();
    return this.translate.instant('STOCK.ARTICLES.EMPTY');
  });
  /** Colonnes de la liste : mêmes données qu'avant ; statut en pastille, stock aligné à droite, actions sans export. */
  protected readonly columns = computed<NgTableColumn<ArticleStock>[]>(() => {
    this.lang();
    const t = (key: string): string => this.translate.instant(key);
    const statut = (value: unknown): string =>
      t(value === 'ACTIVE' ? 'STOCK.ARTICLES.ACTIVE' : 'STOCK.ARTICLES.INACTIVE');
    return [
      {id: 'code', header: t('COMMON.CODE_LABEL'), valueAccessor: (a) => a.code},
      {id: 'libelle', header: t('STOCK.ARTICLE_LIBELLE_LABEL'), valueAccessor: (a) => a.libelle},
      {id: 'unite', header: t('STOCK.UNIT_LABEL'), valueAccessor: (a) => a.unite || '—'},
      {id: 'dosage', header: t('STOCK.ARTICLES.COL_DOSAGE'), valueAccessor: (a) => this.dosageLabel(a)},
      {
        id: 'stock', header: t('STOCK.ARTICLES.COL_STOCK'), valueAccessor: (a) => a.stockQuantity,
        cellTemplate: this.numTemplate() as CellTemplate,
      },
      {
        id: 'statut', header: t('STOCK.ARTICLES.COL_STATUT'),
        valueAccessor: (a) => (a.active ? 'ACTIVE' : 'INACTIVE'),
        cell: {type: 'badge', tone: ARTICLE_STATUT_TONES, label: statut},
        exportValueAccessor: (a) => statut(a.active ? 'ACTIVE' : 'INACTIVE'),
      },
      {
        id: 'actions', header: '', valueAccessor: () => '', exportable: false, searchable: false,
        mobileRowActions: true, cellTemplate: this.actionsTemplate() as CellTemplate,
      },
    ];
  });
  protected readonly rowClassFn = computed(() => {
    const editing = this.editingId();
    return (row: ArticleStock): Record<string, boolean> => ({editing: row.id === editing});
  });
  protected readonly editingId = signal<string | null>(null);
  protected readonly formModel = signal<ArticleFormModel>(emptyForm());
  protected readonly articleForm = compatForm(this.formModel, (form) => {
    required(form.code);
    maxLength(form.code, 50);
    required(form.libelle);
    maxLength(form.libelle, 150);
    required(form.unite);
    maxLength(form.unite, 30);
  });
  /** Dosage et unité de dosage vont ensemble (même règle que l'agrégat côté serveur). */
  protected readonly dosageCoherent = computed(() => {
    const m = this.formModel();
    return (toNumber(m.dosageParUnite) !== null) === !!m.uniteDosage.trim();
  });
  protected readonly stockMaxCoherent = computed(() => {
    const m = this.formModel();
    const max = toNumber(m.stockMax);
    return max === null || max >= (toNumber(m.seuilAlerte) ?? 0);
  });
  /** Comptes comptables de l'article : facultatifs, 1 à 20 lettres ou chiffres (même règle que le serveur). */
  protected readonly comptesCoherents = computed(() => {
    const m = this.formModel();
    return [m.compteStock, m.compteCharge].every((c) => !c.trim() || /^[0-9A-Za-z]{1,20}$/.test(c.trim()));
  });
  /** Aperçu de la conversion : « 1 seringue = 4000 UI ». */
  protected readonly conversionPreview = computed(() => {
    const m = this.formModel();
    const dosage = toNumber(m.dosageParUnite);
    return dosage !== null && m.unite.trim() && m.uniteDosage.trim()
      ? {unite: m.unite.trim(), dosage, uniteDosage: m.uniteDosage.trim()} : null;
  });
  protected readonly canSave = computed(() => {
    const m = this.formModel();
    return this.articleForm().valid() && !!m.code.trim() && !!m.libelle.trim() && !!m.unite.trim()
      && this.dosageCoherent() && this.stockMaxCoherent() && this.comptesCoherents()
      && (!m.estTraitementAnemie || !!m.typeTraitementAnemie)
      && !this.store.saving();
  });
  protected readonly filters: ArticleActiveFilter[] = ['active', 'inactive', 'all'];
  protected readonly conservations: ConditionConservation[] = ['AMBIANT', 'REFRIGERE', 'CONGELE'];

  private readonly shell = inject(AppShellStore);
  private readonly snack = inject(MatSnackBar);

  constructor() {
    effect(() => {
      if (!this.shell.currentCenterId()) return;
      untracked(() => {
        this.store.loadPage({page: 0, size: this.store.pageSize()});
        this.store.loadReferentiels();
      });
    });
    effect(() => {
      const message = this.store.successMessage();
      if (!message) return;
      untracked(() => {
        this.snack.open(this.translate.instant(message), this.translate.instant('COMMON.OK'), {duration: 2500});
        this.cancelEdit();
        this.store.clearMessages();
      });
    });
    effect(() => {
      const error = this.store.error();
      if (!error) return;
      untracked(() => {
        this.snack.open(this.translate.instant(error), this.translate.instant('COMMON.RETRY'), {duration: 4000});
        this.store.clearMessages();
      });
    });
  }

  protected onSearch(value: string): void {
    this.store.search(value);
  }

  protected onFilter(filter: ArticleActiveFilter): void {
    this.store.setActiveFilter(filter);
  }

  /**
   * Paginateur de la table (mode `remote`) : ne charge que la page demandée, et seulement si elle diffère de celle
   * déjà affichée (la première émission de la table ne relance pas le chargement initial).
   */
  protected onRemoteQuery(query: NgTableRemoteQuery): void {
    const index = query.page.index;
    const size = query.page.size || this.store.pageSize();
    if (index === this.store.pageIndex() && size === this.store.pageSize()) return;
    this.store.loadPage({page: index, size});
  }

  protected edit(article: ArticleStock): void {
    this.editingId.set(article.id);
    this.formModel.set({
      code: article.code, libelle: article.libelle, dci: article.dci ?? '',
      formeGalenique: article.formeGalenique ?? '', codeBarres: article.codeBarres ?? '',
      referenceFabricant: article.referenceFabricant ?? '', unite: article.unite ?? '',
      uniteAchat: article.uniteAchat ?? '', coefficientAchat: fromNumber(article.coefficientAchat),
      dosageParUnite: fromNumber(article.dosageParUnite), uniteDosage: article.uniteDosage ?? '',
      fournisseurId: article.fournisseurId ?? '', tvaTypeId: article.tvaTypeId ?? '',
      prixAchat: fromNumber(article.prixAchat), seuilAlerte: fromNumber(article.seuilAlerte ?? 0),
      stockMax: fromNumber(article.stockMax), gereParLot: article.gereParLot,
      peremptionObligatoire: !!article.peremptionObligatoire,
      conditionConservation: article.conditionConservation ?? '', produitDangereux: !!article.produitDangereux,
      dechetDasri: !!article.dechetDasri, estTraitementAnemie: !!article.typeTraitementAnemie,
      typeTraitementAnemie: article.typeTraitementAnemie ?? '',
      compteStock: article.compteStock ?? '', compteCharge: article.compteCharge ?? '',
    });
  }

  protected cancelEdit(): void {
    this.editingId.set(null);
    this.formModel.set(emptyForm());
  }

  protected toggleActive(article: ArticleStock): void {
    this.store.setActive({id: article.id, active: !article.active});
  }

  protected save(): void {
    if (!this.canSave()) return;
    const payload = this.toPayload(this.formModel());
    const id = this.editingId();
    if (id) this.store.update({id, payload}); else this.store.create(payload);
  }

  protected dosageLabel(article: ArticleStock): string {
    return article.dosageParUnite && article.uniteDosage
      ? `${article.dosageParUnite} ${article.uniteDosage} / ${article.unite}` : '—';
  }

  private toPayload(m: ArticleFormModel): ArticleFichePayload {
    return {
      centerId: '',
      code: m.code.trim(),
      libelle: m.libelle.trim(),
      dci: toText(m.dci),
      formeGalenique: toText(m.formeGalenique),
      codeBarres: toText(m.codeBarres),
      referenceFabricant: toText(m.referenceFabricant),
      unite: m.unite.trim(),
      uniteAchat: toText(m.uniteAchat),
      coefficientAchat: toNumber(m.coefficientAchat),
      dosageParUnite: toNumber(m.dosageParUnite),
      uniteDosage: toText(m.uniteDosage),
      fournisseurId: m.fournisseurId || null,
      tvaTypeId: m.tvaTypeId || null,
      prixAchat: toNumber(m.prixAchat),
      seuilAlerte: toNumber(m.seuilAlerte) ?? 0,
      stockMax: toNumber(m.stockMax),
      gereParLot: m.gereParLot,
      peremptionObligatoire: m.peremptionObligatoire,
      conditionConservation: m.conditionConservation || null,
      produitDangereux: m.produitDangereux,
      dechetDasri: m.dechetDasri,
      typeTraitementAnemie: m.estTraitementAnemie && m.typeTraitementAnemie ? m.typeTraitementAnemie : null,
      compteStock: toText(m.compteStock),
      compteCharge: toText(m.compteCharge),
    };
  }
}
