import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  output,
  signal,
  TemplateRef,
  viewChild,
} from '@angular/core';
import {CommonModule} from '@angular/common';
import {Router} from '@angular/router';
import {MatCardModule} from '@angular/material/card';
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
import {HemodialysisLoaderComponent} from '../../shared/hemodialysis-loader.component';
import {PatientListStore} from './state/patient-list.store';
import {BackendApiService} from '../../core/api/backend-api.service';
import {AppShellStore} from '../../core/state/app-shell.store';
import {
  ConfigurableListComponent,
  SharedListColumn,
  SharedListCopyEvent,
} from '../../shared/configurable-list.component';

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

@Component({
  selector: 'app-patient-list',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
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
    HemodialysisLoaderComponent,
    ConfigurableListComponent,
  ],
  templateUrl: './patient-list.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './patient-list.component.css',
})
export class PatientListComponent {
  readonly newPatient = output<void>();
  readonly selectPatient = output<PatientRow>();
  readonly viewStats = output<PatientRow>();

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
  readonly selectedRowId = signal<string | null>(null);
  readonly columnsMenuItems = computed(() =>
    this.allColumnDefs().filter((column) => column.id !== 'actions'),
  );
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

  protected readonly codeCellTemplate = viewChild<TemplateRef<any>>('codeCell');
  protected readonly nomCellTemplate = viewChild<TemplateRef<any>>('nomCell');
  protected readonly sexeCellTemplate = viewChild<TemplateRef<any>>('sexeCell');
  protected readonly assuranceCellTemplate = viewChild<TemplateRef<any>>('assuranceCell');
  protected readonly etatCellTemplate = viewChild<TemplateRef<any>>('etatCell');
  protected readonly facturationCellTemplate = viewChild<TemplateRef<any>>('facturationCell');
  protected readonly pecStatusCellTemplate = viewChild<TemplateRef<any>>('pecStatusCell');
  protected readonly joursDialyseCellTemplate = viewChild<TemplateRef<any>>('joursDialyseCell');
  protected readonly nullableTextCellTemplate = viewChild<TemplateRef<any>>('nullableTextCell');
  protected readonly actionsCellTemplate = viewChild<TemplateRef<any>>('actionsCell');

