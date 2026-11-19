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
import {RouterLink} from '@angular/router';
import {MatCardModule} from '@angular/material/card';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatSelectModule} from '@angular/material/select';
import {MatTooltipModule} from '@angular/material/tooltip';
import {MatSnackBar} from '@angular/material/snack-bar';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {FacturationStore} from './state/facturation.store';
import {AppShellStore} from '../../core/state/app-shell.store';
import {AuthStore} from '../../core/state/auth.store';
import {BackendApiService, FacturationPreviewInvoice} from '../../core/api/backend-api.service';
import {ConfigurableListComponent, SharedListColumn} from '../../shared/configurable-list.component';
import {MOTIFS_ABSENCE, MotifAbsence} from '../../core/api/absence-patient-api.service';
import {exclusionEstValide} from './state/facturation-exclusion';
import {estSuccesDeCloture} from './state/facturation-periodes';
import {synthesePrintError} from './state/facturation-errors';
import {anneesDesMois} from './state/facturation-periodes';
import {ROLES_SYNTHESE_FACTURATION} from '../../core/api/http-error-detail';
import {etapeCourante, FacturationEtapesComponent} from './etapes/facturation-etapes.component';

/** Exclusion en cours de saisie pour une séance de la simulation. */
type ExclusionDraft = { seanceId: string; motif: MotifAbsence | null; commentaire: string };

@Component({
  selector: 'app-facturation-workspace',
  standalone: true,
  imports: [
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatTooltipModule,
    TranslateModule,
    DecimalPipe,
    ConfigurableListComponent,
    FacturationEtapesComponent,
  ],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './facturation-workspace.component.html',
  styleUrl: './facturation-workspace.component.css',
})
export class FacturationWorkspaceComponent {
  protected readonly store = inject(FacturationStore);
  private readonly appShell = inject(AppShellStore);
  private readonly auth = inject(AuthStore);
  private readonly translate = inject(TranslateService);
  private readonly api = inject(BackendApiService);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly currentCenterId = computed(() => this.appShell.currentCenterId());
  protected readonly currentUsername = computed(() => this.auth.username() ?? 'system');

  /** La synthèse mensuelle est réservée à ADMIN et SECRETAIRE (le backend refuse les autres en 403). */
  protected readonly canPrintSynthese = computed(() => ROLES_SYNTHESE_FACTURATION.some((role) => this.auth.hasRole(role)));

  /** Message de succès du calcul : ceux de la clôture mensuelle appartiennent à l'écran d'administration. */
  protected readonly succesDuCalcul = computed(() => {
    const message = this.store.successMessage();
    return estSuccesDeCloture(message) ? null : message;
  });

  /** Étape courante de l'indicateur compact (RG-FAC-049). */
  protected readonly etape = computed(() =>
    etapeCourante({preview: this.store.preview(), successMessage: this.succesDuCalcul()}));

  /**
   * La clôture mensuelle vit dans Administration › Paramétrage › Clôtures mensuelles : le lien n'est montré qu'à l'ADMIN,
   * seul profil autorisé à la lire et à l'exercer (le serveur refuse les autres).
   */
  protected readonly estAdmin = computed(() => this.auth.hasRole('ADMIN'));

  protected readonly motifs = MOTIFS_ABSENCE;
  protected readonly exclusionDraft = signal<ExclusionDraft | null>(null);
  protected readonly exclusionValid = computed(() => {
    const draft = this.exclusionDraft();
    return !!draft && exclusionEstValide(draft.motif, draft.commentaire);
  });

