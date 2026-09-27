import {DatePipe} from '@angular/common';
import {ChangeDetectionStrategy, Component, computed, inject, OnInit, signal} from '@angular/core';
import {MatCardModule} from '@angular/material/card';
import {MatTableModule} from '@angular/material/table';
import {MatPaginatorModule, PageEvent} from '@angular/material/paginator';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatSelectModule} from '@angular/material/select';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatTooltipModule} from '@angular/material/tooltip';
import {TranslateModule} from '@ngx-translate/core';
import {compatForm} from '@angular/forms/signals/compat';
import {FormField, FormRoot} from '@angular/forms/signals';
import {AuthStore} from '../../core/state/auth.store';
import {AuditLogStore} from './state/audit-log.store';

/**
 * Journal d'audit (« qui a fait quoi ») : réservé à ADMIN (son centre) et SUPERADMIN (toute la plateforme,
 * filtrable par société). Écritures tracées automatiquement, plus la consultation du dossier médical.
 */
@Component({
  selector: 'app-audit-log',
  standalone: true,
  imports: [
    DatePipe, TranslateModule, MatCardModule, MatTableModule, MatPaginatorModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatButtonModule, MatIconModule, MatProgressBarModule, MatTooltipModule, FormRoot, FormField,
  ],
  templateUrl: './audit-log.component.html',
  styleUrl: './audit-log.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AuditLogComponent implements OnInit {
  protected readonly store = inject(AuditLogStore);
  protected readonly filterModel = signal({societeId: '', userId: '', actionCode: '', from: '', to: ''});
  protected readonly filterForm = compatForm(this.filterModel);
  protected readonly columns = computed(() =>
    this.isOwner()
      ? ['occurredAt', 'username', 'centerId', 'actionCode', 'libelle', 'statusCode', 'durationMs']
      : ['occurredAt', 'username', 'actionCode', 'libelle', 'statusCode', 'durationMs']);
  private readonly auth = inject(AuthStore);
  /** Le propriétaire de la plateforme peut filtrer par société ; un administrateur reste cantonné à son centre. */
  protected readonly isOwner = computed(() => this.auth.hasRole('SUPERADMIN'));

  ngOnInit(): void {
    void this.store.loadActionCodes();
    void this.store.load();
  }

  protected apply(): void {
    const f = this.filterModel();
    this.store.setFilters(f);
    void this.store.load();
    void this.store.loadActionCodes();
  }

  protected clear(): void {
    this.filterModel.set({societeId: '', userId: '', actionCode: '', from: '', to: ''});
    this.store.clearFilters();
    void this.store.load();
  }

  protected onPage(event: PageEvent): void {
    this.store.setPagination(event.pageIndex, event.pageSize);
    void this.store.load();
  }

  protected statusClass(status: number | null): string {
    if (status === null) return '';
    if (status >= 500) return 'crit';
    if (status >= 400) return 'warn';
    return 'ok';
  }
}
