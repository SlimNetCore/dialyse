import {ChangeDetectionStrategy, Component, inject, OnInit, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {ActivatedRoute, Router, RouterLink} from '@angular/router';
import {MatCardModule} from '@angular/material/card';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatSnackBar, MatSnackBarModule} from '@angular/material/snack-bar';
import {AdminApiService} from '../../core/api/admin-api.service';

@Component({
  selector: 'app-role-form',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterLink,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatSnackBarModule,
  ],
  template: `
    <mat-card class="form-card">
      <h2>
        <mat-icon>{{ isEdit() ? 'edit' : 'add' }}</mat-icon>
        {{ isEdit() ? 'Modifier' : 'Créer' }} un rôle
      </h2>

      <div class="grid">
        <mat-form-field appearance="outline"
        >
          <mat-label>Code
          </mat-label
          >
          <input matInput [(ngModel)]="form.code" [disabled]="isEdit()"/>
          <mat-icon matPrefix
          >code
          </mat-icon
          >
        </mat-form-field
        >
        <mat-form-field appearance="outline"
        >
          <mat-label>Nom</mat-label>
          <input matInput [(ngModel)]="form.name"/>
          <mat-icon matPrefix
          >label
          </mat-icon
          >
        </mat-form-field
        >
      </div>
      <mat-form-field appearance="outline" class="full"
      >
        <mat-label>Description
        </mat-label
        >
        <textarea matInput rows="3" [(ngModel)]="form.description"></textarea>
      </mat-form-field>

      <div class="actions">
        <button mat-stroked-button routerLink="/admin/roles">
          <mat-icon>arrow_back</mat-icon>
          Retour
        </button>
        <button mat-flat-button color="primary" (click)="save()">
          <mat-icon>save</mat-icon>
          Enregistrer
        </button>
      </div>
    </mat-card>
  `,
  changeDetection: ChangeDetectionStrategy.Eager,
  styles: [
    `
      .form-card {
        max-width: 600px;
        margin: 0 auto;
      }

      h2 {
        display: flex;
        align-items: center;
        gap: 8px;
        color: #1b5e20;
      }

      .grid {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 12px;
      }

      .full {
        width: 100%;
      }

      .actions {
        display: flex;
        justify-content: space-between;
        margin-top: 16px;
      }
    `,
  ],
})
export class RoleFormComponent implements OnInit {
  private readonly api = inject(AdminApiService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly snackbar = inject(MatSnackBar);

  readonly isEdit = signal(false);
  private editId = '';
  form = { code: '', name: '', description: '' };

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.isEdit.set(true);
      this.editId = id;
      this.api.getRole(id).subscribe((r) => {
        this.form.code = r.CODE;
        this.form.name = r.NAME;
        this.form.description = r.DESCRIPTION;
      });
    }
  }

  save(): void {
    if (this.isEdit()) {
      this.api.updateRole(this.editId, this.form).subscribe(() => {
        this.snackbar.open('Rôle mis à jour', 'OK', {duration: 2000});
        this.router.navigate(['/admin/roles']);
      });
    } else {
      if (!this.form.code || !this.form.name) {
        this.snackbar.open('Remplissez code et nom', 'OK', {duration: 2500});
        return;
      }
      this.api.createRole(this.form).subscribe(() => {
        this.snackbar.open('Rôle créé', 'OK', {duration: 2000});
        this.router.navigate(['/admin/roles']);
      });
    }
  }
}
