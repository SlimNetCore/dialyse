import {ChangeDetectionStrategy, Component, computed, effect, inject, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {compatForm} from '@angular/forms/signals/compat';
import {FormField, FormRoot, required} from '@angular/forms/signals';
import {MatCardModule} from '@angular/material/card';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatCheckboxModule} from '@angular/material/checkbox';
import {MatTableModule} from '@angular/material/table';
import {MatPaginatorModule, PageEvent} from '@angular/material/paginator';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatTooltipModule} from '@angular/material/tooltip';
import {MatDialog} from '@angular/material/dialog';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {GroupeArticle} from '../../../core/api/groupes-articles-api.service';
import {ArticleStock, StockApiService} from '../../../core/api/stock-api.service';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {ConfirmDialogComponent} from '../../../shared/confirm-dialog.component';
import {GroupesArticlesStore} from './groupes-articles.store';
import {filterArticles} from './groupes-articles.util';

type GroupeFormModel = { nom: string; description: string };

function emptyForm(): GroupeFormModel {
  return {nom: '', description: ''};
}

/**
 * Administration des groupes d'articles du centre (ex. « KIT CNAS ») : un groupe nommé regroupe des articles
 * dont la valorisation du stock est suivie sur le tableau de bord de la direction.
 */
@Component({
  selector: 'app-groupes-articles',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule, MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule,
    MatCheckboxModule, MatTableModule, MatPaginatorModule, MatProgressBarModule, MatTooltipModule,
    FormRoot, FormField, TranslateModule,
  ],
  templateUrl: './groupes-articles.component.html',
  styleUrl: './groupes-articles.component.css',
})
export class GroupesArticlesComponent {
  protected readonly store = inject(GroupesArticlesStore);
  protected readonly columns = ['nom', 'nbArticles', 'actions'];
  protected readonly editingId = signal<string | null>(null);
  protected readonly articles = signal<ArticleStock[]>([]);
  protected readonly articlesError = signal(false);
  protected readonly search = signal('');
  protected readonly selectedIds = signal<ReadonlySet<string>>(new Set());
  protected readonly filteredArticles = computed(() => filterArticles(this.articles(), this.search()));
  protected readonly formModel = signal(emptyForm());
  protected readonly groupeForm = compatForm(this.formModel, (form) => {
    required(form.nom);
  });
  protected readonly canSave = computed(() =>
    !!this.formModel().nom.trim() && this.selectedIds().size > 0 && !this.store.saving());
  private readonly stockApi = inject(StockApiService);
  private readonly shell = inject(AppShellStore);
  private readonly dialog = inject(MatDialog);
  private readonly translate = inject(TranslateService);

  constructor() {
    this.store.loadPage({page: 0, size: this.store.pageSize()});
    this.loadArticles();

    effect(() => {
      const msg = this.store.successMessage();
      if (!msg) return;
      this.cancelEdit();
      this.store.loadPage({page: this.store.pageIndex(), size: this.store.pageSize()});
    });
  }

  protected isSelected(id: string): boolean {
    return this.selectedIds().has(id);
  }

  protected toggle(id: string, checked: boolean): void {
    const next = new Set(this.selectedIds());
    if (checked) next.add(id); else next.delete(id);
    this.selectedIds.set(next);
  }

  protected selectAllFiltered(): void {
    const next = new Set(this.selectedIds());
    this.filteredArticles().forEach((a) => next.add(a.id));
    this.selectedIds.set(next);
  }

  protected clearSelection(): void {
    this.selectedIds.set(new Set());
  }

  protected save(): void {
    if (!this.canSave()) return;
    const form = this.formModel();
    const payload = {
      nom: form.nom.trim(),
      description: form.description.trim() || null,
      articleIds: [...this.selectedIds()],
    };
    const id = this.editingId();
    if (id) {
      this.store.update({id, payload});
    } else {
      this.store.create(payload);
    }
  }

  protected edit(row: GroupeArticle): void {
    this.store.clearMessages();
    this.editingId.set(row.id);
    this.formModel.set({nom: row.nom, description: row.description ?? ''});
    this.selectedIds.set(new Set(row.articleIds));
  }

  protected cancelEdit(): void {
    this.editingId.set(null);
    this.formModel.set(emptyForm());
    this.selectedIds.set(new Set());
    this.search.set('');
  }

  protected remove(row: GroupeArticle): void {
    const ref = this.dialog.open(ConfirmDialogComponent, {
      width: 'min(96vw, 460px)',
      data: {
        title: this.translate.instant('GROUPES_ARTICLES.CONFIRM_DELETE_TITLE'),
        message: this.translate.instant('GROUPES_ARTICLES.CONFIRM_DELETE_MESSAGE', {nom: row.nom}),
        confirmLabel: this.translate.instant('COMMON.CONFIRM'),
        cancelLabel: this.translate.instant('COMMON.CANCEL'),
        color: 'warn',
        icon: 'delete',
      },
    });
    ref.afterClosed().subscribe((confirmed) => {
      if (confirmed) this.store.remove(row.id);
    });
  }

  protected onPageChange(event: PageEvent): void {
    this.store.setPagination(event.pageIndex, event.pageSize);
    this.store.loadPage({page: event.pageIndex, size: event.pageSize});
  }

  private loadArticles(): void {
    const centerId = this.shell.currentCenterId();
    if (!centerId) return;
    this.stockApi.listArticles(centerId).subscribe({
      next: (rows) => this.articles.set(rows.filter((a) => a.active)),
      error: () => this.articlesError.set(true),
    });
  }
}
