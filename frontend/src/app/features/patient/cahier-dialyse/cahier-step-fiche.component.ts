import {Component, EventEmitter, inject, Input, OnInit, Output, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {MatCardModule} from '@angular/material/card';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {MatSnackBar} from '@angular/material/snack-bar';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {BackendApiService} from '../../../core/api/backend-api.service';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {AuthStore} from '../../../core/state/auth.store';

@Component({
  selector: 'app-cahier-step-fiche',
  standalone: true,
  imports: [
    CommonModule, MatCardModule,
    MatButtonModule, MatIconModule, MatProgressSpinnerModule, TranslateModule
  ],
  template: `
    <div class="step-fiche-content">
      <mat-card class="patient-card" data-testid="cahier-fiche-card">
        <mat-card-header>
          <mat-card-title>{{ 'CAHIER.FICHE_PATIENT_TITLE' | translate }}</mat-card-title>
          <mat-card-subtitle>
            {{ recapMode ? ('CAHIER.FICHE_INFO' | translate) + ' - Recapitulatif' : ('CAHIER.FICHE_INFO' | translate) }}
          </mat-card-subtitle>
        </mat-card-header>

        @if (loading()) {
          <div class="loading-wrap">
            <mat-progress-spinner mode="indeterminate" diameter="40"></mat-progress-spinner>
          </div>
        } @else if (error()) {
          <mat-card-content class="error-msg">
            {{ error() }}
          </mat-card-content>
        } @else {
          <mat-card-content>
            <div class="identity-banner">
              <div>
                <p class="banner-label">Patient</p>
                <h3>{{ patientData().nom || '-' }} {{ patientData().prenom || '' }}</h3>
              </div>
              <div class="banner-pills">
                <span class="age-pill">{{ ageBadgeLabel() }}</span>
                <span class="etat-pill">{{ displayValue(patientData().etatPatient) }}</span>
              </div>
            </div>

            <div class="recap-grid" [class.recap-grid-compact]="!recapMode">
              <section class="recap-section">
                <h4>Identite</h4>
                <div class="recap-items">
                  <div class="recap-item">
                    <span class="recap-label">{{ 'PATIENT_FORM.CODE_PATIENT' | translate }}</span>
                    <strong>{{ displayValue(patientData().code) }}</strong>
                  </div>
                  <div class="recap-item">
                    <span class="recap-label">{{ 'PATIENT_FORM.NUMERO_ASSURANCE' | translate }}</span>
                    <strong>{{ displayNumeroAssurance(patientData().numeroAssurance) }}</strong>
                  </div>
                  <div class="recap-item">
                    <span class="recap-label">{{ 'PATIENT_FORM.SEXE' | translate }}</span>
                    <strong>{{ displayValue(patientData().sexe) }}</strong>
                  </div>
                  <div class="recap-item">
                    <span class="recap-label">{{ 'PATIENT_FORM.GROUPE_SANGUIN' | translate }}</span>
                    <strong>{{ displayValue(patientData().groupeSanguin) }}</strong>
                  </div>
                </div>
              </section>

              <section class="recap-section">
                <h4>Admission</h4>
                <div class="recap-items">
                  <div class="recap-item">
                    <span class="recap-label">{{ 'PATIENT_FORM.DATE_NAISSANCE' | translate }}</span>
                    <strong>{{ displayValue(patientData().dateNaissance) }}</strong>
                  </div>
                  <div class="recap-item">
                    <span class="recap-label">{{ 'PATIENT_FORM.DATE_ADMISSION' | translate }}</span>
                    <strong>{{ displayValue(patientData().dateAdmission) }}</strong>
                  </div>
                </div>
              </section>

              <section class="recap-section recap-section-wide">
                <h4>Coordonnees</h4>
                <div class="recap-items">
                  <div class="recap-item recap-item-wide">
                    <span class="recap-label">{{ 'PATIENT_FORM.ADRESSE' | translate }}</span>
                    <strong>{{ displayValue(patientData().adresse) }}</strong>
                  </div>
                  <div class="recap-item">
                    <span class="recap-label">{{ 'PATIENT_FORM.TELEPHONE' | translate }}</span>
                    <strong>{{ displayValue(patientData().telPersonnel) }}</strong>
                  </div>
                  <div class="recap-item">
                    <span class="recap-label">{{ 'PATIENT_FORM.EMAIL' | translate }}</span>
                    <strong>{{ displayValue(patientData().email) }}</strong>
                  </div>
                </div>
              </section>
            </div>

            <div class="actions" *ngIf="showProceed || !readonly">
              @if (!readonly) {
                <button mat-raised-button color="primary" (click)="save()" [disabled]="!canSave()">
                  <mat-icon>save</mat-icon>
                  {{ 'COMMON.SAVE' | translate }}
                </button>
              }
              <button mat-stroked-button *ngIf="showProceed" (click)="proceed()">
                <mat-icon>arrow_forward</mat-icon>
                {{ 'COMMON.NEXT_STEP' | translate }}
              </button>
            </div>
          </mat-card-content>
        }
      </mat-card>
    </div>
  `,
  styles: [`
    .step-fiche-content { padding: 8px 0; }
    .patient-card { margin: 0; border-radius: 20px; }
    .identity-banner {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 12px;
      margin-bottom: 14px;
      padding: 14px;
      border: 1px solid var(--app-border);
      border-radius: 16px;
      background: var(--app-frost);
    }
    .banner-label {
      margin: 0;
      font-size: 11px;
      text-transform: uppercase;
      letter-spacing: .08em;
      color: var(--app-muted);
    }
    .identity-banner h3 {
      margin: 4px 0 0;
      color: var(--app-text);
      font-size: 1.1rem;
    }
    .banner-pills {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      flex-wrap: wrap;
      justify-content: flex-end;
    }
    .age-pill {
      padding: 6px 12px;
      border-radius: 999px;
      border: 1px solid color-mix(in srgb, var(--app-primary-outline) 80%, #b45309 20%);
      background: color-mix(in srgb, var(--app-primary-soft) 72%, #f59e0b 28%);
      color: var(--app-text);
      font-weight: 700;
      font-size: 12px;
      white-space: nowrap;
    }
    .etat-pill {
      padding: 6px 12px;
      border-radius: 999px;
      border: 1px solid var(--app-primary-outline);
      background: var(--app-primary-soft);
      color: var(--app-primary);
      font-weight: 700;
      font-size: 12px;
      white-space: nowrap;
    }
    .recap-grid {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 14px;
      margin-bottom: 12px;
    }
    .recap-grid-compact {
      grid-template-columns: 1fr;
    }
    .recap-section {
      border: 1px solid var(--app-border);
      border-radius: 16px;
      padding: 14px;
      background: var(--app-surface-soft);
    }
    .recap-section-wide {
      grid-column: 1 / -1;
    }
    .recap-section h4 {
      margin: 0 0 10px;
      font-size: 12px;
      text-transform: uppercase;
      letter-spacing: .08em;
      color: var(--app-primary);
    }
    .recap-items {
      display: grid;
      gap: 10px;
      grid-template-columns: repeat(2, minmax(0, 1fr));
    }
    .recap-item {
      border: 1px solid var(--app-border);
      border-radius: 12px;
      padding: 10px 12px;
      background: var(--app-frost);
    }
    .recap-item-wide {
      grid-column: 1 / -1;
    }
    .recap-label {
      display: block;
      font-size: 11px;
      color: var(--app-muted);
      margin-bottom: 4px;
    }
    .recap-item strong {
      display: block;
      color: var(--app-text);
      font-size: 13px;
      font-weight: 700;
      word-break: break-word;
    }
    .loading-wrap { display: flex; justify-content: center; padding: 32px 0; }
    .error-msg { color: var(--app-error); font-size: 13px; padding: 12px; background: rgba(239, 68, 68, 0.08); border-radius: 8px; }
    .actions { display: flex; gap: 12px; justify-content: flex-end; margin-top: 16px; }
    @media (max-width: 1100px) {
      .recap-items { grid-template-columns: 1fr; }
    }
    @media (max-width: 900px) {
      .identity-banner { flex-wrap: wrap; }
      .recap-grid { grid-template-columns: 1fr; }
    }
  `]
})
export class CahierStepFicheComponent implements OnInit {
  @Input() patientId!: string;
  @Input() readonly = false;
  @Input() recapMode = false;
  @Input() showProceed = true;
  @Output() dataChange = new EventEmitter<any>();
  @Output() validChange = new EventEmitter<boolean>();
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly patientData = signal<any>({});
  private readonly api = inject(BackendApiService);
  private readonly appShell = inject(AppShellStore);
  private readonly auth = inject(AuthStore);
  private readonly snackBar = inject(MatSnackBar);
  private readonly translate = inject(TranslateService);

  readonly canSave = () => this.auth.hasRole('ADMIN') || this.auth.hasRole('SECRETAIRE');

  ngOnInit(): void {
    this.loadPatient();
  }

  save(): void {
    this.snackBar.open(this.translate.instant('CAHIER.FICHE_READONLY'), 'OK', {duration: 3000});
  }

  proceed(): void {
    this.validChange.emit(true);
  }

  displayValue(value: unknown): string {
    if (value === null || value === undefined) return '-';
    if (typeof value === 'string') {
      const normalized = value.trim();
      return normalized ? normalized : '-';
    }
    if (typeof value === 'number' || typeof value === 'boolean') {
      return String(value);
    }
    if (Array.isArray(value)) {
      const rendered = value.map((item) => this.displayValue(item)).filter((item) => item !== '-').join(', ');
      return rendered || '-';
    }
    if (typeof value === 'object') {
      const entity = value as Record<string, unknown>;
      const prioritizedKeys = ['value', 'label', 'libelle', 'code', 'id'];
      for (const key of prioritizedKeys) {
        if (entity[key] !== undefined && entity[key] !== null) {
          const normalized = this.displayValue(entity[key]);
          if (normalized !== '-') return normalized;
        }
      }
      return '-';
    }
    return '-';
  }

  displayNumeroAssurance(value: unknown): string {
    if (value && typeof value === 'object' && !Array.isArray(value)) {
      const entity = value as Record<string, unknown>;
      const nssValue = entity['numeroAssurance'] ?? entity['nss'] ?? entity['numero'] ?? entity['value'] ?? entity['code'];
      return this.displayValue(nssValue);
    }
    return this.displayValue(value);
  }

  ageBadgeLabel(): string {
    const age = this.computeAge(this.patientData().dateNaissance);
    return age === null ? 'Age: -' : `Age: ${age} ans`;
  }

  private loadPatient(): void {
    const centerId = this.appShell.currentCenterId();
    if (!centerId || !this.patientId) {
      this.error.set(this.translate.instant('COMMON.ERROR_MISSING_DATA'));
      this.loading.set(false);
      return;
    }

    this.api.getPatient(this.patientId, centerId, this.auth.username() || '').subscribe({
      next: (patient) => {
        this.patientData.set(patient);
        this.loading.set(false);
        this.validChange.emit(true);
      },
      error: (err) => {
        this.error.set(err?.error?.message || this.translate.instant('COMMON.ERROR_LOAD'));
        this.loading.set(false);
      }
    });
  }

  private computeAge(rawDate: unknown): number | null {
    const source = this.displayValue(rawDate);
    if (source === '-') return null;
    const parsed = this.parseDate(source);
    if (!parsed) return null;

    const today = new Date();
    let age = today.getFullYear() - parsed.getFullYear();
    const monthDiff = today.getMonth() - parsed.getMonth();
    const dayDiff = today.getDate() - parsed.getDate();
    if (monthDiff < 0 || (monthDiff === 0 && dayDiff < 0)) {
      age--;
    }
    if (age < 0 || age > 130) return null;
    return age;
  }

  private parseDate(value: string): Date | null {
    const clean = value.trim();
    if (!clean) return null;

    const iso = clean.match(/^(\d{4})-(\d{2})-(\d{2})/);
    if (iso) {
      const parsed = new Date(Number(iso[1]), Number(iso[2]) - 1, Number(iso[3]));
      return Number.isNaN(parsed.getTime()) ? null : parsed;
    }

    const fr = clean.match(/^(\d{2})\/(\d{2})\/(\d{4})$/);
    if (fr) {
      const parsed = new Date(Number(fr[3]), Number(fr[2]) - 1, Number(fr[1]));
      return Number.isNaN(parsed.getTime()) ? null : parsed;
    }

    return null;
  }
}




