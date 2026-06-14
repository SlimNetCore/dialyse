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
import {AuthStore} from '../../core/state/auth.store';
import {Fournisseur, StockApiService} from '../../core/api/stock-api.service';

@Component({
  selector: 'app-fournisseurs',
  standalone: true,
  imports: [
    CommonModule, MatCardModule, MatFormFieldModule,
    MatInputModule, MatButtonModule, MatIconModule, MatTableModule,
    FormRoot, FormField,
  ],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    <section class="app-page">
      <div class="app-hero-card">
        <span class="app-eyebrow">Stock · Référentiel</span>
        <h1 class="app-section-title">Fournisseurs</h1>
      </div>

      <mat-card>
        <mat-card-header>
          <mat-card-title>Nouveau fournisseur</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <form [formRoot]="supplierForm" class="form-grid" (submit)="save(); $event.preventDefault()">
            <mat-form-field appearance="outline">
              <mat-label>Code</mat-label>
              <input matInput [formField]="supplierForm.code"/>
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Raison sociale</mat-label>
              <input matInput [formField]="supplierForm.raisonSociale"/>
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Contact</mat-label>
              <input matInput [formField]="supplierForm.contact"/>
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Téléphone</mat-label>
              <input matInput [formField]="supplierForm.telephone"/>
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Email</mat-label>
              <input matInput [formField]="supplierForm.email" type="email"/>
            </mat-form-field>
            <div class="form-actions">
              <button mat-flat-button color="primary" type="submit" [disabled]="!canSave()">
                <mat-icon>save</mat-icon>
                Enregistrer
              </button>
            </div>
          </form>
        </mat-card-content>
      </mat-card>

      <mat-card>
        <mat-card-header>
          <mat-card-title>Liste</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <table mat-table [dataSource]="items()" class="full-width">
            <ng-container matColumnDef="code">
              <th mat-header-cell *matHeaderCellDef>Code</th>
              <td mat-cell *matCellDef="let f">{{ f.code }}</td>
            </ng-container>
            <ng-container matColumnDef="raisonSociale">
              <th mat-header-cell *matHeaderCellDef>Raison sociale</th>
              <td mat-cell *matCellDef="let f">{{ f.raisonSociale }}</td>
            </ng-container>
            <ng-container matColumnDef="contact">
              <th mat-header-cell *matHeaderCellDef>Contact</th>
              <td mat-cell *matCellDef="let f">{{ f.contact }}</td>
            </ng-container>
            <ng-container matColumnDef="telephone">
              <th mat-header-cell *matHeaderCellDef>Téléphone</th>
              <td mat-cell *matCellDef="let f">{{ f.telephone }}</td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="cols"></tr>
            <tr mat-row *matRowDef="let row; columns: cols" [attr.data-row-id]="row.code || ''"></tr>
          </table>
        </mat-card-content>
      </mat-card>
    </section>
  `,
  styles: [`
    .form-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 12px; }
    .form-actions { display: flex; align-items: center; }
    .full-width { width: 100%; }
    mat-card { margin-top: 16px; }
  `],
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
          this.snack.open('Fournisseur enregistré', 'OK', {duration: 2500});
          this.formModel.set(this.createInitialForm());
          this.reload();
        },
        complete: () => this.saving.set(false),
        error: () => {
          this.saving.set(false);
          this.snack.open('Erreur lors de l\'enregistrement', 'Fermer', {duration: 4000});
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


