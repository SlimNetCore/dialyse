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
import {DatePipe, DecimalPipe} from '@angular/common';
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
import {BaseChartDirective} from 'ng2-charts';
import {Chart, ChartData, ChartOptions, registerables} from 'chart.js';
import {FacturationStore} from './state/facturation.store';
import {AppShellStore} from '../../core/state/app-shell.store';
import {AuthStore} from '../../core/state/auth.store';
import {BackendApiService, FacturationPreviewInvoice} from '../../core/api/backend-api.service';
import {ConfigurableListComponent, SharedListColumn} from '../../shared/configurable-list.component';
import {MOTIFS_ABSENCE, MotifAbsence} from '../../core/api/absence-patient-api.service';
import {exclusionEstValide} from './state/facturation-exclusion';
import {synthesePrintError} from './state/facturation-errors';
import {anneesDesMois, MOTIF_REOUVERTURE_MAX, motifDeReouvertureValide} from './state/facturation-periodes';
import {ROLES_SYNTHESE_FACTURATION} from '../../core/api/http-error-detail';

/** Exclusion en cours de saisie pour une séance de la simulation. */
type ExclusionDraft = { seanceId: string; motif: MotifAbsence | null; commentaire: string };

Chart.register(...registerables);

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
    BaseChartDirective,
    TranslateModule,
    DecimalPipe,
    DatePipe,
    ConfigurableListComponent,
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

  protected readonly motifs = MOTIFS_ABSENCE;
  protected readonly exclusionDraft = signal<ExclusionDraft | null>(null);
  protected readonly exclusionValid = computed(() => {
    const draft = this.exclusionDraft();
    return !!draft && exclusionEstValide(draft.motif, draft.commentaire);
  });

  /** Clôture / réouverture mensuelle (F7) : confirmation de clôture et saisie du motif de réouverture. */
  protected readonly clotureAConfirmer = signal<string | null>(null);
  protected readonly reouvertureDraft = signal<{ mois: string; motif: string } | null>(null);
  protected readonly reouvertureValide = computed(() => {
    const draft = this.reouvertureDraft();
    return !!draft && motifDeReouvertureValide(draft.motif) !== '';
  });
  protected readonly motifMax = MOTIF_REOUVERTURE_MAX;
  protected readonly anneeOptions = computed(() => {
    const courante = new Date().getUTCFullYear();
    const choisie = this.store.anneePeriodes();
    const debut = Math.min(courante - 4, choisie);
    const fin = Math.max(courante + 1, choisie);
    return Array.from({length: fin - debut + 1}, (_, i) => debut + i);
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

  protected readonly revenueChartData = computed<ChartData<'line'>>(() => {
    const points = this.store.revenueTrend();
    return {
      labels: points.map((point) => this.monthLabel(point.month)),
      datasets: [
        {
          label: this.translate.instant('FACTURATION.DASHBOARD.CHART.REVENUE'),
          data: points.map((point) => Number(point.revenueHt ?? 0)),
          borderColor: this.themeColor('--app-primary', '#26a69a'),
          backgroundColor: this.themeColor('--app-primary-soft', 'rgba(38, 166, 154, 0.20)'),
          tension: 0.3,
          fill: true,
        },
      ],
    };
  });
  protected readonly byCaisseRows = computed(() => {
    const rows = this.store.dashboard()?.byInsurance ?? [];
    const buckets = [
      {code: 'CNAS', label: 'CNAS', seancesCount: 0, patientsCount: 0},
      {code: 'CASNOS', label: 'CASNOS', seancesCount: 0, patientsCount: 0},
      {code: 'CAMSSP', label: 'CAMSSP', seancesCount: 0, patientsCount: 0},
    ];
    for (const row of rows) {
      const key = `${row.code ?? ''} ${row.label ?? ''}`.toUpperCase();
      const target = buckets.find((bucket) => key.includes(bucket.code));
      if (!target) {
        continue;
      }
      target.seancesCount += Number(row.seancesCount ?? 0);
      target.patientsCount += Number(row.patientsCount ?? 0);
    }
    return buckets;
  });

  constructor() {
    effect(() => {
      const centerId = this.currentCenterId();
      if (!centerId) {
        return;
      }
      this.store.setActiveCenterId(centerId);
      this.store.loadSettings({centerId});
      this.store.loadForfaits({centerId});
      this.store.loadDashboard({centerId, month: this.store.month()});
      this.store.loadRevenueTrend({centerId, endingMonth: this.store.month(), months: 6});
    });

    // Charge les mois de l'année affichée et de toute année visée par la période choisie (une fois par année).
    effect(() => {
      const centerId = this.currentCenterId();
      if (!centerId) {
        return;
      }
      const chargees = this.store.anneesChargees();
      const voulues = new Set([this.store.anneePeriodes(), ...anneesDesMois(this.store.moisVises())]);
      for (const annee of voulues) {
        if (!chargees.includes(annee)) {
          this.store.loadPeriodes({centerId, annee});
        }
      }
    });

    effect(() => {
      const centerId = this.currentCenterId();
      const success = this.store.successMessage();
      if (!centerId || success !== 'FACTURATION.SUCCESS.VALIDATED') {
        return;
      }
      this.store.loadDashboard({centerId, month: this.store.month()});
      this.store.loadRevenueTrend({centerId, endingMonth: this.store.month(), months: 6});
    });
  }

  protected onPeriodMode(mode: 'month' | 'custom'): void {
    this.store.setPeriodMode(mode);
  }

  protected onMonthChange(value: string): void {
    this.store.setMonth(value);
    const centerId = this.currentCenterId();
    if (centerId && /^\d{4}-\d{2}$/.test(value)) {
      this.store.loadDashboard({centerId, month: value});
      this.store.loadRevenueTrend({centerId, endingMonth: value, months: 6});
    }
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

  protected onAnneePeriodes(annee: number): void {
    this.store.setAnneePeriodes(annee);
  }

  protected onRafraichirPeriodes(): void {
    const centerId = this.currentCenterId();
    if (centerId) {
      this.store.loadPeriodes({centerId, annee: this.store.anneePeriodes()});
    }
  }

  protected onDemanderCloture(mois: string): void {
    this.reouvertureDraft.set(null);
    this.clotureAConfirmer.set(mois);
  }

  protected onAnnulerCloture(): void {
    this.clotureAConfirmer.set(null);
  }

  protected onConfirmerCloture(): void {
    const centerId = this.currentCenterId();
    const mois = this.clotureAConfirmer();
    if (!centerId || !mois) {
      return;
    }
    this.store.cloturerMois({centerId, mois});
    this.clotureAConfirmer.set(null);
  }

  protected onOuvrirReouverture(mois: string): void {
    this.clotureAConfirmer.set(null);
    this.reouvertureDraft.set({mois, motif: ''});
  }

  protected onAnnulerReouverture(): void {
    this.reouvertureDraft.set(null);
  }

  protected onMotifReouverture(motif: string): void {
    this.reouvertureDraft.update((draft) => (draft ? {...draft, motif} : draft));
  }

  protected onConfirmerReouverture(): void {
    const centerId = this.currentCenterId();
    const draft = this.reouvertureDraft();
    const motif = draft ? motifDeReouvertureValide(draft.motif) : '';
    if (!centerId || !draft || !motif) {
      return;
    }
    this.store.rouvrirMois({centerId, mois: draft.mois, motif});
    this.reouvertureDraft.set(null);
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

  protected refreshDashboard(): void {
    const centerId = this.currentCenterId();
    if (!centerId) {
      return;
    }
    this.store.loadDashboard({centerId, month: this.store.month()});
    this.store.loadRevenueTrend({centerId, endingMonth: this.store.month(), months: 6});
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

  protected revenueChartOptions(): ChartOptions<'line'> {
    return {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: {display: true, position: 'bottom'},
      },
      scales: {
        y: {
          title: {
            display: true,
            text: this.translate.instant('FACTURATION.DASHBOARD.CHART.REVENUE'),
          },
          ticks: {
            callback: (value) => `${value}`,
          },
        },
      },
    };
  }

  private monthLabel(value: string): string {
    if (!/^\d{4}-\d{2}$/.test(value)) {
      return value;
    }
    const [year, month] = value.split('-').map((item) => Number(item));
    const d = new Date(Date.UTC(year, month - 1, 1));
    return d.toLocaleDateString('fr-FR', {month: 'short', year: '2-digit'});
  }

  private themeColor(variable: string, fallback: string): string {
    if (typeof window === 'undefined') {
      return fallback;
    }
    const raw = getComputedStyle(document.documentElement).getPropertyValue(variable).trim();
    return raw || fallback;
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
