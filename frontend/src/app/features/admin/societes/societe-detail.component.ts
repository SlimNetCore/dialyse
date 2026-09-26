import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  OnDestroy,
  OnInit,
  signal
} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {ActivatedRoute, RouterLink} from '@angular/router';
import {MatCardModule} from '@angular/material/card';
import {MatTableModule} from '@angular/material/table';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatSelectModule} from '@angular/material/select';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatSnackBar} from '@angular/material/snack-bar';
import {MatTooltipModule} from '@angular/material/tooltip';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {compatForm} from '@angular/forms/signals/compat';
import {FormField, FormRoot, required} from '@angular/forms/signals';
import {DirectionAccountsComponent} from './direction-accounts.component';
import {CentreSociete, Societe, SocieteApiService} from '../../../core/api/societe-api.service';
import {
  centresSummary,
  centreToForm,
  emptyCentreForm,
  emptySocieteForm,
  isCentreFormValid,
  isSocieteFormValid,
  location,
  societeToForm,
  precheckLogoFile,
  toCentrePayload,
  toSocietePayload,
} from './societe.util';

/**
 * Détail d'une société (SUPERADMIN) : modification des informations, activation, et gestion de ses centres
 * (création, modification, activation, transfert vers une autre société).
 * <p>
 * La règle « une société conserve toujours au moins un centre actif » est appliquée par le serveur ; ce écran
 * affiche simplement le refus lorsqu'elle est violée.
 */
