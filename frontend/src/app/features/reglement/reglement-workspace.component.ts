import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
  TemplateRef,
  viewChild
} from '@angular/core';
import {DecimalPipe} from '@angular/common';
import {MatCardModule} from '@angular/material/card';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatSelectModule} from '@angular/material/select';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatTooltipModule} from '@angular/material/tooltip';
import {MatBadgeModule} from '@angular/material/badge';
import {MatDialog} from '@angular/material/dialog';
import {toSignal} from '@angular/core/rxjs-interop';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {NgTableColumn, NgTableComponent, NgTableRemoteQuery} from '@sbourahla/ng-table';
import {map, merge} from 'rxjs';
import {ReglementPreviewRow, ReglementStore} from './state/reglement.store';
import {MODES_DE_REGLEMENT} from './state/reglement-payment.util';
import {
  CriteresDeLaPage,
  filtrerEtTrierLaPage,
  TAILLES_DE_PAGE_REGLEMENT,
  TONS_ETAT_REGLEMENT,
  TONS_SOLDE_REGLEMENT,
} from './state/reglement-table.util';
import {AppShellStore} from '../../core/state/app-shell.store';
import {AuthStore} from '../../core/state/auth.store';
import {ReglementInvoiceRow} from '../../core/api/backend-api.service';
import {
  PaymentHistoryDialogComponent,
  PaymentHistoryDialogData
} from './payment-history-dialog/payment-history-dialog.component';
import {NgTableTranslatedLabels} from '../../shared/ng-table-labels';

