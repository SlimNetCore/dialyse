import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  ElementRef,
  inject,
  OnDestroy,
  OnInit,
  signal,
  ViewChild,
} from '@angular/core';
import {RouterLink} from '@angular/router';
import {MatButtonModule} from '@angular/material/button';
import {MatCardModule} from '@angular/material/card';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatIconModule} from '@angular/material/icon';
import {MatInputModule} from '@angular/material/input';
import {MatSelectModule} from '@angular/material/select';
import {MatSnackBar} from '@angular/material/snack-bar';
import {MatTableModule} from '@angular/material/table';
import {MatTabsModule} from '@angular/material/tabs';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {BaseChartDirective} from 'ng2-charts';
import {Chart, ChartData, ChartOptions, registerables} from 'chart.js';
import {AuthStore} from '../../core/state/auth.store';
import {AppShellStore} from '../../core/state/app-shell.store';
import {BackendApiService, SeanceDashboardDetailItem, SeanceListItem} from '../../core/api/backend-api.service';
import {WebSocketService} from '../../core/ws/websocket.service';
import {SeanceStore} from './state/seance.store';

type BarcodeDetectorInstance = {
  detect: (source: ImageBitmapSource) => Promise<Array<{ rawValue?: string }>>;
};
type BarcodeDetectorConstructor = new (options?: { formats?: string[] }) => BarcodeDetectorInstance;
Chart.register(...registerables);

