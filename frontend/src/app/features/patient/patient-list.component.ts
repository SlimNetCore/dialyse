import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  EventEmitter,
  HostListener,
  inject,
  Output,
  signal,
} from '@angular/core';
import {CommonModule} from '@angular/common';
import {Router} from '@angular/router';
import {MatCardModule} from '@angular/material/card';
import {MatTableModule} from '@angular/material/table';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatChipsModule} from '@angular/material/chips';
import {MatTooltipModule} from '@angular/material/tooltip';
import {MatSnackBar, MatSnackBarModule} from '@angular/material/snack-bar';
import {MatDialog} from '@angular/material/dialog';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {PatientQrCardComponent} from './patient-qr-card.component';
import {PatientSummaryCardsComponent} from './patient-summary-cards.component';
import {PatientSummaryDetailsDialogComponent} from './patient-summary-details-dialog.component';
import {AuthStore} from '../../core/state/auth.store';
import {MatMenuModule} from '@angular/material/menu';
import {MatCheckboxModule} from '@angular/material/checkbox';
import {MatPaginatorModule, PageEvent} from '@angular/material/paginator';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {WebSocketService} from '../../core/ws/websocket.service';
import {ColumnFilterRendererComponent} from '../../shared/column-filter-renderer.component';
import {HemodialysisLoaderComponent} from '../../shared/hemodialysis-loader.component';
import {PatientListStore} from './state/patient-list.store';
import {BackendApiService} from '../../core/api/backend-api.service';
import {AppShellStore} from '../../core/state/app-shell.store';

export interface PatientRow {
  id: string;
  code: string;
  nom: string;
  prenom: string;
  sexe: string;
  dateAdmission: string;
  numeroAssurance: string;
  etatPatient: string;
  dateEvenementEtat?: string;
  nonFacturable?: boolean;
  medecinTraitantId?: string;
  positionId?: string;
  transporteurAllerId?: string;
  transporteurRetourId?: string;
  joursDialyse?: any;
  pecStatus?: string;
  pecForfaitId?: string;
}

type FilterType = 'text' | 'date';

@Component({
  selector: 'app-patient-list',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatChipsModule,
    MatTooltipModule,
    MatSnackBarModule,
    MatMenuModule,
    MatCheckboxModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
    TranslateModule,
    PatientQrCardComponent,
    PatientSummaryCardsComponent,
    ColumnFilterRendererComponent,
    HemodialysisLoaderComponent,
  ],
  templateUrl: './patient-list.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './patient-list.component.css',
})
export class PatientListComponent {
  @Output() newPatient = new EventEmitter<void>();
  @Output() selectPatient = new EventEmitter<PatientRow>();
  @Output() viewStats = new EventEmitter<PatientRow>();

