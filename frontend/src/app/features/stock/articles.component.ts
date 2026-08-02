import {ChangeDetectionStrategy, Component, computed, inject, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {compatForm} from '@angular/forms/signals/compat';
import {FormField, FormRoot, maxLength, min, required} from '@angular/forms/signals';
import {MatCardModule} from '@angular/material/card';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatTableModule} from '@angular/material/table';
import {MatSnackBar} from '@angular/material/snack-bar';
import {MatCheckboxModule} from '@angular/material/checkbox';
import {TranslateModule} from '@ngx-translate/core';
import {TranslateService} from '@ngx-translate/core';
import {AuthStore} from '../../core/state/auth.store';
import {StockApiService} from '../../core/api/stock-api.service';
import {ReferentialApiService, RefItem} from '../../core/api/referential-api.service';

@Component({
  selector: 'app-stock-articles',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatTableModule,
    MatCheckboxModule,
    FormRoot,
    FormField,
    TranslateModule,
  ],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './articles.component.html',
  styleUrl: './articles.component.css',
})
export class ArticlesComponent {
  protected readonly cols = ['code', 'libelle', 'unite', 'lot'];
  protected readonly articles = signal<RefItem[]>([]);
  protected readonly saving = signal(false);
  protected readonly formModel = signal(this.createInitialForm());
  protected readonly articleForm = compatForm(this.formModel, (form) => {
    required(form.code);
    maxLength(form.code, 50);
    required(form.libelle);
    maxLength(form.libelle, 150);
    required(form.unite);
    maxLength(form.unite, 30);
    min(form.seuilAlerte, 0);
  });
  protected readonly canSave = computed(() => {
    const form = this.formModel();
    return !!form.code.trim() && !!form.libelle.trim() && !!form.unite.trim() && form.seuilAlerte >= 0 && !this.saving();
  });

  private readonly api = inject(StockApiService);
  private readonly refApi = inject(ReferentialApiService);
  private readonly auth = inject(AuthStore);
  private readonly snack = inject(MatSnackBar);
  private readonly translate = inject(TranslateService);

  constructor() {
    this.reload();
  }

  protected save(): void {
    const centerId = this.auth.centerId();
    const form = this.formModel();
    if (!centerId || !this.canSave()) {
      return;
    }
    this.saving.set(true);
    this.api.createArticle({
      centerId,
      code: form.code.trim(),
      libelle: form.libelle.trim(),
      unite: form.unite.trim(),
      seuilAlerte: Number(form.seuilAlerte ?? 0),
      gereParLot: !!form.gereParLot,
    }).subscribe({
      next: () => {
        this.snack.open(
          this.translate.instant('STOCK.ARTICLES.CREATED_OK'),
          this.translate.instant('COMMON.OK'),
          {duration: 2500},
        );
        this.formModel.set(this.createInitialForm());
        this.reload();
      },
      error: () => this.snack.open(
        this.translate.instant('STOCK.ARTICLES.CREATE_ERROR'),
        this.translate.instant('COMMON.RETRY'),
        {duration: 4000},
      ),
      complete: () => this.saving.set(false),
    });
  }

  private reload(): void {
    const centerId = this.auth.centerId();
    if (!centerId) {
      return;
    }
    this.refApi.getArticles(centerId).subscribe({next: items => this.articles.set(items)});
  }

  private createInitialForm() {
    return {
      code: '',
      libelle: '',
      unite: '',
      seuilAlerte: 0,
      gereParLot: true,
    };
  }
}

