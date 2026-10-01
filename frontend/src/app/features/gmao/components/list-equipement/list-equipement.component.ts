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
import {Equipement} from '../../../../core/api/gmao-api.service';
import {ConfirmDialogComponent} from '../../../../shared/confirm-dialog.component';
import {GmaoEquipementsStore} from '../../state/gmao-equipements.store';
import {statutEquipementTone, STATUTS_EQUIPEMENT} from '../../gmao-options.util';

/**
 * Liste paginée des équipements GMAO (AGENTS.md §9 — jamais de chargement non paginé).
 */
@Component({
  selector: 'app-gmao-list-equipement',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule, RouterLink, MatTableModule, MatPaginatorModule, MatButtonModule, MatIconModule,
    MatFormFieldModule, MatSelectModule, MatTooltipModule, MatProgressBarModule, TranslateModule,
  ],
  templateUrl: './list-equipement.component.html',
  styleUrls: ['./list-equipement.component.css', '../../gmao-shared.css'],
})
export class GmaoListEquipementComponent {
  protected readonly store = inject(GmaoEquipementsStore);
  protected readonly columns = ['code', 'designation', 'type', 'statut', 'localisation', 'actions'];
  protected readonly statuts = STATUTS_EQUIPEMENT;
  protected readonly statutEquipementTone = statutEquipementTone;
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

  markOutOfService(row: Equipement): void {
    const ref = this.dialog.open(ConfirmDialogComponent, {
      width: 'min(96vw, 460px)',
      data: {
        title: this.translate.instant('GMAO.EQUIPEMENTS.CONFIRM_OUT_OF_SERVICE_TITLE'),
        message: this.translate.instant('GMAO.EQUIPEMENTS.CONFIRM_OUT_OF_SERVICE_MESSAGE', {code: row.code}),
        confirmLabel: this.translate.instant('COMMON.CONFIRM'),
        cancelLabel: this.translate.instant('COMMON.CANCEL'),
        color: 'warn',
        icon: 'report',
      },
    });
    ref.afterClosed().subscribe((confirmed) => {
      if (!confirmed) return;
      const raison = this.translate.instant('GMAO.EQUIPEMENTS.DEFAULT_OUT_OF_SERVICE_REASON');
      this.store.markOutOfService({id: row.id, raison});
    });
  }

  reactivate(row: Equipement): void {
    this.store.reactivate(row.id);
  }
}
