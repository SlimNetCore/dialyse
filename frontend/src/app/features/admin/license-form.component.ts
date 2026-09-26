import {ChangeDetectionStrategy, Component, computed, effect, inject, OnInit, signal, untracked} from '@angular/core';
import {Router, RouterLink} from '@angular/router';
import {MatCardModule} from '@angular/material/card';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatSelectModule} from '@angular/material/select';
import {MatButtonModule} from '@angular/material/button';
import {MatCheckboxModule} from '@angular/material/checkbox';
import {MatIconModule} from '@angular/material/icon';
import {MatTooltipModule} from '@angular/material/tooltip';
import {MatSnackBar, MatSnackBarModule} from '@angular/material/snack-bar';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {compatForm} from '@angular/forms/signals/compat';
import {FormField, FormRoot, required} from '@angular/forms/signals';
import {License, LicenseApiService} from '../../core/api/license-api.service';
import {Societe, SocieteApiService} from '../../core/api/societe-api.service';
import {
  addDays,
  defaultValidUntil,
  LicenseFormValue,
  toDateInputValue,
  validateLicenseForm,
} from './license-form.util';

/**
 * Attribution d'une licence à une société (propriétaire / SUPERADMIN) : une licence — donc une clé — est émise
 * pour chaque centre actif de la société, ou pour la sélection cochée.
 */
@Component({
  selector: 'app-license-form',
  standalone: true,
  imports: [
    RouterLink,
    TranslateModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatCheckboxModule,
    MatIconModule,
    MatTooltipModule,
    MatSnackBarModule,
    MatProgressSpinnerModule,
    FormRoot,
    FormField,
  ],
  templateUrl: './license-form.component.html',
  styleUrl: './license-form.component.css',
  changeDetection: ChangeDetectionStrategy.Eager,
})
export class LicenseFormComponent implements OnInit {
  readonly saving = signal(false);
  readonly issued = signal<License[]>([]);
  readonly societes = signal<Societe[]>([]);
  /** Centres cochés de la société choisie (tous les centres actifs par défaut). */
  readonly selectedCenterIds = signal<ReadonlySet<string>>(new Set());

  readonly form = signal<LicenseFormValue>({
    societeId: '',
    type: 'STANDARD',
    maxUsers: 5,
    validFrom: toDateInputValue(new Date()),
    validUntil: toDateInputValue(addDays(new Date(), 365)),
  });
  readonly licenseForm = compatForm(this.form, (f) => {
    required(f.societeId);
    required(f.validFrom);
    required(f.validUntil);
  });
  readonly selectedCount = computed(() => this.selectedCenterIds().size);
  private readonly selectedSocieteId = computed(() => this.form().societeId);
  /** Centres actifs de la société choisie : seuls ceux-là peuvent être licenciés. */
  readonly activeCentres = computed(
    () => this.societes().find((s) => s.id === this.selectedSocieteId())?.centres.filter((c) => c.actif) ?? [],
  );

  private readonly api = inject(LicenseApiService);
  private readonly societeApi = inject(SocieteApiService);
  private readonly router = inject(Router);
  private readonly snackbar = inject(MatSnackBar);
  private readonly translate = inject(TranslateService);

  constructor() {
    // Changer de société recoche tous ses centres actifs.
    effect(() => {
      const centres = this.activeCentres();
      untracked(() => this.selectedCenterIds.set(new Set(centres.map((c) => c.id))));
    });
  }

  ngOnInit(): void {
    this.societeApi.list('', 0, 100).subscribe({
      next: (page) => this.societes.set(page.items.filter((s) => s.actif)),
      error: () => this.notify('LICENCES.SOCIETES_LOAD_ERROR', 3500),
    });
  }

  toggleCentre(id: string, checked: boolean): void {
    this.selectedCenterIds.update((current) => {
      const next = new Set(current);
      if (checked) next.add(id); else next.delete(id);
      return next;
    });
  }

  setType(type: 'STANDARD' | 'TRIAL'): void {
    this.form.update((f) => ({
      ...f,
      type,
      maxUsers: type === 'TRIAL' ? f.maxUsers || 3 : f.maxUsers,
      validUntil: defaultValidUntil(type, f.validFrom),
    }));
  }

  save(): void {
    if (this.saving()) return;
    const f = this.form();
    const error = validateLicenseForm(f, this.selectedCount());
    if (error) {
      this.notify('LICENCES.' + error, 3000);
      return;
    }
    const all = this.selectedCount() === this.activeCentres().length;
    this.saving.set(true);
    this.api.issueForSociete({
      societeId: f.societeId,
      centerIds: all ? [] : [...this.selectedCenterIds()],
      type: f.type,
      maxUsers: f.maxUsers,
      validFrom: new Date(f.validFrom).toISOString(),
      validUntil: new Date(f.validUntil).toISOString(),
    }).subscribe({
      next: (licenses) => {
        this.saving.set(false);
        this.issued.set(licenses);
      },
      error: (err) => {
        this.saving.set(false);
        this.snackbar.open(err?.error?.detail || this.translate.instant('LICENCES.ISSUE_ERROR'),
          this.translate.instant('COMMON.OK'), {duration: 4500});
      },
    });
  }

  copyKey(license: License): void {
    navigator.clipboard
      .writeText(license.licenseKey)
      .then(() => this.notify('LICENCES.KEY_COPIED', 2000))
      .catch(() => this.notify('LICENCES.COPY_FAILED', 3000));
  }

  goToList(): void {
    void this.router.navigate(['/admin/licenses']);
  }

  private notify(key: string, duration: number): void {
    this.snackbar.open(this.translate.instant(key), this.translate.instant('COMMON.OK'), {duration});
  }
}
