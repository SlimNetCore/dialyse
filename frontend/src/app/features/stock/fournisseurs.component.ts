import {ChangeDetectionStrategy, Component, computed, inject, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {compatForm} from '@angular/forms/signals/compat';
import {FormField, FormRoot, required} from '@angular/forms/signals';
import {MatCardModule} from '@angular/material/card';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatTableModule} from '@angular/material/table';
import {MatSnackBar} from '@angular/material/snack-bar';
import {TranslateModule} from '@ngx-translate/core';
import {TranslateService} from '@ngx-translate/core';
import {AuthStore} from '../../core/state/auth.store';
import {Fournisseur, StockApiService} from '../../core/api/stock-api.service';

@Component({
  selector: 'app-fournisseurs',
  standalone: true,
  imports: [
    CommonModule, MatCardModule, MatFormFieldModule,
    MatInputModule, MatButtonModule, MatIconModule, MatTableModule,
    FormRoot, FormField, TranslateModule,
  ],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './fournisseurs.component.html',
  styleUrl: './fournisseurs.component.css',
})
export class FournisseursComponent {
  protected readonly cols = ['code', 'raisonSociale', 'contact', 'telephone'];
  protected readonly items = signal<Fournisseur[]>([]);
  protected readonly saving = signal(false);
  protected readonly formModel = signal(this.createInitialForm());
  protected readonly supplierForm = compatForm(this.formModel, (form) => {
    required(form.raisonSociale);
  });
  protected readonly canSave = computed(() => !!this.formModel().raisonSociale.trim() && !this.saving());
  private readonly api = inject(StockApiService);
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
    this.api.createFournisseur({
      centerId,
      raisonSociale: form.raisonSociale.trim(),
      code: form.code.trim() || undefined,
      contact: form.contact.trim() || undefined,
      telephone: form.telephone.trim() || undefined,
      email: form.email.trim() || undefined,
    })
      .subscribe({
        next: () => {
          this.snack.open(
            this.translate.instant('STOCK.FOURNISSEURS.SAVED_OK'),
            this.translate.instant('COMMON.OK'),
            {duration: 2500},
          );
          this.formModel.set(this.createInitialForm());
          this.reload();
        },
        complete: () => this.saving.set(false),
        error: () => {
          this.saving.set(false);
          this.snack.open(
            this.translate.instant('STOCK.FOURNISSEURS.SAVE_ERROR'),
            this.translate.instant('COMMON.RETRY'),
            {duration: 4000},
          );
        },
      });
  }

  private reload(): void {
    const centerId = this.auth.centerId();
    if (!centerId) {
      return;
    }
    this.api.listFournisseurs(centerId).subscribe({next: (f) => this.items.set(f)});
  }

  private createInitialForm() {
    return {
      code: '',
      raisonSociale: '',
      contact: '',
      telephone: '',
      email: '',
    };
  }
}


