import {DatePipe, DecimalPipe} from '@angular/common';
import {ChangeDetectionStrategy, Component, inject, OnInit, signal} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {MatButtonToggleModule} from '@angular/material/button-toggle';
import {MatCardModule} from '@angular/material/card';
import {MatDialog} from '@angular/material/dialog';
import {MatIconModule} from '@angular/material/icon';
import {MatPaginatorModule, PageEvent} from '@angular/material/paginator';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatTableModule} from '@angular/material/table';
import {MatTooltipModule} from '@angular/material/tooltip';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {firstValueFrom} from 'rxjs';
import {TriRequetes} from '../../core/api/supervision-api.service';
import {ConfirmDialogComponent} from '../../shared/confirm-dialog.component';
import {formatDuree, formatPart} from './performance-base.util';
import {PerformanceBaseStore} from './state/performance-base.store';

/**
 * Performance de la base (propriétaire / SUPERADMIN) : les requêtes qui coûtent le plus, relevées par
 * pg_stat_statements, pour décider quoi améliorer (index, requête, cache). Aucun texte de requête ne contient de
 * donnée de patient : les valeurs sont remplacées par $1, $2…
 */
@Component({
  selector: 'app-performance-base',
  standalone: true,
  imports: [
    DatePipe, DecimalPipe, TranslateModule, MatCardModule, MatTableModule, MatPaginatorModule, MatButtonModule,
    MatButtonToggleModule, MatIconModule, MatProgressBarModule, MatTooltipModule,
  ],
  templateUrl: './performance-base.component.html',
  styleUrl: './performance-base.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PerformanceBaseComponent implements OnInit {
  protected readonly store = inject(PerformanceBaseStore);
  protected readonly columns = ['niveau', 'requete', 'appels', 'tempsTotal', 'part', 'tempsMoyen', 'tempsMax', 'lignes'];
  protected readonly tris: TriRequetes[] = ['TEMPS_TOTAL', 'TEMPS_MOYEN', 'APPELS'];
  protected readonly duree = formatDuree;
  protected readonly part = formatPart;
  /** Requêtes dont le texte complet est déplié, et dernière requête copiée (retour visuel de quelques secondes). */
  protected readonly depliees = signal<ReadonlySet<string>>(new Set());
  protected readonly copiee = signal<string | null>(null);
  private readonly dialog = inject(MatDialog);
  private readonly translate = inject(TranslateService);

  ngOnInit(): void {
    void this.store.load();
  }

  protected changerTri(tri: TriRequetes): void {
    this.store.setTri(tri);
    void this.store.load();
  }

  protected onPage(event: PageEvent): void {
    this.store.setPagination(event.pageIndex, event.pageSize);
    void this.store.load();
  }

  protected basculer(id: string): void {
    const suivantes = new Set(this.depliees());
    if (!suivantes.delete(id)) suivantes.add(id);
    this.depliees.set(suivantes);
  }

  protected async copier(id: string, requete: string): Promise<void> {
    try {
      await navigator.clipboard.writeText(requete);
      this.copiee.set(id);
      setTimeout(() => this.copiee.set(null), 2000);
    } catch {
      this.copiee.set(null);
    }
  }

  protected async reinitialiser(): Promise<void> {
    const ref = this.dialog.open(ConfirmDialogComponent, {
      width: 'min(96vw, 460px)',
      data: {
        title: this.translate.instant('SUPERVISION.RESET_TITLE'),
        message: this.translate.instant('SUPERVISION.RESET_MESSAGE'),
        confirmLabel: this.translate.instant('SUPERVISION.RESET_CONFIRM'),
        cancelLabel: this.translate.instant('PATIENT_FORM.BTN_CANCEL'),
        color: 'warn',
        icon: 'restart_alt',
      },
    });
    if (await firstValueFrom(ref.afterClosed())) {
      await this.store.reinitialiser();
    }
  }
}
