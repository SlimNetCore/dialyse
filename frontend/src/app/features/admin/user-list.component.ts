import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { TranslateModule } from '@ngx-translate/core';
import { AdminApiService, AppUser } from '../../core/api/admin-api.service';

@Component({
  selector: 'app-user-list',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule, MatTableModule, MatButtonModule, MatIconModule, MatChipsModule, MatCardModule, MatFormFieldModule, MatInputModule, MatSnackBarModule, TranslateModule],
  template: `
    <mat-card>
      <div class="header">
        <h2><mat-icon>people</mat-icon> Gestion des utilisateurs</h2>
        <button mat-flat-button color="primary" routerLink="/admin/users/new"><mat-icon>person_add</mat-icon> Nouvel utilisateur</button>
      </div>

      <mat-form-field appearance="outline" class="search">
        <mat-label>Rechercher</mat-label>
        <input matInput [(ngModel)]="searchTerm" (ngModelChange)="filter()" />
        <mat-icon matPrefix>search</mat-icon>
      </mat-form-field>

      <table mat-table [dataSource]="filtered()" class="full-width">
        <ng-container matColumnDef="username"><th mat-header-cell *matHeaderCellDef>Nom d'utilisateur</th><td mat-cell *matCellDef="let u">{{ u.USERNAME }}</td></ng-container>
        <ng-container matColumnDef="fullName"><th mat-header-cell *matHeaderCellDef>Nom complet</th><td mat-cell *matCellDef="let u">{{ u.FULL_NAME }}</td></ng-container>
        <ng-container matColumnDef="email"><th mat-header-cell *matHeaderCellDef>Email</th><td mat-cell *matCellDef="let u">{{ u.EMAIL }}</td></ng-container>
        <ng-container matColumnDef="roles">
          <th mat-header-cell *matHeaderCellDef>Rôles</th>
          <td mat-cell *matCellDef="let u">
            <mat-chip-set>
              @for (r of u.roles; track r.ID) { <mat-chip>{{ r.NAME }}</mat-chip> }
            </mat-chip-set>
          </td>
        </ng-container>
        <ng-container matColumnDef="centers">
          <th mat-header-cell *matHeaderCellDef>Centres</th>
          <td mat-cell *matCellDef="let u">
            <mat-chip-set>
              @for (c of u.centers; track c.ID) { <mat-chip>{{ c.NAME }}</mat-chip> }
            </mat-chip-set>
          </td>
        </ng-container>
        <ng-container matColumnDef="active"><th mat-header-cell *matHeaderCellDef>Actif</th><td mat-cell *matCellDef="let u"><mat-icon [style.color]="u.ACTIVE ? '#1b5e20' : '#c62828'">{{ u.ACTIVE ? 'check_circle' : 'cancel' }}</mat-icon></td></ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef>Actions</th>
          <td mat-cell *matCellDef="let u">
            <button mat-icon-button color="primary" [routerLink]="['/admin/users', u.ID, 'edit']"><mat-icon>edit</mat-icon></button>
            <button mat-icon-button color="warn" (click)="deleteUser(u)"><mat-icon>delete</mat-icon></button>
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="cols"></tr>
        <tr mat-row *matRowDef="let row; columns: cols;"></tr>
      </table>
    </mat-card>
  `,
  styles: [`
    .header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
    .header h2 { display: flex; align-items: center; gap: 8px; color: #1b5e20; margin: 0; }
    .search { width: 100%; margin-bottom: 8px; }
    .full-width { width: 100%; }
  `]
})
export class UserListComponent implements OnInit {
  private readonly api = inject(AdminApiService);
  private readonly snackbar = inject(MatSnackBar);
  readonly cols = ['username', 'fullName', 'email', 'roles', 'centers', 'active', 'actions'];
  readonly users = signal<AppUser[]>([]);
  readonly filtered = signal<AppUser[]>([]);
  searchTerm = '';

  ngOnInit(): void { this.load(); }

  load(): void {
    this.api.listUsers().subscribe(u => { this.users.set(u); this.filter(); });
  }

  filter(): void {
    const t = this.searchTerm.toLowerCase();
    this.filtered.set(this.users().filter(u =>
      (u.USERNAME?.toLowerCase().includes(t) || u.FULL_NAME?.toLowerCase().includes(t) || u.EMAIL?.toLowerCase().includes(t))
    ));
  }

  deleteUser(u: AppUser): void {
    if (!confirm(`Supprimer l'utilisateur ${u.USERNAME} ?`)) return;
    this.api.deleteUser(u.ID).subscribe(() => { this.snackbar.open('Utilisateur supprimé', 'OK', { duration: 2000 }); this.load(); });
  }
}

