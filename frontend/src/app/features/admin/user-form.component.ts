import {ChangeDetectionStrategy, Component, inject, OnInit, signal} from '@angular/core';
import {ActivatedRoute, Router, RouterLink} from '@angular/router';
import {MatCardModule} from '@angular/material/card';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatCheckboxModule} from '@angular/material/checkbox';
import {MatSelectModule} from '@angular/material/select';
import {MatSnackBar, MatSnackBarModule} from '@angular/material/snack-bar';
import {compatForm} from '@angular/forms/signals/compat';
import {FormField, FormRoot, readonly, required} from '@angular/forms/signals';
import {AdminApiService, AppRole} from '../../core/api/admin-api.service';

@Component({
  selector: 'app-user-form',
  standalone: true,
  imports: [
    RouterLink,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatCheckboxModule,
    MatSelectModule,
    FormRoot,
    FormField,
    MatSnackBarModule,
  ],
  templateUrl: './user-form.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './user-form.component.css',
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

  readonly form = signal({
    username: '',
    password: '',
    fullName: '',
    email: '',
    active: true,
    roleIds: [] as string[],
    centerIds: [] as string[],
  });

  readonly userForm = compatForm(this.form, (form) => {
    required(form.username);
    readonly(form.username, {when: () => this.isEdit()});
  });

  ngOnInit(): void {
    this.api.listRoles().subscribe((r) => this.roles.set(r));
    this.api.listCenters().subscribe((c) => this.centers.set(c));

    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.isEdit.set(true);
      this.editId = id;
      this.api.getUser(id).subscribe((u) => {
        this.form.set({
          username: u.USERNAME,
          password: '',
          fullName: u.FULL_NAME,
          email: u.EMAIL,
          active: u.ACTIVE,
          roleIds: u.roles.map((r) => r.ID),
          centerIds: u.centers.map((c) => c.ID),
        });
      });
    }
  }

  save(): void {
    const form = this.form();
    if (this.isEdit()) {
      this.api
        .updateUser(this.editId, {
          email: form.email,
          fullName: form.fullName,
          active: form.active,
          password: form.password || undefined,
          roleIds: form.roleIds,
          centerIds: form.centerIds,
        })
        .subscribe(() => {
          this.snackbar.open('Utilisateur mis à jour', 'OK', {duration: 2000});
          this.router.navigate(['/admin/users']);
        });
    } else {
      if (!form.username || !form.password) {
        this.snackbar.open('Remplissez les champs obligatoires', 'OK', {duration: 2500});
        return;
      }
      this.api
        .createUser({
          username: form.username,
          password: form.password,
          email: form.email,
          fullName: form.fullName,
          active: form.active,
          roleIds: form.roleIds,
          centerIds: form.centerIds,
        })
        .subscribe(() => {
          this.snackbar.open('Utilisateur créé', 'OK', {duration: 2000});
          this.router.navigate(['/admin/users']);
        });
    }
  }
}
