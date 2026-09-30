import {Routes} from '@angular/router';
import {superadminGuard} from '../../core/auth/superadmin.guard';

export const adminRoutes: Routes = [
  { path: 'users', loadComponent: () => import('./user-list.component').then(m => m.UserListComponent) },
  { path: 'users/new', loadComponent: () => import('./user-form.component').then(m => m.UserFormComponent) },
  { path: 'users/:id/edit', loadComponent: () => import('./user-form.component').then(m => m.UserFormComponent) },
  {
    path: 'societes',
    canActivate: [superadminGuard],
    loadComponent: () => import('./societes/societes.component').then(m => m.SocietesComponent),
  },
  {
    path: 'societes/:id',
    canActivate: [superadminGuard],
    loadComponent: () => import('./societes/societe-detail.component').then(m => m.SocieteDetailComponent),
  },
  {
    path: 'licenses',
    canActivate: [superadminGuard],
    loadComponent: () => import('./license-list.component').then(m => m.LicenseListComponent),
  },
  {
    path: 'licenses/new',
    canActivate: [superadminGuard],
    loadComponent: () => import('./license-form.component').then(m => m.LicenseFormComponent),
  },
  {path: 'audit', loadComponent: () => import('./audit-log.component').then(m => m.AuditLogComponent)},
  { path: 'roles', loadComponent: () => import('./role-list.component').then(m => m.RoleListComponent) },
  { path: 'roles/new', loadComponent: () => import('./role-form.component').then(m => m.RoleFormComponent) },
  { path: 'roles/:id/edit', loadComponent: () => import('./role-form.component').then(m => m.RoleFormComponent) },
  {
    path: 'parametrage/calendrier-clinique',
    loadComponent: () =>
      import('../seances/seance-calendar-center.component').then((m) => m.SeanceCalendarCenterComponent),
  },
  {
    path: 'parametrage/facturation',
    loadComponent: () => import('./facturation-settings.component').then((m) => m.FacturationSettingsComponent),
  },
  {
    path: 'parametrage/tva',
    loadComponent: () => import('./tva-types.component').then((m) => m.TvaTypesComponent),
  },
  {
    path: 'parametrage/referentiels',
    loadComponent: () =>
      import('./referentiels/referentiels-admin.component').then((m) => m.ReferentielsAdminComponent),
  },
  {
    path: 'parametrage/reprise',
    loadComponent: () => import('./reprise/reprise-donnees.component').then((m) => m.RepriseDonneesComponent),
  },
  { path: '', redirectTo: 'users', pathMatch: 'full' }
];

