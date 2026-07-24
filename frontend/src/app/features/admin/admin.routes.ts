import {Routes} from '@angular/router';

export const adminRoutes: Routes = [
  { path: 'users', loadComponent: () => import('./user-list.component').then(m => m.UserListComponent) },
  { path: 'users/new', loadComponent: () => import('./user-form.component').then(m => m.UserFormComponent) },
  { path: 'users/:id/edit', loadComponent: () => import('./user-form.component').then(m => m.UserFormComponent) },
  { path: 'roles', loadComponent: () => import('./role-list.component').then(m => m.RoleListComponent) },
  { path: 'roles/new', loadComponent: () => import('./role-form.component').then(m => m.RoleFormComponent) },
  { path: 'roles/:id/edit', loadComponent: () => import('./role-form.component').then(m => m.RoleFormComponent) },
  {
    path: 'parametrage/calendrier-clinique',
    loadComponent: () =>
      import('../seances/seance-calendar-center.component').then((m) => m.SeanceCalendarCenterComponent),
  },
  { path: '', redirectTo: 'users', pathMatch: 'full' }
];

