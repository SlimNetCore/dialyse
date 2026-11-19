import {ChangeDetectionStrategy, Component, computed, effect, inject, untracked} from '@angular/core';
import {RouterLink} from '@angular/router';
import {MatDialog} from '@angular/material/dialog';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatIconModule} from '@angular/material/icon';
import {MatInputModule} from '@angular/material/input';
import {MatSnackBar, MatSnackBarModule} from '@angular/material/snack-bar';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {BackendApiService} from '../../core/api/backend-api.service';
import {isDoctorOnlyFor} from '../../core/auth/role-scope.guard';
import {AppShellStore} from '../../core/state/app-shell.store';
import {AuthStore} from '../../core/state/auth.store';
import {WebSocketService} from '../../core/ws/websocket.service';
import {PatientSummaryCardsComponent} from './patient-summary-cards.component';
import {PatientSummaryDetailsDialogComponent} from './patient-summary-details-dialog.component';
import {PatientTableauDeBordStore} from './state/patient-tableau-de-bord.store';

/** Évènements temps réel qui changent la synthèse ou les indicateurs PEC / attestations. */
const EVENEMENTS_RAFRAICHISSANT = /^(PATIENT_|PEC_|ATTESTATION_)/;

/**
 * Tableau de bord du module Patients (/patients/tableau-de-bord) : synthèse mensuelle des patients (répartitions,
 * rapport imprimable, détails) et indicateurs PEC / attestations, chacun menant à la liste correspondante. Lecture
 * seule : la saisie reste dans la liste des patients et les listes PEC / attestations.
 */
@Component({
  selector: 'app-patient-tableau-de-bord',
  standalone: true,
  imports: [
    RouterLink,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSnackBarModule,
    TranslateModule,
    PatientSummaryCardsComponent,
  ],
  providers: [PatientTableauDeBordStore],
  templateUrl: './patient-tableau-de-bord.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './patient-tableau-de-bord.component.css',
})
export class PatientTableauDeBordComponent {
  private readonly store = inject(PatientTableauDeBordStore);
  private readonly auth = inject(AuthStore);
  private readonly appShell = inject(AppShellStore);
  private readonly ws = inject(WebSocketService);
  private readonly api = inject(BackendApiService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly translate = inject(TranslateService);

  /** Prises en charge et attestations relèvent de l'administration : masquées au médecin seul (comme avant). */
  protected readonly canManageAdministrative = computed(() => !isDoctorOnlyFor((role) => this.auth.hasRole(role)));

  readonly summaryMonth = this.store.summaryMonth;
  readonly summary = this.store.summary;
  readonly summaryLoading = this.store.summaryLoading;
  readonly printingReport = this.store.printingReport;
  readonly expirationDays = this.store.expirationDays;
  readonly pecStats = this.store.pecStats;
  readonly pecStatsLoading = this.store.pecStatsLoading;

  constructor() {
    effect(() => {
      const centerId = this.appShell.currentCenterId();
      const month = this.store.summaryMonth();
      untracked(() => centerId ? this.store.loadSummary({centerId, month}) : this.store.reset());
    });

    effect(() => {
      const centerId = this.appShell.currentCenterId();
      const expirationDays = this.store.expirationDays();
      if (!centerId || !this.canManageAdministrative()) return;
      untracked(() => this.store.loadPecStats({centerId, expirationDays}));
    });

    effect(() => {
      const evt = this.ws.lastEvent();
      if (!evt || !EVENEMENTS_RAFRAICHISSANT.test(evt.type)) return;
      untracked(() => this.refresh());
    });
  }

  onSummaryMonthChange(month: string): void {
    this.store.setSummaryMonth(month);
  }

  onExpirationDaysChange(event: Event): void {
    const value = Number((event.target as HTMLInputElement | null)?.value);
    this.store.setExpirationDays(value);
  }

  printReport(): void {
    const centerId = this.auth.centerId();
    if (!centerId) return;
    this.store.printReport({centerId});
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

  private refresh(): void {
    const centerId = this.appShell.currentCenterId();
    if (!centerId) return;
    this.store.loadSummary({centerId, month: this.store.summaryMonth()});
    if (this.canManageAdministrative()) {
      this.store.loadPecStats({centerId, expirationDays: this.store.expirationDays()});
    }
  }
}
