import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { AdminApiService, AppRole } from '../../core/api/admin-api.service';

@Component({
  selector: 'app-role-list',
  standalone: true,
  imports: [CommonModule, RouterLink, MatTableModule, MatButtonModule, MatIconModule, MatCardModule, MatSnackBarModule],
  template: `
    <mat-card>
      <div class="header">
        <h2><mat-icon>admin_panel_settings</mat-icon> Gestion des rôles</h2>
        <button mat-flat-button color="primary" routerLink="/admin/roles/new"><mat-icon>add</mat-icon> Nouveau rôle</button>
      </div>

      <table mat-table [dataSource]="roles()" class="full-width">
        <ng-container matColumnDef="code"><th mat-header-cell *matHeaderCellDef>Code</th><td mat-cell *matCellDef="let r">{{ r.CODE }}</td></ng-container>
        <ng-container matColumnDef="name"><th mat-header-cell *matHeaderCellDef>Nom</th><td mat-cell *matCellDef="let r">{{ r.NAME }}</td></ng-container>
        <ng-container matColumnDef="description"><th mat-header-cell *matHeaderCellDef>Description</th><td mat-cell *matCellDef="let r">{{ r.DESCRIPTION }}</td></ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef>Actions</th>
          <td mat-cell *matCellDef="let r">
            <button mat-icon-button color="primary" [routerLink]="['/admin/roles', r.ID, 'edit']"><mat-icon>edit</mat-icon></button>
            <button mat-icon-button color="warn" (click)="deleteRole(r)"><mat-icon>delete</mat-icon></button>
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
    .full-width { width: 100%; }
  `]
})
export class RoleListComponent implements OnInit {
  private readonly api = inject(AdminApiService);
  private readonly snackbar = inject(MatSnackBar);
  readonly cols = ['code', 'name', 'description', 'actions'];
  readonly roles = signal<AppRole[]>([]);

  ngOnInit(): void { this.load(); }

  load(): void { this.api.listRoles().subscribe(r => this.roles.set(r)); }

  deleteRole(r: AppRole): void {
    if (!confirm(`Supprimer le rôle ${r.CODE} ?`)) return;
    this.api.deleteRole(r.ID).subscribe(() => { this.snackbar.open('Rôle supprimé', 'OK', { duration: 2000 }); this.load(); });
  }
}

