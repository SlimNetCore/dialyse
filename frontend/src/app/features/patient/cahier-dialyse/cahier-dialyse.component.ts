import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  computed,
  HostListener,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import {CommonModule} from '@angular/common';
import {ActivatedRoute, Router} from '@angular/router';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatCardModule} from '@angular/material/card';
import {MatTooltipModule} from '@angular/material/tooltip';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {TranslateModule} from '@ngx-translate/core';
import {BackendApiService, SeanceListItem, SeanceSummary} from '../../../core/api/backend-api.service';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {CahierStepParamedicalComponent} from './cahier-step-paramedical.component';
import {CahierStepMedicalComponent} from './cahier-step-medical.component';

@Component({
  selector: 'app-cahier-dialyse',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatIconModule,
    MatCardModule,
    MatTooltipModule,
    MatProgressSpinnerModule,
    TranslateModule,
    CahierStepParamedicalComponent,
    CahierStepMedicalComponent,
  ],
  templateUrl: './cahier-dialyse.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './cahier-dialyse.component.css',
})
export class CahierDialyseComponent implements AfterViewInit, OnInit {
  readonly isMobileViewport = signal(
    typeof window !== 'undefined' ? window.innerWidth <= 900 : false,
  );

  readonly loadingSeances = signal(false);
  readonly loadingSummary = signal(false);
  readonly loadError = signal<string | null>(null);
  readonly patientSeances = signal<SeanceListItem[]>([]);
  readonly selectedSummary = signal<SeanceSummary | null>(null);
  readonly currentPageIndex = signal(0);

  private readonly route = inject(ActivatedRoute);
  readonly patientId = this.route.snapshot.paramMap.get('id') ?? '';

  private readonly router = inject(Router);
  private readonly appShell = inject(AppShellStore);
  private readonly api = inject(BackendApiService);

  readonly selectedSeance = computed(() => this.patientSeances()[this.currentPageIndex()] ?? null);
  readonly totalPages = computed(() => this.patientSeances().length);
  readonly pageNumber = computed(() => this.currentPageIndex() + 1);
  readonly canGoPrev = computed(() => this.currentPageIndex() > 0);
  readonly canGoNext = computed(() => this.currentPageIndex() < this.totalPages() - 1);
  readonly hasSeances = computed(() => this.totalPages() > 0);
  readonly selectedSeanceStatus = computed(() => this.selectedSeance()?.status ?? 'BROUILLON');
  readonly selectedSeanceDate = computed(() => this.selectedSeance()?.dateSeance ?? null);
  readonly selectedConsommables = computed(() => this.selectedSummary()?.consommables ?? []);
  readonly hasConsommables = computed(() => this.selectedConsommables().length > 0);
  readonly selectedForfait = computed(() => this.selectedSummary()?.forfait ?? null);

  ngOnInit(): void {
    this.loadSeanceBook();
  }

  ngAfterViewInit(): void {
    this.isMobileViewport.set(typeof window !== 'undefined' ? window.innerWidth <= 900 : false);
  }

  @HostListener('window:resize')
  onResize(): void {
    this.isMobileViewport.set(window.innerWidth <= 900);
  }

  reloadBook(): void {
    this.loadSeanceBook();
  }

  statusClass(status: string): string {
    const normalized = (status ?? '').toUpperCase();
    if (normalized === 'VALIDEE') return 'status-validee';
    if (normalized === 'SIGNEE') return 'status-signee';
    if (normalized === 'FACTUREE') return 'status-facturee';
    return 'status-brouillon';
  }

  formatSeanceDate(rawDate: string | null): string {
    const source = (rawDate ?? '').trim();
    const match = source.match(/^(\d{4})-(\d{2})-(\d{2})$/);
    if (!match) return source || '-';
    return `${match[3]}/${match[2]}/${match[1]}`;
  }

  formatNumber(value: number | string | null | undefined): string {
    if (value === null || value === undefined || value === '') return '-';
    const parsed = Number(value);
    if (!Number.isFinite(parsed)) return '-';
    return new Intl.NumberFormat('fr-FR', {maximumFractionDigits: 2}).format(parsed);
  }

  formatCurrency(value: number | string | null | undefined): string {
    if (value === null || value === undefined || value === '') return '-';
    const parsed = Number(value);
    if (!Number.isFinite(parsed)) return '-';
    return new Intl.NumberFormat('fr-FR', {
      style: 'currency',
      currency: 'DZD',
      maximumFractionDigits: 2,
    }).format(parsed);
  }

  goToPage(index: number): void {
    if (index < 0 || index >= this.totalPages()) return;
    this.currentPageIndex.set(index);
    this.loadSelectedSeanceSummary();
  }

  onSeanceSelectChange(event: any): void {
    const index = parseInt(event.target.value, 10);
    this.goToPage(index);
  }

  goToPreviousPage(): void {
    if (!this.canGoPrev()) return;
    this.goToPage(this.currentPageIndex() - 1);
  }

  goToNextPage(): void {
    if (!this.canGoNext()) return;
    this.goToPage(this.currentPageIndex() + 1);
  }

  goBack(): void {
    this.router.navigate(['/patients']);
  }

  private loadSeanceBook(): void {
    const centerId = this.appShell.currentCenterId();
    if (!centerId || !this.patientId) {
      this.loadError.set('COMMON.ERROR_MISSING_DATA');
      return;
    }

    this.loadingSeances.set(true);
    this.loadError.set(null);
    this.api.listSeances(centerId, 0, 500).subscribe({
      next: (response) => {
        const seances = (response.items ?? [])
          .filter((item: SeanceListItem) => {
            if (item.centerId !== centerId || item.patientId !== this.patientId) return false;
            const status = (item.status ?? '').toUpperCase();
            return status === 'VALIDEE' || status === 'SIGNEE' || status === 'FACTUREE';
          })
          .sort((a: SeanceListItem, b: SeanceListItem) => b.dateSeance.localeCompare(a.dateSeance));

        this.patientSeances.set(seances);
        this.currentPageIndex.set(0);
        this.loadingSeances.set(false);

        if (seances.length > 0) {
          this.loadSelectedSeanceSummary();
        } else {
          this.selectedSummary.set(null);
        }
      },
      error: () => {
        this.loadingSeances.set(false);
        this.loadError.set('COMMON.ERROR_LOAD');
      },
    });
  }

  private loadSelectedSeanceSummary(): void {
    const centerId = this.appShell.currentCenterId();
    const seance = this.selectedSeance();
    if (!centerId || !seance?.id) {
      this.selectedSummary.set(null);
      return;
    }

    this.loadingSummary.set(true);
    this.api.getSeanceSummary(seance.id, centerId).subscribe({
      next: (summary) => {
        this.selectedSummary.set(summary);
        this.loadingSummary.set(false);
      },
      error: () => {
        this.loadingSummary.set(false);
      },
    });
  }
}