  private readonly allColumnDefsById = computed<Record<string, SharedListColumn<PatientRow>>>(() => ({
    numeroAssurance: {
      id: 'numeroAssurance',
      headerKey: 'PATIENT_LIST.COL_ASSURANCE',
      valueAccessor: (row) => row.numeroAssurance ?? '',
      sortable: true,
      resizable: true,
      minWidthPx: 170,
      filter: {type: 'text', labelKey: 'PATIENT_LIST.COL_ASSURANCE'},
      copy: {valueAccessor: (row) => row.numeroAssurance ?? '', tooltipKey: 'PATIENT_LIST.COPY_TOOLTIP'},
      cellTemplate: this.assuranceCellTemplate() ?? undefined,
    },
    code: {
      id: 'code',
      headerKey: 'PATIENT_LIST.COL_CODE',
      valueAccessor: (row) => row.code ?? '',
      sortable: true,
      resizable: true,
      minWidthPx: 140,
      filter: {type: 'text', labelKey: 'PATIENT_LIST.COL_CODE'},
      copy: {valueAccessor: (row) => row.code ?? '', tooltipKey: 'PATIENT_LIST.COPY_TOOLTIP'},
      cellTemplate: this.codeCellTemplate() ?? undefined,
    },
    nom: {
      id: 'nom',
      headerKey: 'PATIENT_LIST.COL_NOM',
      valueAccessor: (row) => row.nom ?? '',
      sortable: true,
      resizable: true,
      minWidthPx: 180,
      filter: {type: 'text', labelKey: 'PATIENT_LIST.COL_NOM'},
      cellTemplate: this.nomCellTemplate() ?? undefined,
    },
    prenom: {
      id: 'prenom',
      headerKey: 'PATIENT_LIST.COL_PRENOM',
      valueAccessor: (row) => row.prenom ?? '',
      sortable: true,
      resizable: true,
      minWidthPx: 170,
      filter: {type: 'text', labelKey: 'PATIENT_LIST.COL_PRENOM'},
    },
    sexe: {
      id: 'sexe',
      headerKey: 'PATIENT_LIST.COL_SEXE',
      valueAccessor: (row) => row.sexe ?? '',
      sortable: true,
      resizable: true,
      minWidthPx: 120,
      maxWidthPx: 140,
      filter: {type: 'enum', options: this.sexeFilterOptions, labelKey: 'PATIENT_LIST.COL_SEXE'},
      cellTemplate: this.sexeCellTemplate() ?? undefined,
    },
    dateAdmission: {
      id: 'dateAdmission',
      headerKey: 'PATIENT_LIST.COL_DATE_ADMISSION',
      valueAccessor: (row) => row.dateAdmission ?? '',
      sortable: true,
      resizable: true,
      minWidthPx: 170,
      filter: {type: 'date', labelKey: 'PATIENT_LIST.COL_DATE_ADMISSION'},
    },
    etatPatient: {
      id: 'etatPatient',
      headerKey: 'PATIENT_LIST.COL_ETAT',
      valueAccessor: (row) => row.etatPatient ?? '',
      sortable: true,
      resizable: true,
      minWidthPx: 170,
      filter: {type: 'enum', options: this.etatFilterOptions, labelKey: 'PATIENT_LIST.COL_ETAT'},
      cellTemplate: this.etatCellTemplate() ?? undefined,
    },
    nonFacturable: {
      id: 'nonFacturable',
      headerKey: 'PATIENT_LIST.BILLING_LABEL',
      valueAccessor: (row) => !!row.nonFacturable,
      sortable: true,
      resizable: true,
      minWidthPx: 170,
      filter: {type: 'boolean', labelKey: 'PATIENT_LIST.BILLING_LABEL'},
      cellTemplate: this.facturationCellTemplate() ?? undefined,
    },
    pecStatus: {
      id: 'pecStatus',
      headerKey: 'PATIENT_LIST.COL_PEC',
      valueAccessor: (row) => row.pecStatus ?? '',
      sortable: true,
      resizable: true,
      minWidthPx: 145,
      filter: {type: 'enum', options: this.pecFilterOptions, labelKey: 'PATIENT_LIST.COL_PEC'},
      cellTemplate: this.pecStatusCellTemplate() ?? undefined,
    },
    medecinTraitantId: {
      id: 'medecinTraitantId',
      headerKey: 'PATIENT_FORM.MEDECIN_TRAITANT',
      valueAccessor: (row) => row.medecinTraitantId ?? '',
      sortable: true,
      resizable: true,
      minWidthPx: 175,
      filter: {type: 'text', labelKey: 'PATIENT_FORM.MEDECIN_TRAITANT'},
      cellTemplate: this.nullableTextCellTemplate() ?? undefined,
    },
    positionId: {
      id: 'positionId',
      headerKey: 'PATIENT_FORM.POSITION',
      valueAccessor: (row) => row.positionId ?? '',
      sortable: true,
      resizable: true,
      minWidthPx: 150,
      filter: {type: 'text', labelKey: 'PATIENT_FORM.POSITION'},
      cellTemplate: this.nullableTextCellTemplate() ?? undefined,
    },
    transporteurAllerId: {
      id: 'transporteurAllerId',
      headerKey: 'PATIENT_FORM.TRANSPORTEUR_ALLER',
      valueAccessor: (row) => row.transporteurAllerId ?? '',
      sortable: true,
      resizable: true,
      minWidthPx: 185,
      filter: {type: 'text', labelKey: 'PATIENT_FORM.TRANSPORTEUR_ALLER'},
      cellTemplate: this.nullableTextCellTemplate() ?? undefined,
    },
    transporteurRetourId: {
      id: 'transporteurRetourId',
      headerKey: 'PATIENT_FORM.TRANSPORTEUR_RETOUR',
      valueAccessor: (row) => row.transporteurRetourId ?? '',
      sortable: true,
      resizable: true,
      minWidthPx: 190,
      filter: {type: 'text', labelKey: 'PATIENT_FORM.TRANSPORTEUR_RETOUR'},
      cellTemplate: this.nullableTextCellTemplate() ?? undefined,
    },
    joursDialyse: {
      id: 'joursDialyse',
      headerKey: 'PATIENT_FORM.JOURS_DIALYSE',
      valueAccessor: (row) => this.dialyseDaysAsFilterText(row),
      sortable: false,
      resizable: true,
      minWidthPx: 190,
      filter: {type: 'text', labelKey: 'PATIENT_FORM.JOURS_DIALYSE'},
      cellTemplate: this.joursDialyseCellTemplate() ?? undefined,
    },
    pecForfaitId: {
      id: 'pecForfaitId',
      headerKey: 'PATIENT_LIST.COL_FORFAIT',
      valueAccessor: (row) => row.pecForfaitId ?? '',
      sortable: true,
      resizable: true,
      minWidthPx: 160,
      filter: {type: 'text', labelKey: 'PATIENT_LIST.COL_FORFAIT'},
      cellTemplate: this.nullableTextCellTemplate() ?? undefined,
    },
    actions: {
      id: 'actions',
      headerKey: 'PATIENT_LIST.COL_ACTIONS',
      valueAccessor: () => '',
      sortable: false,
      resizable: false,
      mobileRowActions: true,
      widthPx: 240,
      minWidthPx: 210,
      maxWidthPx: 300,
      cellTemplate: this.actionsCellTemplate() ?? undefined,
    },
  }));
  private readonly orderedColumnKeys = [
    'numeroAssurance',
    'code',
    'nom',
    'prenom',
    'sexe',
    'dateAdmission',
    'etatPatient',
    'nonFacturable',
    'pecStatus',
    'medecinTraitantId',
    'positionId',
    'transporteurAllerId',
    'transporteurRetourId',
    'joursDialyse',
    'pecForfaitId',
    'actions',
  ] as const;
  readonly allColumnDefs = computed<SharedListColumn<PatientRow>[]>(() => {
    const defsById = this.allColumnDefsById();
    return this.orderedColumnKeys.flatMap((key) => {
      const column = defsById[key];
      return column ? [column] : [];
    });
  });