  private readonly auth = inject(AuthStore);
  private readonly patientListStore = inject(PatientListStore);
  private readonly router = inject(Router);
  readonly etatFilterOptions = [
    {value: 'PERMANENT', label: 'PATIENT_FORM.PERMANENT'},
    {value: 'OCCASIONNEL', label: 'PATIENT_FORM.OCCASIONNEL'},
    {value: 'VACANCIER_LOCAL', label: 'PATIENT_FORM.VACANCIER_LOCAL'},
    {value: 'VACANCIER_ETRANGER', label: 'PATIENT_FORM.VACANCIER_ETRANGER'},
    {value: 'TRANSFERE', label: 'PATIENT_FORM.TRANSFERE'},
    {value: 'DECEDE', label: 'PATIENT_FORM.DECEDE'},
    {value: 'GREFFE', label: 'PATIENT_FORM.GREFFE'},
    {value: 'GUERRI', label: 'PATIENT_FORM.GUERRI'},
  ];
  readonly hasActiveFilters = this.patientListStore.hasActiveFilters;
  readonly isMobileView = signal(typeof window !== 'undefined' ? window.innerWidth <= 760 : false);
  readonly selectedRowId = signal<string | null>(null);
  readonly allColumnsConfig = [
    {key: 'numeroAssurance', labelKey: 'PATIENT_LIST.COL_ASSURANCE', type: 'text' as FilterType},
    {key: 'code', labelKey: 'PATIENT_LIST.COL_CODE', type: 'text' as FilterType},
    {key: 'nom', labelKey: 'PATIENT_LIST.COL_NOM', type: 'text' as FilterType},
    {key: 'prenom', labelKey: 'PATIENT_LIST.COL_PRENOM', type: 'text' as FilterType},
    {key: 'sexe', labelKey: 'PATIENT_LIST.COL_SEXE', type: 'text' as FilterType},
    {
      key: 'dateAdmission',
      labelKey: 'PATIENT_LIST.COL_DATE_ADMISSION',
      type: 'date' as FilterType,
    },
    {key: 'etatPatient', labelKey: 'PATIENT_LIST.COL_ETAT', type: 'text' as FilterType},
    {
      key: 'nonFacturable',
      labelKey: 'PATIENT_LIST.NON_FACTURABLE_TOOLTIP',
      type: 'text' as FilterType,
    },
    {key: 'pecStatus', labelKey: 'PATIENT_LIST.COL_PEC', type: 'text' as FilterType},
    {
      key: 'medecinTraitantId',
      labelKey: 'PATIENT_FORM.MEDECIN_TRAITANT',
      type: 'text' as FilterType,
    },
    {key: 'positionId', labelKey: 'PATIENT_FORM.POSITION', type: 'text' as FilterType},
    {
      key: 'transporteurAllerId',
      labelKey: 'PATIENT_FORM.TRANSPORTEUR_ALLER',
      type: 'text' as FilterType,
    },
    {
      key: 'transporteurRetourId',
      labelKey: 'PATIENT_FORM.TRANSPORTEUR_RETOUR',
      type: 'text' as FilterType,
    },
    {key: 'joursDialyse', labelKey: 'PATIENT_FORM.JOURS_DIALYSE', type: 'text' as FilterType},
    {key: 'pecForfaitId', labelKey: 'PATIENT_LIST.COL_FORFAIT', type: 'text' as FilterType},
    {key: 'actions', labelKey: 'PATIENT_LIST.COL_ACTIONS', type: 'text' as FilterType},
  ] as const;
  readonly visibleColumns = signal<Record<string, boolean>>({
    code: false,
    nom: true,
    prenom: true,
    sexe: true,
    dateAdmission: true,
    numeroAssurance: true,
    etatPatient: true,
    nonFacturable: true,
    pecStatus: false,
    medecinTraitantId: false,
    positionId: false,
    transporteurAllerId: false,
    transporteurRetourId: false,
    joursDialyse: true,
    pecForfaitId: false,
    actions: true,
  });
  readonly sexeFilterOptions = [
    {value: 'M', label: 'PATIENT_FORM.MASCULIN'},
    {value: 'F', label: 'PATIENT_FORM.FEMININ'},
  ];
  readonly pecFilterOptions = [
    {value: 'CREE', label: 'STATUS.CREE_TITLE'},
    {value: 'VALIDEE', label: 'STATUS.VALIDEE_TITLE'},
    {value: 'CLOTUREE', label: 'STATUS.CLOTUREE_TITLE'},
  ];
  readonly summaryMonth = this.patientListStore.summaryMonth;
  readonly rows = this.patientListStore.rows;
  readonly loading = this.patientListStore.loading;
  readonly printingList = this.patientListStore.printingList;
  readonly exportingList = this.patientListStore.exportingList;
  readonly printingRowId = this.patientListStore.printingRowId;
  readonly summary = this.patientListStore.summary;
  readonly summaryLoading = this.patientListStore.summaryLoading;
  private readonly translate = inject(TranslateService);
  private readonly api = inject(BackendApiService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly dialog = inject(MatDialog);
  readonly total = this.patientListStore.total;
  readonly pageIndex = this.patientListStore.pageIndex;
  readonly pageSize = this.patientListStore.pageSize;
  readonly columnFilters = this.patientListStore.columnFilters;
  readonly recentPatientId = this.patientListStore.recentPatientId;
  private readonly ws = inject(WebSocketService);
  private readonly appShell = inject(AppShellStore);

  readonly copiedField = signal<string | null>(null);

  private readonly openFilterColumn = signal<string | null>(null);
  private readonly isCompactViewport = signal(
    typeof window !== 'undefined' ? window.innerWidth <= 900 : false,
  );
  private readonly mobilePriorityColumns = new Set<string>([
    'numeroAssurance',
    'nom',
    'prenom',
    'etatPatient',
    'actions',
  ]);

  columnFilterValue(column: string): string {
    return this.columnFilters()[column] ?? '';
  }

  isColumnFiltered(column: string): boolean {
    return !!(this.columnFilters()[column] ?? '').trim();
  }

  readonly displayedColumns = computed(() => {
    const visible = this.allColumnsConfig
      .filter((c) => this.visibleColumns()[c.key])
      .map((c) => c.key);
    if (!this.isCompactViewport()) return visible;

    const prioritized = visible.filter((key) => this.mobilePriorityColumns.has(key));
    if (visible.includes('actions') && !prioritized.includes('actions'))
      prioritized.push('actions');
    return prioritized.length > 0 ? prioritized : visible.slice(0, 4);
  });

  constructor() {
    effect(() => {
      const evt = this.ws.lastEvent();
      if (
        evt?.type === 'PATIENT_CREATED' ||
        evt?.type === 'PATIENT_UPDATED' ||
        evt?.type === 'PEC_VALIDATED' ||
        evt?.type === 'PEC_CLOSED' ||
        evt?.type === 'PEC_DELETED' ||
        evt?.type === 'ATTESTATION_CREATED' ||
        evt?.type === 'ATTESTATION_DELETED'
      ) {
        this.patientListStore.refreshCurrentPage();
        this.patientListStore.refreshSummary();
      }
    });

    effect(() => {
      const recentId = this.recentPatientId();
      if (!recentId) return;
      setTimeout(() => this.patientListStore.clearRecentPatient(), 6000);
    });
  }

  isRecentRow(rowId: string): boolean {
    return !!rowId && rowId === this.recentPatientId();
  }

  clearColumnFilter(column: string): void {
    this.patientListStore.clearFilter(column);
  }

  isFilterOpen(column: string): boolean {
    return this.openFilterColumn() === column;
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    const target = event.target as HTMLElement | null;
    if (!target) {
      this.openFilterColumn.set(null);
      return;
    }
    if (target.closest('.th-wrap')) {
      return;
    }
    this.openFilterColumn.set(null);
  }

  @HostListener('window:resize')
  onWindowResize(): void {
    this.isCompactViewport.set(window.innerWidth <= 900);
    this.isMobileView.set(window.innerWidth <= 760);
  }

  onRowClick(row: PatientRow): void {
    // Le clic sur la ligne ne doit plus ouvrir la fiche patient.
    this.selectedRowId.set(this.selectedRowId() === row.id ? null : row.id);
  }

  clearAllColumnFilters(): void {
    this.openFilterColumn.set(null);
    this.patientListStore.clearAllFilters();
  }

  toggleFilterPanel(column: string, event: MouseEvent): void {
    event.stopPropagation();
    if (typeof window !== 'undefined' && window.innerWidth <= 760) {
      const wrap = (event.target as HTMLElement).closest('.th-wrap');
      if (wrap) {
        const rect = wrap.getBoundingClientRect();
        document.documentElement.style.setProperty(
          '--filter-row-bottom',
          `${Math.round(rect.bottom + 6)}px`,
        );
      }
    }
    this.openFilterColumn.update((current) => (current === column ? null : column));
  }

  isColumnVisible(column: string): boolean {
    return this.visibleColumns()[column] ?? false;
  }

  onPageChange(event: PageEvent): void {
    this.patientListStore.setPagination(event.pageIndex, event.pageSize);
  }

  toggleColumn(column: string, checked: boolean): void {
    this.visibleColumns.update((prev) => ({...prev, [column]: checked}));
  }

  printFiche(patient: PatientRow): void {
    const centerId = this.auth.centerId();
    if (!centerId) return;
    this.patientListStore.setPrintingRowId(patient.id);
    this.patientListStore.printFiche({centerId, patientId: patient.id});
    // Auto-clear after 10s
    setTimeout(() => this.patientListStore.setPrintingRowId(null), 10000);
  }

  copyToClipboard(value: string, fieldKey: string, event: MouseEvent): void {
    event.stopPropagation();
    if (!value) return;
    const centerId = this.appShell.currentCenterId();
    if (centerId) {
      this.appShell.setSeanceScanClipboard(centerId, value);
    }
    navigator.clipboard.writeText(value).then(() => {
      this.copiedField.set(fieldKey);
      this.snackBar.open(this.translate.instant('PATIENT_LIST.COPY_SUCCESS'), '', {duration: 1800});
      setTimeout(() => this.copiedField.set(null), 2000);
    }).catch(() => {
      // Even when browser clipboard is blocked, we keep app-level clipboard for scanner autofill.
      this.copiedField.set(fieldKey);
      this.snackBar.open(this.translate.instant('PATIENT_LIST.COPY_SUCCESS'), '', {duration: 1800});
      setTimeout(() => this.copiedField.set(null), 2000);
    });
  }

  openCahier(patient: PatientRow): void {
    this.router.navigate(['/patients', patient.id, 'cahier'], {queryParams: {mode: 'recap'}});
  }

  printList(): void {
    const centerId = this.auth.centerId();
    if (!centerId) return;
    this.patientListStore.printList({centerId});
  }

  onSummaryMonthChange(month: string): void {
    this.patientListStore.setSummaryMonth(month);
  }

  onSummaryViewDetails(month: string): void {
    const centerId = this.auth.centerId();
    if (!centerId) return;

    this.api.getPatientSummaryDetails(centerId, month).subscribe({
      next: (details) => {
        this.dialog.open(PatientSummaryDetailsDialogComponent, {
          width: 'min(96vw, 1180px)',
          maxWidth: '96vw',
          data: details,
        });
      },
      error: () => {
        this.snackBar.open(this.translate.instant('PATIENT_LIST.SUMMARY_DETAILS_LOAD_ERROR'), 'OK', {
          duration: 3500,
        });
      },
    });
  }

  exportListExcel(): void {
    const centerId = this.auth.centerId();
    if (!centerId) return;
    this.patientListStore.exportListExcel({centerId});
  }

  eventDateTooltip(row: PatientRow): string {
    if (!this.hasEventTooltip(row)) return '';
    const formattedDate = this.formatEventDate(row.dateEvenementEtat ?? '');
    const label = this.translate.instant(this.eventDateLabelKey(row));
    return formattedDate
      ? `${label}: ${formattedDate}`
      : this.translate.instant('PATIENT_LIST.EVENT_DATE_NOT_PROVIDED', {label});
  }

  hasEventTooltip(row: PatientRow): boolean {
    // Some backends can return labels with accents/case/spacing; normalize before checking.
    const normalizedStatus = this.normalizeStatusCode(row.etatPatient);
    const hasEventDate = !!(row.dateEvenementEtat ?? '').trim();
    return hasEventDate
      || normalizedStatus === 'TRANSFERE'
      || normalizedStatus === 'GREFFE'
      || normalizedStatus === 'DECEDE'
      || normalizedStatus === 'GUERRI'
      || normalizedStatus === 'OCCASIONNEL'
      || normalizedStatus === 'VACANCIER_LOCAL'
      || normalizedStatus === 'VACANCIER_ETRANGER';
  }

  visibleEventDate(row: PatientRow): string {
    const formattedDate = this.formatEventDate(row.dateEvenementEtat ?? '');
    const label = this.translate.instant(this.eventDateLabelKey(row));
    return formattedDate
      ? `${label}: ${formattedDate}`
      : this.translate.instant('PATIENT_LIST.EVENT_DATE_NOT_PROVIDED', {label});
  }

  onColumnFilterValue(column: string, value: string): void {
    this.patientListStore.setFilter(column, value);
  }

  private formatEventDate(rawDate: string): string {
    const raw = (rawDate ?? '').trim();
    if (!raw) return '';
    const match = raw.match(/^(\d{4})-(\d{2})-(\d{2})/);
    if (!match) return raw;
    return `${match[3]}/${match[2]}/${match[1]}`;
  }

  private eventDateLabelKey(row: PatientRow): string {
    return this.eventDateLabelKeyFromStatus(row.etatPatient);
  }

  private eventDateLabelKeyFromStatus(etatPatient: string | undefined): string {
    switch (this.normalizeStatusCode(etatPatient)) {
      case 'OCCASIONNEL':
      case 'VACANCIER_LOCAL':
      case 'VACANCIER_ETRANGER':
        return 'PATIENT_LIST.EVENT_DATE_SORTIE';
      case 'GREFFE':
        return 'PATIENT_LIST.EVENT_DATE_GREFFE';
      case 'GUERRI':
        return 'PATIENT_LIST.EVENT_DATE_GUERISON';
      case 'DECEDE':
        return 'PATIENT_LIST.EVENT_DATE_DECES';
      case 'TRANSFERE':
        return 'PATIENT_LIST.EVENT_DATE_TRANSFERT';
      default:
        return 'PATIENT_LIST.EVENT_DATE_GENERIC';
    }
  }

  private normalizeStatusCode(value: string | undefined): string {
    return (value ?? '')
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .replace(/\s+/g, '_')
      .toUpperCase()
      .trim();
  }
}