@Component({
  selector: 'app-societe-detail',
  standalone: true,
  imports: [
    RouterLink,
    TranslateModule,
    MatCardModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatProgressBarModule,
    MatTooltipModule,
    FormRoot,
    FormField,
    DirectionAccountsComponent,
  ],
  templateUrl: './societe-detail.component.html',
  styleUrl: './societes.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SocieteDetailComponent implements OnInit, OnDestroy {
  protected readonly societe = signal<Societe | null>(null);
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly notFound = signal(false);
  protected readonly displayedColumns = ['code', 'nom', 'ville', 'telephone', 'actif', 'actions'];
  /** Logo de la société : aperçu (object URL d'un blob authentifié) et erreurs de validation de l'envoi. */
  protected readonly logoUrl = signal<string | null>(null);
  protected readonly logoErrors = signal<string[]>([]);
  protected readonly uploadingLogo = signal(false);
  protected readonly societeModel = signal(emptySocieteForm());
  protected readonly societeForm = compatForm(this.societeModel, (f) => {
    required(f.code);
    required(f.raisonSociale);
  });
  /** Formulaire de centre : création (`editingCentreId` = null) ou modification. */
  protected readonly showCentreForm = signal(false);
  protected readonly editingCentreId = signal<string | null>(null);
  protected readonly centreModel = signal(emptyCentreForm());
  protected readonly centreForm = compatForm(this.centreModel, (f) => {
    required(f.code);
    required(f.nom);
  });
  /** Transfert d'un centre vers une autre société. */
  protected readonly transferring = signal<CentreSociete | null>(null);
  protected readonly transferTargets = signal<Societe[]>([]);
  protected readonly transferModel = signal({societeCibleId: ''});
  protected readonly transferForm = compatForm(this.transferModel);
  protected readonly location = location;
  protected readonly summary = computed(() => {
    const s = this.societe();
    return s ? centresSummary(s) : {actifs: 0, total: 0};
  });
  protected readonly canSaveSociete = computed(() => isSocieteFormValid(this.societeModel()) && !this.saving());
  protected readonly canSaveCentre = computed(() => isCentreFormValid(this.centreModel()) && !this.saving());
  protected readonly canTransfer = computed(() => !!this.transferModel().societeCibleId && !this.saving());
  private readonly api = inject(SocieteApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly snack = inject(MatSnackBar);
  private readonly translate = inject(TranslateService);
  private readonly destroyRef = inject(DestroyRef);

  ngOnInit(): void {
    this.reload();
  }

  ngOnDestroy(): void {
    this.revokeLogo();
  }

  protected onLogoSelected(event: Event): void {
    const s = this.societe();
    const input = event.target as HTMLInputElement;
    const file = input.files?.item(0) ?? null;
    input.value = '';
    if (!s || !file) return;
    this.logoErrors.set([]);
    const problem = precheckLogoFile(file);
    if (problem) {
      this.logoErrors.set([problem]);
      return;
    }
    this.uploadingLogo.set(true);
    this.api.uploadLogo(s.id, file).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (updated) => {
        this.uploadingLogo.set(false);
        this.apply(updated);
        this.notify('SOCIETES.LOGO_UPLOADED_OK');
      },
      error: (err) => {
        this.uploadingLogo.set(false);
        const codes = (err?.status === 422 ? err.error?.violations : null) as Array<{ code?: string }> | null;
        if (codes?.length) {
          this.logoErrors.set(codes.map((v) => v.code ?? 'IMAGE_INVALID'));
        } else {
          this.fail(err);
        }
      },
    });
  }

  protected removeLogo(): void {
    const s = this.societe();
    if (!s) return;
    this.api.deleteLogo(s.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.apply({...s, hasLogo: false});
        this.notify('SOCIETES.LOGO_REMOVED_OK');
      },
      error: (err) => this.fail(err),
    });
  }

  // ───────────────────────────── Logo ─────────────────────────────

  protected saveSociete(): void {
    const s = this.societe();
    if (!s || !this.canSaveSociete()) return;
    this.run(this.api.update(s.id, toSocietePayload(this.societeModel())), 'SOCIETES.UPDATED_OK');
  }

  protected toggleSociete(): void {
    const s = this.societe();
    if (!s) return;
    this.run(this.api.setActive(s.id, !s.actif), s.actif ? 'SOCIETES.DEACTIVATED_OK' : 'SOCIETES.ACTIVATED_OK');
  }

  protected openCentreForm(centre?: CentreSociete): void {
    this.editingCentreId.set(centre?.id ?? null);
    this.centreModel.set(centre ? centreToForm(centre) : emptyCentreForm());
    this.showCentreForm.set(true);
  }

  protected closeCentreForm(): void {
    this.showCentreForm.set(false);
    this.editingCentreId.set(null);
  }

  // ───────────────────────────── Société ─────────────────────────────

  protected saveCentre(): void {
    const s = this.societe();
    if (!s || !this.canSaveCentre()) return;
    const payload = toCentrePayload(this.centreModel());
    const editing = this.editingCentreId();
    this.run(editing ? this.api.updateCentre(s.id, editing, payload) : this.api.addCentre(s.id, payload),
      editing ? 'SOCIETES.CENTRE_UPDATED_OK' : 'SOCIETES.CENTRE_CREATED_OK', () => this.closeCentreForm());
  }

  protected toggleCentre(centre: CentreSociete): void {
    const s = this.societe();
    if (!s) return;
    this.run(this.api.setCentreActive(s.id, centre.id, !centre.actif),
      centre.actif ? 'SOCIETES.CENTRE_DEACTIVATED_OK' : 'SOCIETES.CENTRE_ACTIVATED_OK');
  }

  // ───────────────────────────── Centres ─────────────────────────────

  protected openTransfer(centre: CentreSociete): void {
    const s = this.societe();
    if (!s) return;
    this.transferring.set(centre);
    this.transferModel.set({societeCibleId: ''});
    this.api.list('', 0, 100).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (page) => this.transferTargets.set(page.items.filter((x) => x.id !== s.id && x.actif)),
      error: () => this.transferTargets.set([]),
    });
  }

  protected closeTransfer(): void {
    this.transferring.set(null);
  }

  protected confirmTransfer(): void {
    const s = this.societe();
    const centre = this.transferring();
    if (!s || !centre || !this.canTransfer()) return;
    this.saving.set(true);
    this.api.transferCentre(s.id, centre.id, this.transferModel().societeCibleId)
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.saving.set(false);
        this.transferring.set(null);
        this.notify('SOCIETES.CENTRE_TRANSFERRED_OK');
        this.reload();
      },
      error: (err) => this.fail(err),
    });
  }

  private reload(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.notFound.set(true);
      this.loading.set(false);
      return;
    }
    this.api.get(id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (s) => this.apply(s),
      error: () => {
        this.notFound.set(true);
        this.loading.set(false);
      },
    });
  }

  private apply(s: Societe): void {
    this.societe.set(s);
    this.societeModel.set(societeToForm(s));
    this.loading.set(false);
    this.refreshLogo(s);
  }

  private refreshLogo(s: Societe): void {
    this.revokeLogo();
    if (!s.hasLogo) return;
    this.api.getLogo(s.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (blob) => this.logoUrl.set(URL.createObjectURL(blob)),
      error: () => this.logoUrl.set(null),
    });
  }

  private revokeLogo(): void {
    const url = this.logoUrl();
    if (url) URL.revokeObjectURL(url);
    this.logoUrl.set(null);
  }

  // ───────────────────────────── Utilitaires ─────────────────────────────

  private run(call: ReturnType<SocieteApiService['update']>, successKey: string, after?: () => void): void {
    this.saving.set(true);
    call.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (s) => {
        this.saving.set(false);
        this.apply(s);
        after?.();
        this.notify(successKey);
      },
      error: (err) => this.fail(err),
    });
  }

  private fail(err: { error?: { detail?: string } } | null): void {
    this.saving.set(false);
    this.snack.open(err?.error?.detail ?? this.translate.instant('SOCIETES.ACTION_ERROR'),
      this.translate.instant('COMMON.OK'), {duration: 7000});
  }

  private notify(key: string): void {
    this.snack.open(this.translate.instant(key), this.translate.instant('COMMON.OK'), {duration: 3000});
  }
}
