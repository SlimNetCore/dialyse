import {ChangeDetectionStrategy, Component, computed, inject, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {ActivatedRoute, Router, RouterLink} from '@angular/router';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatDialog} from '@angular/material/dialog';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {EquipementFiche, GmaoApiService} from '../../../../core/api/gmao-api.service';
import {ConfirmDialogComponent} from '../../../../shared/confirm-dialog.component';
import {ROLE_GMAO_REFORME, statutEquipementTone} from '../../gmao-options.util';
import {AuthStore} from '../../../../core/state/auth.store';

/**
 * Fiche détaillée d'un équipement GMAO — aide à la décision : coût cumulé de maintenance,
 * temps d'indisponibilité, dernière intervention, prochaine maintenance planifiée.
 */
@Component({
  selector: 'app-gmao-fiche-equipement',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule, RouterLink, MatButtonModule, MatIconModule, MatProgressBarModule, TranslateModule,
  ],
  templateUrl: './fiche-equipement.component.html',
  styleUrls: ['./fiche-equipement.component.css', '../../gmao-shared.css'],
})
export class GmaoFicheEquipementComponent {
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly fiche = signal<EquipementFiche | null>(null);
  protected readonly statutEquipementTone = statutEquipementTone;
  private readonly api = inject(GmaoApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly auth = inject(AuthStore);
  /** Seule la personne habilitée (rôle GMAO_REFORME) décide de la réforme ; les autres ne voient pas l'action. */
  protected readonly canReform = computed(() => this.auth.hasRole(ROLE_GMAO_REFORME));
  private readonly translate = inject(TranslateService);
  private readonly equipementId = this.route.snapshot.paramMap.get('id')!;

  constructor() {
    this.reload();
  }

  protected reload(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.getEquipementFiche(this.equipementId).subscribe({
      next: (fiche) => {
        this.fiche.set(fiche);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('GMAO.EQUIPEMENTS.LOAD_ERROR');
        this.loading.set(false);
      },
    });
  }

  protected markOutOfService(): void {
    const ref = this.dialog.open(ConfirmDialogComponent, {
      width: 'min(96vw, 460px)',
      data: {
        title: this.translate.instant('GMAO.EQUIPEMENTS.CONFIRM_OUT_OF_SERVICE_TITLE'),
        message: this.translate.instant('GMAO.EQUIPEMENTS.CONFIRM_OUT_OF_SERVICE_MESSAGE', {code: this.fiche()?.equipement.code}),
        confirmLabel: this.translate.instant('COMMON.CONFIRM'),
        cancelLabel: this.translate.instant('COMMON.CANCEL'),
        color: 'warn',
        icon: 'report',
      },
    });
    ref.afterClosed().subscribe((confirmed) => {
      if (!confirmed) return;
      const raison = this.translate.instant('GMAO.EQUIPEMENTS.DEFAULT_OUT_OF_SERVICE_REASON');
      this.api.markEquipementOutOfService(this.equipementId, raison).subscribe(() => this.reload());
    });
  }

  protected reactivate(): void {
    this.api.reactivateEquipement(this.equipementId).subscribe(() => this.reload());
  }

  protected reformer(): void {
    const ref = this.dialog.open(ConfirmDialogComponent, {
      width: 'min(96vw, 460px)',
      data: {
        title: this.translate.instant('GMAO.EQUIPEMENTS.CONFIRM_REFORME_TITLE'),
        message: this.translate.instant('GMAO.EQUIPEMENTS.CONFIRM_REFORME_MESSAGE'),
        confirmLabel: this.translate.instant('COMMON.CONFIRM'),
        cancelLabel: this.translate.instant('COMMON.CANCEL'),
        color: 'warn',
        icon: 'delete_forever',
      },
    });
    ref.afterClosed().subscribe((confirmed) => {
      if (!confirmed) return;
      const motif = this.translate.instant('GMAO.EQUIPEMENTS.DEFAULT_REFORME_MOTIF');
      this.api.reformerEquipement(this.equipementId, motif).subscribe(() => this.reload());
    });
  }

  protected goToEdit(): void {
    this.router.navigate(['/gmao/equipements', this.equipementId, 'edit']);
  }
}