  protected readonly invoiceExpandCellTemplate = viewChild<TemplateRef<any>>('invoiceExpandCell');
  protected readonly invoiceNumeroCellTemplate = viewChild<TemplateRef<any>>('invoiceNumeroCell');
  protected readonly invoicePatientCellTemplate = viewChild<TemplateRef<any>>('invoicePatientCell');
  protected readonly invoiceStatusCellTemplate = viewChild<TemplateRef<any>>('invoiceStatusCell');
  protected readonly invoiceSeancesCellTemplate = viewChild<TemplateRef<any>>('invoiceSeancesCell');
  protected readonly invoiceAmountCellTemplate = viewChild<TemplateRef<any>>('invoiceAmountCell');
  protected readonly invoiceDetailTemplateRef = viewChild<TemplateRef<any>>('invoiceDetailRow');

  protected readonly invoiceColumns = computed<SharedListColumn<FacturationPreviewInvoice>[]>(() => [
    {
      id: 'expand',
      headerKey: '',
      valueAccessor: () => '',
      sortable: false,
      resizable: false,
      widthPx: 64,
      minWidthPx: 56,
      maxWidthPx: 76,
      cellTemplate: this.invoiceExpandCellTemplate() ?? undefined,
    },
    {
      id: 'numeroFacture',
      headerKey: 'FACTURATION.PREVIEW.TABLE.NUMERO',
      valueAccessor: (row) => row.numeroFacture ?? '',
      sortable: true,
      resizable: true,
      minWidthPx: 170,
      filter: {type: 'text'},
      cellTemplate: this.invoiceNumeroCellTemplate() ?? undefined,
      copy: true,
    },
    {
      id: 'patient',
      headerKey: 'FACTURATION.PREVIEW.TABLE.PATIENT',
      valueAccessor: (row) => `${row.patientCode ?? ''} ${row.patientFullName ?? ''}`.trim(),
      sortValueAccessor: (row) => row.patientFullName ?? '',
      sortable: true,
      resizable: true,
      minWidthPx: 220,
      filter: {type: 'text'},
      cellTemplate: this.invoicePatientCellTemplate() ?? undefined,
    },
    {
      id: 'patientStatusSnapshot',
      headerKey: 'FACTURATION.PREVIEW.TABLE.PATIENT_STATUS',
      valueAccessor: (row) => row.patientStatusSnapshot ?? '',
      sortable: true,
      resizable: true,
      minWidthPx: 140,
      filter: {type: 'text'},
      cellTemplate: this.invoiceStatusCellTemplate() ?? undefined,
    },
    {
      id: 'seancesCount',
      headerKey: 'FACTURATION.PREVIEW.TABLE.SESSIONS',
      valueAccessor: (row) => row.seances?.length ?? 0,
      sortValueAccessor: (row) => row.seances?.length ?? 0,
      sortable: true,
      resizable: true,
      minWidthPx: 110,
      cellTemplate: this.invoiceSeancesCellTemplate() ?? undefined,
    },
    {
      id: 'totalHt',
      headerKey: 'FACTURATION.PREVIEW.INVOICE_HT',
      valueAccessor: (row) => Number(row.totalHt ?? 0),
      sortValueAccessor: (row) => Number(row.totalHt ?? 0),
      sortable: true,
      resizable: true,
      minWidthPx: 120,
      cellTemplate: this.invoiceAmountCellTemplate() ?? undefined,
    },
    {
      id: 'totalTva',
      headerKey: 'FACTURATION.PREVIEW.INVOICE_TVA',
      valueAccessor: (row) => Number(row.totalTva ?? 0),
      sortValueAccessor: (row) => Number(row.totalTva ?? 0),
      sortable: true,
      resizable: true,
      minWidthPx: 120,
      cellTemplate: this.invoiceAmountCellTemplate() ?? undefined,
    },
    {
      id: 'totalTtc',
      headerKey: 'FACTURATION.PREVIEW.INVOICE_TTC',
      valueAccessor: (row) => Number(row.totalTtc ?? 0),
      sortValueAccessor: (row) => Number(row.totalTtc ?? 0),
      sortable: true,
      resizable: true,
      minWidthPx: 130,
      cellTemplate: this.invoiceAmountCellTemplate() ?? undefined,
    },
  ]);

