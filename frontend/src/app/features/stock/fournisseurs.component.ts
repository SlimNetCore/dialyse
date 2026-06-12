import {ChangeDetectionStrategy, Component, inject, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormBuilder, ReactiveFormsModule, Validators} from '@angular/forms';
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
    CommonModule, ReactiveFormsModule, MatCardModule, MatFormFieldModule,
    MatInputModule, MatButtonModule, MatIconModule, MatTableModule,
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
          <form [formGroup]="form" class="form-grid" (ngSubmit)="save()">
            <mat-form-field appearance="outline">
              <mat-label>Code</mat-label>
              <input matInput formControlName="code"/>
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Raison sociale</mat-label>
              <input matInput formControlName="raisonSociale" required/>
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Contact</mat-label>
              <input matInput formControlName="contact"/>
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Téléphone</mat-label>
              <input matInput formControlName="telephone"/>
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Email</mat-label>
              <input matInput formControlName="email" type="email"/>
            </mat-form-field>
            <div class="form-actions">
              <button mat-flat-button color="primary" type="submit" [disabled]="form.invalid || saving()">
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
            <tr mat-row *matRowDef="let row; columns: cols"></tr>
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
  private readonly api = inject(StockApiService);
  private readonly auth = inject(AuthStore);
  private readonly fb = inject(FormBuilder);
  protected readonly form = this.fb.group({
    code: [''],
    raisonSociale: ['', Validators.required],
    contact: [''],
    telephone: [''],
    email: [''],
  });
  private readonly snack = inject(MatSnackBar);

  constructor() {
    this.reload();
  }

  protected save(): void {
    const centerId = this.auth.centerId();
    if (!centerId || this.form.invalid) {
      return;
    }
    this.saving.set(true);
    const v = this.form.value;
    this.api.createFournisseur({
      centerId,
      raisonSociale: v.raisonSociale!,
      code: v.code ?? undefined,
      contact: v.contact ?? undefined,
      telephone: v.telephone ?? undefined,
      email: v.email ?? undefined,
    })
      .subscribe({
        next: () => {
          this.snack.open('Fournisseur enregistré', 'OK', {duration: 2500});
          this.form.reset();
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
}


