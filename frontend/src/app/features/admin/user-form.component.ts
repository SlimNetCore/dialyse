import {ChangeDetectionStrategy, Component, computed, inject, OnInit, signal} from '@angular/core';
import {ActivatedRoute, Router, RouterLink} from '@angular/router';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatCheckboxModule} from '@angular/material/checkbox';
import {MatSelectModule} from '@angular/material/select';
import {MatChipsModule} from '@angular/material/chips';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {MatSnackBar, MatSnackBarModule} from '@angular/material/snack-bar';
import {compatForm} from '@angular/forms/signals/compat';
import {FormField, FormRoot, readonly, required} from '@angular/forms/signals';
import {AdminApiService, AppRole} from '../../core/api/admin-api.service';
import {AppShellStore} from '../../core/state/app-shell.store';

@Component({
  selector: 'app-user-form',
  standalone: true,
  imports: [
    RouterLink,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatCheckboxModule,
    MatSelectModule,
    MatChipsModule,
    MatProgressSpinnerModule,
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
  readonly saving = signal(false);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly snackbar = inject(MatSnackBar);

  readonly isEdit = signal(false);
  readonly roles = signal<AppRole[]>([]);
  readonly loadingUser = signal(false);
  readonly showPassword = signal(false);
  readonly selectedRoles = computed(() => {
    const ids = new Set(this.form().roleIds);
    return this.roles().filter((r) => ids.has(r.id));
  });
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
    required(form.password, {when: () => !this.isEdit()});
  });
  readonly selectedCenters = computed(() => {
    const ids = new Set(this.form().centerIds);
    return this.centers().filter((c) => ids.has(c.id));
  });
  private readonly appShell = inject(AppShellStore);
  readonly centers = this.appShell.availableCenters;

  ngOnInit(): void {
    this.api.listRoles().subscribe((r) => this.roles.set(r));

    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.isEdit.set(true);
      this.editId = id;
      this.loadingUser.set(true);
      this.api.getUser(id).subscribe({
        next: (u) => {
          this.form.set({
            username: u.username,
            password: '',
            fullName: u.full_name,
            email: u.email,
            active: u.active,
            roleIds: (u.roles ?? []).map((r) => r.id),
            centerIds: (u.centers ?? []).map((c) => c.id),
          });
          this.loadingUser.set(false);
        },
        error: () => {
          this.loadingUser.set(false);
          this.snackbar.open("Impossible de charger l'utilisateur", 'OK', {duration: 3000});
          this.router.navigate(['/admin/users']);
        },
      });
    }
  }

  removeRole(id: string): void {
    this.form.update((f) => ({...f, roleIds: f.roleIds.filter((r) => r !== id)}));
  }

  removeCenter(id: string): void {
    this.form.update((f) => ({...f, centerIds: f.centerIds.filter((c) => c !== id)}));
  }

  save(): void {
    if (this.saving()) {
      return;
    }
    const form = this.form();

    if (!form.username.trim()) {
      this.snackbar.open("Le nom d'utilisateur est obligatoire", 'OK', {duration: 2500});
      return;
    }
    if (!this.isEdit() && form.password.length < 6) {
      this.snackbar.open('Le mot de passe doit contenir au moins 6 caractères', 'OK', {duration: 3000});
      return;
    }
    if (this.isEdit() && form.password && form.password.length < 6) {
      this.snackbar.open('Le nouveau mot de passe doit contenir au moins 6 caractères', 'OK', {duration: 3000});
      return;
    }
    if (!this.isEmailValid(form.email)) {
      this.snackbar.open("Adresse e-mail invalide", 'OK', {duration: 2500});
      return;
    }

    this.saving.set(true);

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
        .subscribe({
          next: () => {
            this.saving.set(false);
            this.snackbar.open('Utilisateur mis à jour', 'OK', {duration: 2000});
            this.router.navigate(['/admin/users']);
          },
          error: (err) => {
            this.saving.set(false);
            this.snackbar.open(err?.error?.error || 'Échec de la mise à jour', 'OK', {duration: 3500});
          },
        });
    } else {
      this.api
        .createUser({
          username: form.username.trim(),
          password: form.password,
          email: form.email,
          fullName: form.fullName,
          active: form.active,
          roleIds: form.roleIds,
          centerIds: form.centerIds,
        })
        .subscribe({
          next: () => {
            this.saving.set(false);
            this.snackbar.open('Utilisateur créé', 'OK', {duration: 2000});
            this.router.navigate(['/admin/users']);
          },
          error: (err) => {
            this.saving.set(false);
            this.snackbar.open(err?.error?.error || 'Échec de la création', 'OK', {duration: 3500});
          },
        });
    }
  }

  private isEmailValid(email: string): boolean {
    return !email || /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
  }
}