  protected readonly invoiceRowKey = (row: FacturationPreviewInvoice): string => row.previewKey;

  protected readonly canExpandInvoice = (row: FacturationPreviewInvoice): boolean =>
    (row.seances?.length ?? 0) > 0;

  protected readonly invoiceRowClassFn = () => ({'invoice-row': true});

  constructor() {
    effect(() => {
      const centerId = this.currentCenterId();
      if (!centerId) {
        return;
      }
      this.store.setActiveCenterId(centerId);
      this.store.loadSettings({centerId});
      this.store.loadForfaits({centerId});
    });

    // Charge les mois de toute année visée par la période choisie (une fois par année) : la validation et les actions
    // sur une séance d'un mois clos sont bloquées ici, la clôture elle-même se fait dans le paramétrage.
    effect(() => {
      const centerId = this.currentCenterId();
      if (!centerId) {
        return;
      }
      const chargees = this.store.anneesChargees();
      const voulues = new Set(anneesDesMois(this.store.moisVises()));
      for (const annee of voulues) {
        if (!chargees.includes(annee)) {
          this.store.loadPeriodes({centerId, annee});
        }
      }
    });
  }

  protected onPeriodMode(mode: 'month' | 'custom'): void {
    this.store.setPeriodMode(mode);
  }

  protected onMonthChange(value: string): void {
    this.store.setMonth(value);
  }

  protected onStartDateChange(value: string): void {
    this.store.setStartDate(value);
  }

  protected onEndDateChange(value: string): void {
    this.store.setEndDate(value);
  }

  protected onCalculatePreview(): void {
    const centerId = this.currentCenterId();
    if (!centerId) {
      return;
    }
    this.store.calculatePreview({centerId});
  }

  protected onValidateFacturation(): void {
    const centerId = this.currentCenterId();
    if (!centerId) {
      return;
    }
    this.store.validateFacturation({
      centerId,
      userId: this.currentUsername(),
    });
  }

  protected onOpenExclusion(seanceId: string): void {
    this.exclusionDraft.set({seanceId, motif: null, commentaire: ''});
  }

  protected onCancelExclusion(): void {
    this.exclusionDraft.set(null);
  }

  protected onExclusionMotif(motif: MotifAbsence): void {
    this.exclusionDraft.update((draft) => (draft ? {...draft, motif} : draft));
  }

  protected onExclusionCommentaire(commentaire: string): void {
    this.exclusionDraft.update((draft) => (draft ? {...draft, commentaire} : draft));
  }

  /** Envoie l'exclusion avec son motif (et son commentaire, obligatoire pour AUTRE). */
  protected onConfirmExclusion(): void {
    const centerId = this.currentCenterId();
    const draft = this.exclusionDraft();
    if (!centerId || !draft || !draft.motif || !exclusionEstValide(draft.motif, draft.commentaire)) {
      return;
    }
    this.store.removeSeanceFromPreview({
      centerId,
      userId: this.currentUsername(),
      seanceId: draft.seanceId,
      motif: draft.motif,
      commentaire: draft.commentaire,
    });
    this.exclusionDraft.set(null);
  }

  protected onReincludeSeance(seanceId: string): void {
    const centerId = this.currentCenterId();
    if (!centerId) {
      return;
    }
    this.store.reincludeSeanceInPreview({centerId, seanceId});
  }

  protected onPreviewSeanceForfaitChange(seanceId: string, forfaitId: string | null): void {
    const centerId = this.currentCenterId();
    if (!centerId || !forfaitId) {
      return;
    }
    this.store.updateSeanceForfaitInPreview({
      centerId,
      userId: this.currentUsername(),
      seanceId,
      forfaitId,
    });
  }

  /** Valider est bloqué si un mois couvert par la période choisie est clos. */
  protected readonly validationBloquee = computed(() => this.store.moisClosVises().length > 0);