@Component({
  selector: 'app-reglement-workspace',
  standalone: true,
  imports: [
    DecimalPipe,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatProgressBarModule,
    MatTooltipModule,
    MatBadgeModule,
    TranslateModule,
    NgTableComponent,
  ],
  templateUrl: './reglement-workspace.component.html',
  styleUrl: './reglement-workspace.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReglementWorkspaceComponent {
  protected readonly store = inject(ReglementStore);
  protected readonly ngTableLabels = inject(NgTableTranslatedLabels).labels;
  protected readonly taillesDePage = TAILLES_DE_PAGE_REGLEMENT;
  protected readonly montantRegleCellTemplate = viewChild<TemplateRef<any>>('montantRegleCell');
  protected readonly montantFactureCellTemplate = viewChild<TemplateRef<any>>('montantFactureCell');
  protected readonly codeReglementCellTemplate = viewChild<TemplateRef<any>>('codeReglementCell');
  protected readonly actionsCellTemplate = viewChild<TemplateRef<any>>('actionsCell');
  private readonly translate = inject(TranslateService);
  private readonly langue = toSignal(
    merge(this.translate.onLangChange, this.translate.onTranslationChange).pipe(map(() => Date.now())),
    {initialValue: 0});

  /** Tri et filtres de colonne courants de la table (appliqués à la page chargée). */
  private readonly criteresDeLaPage = signal<CriteresDeLaPage>({sort: null, filters: {}});

  protected readonly libelleVide = computed(() => {
    this.langue();
    return this.translate.instant('REGLEMENT_MODULE.TABLE.EMPTY');
  });

  protected readonly reglementColumns = computed<NgTableColumn<ReglementPreviewRow>[]>(() => {
    this.langue();
    const t = (cle: string): string => this.translate.instant(`REGLEMENT_MODULE.TABLE.${cle}`);
    const texte = (id: keyof ReglementPreviewRow & string, cle: string, minWidthPx: number):
      NgTableColumn<ReglementPreviewRow> => ({
      id, header: t(cle), valueAccessor: (row) => row[id] ?? '', sortable: true, resizable: true, minWidthPx,
      filter: {type: 'text', label: t(cle)},
    });
    return [
      texte('numeroFacture', 'NUMERO_FACTURE', 150),
      texte('numeroAssurance', 'NUMERO_ASSURANCE', 150),
      texte('patientNom', 'PATIENT_NOM', 150),
      texte('patientPrenom', 'PATIENT_PRENOM', 150),
      texte('caisse', 'CAISSE', 140),
      texte('agence', 'AGENCE', 140),
      texte('centrePayeur', 'CENTRE_PAYEUR', 160),
      {
        id: 'montantFacture', header: t('MONTANT_FACTURE'),
        valueAccessor: (row) => Number(row.montantFacture ?? 0),
        sortable: true, resizable: true, minWidthPx: 150,
        filter: {type: 'text', label: t('MONTANT_FACTURE')},
        cellTemplate: this.montantFactureCellTemplate() ?? undefined,
      },
      {
        id: 'montantRegle', header: t('MONTANT_REGLE'),
        valueAccessor: (row) => Number(row.montantRegle ?? 0),
        sortable: true, resizable: true, minWidthPx: 180,
        filter: {type: 'text', label: t('MONTANT_REGLE')},
        cellTemplate: this.montantRegleCellTemplate() ?? undefined,
      },
      {
        id: 'solde', header: t('SOLDE'), valueAccessor: (row) => row.soldeType,
        sortable: false, resizable: true, minWidthPx: 190, textOverflow: 'wrap',
        filterPredicate: (row, valeur) => this.soldeLabel(row).toLocaleLowerCase()
          .includes(valeur.trim().toLocaleLowerCase()),
        searchable: (row) => this.soldeLabel(row),
        exportValueAccessor: (row) => this.soldeLabel(row),
        filter: {type: 'text', label: t('SOLDE')},
        cell: {type: 'badge', tone: TONS_SOLDE_REGLEMENT, label: (_valeur, row) => this.soldeLabel(row)},
      },
      {
        id: 'etat', header: t('ETAT'), valueAccessor: (row) => row.etat,
        sortValueAccessor: (row) => this.statusLabel(row),
        sortable: true, resizable: true, minWidthPx: 140,
        filterPredicate: (row, valeur) => this.statusLabel(row).toLocaleLowerCase()
          .includes(valeur.trim().toLocaleLowerCase()),
        searchable: (row) => this.statusLabel(row),
        exportValueAccessor: (row) => this.statusLabel(row),
        filter: {type: 'text', label: t('ETAT')},
        cell: {type: 'badge', tone: TONS_ETAT_REGLEMENT, label: (_valeur, row) => this.statusLabel(row)},
      },
      {
        id: 'codeReglement', header: t('CODE_REGLEMENT'), valueAccessor: (row) => row.latestCodeReglement ?? '',
        sortable: true, resizable: true, minWidthPx: 170,
        filter: {type: 'text', label: t('CODE_REGLEMENT')},
        cellTemplate: this.codeReglementCellTemplate() ?? undefined,
      },
      {
        id: 'actions', header: t('ACTIONS'), valueAccessor: () => '',
        sortable: false, resizable: false, mobileRowActions: true, exportable: false,
        minWidthPx: 240, widthPx: 280, maxWidthPx: 360,
        cellTemplate: this.actionsCellTemplate() ?? undefined,
      },
    ];
  });

  /** Lignes de la page chargée (avec l'aperçu des brouillons), triées et filtrées par les colonnes. */
  protected readonly lignes = computed(() =>
    filtrerEtTrierLaPage(this.store.visibleRows(), this.reglementColumns(), this.criteresDeLaPage()));

  protected readonly rowKey = (row: ReglementPreviewRow): unknown => row.factureId;

  protected readonly rowClassFn = (row: ReglementPreviewRow) => ({
    'row-has-draft': !!row.hasDraft,
  });
  protected readonly modes = MODES_DE_REGLEMENT;
  protected readonly years =Array.from({length: 6}, (_, index) => new Date().getFullYear() - index);
  protected readonly months = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12];
  private readonly appShell = inject(AppShellStore);
  protected readonly centerId = computed(() => this.appShell.currentCenterId());
  private readonly auth = inject(AuthStore);
  protected readonly userId = computed(() => this.auth.username() ?? 'system');
  private readonly dialog = inject(MatDialog);

  constructor() {
    // Vider les champs de saisie après chaque sauvegarde réussie
    effect(() => {
      const key = this.store.saveCycleKey();
      if (key > 0) {
        this.clearPaymentInputs();
      }
    });
  }

  protected onYearChange(value: string): void {
    this.store.setYear(Number(value));
  }

  protected onMonthChange(value: string): void {
    this.store.setMonth(value ? Number(value) : null);
  }

  protected onCaisseChange(value: string): void {
    this.store.setCaisseId(value || null);
  }

  protected onAgenceChange(value: string): void {
    this.store.setAgenceId(value || null);
  }

  protected onCentrePayeurChange(value: string): void {
    this.store.setCentrePayeurId(value || null);
  }

  /**
   * Requête de la table (mode remote) : la page et sa taille sont demandées au serveur ; le tri et les filtres de
   * colonne, que l'API ne connaît pas, s'appliquent à la page chargée comme dans l'ancienne liste.
   */
  protected onRemoteQueryChange(requete: NgTableRemoteQuery): void {
    this.criteresDeLaPage.set({sort: requete.sort?.direction ? requete.sort : null, filters: requete.filters ?? {}});
    const taille = requete.page?.size && requete.page.size > 0 ? requete.page.size : this.store.pageSize();
    const page = requete.page?.index ?? this.store.pageIndex();
    if (page !== this.store.pageIndex() || taille !== this.store.pageSize()) {
      this.store.setPagination(taille !== this.store.pageSize() ? 0 : page, taille);
    }
  }

  protected onResetFilters(): void {
    this.store.clearFilters();
  }

  protected onRefresh(): void {
    const centerId = this.centerId();
    const userId = this.userId();
    if (!centerId) {
      return;
    }
    this.store.loadPage({centerId, userId, page: this.store.pageIndex(), size: this.store.pageSize()});
  }

  protected onPaymentInput(factureId: string, value: string): void {
    this.store.setPaymentDraft(factureId, value);
  }

  protected onPaymentEnter(event: Event): void {
    event.preventDefault();
    const currentInput = event.target as HTMLInputElement;
    const allInputs = Array.from(
      document.querySelectorAll<HTMLInputElement>('.reglement-data-list .payment-field input[type="number"]')
    );
    const currentIndex = allInputs.indexOf(currentInput);
    if (currentIndex >= 0 && currentIndex < allInputs.length - 1) {
      const next = allInputs[currentIndex + 1];
      next.focus();
      next.select();
    }
  }

  protected onModeChange(value: string): void {
    this.store.setModeReglement(value || null);
  }

  protected onReferenceInput(value: string): void {
    this.store.setReferencePaiement(value);
  }

  protected onBatchSave(): void {
    const centerId = this.centerId();
    if (!centerId) return;
    this.store.batchSavePayments({centerId});
  }

  protected readonly trackByFactureId = (_index: number, row: ReglementPreviewRow): string => row.factureId;

  protected hasMultiplePayments(row: ReglementPreviewRow): boolean {
    return (row.paymentCount ?? 0) > 1;
  }

  protected isPaymentLocked(row: ReglementPreviewRow): boolean {
    return row.soldeType === 'REGLE' && !row.hasDraft;
  }

  protected openHistory(row: ReglementInvoiceRow): void {
    const centerId = this.centerId();
    if (!centerId) return;
    this.dialog.open(PaymentHistoryDialogComponent, {
      data: {row, centerId} as PaymentHistoryDialogData,
      width: '700px',
      maxWidth: '98vw',
      panelClass: 'app-dialog',
    });
  }

  protected onExport(format: 'excel' | 'csv'): void {
    const centerId = this.centerId();
    if (!centerId) return;
    this.store.exportReglements({centerId, format});
  }

  protected isSavingRow(factureId: string): boolean {
    return this.store.savingFactureIds().includes(factureId);
  }

  protected statusLabel(row: ReglementPreviewRow): string {
    return this.translate.instant(`REGLEMENT_MODULE.STATUS.${row.etat}`);
  }

  protected soldeLabel(row: ReglementPreviewRow): string {
    if (row.soldeType === 'TROP_PERCU') {
      return this.translate.instant('REGLEMENT_MODULE.TABLE.SOLDE_OVERPAID', {amount: row.tropPercu});
    }
    if (row.soldeType === 'REGLE') {
      return this.translate.instant('REGLEMENT_MODULE.TABLE.SOLDE_SETTLED');
    }
    return this.translate.instant('REGLEMENT_MODULE.TABLE.SOLDE_REMAINING', {amount: row.reste});
  }

  protected multiplePaymentsTooltip(row: ReglementPreviewRow): string {
    return this.translate.instant('REGLEMENT_MODULE.TABLE.MULTIPLE_BADGE_TOOLTIP', {
      count: row.paymentCount ?? 0,
    });
  }

  private clearPaymentInputs(): void {
    document.querySelectorAll<HTMLInputElement>(
      '.reglement-data-list .payment-field input[type="number"]'
    ).forEach((input) => {
      input.value = '';
    });
  }

  protected monthLabel(month: number): string {
    return this.translate.instant(`REGLEMENT_MODULE.MONTHS.${month}`);
  }
}