@Component({
  standalone: true,
  imports: [MatCardModule, MatIconModule, MatFormFieldModule, MatInputModule,
    MatButtonModule, MatTableModule, MatSelectModule, MatTabsModule, TranslateModule, BaseChartDirective, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './seances-page.component.html',
  styleUrl: './seances-page.component.css',
})
export class SeancesPageComponent implements OnInit, OnDestroy {
  protected readonly store = inject(SeanceStore);
  protected readonly cameraActive = signal(false);
  protected readonly cameraStarting = signal(false);
  protected readonly activeTabIndex = signal(0);
  // Alias réactifs attendus par le template
  protected readonly qrCode = computed(() => this.store.qrCode());
  protected readonly scanState = computed(() => this.store.scanState());
  protected readonly scanMessage = computed(() => this.store.scanMessage());
  protected readonly scanning = computed(() => this.store.scanning());
  protected readonly seances = computed(() => this.store.seances());
  protected readonly dashboardMonth = computed(() => this.store.dashboardMonth());
  protected readonly dashboardLoading = computed(() => this.store.dashboardLoading());
  protected readonly seanceDashboard = computed(() => this.store.seanceDashboard());
  protected readonly dashboardDetailsOpen = computed(() => this.store.dashboardDetailsOpen());
  protected readonly dashboardDetailsKind = computed(() => this.store.dashboardDetailsKind());
  protected readonly dashboardDetailsLoading = computed(() => this.store.dashboardDetailsLoading());
  protected readonly dashboardDetailItems = computed(() => this.store.dashboardDetailItems());
  protected readonly dashboardDetailPageIndex = computed(() => this.store.dashboardDetailPageIndex());
  protected readonly dashboardDetailPageSize = 10;
  protected readonly dashboardDetailPageItems = computed(() => this.store.dashboardDetailPageItems());
  protected readonly dashboardDetailTotalPages = computed(() => this.store.dashboardDetailTotalPages());
  protected readonly journalDate = computed(() => this.store.journalDate());
  protected readonly journalLoading = computed(() => this.store.journalLoading());
  protected readonly journalPatients = computed(() => this.store.journalPatients());
  protected readonly journalArticles = computed(() => this.store.journalArticles());
  protected readonly selectedSeanceId = computed(() => this.store.selectedSeanceId());
  protected readonly selectedPreview = computed(() => this.store.selectedPreview());
  protected readonly editDateSeance = computed(() => this.store.editDateSeance());
  protected readonly savingDate = computed(() => this.store.savingDate());
  protected readonly savingParamedical = computed(() => this.store.savingParamedical());
  protected readonly savingMedical = computed(() => this.store.savingMedical());
  protected readonly validatingSeance = computed(() => this.store.validatingSeance());
  protected readonly summary = computed(() => this.store.summary());
  protected readonly taAvant = computed(() => this.store.taAvant());
  protected readonly taApres = computed(() => this.store.taApres());
  protected readonly poidsAvantKg = computed(() => this.store.poidsAvantKg());
  protected readonly poidsApresKg = computed(() => this.store.poidsApresKg());
  protected readonly dureeMinutes = computed(() => this.store.dureeMinutes());
  protected readonly debitSangMlMin = computed(() => this.store.debitSangMlMin());
  protected readonly ultrafiltrationMl = computed(() => this.store.ultrafiltrationMl());
  protected readonly anticoagulant = computed(() => this.store.anticoagulant());
  protected readonly typeDialysat = computed(() => this.store.typeDialysat());
  protected readonly incidents = computed(() => this.store.incidents());
  protected readonly prescription = computed(() => this.store.prescription());
  protected readonly toleranceSeance = computed(() => this.store.toleranceSeance());
  protected readonly examenClinique = computed(() => this.store.examenClinique());
  protected readonly resultatsBiologiques = computed(() => this.store.resultatsBiologiques());
  protected readonly ajustementsTherapeutiques = computed(() => this.store.ajustementsTherapeutiques());
  protected readonly conclusionMedicale = computed(() => this.store.conclusionMedicale());
  protected readonly selectionLoading = computed(() => this.store.summaryLoading());
  protected readonly seanceCols = ['dateSeance', 'patient', 'status', 'forfait', 'actions'];
  protected readonly journalPatientCols = ['patient', 'code', 'status'];
  protected readonly dashboardDetailCols = ['date', 'patient', 'weekday', 'status'];
  protected readonly consommableCols = ['article', 'quantite', 'actions'];
  // Consommables
  protected readonly consommables = computed(() => this.store.consommables());
  protected readonly availableArticles = computed(() => this.store.availableArticles());
  protected readonly newConsommableArticleId = computed(() => this.store.newConsommableArticleId());
  protected readonly newConsommableQuantite = computed(() => this.store.newConsommableQuantite());
  protected readonly canScanSeances = computed(() => this.hasAnyRole('ADMIN', 'INFIRMIER', 'SECRETAIRE'));
  protected readonly canOpenSeanceDetails = computed(() => this.hasAnyRole('ADMIN', 'INFIRMIER', 'MEDECIN'));
  protected readonly canEditDate = computed(() => this.hasAnyRole('ADMIN'));
  protected readonly canEditParamedical = computed(() => this.hasAnyRole('ADMIN', 'INFIRMIER', 'SECRETAIRE'));
  protected readonly canEditMedical = computed(() => this.hasAnyRole('ADMIN', 'MEDECIN'));
  protected readonly chartOptions: ChartOptions<'bar' | 'doughnut'> = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {legend: {labels: {color: '#64748b'}}},
    scales: {x: {ticks: {color: '#64748b'}}, y: {ticks: {color: '#64748b'}, beginAtZero: true}},
  };
  protected readonly presenceAbsenceChart = computed<ChartData<'bar'>>(() => {
    const d = this.store.seanceDashboard();
    return {
      labels: ['Presences', 'Absences'],
      datasets: [{data: [d?.presenceCount ?? 0, d?.absenceCount ?? 0], backgroundColor: ['#16a34a', '#dc2626']}]
    };
  });
  protected readonly sexeChart = computed<ChartData<'doughnut'>>(() => {
    const dist = this.store.seanceDashboard()?.sexeDistribution ?? {};
    return {
      labels: ['M', 'F', 'Autre'],
      datasets: [{
        data: [dist['M'] ?? 0, dist['F'] ?? 0, dist['AUTRE'] ?? 0],
        backgroundColor: ['#3b82f6', '#ec4899', '#f59e0b']
      }]
    };
  });
  protected readonly ageChart = computed<ChartData<'bar'>>(() => {
    const dist = this.store.seanceDashboard()?.ageDistribution ?? {};
    return {
      labels: ['0-17', '18-39', '40-59', '60+', 'Inconnu'],
      datasets: [{
        data: [dist['0-17'] ?? 0, dist['18-39'] ?? 0, dist['40-59'] ?? 0, dist['60+'] ?? 0, dist['INCONNU'] ?? 0],
        backgroundColor: '#2563eb'
      }]
    };
  });
  @ViewChild('qrImageInput') private qrImageInputRef?: ElementRef<HTMLInputElement>;
  @ViewChild('cameraVideo') private cameraVideoRef?: ElementRef<HTMLVideoElement>;
  private readonly api = inject(BackendApiService);
  private readonly appShell = inject(AppShellStore);
  private readonly auth = inject(AuthStore);
  private readonly ws = inject(WebSocketService);
  private readonly translate = inject(TranslateService);
  private readonly snackBar = inject(MatSnackBar);
  private cameraStream: MediaStream | null = null;
  private cameraFrameId: number | null = null;
  private cameraDetector: BarcodeDetectorInstance | null = null;
  private cameraDetectionInFlight = false;

  constructor() {
    effect(() => {
      const event = this.ws.lastEvent();
      const centerId = this.appShell.currentCenterId();
      if (!event || !centerId || event.centerId !== centerId) return;
      if (!this.mustRefreshFromEvent(event.type)) return;
      this.refreshRealtime(centerId);
    });
  }

  ngOnInit(): void {
    const centerId = this.appShell.currentCenterId();
    if (centerId) {
      this.store.loadSeances({centerId});
      this.store.loadArticlesStock({centerId});
      this.loadSeanceDashboard();
      this.loadJournalByDate();
    }
  }

  ngOnDestroy(): void {
    this.stopCamera();
  }

  protected scrollToScanner(): void {
    const el = document.getElementById('scanner-card');
    if (el) {
      el.scrollIntoView({behavior: 'smooth', block: 'center'});
    }
  }

  protected onQrInput(e: Event): void {
    this.store.setQrCode((e.target as HTMLInputElement)?.value ?? '');
  }


  protected onEditDateInput(e: Event): void {
    this.store.setEditDateSeance((e.target as HTMLInputElement)?.value ?? todayIso());
  }

  protected onJournalDateInput(e: Event): void {
    this.store.setJournalDate((e.target as HTMLInputElement)?.value ?? todayIso());
  }

  protected onDashboardMonthInput(e: Event): void {
    this.store.setDashboardMonth((e.target as HTMLInputElement)?.value ?? currentMonthIso());
  }

  protected onTaAvantInput(e: Event): void {
    this.store.patchParamedical({taAvant: (e.target as HTMLInputElement)?.value ?? ''});
  }

  protected onTaApresInput(e: Event): void {
    this.store.patchParamedical({taApres: (e.target as HTMLInputElement)?.value ?? ''});
  }

  protected onPoidsAvantInput(e: Event): void {
    this.store.patchParamedical({poidsAvantKg: parseNum((e.target as HTMLInputElement)?.value)});
  }

  protected onPoidsApresInput(e: Event): void {
    this.store.patchParamedical({poidsApresKg: parseNum((e.target as HTMLInputElement)?.value)});
  }

  protected onDureeInput(e: Event): void {
    this.store.patchParamedical({dureeMinutes: parseNum((e.target as HTMLInputElement)?.value)});
  }

  protected onDebitSangInput(e: Event): void {
    this.store.patchParamedical({debitSangMlMin: parseNum((e.target as HTMLInputElement)?.value)});
  }

  protected onUltrafiltrationInput(e: Event): void {
    this.store.patchParamedical({ultrafiltrationMl: parseNum((e.target as HTMLInputElement)?.value)});
  }

  protected onAnticoagulantInput(e: Event): void {
    this.store.patchParamedical({anticoagulant: (e.target as HTMLInputElement)?.value ?? ''});
  }

  protected onTypeDialysatInput(e: Event): void {
    this.store.patchParamedical({typeDialysat: (e.target as HTMLInputElement)?.value ?? ''});
  }

  protected onIncidentsInput(e: Event): void {
    this.store.patchParamedical({incidents: (e.target as HTMLTextAreaElement)?.value ?? ''});
  }

  protected onPrescriptionInput(e: Event): void {
    this.store.patchMedical({prescription: (e.target as HTMLTextAreaElement)?.value ?? ''});
  }

  protected onToleranceSeanceInput(e: Event): void {
    this.store.patchMedical({toleranceSeance: (e.target as HTMLTextAreaElement)?.value ?? ''});
  }

  protected onExamenCliniqueInput(e: Event): void {
    this.store.patchMedical({examenClinique: (e.target as HTMLTextAreaElement)?.value ?? ''});
  }

  protected onResultatsBiologiquesInput(e: Event): void {
    this.store.patchMedical({resultatsBiologiques: (e.target as HTMLTextAreaElement)?.value ?? ''});
  }

  protected onAjustementsTherapeutiquesInput(e: Event): void {
    this.store.patchMedical({ajustementsTherapeutiques: (e.target as HTMLTextAreaElement)?.value ?? ''});
  }

  protected onConclusionMedicaleInput(e: Event): void {
    this.store.patchMedical({conclusionMedicale: (e.target as HTMLTextAreaElement)?.value ?? ''});
  }

  protected triggerImagePicker(): void {
    if (!this.canScanSeances()) {
      this.snackBar.open('Votre profil ne peut pas scanner', 'OK', {duration: 3000});
      return;
    }
    this.qrImageInputRef?.nativeElement.click();
  }

  protected toggleCamera(): void {
    if (this.cameraActive()) {
      this.stopCamera();
      return;
    }
    void this.startCamera();
  }

  protected onQrImageSelected(event: Event): void {
    const input = event.target as HTMLInputElement | null;
    const file = input?.files?.[0];
    if (!file) return;
    void this.scanQrFromImage(file);
    if (input) input.value = '';
  }

  protected scanQr(): void {
    const centerId = this.appShell.currentCenterId();
    const qr = this.store.qrCode().trim();
    if (!this.canScanSeances() || !centerId || !qr) {
      this.snackBar.open('Centre ou QR manquant', 'OK', {duration: 3000});
      return;
    }
    this.stopCamera();
    this.store.scanQr({centerId, qrCode: qr, dateSeance: this.store.dateSeance()});
  }

  protected selectSeance(seance: SeanceListItem): void {
    if (!this.canOpenSeanceDetails()) return;
    const centerId = this.appShell.currentCenterId();
    if (!centerId) return;
    this.activeTabIndex.set(0);
    this.store.selectSeance(seance.id);
    this.store.loadSeanceSummary({seanceId: seance.id, centerId});
  }

  protected saveSelectedDate(): void {
    const centerId = this.appShell.currentCenterId();
    const seanceId = this.store.summary()?.seance.id;
    if (!centerId || !seanceId || !this.canEditDate()) return;
    this.store.saveDate({seanceId, centerId, dateSeance: this.store.editDateSeance()});
    this.store.loadSeances({centerId});
  }

  protected saveParamedical(): void {
    const centerId = this.appShell.currentCenterId();
    const seanceId = this.store.summary()?.seance.id;
    if (!centerId || !seanceId || !this.canEditParamedical()) return;
    this.store.saveParamedical({
      seanceId,
      payload: {
        centerId,
        taAvant: nullableText(this.store.taAvant()),
        taApres: nullableText(this.store.taApres()),
        poidsAvantKg: this.store.poidsAvantKg(),
        poidsApresKg: this.store.poidsApresKg(),
        dureeMinutes: this.store.dureeMinutes(),
        debitSangMlMin: this.store.debitSangMlMin(),
        ultrafiltrationMl: this.store.ultrafiltrationMl(),
        anticoagulant: nullableText(this.store.anticoagulant()),
        typeDialysat: nullableText(this.store.typeDialysat()),
        incidents: nullableText(this.store.incidents())
      }
    });
  }

  protected saveMedical(): void {
    const centerId = this.appShell.currentCenterId();
    const seanceId = this.store.summary()?.seance.id;
    if (!centerId || !seanceId || !this.canEditMedical()) return;
    this.store.saveMedical({
      seanceId,
      payload: {
        centerId,
        prescription: nullableText(this.store.prescription()),
        toleranceSeance: nullableText(this.store.toleranceSeance()),
        examenClinique: nullableText(this.store.examenClinique()),
        resultatsBiologiques: nullableText(this.store.resultatsBiologiques()),
        ajustementsTherapeutiques: nullableText(this.store.ajustementsTherapeutiques()),
        conclusionMedicale: nullableText(this.store.conclusionMedicale())
      }
    });
  }

  protected validateSeanceParamedical(): void {
    const centerId = this.appShell.currentCenterId();
    const seanceId = this.store.summary()?.seance.id;
    const userId = this.auth.username();
    if (!centerId || !seanceId || !userId || !this.canValidateSeance() || this.store.isSeanceAlreadyValidated()) return;
    const consommations = this.store.consommables().map((c) => ({
      articleId: c.articleId,
      quantite: c.quantite,
    }));
    this.store.validateSeance({seanceId, payload: {centerId, userId, consommations}});
    this.store.loadSeanceSummary({seanceId, centerId});
  }

  protected canValidateSeance(): boolean {
    return this.hasAnyRole('ADMIN', 'INFIRMIER');
  }

  protected isSeanceAlreadyValidated(): boolean {
    return this.store.isSeanceAlreadyValidated();
  }

  protected onConsommableArticleChange(articleId: string): void {
    this.store.setNewConsommableArticleId(articleId);
  }

  protected onConsommableQuantiteInput(e: Event): void {
    const val = (e.target as HTMLInputElement)?.value;
    const n = parseNum(val);
    this.store.setNewConsommableQuantite(n);
  }

  protected addConsommable(): void {
    const articleId = this.store.newConsommableArticleId();
    const quantite = this.store.newConsommableQuantite();
    if (!articleId || !quantite || quantite <= 0) {
      this.snackBar.open('Sélectionnez un article et une quantité valide', 'OK', {duration: 3000});
      return;
    }
    const article = this.store.availableArticles().find((a) => a.id === articleId);
    if (!article) {
      this.snackBar.open('Article introuvable', 'OK', {duration: 3000});
      return;
    }
    this.store.addConsommable(article, quantite);
  }

  protected removeConsommable(articleId: string): void {
    this.store.removeConsommable(articleId);
  }

  protected articleLabel(articleId: string): string {
    const a = this.store.availableArticles().find((x) => x.id === articleId);
    if (!a) return articleId;
    return `[${a.code}] ${a.libelle ?? ''}`;
  }

  protected loadJournalByDate(): void {
    const centerId = this.appShell.currentCenterId();
    if (!centerId) return;
    this.store.loadJournal({centerId, date: this.store.journalDate()});
  }

  protected loadSeanceDashboard(): void {
    const centerId = this.appShell.currentCenterId();
    if (!centerId) return;
    const [yearText, monthText] = this.store.dashboardMonth().split('-');
    const year = Number(yearText);
    const month = Number(monthText);
    if (!Number.isInteger(year) || !Number.isInteger(month) || month < 1 || month > 12) {
      this.snackBar.open('Mois invalide', 'OK', {duration: 3000});
      return;
    }
    this.store.loadDashboard({centerId, year, month});
  }

  protected exportDashboard(format: 'csv' | 'pdf' | 'xlsx'): void {
    const centerId = this.appShell.currentCenterId();
    if (!centerId) return;
    const [yearText, monthText] = this.store.dashboardMonth().split('-');
    const year = Number(yearText);
    const month = Number(monthText);
    this.api.exportSeanceDashboard(centerId, year, month, format).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `seances-dashboard-${year}-${String(month).padStart(2, '0')}.${format}`;
        a.click();
        URL.revokeObjectURL(url);
      },
      error: () => this.snackBar.open('Export impossible', 'OK', {duration: 3000}),
    });
  }

  protected openDashboardDetails(kind: 'presence' | 'absence'): void {
    const centerId = this.appShell.currentCenterId();
    if (!centerId) return;
    const [yearText, monthText] = this.store.dashboardMonth().split('-');
    const year = Number(yearText);
    const month = Number(monthText);
    this.store.openDashboardDetails(kind);
    this.store.loadDashboardDetails({centerId, year, month, kind});
  }

  protected closeDashboardDetails(): void {
    this.store.closeDashboardDetails();
  }

  protected dashboardDetailsTitle(): string {
    return this.store.dashboardDetailsKind() === 'presence' ? 'Liste des presences' : 'Liste des absences';
  }

  protected dashboardDetailsSubtitle(): string {
    return `${this.store.dashboardDetailItems().length} ligne(s)`;
  }

  protected canPreviousDashboardDetailPage(): boolean {
    return this.store.dashboardDetailPageIndex() > 0;
  }

  protected canNextDashboardDetailPage(): boolean {
    return this.store.dashboardDetailPageIndex() < this.store.dashboardDetailTotalPages() - 1;
  }

  protected previousDashboardDetailPage(): void {
    if (!this.canPreviousDashboardDetailPage()) return;
    this.store.setDashboardDetailPage(this.store.dashboardDetailPageIndex() - 1);
  }

  protected nextDashboardDetailPage(): void {
    if (!this.canNextDashboardDetailPage()) return;
    this.store.setDashboardDetailPage(this.store.dashboardDetailPageIndex() + 1);
  }

  protected dashboardDetailPaginationLabel(): string {
    const total = this.store.dashboardDetailItems().length;
    if (total === 0) return '0 / 0';
    return `${this.store.dashboardDetailPageIndex() + 1} / ${this.store.dashboardDetailTotalPages()}`;
  }

  protected formatDashboardDetailPatient(row: SeanceDashboardDetailItem): string {
    return (`${(row.patientNom ?? '').trim()} ${(row.patientPrenom ?? '').trim()}`).trim() || row.patientId;
  }

  protected formatDashboardDetailWeekday(row: SeanceDashboardDetailItem): string {
    const lang = this.translate.currentLang || 'fr';
    const date = new Date(row.dateSeance);
    if (Number.isNaN(date.getTime())) return row.weekday;
    const label = new Intl.DateTimeFormat(lang, {weekday: 'short'}).format(date);
    return label.charAt(0).toUpperCase() + label.slice(1);
  }

  protected weekdayBadgeClass(row: SeanceDashboardDetailItem): string {
    const map: Record<string, string> = {
      MONDAY: 'weekday-mon',
      TUESDAY: 'weekday-tue',
      WEDNESDAY: 'weekday-wed',
      THURSDAY: 'weekday-thu',
      FRIDAY: 'weekday-fri',
      SATURDAY: 'weekday-sat',
      SUNDAY: 'weekday-sun'
    };
    return map[(row.weekday || '').toUpperCase()] ?? '';
  }

  protected formatJournalPatient(row: {
    patientNom?: string | null;
    patientPrenom?: string | null;
    patientCode?: string | null;
    patientId: string
  }): string {
    return (`${(row.patientNom ?? '').trim()} ${(row.patientPrenom ?? '').trim()}`).trim() || row.patientCode || row.patientId;
  }

  protected formatPatientLabel(s: {
    patientNom?: string | null;
    patientPrenom?: string | null;
    patientCode?: string | null;
    patientId: string
  }): string {
    return (`${(s.patientNom ?? '').trim()} ${(s.patientPrenom ?? '').trim()}`).trim() || s.patientCode || s.patientId;
  }

  protected listForfaitName(seance: SeanceListItem): string {
    const forfait = seance.forfait;
    if (!forfait) {
      return '-';
    }
    return forfait.nom?.trim() || forfait.code?.trim() || '-';
  }

  protected listForfaitPrice(seance: SeanceListItem): string {
    const prix = seance.forfait?.prix;
    if (prix == null || Number.isNaN(Number(prix))) {
      return '';
    }
    return new Intl.NumberFormat(this.translate.currentLang || 'fr', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(Number(prix));
  }

  protected patientFullName(): string {
    const p = this.store.summary()?.patient;
    if (!p) return '-';
    return (`${(p.nom ?? '').trim()} ${(p.prenom ?? '').trim()}`).trim() || '-';
  }

  protected currentForfaitName(): string {
    const forfait = this.store.summary()?.forfait;
    if (!forfait) {
      return '';
    }
    return forfait.nom?.trim() || forfait.code?.trim() || '';
  }

  protected currentForfaitPrice(): string {
    const prix = this.store.summary()?.forfait?.prix;
    if (prix == null || Number.isNaN(Number(prix))) {
      return '';
    }
    return new Intl.NumberFormat(this.translate.currentLang || 'fr', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(Number(prix));
  }

  private getBarcodeDetectorConstructor(): BarcodeDetectorConstructor | null {
    return (window as Window & { BarcodeDetector?: BarcodeDetectorConstructor }).BarcodeDetector ?? null;
  }

  private supportsCameraScan(): boolean {
    return typeof navigator !== 'undefined' && !!navigator.mediaDevices?.getUserMedia && !!this.getBarcodeDetectorConstructor();
  }

  private async startCamera(): Promise<void> {
    if (!this.canScanSeances() || !this.supportsCameraScan()) {
      this.snackBar.open('Scan camera non supporte', 'OK', {duration: 3000});
      return;
    }
    const video = this.cameraVideoRef?.nativeElement;
    if (!video) return;
    this.cameraStarting.set(true);
    try {
      this.cameraStream = await navigator.mediaDevices.getUserMedia({
        video: {facingMode: {ideal: 'environment'}},
        audio: false
      });
      video.srcObject = this.cameraStream;
      await video.play();
      this.cameraActive.set(true);
      this.scheduleCameraDetection();
    } catch {
      this.stopCamera();
      this.snackBar.open('Impossible acces camera', 'OK', {duration: 3000});
    } finally {
      this.cameraStarting.set(false);
    }
  }

  private stopCamera(): void {
    if (this.cameraFrameId !== null) {
      cancelAnimationFrame(this.cameraFrameId);
      this.cameraFrameId = null;
    }
    const video = this.cameraVideoRef?.nativeElement;
    if (video) {
      video.pause();
      video.srcObject = null;
    }
    this.cameraStream?.getTracks().forEach((t) => t.stop());
    this.cameraStream = null;
    this.cameraActive.set(false);
    this.cameraDetectionInFlight = false;
  }

  private scheduleCameraDetection(): void {
    if (!this.cameraActive()) return;
    this.cameraFrameId = requestAnimationFrame(() => void this.detectQrFromCameraFrame());
  }

  private async detectQrFromCameraFrame(): Promise<void> {
    if (!this.cameraActive() || this.cameraDetectionInFlight) {
      this.scheduleCameraDetection();
      return;
    }
    const video = this.cameraVideoRef?.nativeElement;
    if (!video || video.readyState < 2) {
      this.scheduleCameraDetection();
      return;
    }
    const DetectorCtor = this.getBarcodeDetectorConstructor();
    if (!DetectorCtor) {
      this.stopCamera();
      return;
    }
    this.cameraDetector ??= new DetectorCtor({formats: ['qr_code']});
    this.cameraDetectionInFlight = true;
    try {
      const barcodes = await this.cameraDetector.detect(video);
      const value = (barcodes[0]?.rawValue ?? '').trim();
      if (value) {
        this.store.setQrCode(value);
        this.stopCamera();
        this.scanQr();
        return;
      }
    } catch {
      this.stopCamera();
      return;
    } finally {
      this.cameraDetectionInFlight = false;
    }
    this.scheduleCameraDetection();
  }

  private async scanQrFromImage(file: File): Promise<void> {
    const centerId = this.appShell.currentCenterId();
    if (!centerId) return;
    try {
      const imageUrl = URL.createObjectURL(file);
      const image = new Image();
      image.src = imageUrl;
      await image.decode();
      URL.revokeObjectURL(imageUrl);
      const DetectorCtor = this.getBarcodeDetectorConstructor();
      if (!DetectorCtor) {
        this.snackBar.open('Lecture image non supportee', 'OK', {duration: 3000});
        return;
      }
      const detector = new DetectorCtor({formats: ['qr_code']});
      const barcodes = await detector.detect(image);
      const value = (barcodes[0]?.rawValue ?? '').trim();
      if (!value) {
        this.snackBar.open('Aucun QR detecte', 'OK', {duration: 3000});
        return;
      }
      this.store.setQrCode(value);
      this.scanQr();
    } catch {
      this.snackBar.open('Impossible lire image QR', 'OK', {duration: 3000});
    }
  }

  private hasAnyRole(...roles: string[]): boolean {
    return roles.some((role) => this.auth.hasRole(role));
  }

  private mustRefreshFromEvent(type: string): boolean {
    return type.startsWith('SEANCE_') || type === 'PATIENT_SCANNED';
  }

  private refreshRealtime(centerId: string): void {
    this.store.loadSeances({centerId});
    this.store.loadJournal({centerId, date: this.store.journalDate()});
    this.refreshDashboardSilent(centerId);
    const selectedSeanceId = this.store.selectedSeanceId();
    if (selectedSeanceId) {
      this.store.loadSeanceSummary({seanceId: selectedSeanceId, centerId});
    }
  }

  private refreshDashboardSilent(centerId: string): void {
    const [yearText, monthText] = this.store.dashboardMonth().split('-');
    const year = Number(yearText);
    const month = Number(monthText);
    if (!Number.isInteger(year) || !Number.isInteger(month) || month < 1 || month > 12) {
      return;
    }
    this.store.loadDashboard({centerId, year, month});
  }
}

function todayIso(): string {
  return new Date().toISOString().slice(0, 10);
}

function currentMonthIso(): string {
  return todayIso().slice(0, 7);
}

function nullableText(value: string): string | null {
  const t = value.trim();
  return t.length ? t : null;
}

function parseNum(raw?: string | null): number | null {
  if (!raw) return null;
  const n = Number(raw);
  return Number.isFinite(n) ? n : null;
}
