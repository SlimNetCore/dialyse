import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute, RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { AdminApiService, AppRole } from '../../core/api/admin-api.service';

@Component({
  selector: 'app-user-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule, MatCheckboxModule, MatSelectModule, MatSnackBarModule],
  template: `
    <mat-card class="form-card">
      <h2><mat-icon>{{ isEdit() ? 'edit' : 'person_add' }}</mat-icon> {{ isEdit() ? 'Modifier' : 'Créer' }} un utilisateur</h2>

      <div class="grid">
        <mat-form-field appearance="outline"><mat-label>Nom d'utilisateur</mat-label><input matInput [(ngModel)]="form.username" [disabled]="isEdit()" /><mat-icon matPrefix>person</mat-icon></mat-form-field>
        <mat-form-field appearance="outline"><mat-label>Mot de passe</mat-label><input matInput type="password" [(ngModel)]="form.password" /><mat-icon matPrefix>lock</mat-icon></mat-form-field>
        <mat-form-field appearance="outline"><mat-label>Nom complet</mat-label><input matInput [(ngModel)]="form.fullName" /><mat-icon matPrefix>badge</mat-icon></mat-form-field>
        <mat-form-field appearance="outline"><mat-label>Email</mat-label><input matInput type="email" [(ngModel)]="form.email" /><mat-icon matPrefix>email</mat-icon></mat-form-field>
      </div>

      <mat-checkbox [(ngModel)]="form.active" color="primary">Actif</mat-checkbox>

      <mat-form-field appearance="outline" class="full">
        <mat-label>Rôles</mat-label>
        <mat-select [(ngModel)]="form.roleIds" multiple>
          @for (r of roles(); track r.ID) {
            <mat-option [value]="r.ID">{{ r.NAME }} ({{ r.CODE }})</mat-option>
          }
        </mat-select>
        <mat-icon matPrefix>admin_panel_settings</mat-icon>
      </mat-form-field>

      <mat-form-field appearance="outline" class="full">
        <mat-label>Centres</mat-label>
        <mat-select [(ngModel)]="form.centerIds" multiple>
          @for (c of centers(); track c.id) {
            <mat-option [value]="c.id">{{ c.name }}</mat-option>
          }
        </mat-select>
        <mat-icon matPrefix>business</mat-icon>
      </mat-form-field>

      <div class="actions">
        <button mat-stroked-button routerLink="/admin/users"><mat-icon>arrow_back</mat-icon> Retour</button>
        <button mat-flat-button color="primary" (click)="save()"><mat-icon>save</mat-icon> Enregistrer</button>
      </div>
    </mat-card>
  `,
  styles: [`
    .form-card { max-width: 800px; margin: 0 auto; }
    h2 { display: flex; align-items: center; gap: 8px; color: #1b5e20; }
    .grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
    .full { width: 100%; }
    .actions { display: flex; justify-content: space-between; margin-top: 16px; }
  `]
})
export class UserFormComponent implements OnInit {
  private readonly api = inject(AdminApiService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly snackbar = inject(MatSnackBar);

  readonly isEdit = signal(false);
  readonly roles = signal<AppRole[]>([]);
  readonly centers = signal<any[]>([]);
  private editId = '';

  form = { username: '', password: '', fullName: '', email: '', active: true, roleIds: [] as string[], centerIds: [] as string[] };

  ngOnInit(): void {
    this.api.listRoles().subscribe(r => this.roles.set(r));
    this.api.listCenters().subscribe(c => this.centers.set(c));

    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.isEdit.set(true);
      this.editId = id;
      this.api.getUser(id).subscribe(u => {
        this.form.username = u.USERNAME;
        this.form.fullName = u.FULL_NAME;
        this.form.email = u.EMAIL;
        this.form.active = u.ACTIVE;
        this.form.roleIds = u.roles.map(r => r.ID);
        this.form.centerIds = u.centers.map(c => c.ID);
      });
    }
  }

  save(): void {
    if (this.isEdit()) {
      this.api.updateUser(this.editId, {
        email: this.form.email, fullName: this.form.fullName, active: this.form.active,
        password: this.form.password || undefined, roleIds: this.form.roleIds, centerIds: this.form.centerIds
      }).subscribe(() => { this.snackbar.open('Utilisateur mis à jour', 'OK', { duration: 2000 }); this.router.navigate(['/admin/users']); });
    } else {
      if (!this.form.username || !this.form.password) { this.snackbar.open('Remplissez les champs obligatoires', 'OK', { duration: 2500 }); return; }
      this.api.createUser({
        username: this.form.username, password: this.form.password, email: this.form.email,
        fullName: this.form.fullName, active: this.form.active, roleIds: this.form.roleIds, centerIds: this.form.centerIds
      }).subscribe(() => { this.snackbar.open('Utilisateur créé', 'OK', { duration: 2000 }); this.router.navigate(['/admin/users']); });
    }
  }
}

