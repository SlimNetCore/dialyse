import {ChangeDetectionStrategy, Component, effect, inject} from '@angular/core';
import {CommonModule} from '@angular/common';
import {RouterLink} from '@angular/router';
import {MatTableModule} from '@angular/material/table';
import {MatPaginatorModule, PageEvent} from '@angular/material/paginator';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatSelectModule} from '@angular/material/select';
import {MatTooltipModule} from '@angular/material/tooltip';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatDialog} from '@angular/material/dialog';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {Intervention} from '../../../../core/api/gmao-api.service';
import {ConfirmDialogComponent} from '../../../../shared/confirm-dialog.component';
import {GmaoInterventionsStore} from '../../state/gmao-interventions.store';
import {statutInterventionTone, STATUTS_INTERVENTION} from '../../gmao-options.util';
import {GmaoLigneCoutDialogComponent} from '../ligne-cout-dialog/ligne-cout-dialog.component';
import {
  FinishInterventionResult,
  GmaoFinishInterventionDialogComponent,
} from '../finish-intervention-dialog/finish-intervention-dialog.component';

/**
 * Liste paginée des interventions GMAO (AGENTS.md §9 — jamais de chargement non paginé).
 */
@Component({
  selector: 'app-gmao-list-intervention',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule, RouterLink, MatTableModule, MatPaginatorModule, MatButtonModule, MatIconModule,
    MatFormFieldModule, MatSelectModule, MatTooltipModule, MatProgressBarModule, TranslateModule,
  ],
  templateUrl: './list-intervention.component.html',
  styleUrls: ['./list-intervention.component.css', '../../gmao-shared.css'],
})
export class GmaoListInterventionComponent {
  protected readonly store = inject(GmaoInterventionsStore);
  protected readonly columns = ['type', 'description', 'dateDebut', 'statut', 'coutTotal', 'actions'];
  protected readonly statuts = STATUTS_INTERVENTION;
  protected readonly statutInterventionTone = statutInterventionTone;
  private readonly dialog = inject(MatDialog);
  private readonly translate = inject(TranslateService);

  constructor() {
    this.store.loadPage({page: 0, size: this.store.pageSize()});

    effect(() => {
      const msg = this.store.successMessage();
      if (!msg) return;
      this.store.loadPage({page: this.store.pageIndex(), size: this.store.pageSize()});
    });
  }

  onStatutFilterChange(statut: string | null): void {
    this.store.setStatutFilter(statut);
    this.store.loadPage({page: 0, size: this.store.pageSize()});
  }

  onPageChange(event: PageEvent): void {
    this.store.setPagination(event.pageIndex, event.pageSize);
    this.store.loadPage({page: event.pageIndex, size: event.pageSize});
  }

  start(row: Intervention): void {
    this.store.startIntervention(row.id);
  }

  finish(row: Intervention): void {
    const ref = this.dialog.open(GmaoFinishInterventionDialogComponent, {width: 'min(96vw, 480px)'});
    ref.afterClosed().subscribe((result: FinishInterventionResult | null) => {
      if (!result) return;
      this.store.finishIntervention({id: row.id, ...result});
    });
  }

  cancel(row: Intervention): void {
    const ref = this.dialog.open(ConfirmDialogComponent, {
      width: 'min(96vw, 460px)',
      data: {
        title: this.translate.instant('GMAO.INTERVENTIONS.CONFIRM_CANCEL_TITLE'),
        message: this.translate.instant('GMAO.INTERVENTIONS.CONFIRM_CANCEL_MESSAGE'),
        confirmLabel: this.translate.instant('COMMON.CONFIRM'),
        cancelLabel: this.translate.instant('COMMON.CANCEL'),
        color: 'warn',
        icon: 'cancel',
      },
    });
    ref.afterClosed().subscribe((confirmed) => {
      if (!confirmed) return;
      const raison = this.translate.instant('GMAO.INTERVENTIONS.DEFAULT_CANCEL_REASON');
      this.store.cancelIntervention({id: row.id, raison});
    });
  }

  addLigneCout(row: Intervention): void {
    const ref = this.dialog.open(GmaoLigneCoutDialogComponent, {width: 'min(96vw, 480px)'});
    ref.afterClosed().subscribe((payload) => {
      if (!payload) return;
      this.store.ajouterLigneCout({id: row.id, payload});
    });
  }
}
