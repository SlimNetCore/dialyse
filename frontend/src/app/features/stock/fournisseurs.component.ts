import {ChangeDetectionStrategy, Component, computed, inject, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {compatForm} from '@angular/forms/signals/compat';
import {FormField, FormRoot, required} from '@angular/forms/signals';
import {MatCardModule} from '@angular/material/card';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatSnackBar} from '@angular/material/snack-bar';
import {toSignal} from '@angular/core/rxjs-interop';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {NgTableColumn, NgTableComponent, NgTableRemoteQuery} from '@sbourahla/ng-table';
import {map, merge} from 'rxjs';
import {AuthStore} from '../../core/state/auth.store';
import {Fournisseur, StockApiService} from '../../core/api/stock-api.service';
import {CorrespondanceDePage} from '../../core/api/page-serveur';
import {ListeServeur} from '../../core/api/liste-serveur';
import {NgTableTranslatedLabels} from '../../shared/ng-table-labels';

/** Taille de page par défaut de la liste des fournisseurs (paginée côté serveur, B1). */
export const FOURNISSEUR_PAGE_SIZE = 20;
export const FOURNISSEUR_PAGE_SIZES: readonly number[] = [10, 20, 50, 100];

/** Colonnes ⇒ paramètres de `GET /referentiel/fournisseurs/page` (RG-STK-087) ; recherche globale ⇒ `q`. */
export const CORRESPONDANCE_FOURNISSEURS: CorrespondanceDePage = {
  tris: {code: 'code', raisonSociale: 'raisonSociale', contact: 'contact'},
  filtres: {code: 'code', raisonSociale: 'raisonSociale', contact: 'contact'},
  recherche: 'q',
};

/**
 * Fournisseurs du centre : saisie d'un fournisseur et liste en `<ng-table>` paginée, triée et filtrée côté serveur.
 */
@Component({
  selector: 'app-fournisseurs',
  standalone: true,
  imports: [
    CommonModule, MatCardModule, MatFormFieldModule,
    MatInputModule, MatButtonModule, MatIconModule,
    FormRoot, FormField, TranslateModule, NgTableComponent,
  ],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './fournisseurs.component.html',
  styleUrl: './fournisseurs.component.css',
})
export class FournisseursComponent {
  protected readonly ngTableLabels = inject(NgTableTranslatedLabels).labels;
  protected readonly pageSize = FOURNISSEUR_PAGE_SIZE;
  protected readonly pageSizes = FOURNISSEUR_PAGE_SIZES;
  protected readonly supplierKey = (row: Fournisseur): unknown => row.id;
  private readonly translate = inject(TranslateService);
  private readonly lang = toSignal(
    merge(this.translate.onLangChange, this.translate.onTranslationChange).pipe(map(() => Date.now())),
    {initialValue: 0});
  protected readonly columns = computed<NgTableColumn<Fournisseur>[]>(() => {
    this.lang();
    const t = (key: string): string => this.translate.instant(key);
    return [
      {
        id: 'code', header: t('COMMON.CODE_LABEL'), valueAccessor: (f) => f.code ?? '', sortable: true,
        filter: {type: 'text', label: t('COMMON.CODE_LABEL')},
      },
      {
        id: 'raisonSociale', header: t('STOCK.COMPANY_NAME_LABEL'), valueAccessor: (f) => f.raisonSociale,
        sortable: true, filter: {type: 'text', label: t('STOCK.COMPANY_NAME_LABEL')},
      },
      {
        id: 'contact', header: t('STOCK.CONTACT_LABEL'), valueAccessor: (f) => f.contact ?? '', sortable: true,
        filter: {type: 'text', label: t('STOCK.CONTACT_LABEL')},
      },
      {id: 'telephone', header: t('STOCK.PHONE_LABEL'), valueAccessor: (f) => f.telephone ?? '', sortable: false},
    ];
  });
  private readonly api = inject(StockApiService);
  private readonly auth = inject(AuthStore);
  protected readonly liste = new ListeServeur<Fournisseur>(
    (parametres) => this.api.listFournisseursPage(this.auth.centerId() ?? '', parametres),
    CORRESPONDANCE_FOURNISSEURS, FOURNISSEUR_PAGE_SIZE);
  protected readonly saving = signal(false);
  protected readonly formModel = signal(this.createInitialForm());
  protected readonly supplierForm = compatForm(this.formModel, (form) => {
    required(form.raisonSociale);
  });
  protected readonly canSave = computed(() => !!this.formModel().raisonSociale.trim() && !this.saving());
  private readonly snack = inject(MatSnackBar);

  constructor() {
    if (this.auth.centerId()) this.liste.charger();
  }

  protected onRemoteQueryChange(requete: NgTableRemoteQuery): void {
    if (this.auth.centerId()) this.liste.onRemoteQueryChange(requete);
  }

  protected save(): void {
    const centerId = this.auth.centerId();
    const form = this.formModel();
    if (!centerId || !this.canSave()) {
      return;
    }
    this.saving.set(true);
    this.api.createFournisseur({
      centerId,
      raisonSociale: form.raisonSociale.trim(),
      code: form.code.trim() || undefined,
      contact: form.contact.trim() || undefined,
      telephone: form.telephone.trim() || undefined,
      email: form.email.trim() || undefined,
    })
      .subscribe({
        next: () => {
          this.snack.open(
            this.translate.instant('STOCK.FOURNISSEURS.SAVED_OK'),
            this.translate.instant('COMMON.OK'),
            {duration: 2500},
          );
          this.formModel.set(this.createInitialForm());
          this.reload();
        },
        complete: () => this.saving.set(false),
        error: () => {
          this.saving.set(false);
          this.snack.open(
            this.translate.instant('STOCK.FOURNISSEURS.SAVE_ERROR'),
            this.translate.instant('COMMON.RETRY'),
            {duration: 4000},
          );
        },
      });
  }

  private reload(): void {
    if (this.auth.centerId()) this.liste.recharger();
  }

  private createInitialForm() {
    return {
      code: '',
      raisonSociale: '',
      contact: '',
      telephone: '',
      email: '',
    };
  }
}