  readonly rowClassResolver = (row: PatientRow) => ({
    'patient-row': true,
    'patient-row-recent': this.isRecentRow(row.id),
    'patient-row-selected': this.selectedRowId() === row.id,
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


  onRowClick(row: PatientRow): void {
    // Le clic sur la ligne ne doit plus ouvrir la fiche patient.
    this.selectedRowId.set(this.selectedRowId() === row.id ? null : row.id);
  }

  clearAllColumnFilters(): void {
    this.patientListStore.clearAllFilters();
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

  onListFiltersChange(filters: Record<string, string>): void {
    for (const column of this.allColumnDefs()) {
      if (column.id === 'actions') continue;
      this.patientListStore.setFilter(column.id, filters[column.id] ?? '');
    }
  }

  printFiche(patient: PatientRow): void {
    const centerId = this.auth.centerId();
    if (!centerId) return;
    this.patientListStore.setPrintingRowId(patient.id);
    this.patientListStore.printFiche({centerId, patientId: patient.id});
    // Auto-clear after 10s
    setTimeout(() => this.patientListStore.setPrintingRowId(null), 10000);
  }

  onListCellCopied(event: SharedListCopyEvent<PatientRow>): void {
    const value = event?.value ?? '';
    const fieldKey = `${event.columnId}_${event.row?.id ?? ''}`;
    if (!value) return;
    const centerId = this.appShell.currentCenterId();
    if (centerId) {
      this.appShell.setSeanceScanClipboard(centerId, value);
    }
    this.copiedField.set(fieldKey);
    this.snackBar.open(this.translate.instant('PATIENT_LIST.COPY_SUCCESS'), '', {duration: 1800});
    setTimeout(() => this.copiedField.set(null), 2000);
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

  onListRowClick(row: PatientRow): void {
    this.onRowClick(row);
  }

  hasDialyseDay(row: PatientRow): boolean {
    const days = row.joursDialyse;
    return !!(
      days &&
      (days.dimanche ||
        days.lundi ||
        days.mardi ||
        days.mercredi ||
        days.jeudi ||
        days.vendredi ||
        days.samedi)
    );
  }

  textOrDash(value: string | undefined | null): string {
    return (value ?? '').trim() || '-';
  }

  private dialyseDaysAsFilterText(row: PatientRow): string {
    const days = row.joursDialyse;
    if (!days) return '';
    const labels: string[] = [];
    if (days.dimanche) labels.push('dimanche');
    if (days.lundi) labels.push('lundi');
    if (days.mardi) labels.push('mardi');
    if (days.mercredi) labels.push('mercredi');
    if (days.jeudi) labels.push('jeudi');
    if (days.vendredi) labels.push('vendredi');
    if (days.samedi) labels.push('samedi');
    return labels.join(' ');
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
