import {ChangeDetectionStrategy, Component, EventEmitter, inject, Input, OnInit, Output, signal,} from '@angular/core';
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
    CommonModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    TranslateModule,
  ],
  templateUrl: './cahier-step-fiche.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './cahier-step-fiche.component.css',
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
      const rendered = value
        .map((item) => this.displayValue(item))
        .filter((item) => item !== '-')
        .join(', ');
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
      const nssValue =
        entity['numeroAssurance'] ??
        entity['nss'] ??
        entity['numero'] ??
        entity['value'] ??
        entity['code'];
      return this.displayValue(nssValue);
    }
    return this.displayValue(value);
  }

  generatorStateLabel(value: unknown): string {
    const normalized = this.displayValue(value);
    if (normalized === '-') {
      return '-';
    }
    const key = `GENERATEUR_ETATS.${normalized.toUpperCase()}`;
    const translated = this.translate.instant(key);
    return translated && translated !== key ? translated : normalized.replaceAll('_', ' ');
  }

  generatorSummary(): string {
    const data = this.patientData();
    const name = this.displayValue(data.generateurNom);
    const brand = this.displayValue(data.generateurMarque);
    const state = this.generatorStateLabel(data.generateurEtat);
    const parts = [name, brand, state].filter((item) => item !== '-');
    return parts.length ? parts.join(' — ') : '-';
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
      },
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