  /** Mois de la date d'une séance (AAAA-MM) clos : exclusion, réinclusion et changement de forfait sont refusés. */
  protected moisDeSeanceClos(seanceDate: string | null | undefined): boolean {
    return !!seanceDate && this.store.moisEstClos(seanceDate.slice(0, 7));
  }

  protected seanceStatusClass(status: string | null | undefined): string {
    const normalized = (status ?? '').toUpperCase();
    if (normalized.includes('TERMINE') || normalized.includes('FACTUREE')) {
      return 'status-done';
    }
    if (normalized.includes('EN_COURS')) {
      return 'status-in-progress';
    }
    return 'status-pending';
  }

  protected patientStatusClass(status: string | null | undefined): string {
    const s = (status ?? '').toUpperCase();
    if (s === 'PERMANENT') return 'pat-status-permanent';
    if (s.includes('TRANSFER')) return 'pat-status-transferred';
    if (s === 'VACANCIER') return 'pat-status-vacancier';
    if (s === 'DÉCÉDÉ' || s === 'DECEDE') return 'pat-status-deceased';
    if (s === 'GREFFÉ' || s === 'GREFFE') return 'pat-status-greffe';
    return 'pat-status-default';
  }

  protected forfaitOptions(): Array<{ id: string; label: string; price: number | null }> {
    return this.store.forfaits().map((item) => ({
      id: item.id,
      label: this.resolveForfaitLabel(item),
      price: this.resolveForfaitPrice(item),
    }));
  }

  protected onPrintMonthlySummary(): void {
    const centerId = this.currentCenterId();
    if (!centerId || !this.canPrintSynthese()) {
      return;
    }
    const period = this.resolveSummaryPeriod();
    this.api.printFacturationSynthese({
      centerId,
      periodStart: period.start,
      periodEnd: period.end,
      format: 'PDF',
    }).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        window.open(url, '_blank', 'noopener');
        setTimeout(() => URL.revokeObjectURL(url), 10_000);
      },
      error: async (err: unknown) => {
        const {message, translatable} = await synthesePrintError(err);
        this.snackBar.open(
          translatable ? this.translate.instant(message) : message,
          this.translate.instant('COMMON.OK'),
          {duration: 6000}
        );
      },
    });
  }

  private resolveForfaitLabel(item: {
    code?: string | null;
    nom?: string | null;
    label?: string | null;
    libelle?: string | null
  }): string {
    const code = item.code ?? '';
    const label = item.label ?? item.nom ?? '';
    if (code && label) {
      return `${code} - ${label}`;
    }
    return label || code || 'Forfait';
  }

  private resolveForfaitPrice(item: { libelle?: string | null; prix?: number | null }): number | null {
    if (typeof item.prix === 'number') {
      return item.prix;
    }
    if (item.libelle == null) {
      return null;
    }
    const parsed = Number(item.libelle);
    return Number.isFinite(parsed) ? parsed : null;
  }

  private resolveSummaryPeriod(): { start: string; end: string } {
    if (this.store.periodMode() === 'custom') {
      return {
        start: this.store.startDate(),
        end: this.store.endDate(),
      };
    }
    const month = this.store.month();
    const [yearRaw, monthRaw] = month.split('-');
    const year = Number(yearRaw);
    const monthValue = Number(monthRaw);
    if (!year || !monthValue) {
      const now = new Date();
      const fallbackMonth = `${now.getUTCMonth() + 1}`.padStart(2, '0');
      const start = `${now.getUTCFullYear()}-${fallbackMonth}-01`;
      const end = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() + 1, 0)).toISOString().slice(0, 10);
      return {start, end};
    }
    const start = `${year}-${`${monthValue}`.padStart(2, '0')}-01`;
    const end = new Date(Date.UTC(year, monthValue, 0)).toISOString().slice(0, 10);
    return {start, end};
  }
}
